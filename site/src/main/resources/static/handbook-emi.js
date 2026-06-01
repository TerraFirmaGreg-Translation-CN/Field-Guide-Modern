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

  function init() {
    var Renderer = globalThis.EmiRecipeRenderer;
    if (!Renderer) {
      console.warn('handbook-emi: EmiRecipeRenderer not loaded');
      return;
    }
    var renderer = new Renderer({
      baseUrl: emiBundleBaseUrl(),
      injectIconStylesheets: true,
    });
    renderer.mountAll({ root: document });

    document.querySelectorAll('.emi-handbook-tag[data-tag-id]').forEach(function (el) {
      el.addEventListener('click', function (event) {
        event.preventDefault();
        event.stopPropagation();
        var tagId = el.getAttribute('data-tag-id');
        if (tagId && typeof globalThis.showEmiTagPopover === 'function') {
          globalThis.showEmiTagPopover(tagId, el, renderer);
        }
      });
    });
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
