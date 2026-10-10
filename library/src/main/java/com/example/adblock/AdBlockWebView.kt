package com.example.adblock

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import java.io.ByteArrayInputStream

@SuppressLint("SetJavaScriptEnabled")
open class AdBlockWebView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : WebView(context, attrs, defStyleAttr) {

    var adBlocker: AdBlocker = AdBlocker.Builder(context)
        .addAssetSafe(AdBlocker.DEFAULT_ASSET)
        .build()
        set(value) {
            field = value
            hookScript = loadHookScript()
        }

    var onAdBlocked: ((AdBlockEvent) -> Unit)? = null

    var onPageStartedCallback: ((url: String) -> Unit)? = null

    var onPageFinishedCallback: ((url: String) -> Unit)? = null

    var onTitleChangedCallback: ((title: String) -> Unit)? = null

    private val uiHandler = Handler(Looper.getMainLooper())

    private val bridge: JsBridge = JsBridge()

    private var hookScript: String = ""

    init {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.loadsImagesAutomatically = true
        settings.setSupportMultipleWindows(false)
        settings.javaScriptCanOpenWindowsAutomatically = false
        settings.mediaPlaybackRequiresUserGesture = false
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        CookieManager.getInstance().setAcceptCookie(true)

        addJavascriptInterface(bridge, BRIDGE_NAME)
        hookScript = loadHookScript()
        installDocumentStartScript()

        webViewClient = createClient()
        webChromeClient = object : WebChromeClient() {
            override fun onReceivedTitle(view: WebView?, title: String?) {
                title?.let { onTitleChangedCallback?.invoke(it) }
                super.onReceivedTitle(view, title)
            }
        }
    }

    fun loadUrlWithAdBlock(url: String) {
        val normalized = if (url.startsWith("//")) "https:$url" else url
        loadUrl(normalized)
    }

    override fun onDetachedFromWindow() {
        try {
            removeJavascriptInterface(BRIDGE_NAME)
        } catch (_: Throwable) {
        }
        super.onDetachedFromWindow()
    }

    private fun createClient(): WebViewClient = object : WebViewClient() {

        override fun shouldInterceptRequest(
            view: WebView?,
            request: WebResourceRequest?
        ): WebResourceResponse? {
            val url = request?.url?.toString() ?: return super.shouldInterceptRequest(view, request)
            if (adBlocker.shouldBlock(url)) {
                report(AdBlockEvent.Type.REQUEST, url)
                return WebResourceResponse(
                    "text/plain",
                    "utf-8",
                    ByteArrayInputStream(ByteArray(0))
                )
            }
            return super.shouldInterceptRequest(view, request)
        }

        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
            val url = request?.url?.toString() ?: return super.shouldOverrideUrlLoading(view, request)
            if (adBlocker.shouldBlock(url)) {
                report(AdBlockEvent.Type.REQUEST, url)
                return true
            }
            return super.shouldOverrideUrlLoading(view, request)
        }

        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
            url?.let { onPageStartedCallback?.invoke(it) }
            if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                evaluateFallbackInjection(url)
            }
            super.onPageStarted(view, url, favicon)
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                evaluateFallbackInjection(url)
            }
            url?.let { onPageFinishedCallback?.invoke(it) }
            super.onPageFinished(view, url)
        }

        }

    private fun installDocumentStartScript() {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(this, hookScript, setOf("*"))
        }
    }

    private fun evaluateFallbackInjection(url: String?) {
        if (hookScript.isBlank()) return
        if (url.isNullOrBlank()) return
        if (url.startsWith("data:") || url.startsWith("about:")) return
        runCatching { evaluateJavascript(hookScript, null) }
    }

    private fun loadHookScript(): String {
        val asset = runCatching {
            context.assets.open("adblock/adblock_inject.js")
                .bufferedReader()
                .use { it.readText() }
        }.getOrDefault("")
        return "$asset\n__AdBlockBoot();"
    }

    private fun report(type: AdBlockEvent.Type, url: String) {
        uiHandler.post { onAdBlocked?.invoke(AdBlockEvent(type, url)) }
    }

    inner class JsBridge {

        @android.webkit.JavascriptInterface
        fun shouldBlock(url: String?): Boolean {
            val value = url ?: return false
            val blocked = adBlocker.shouldBlock(value)
            if (blocked) report(AdBlockEvent.Type.REQUEST, value)
            return blocked
        }

        @android.webkit.JavascriptInterface
        fun report(type: String?, url: String?) {
            report(AdBlockEvent.Type.from(type), url ?: "")
        }
    }

    companion object {
        private const val BRIDGE_NAME = "__AdBlock"
    }
}