<#-- EMI recipe cards: npm package emi-recipe-renderer, loaded from jsDelivr (see site/package.json). -->
<meta name="emi-renderer-version" content="${emiRendererVersion}">
<meta name="emi-bundle-root" content="${emiBundleRoot}/">
<link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/emi-recipe-renderer@${emiRendererVersion}/dist/emi.min.css" crossorigin="anonymous">
<script>
(function () {
  var version = document.querySelector('meta[name="emi-renderer-version"]').content;
  var script = document.createElement('script');
  script.src = 'https://cdn.jsdelivr.net/npm/emi-recipe-renderer@' + version + '/dist/emi.min.js';
  script.crossOrigin = 'anonymous';
  script.onerror = function () {
    console.error('handbook: failed to load emi-recipe-renderer@' + version + ' from jsDelivr');
  };
  script.onload = function () {
    if (!globalThis.EmiRecipeRenderer) {
      console.error('handbook: EmiRecipeRenderer missing after CDN script load');
      return;
    }
    var boot = document.createElement('script');
    boot.src = '${root}/static/handbook-emi.js';
    boot.onerror = function () {
      console.error('handbook: failed to load handbook-emi.js');
    };
    document.body.appendChild(boot);
  };
  document.body.appendChild(script);
})();
</script>
