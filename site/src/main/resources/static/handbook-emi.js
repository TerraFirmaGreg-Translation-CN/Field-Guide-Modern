/**
 * Mounts {@code .emi-recipe} placeholders using emi-recipe-renderer (CDN).
 * Expects {@code <meta name="emi-bundle-root">} from entry.ftl and export {@code emi/} beside the site root.
 * Theme follows Bootstrap {@code data-bs-theme} (see theme-switcher.js).
 * Item/tag navigation uses {@code <meta name="recipe-book-base-url">} (SiteRenderer / RECIPE_BOOK_BASE_URL).
 */
(function () {
  'use strict';

  var handbookEmiRenderer = null;

  function emiBundleBaseUrl() {
    var meta = document.querySelector('meta[name="emi-bundle-root"]');
    if (meta && meta.content) {
      return new URL(meta.content, window.location.href).href;
    }
    return new URL('../../emi/', window.location.href).href;
  }

  function recipeBookBaseUrl() {
    var meta = document.querySelector('meta[name="recipe-book-base-url"]');
    if (!meta || !meta.content) {
      return '';
    }
    return meta.content.trim();
  }

  function pageLocale() {
    var meta = document.querySelector('meta[name="emi-locale"]');
    if (meta && meta.content) {
      return meta.content.trim().toLowerCase().replace(/-/g, '_');
    }
    var match = window.location.pathname.match(/\/([a-z]{2}_[a-z]{2})\//i);
    return match ? match[1].toLowerCase() : 'en_us';
  }

  /** Resolved light/dark for EMI (matches data-bs-theme after theme-switcher runs). */
  function pageEmiTheme() {
    var bs = document.documentElement.getAttribute('data-bs-theme');
    if (bs === 'light' || bs === 'dark') {
      return bs;
    }
    if (window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches) {
      return 'dark';
    }
    return 'light';
  }

  function buildRecipeBookUrl(queryParams) {
    var base = recipeBookBaseUrl();
    if (!base) {
      return null;
    }
    try {
      var url = new URL(base.endsWith('/') ? base : base + '/');
      Object.keys(queryParams).forEach(function (key) {
        var value = queryParams[key];
        if (value != null && value !== '') {
          url.searchParams.set(key, value);
        }
      });
      return url.href;
    } catch (err) {
      console.warn('handbook-emi: invalid recipe-book-base-url', base, err);
      return null;
    }
  }

  function openRecipeBook(queryParams) {
    var href = buildRecipeBookUrl(queryParams);
    if (!href) {
      return;
    }
    window.open(href, '_blank', 'noopener,noreferrer');
  }

  function recipeBookNavigationHandlers(locale) {
    if (!recipeBookBaseUrl()) {
      return {};
    }
    return {
      onItemClick: function (itemId) {
        var id = String(itemId || '').trim();
        if (!id) {
          return;
        }
        openRecipeBook({ lang: locale, item: id.toLowerCase() });
      },
      onTagClick: function (tag, context) {
        var tagId = typeof tag === 'string' ? tag.trim() : '';
        if (!tagId && context && context.tagRef) {
          tagId = String(context.tagRef).replace(/^#(item|block|fluid):/, '').trim();
        }
        if (!tagId) {
          return;
        }
        openRecipeBook({ lang: locale, tag: tagId });
      },
    };
  }

  function applyEmiThemeToRenderer(renderer, theme) {
    if (!renderer) {
      return;
    }
    if (typeof renderer.setTheme === 'function' && (theme === 'light' || theme === 'dark')) {
      renderer.setTheme(theme);
      return;
    }
    if (typeof globalThis.applyEmiTheme === 'function') {
      globalThis.applyEmiTheme(theme, { themeRoot: document.documentElement });
    }
  }

  function wireHandbookEmiTheme(renderer) {
    handbookEmiRenderer = renderer;
    window.addEventListener('handbook-theme-change', function (event) {
      var theme = event && event.detail && event.detail.theme;
      if (theme === 'light' || theme === 'dark') {
        applyEmiThemeToRenderer(handbookEmiRenderer, theme);
      }
    });
  }

  globalThis.syncHandbookEmiTheme = function (theme) {
    if (theme !== 'light' && theme !== 'dark') {
      theme = pageEmiTheme();
    }
    applyEmiThemeToRenderer(handbookEmiRenderer, theme);
  };

  async function init() {
    var Renderer = globalThis.EmiRecipeRenderer;
    if (!Renderer || typeof Renderer.mountAll !== 'function') {
      console.warn('handbook-emi: EmiRecipeRenderer.mountAll not available');
      return;
    }

    var baseUrl = emiBundleBaseUrl();
    var locale = pageLocale();
    var navHandlers = recipeBookNavigationHandlers(locale);
    var rendererOpts = {
      baseUrl: baseUrl,
      /* Handbook already loads assets/icons/icons.css; avoid EMI bundle CSS overriding page icons */
      injectIconStylesheets: false,
      locale: locale,
      theme: pageEmiTheme(),
      themeRoot: document.documentElement,
      onItemClick: navHandlers.onItemClick,
      onTagClick: navHandlers.onTagClick,
    };

    var renderer;
    try {
      renderer = new Renderer(rendererOpts);
      await Renderer.mountAll({
        baseUrl: rendererOpts.baseUrl,
        injectIconStylesheets: rendererOpts.injectIconStylesheets,
        locale: rendererOpts.locale,
        theme: rendererOpts.theme,
        themeRoot: rendererOpts.themeRoot,
        onItemClick: rendererOpts.onItemClick,
        onTagClick: rendererOpts.onTagClick,
        renderer: renderer,
        root: document,
      });
      wireHandbookEmiTheme(renderer);
    } catch (err) {
      console.error('handbook-emi: mountAll failed', err);
      return;
    }

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
    document.addEventListener('DOMContentLoaded', function () {
      init();
    });
  } else {
    init();
  }
})();
