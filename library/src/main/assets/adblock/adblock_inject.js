(function () {

  var BRIDGE = null;
  var installed = false;

  function bridge() {
    if (BRIDGE) return BRIDGE;
    try { BRIDGE = window.__AdBlock || null; } catch (e) { BRIDGE = null; }
    return BRIDGE;
  }

  function shouldBlock(url) {
    try {
      var b = bridge();
      if (!url || !b || typeof b.shouldBlock !== 'function') return false;
      return b.shouldBlock(String(url)) === true;
    } catch (e) { return false; }
  }

  function report(type, url) {
    try {
      var b = bridge();
      if (b && typeof b.report === 'function') b.report(type, String(url || ''));
    } catch (e) {}
  }

  function blockPopups() {
    try {
      window.open = function (url) { report('POPUP', url); return null; };
    } catch (e) {}
  }

  function blockScriptSrc() {
    try {
      var d = Object.getOwnPropertyDescriptor(HTMLScriptElement.prototype, 'src');
      if (d && d.set) {
        Object.defineProperty(HTMLScriptElement.prototype, 'src', {
          configurable: true,
          get: function () { return d.get.call(this); },
          set: function (value) {
            if (shouldBlock(value)) { report('SCRIPT', value); return; }
            return d.set.call(this, value);
          }
        });
      }
    } catch (e) {}
  }

  function filterDocumentWrite(html) {
    if (typeof html !== 'string') return html;
    return html.replace(/<script[^>]+src\s*=\s*["']([^"']+)["'][^>]*>(?:<\/script>)?/gi, function (m, src) {
      if (shouldBlock(src)) { report('SCRIPT', src); return ''; }
      return m;
    });
  }

  function blockDocumentWrite() {
    try {
      var doc = Document.prototype;
      var w = doc.write, wl = doc.writeln;
      doc.write = function () {
        var args = Array.prototype.slice.call(arguments).map(filterDocumentWrite);
        return w.apply(this, args);
      };
      doc.writeln = function () {
        var args = Array.prototype.slice.call(arguments).map(filterDocumentWrite);
        return wl.apply(this, args);
      };
    } catch (e) {}
  }

  function blockBeforeUnloadHijack() {
    try {
      Object.defineProperty(window, 'onbeforeunload', {
        configurable: true,
        get: function () { return null; },
        set: function () {}
      });
      window.addEventListener('beforeunload', function (e) { e.stopImmediatePropagation(); }, true);
    } catch (e) {}
  }

  function neutralizeAntiAdblock() {
    ['adsbygoogle', 'google_ads_frame', '_ads', 'adblock', 'adBlockDetected'].forEach(function (name) {
      try {
        Object.defineProperty(window, name, {
          configurable: true,
          get: function () { return {}; },
          set: function () {}
        });
      } catch (e) {}
    });
  }

  function injectCss() {
    try {
      var css = [
        'ins.adsbygoogle{display:none!important}',
        '[id^="google_ads"]{display:none!important}',
        '[id^="div-gpt-ad"]{display:none!important}',
        'iframe[src*="//ads."]{display:none!important}',
        'iframe[src*="doubleclick.net"]{display:none!important}',
        'iframe[src*="googlesyndication"]{display:none!important}',
        '[id*="ad-overlay"]{display:none!important}',
        '[class*="ad-overlay"]{display:none!important}',
        '[class^="ad-"]{display:none!important}',
        '[id^="ad_"]{display:none!important}'
      ].join('\n');
      var style = document.createElement('style');
      style.type = 'text/css';
      style.textContent = css;
      (document.head || document.documentElement).appendChild(style);
    } catch (e) {}
  }

  function findSrc(el) {
    try { return el.src || ''; } catch (e) { return ''; }
  }

  function clean(root) {
    try {
      var nodes = root && root.querySelectorAll
        ? root.querySelectorAll('iframe[src], script[src], object[data]')
        : [];
      for (var i = 0; i < nodes.length; i++) {
        var el = nodes[i];
        var src = findSrc(el);
        if (src && shouldBlock(src)) { report('DOM', src); el.remove(); }
      }
    } catch (e) {}
  }

  var observer = null;

  function startObserver() {
    try {
      if (observer) return;
      observer = new MutationObserver(function (mutations) {
        for (var i = 0; i < mutations.length; i++) {
          var m = mutations[i];
          if (!m.addedNodes) continue;
          for (var j = 0; j < m.addedNodes.length; j++) {
            var n = m.addedNodes[j];
            if (n && n.nodeType === 1) clean(n);
          }
        }
      });
      observer.observe(document.documentElement, { childList: true, subtree: true });
    } catch (e) {}
  }

  function boot() {
    if (installed) return;
    installed = true;
    blockPopups();
    blockScriptSrc();
    blockDocumentWrite();
    blockBeforeUnloadHijack();
    neutralizeAntiAdblock();
    injectCss();
    clean(document);
    startObserver();
  }

  window.__AdBlockBoot = boot;

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', boot);
  } else {
    boot();
  }

})();