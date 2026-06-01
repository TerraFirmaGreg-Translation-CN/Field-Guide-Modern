/**
 * Mounts {@code .emi-recipe} placeholders using emi-recipe-renderer (CDN).
 * Expects {@code <meta name="emi-bundle-root">} from entry.ftl and export {@code emi/} beside the site root.
 */
(function () {
  'use strict';

  function emiBundleBaseUrl() {
    var meta = document.querySelector('meta[name="emi-bundle-root"]');
    if (meta && meta.content) {
      return new URL(meta.content, window.location.href).href;
    }
    return new URL('../../emi/', window.location.href).href;
  }

  function pageLocale() {
    var match = window.location.pathname.match(/\/([a-z]{2}_[a-z]{2})\//i);
    return match ? match[1].toLowerCase() : 'en_us';
  }

  async function init() {
    var Renderer = globalThis.EmiRecipeRenderer;
    if (!Renderer || typeof Renderer.mountAll !== 'function') {
      console.warn('handbook-emi: EmiRecipeRenderer.mountAll not available');
      return;
    }

    var baseUrl = emiBundleBaseUrl();
    var rendererOpts = {
      baseUrl: baseUrl,
      injectIconStylesheets: true,
      locale: pageLocale(),
    };

    try {
      await Renderer.mountAll({
        baseUrl: rendererOpts.baseUrl,
        injectIconStylesheets: rendererOpts.injectIconStylesheets,
        locale: rendererOpts.locale,
        root: document,
      });
    } catch (err) {
      console.error('handbook-emi: mountAll failed', err);
      return;
    }

    var tagRenderer = new Renderer(rendererOpts);
    document.querySelectorAll('.emi-handbook-tag[data-tag-id]').forEach(function (el) {
      el.addEventListener('click', function (event) {
        event.preventDefault();
        event.stopPropagation();
        var tagId = el.getAttribute('data-tag-id');
        if (tagId && typeof globalThis.showEmiTagPopover === 'function') {
          globalThis.showEmiTagPopover(tagId, el, tagRenderer);
        }
      });
    });
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', function () {
      init();
    });
  } else {
    init();
  }
})();
