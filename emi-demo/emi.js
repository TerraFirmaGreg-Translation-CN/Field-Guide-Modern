/**
 * emi.js — Field Guide EMI layout renderer (schema v2). Standalone library; no demo UI.
 *
 * @exports EmiRecipeRenderer, initEmiSlotCarousels, hideEmiTagPopover
 *
 * Declarative mount: {@code <div class="emi-recipe" data-recipe-id="namespace:path">}.
 * Export root is {@code mountAll({ baseUrl })} only — not per-element attributes.
 */
(function (global) {
  'use strict';

  const TEXTURE_MANIFEST = 'generated/recipe-textures/manifest.json';
  const INDEX_PATH = 'generated/recipes/layouts-index.json';
  const MISSING_ICON_ID = 'fieldguide:missing_icon';

  function joinBase(base, path) {
    const b = base.replace(/\/+$/, '');
    const p = path.replace(/^\/+/, '');
    return `${b}/${p}`;
  }

  function stripRegistryId(id) {
    if (!id) return id;
    const brace = id.indexOf('{');
    return brace >= 0 ? id.slice(0, brace) : id;
  }

  function fluidAmount(amount) {
    return amount != null && amount > 0 ? amount : 1000;
  }

  function itemAmount(amount) {
    return amount != null && amount > 0 ? amount : 1;
  }

  function formatFluidMb(amount) {
    return `${fluidAmount(amount)} mB`;
  }

  /** Compact label for tank slots (EMI-style: number only, no unit). */
  function formatFluidMbShort(amount) {
    return String(fluidAmount(amount));
  }

  function formatItemCount(amount) {
    const n = itemAmount(amount);
    return n > 1 ? String(n) : '';
  }

  function createSlotCountLabel(text, kind) {
    const el = document.createElement('span');
    el.className = kind === 'fluid' ? 'emi-slot-count emi-slot-count-fluid' : 'emi-slot-count';
    el.textContent = text;
    return el;
  }

  function parseFluidFromItemNbt(nbt) {
    if (!nbt || typeof nbt !== 'string') return null;
    const nameMatch = nbt.match(/FluidName:"([^"]+)"/);
    if (!nameMatch) return null;
    const amountMatch = nbt.match(/Amount:(\d+)/);
    return {
      id: nameMatch[1],
      amount: amountMatch ? parseInt(amountMatch[1], 10) : 1000,
    };
  }

  function parseRemainderIcon(raw) {
    if (raw == null) return null;
    if (typeof raw === 'string') {
      if (raw.startsWith('item:') || raw.startsWith('fluid:')) return 'other';
      return null;
    }
    if (typeof raw === 'object' && raw.type) return 'other';
    return null;
  }

  function parseIngredientEntry(ingredient) {
    if (ingredient == null) return null;
    if (typeof ingredient === 'string') {
      if (ingredient.startsWith('item:')) {
        return { kind: 'item', ids: [stripRegistryId(ingredient.slice(5))], amount: 1 };
      }
      if (ingredient.startsWith('#item:')) {
        return { kind: 'tag', tag: ingredient.slice(6) };
      }
      if (ingredient.startsWith('fluid:')) {
        const body = ingredient.slice(6);
        const colon = body.lastIndexOf(':');
        if (colon > 0) {
          return {
            kind: 'fluid',
            id: body.slice(0, colon),
            amount: fluidAmount(parseInt(body.slice(colon + 1), 10)),
          };
        }
      }
      return { kind: 'item', ids: [stripRegistryId(ingredient)], amount: 1 };
    }
    if (typeof ingredient === 'object') {
      if (ingredient.type === 'fluid' && ingredient.id) {
        return { kind: 'fluid', id: ingredient.id, amount: fluidAmount(ingredient.amount) };
      }
      if (ingredient.type === 'item' && ingredient.id) {
        const entry = {
          kind: 'item',
          ids: [stripRegistryId(String(ingredient.id))],
          amount: itemAmount(ingredient.amount),
          nbt: ingredient.nbt || null,
          iconKey: ingredient.iconKey ? String(ingredient.iconKey) : null,
        };
        const fluid = parseFluidFromItemNbt(entry.nbt);
        if (fluid) entry.fluid = fluid;
        const rem = parseRemainderIcon(ingredient.remainder);
        if (rem) entry.remainderIcon = rem;
        return entry;
      }
      if (ingredient.id) {
        return {
          kind: 'item',
          ids: [String(ingredient.id)],
          amount: itemAmount(ingredient.amount),
        };
      }
    }
    return null;
  }

  function parseIngredient(ingredient) {
    if (Array.isArray(ingredient)) {
      const entries = ingredient.map(parseIngredientEntry).filter(Boolean);
      return entries.length ? { kind: 'list', entries } : null;
    }
    return parseIngredientEntry(ingredient);
  }

  function appendSlotQuantity(inner, parsed) {
    if (!parsed) return;
    if (parsed.kind === 'item') {
      if (parsed.fluid) {
        inner.appendChild(createSlotCountLabel(formatFluidMbShort(parsed.fluid.amount), 'fluid'));
        return;
      }
      const text = formatItemCount(parsed.amount);
      if (text) inner.appendChild(createSlotCountLabel(text, 'item'));
    } else if (parsed.kind === 'fluid') {
      inner.appendChild(createSlotCountLabel(formatFluidMbShort(parsed.amount), 'fluid'));
    }
  }

  /** Lookup key for icon atlas (plain id or id@hash from export). */
  function lookupIconKey(entry) {
    if (!entry) return null;
    if (entry.iconKey) return entry.iconKey;
    if (entry.kind === 'item' && entry.ids?.[0]) return entry.ids[0];
    return null;
  }

  /**
   * EMI ListEmiIngredient: pick one stable display stack (minimum fluid amount when present).
   */
  function resolveListDisplayEntry(parsed, widget) {
    if (!parsed?.entries?.length) return null;
    const withFluid = parsed.entries.filter((e) => e.fluid?.amount != null);
    if (withFluid.length) {
      return withFluid.reduce((a, b) => (a.fluid.amount <= b.fluid.amount ? a : b));
    }
    const displayId = widget?.tagDisplayItem
      ? stripRegistryId(String(widget.tagDisplayItem))
      : null;
    if (displayId) {
      const match = parsed.entries.find((e) => e.ids?.[0] === displayId);
      if (match) return match;
    }
    return parsed.entries[0];
  }

  function formatPopoverEntryTooltip(entry) {
    if (!entry || entry.kind !== 'item') return '';
    if (entry.fluid?.id) return entry.ids?.[0] || entry.fluid.id;
    const count = formatItemCount(entry.amount);
    return count ? `${entry.ids[0]} x${count}` : String(entry.ids[0]);
  }

  function formatItemEntryTooltip(entry) {
    if (!entry || entry.kind !== 'item') return '';
    if (entry.fluid?.id) {
      return `${entry.fluid.id} (${formatFluidMb(entry.fluid.amount)})`;
    }
    return formatPopoverEntryTooltip(entry);
  }

  function formatListPopoverTitle(entry) {
    if (!entry) return '';
    if (entry.fluid?.id) return entry.fluid.id;
    return formatPopoverEntryTooltip(entry);
  }

  function formatListSlotTooltip(parsed, widget) {
    const display = resolveListDisplayEntry(parsed, widget);
    return formatItemEntryTooltip(display);
  }

  function formatParsedTooltip(parsed, widget) {
    if (!parsed) return '';
    if (parsed.kind === 'list') {
      return formatListSlotTooltip(parsed, widget);
    }
    if (parsed.kind === 'fluid' && parsed.id) {
      return `${parsed.id} (${formatFluidMb(parsed.amount)})`;
    }
    if (parsed.kind === 'item') {
      if (parsed.fluid?.id) {
        return `${parsed.fluid.id} (${formatFluidMb(parsed.fluid.amount)})`;
      }
      const count = formatItemCount(parsed.amount);
      return count ? `${parsed.ids[0]} x${count}` : String(parsed.ids[0]);
    }
    if (parsed.kind === 'tag' && parsed.tag) {
      return `Tag: ${parsed.tag}`;
    }
    return '';
  }

  function slotTooltip(ingredient, w, parsed) {
    if (parsed) {
      const fromParsed = formatParsedTooltip(parsed, w);
      if (fromParsed) return fromParsed;
    }
    if (ingredient == null) return '';
    if (Array.isArray(ingredient)) {
      const listParsed = parseIngredient(ingredient);
      const fromList = formatParsedTooltip(listParsed, w);
      return fromList || `${ingredient.length} ingredient options`;
    }
    if (typeof ingredient === 'string') {
      if (ingredient.startsWith('#item:')) {
        return `Tag: ${ingredient.slice(6)}`;
      }
      const text = ingredient.replace(/^#item:/, 'Tag: ').replace(/^item:/, '');
      return stripRegistryId(text);
    }
    if (typeof ingredient === 'object') {
      if (ingredient.type === 'fluid') {
        return `${ingredient.id} (${formatFluidMb(ingredient.amount)})`;
      }
      if (ingredient.type === 'item' && ingredient.id) {
        const fluid = parseFluidFromItemNbt(ingredient.nbt);
        if (fluid) {
          return `${fluid.id} (${formatFluidMb(fluid.amount)})`;
        }
        const count = formatItemCount(ingredient.amount);
        return count ? `${ingredient.id} x${count}` : String(ingredient.id);
      }
      if (ingredient.id) {
        const count = formatItemCount(ingredient.amount);
        return count ? `${ingredient.id} x${count}` : String(ingredient.id);
      }
    }
    return `${w.type} (${w.role || '?'})`;
  }

  function createAtlasSpan(cssClass, registryId) {
    const span = document.createElement('span');
    span.className = cssClass;
    span.dataset.item = registryId;
    return span;
  }

  /** EMI {@code ListEmiIngredient} uses wall-clock seconds, not shuffle. */
  const LIST_INGREDIENT_CAROUSEL_MS = 1000;

  function listIngredientCarouselIndex(count) {
    return Math.floor(Date.now() / LIST_INGREDIENT_CAROUSEL_MS) % count;
  }

  function initEmiSlotCarousels(root) {
    root.querySelectorAll('.icon-carousel').forEach((container) => {
      if (container._emiCarouselTimer) {
        clearInterval(container._emiCarouselTimer);
        container._emiCarouselTimer = null;
      }
      const frames = container.querySelectorAll(':scope > span');
      if (frames.length < 2) return;
      const update = () => {
        const index = listIngredientCarouselIndex(frames.length);
        frames.forEach((frame, i) => {
          frame.classList.toggle('icon-carousel-active', i === index);
        });
      };
      update();
      container._emiCarouselTimer = window.setInterval(update, 200);
    });
  }

  class EmiRecipeRenderer {
    constructor(options = {}) {
      this.baseUrl = options.baseUrl || 'export';
      this.missingIconId = options.missingIconId || MISSING_ICON_ID;
      this.injectIconStylesheets = options.injectIconStylesheets === true;
      this._tooltipEl = options.tooltipElement
        || (typeof options.tooltipElementId === 'string'
          ? document.getElementById(options.tooltipElementId)
          : null)
        || document.getElementById('tooltip');
      this._tagPopoverEl = options.tagPopoverElement
        || (typeof options.tagPopoverElementId === 'string'
          ? document.getElementById(options.tagPopoverElementId)
          : null)
        || document.getElementById('tag-popover');
      this.textureManifest = null;
      this.textureManifestPromise = null;
      this.tagMembers = null;
      this.tagMembersPromise = null;
      this.itemIconIds = null;
      this.blockItemIconIds = null;
      this.iconIndexPromise = null;
      this.iconStylesPromise = null;
      this.usesUnifiedIcons = null;
    }

    setBaseUrl(url) {
      this.baseUrl = url;
      this.textureManifest = null;
      this.textureManifestPromise = null;
      this.tagMembers = null;
      this.tagMembersPromise = null;
      this.itemIconIds = null;
      this.blockItemIconIds = null;
      this.iconIndexPromise = null;
      this.iconStylesPromise = null;
      this.usesUnifiedIcons = null;
      document.querySelectorAll('link[data-emi-icon]').forEach((el) => el.remove());
    }

    async loadIndex() {
      const res = await fetch(joinBase(this.baseUrl, INDEX_PATH));
      if (!res.ok) throw new Error(`index HTTP ${res.status}`);
      return res.json();
    }

    async loadLayout(recipeId, index) {
      const entry = index.recipes[recipeId];
      if (!entry?.layout) throw new Error(`no layout for ${recipeId}`);
      const res = await fetch(joinBase(this.baseUrl, entry.layout));
      if (!res.ok) throw new Error(`layout HTTP ${res.status}`);
      return {
        layout: await res.json(),
        entry,
        layoutPath: entry.layout,
      };
    }

    async ensureTextureManifest() {
      if (this.textureManifest) return this.textureManifest;
      if (!this.textureManifestPromise) {
        this.textureManifestPromise = fetch(joinBase(this.baseUrl, TEXTURE_MANIFEST))
          .then((r) => (r.ok ? r.json() : { textures: {} }))
          .then((j) => {
            this.textureManifest = j.textures || {};
            return this.textureManifest;
          })
          .catch(() => {
            this.textureManifest = {};
            return this.textureManifest;
          });
      }
      return this.textureManifestPromise;
    }

    async ensureTagMembers() {
      if (this.tagMembers) return this.tagMembers;
      if (!this.tagMembersPromise) {
        this.tagMembersPromise = fetch(joinBase(this.baseUrl, 'index/tag-members.json'))
          .then((r) => (r.ok ? r.json() : { items: {} }))
          .then((j) => {
            this.tagMembers = j.items || {};
            return this.tagMembers;
          })
          .catch(() => {
            this.tagMembers = {};
            return this.tagMembers;
          });
      }
      return this.tagMembersPromise;
    }

    async ensureIconStylesheets() {
      if (!this.injectIconStylesheets) return;
      if (this.iconStylesPromise) return this.iconStylesPromise;
      this.iconStylesPromise = (async () => {
        await this.ensureIconIndices();
        const sheets = this.usesUnifiedIcons
          ? [['icons', 'generated/icons/icons.css']]
          : [
              ['items', 'generated/items/item-icons.css'],
              ['block-items', 'generated/block-items/block-item-icons.css'],
              ['fluids', 'generated/fluids/fluid-icons.css'],
            ];
        await Promise.all(sheets.map(([kind, rel]) => {
          if (document.querySelector(`link[data-emi-icon="${kind}"]`)) {
            return Promise.resolve();
          }
          return new Promise((resolve) => {
            const link = document.createElement('link');
            link.rel = 'stylesheet';
            link.href = joinBase(this.baseUrl, rel);
            link.dataset.emiIcon = kind;
            link.onload = () => resolve();
            link.onerror = () => resolve();
            document.head.appendChild(link);
          });
        }));
      })();
      return this.iconStylesPromise;
    }

    async ensureIconIndices() {
      if (this.iconIds) return;
      if (!this.iconIndexPromise) {
        this.iconIndexPromise = (async () => {
          const unified = await fetch(joinBase(this.baseUrl, 'generated/icons/index.json'))
            .then((r) => (r.ok ? r.json() : null));
          if (unified?.items) {
            this.usesUnifiedIcons = true;
            this.iconIds = new Set(Object.keys(unified.items));
            this.iconIds.add(MISSING_ICON_ID);
            return;
          }
          this.usesUnifiedIcons = false;
          const [items, blocks] = await Promise.all([
            fetch(joinBase(this.baseUrl, 'generated/items/index.json'))
              .then((r) => (r.ok ? r.json() : { items: {} })),
            fetch(joinBase(this.baseUrl, 'generated/block-items/index.json'))
              .then((r) => (r.ok ? r.json() : { items: {} })),
          ]);
          this.iconIds = new Set([
            ...Object.keys(items.items || {}),
            ...Object.keys(blocks.items || {}),
          ]);
        })().catch(() => {
          this.usesUnifiedIcons = false;
          this.iconIds = new Set();
        });
      }
      return this.iconIndexPromise;
    }

    /** Atlas sprite id for an item/fluid, falling back to {@link MISSING_ICON_ID}. */
    resolveAtlasId(registryId) {
      if (registryId && this.iconIds?.has(registryId)) {
        return registryId;
      }
      return this.missingIconId;
    }

    createAtlasSpanForItem(registryId) {
      return this.createAtlasSpanForIconKey(registryId);
    }

    createAtlasSpanForIconKey(lookupKey) {
      const id = this.resolveAtlasId(lookupKey);
      const span = createAtlasSpan('icon-atlas', id);
      if (id === this.missingIconId && lookupKey) {
        span.title = lookupKey;
        span.dataset.missingFor = lookupKey;
      }
      return span;
    }

    /** @deprecated */
    iconRefForItem(id) {
      return { cssClass: 'icon-atlas', legacy: false };
    }

    resolveItemIds(parsed) {
      if (!parsed) return [];
      if (parsed.kind === 'item') return parsed.ids;
      if (parsed.kind === 'list') {
        return parsed.entries.flatMap((entry) => (entry.kind === 'item' ? entry.ids : []));
      }
      if (parsed.kind === 'tag') {
        return this.tagMembers?.[parsed.tag] || [];
      }
      if (parsed.kind === 'fluid') return parsed.id ? [parsed.id] : [];
      return [];
    }

    /** Tag/list/item icon keys for slot display (carousel when size > 1). */
    resolveIconIds(parsed, maxCount = 32) {
      if (!parsed) return [];
      let keys = [];
      if (parsed.kind === 'item') {
        const k = lookupIconKey(parsed);
        if (k) keys = [k];
      } else if (parsed.kind === 'list') {
        keys = parsed.entries
          .filter((e) => e.kind === 'item' && lookupIconKey(e))
          .map((e) => lookupIconKey(e));
      } else if (parsed.kind === 'tag') {
        keys = this.resolveItemIds(parsed);
      } else if (parsed.kind === 'fluid') {
        keys = parsed.id ? [parsed.id] : [];
      }
      const withIcons = keys.filter((k) => this.iconIds?.has(k));
      let merged = withIcons.length ? withIcons : keys;
      if (merged.length === 0 && parsed.kind === 'tag') {
        merged = [this.missingIconId];
      }
      return merged.length > maxCount ? merged.slice(0, maxCount) : merged;
    }

    /** EMI TagEmiIngredient: stacks.get(0), with fallbacks when tag-members is missing. */
    resolveTagDisplayId(parsed, widget) {
      const members = this.resolveItemIds(parsed);
      const withIcon = members.find((id) => this.iconIds?.has(id));
      if (withIcon) return withIcon;
      if (widget?.tagDisplayItem) {
        const key = stripRegistryId(String(widget.tagDisplayItem));
        if (this.iconIds?.has(key)) return key;
        return key;
      }
      if (members.length) return members[0];
      return this.missingIconId;
    }

    createSlotIcon(parsed, slotW, slotH, widget) {
      const ICON = 16;
      const wrap = document.createElement('div');
      wrap.className = 'emi-slot-icon';
      wrap.style.left = `${(slotW - ICON) / 2}px`;
      wrap.style.top = `${(slotH - ICON) / 2}px`;

      if (parsed.kind === 'fluid') {
        return wrap;
      }

      const keys = this.resolveIconIds(parsed);
      if (keys.length === 0) return null;

      if (keys.length === 1) {
        wrap.appendChild(this.createAtlasSpanForIconKey(keys[0]));
      } else {
        const carousel = document.createElement('div');
        carousel.className = 'icon-carousel emi-slot-carousel';
        keys.forEach((id, i) => {
          const span = this.createAtlasSpanForIconKey(id);
          if (!span) return;
          span.classList.toggle('icon-carousel-active', i === listIngredientCarouselIndex(keys.length));
          carousel.appendChild(span);
        });
        if (!carousel.childNodes.length) return null;
        wrap.appendChild(carousel);
      }

      return wrap.childNodes.length ? wrap : null;
    }

    resolveTexture(id) {
      if (!id || !this.textureManifest) return null;
      const rel = this.textureManifest[id];
      return rel ? joinBase(this.baseUrl, `generated/recipe-textures/${rel}`) : null;
    }

    /** EMI recipe panel nine-patch: BACKGROUND @ u=27,v=0 corner=4 center=1 */
    createNinePatchBackground(url, w, h, u, v, cor, cen, texSize) {
      const root = document.createElement('div');
      root.className = 'emi-nine-patch';
      root.style.width = `${w}px`;
      root.style.height = `${h}px`;
      const corcen = cor + cen;
      const innerW = w - cor * 2;
      const innerH = h - cor * 2;
      const patches = [
        [0, 0, cor, cor, u, v, cor, cor],
        [cor, 0, innerW, cor, u + cor, v, cen, cor],
        [cor + innerW, 0, cor, cor, u + corcen, v, cor, cor],
        [0, cor, cor, innerH, u, v + cor, cor, cen],
        [cor, cor, innerW, innerH, u + cor, v + cor, cen, cen],
        [cor + innerW, cor, cor, innerH, u + corcen, v + cor, cor, cen],
        [0, cor + innerH, cor, cor, u, v + corcen, cor, cor],
        [cor, cor + innerH, innerW, cor, u + cor, v + corcen, cen, cor],
        [cor + innerW, cor + innerH, cor, cor, u + corcen, v + corcen, cor, cor],
      ];
      for (const [px, py, pw, ph, su, sv, sw, sh] of patches) {
        const el = document.createElement('div');
        el.className = 'emi-nine-patch-cell';
        el.style.left = `${px}px`;
        el.style.top = `${py}px`;
        el.style.width = `${pw}px`;
        el.style.height = `${ph}px`;
        el.style.backgroundImage = `url("${url}")`;
        el.style.backgroundRepeat = 'no-repeat';
        el.style.backgroundSize = `${(texSize / sw) * pw}px ${(texSize / sh) * ph}px`;
        el.style.backgroundPosition = `-${(su / sw) * pw}px -${(sv / sh) * ph}px`;
        root.appendChild(el);
      }
      return root;
    }

    async render(container, layout) {
      await Promise.all([
        this.ensureTextureManifest(),
        this.ensureTagMembers(),
        this.ensureIconIndices(),
        this.ensureIconStylesheets(),
      ]);
      container.replaceChildren();

      const panel = layout.panel || {};
      const margin = panel.margin ?? 4;
      const frameW = panel.frameWidth ?? (panel.width ?? 0) + margin * 2;
      const frameH = panel.frameHeight ?? (panel.height ?? 0) + margin * 2;
      const displayScale = layout.scale ?? 2;

      const stageWrap = document.createElement('div');
      stageWrap.className = 'emi-recipe-stage';
      const displayW = frameW * displayScale;
      const displayH = frameH * displayScale;
      stageWrap.style.width = `${displayW}px`;
      stageWrap.style.height = `${displayH}px`;

      const frameEl = document.createElement('div');
      frameEl.className = 'emi-recipe-root';
      frameEl.style.setProperty('--emi-scale', String(displayScale));
      frameEl.style.width = `${frameW}px`;
      frameEl.style.height = `${frameH}px`;

      const content = document.createElement('div');
      content.className = 'emi-recipe-content';
      content.style.left = `${margin}px`;
      content.style.top = `${margin}px`;
      content.style.width = `${panel.width ?? frameW - margin * 2}px`;
      content.style.height = `${panel.height ?? frameH - margin * 2}px`;

      const bgUrl = this.resolveTexture('emi:textures/gui/background.png');
      if (bgUrl) {
        frameEl.appendChild(this.createNinePatchBackground(bgUrl, frameW, frameH, 27, 0, 4, 1, 256));
      }

      for (const w of layout.widgets || []) {
        const node = this.renderWidget(w);
        if (node) content.appendChild(node);
      }

      frameEl.appendChild(content);
      stageWrap.appendChild(frameEl);
      container.appendChild(stageWrap);
      initEmiSlotCarousels(stageWrap);
    }

    renderWidget(w) {
      switch (w.type) {
        case 'root_chrome':
        case 'drawable_raster':
        case 'raster':
          return this.renderChrome(w);
        case 'texture':
          return this.renderTexture(w);
        case 'animated_texture':
          return this.renderTexture(w);
        case 'filling_arrow':
          return this.renderFillingArrow(w);
        case 'text':
          return this.renderText(w);
        case 'slot':
          return this.renderSlot(w);
        case 'tank':
          return this.renderTank(w);
        default:
          return null;
      }
    }

    renderChrome(w) {
      if (!w.chrome) return null;
      const box = document.createElement('div');
      box.className = 'emi-layer emi-layer-chrome';
      box.style.left = `${w.x}px`;
      box.style.top = `${w.y}px`;
      box.style.width = `${w.w}px`;
      box.style.height = `${w.h}px`;
      const img = document.createElement('img');
      img.src = joinBase(this.baseUrl, w.chrome);
      img.alt = '';
      img.draggable = false;
      box.appendChild(img);
      return box;
    }

    renderTexture(w) {
      const url = this.resolveTexture(w.texture);
      if (!url) return null;
      const box = document.createElement('div');
      box.className = 'emi-layer emi-layer-texture';
      box.style.left = `${w.x}px`;
      box.style.top = `${w.y}px`;
      box.style.width = `${w.w}px`;
      box.style.height = `${w.h}px`;
      const texW = w.texW || 256;
      const texH = w.texH || 256;
      box.style.backgroundImage = `url("${url}")`;
      box.style.backgroundPosition = `-${w.u || 0}px -${w.v || 0}px`;
      box.style.backgroundSize = `${texW}px ${texH}px`;
      return box;
    }

    renderFillingArrow(w) {
      const url = this.resolveTexture('emi:textures/gui/widgets.png');
      if (!url) return null;
      const div = document.createElement('div');
      div.className = 'emi-layer emi-layer-texture';
      div.style.left = `${w.x}px`;
      div.style.top = `${w.y}px`;
      div.style.width = '24px';
      div.style.height = '17px';
      div.style.backgroundImage = `url("${url}")`;
      div.style.backgroundPosition = '-44px -17px';
      div.style.backgroundSize = '256px 256px';
      return div;
    }

    renderText(w) {
      const el = document.createElement('div');
      el.className = 'emi-text';
      el.style.left = `${(w.x || 0) + (w.baseX || 0)}px`;
      el.style.top = `${(w.y || 0) + (w.baseY || 0)}px`;
      if (w.color != null) {
        el.style.color = `#${(w.color & 0xffffff).toString(16).padStart(6, '0')}`;
      }
      el.textContent = w.text || '';
      return el;
    }

    drawSlotBackground(inner, isOutput) {
      const url = this.resolveTexture('emi:textures/gui/widgets.png');
      if (!url) return;
      inner.style.backgroundImage = `url("${url}")`;
      inner.style.backgroundSize = '256px 256px';
      inner.style.backgroundRepeat = 'no-repeat';
      inner.style.backgroundPosition = isOutput ? '-18px 0' : '0 0';
    }

    /** Full fluid icon (EMI-style); amount is shown via the slot count label. */
    createTankFluidFill(parsed) {
      const wrap = document.createElement('div');
      wrap.className = 'emi-tank-fluid';
      const tile = createAtlasSpan('icon-atlas', this.resolveAtlasId(parsed.id));
      tile.classList.add('emi-tank-fluid-tile');
      wrap.appendChild(tile);
      return wrap;
    }

    shouldShowTagIndicator(parsed) {
      if (!parsed) return false;
      if (parsed.kind === 'tag') return this.resolveItemIds(parsed).length > 1;
      if (parsed.kind === 'list') return parsed.entries.length > 1;
      return false;
    }

    resolveRemainderIcon(parsed, w) {
      if (w?.remainderIcon) return w.remainderIcon;
      if (parsed?.kind === 'item' && parsed.remainderIcon) return parsed.remainderIcon;
      if (parsed?.kind === 'list') {
        const display = resolveListDisplayEntry(parsed, w);
        return display?.remainderIcon || null;
      }
      return null;
    }

    appendRemainderIcon(inner, kind) {
      const url = this.resolveTexture('emi:textures/gui/widgets.png');
      if (!url || !kind) return;
      const mark = document.createElement('span');
      mark.className = `emi-slot-remainder-mark emi-slot-remainder-mark-${kind}`;
      mark.style.backgroundImage = `url("${url}")`;
      inner.appendChild(mark);
    }

    appendTagIndicator(inner) {
      const url = this.resolveTexture('emi:textures/gui/widgets.png');
      if (!url) return;
      const mark = document.createElement('span');
      mark.className = 'emi-slot-tag-mark';
      mark.style.backgroundImage = `url("${url}")`;
      inner.appendChild(mark);
    }

    bindSlotHover(el, ingredient, w, parsed) {
      el.tabIndex = 0;
      const tooltipFor = () => {
        if (parsed?.kind === 'tag' && parsed.tag) {
          return `#item:${parsed.tag}`;
        }
        if (parsed?.kind === 'list') {
          return formatListSlotTooltip(parsed, w);
        }
        return slotTooltip(ingredient, w, parsed);
      };
      el.addEventListener('mouseenter', () => showTooltip(tooltipFor(), el, this._tooltipEl));
      el.addEventListener('mouseleave', () => hideTooltip(this._tooltipEl));
      el.addEventListener('focus', () => showTooltip(tooltipFor(), el, this._tooltipEl));
      el.addEventListener('blur', () => hideTooltip(this._tooltipEl));

      if (parsed?.kind === 'tag' && parsed.tag) {
        el.classList.add('emi-slot-tag-input');
        el.title = `Click to view tag: #item:${parsed.tag}`;
        el.addEventListener('click', (e) => {
          e.stopPropagation();
          showTagPopover(parsed.tag, el, this);
        });
      } else if (parsed?.kind === 'list' && parsed.entries.length > 1) {
        el.classList.add('emi-slot-tag-input');
        el.title = 'Click to view alternatives';
        el.addEventListener('click', (e) => {
          e.stopPropagation();
          showListPopover(parsed, w, el, this);
        });
      }
    }

    renderTank(w) {
      const slotW = w.w || 18;
      const slotH = w.h || 18;
      const parsed = parseIngredient(w.ingredient);
      const hasFluid = parsed?.kind === 'fluid' && parsed.id;

      const el = document.createElement('div');
      el.className = 'emi-slot emi-tank';
      el.style.left = `${w.x}px`;
      el.style.top = `${w.y}px`;
      el.style.width = `${slotW}px`;
      el.style.height = `${slotH}px`;

      const inner = document.createElement('div');
      inner.className = 'emi-slot-inner emi-tank-inner';
      this.drawSlotBackground(inner, false);

      if (hasFluid) {
        inner.appendChild(this.createTankFluidFill(parsed));
        appendSlotQuantity(inner, parsed);
        this.bindSlotHover(el, w.ingredient, w, parsed);
      } else {
        el.classList.add('emi-slot-empty');
      }

      el.appendChild(inner);
      return el;
    }

    renderSlot(w) {
      const slotW = w.w || 18;
      const slotH = w.h || 18;
      const parsed = parseIngredient(w.ingredient);
      const hasIngredient = parsed != null && (
        parsed.kind === 'fluid'
        || parsed.kind === 'list'
        || (parsed.kind === 'item' && parsed.ids.length > 0)
        || (parsed.kind === 'tag' && parsed.tag)
      );
      const isOutput = w.role === 'output' || w.output || w.large;
      const showBack = true;

      const el = document.createElement('div');
      el.className = 'emi-slot';
      if (showBack) el.classList.add('draw-back');
      if (w.large) el.classList.add('large');
      if (!hasIngredient) el.classList.add('emi-slot-empty');
      el.style.left = `${w.x}px`;
      el.style.top = `${w.y}px`;
      el.style.width = `${slotW}px`;
      el.style.height = `${slotH}px`;

      const inner = document.createElement('div');
      inner.className = 'emi-slot-inner';
      if (showBack) {
        this.drawSlotBackground(inner, isOutput);
      }

      if (hasIngredient) {
        const icon = this.createSlotIcon(parsed, slotW, slotH, w);
        if (icon) inner.appendChild(icon);
        const remainderIcon = this.resolveRemainderIcon(parsed, w);
        if (remainderIcon) {
          this.appendRemainderIcon(inner, remainderIcon);
        }
        appendSlotQuantity(
          inner,
          parsed?.kind === 'list' ? resolveListDisplayEntry(parsed, w) : parsed,
        );
        if (this.shouldShowTagIndicator(parsed)) {
          this.appendTagIndicator(inner);
        }
        this.bindSlotHover(el, w.ingredient, w, parsed);
      }

      el.appendChild(inner);
      return el;
    }

    static _mountRendererOptions(options) {
      return {
        injectIconStylesheets: options.injectIconStylesheets,
        missingIconId: options.missingIconId,
        tooltipElement: options.tooltipElement,
        tagPopoverElement: options.tagPopoverElement,
        tooltipElementId: options.tooltipElementId,
        tagPopoverElementId: options.tagPopoverElementId,
      };
    }

    static _collectMountTargets(root, selector) {
      const nodes = root.querySelectorAll(selector);
      const items = [];
      for (const el of nodes) {
        const recipeId = (el.dataset.recipeId || '').trim();
        if (!recipeId) continue;
        items.push({ el, recipeId });
      }
      return { nodes, items };
    }

    static _showMountError(el, recipeId, message) {
      el.replaceChildren();
      el.classList.remove('emi-recipe-pending');
      const p = document.createElement('p');
      p.className = 'emi-recipe-error';
      p.textContent = message || `Failed to load recipe: ${recipeId}`;
      el.appendChild(p);
    }

    static _displaySizeFromLayout(layout) {
      const panel = layout.panel || {};
      const margin = panel.margin ?? 4;
      const scale = layout.scale ?? 2;
      const frameW = panel.frameWidth ?? (panel.width ?? 0) + margin * 2;
      const frameH = panel.frameHeight ?? (panel.height ?? 0) + margin * 2;
      return {
        width: frameW * scale,
        height: frameH * scale,
      };
    }

    /** Reserve block size on the mount node before paint (grid row height). */
    static _reserveContainerSize(el, layout) {
      const { width, height } = EmiRecipeRenderer._displaySizeFromLayout(layout);
      el.style.width = `${width}px`;
      el.style.minHeight = `${height}px`;
      el.style.boxSizing = 'border-box';
    }

    static async _mountOne(renderer, index, el, recipeId) {
      const { layout } = await renderer.loadLayout(recipeId, index);
      EmiRecipeRenderer._reserveContainerSize(el, layout);
      await renderer.render(el, layout);
      el.dataset.emiMounted = '1';
      el.classList.remove('emi-recipe-pending');
      el.style.width = '';
      el.style.minHeight = '';
    }

    static _emitMountProgress(options, stats, total) {
      if (typeof options.onProgress === 'function') {
        options.onProgress({
          mounted: stats.mounted,
          failed: stats.failed,
          pending: total - stats.mounted - stats.failed,
          total,
        });
      }
    }

    /**
     * Load and render one {@code .emi-recipe} from {@code data-recipe-id}
     * (via layouts-index → layout JSON). Layout paths are not part of the public API.
     */
    static async mountElement(el, options = {}) {
      const recipeId = (el.dataset.recipeId || options.recipeId || '').trim();
      if (!recipeId) {
        throw new Error('emi-recipe: missing data-recipe-id');
      }
      const baseUrl = (options.baseUrl || 'export').trim();
      const renderer = new EmiRecipeRenderer({
        baseUrl,
        ...EmiRecipeRenderer._mountRendererOptions(options),
      });
      try {
        const index = await renderer.loadIndex();
        await EmiRecipeRenderer._mountOne(renderer, index, el, recipeId);
        return { recipeId, baseUrl, renderer };
      } catch (e) {
        EmiRecipeRenderer._showMountError(el, recipeId, e?.message);
        throw e;
      }
    }

    /**
     * Mount {@code .emi-recipe[data-recipe-id]} under {@code options.root}.
     * {@code options.lazy === true}: Intersection Observer, mount when near viewport.
     */
    static async mountAll(options = {}) {
      if (options.lazy) {
        return EmiRecipeRenderer._mountAllLazy(options);
      }
      return EmiRecipeRenderer._mountAllEager(options);
    }

    static async _mountAllEager(options = {}) {
      const root = options.root || document;
      const selector = options.selector || '.emi-recipe[data-recipe-id]';
      const baseUrl = (options.baseUrl || 'export').trim();
      const { nodes, items } = EmiRecipeRenderer._collectMountTargets(root, selector);
      const mountOpts = EmiRecipeRenderer._mountRendererOptions(options);
      const stats = { mounted: 0, failed: 0, errors: [] };

      if (items.length === 0) {
        return {
          total: nodes.length,
          mounted: 0,
          failed: 0,
          errors: [],
          baseUrl,
          lazy: false,
        };
      }

      const renderer = new EmiRecipeRenderer({ baseUrl, ...mountOpts });
      let index;
      try {
        index = await renderer.loadIndex();
      } catch (e) {
        for (const { el, recipeId } of items) {
          stats.failed += 1;
          EmiRecipeRenderer._showMountError(el, recipeId, `layouts-index: ${e.message}`);
          stats.errors.push({ recipeId, error: e });
        }
        EmiRecipeRenderer._emitMountProgress(options, stats, items.length);
        return {
          total: nodes.length,
          mounted: stats.mounted,
          failed: stats.failed,
          errors: stats.errors,
          baseUrl,
          lazy: false,
        };
      }

      for (const { el, recipeId } of items) {
        try {
          await EmiRecipeRenderer._mountOne(renderer, index, el, recipeId);
          stats.mounted += 1;
        } catch (err) {
          stats.failed += 1;
          EmiRecipeRenderer._showMountError(el, recipeId, err?.message);
          stats.errors.push({ recipeId, error: err });
        }
        EmiRecipeRenderer._emitMountProgress(options, stats, items.length);
      }

      return {
        total: nodes.length,
        mounted: stats.mounted,
        failed: stats.failed,
        errors: stats.errors,
        baseUrl,
        lazy: false,
      };
    }

    static async _mountAllLazy(options = {}) {
      const root = options.root || document;
      const baseUrl = (options.baseUrl || 'export').trim();
      const mountOpts = EmiRecipeRenderer._mountRendererOptions(options);
      const { nodes, items } = EmiRecipeRenderer._collectMountTargets(
        root,
        '.emi-recipe[data-recipe-id]',
      );
      const stats = { mounted: 0, failed: 0, errors: [] };
      const inFlight = new Set();

      if (items.length === 0) {
        return {
          total: nodes.length,
          mounted: 0,
          failed: 0,
          errors: [],
          baseUrl,
          lazy: true,
          getStats: () => ({ ...stats, pending: 0, total: 0 }),
          disconnect() {},
          flush() {},
        };
      }

      const renderer = new EmiRecipeRenderer({ baseUrl, ...mountOpts });
      let index;
      try {
        index = await renderer.loadIndex();
      } catch (e) {
        for (const { el, recipeId } of items) {
          stats.failed += 1;
          EmiRecipeRenderer._showMountError(el, recipeId, `layouts-index: ${e.message}`);
          stats.errors.push({ recipeId, error: e });
        }
        EmiRecipeRenderer._emitMountProgress(options, stats, items.length);
        return {
          total: nodes.length,
          mounted: stats.mounted,
          failed: stats.failed,
          errors: stats.errors,
          baseUrl,
          lazy: true,
          getStats: () => ({
            mounted: stats.mounted,
            failed: stats.failed,
            pending: items.length - stats.mounted - stats.failed,
            total: items.length,
          }),
          disconnect() {},
          async flush() {},
        };
      }

      const tryMount = async (el, recipeId) => {
        if (el.dataset.emiMounted === '1' || inFlight.has(el)) return;
        inFlight.add(el);
        try {
          await EmiRecipeRenderer._mountOne(renderer, index, el, recipeId);
          stats.mounted += 1;
        } catch (err) {
          stats.failed += 1;
          EmiRecipeRenderer._showMountError(el, recipeId, err?.message);
          stats.errors.push({ recipeId, error: err });
        } finally {
          inFlight.delete(el);
          EmiRecipeRenderer._emitMountProgress(options, stats, items.length);
        }
      };

      const flushPending = async () => {
        const pending = items.filter(({ el }) => el.dataset.emiMounted !== '1' && !inFlight.has(el));
        await Promise.all(pending.map(({ el, recipeId }) => tryMount(el, recipeId)));
      };

      if (typeof IntersectionObserver === 'undefined') {
        await flushPending();
        return {
          total: nodes.length,
          mounted: stats.mounted,
          failed: stats.failed,
          errors: stats.errors,
          baseUrl,
          lazy: false,
          getStats: () => ({
            mounted: stats.mounted,
            failed: stats.failed,
            pending: items.length - stats.mounted - stats.failed,
            total: items.length,
          }),
          disconnect() {},
          flush: flushPending,
        };
      }

      const observer = new IntersectionObserver((entries) => {
        for (const entry of entries) {
          if (!entry.isIntersecting) continue;
          observer.unobserve(entry.target);
          const id = (entry.target.dataset.recipeId || '').trim();
          if (id) tryMount(entry.target, id);
        }
      }, {
        root: options.observeRoot || null,
        rootMargin: options.rootMargin ?? '400px 0px',
        threshold: options.threshold ?? 0,
      });

      for (const { el } of items) {
        if (el.dataset.emiMounted === '1') continue;
        el.classList.add('emi-recipe-pending');
        observer.observe(el);
      }

      EmiRecipeRenderer._emitMountProgress(options, stats, items.length);

      return {
        total: nodes.length,
        mounted: stats.mounted,
        failed: stats.failed,
        errors: stats.errors,
        baseUrl,
        lazy: true,
        getStats: () => ({
          mounted: stats.mounted,
          failed: stats.failed,
          pending: items.length - stats.mounted - stats.failed,
          total: items.length,
        }),
        disconnect() {
          observer.disconnect();
        },
        flush: flushPending,
      };
    }
  }

  function showTooltip(text, anchor, tipEl) {
    const tip = tipEl || document.getElementById('tooltip');
    if (!tip || !text) return;
    tip.textContent = text;
    tip.style.display = 'block';
    const r = anchor.getBoundingClientRect();
    tip.style.left = `${Math.min(r.left, window.innerWidth - 330)}px`;
    tip.style.top = `${r.bottom + 6}px`;
  }

  function hideTooltip(tipEl) {
    const tip = tipEl || document.getElementById('tooltip');
    if (tip) tip.style.display = 'none';
  }

  const TAG_SLOT = 18;
  const TAG_GRID_Y = 24;
  const TAG_DISPLAY_SCALE = 2;
  /** EMI EmiIngredientRecipe: fixed 8-column panel (144px). */
  const TAG_PANEL_COLS = 8;
  const TAG_PANEL_W = TAG_PANEL_COLS * TAG_SLOT;
  /** EMI drawNinePatch uses displayWidth/Height + 8 (4px border each side). */
  const TAG_PANEL_BORDER = 4;

  function tagContentHeight(itemCount) {
    if (!itemCount) return TAG_GRID_Y;
    const rows = Math.ceil(itemCount / TAG_PANEL_COLS);
    return TAG_GRID_Y + rows * TAG_SLOT;
  }

  function resolveTagPopoverIds(renderer, tag) {
    const members = renderer.tagMembers?.[tag];
    if (Array.isArray(members) && members.length) {
      return members;
    }
    return [];
  }

  function tagEmiRecipeId(tag) {
    return `emi:/tag/item/${tag.replace(':', '/')}`;
  }

  let tagPopoverAnchor = null;

  function hideEmiTagPopover(popEl) {
    const pop = popEl || document.getElementById('tag-popover');
    if (pop) pop.hidden = true;
    tagPopoverAnchor = null;
  }

  function listPopoverItems(parsed) {
    return parsed.entries.map((entry) => ({
      lookupKey: lookupIconKey(entry) || entry.ids?.[0] || MISSING_ICON_ID,
      tooltip: formatPopoverEntryTooltip(entry),
      remainderIcon: entry.remainderIcon || null,
      quantity: entry.fluid ? null : entry,
    }));
  }

  function createPopoverSlot(renderer, spec, showTagMark) {
    const cell = document.createElement('div');
    cell.className = 'tag-popover-slot tag-popover-grid-slot';
    cell.tabIndex = 0;

    const inner = document.createElement('div');
    inner.className = 'emi-slot-inner';
    renderer.drawSlotBackground(inner, false);

    const iconWrap = document.createElement('div');
    iconWrap.className = 'emi-slot-icon';
    iconWrap.style.left = '1px';
    iconWrap.style.top = '1px';
    iconWrap.appendChild(renderer.createAtlasSpanForIconKey(spec.lookupKey));
    inner.appendChild(iconWrap);
    if (spec.remainderIcon) renderer.appendRemainderIcon(inner, spec.remainderIcon);
    if (spec.quantity) appendSlotQuantity(inner, spec.quantity);
    if (showTagMark) renderer.appendTagIndicator(inner);

    cell.appendChild(inner);
    cell.addEventListener('mouseenter', () => showTooltip(spec.tooltip, cell, renderer._tooltipEl));
    cell.addEventListener('mouseleave', () => hideTooltip(renderer._tooltipEl));
    cell.addEventListener('focus', () => showTooltip(spec.tooltip, cell, renderer._tooltipEl));
    cell.addEventListener('blur', () => hideTooltip(renderer._tooltipEl));
    return cell;
  }

  function createTagPopoverSlot(renderer, itemId, showTagMark) {
    return createPopoverSlot(renderer, {
      lookupKey: itemId,
      tooltip: itemId,
      remainderIcon: null,
      quantity: null,
    }, showTagMark);
  }

  async function showIngredientPopover({
    title,
    subtitle,
    items,
    anchorEl,
    renderer,
    emptyMessage,
    featuredIndex = 0,
  }) {
    const pop = renderer._tagPopoverEl;
    if (!pop) return;

    if (tagPopoverAnchor === anchorEl && !pop.hidden) {
      hideEmiTagPopover(pop);
      return;
    }

    await Promise.all([
      renderer.ensureTagMembers(),
      renderer.ensureIconStylesheets(),
      renderer.ensureIconIndices(),
    ]);

    hideTooltip(renderer._tooltipEl);
    tagPopoverAnchor = anchorEl;

    const header = pop.querySelector('.tag-popover-header');
    const stageWrap = pop.querySelector('.tag-popover-stage-wrap');
    const footer = pop.querySelector('.tag-popover-footer');

    header.replaceChildren();
    const titleEl = document.createElement('div');
    titleEl.className = 'tag-popover-title';
    titleEl.textContent = title;
    const subEl = document.createElement('div');
    subEl.className = 'tag-popover-emi-id';
    subEl.textContent = subtitle;
    header.appendChild(titleEl);
    header.appendChild(subEl);
    footer.textContent = subtitle;

    stageWrap.replaceChildren();

    const contentW = TAG_PANEL_W;
    const contentH = tagContentHeight(items.length);
    const panelW = contentW + TAG_PANEL_BORDER * 2;
    const panelH = contentH + TAG_PANEL_BORDER * 2;
    const scale = TAG_DISPLAY_SCALE;

    const stage = document.createElement('div');
    stage.className = 'tag-popover-stage';
    stage.style.width = `${panelW * scale}px`;
    stage.style.height = `${panelH * scale}px`;

    const root = document.createElement('div');
    root.className = 'tag-popover-root';
    root.style.setProperty('--emi-scale', String(scale));
    root.style.width = `${panelW}px`;
    root.style.height = `${panelH}px`;

    const body = document.createElement('div');
    body.className = 'tag-popover-body';
    body.style.left = `${TAG_PANEL_BORDER}px`;
    body.style.top = `${TAG_PANEL_BORDER}px`;
    body.style.width = `${contentW}px`;
    body.style.height = `${contentH}px`;

    const bgUrl = renderer.resolveTexture('emi:textures/gui/background.png');
    if (bgUrl) {
      root.appendChild(renderer.createNinePatchBackground(
        bgUrl, panelW, panelH, 27, 0, 4, 1, 256,
      ));
    }

    if (items.length > 0) {
      const featuredSpec = items[featuredIndex] || items[0];
      const featured = document.createElement('div');
      featured.className = 'tag-popover-featured-slot';
      featured.appendChild(createPopoverSlot(renderer, featuredSpec, items.length > 1));
      body.appendChild(featured);

      const grid = document.createElement('div');
      grid.className = 'tag-popover-grid';
      items.forEach((spec, i) => {
        const cell = createPopoverSlot(renderer, spec, false);
        cell.style.left = `${(i % TAG_PANEL_COLS) * TAG_SLOT}px`;
        cell.style.top = `${Math.floor(i / TAG_PANEL_COLS) * TAG_SLOT}px`;
        grid.appendChild(cell);
      });
      body.appendChild(grid);
    } else {
      const empty = document.createElement('p');
      empty.className = 'tag-popover-empty';
      empty.textContent = emptyMessage || 'No items';
      body.appendChild(empty);
    }

    root.appendChild(body);
    stage.appendChild(root);
    stageWrap.appendChild(stage);
    pop.hidden = false;
  }

  async function showListPopover(parsed, widget, anchorEl, renderer) {
    const items = listPopoverItems(parsed);
    const display = resolveListDisplayEntry(parsed, widget);
    const displayKey = lookupIconKey(display) || display?.ids?.[0];
    let featuredIndex = items.findIndex((item) => item.lookupKey === displayKey);
    if (featuredIndex < 0) featuredIndex = 0;
    await showIngredientPopover({
      title: formatListPopoverTitle(display),
      subtitle: `${items.length} ingredient options`,
      items,
      anchorEl,
      renderer,
      emptyMessage: 'No alternatives',
      featuredIndex,
    });
  }

  async function showTagPopover(tag, anchorEl, renderer) {
    const ids = resolveTagPopoverIds(renderer, tag);
    const items = ids.map((id) => ({
      lookupKey: id,
      tooltip: id,
      remainderIcon: null,
      quantity: null,
    }));
    await showIngredientPopover({
      title: `#item:${tag}`,
      subtitle: tagEmiRecipeId(tag),
      items,
      anchorEl,
      renderer,
      emptyMessage: `No members for #item:${tag} in tag-members.json`,
    });
  }

  const api = {
    EmiRecipeRenderer,
    initEmiSlotCarousels,
    hideEmiTagPopover,
    MISSING_ICON_ID,
  };
  if (typeof module !== 'undefined' && module.exports) {
    module.exports = api;
  } else {
    global.EmiRecipeRenderer = EmiRecipeRenderer;
    global.initEmiSlotCarousels = initEmiSlotCarousels;
    global.hideEmiTagPopover = hideEmiTagPopover;
    global.EmiMissingIconId = MISSING_ICON_ID;
  }
})(typeof globalThis !== 'undefined' ? globalThis : window);
