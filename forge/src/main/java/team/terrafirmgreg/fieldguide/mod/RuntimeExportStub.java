package team.terrafirmgreg.fieldguide.mod;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import team.terrafirmgreg.fieldguide.export.FieldGuideExportMode;
import team.terrafirmgreg.fieldguide.export.patchouli.Book;
import team.terrafirmgreg.fieldguide.export.patchouli.BookCategory;
import team.terrafirmgreg.fieldguide.export.patchouli.BookEntry;
import team.terrafirmgreg.fieldguide.export.patchouli.PatchouliBookLoader;
import team.terrafirmgreg.fieldguide.export.scan.BlockStateExportMaps;
import team.terrafirmgreg.fieldguide.export.scan.BlockStateResolver;
import team.terrafirmgreg.fieldguide.export.scan.BookScanResult;
import team.terrafirmgreg.fieldguide.export.scan.BookScanner;
import team.terrafirmgreg.fieldguide.export.scan.PatchouliMultiblockExporter;
import team.terrafirmgreg.fieldguide.export.render.IconItemOrdering;
import team.terrafirmgreg.fieldguide.export.render.ItemIconRendererExporter;
import team.terrafirmgreg.fieldguide.export.FieldGuideExportLanguages;
import team.terrafirmgreg.fieldguide.export.render.EmiBundleManifestWriter;
import team.terrafirmgreg.fieldguide.export.render.EmiRecipeLayoutExporter;
import team.terrafirmgreg.fieldguide.export.render.EmiRecipeResolver;
import team.terrafirmgreg.fieldguide.export.render.RecipeReferenceImageExporter;
import team.terrafirmgreg.fieldguide.export.resources.ExportDirectoryStats;
import team.terrafirmgreg.fieldguide.export.resources.ExportLogsCollector;
import team.terrafirmgreg.fieldguide.export.resources.ExportedRecipeLoader;
import team.terrafirmgreg.fieldguide.export.resources.EmiItemsIndexExporter;
import team.terrafirmgreg.fieldguide.export.resources.LangKeyCollector;
import team.terrafirmgreg.fieldguide.export.resources.LangMergerExporter;
import team.terrafirmgreg.fieldguide.export.resources.RecipeBundleExporter;
import team.terrafirmgreg.fieldguide.export.resources.RecipeIndexExporter;
import team.terrafirmgreg.fieldguide.export.resources.RegistryTranslationExporter;
import team.terrafirmgreg.fieldguide.export.resources.ClosureNamespaces;
import team.terrafirmgreg.fieldguide.export.resources.ClosureResourceExporter;
import team.terrafirmgreg.fieldguide.export.resources.RuntimeResourceExporter;
import team.terrafirmgreg.fieldguide.export.resources.TagBundleExporter;
import team.terrafirmgreg.fieldguide.export.resources.TagClosureExpander;
import team.terrafirmgreg.fieldguide.export.resources.TagMembersIndexExporter;
import team.terrafirmgreg.fieldguide.support.FieldGuidePageSupport;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * First-pass runtime export: loads TFC's {@code field_guide} Patchouli book via
 * {@link PatchouliBookLoader} and writes a {@code manifest.json} summary so CI / local runs
 * can verify the loader sees the book.
 *
 * <p>The "stub" name is kept because no rendering happens yet (no HTML / textures / search
 * index); subsequent passes will hang off this same entry point.</p>
 */
public final class RuntimeExportStub {

    private static final Logger LOGGER = LogManager.getLogger("fieldguide");

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private RuntimeExportStub() {}

    public static Component run(Path outputDir) throws IOException {
        Files.createDirectories(outputDir);

        FieldGuideExportMode exportMode = FieldGuideExportMode.current();
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("status", "export");
        manifest.put("exportedAt", Instant.now().toString());
        manifest.put("exportMode", exportMode.name().toLowerCase());

        Minecraft client = Minecraft.getInstance();
        if (client == null) {
            manifest.put("error", "Minecraft.getInstance() returned null");
            return write(outputDir, manifest);
        }

        BookScanResult scanResult = null;
        Book book = null;
        BlockStateResolution blockstates = null;
        List<PatchouliMultiblockExporter.ExportedMultiblock> multiblockDefs = null;
        try {
            book = PatchouliBookLoader.forTfcFieldGuide(client).load();
            manifest.put("book", summarize(book));

            scanResult = BookScanner.scan(book);
            blockstates = resolveBlockstates(scanResult);
            multiblockDefs = PatchouliMultiblockExporter.exportAll(
                    scanResult.getMultiblocks(),
                    client.level,
                    client.getResourceManager(),
                    book.getNamespace(),
                    book.getBookId());

            Map<String, Object> stats = new LinkedHashMap<>(scanResult.toStatsMap());
            stats.put("blockstateResolved", blockstates.resolvedCount);
            stats.put("blockstateEnriched", blockstates.enrichedCount);
            stats.put("blockstateTags", blockstates.tagCount);
            stats.put("blockstateFailed", blockstates.failedCount);
            int mbOk = 0;
            int mbFail = 0;
            for (PatchouliMultiblockExporter.ExportedMultiblock mb : multiblockDefs) {
                if (mb.isOk()) mbOk++;
                else mbFail++;
            }
            stats.put("multiblockResolved", mbOk);
            stats.put("multiblockFailed", mbFail);
            applyMissingStats(stats, collectMissing(blockstates, multiblockDefs));
            manifest.put("stats", stats);
            logScanSummary(book, scanResult, blockstates, multiblockDefs);
        } catch (Throwable t) {
            LOGGER.error("patchouli book load failed", t);
            manifest.put("error", t.getClass().getSimpleName() + ": " + t.getMessage());
        }

        RuntimeResourceExporter.Result fullResources = null;
        ClosureResourceExporter.Result closureResources = null;
        LangMergerExporter.Result langExport = null;
        RecipeBundleExporter.Result recipeBundles = null;
        TagBundleExporter.Result tagBundles = null;
        RegistryTranslationExporter.Result registryLabels = null;
        ItemIconRendererExporter.Result itemIcons = null;
        RecipeReferenceImageExporter.Result recipeReferences = null;
        EmiRecipeLayoutExporter.Result recipeLayouts = null;
        RecipeIndexExporter.Result recipeIndex = null;
        EmiItemsIndexExporter.Result emiItemsIndex = null;
        TagMembersIndexExporter.Result tagMembersIndex = null;
        Set<String> closureItemIds = null;
        Set<String> closureFluidIds = null;
        TagClosureExpander.Expansion tagExpansion = null;

        if (exportMode.isClosure()) {
            LOGGER.info("[export] mode=closure (book-referenced assets, tag expansion, filtered tags/recipes)");
        } else {
            LOGGER.info("[export] mode=full (merged assets/ + data/ trees)");
        }

        try {
            if (!exportMode.isClosure()) {
                fullResources = RuntimeResourceExporter.export(outputDir, client);
            }
        } catch (Throwable t) {
            LOGGER.error("asset/data export failed", t);
            manifest.put("resourceExportError", t.getClass().getSimpleName() + ": " + t.getMessage());
        }
        try {
            var server = client.getSingleplayerServer();
            if (server != null && RecipeBundleExporter.isEnabled()) {
                if (exportMode.isClosure() && scanResult != null) {
                    recipeBundles = RecipeBundleExporter.export(outputDir, server, scanResult.getRecipes());
                    closureItemIds = new TreeSet<>(scanResult.getItems());
                    closureFluidIds = new TreeSet<>();
                    closureItemIds.addAll(recipeBundles.referencedItems());
                    closureFluidIds.addAll(recipeBundles.referencedFluids());

                    Set<String> seedTags = new TreeSet<>(scanResult.getTags());
                    seedTags.addAll(recipeBundles.referencedTags());
                    if (EmiRecipeLayoutExporter.isEnabled() && EmiRecipeResolver.isEmiAvailable()) {
                        try {
                            recipeLayouts = EmiRecipeLayoutExporter.export(
                                    outputDir, client, scanResult.getRecipes());
                            seedTags.addAll(recipeLayouts.referencedTags());
                            closureItemIds.addAll(recipeLayouts.referencedItems());
                            closureFluidIds.addAll(recipeLayouts.referencedFluids());
                            LOGGER.info(
                                    "[emi-layout] pre-closure: {} items, {} fluids, {} tags from EMI layouts",
                                    recipeLayouts.referencedItems().size(),
                                    recipeLayouts.referencedFluids().size(),
                                    recipeLayouts.referencedTags().size());
                        } catch (Exception e) {
                            LOGGER.error("emi recipe layout export failed (pre-closure)", e);
                        }
                    }
                    tagExpansion = TagClosureExpander.expand(server, seedTags);
                    closureItemIds.addAll(tagExpansion.items());
                    closureFluidIds.addAll(tagExpansion.fluids());
                } else {
                    recipeBundles = RecipeBundleExporter.export(outputDir, server);
                }
            } else if (server == null) {
                LOGGER.warn("[recipes] no integrated server — skipping recipe bundles");
            }
        } catch (Throwable t) {
            LOGGER.error("recipe bundle export failed", t);
            manifest.put("recipeBundleExportError", t.getClass().getSimpleName() + ": " + t.getMessage());
        }
        try {
            if (exportMode.isClosure() && scanResult != null && book != null) {
                Set<String> extraBlocks = tagExpansion != null ? tagExpansion.blocks() : Set.of();
                closureResources = ClosureResourceExporter.export(
                        outputDir, client, book, scanResult,
                        blockstates != null ? blockstates.entries : List.of(),
                        multiblockDefs,
                        extraBlocks);
            }
        } catch (Throwable t) {
            LOGGER.error("closure resource export failed", t);
            manifest.put("resourceExportError", t.getClass().getSimpleName() + ": " + t.getMessage());
        }
        try {
            var server = client.getSingleplayerServer();
            if (server != null && TagBundleExporter.isEnabled()) {
                if (exportMode.isClosure() && tagExpansion != null) {
                    tagBundles = TagBundleExporter.export(outputDir, server, tagExpansion.tags());
                } else if (!exportMode.isClosure()) {
                    tagBundles = TagBundleExporter.export(outputDir, server);
                }
            }
        } catch (Throwable t) {
            LOGGER.error("tag bundle export failed", t);
            manifest.put("tagBundleExportError", t.getClass().getSimpleName() + ": " + t.getMessage());
        }
        try {
            var server = client.getSingleplayerServer();
            if (server != null
                    && exportMode.isClosure()
                    && tagExpansion != null
                    && TagMembersIndexExporter.isEnabled()) {
                tagMembersIndex = TagMembersIndexExporter.export(outputDir, server, tagExpansion.tags());
            }
        } catch (Throwable t) {
            LOGGER.error("tag members index export failed", t);
            manifest.put("tagMembersIndexExportError", t.getClass().getSimpleName() + ": " + t.getMessage());
        }
        try {
            if (LangMergerExporter.isEnabled()) {
                if (exportMode.isClosure() && book != null && scanResult != null) {
                    Set<String> langKeys = buildClosureLangKeys(
                            outputDir, book, scanResult, tagExpansion, closureItemIds, closureFluidIds);
                    Set<String> langsNs = ClosureNamespaces.from(
                            book, scanResult,
                            recipeBundles != null ? recipeBundles.referencedItems() : Set.of(),
                            langKeys);
                    langExport = LangMergerExporter.exportHandbookLang(outputDir, client, langsNs, langKeys);
                } else {
                    langExport = LangMergerExporter.exportHandbookLang(outputDir, client, null, null);
                }
            }
        } catch (Throwable t) {
            LOGGER.error("lang merge export failed", t);
            manifest.put("langExportError", t.getClass().getSimpleName() + ": " + t.getMessage());
        }
        if (Boolean.getBoolean("fieldguide.exportRegistryLabels")) {
            try {
                registryLabels = RegistryTranslationExporter.export(outputDir, client);
            } catch (Throwable t) {
                LOGGER.error("registry label export failed", t);
                manifest.put("registryLabelExportError", t.getClass().getSimpleName() + ": " + t.getMessage());
            }
        }
        try {
            if (EmiRecipeLayoutExporter.isEnabled() && scanResult != null && recipeLayouts == null) {
                if (EmiRecipeResolver.isEmiAvailable()) {
                    recipeLayouts = EmiRecipeLayoutExporter.export(outputDir, client, scanResult.getRecipes());
                } else {
                    LOGGER.warn("[emi-layout] EMI not loaded — skipping recipe layout export");
                }
            }
        } catch (Throwable t) {
            LOGGER.error("emi recipe layout export failed", t);
            manifest.put("recipeLayoutExportError", t.getClass().getSimpleName() + ": " + t.getMessage());
        }
        try {
            if (LangMergerExporter.isEnabled()) {
                Map<String, Object> emiLangStats = exportEmiBundleLang(
                        outputDir,
                        client,
                        exportMode,
                        closureItemIds,
                        closureFluidIds,
                        recipeLayouts,
                        tagExpansion);
                if (!emiLangStats.isEmpty()) {
                    manifest.put("emiLang", emiLangStats);
                }
            }
        } catch (Throwable t) {
            LOGGER.error("emi bundle lang export failed", t);
            manifest.put("emiLangExportError", t.getClass().getSimpleName() + ": " + t.getMessage());
        }
        try {
            if (ItemIconRendererExporter.isEnabled()) {
                if (exportMode.isClosure() && closureItemIds != null) {
                    Set<String> recipeIconRefs = new TreeSet<>();
                    if (recipeBundles != null) {
                        recipeIconRefs.addAll(recipeBundles.referencedItems());
                    }
                    if (recipeLayouts != null) {
                        recipeIconRefs.addAll(recipeLayouts.referencedItems());
                    }
                    Map<String, Integer> iconUsage = IconItemOrdering.buildUsageWeights(
                            scanResult,
                            recipeIconRefs,
                            tagExpansion != null ? tagExpansion.items() : Set.of());
                    Set<String> fluidFilter = fluidIconFilter(exportMode, closureFluidIds);
                    Map<String, ItemStack> iconVariants = new LinkedHashMap<>();
                    if (recipeLayouts != null) {
                        iconVariants.putAll(recipeLayouts.iconVariants());
                    }
                    itemIcons = ItemIconRendererExporter.export(
                            outputDir, client, closureItemIds, fluidFilter, iconUsage, iconVariants);
                } else if (exportMode.isClosure()) {
                    LOGGER.warn("[icons] closure without item id set — skipping icons");
                } else {
                    itemIcons = ItemIconRendererExporter.export(outputDir, client);
                }
            } else {
                LOGGER.info("[icons] skipped (fieldguide.skipItemIconExport=true)");
            }
        } catch (Throwable t) {
            LOGGER.error("icon export failed", t);
            manifest.put("iconExportError", t.getClass().getSimpleName() + ": " + t.getMessage());
        }
        try {
            if (RecipeReferenceImageExporter.isEnabled() && scanResult != null) {
                if (RecipeReferenceImageExporter.isEmiAvailable()) {
                    recipeReferences = RecipeReferenceImageExporter.export(outputDir, client, scanResult.getRecipes());
                } else {
                    LOGGER.warn("[recipe-reference] EMI not loaded — skipping reference PNG export");
                }
            }
        } catch (Throwable t) {
            LOGGER.error("recipe reference image export failed", t);
            manifest.put("recipeReferenceExportError", t.getClass().getSimpleName() + ": " + t.getMessage());
        }
        try {
            if (RecipeIndexExporter.isEnabled()) {
                recipeIndex = RecipeIndexExporter.export(outputDir);
            }
        } catch (Throwable t) {
            LOGGER.error("recipe index export failed", t);
            manifest.put("recipeIndexExportError", t.getClass().getSimpleName() + ": " + t.getMessage());
        }
        try {
            if (EmiItemsIndexExporter.isEnabled()) {
                emiItemsIndex = EmiItemsIndexExporter.export(outputDir);
            }
        } catch (Throwable t) {
            LOGGER.error("emi items index export failed", t);
            manifest.put("emiItemsIndexExportError", t.getClass().getSimpleName() + ": " + t.getMessage());
        }

        try {
            ExportDirectoryStats.Summary size = ExportDirectoryStats.summarize(outputDir);
            manifest.put("exportSize", ExportDirectoryStats.toMap(size));
            LOGGER.info("[export] total on disk: {} files, {} bytes under {}",
                    size.fileCount(), size.totalBytes(), outputDir.toAbsolutePath());
        } catch (IOException e) {
            LOGGER.warn("[export] could not summarize export directory size", e);
        }

        if (fullResources != null) {
            Map<String, Object> resourceStats = new LinkedHashMap<>();
            resourceStats.put("assetFiles", fullResources.assetFiles());
            resourceStats.put("dataFiles", fullResources.dataFiles());
            resourceStats.put("assetBytes", fullResources.assetBytes());
            resourceStats.put("dataBytes", fullResources.dataBytes());
            resourceStats.put("assetFailures", fullResources.assetFailures());
            resourceStats.put("dataFailures", fullResources.dataFailures());
            resourceStats.put("serverSkipped", fullResources.serverSkipped());
            manifest.put("resources", resourceStats);
        }
        if (closureResources != null) {
            Map<String, Object> resourceStats = new LinkedHashMap<>();
            resourceStats.put("assetFiles", closureResources.assetFiles());
            resourceStats.put("dataFiles", closureResources.dataFiles());
            resourceStats.put("assetBytes", closureResources.assetBytes());
            resourceStats.put("dataBytes", closureResources.dataBytes());
            resourceStats.put("failures", closureResources.failures());
            resourceStats.put("serverSkipped", closureResources.serverSkipped());
            resourceStats.put("closureSeeded", closureResources.seededLocations());
            resourceStats.put("closureWritten", closureResources.writtenLocations());
            manifest.put("resources", resourceStats);
        }
        if (langExport != null) {
            Map<String, Object> langStats = new LinkedHashMap<>();
            langStats.put("languagesWritten", langExport.languagesWritten());
            langStats.put("bytesWritten", langExport.totalBytes());
            langStats.put("duplicateKeyWarnings", langExport.duplicateKeyWarnings());
            if (langExport.closureKeysRequested() > 0) {
                langStats.put("closureKeysRequested", langExport.closureKeysRequested());
                langStats.put("keysPerLanguage", langExport.keysPerLanguage());
                langStats.put("keysSkippedWhileScanning", langExport.keysSkipped());
            }
            manifest.put("lang", langStats);
        }
        if (recipeBundles != null) {
            Map<String, Object> recipeStats = new LinkedHashMap<>();
            recipeStats.put("namespaces", recipeBundles.namespaces());
            recipeStats.put("recipes", recipeBundles.recipes());
            recipeStats.put("datapackRecipes", recipeBundles.datapackRecipes());
            recipeStats.put("runtimeRecipesCollected", recipeBundles.runtimeRecipes());
            recipeStats.put("runtimeRecipesMerged", recipeBundles.runtimeMerged());
            recipeStats.put("runtimeEncodeFailures", recipeBundles.runtimeEncodeFailures());
            recipeStats.put("referencedItems", recipeBundles.referencedItems().size());
            recipeStats.put("referencedTags", recipeBundles.referencedTags().size());
            recipeStats.put("referencedFluids", recipeBundles.referencedFluids().size());
            recipeStats.put("bytesWritten", recipeBundles.bytesWritten());
            manifest.put("recipeBundles", recipeStats);
        }
        if (tagExpansion != null) {
            Map<String, Object> tagExpStats = new LinkedHashMap<>();
            tagExpStats.put("seedTags", tagExpansion.tags().size());
            tagExpStats.put("expandedItems", tagExpansion.items().size());
            tagExpStats.put("expandedBlocks", tagExpansion.blocks().size());
            tagExpStats.put("expandedFluids", tagExpansion.fluids().size());
            manifest.put("tagExpansion", tagExpStats);
        }
        if (tagBundles != null) {
            Map<String, Object> tagStats = new LinkedHashMap<>();
            tagStats.put("namespaces", tagBundles.namespaces());
            tagStats.put("tagsWritten", tagBundles.tagsWritten());
            tagStats.put("bytesWritten", tagBundles.bytesWritten());
            manifest.put("tagBundles", tagStats);
        }
        if (tagMembersIndex != null) {
            Map<String, Object> tagIndexStats = new LinkedHashMap<>();
            tagIndexStats.put("tagsIndexed", tagMembersIndex.tagsIndexed());
            tagIndexStats.put("itemTagEntries", tagMembersIndex.itemTagEntries());
            tagIndexStats.put("blockTagEntries", tagMembersIndex.blockTagEntries());
            tagIndexStats.put("fluidTagEntries", tagMembersIndex.fluidTagEntries());
            tagIndexStats.put("totalMemberRefs", tagMembersIndex.totalMemberRefs());
            tagIndexStats.put("bytesWritten", tagMembersIndex.indexBytes());
            manifest.put("tagMembersIndex", tagIndexStats);
        }
        if (recipeIndex != null) {
            Map<String, Object> indexStats = new LinkedHashMap<>();
            indexStats.put("outputItems", recipeIndex.outputItems());
            indexStats.put("recipeRefs", recipeIndex.recipeRefs());
            indexStats.put("bytesWritten", recipeIndex.indexBytes());
            manifest.put("recipeIndex", indexStats);
        }
        if (emiItemsIndex != null) {
            Map<String, Object> itemsStats = new LinkedHashMap<>();
            itemsStats.put("itemCount", emiItemsIndex.itemCount());
            itemsStats.put("inputsIndexed", emiItemsIndex.inputsIndexed());
            itemsStats.put("outputsIndexed", emiItemsIndex.outputsIndexed());
            itemsStats.put("bytesWritten", emiItemsIndex.indexBytes());
            manifest.put("emiItemsIndex", itemsStats);
        }
        if (registryLabels != null) {
            Map<String, Object> labelStats = new LinkedHashMap<>();
            labelStats.put("languages", registryLabels.languages());
            labelStats.put("itemEntries", registryLabels.itemEntries());
            labelStats.put("blockEntries", registryLabels.blockEntries());
            labelStats.put("fluidEntries", registryLabels.fluidEntries());
            manifest.put("registryLabels", labelStats);
        }
        if (itemIcons != null) {
            Map<String, Object> iconStats = new LinkedHashMap<>();
            iconStats.put("itemsWritten", itemIcons.itemsWritten());
            iconStats.put("fluidsWritten", itemIcons.fluidsWritten());
            iconStats.put("spritesWritten", itemIcons.totalSpritesWritten());
            iconStats.put("atlasPages", itemIcons.atlasPages());
            iconStats.put("failures", itemIcons.failures());
            iconStats.put("fluidsSkippedNoStack", itemIcons.fluidsSkippedNoStack());
            iconStats.put("atlasPngBytes", itemIcons.atlasPngBytes());
            iconStats.put("indexBytes", itemIcons.indexBytes());
            iconStats.put("cssBytes", itemIcons.cssBytes());
            manifest.put("icons", iconStats);
        }
        if (recipeLayouts != null) {
            Map<String, Object> layoutStats = new LinkedHashMap<>();
            layoutStats.put("requested", recipeLayouts.requested());
            layoutStats.put("written", recipeLayouts.written());
            layoutStats.put("missing", recipeLayouts.missing());
            layoutStats.put("failures", recipeLayouts.failures());
            layoutStats.put("chromeLayers", recipeLayouts.chromeLayers());
            layoutStats.put("chromeDeduped", recipeLayouts.chromeDeduped());
            layoutStats.put("uniqueChromeFiles", recipeLayouts.uniqueChromeFiles());
            layoutStats.put("jsonBytes", recipeLayouts.jsonBytes());
            layoutStats.put("chromeBytes", recipeLayouts.chromeBytes());
            if (recipeLayouts.textures() != null) {
                layoutStats.put("texturesWritten", recipeLayouts.textures().written());
                layoutStats.put("texturesMissing", recipeLayouts.textures().missing());
                layoutStats.put("texturePngBytes", recipeLayouts.textures().pngBytes());
            }
            manifest.put("recipeLayouts", layoutStats);
        }
        if (recipeReferences != null) {
            Map<String, Object> refStats = new LinkedHashMap<>();
            refStats.put("requested", recipeReferences.requested());
            refStats.put("written", recipeReferences.written());
            refStats.put("missing", recipeReferences.missing());
            refStats.put("failures", recipeReferences.failures());
            refStats.put("pngBytes", recipeReferences.pngBytes());
            manifest.put("recipeReferences", refStats);
        }

        try {
            if (ExportLogsCollector.isEnabled()) {
                Path gamedir = client.gameDirectory != null ? client.gameDirectory.toPath() : null;
                Path workspace = gamedir != null ? gamedir.getParent() : null;
                String ws = System.getenv("GITHUB_WORKSPACE");
                if (ws != null && !ws.isBlank()) {
                    workspace = Path.of(ws);
                }
                ExportLogsCollector.Result logs = ExportLogsCollector.collect(outputDir, gamedir, workspace);
                Map<String, Object> logStats = new LinkedHashMap<>();
                logStats.put("filesCopied", logs.filesCopied());
                logStats.put("bytesCopied", logs.bytesCopied());
                manifest.put("exportLogs", logStats);
            }
        } catch (Throwable t) {
            LOGGER.warn("[export-logs] failed", t);
            manifest.put("exportLogsError", t.getClass().getSimpleName() + ": " + t.getMessage());
        }

        Component result = write(outputDir, manifest);
        if (scanResult != null) {
            writeMeta(outputDir, scanResult, blockstates, multiblockDefs, fullResources, closureResources, registryLabels, itemIcons);
        }
        return result;
    }

    private static BlockStateResolution resolveBlockstates(BookScanResult scan) {
        BlockStateResolution res = new BlockStateResolution();
        for (String ref : scan.getBlockstateRefs()) {
            BlockStateResolver.Resolved r = BlockStateResolver.resolve(ref);
            res.entries.add(r);
            if ("tag".equals(r.kind)) {
                if (r.error == null) res.tagCount++;
                else res.failedCount++;
            } else if (r.isOk()) {
                res.resolvedCount++;
                if (r.hasOverride()) {
                    res.enrichedCount++;
                }
            } else {
                res.failedCount++;
            }
        }
        return res;
    }

    private static final class BlockStateResolution {
        final List<BlockStateResolver.Resolved> entries = new ArrayList<>();
        int resolvedCount;
        int enrichedCount;
        int tagCount;
        int failedCount;
    }

    private static void logScanSummary(
            Book book,
            BookScanResult scan,
            BlockStateResolution blockstates,
            List<PatchouliMultiblockExporter.ExportedMultiblock> multiblockDefs) {
        LOGGER.info("[scan] {}:{} ({}): {} pages | {} recipes | {} items | {} tags | {} textures | {} entities | {} multiblocks | {} blockstateRefs",
                book.getNamespace(), book.getBookId(), book.getLanguage(),
                scan.getPageCount(), scan.getRecipes().size(),
                scan.getItems().size(), scan.getTags().size(), scan.getTextures().size(),
                scan.getEntities().size(), scan.getMultiblocks().size(),
                scan.getBlockstateRefs().size());
        if (scan.getPagesWithoutRaw() > 0) {
            LOGGER.warn("[scan] {} page(s) had no raw JSON attached — references on those pages are NOT in meta.json",
                    scan.getPagesWithoutRaw());
        }
        for (Map.Entry<String, Integer> entry : scan.getPagesByType().entrySet()) {
            LOGGER.info("[scan]   {} = {}", entry.getKey(), entry.getValue());
        }
        if (blockstates != null) {
            LOGGER.info("[scan] blockstates: {} resolved, {} with override, {} tags, {} failed",
                    blockstates.resolvedCount, blockstates.enrichedCount,
                    blockstates.tagCount, blockstates.failedCount);
            for (BlockStateResolver.Resolved r : blockstates.entries) {
                if (!r.isOk() && !"tag".equals(r.kind)) {
                    LOGGER.warn("[scan]   blockstate {} -> {}", r.ref, r.error);
                } else if (r.unknownProperties != null || r.invalidProperties != null) {
                    LOGGER.warn("[scan]   blockstate {} had bad properties: unknown={} invalid={}",
                            r.ref, r.unknownProperties, r.invalidProperties);
                } else if (r.hasOverride()) {
                    LOGGER.info("[scan]   blockstate {} -> override {}", r.ref, r.override);
                }
            }
        }
        if (multiblockDefs != null) {
            int ok = 0;
            int fail = 0;
            for (PatchouliMultiblockExporter.ExportedMultiblock mb : multiblockDefs) {
                if (mb.isOk()) ok++;
                else fail++;
            }
            LOGGER.info("[scan] multiblock defs: {} resolved, {} failed (from Patchouli registry)", ok, fail);
            for (PatchouliMultiblockExporter.ExportedMultiblock mb : multiblockDefs) {
                if (!mb.isOk()) {
                    LOGGER.warn("[scan]   multiblock {} -> {} ({})", mb.id, mb.error, mb.source);
                }
            }
        }
        Map<String, Object> missing = collectMissing(blockstates, multiblockDefs);
        if (!missing.isEmpty()) {
            @SuppressWarnings("unchecked")
            Map<String, Object> ms = (Map<String, Object>) missing.get("stats");
            LOGGER.warn("[scan] missing: {} total ({} blockstates, {} multiblocks)",
                    ms.get("total"), ms.get("blockstates"), ms.get("multiblocks"));
        }
    }

    private static Map<String, Object> summarize(Book book) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("namespace", book.getNamespace());
        summary.put("bookId", book.getBookId());
        summary.put("language", book.getLanguage());
        summary.put("name", book.getName());
        summary.put("subtitle", book.getSubtitle());
        summary.put("landingText", book.getLandingText());
        summary.put("source", book.getAssetSource().sourceId());
        summary.put("categoryCount", book.getCategories().size());
        summary.put("entryCount", book.getEntries().size());

        List<Map<String, Object>> categories = new ArrayList<>();
        for (BookCategory cat : book.getCategories()) {
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("id", cat.getId());
            c.put("name", cat.getName());
            c.put("sort", cat.getSort());
            c.put("source", cat.getAssetSource().sourceId());
            c.put("entryCount", cat.getEntries().size());
            List<Map<String, Object>> entries = new ArrayList<>();
            for (BookEntry e : cat.getEntries()) {
                Map<String, Object> em = new LinkedHashMap<>();
                em.put("id", e.getId());
                em.put("name", e.getName());
                em.put("pageCount", e.getPages().size());
                em.put("source", e.getAssetSource().sourceId());
                entries.add(em);
            }
            c.put("entries", entries);
            categories.add(c);
        }
        summary.put("categories", categories);
        return summary;
    }

    /**
     * Entries that could not be resolved at runtime (unknown block, not in Patchouli registry, …).
     * Kept separate from {@code blockstates} / {@code multiblockDefs} so CI and CLI can fail fast.
     */
    private static Map<String, Object> collectMissing(
            BlockStateResolution blockstates,
            List<PatchouliMultiblockExporter.ExportedMultiblock> multiblockDefs) {
        List<Map<String, Object>> missingBlockstates = new ArrayList<>();
        List<Map<String, Object>> missingMultiblocks = new ArrayList<>();

        if (blockstates != null) {
            for (BlockStateResolver.Resolved r : blockstates.entries) {
                if (r.isOk()) {
                    continue;
                }
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("ref", r.ref);
                m.put("error", r.error != null ? r.error : "unknown");
                if (r.kind != null) {
                    m.put("kind", r.kind);
                }
                missingBlockstates.add(m);
            }
        }
        if (multiblockDefs != null) {
            for (PatchouliMultiblockExporter.ExportedMultiblock mb : multiblockDefs) {
                if (mb.isOk()) {
                    continue;
                }
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", mb.id);
                m.put("error", mb.error != null ? mb.error : "unknown");
                if (mb.source != null) {
                    m.put("source", mb.source);
                }
                missingMultiblocks.add(m);
            }
        }

        if (missingBlockstates.isEmpty() && missingMultiblocks.isEmpty()) {
            return Map.of();
        }

        Map<String, Object> out = new LinkedHashMap<>();
        Map<String, Object> missingStats = new LinkedHashMap<>();
        missingStats.put("blockstates", missingBlockstates.size());
        missingStats.put("multiblocks", missingMultiblocks.size());
        missingStats.put("total", missingBlockstates.size() + missingMultiblocks.size());
        out.put("stats", missingStats);
        if (!missingBlockstates.isEmpty()) {
            out.put("blockstates", missingBlockstates);
        }
        if (!missingMultiblocks.isEmpty()) {
            out.put("multiblocks", missingMultiblocks);
        }
        return out;
    }

    private static void applyMissingStats(Map<String, Object> stats, Map<String, Object> missing) {
        if (missing.isEmpty()) {
            return;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> missingStats = (Map<String, Object>) missing.get("stats");
        stats.put("missingBlockstates", missingStats.get("blockstates"));
        stats.put("missingMultiblocks", missingStats.get("multiblocks"));
        stats.put("missingTotal", missingStats.get("total"));
    }

    private static Map<String, Object> exportEmiBundleLang(
            Path outputDir,
            Minecraft client,
            FieldGuideExportMode exportMode,
            Set<String> closureItemIds,
            Set<String> closureFluidIds,
            EmiRecipeLayoutExporter.Result recipeLayouts,
            TagClosureExpander.Expansion tagExpansion) throws IOException {
        Set<String> languages = FieldGuideExportLanguages.resolve();
        if (languages == null) {
            languages = new LinkedHashSet<>(FieldGuideExportLanguages.SUPPORTED);
        }

        Set<String> emiKeys = null;
        if (exportMode.isClosure()) {
            Set<String> items = new TreeSet<>();
            if (closureItemIds != null) {
                items.addAll(closureItemIds);
            }
            Set<String> fluids = new TreeSet<>();
            if (closureFluidIds != null) {
                fluids.addAll(closureFluidIds);
            }
            if (tagExpansion != null) {
                items.addAll(tagExpansion.items());
                fluids.addAll(tagExpansion.fluids());
            }
            Set<String> tags = new TreeSet<>();
            if (recipeLayouts != null) {
                tags.addAll(recipeLayouts.referencedTags());
            }
            if (tagExpansion != null) {
                tags.addAll(tagExpansion.tags());
            }
            emiKeys = LangKeyCollector.collectEmiRegistry(items, fluids, tags);
        }

        LangMergerExporter.Result emiLang = LangMergerExporter.exportEmiLang(outputDir, client, emiKeys);

        int recipeCount = recipeLayouts != null ? recipeLayouts.written() : 0;
        EmiBundleManifestWriter.write(
                outputDir,
                List.copyOf(languages),
                EmiRecipeLayoutExporter.layoutScale(),
                recipeCount);

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("languagesWritten", emiLang.languagesWritten());
        stats.put("bytesWritten", emiLang.totalBytes());
        stats.put("keysPerLanguage", emiLang.keysPerLanguage());
        stats.put("languages", languages);
        if (emiKeys != null) {
            stats.put("emiKeysRequested", emiKeys.size());
        }
        return stats;
    }

    private static Set<String> buildClosureLangKeys(
            Path outputDir,
            Book book,
            BookScanResult scan,
            TagClosureExpander.Expansion tagExpansion,
            Set<String> items,
            Set<String> fluids) throws IOException {
        Set<String> allItems = new TreeSet<>();
        if (items != null) {
            allItems.addAll(items);
        }
        Set<String> blocks = new TreeSet<>();
        Set<String> allFluids = new TreeSet<>();
        if (fluids != null) {
            allFluids.addAll(fluids);
        }
        if (tagExpansion != null) {
            allItems.addAll(tagExpansion.items());
            blocks.addAll(tagExpansion.blocks());
            allFluids.addAll(tagExpansion.fluids());
        }
        var recipeJson = ExportedRecipeLoader.loadAll(outputDir);
        return LangKeyCollector.collect(book, scan, allItems, blocks, allFluids, recipeJson);
    }

    private static Component write(Path outputDir, Map<String, Object> manifest) throws IOException {
        Path manifestFile = outputDir.resolve("manifest.json");
        Files.writeString(manifestFile, GSON.toJson(manifest));
        return Component.literal("[fieldguide] Export wrote " + manifestFile.toAbsolutePath());
    }

    /**
     * Writes the full reference inventory ({@code meta.json}) alongside the manifest summary.
     * Kept as a separate file because it can grow to many KBs once the modpack stabilizes,
     * and CI / chat output only need the counts in {@code manifest.json.stats}.
     */
    private static void writeMeta(
            Path outputDir,
            BookScanResult scan,
            BlockStateResolution blockstates,
            List<PatchouliMultiblockExporter.ExportedMultiblock> multiblockDefs,
            RuntimeResourceExporter.Result fullResources,
            ClosureResourceExporter.Result closureResources,
            RegistryTranslationExporter.Result registryLabels,
            ItemIconRendererExporter.Result itemIcons) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("schemaVersion", "1.3");
        meta.put("scannedAt", Instant.now().toString());

        Map<String, Object> stats = new LinkedHashMap<>(scan.toStatsMap());
        if (blockstates != null) {
            stats.put("blockstateResolved", blockstates.resolvedCount);
            stats.put("blockstateEnriched", blockstates.enrichedCount);
            stats.put("blockstateTags", blockstates.tagCount);
            stats.put("blockstateFailed", blockstates.failedCount);
        }
        if (multiblockDefs != null) {
            int mbOk = 0;
            int mbFail = 0;
            for (PatchouliMultiblockExporter.ExportedMultiblock mb : multiblockDefs) {
                if (mb.isOk()) mbOk++;
                else mbFail++;
            }
            stats.put("multiblockResolved", mbOk);
            stats.put("multiblockFailed", mbFail);
        }
        if (fullResources != null) {
            stats.put("assetFiles", fullResources.assetFiles());
            stats.put("dataFiles", fullResources.dataFiles());
            stats.put("assetBytes", fullResources.assetBytes());
            stats.put("dataBytes", fullResources.dataBytes());
            stats.put("assetExportFailures", fullResources.assetFailures());
            stats.put("dataExportFailures", fullResources.dataFailures());
            stats.put("dataExportSkipped", fullResources.serverSkipped());
        }
        if (closureResources != null) {
            stats.put("assetFiles", closureResources.assetFiles());
            stats.put("dataFiles", closureResources.dataFiles());
            stats.put("assetBytes", closureResources.assetBytes());
            stats.put("dataBytes", closureResources.dataBytes());
            stats.put("closureSeeded", closureResources.seededLocations());
            stats.put("closureWritten", closureResources.writtenLocations());
            stats.put("dataExportSkipped", closureResources.serverSkipped());
        }
        if (registryLabels != null) {
            stats.put("registryLabelLanguages", registryLabels.languages());
            stats.put("registryLabelItems", registryLabels.itemEntries());
            stats.put("registryLabelBlocks", registryLabels.blockEntries());
            stats.put("registryLabelFluids", registryLabels.fluidEntries());
        }
        if (itemIcons != null) {
            stats.put("iconsWritten", itemIcons.totalSpritesWritten());
            stats.put("itemIconsWritten", itemIcons.itemsWritten());
            stats.put("fluidIconsWritten", itemIcons.fluidsWritten());
            stats.put("iconAtlasPages", itemIcons.atlasPages());
            stats.put("iconFailures", itemIcons.failures());
            stats.put("iconAtlasBytes", itemIcons.atlasPngBytes());
        }
        meta.put("stats", stats);
        meta.put("pageTypeSupport", buildPageTypeSupport(scan));

        Map<String, Object> refs = new LinkedHashMap<>();
        refs.put("recipes", scan.getRecipes());
        refs.put("recipesByPageType", scan.getRecipesByPageType());
        refs.put("items", scan.getItems());
        refs.put("tags", scan.getTags());
        refs.put("textures", scan.getTextures());
        refs.put("entities", scan.getEntities());
        refs.put("multiblocks", scan.getMultiblocks());
        refs.put("models", scan.getModels());
        refs.put("blockstateRefs", scan.getBlockstateRefs());
        meta.put("refs", refs);

        if (blockstates != null) {
            List<Map<String, Object>> bsList = new ArrayList<>();
            for (BlockStateResolver.Resolved r : blockstates.entries) {
                bsList.add(BlockStateExportMaps.toMap(r));
            }
            meta.put("blockstates", bsList);
        }

        if (multiblockDefs != null && !multiblockDefs.isEmpty()) {
            List<Map<String, Object>> mbList = new ArrayList<>();
            for (PatchouliMultiblockExporter.ExportedMultiblock mb : multiblockDefs) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", mb.id);
                if (mb.source != null) m.put("source", mb.source);
                if (mb.error != null) m.put("error", mb.error);
                if (!mb.mapping.isEmpty()) m.put("mapping", mb.mapping);
                if (!mb.blockstates.isEmpty()) m.put("blockstates", mb.blockstates);
                mbList.add(m);
            }
            meta.put("multiblockDefs", mbList);
        }

        Map<String, Object> missing = collectMissing(blockstates, multiblockDefs);
        if (!missing.isEmpty()) {
            meta.put("missing", missing);
            applyMissingStats(stats, missing);
        }

        Path metaFile = outputDir.resolve("meta.json");
        try {
            Files.writeString(metaFile, GSON.toJson(meta));
            LOGGER.info("wrote {}", metaFile.toAbsolutePath());
        } catch (IOException e) {
            LOGGER.error("failed to write {}", metaFile.toAbsolutePath(), e);
        }
    }

    /**
     * Cross-checks scanned {@code pagesByType} counts against the declared support catalog in core.
     */
    private static Map<String, Object> buildPageTypeSupport(BookScanResult scan) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("catalog", FieldGuidePageSupport.exportCatalog());

        Map<String, Integer> seen = scan.getPagesByType();
        List<Map<String, Object>> inBook = new ArrayList<>();
        for (Map.Entry<String, Integer> e : seen.entrySet()) {
            if (!e.getKey().startsWith("tfc:")) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("type", e.getKey());
            row.put("count", e.getValue());
            row.put("tier", FieldGuidePageSupport.tierOf(e.getKey()).name().toLowerCase());
            inBook.add(row);
        }
        out.put("tfcPagesInBook", inBook);
        return out;
    }

    /**
     * {@code null} = all fluids (full export); empty = skip; non-empty = closure filter.
     */
    private static Set<String> fluidIconFilter(FieldGuideExportMode exportMode, Set<String> closureFluidIds) {
        if (!ItemIconRendererExporter.fluidsEnabled()) {
            return Set.of();
        }
        if (exportMode.isClosure()) {
            return closureFluidIds != null && !closureFluidIds.isEmpty() ? closureFluidIds : Set.of();
        }
        return null;
    }
}
