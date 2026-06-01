/**
 * Mount EMI recipe cards and tag popovers on handbook pages.
 * Requires {@code ../../emi/} bundle copied next to the static site root.
 */
(function () {
  'use strict';

  function emiBaseUrl() {
    return new URL('../../emi/', window.location.href).href;
  }

  function init() {
    var Renderer = globalThis.EmiRecipeRenderer;
    if (!Renderer) {
      console.warn('handbook-emi: EmiRecipeRenderer not loaded');
      return;
    }
    var renderer = new Renderer({
      baseUrl: emiBaseUrl(),
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
