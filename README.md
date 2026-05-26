# Field-Guide-Modern

Multi-module project for the TFG Patchouli field guide website.

## Design docs

设计文档在工作区根目录 [`docs/`](../docs/)（不在本仓库 `Field-Guide-Modern/docs/` 下）。

| Doc | Topic |
|-----|--------|
| [docs/design/emi-web-renderer.md](../docs/design/emi-web-renderer.md) | **emi.js** — EMI widget 布局导出（Forge 已实现）+ 前端重现 |
| [docs/design/emi-js-library.md](../docs/design/emi-js-library.md) | **emi.js** 库边界、API、按块加载 |
| [emi-demo/](emi-demo/) | emi.js 浏览器 demo（配方列表 + 渲染） |

## Modules

| Module | Purpose |
|--------|---------|
| `site` | **P1 static site generator**: `guide-export/` → HTML (FreeMarker); only reads runtime export |
| `cli` | **Legacy** offline generator: modpack jars + datapack → HTML (`field-guide-tfg` fat jar) |
| `forge` | Forge 1.20.1 runtime export: `meta.json` + merged `assets/` + `data/` + `extras/registry-labels/` |

## Modpack pin

Use the **latest stable release tag** from [Modpack-Modern releases](https://github.com/TerraFirmaGreg-Team/Modpack-Modern/releases) (semver `x.y.z`), not the `dev` branch. CI runs `scripts/checkout-modpack-latest-release.sh` on each job.

```bash
bash scripts/checkout-modpack-latest-release.sh
cd Modpack-Modern && java -jar pakku.jar fetch && cd ..
```

To pin a specific version locally or in CI: `MODPACK_TAG=0.12.7 bash scripts/checkout-modpack-latest-release.sh`

## Build

Gradle only compiles and tests modules — it does not export game data or generate the site.

```bash
./gradlew build                 # core + cli + site + forge
./gradlew :forge:reobfJar       # mod jar only
./gradlew :cli:jar              # legacy CLI fat jar → cli/build/libs/
```

Export and site generation are run outside Gradle (modpack client, `java -jar`, IDE, `buildBook.sh`, etc.).

```bash
./deploy.sh                    # pakku fetch + :cli:jar + java -jar → output/
SKIP_BUILD=1 ./deploy.sh       # reuse cli/build/libs/field-guide-tfg-*.jar
SKIP_PAKKU=1 ./deploy.sh       # skip mod download
```

Windows: `deploy.bat` (same env vars). CI: `.github/workflows/build.yml`.

Manual in-game export: `/fieldguide export`.

Runtime export writes `forge/build/guide-export/` (default **`closure`** — only book-referenced assets; use `-Dfieldguide.exportMode=full` for the full mirror):

- `assets/` + `data/` — **closure**: models/textures/blockstates/Patchouli book reachable from `meta.json` refs; **full**: merged runtime trees (no `particles`/`sounds`/`worldgen`/`structures`)
- `lang/<lang>.json` — **closure**: only translation keys for book/recipe/registry refs; **full**: all mod lang keys merged
- `data/<namespace>/recipes.json` — datapack + **live RecipeManager** (KubeJS/GT); keys `gtceu:foo/bar`
- `data/<namespace>/tags/{items,blocks,fluids}.json` — runtime tags (**full**: all tags; **closure**: expanded tag closure only)
- `index/recipes-by-output.json` — item id → `[{ namespace, recipeId }]` (built at end of export)
- `index/tag-members.json` — **closure only**: tag id → fully expanded `[registry ids]` per `items` / `blocks` / `fluids`
- `logs/` — modpack `logs/` + `debug.log` + `HeadlessMC/` (for CI artifact debugging; skip with `-Dfieldguide.skipExportLogs=true`)
- `generated/icons/` — unified **32×32** item + block-item + fluid atlas (`icons.css` + `index.json` + `atlas-*.png`)

Closure mode expands **tags** from book + recipes into concrete items/blocks/fluids (like CLI), exports filtered `data/*/tags/*.json`, writes `index/tag-members.json` (tag → members), and renders icons for expanded members. Icon cell size: `-Dfieldguide.iconSize=32` (legacy: `itemIconSize` / `blockItemIconSize` / `fluidIconSize`). Atlas page max edge defaults to **2048** (`-Dfieldguide.itemIconAtlasMaxSize=…`) for WebGL-friendly textures.

`extras/registry-labels/` is **off** by default (`-Dfieldguide.exportRegistryLabels=true` to enable). **`additionalplacements`** is excluded by default. Export mode: `-Dfieldguide.exportMode=closure` (default) or `full` / `-Dfieldguide.fullExport=true`. More flags: `-Dfieldguide.skipItemIconExport=true`, `-Dfieldguide.skipFluidIconExport=true`, `-Dfieldguide.itemIconAtlasMaxSize=4096`, `-Dfieldguide.exportExcludedNamespaces=...`.

## Layout

- `cli/src` — existing Java generator
- `forge/src` — Forge mod (`fieldguide` mod id)
- `custom/`, `assets/` — used by CLI at repo root (unchanged)
