/**
 * All-recipes perf page: builds a grid of .emi-recipe + lazy mountAll.
 */
(function (global) {
  'use strict';

  const { EmiRecipeRenderer, hideEmiTagPopover } = global;

  class EmiPerfApp {
    constructor(options) {
      this.baseUrl = options.baseUrl;
      this.index = null;
      this.recipeIds = [];
      this.mountSession = null;
      this.listBuiltAt = 0;
      this.mountStartedAt = 0;

      this.els = {
        baseUrl: document.getElementById('base-url'),
        filter: document.getElementById('filter-input'),
        grid: document.getElementById('perf-grid'),
        stats: document.getElementById('perf-stats'),
        rebuild: document.getElementById('btn-rebuild'),
        flush: document.getElementById('btn-flush'),
      };

      this.els.baseUrl.value = options.baseUrl;
      this.els.baseUrl.addEventListener('change', () => this.reload());
      this.els.filter.addEventListener('input', () => this.debouncedRebuild());
      this.els.rebuild.addEventListener('click', () => this.rebuild());
      this.els.flush.addEventListener('click', () => this.flushRemaining());
    }

    debouncedRebuild() {
      clearTimeout(this._filterTimer);
      this._filterTimer = setTimeout(() => this.rebuild(), 200);
    }

    disconnectMount() {
      if (this.mountSession?.disconnect) {
        this.mountSession.disconnect();
      }
      this.mountSession = null;
    }

    formatStats(extra = '') {
      const s = this.mountSession?.getStats?.() || {
        mounted: 0,
        failed: 0,
        pending: this.recipeIds.length,
        total: this.recipeIds.length,
      };
      const lines = [
        `listed ${this.recipeIds.length}`,
        `mounted ${s.mounted}`,
        `failed ${s.failed}`,
        `pending ${s.pending}`,
      ];
      if (this.listBuiltAt) {
        lines.push(`list DOM ${(performance.now() - this.listBuiltAt).toFixed(0)} ms`);
      }
      if (this.mountStartedAt && s.pending === 0 && s.mounted + s.failed >= s.total) {
        lines.push(`mount done ${(performance.now() - this.mountStartedAt).toFixed(0)} ms`);
      }
      if (extra) lines.push(extra);
      const cols = this.gridColumnCount();
      if (cols > 0) lines.push(`~${cols} cols`);
      this.els.stats.textContent = lines.join(' · ');
    }

    gridColumnCount() {
      const grid = this.els.grid;
      if (!grid || !grid.children.length) return 0;
      const style = getComputedStyle(grid);
      const tpl = style.gridTemplateColumns;
      if (!tpl || tpl === 'none') return 0;
      return tpl.split(' ').filter((s) => s && s !== '0px').length;
    }

    filteredIds() {
      const q = (this.els.filter.value || '').trim().toLowerCase();
      if (!q) return [...this.recipeIds];
      return this.recipeIds.filter((id) => {
        const cat = this.index?.recipes?.[id]?.category || '';
        return id.toLowerCase().includes(q) || cat.toLowerCase().includes(q);
      });
    }

    buildGrid(ids) {
      const frag = document.createDocumentFragment();
      for (const id of ids) {
        const card = document.createElement('article');
        card.className = 'perf-card';

        const label = document.createElement('p');
        label.className = 'perf-card-id';
        label.textContent = id;
        card.appendChild(label);

        const slot = document.createElement('div');
        slot.className = 'emi-recipe emi-recipe-pending';
        slot.dataset.recipeId = id;
        const scale = this.index?.scale ?? 2;
        slot.style.minWidth = `${126 * scale}px`;
        slot.style.minHeight = `${62 * scale}px`;
        card.appendChild(slot);

        frag.appendChild(card);
      }
      this.els.grid.replaceChildren(frag);
    }

    async startLazyMount() {
      this.disconnectMount();
      hideEmiTagPopover();
      this.mountStartedAt = performance.now();

      this.mountSession = await EmiRecipeRenderer.mountAll({
        root: this.els.grid,
        baseUrl: this.baseUrl,
        lazy: true,
        injectIconStylesheets: true,
        rootMargin: '500px 0px',
        onProgress: () => this.formatStats(),
      });

      this.formatStats('lazy IO active');
    }

    async rebuild() {
      const ids = this.filteredIds();
      this.listBuiltAt = performance.now();
      this.buildGrid(ids);
      this.formatStats('rebuilt');
      await this.startLazyMount();
    }

    async flushRemaining() {
      if (!this.mountSession?.flush) return;
      this.els.flush.disabled = true;
      await this.mountSession.flush();
      this.els.flush.disabled = false;
      this.formatStats('flush done');
    }

    async reload() {
      this.baseUrl = this.els.baseUrl.value.trim() || 'export';
      localStorage.setItem('emiDemoBaseUrl', this.baseUrl);
      this.disconnectMount();
      this.els.stats.textContent = 'Loading index…';

      const probe = new EmiRecipeRenderer({ baseUrl: this.baseUrl });
      try {
        this.index = await probe.loadIndex();
        this.recipeIds = Object.keys(this.index.recipes || {}).sort();
        await this.rebuild();
      } catch (e) {
        this.els.stats.textContent = `Index failed: ${e.message}`;
        this.els.grid.replaceChildren();
      }
    }
  }

  document.addEventListener('DOMContentLoaded', () => {
    const params = new URLSearchParams(location.search);
    const defaultBase = params.get('base')
      || localStorage.getItem('emiDemoBaseUrl')
      || 'export';
    const app = new EmiPerfApp({ baseUrl: defaultBase });
    app.reload();
    global.emiPerfApp = app;

    window.addEventListener('resize', () => {
      if (global.emiPerfApp) global.emiPerfApp.formatStats();
    });

    document.getElementById('tag-popover')?.addEventListener('click', (e) => {
      if (e.target.id === 'tag-popover') hideEmiTagPopover();
    });
    document.addEventListener('keydown', (e) => {
      if (e.key === 'Escape') hideEmiTagPopover();
    });
  });
})(window);
