package team.terrafirmgreg.fieldguide.export.render;

import com.google.common.io.MoreFiles;
import com.google.common.io.RecursiveDeleteOption;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import team.terrafirmgreg.fieldguide.generated.EmiBundlePaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Exports item and fluid icons into a single atlas under {@code emi/icons/}
 * (default 32×32, {@code icons.css} + {@code index.json}).
 */
public final class ItemIconRendererExporter {

    private static final Logger LOGGER = LogManager.getLogger("fieldguide");

    private static final int LOG_EVERY = 500;

    private ItemIconRendererExporter() {}

    public record Result(
            int itemsWritten,
            int fluidsWritten,
            int atlasPages,
            int itemFailures,
            int fluidFailures,
            int fluidsSkippedNoStack,
            long atlasPngBytes,
            long indexBytes,
            long cssBytes) {

        public int totalSpritesWritten() {
            return itemsWritten + fluidsWritten;
        }

        public int failures() {
            return itemFailures + fluidFailures;
        }
    }

    public static boolean isEnabled() {
        return !Boolean.getBoolean("fieldguide.skipItemIconExport");
    }

    public static boolean fluidsEnabled() {
        return !Boolean.getBoolean("fieldguide.skipFluidIconExport");
    }

    public static Result export(Path outputDir, Minecraft client) {
        return export(outputDir, client, null, null, null);
    }

    /** @param onlyItemIds when non-null, only these {@code namespace:path} registry ids (closure). */
    public static Result export(Path outputDir, Minecraft client, Set<String> onlyItemIds) {
        return export(outputDir, client, onlyItemIds, null, null);
    }

    public static Result export(
            Path outputDir,
            Minecraft client,
            Set<String> onlyItemIds,
            Map<String, Integer> usageWeights) {
        return export(outputDir, client, onlyItemIds, null, usageWeights);
    }

    /**
     * @param onlyFluidIds {@code null} = all fluids (full export); empty set = skip fluids;
     *                     non-empty = closure filter
     * @param usageWeights when non-null, sprites are packed by descending weight
     */
    public static Result export(
            Path outputDir,
            Minecraft client,
            Set<String> onlyItemIds,
            Set<String> onlyFluidIds,
            Map<String, Integer> usageWeights) {
        return export(outputDir, client, onlyItemIds, onlyFluidIds, usageWeights, Map.of());
    }

    /**
     * @param iconVariants NBT item stacks keyed by {@link IconStackKey} (rendered after plain items)
     */
    public static Result export(
            Path outputDir,
            Minecraft client,
            Set<String> onlyItemIds,
            Set<String> onlyFluidIds,
            Map<String, Integer> usageWeights,
            Map<String, ItemStack> iconVariants) {
        Path iconsRoot = EmiBundlePaths.resolve(outputDir, EmiBundlePaths.ICONS_DIR);
        clearLegacyDirs(outputDir);
        clearDir(iconsRoot);

        int cell = IconExportSizes.iconCellSize();
        int atlasMax = IconExportSizes.atlasMaxSize();

        boolean exportFluids =
                fluidsEnabled() && (onlyFluidIds == null || !onlyFluidIds.isEmpty());

        List<ResourceLocation> itemOrder =
                IconItemOrdering.orderedForPass(onlyItemIds, item -> true, usageWeights);
        List<String> fluidOrder = exportFluids ? orderedFluidIds(onlyFluidIds) : List.of();
        Map<String, ItemStack> variants = iconVariants != null ? iconVariants : Map.of();

        if (onlyItemIds != null) {
            LOGGER.info("[icons] closure: {} items, {} fluids, {} nbt variants at {}px → {}",
                    onlyItemIds.size(), fluidOrder.size(), variants.size(), cell, iconsRoot);
        } else {
            int totalItems = 0;
            for (Item ignored : BuiltInRegistries.ITEM) {
                totalItems++;
            }
            LOGGER.info("[icons] full export: {} registry items, {} fluids at {}px (max atlas {}px) → {}",
                    totalItems, fluidOrder.size(), cell, atlasMax, iconsRoot);
            logRegistryItemCountsByNamespace();
        }

        int totalSprites = itemOrder.size() + fluidOrder.size() + variants.size() + 1;
        List<IconAtlasLayout.PagePlan> layout = IconAtlasLayout.plan(totalSprites, cell, atlasMax);
        if (!layout.isEmpty()) {
            IconAtlasLayout.PagePlan first = layout.get(0);
            LOGGER.info("[icons] {} sprites, planned {} page(s), first page {}×{} cells ({}×{}px)",
                    totalSprites, layout.size(), first.cols(), first.rows(),
                    first.widthPx(cell), first.heightPx(cell));
        }

        int itemsPlaced = 0;
        int itemFailures = 0;
        int fluidsPlaced = 0;
        int fluidFailures = 0;
        int fluidsSkipped = 0;

        int variantsPlaced = 0;
        int variantFailures = 0;

        ItemIconAtlasBuilder.AtlasResult atlasResult;
        try (var renderer = new OffScreenRenderer(cell, cell);
                var atlas = new ItemIconAtlasBuilder(iconsRoot, cell, atlasMax, "icon", layout, usageWeights)) {
            var guiGraphics = new GuiGraphics(client, client.renderBuffers().bufferSource());
            IconPlaceholderRenderer.render(client, guiGraphics, renderer);
            atlas.place(IconPlaceholderRenderer.REGISTRY_ID, renderer);

            renderer.setupItemRendering();

            int index = 0;
            int itemTotal = itemOrder.size();
            for (ResourceLocation itemId : itemOrder) {
                index++;
                Item item = BuiltInRegistries.ITEM.get(itemId);
                if (item == null || item == Items.AIR) {
                    continue;
                }

                try {
                    renderItemIcon(client, guiGraphics, renderer, item);
                    atlas.place(itemId.toString(), renderer);
                    itemsPlaced++;
                    if (index % LOG_EVERY == 0) {
                        LOGGER.info("[icons] items: progress {}/{} ({} ok, {} fail)",
                                index, itemTotal, itemsPlaced, itemFailures);
                    }
                } catch (Exception e) {
                    itemFailures++;
                    if (itemFailures <= 20) {
                        LOGGER.warn("[icons] item failed {} ({}/{}): {}",
                                itemId, index, itemTotal, failureSummary(e), e);
                    }
                }
            }

            int fluidIndex = 0;
            int fluidTotal = fluidOrder.size();
            renderer.setupFlatGuiRendering();
            for (String fluidIdStr : fluidOrder) {
                fluidIndex++;
                Fluid fluid = BuiltInRegistries.FLUID.get(ResourceLocation.parse(fluidIdStr));
                if (fluid == null || fluid.isSame(Fluids.EMPTY)) {
                    continue;
                }

                try {
                    if (!FluidStillIconRenderer.render(client, guiGraphics, renderer, fluid)) {
                        fluidsSkipped++;
                        continue;
                    }
                    atlas.place(fluidIdStr, renderer);
                    fluidsPlaced++;
                } catch (Exception e) {
                    fluidFailures++;
                    if (fluidFailures <= 20) {
                        LOGGER.warn("[icons] fluid failed {}: {}", fluidIdStr, e.getMessage());
                    }
                }
            }

            if (!variants.isEmpty()) {
                renderer.setupItemRendering();
                int variantIndex = 0;
                int variantTotal = variants.size();
                for (var entry : variants.entrySet()) {
                    variantIndex++;
                    try {
                        renderItemStackIcon(client, guiGraphics, renderer, entry.getValue());
                        atlas.place(entry.getKey(), renderer);
                        variantsPlaced++;
                        if (variantIndex % LOG_EVERY == 0) {
                            LOGGER.info("[icons] nbt variants: progress {}/{} ({} ok, {} fail)",
                                    variantIndex, variantTotal, variantsPlaced, variantFailures);
                        }
                    } catch (Exception e) {
                        variantFailures++;
                        if (variantFailures <= 20) {
                            LOGGER.warn("[icons] nbt variant failed {} ({}/{}): {}",
                                    entry.getKey(), variantIndex, variantTotal, failureSummary(e), e);
                        }
                    }
                }
            }

            atlasResult = atlas.finish();
        } catch (IOException e) {
            throw new RuntimeException("icon atlas export failed", e);
        }

        LOGGER.info("[icons] done: {} items, {} nbt variants, {} fluids ({} skipped no still), {} pages, {} failures at {}",
                itemsPlaced, variantsPlaced, fluidsPlaced, fluidsSkipped, atlasResult.pageCount(),
                itemFailures + fluidFailures + variantFailures, iconsRoot);

        return new Result(
                itemsPlaced + variantsPlaced,
                fluidsPlaced,
                atlasResult.pageCount(),
                itemFailures + variantFailures,
                fluidFailures,
                fluidsSkipped,
                atlasResult.pngBytes(),
                atlasResult.indexBytes(),
                atlasResult.cssBytes());
    }

    private static List<String> orderedFluidIds(Set<String> onlyFluidIds) {
        if (onlyFluidIds != null && onlyFluidIds.isEmpty()) {
            return List.of();
        }
        TreeSet<String> ids = new TreeSet<>();
        for (Fluid fluid : BuiltInRegistries.FLUID) {
            if (fluid == null || fluid.isSame(Fluids.EMPTY)) {
                continue;
            }
            ResourceLocation fluidId = BuiltInRegistries.FLUID.getKey(fluid);
            if (fluidId == null) {
                continue;
            }
            String idStr = fluidId.toString();
            if (onlyFluidIds == null || onlyFluidIds.contains(idStr)) {
                ids.add(idStr);
            }
        }
        return new ArrayList<>(ids);
    }

    private static void clearLegacyDirs(Path outputDir) {
        Path generated = outputDir.resolve("generated");
        for (String legacy : new String[] {"items", "block-items", "fluids"}) {
            clearDir(generated.resolve(legacy));
        }
    }

    private static void clearDir(Path dir) {
        if (!Files.exists(dir)) {
            return;
        }
        try {
            MoreFiles.deleteRecursively(dir, RecursiveDeleteOption.ALLOW_INSECURE);
        } catch (IOException e) {
            LOGGER.warn("[icons] could not clear {}: {}", dir, e.getMessage());
        }
    }

    private static void logRegistryItemCountsByNamespace() {
        Map<String, Integer> byNs = new LinkedHashMap<>();
        int blockItems = 0;
        for (Item item : BuiltInRegistries.ITEM) {
            if (item == null || item == Items.AIR) {
                continue;
            }
            if (item instanceof net.minecraft.world.item.BlockItem) {
                blockItems++;
            }
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
            if (id == null) {
                continue;
            }
            byNs.merge(id.getNamespace(), 1, Integer::sum);
        }
        LOGGER.info("[icons]   block-items in registry: {}", blockItems);
        for (String ns : new String[] {"tfg", "gtceu", "kubejs", "minecraft"}) {
            LOGGER.info("[icons]   {}: {} items in registry", ns, byNs.getOrDefault(ns, 0));
        }
    }

    private static void renderItemStackIcon(
            Minecraft client,
            GuiGraphics guiGraphics,
            OffScreenRenderer renderer,
            ItemStack stack) {
        var sprites = collectSprites(client, stack);
        if (renderer.isAnimated(sprites)) {
            renderer.uploadAnimatedFirstFrame(sprites);
        }
        Runnable draw = () -> {
            guiGraphics.renderItem(stack, 0, 0);
            guiGraphics.renderItemDecorations(client.font, stack, 0, 0, "");
        };
        renderer.captureAsPng(draw);
    }

    private static void renderItemIcon(
            Minecraft client,
            GuiGraphics guiGraphics,
            OffScreenRenderer renderer,
            Item item) {
        ItemStack stack = new ItemStack(item);
        var sprites = collectSprites(client, item);
        if (renderer.isAnimated(sprites)) {
            renderer.uploadAnimatedFirstFrame(sprites);
        }
        Runnable draw = () -> {
            guiGraphics.renderItem(stack, 0, 0);
            guiGraphics.renderItemDecorations(client.font, stack, 0, 0, "");
        };
        renderer.captureAsPng(draw);
    }

    private static Set<TextureAtlasSprite> collectSprites(Minecraft client, ItemStack stack) {
        BakedModel model = client.getItemRenderer().getModel(stack, null, null, 0);
        return guessSprites(Set.of(model));
    }

    private static Set<TextureAtlasSprite> collectSprites(Minecraft client, Item item) {
        return collectSprites(client, new ItemStack(item));
    }

    private static String failureSummary(Throwable e) {
        String msg = e.getMessage();
        if (msg != null && !msg.isBlank()) {
            return e.getClass().getSimpleName() + ": " + msg;
        }
        return e.getClass().getSimpleName();
    }

    private static Set<TextureAtlasSprite> guessSprites(Collection<BakedModel> models) {
        var result = Collections.newSetFromMap(new IdentityHashMap<TextureAtlasSprite, Boolean>());
        var random = RandomSource.create(0);
        for (var model : models) {
            for (var quad : model.getQuads(null, null, random)) {
                result.add(quad.getSprite());
            }
        }
        return result;
    }
}
