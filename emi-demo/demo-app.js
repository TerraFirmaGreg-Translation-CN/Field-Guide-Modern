/**
 * emi-demo review UI (not part of emi.js library).
 */
(function (global) {
  'use strict';

  const { EmiRecipeRenderer, hideEmiTagPopover } = global;

  function joinBase(base, path) {
    const b = base.replace(/\/+$/, '');
    const p = path.replace(/^\/+/, '');
    return `${b}/${p}`;
  }

  function recipeSafeFileName(recipeId) {
    return String(recipeId || 'unknown').replace(/[:/]/g, '_');
  }

  function resolveReferencePath(recipeId, indexEntry, layout) {
    if (layout?.referencePng) return layout.referencePng;
    if (indexEntry?.reference) return indexEntry.reference;
    return `generated/recipes/${recipeSafeFileName(recipeId)}.png`;
  }

  function createCompareSection(title, hint) {
    const section = document.createElement('section');
    section.className = 'compare-section';
    const heading = document.createElement('h2');
    heading.className = 'compare-heading';
    heading.textContent = title;
    section.appendChild(heading);
    if (hint) {
      const sub = document.createElement('p');
      sub.className = 'compare-hint';
      sub.textContent = hint;
      section.appendChild(sub);
    }
    const body = document.createElement('div');
    body.className = 'compare-body';
    section.appendChild(body);
    return { section, body };
  }

  async function loadRecipeData(renderer, recipeId) {
    const colon = recipeId.indexOf(':');
    if (colon < 1) {
      return { found: false, bundlePath: null, jsonText: null };
    }
    const namespace = recipeId.slice(0, colon);
    const bundlePath = `data/${namespace}/recipes.json`;
    if (!renderer._recipeBundleCache) renderer._recipeBundleCache = new Map();
    if (!renderer._recipeBundleCache.has(namespace)) {
      renderer._recipeBundleCache.set(namespace, fetch(joinBase(renderer.baseUrl, bundlePath))
        .then((r) => (r.ok ? r.json() : null))
        .catch(() => null));
    }
    const bundle = await renderer._recipeBundleCache.get(namespace);
    const entry = bundle?.[recipeId];
    if (entry == null) {
      return { found: false, bundlePath, jsonText: null };
    }
    return {
      found: true,
      bundlePath,
      jsonText: JSON.stringify({ [recipeId]: entry }, null, 2),
      recipe: entry,
    };
  }

  class EmiRecipeDemoApp {
    constructor(options) {
      this.renderer = new EmiRecipeRenderer({
        baseUrl: options.baseUrl,
        injectIconStylesheets: true,
      });
      this.index = null;
      this.recipeIds = [];
      this.filtered = [];
      this.selectedId = null;

      this.els = {
        baseUrl: document.getElementById('base-url'),
        filter: document.getElementById('filter-input'),
        list: document.getElementById('recipe-list'),
        listMeta: document.getElementById('list-meta'),
        detailEmpty: document.getElementById('detail-empty'),
        detailTitle: document.getElementById('detail-title'),
        recipeView: document.getElementById('recipe-view'),
        status: document.getElementById('status'),
      };

      this.els.baseUrl.value = options.baseUrl;
      this.els.baseUrl.addEventListener('change', () => this.reload());
      this.els.filter.addEventListener('input', () => this.applyFilter());
    }

    setStatus(msg, isError) {
      this.els.status.textContent = msg || '';
      this.els.status.style.color = isError ? '#f88' : 'var(--muted)';
    }

    async reload() {
      const base = this.els.baseUrl.value.trim() || 'export';
      this.renderer.setBaseUrl(base);
      localStorage.setItem('emiDemoBaseUrl', base);
      this.setStatus('Loading index…');
      try {
        this.index = await this.renderer.loadIndex();
        this.recipeIds = Object.keys(this.index.recipes || {}).sort();
        this.applyFilter();
        this.setStatus(`Loaded ${this.recipeIds.length} recipes`);
      } catch (e) {
        this.setStatus(`Load failed: ${e.message}`, true);
        this.recipeIds = [];
        this.applyFilter();
      }
    }

    applyFilter() {
      const q = (this.els.filter.value || '').trim().toLowerCase();
      this.filtered = q
        ? this.recipeIds.filter((id) => {
            const cat = this.index?.recipes?.[id]?.category || '';
            return id.toLowerCase().includes(q) || cat.toLowerCase().includes(q);
          })
        : [...this.recipeIds];
      this.renderList();
    }

    renderList() {
      this.els.list.replaceChildren();
      this.els.listMeta.textContent = `Showing ${this.filtered.length} / ${this.recipeIds.length}`;

      for (const id of this.filtered) {
        const li = document.createElement('li');
        const btn = document.createElement('button');
        btn.type = 'button';
        if (id === this.selectedId) btn.classList.add('active');

        const idSpan = document.createElement('span');
        idSpan.className = 'recipe-id';
        idSpan.textContent = id;
        btn.appendChild(idSpan);

        const cat = this.index?.recipes?.[id]?.category;
        if (cat) {
          const catSpan = document.createElement('span');
          catSpan.className = 'recipe-cat';
          catSpan.textContent = cat;
          btn.appendChild(catSpan);
        }

        btn.addEventListener('click', () => this.selectRecipe(id));
        li.appendChild(btn);
        this.els.list.appendChild(li);
      }
    }

    async selectRecipe(id) {
      this.selectedId = id;
      this.renderList();
      this.els.detailEmpty.style.display = 'none';
      this.els.detailTitle.textContent = id;
      this.els.recipeView.replaceChildren();
      hideEmiTagPopover();
      this.setStatus(`Loading ${id}…`);

      try {
        const bundle = await this.renderer.loadLayout(id, this.index);
        const { layout, entry } = bundle;
        const recipeData = await loadRecipeData(this.renderer, id);

        const compareRoot = document.createElement('div');
        compareRoot.className = 'emi-recipe-compare';

        const renderSec = createCompareSection(
          'emi.js render',
          'Mounted via data-recipe-id → layouts-index (internal layout JSON)',
        );
        compareRoot.appendChild(renderSec.section);
        const mountEl = document.createElement('div');
        mountEl.className = 'emi-recipe';
        mountEl.dataset.recipeId = id;
        renderSec.body.appendChild(mountEl);
        await EmiRecipeRenderer.mountAll({
          root: renderSec.body,
          baseUrl: this.renderer.baseUrl,
          injectIconStylesheets: true,
        });

        const refSec = createCompareSection(
          'EMI reference',
          'Off-screen EMI render at export time (generated/recipes/*.png)',
        );
        compareRoot.appendChild(refSec.section);
        const refPath = resolveReferencePath(id, entry, layout);
        const refUrl = joinBase(this.renderer.baseUrl, refPath);
        const refImg = document.createElement('img');
        refImg.className = 'compare-reference-img';
        refImg.alt = `EMI reference: ${id}`;
        refImg.loading = 'lazy';
        refImg.src = refUrl;
        const refMissing = document.createElement('p');
        refMissing.className = 'compare-missing';
        refMissing.hidden = true;
        refMissing.textContent = `Reference PNG not found (${refPath}). Re-export to generate with EMI layouts.`;
        refImg.addEventListener('error', () => {
          refImg.hidden = true;
          refMissing.hidden = false;
        });
        refSec.body.appendChild(refImg);
        refSec.body.appendChild(refMissing);

        const jsonSec = createCompareSection(
          'Recipe JSON (data)',
          recipeData.bundlePath || 'data/<namespace>/recipes.json',
        );
        compareRoot.appendChild(jsonSec.section);
        if (recipeData.found) {
          const pre = document.createElement('pre');
          pre.className = 'compare-json';
          const code = document.createElement('code');
          code.textContent = recipeData.jsonText;
          pre.appendChild(code);
          jsonSec.body.appendChild(pre);
        } else {
          const missing = document.createElement('p');
          missing.className = 'compare-missing';
          missing.textContent = recipeData.bundlePath
            ? `${id} not found in ${recipeData.bundlePath}.`
            : `Invalid recipe id: ${id}`;
          jsonSec.body.appendChild(missing);
        }

        this.els.recipeView.appendChild(compareRoot);
        this.setStatus(`${id}${layout.category ? ' · ' + layout.category : ''}`);
      } catch (e) {
        this.setStatus(`Failed to load recipe: ${e.message}`, true);
      }
    }
  }

  global.EmiRecipeDemoApp = EmiRecipeDemoApp;

  document.addEventListener('DOMContentLoaded', () => {
    const params = new URLSearchParams(location.search);
    const defaultBase = params.get('base')
      || localStorage.getItem('emiDemoBaseUrl')
      || 'export';
    const app = new EmiRecipeDemoApp({ baseUrl: defaultBase });
    app.reload();
    global.emiDemoApp = app;

    document.getElementById('tag-popover')?.addEventListener('click', (e) => {
      if (e.target.id === 'tag-popover') hideEmiTagPopover();
    });
    document.addEventListener('keydown', (e) => {
      if (e.key === 'Escape') hideEmiTagPopover();
    });
  });
})(window);
