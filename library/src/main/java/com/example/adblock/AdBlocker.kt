package com.example.adblock

import android.content.Context

class AdBlocker private constructor(
    private val rules: AdBlockRules
) {

    private val cache = LinkedHashMap<String, Boolean>(MAX_CACHE, 0.75f, true)

    fun shouldBlock(url: String): Boolean {
        if (url.isEmpty()) return false
        synchronized(cache) {
            cache[url]?.let { return it }
        }
        val result = rules.isBlocked(url)
        synchronized(cache) {
            cache[url] = result
        }
        return result
    }

    class Builder(private val context: Context) {

        private val lines = ArrayList<String>()

        fun addRules(text: String): Builder = apply {
            text.lineSequence().forEach { lines += it }
        }

        fun addAsset(path: String): Builder = apply {
            context.assets.open(path).bufferedReader().useLines { sequence ->
                sequence.forEach { lines += it }
            }
        }

        fun addAssetSafe(path: String): Builder = apply {
            runCatching { addAsset(path) }
        }

        fun build(): AdBlocker = AdBlocker(AdBlockRules.parse(lines))
    }

    companion object {
        private const val MAX_CACHE = 2048

        fun fromAssets(
            context: Context,
            path: String = DEFAULT_ASSET,
            extraRules: String? = null
        ): AdBlocker = Builder(context)
            .addAssetSafe(path)
            .apply { if (extraRules != null) addRules(extraRules) }
            .build()

        const val DEFAULT_ASSET = "adblock/blocklist.txt"
    }
}