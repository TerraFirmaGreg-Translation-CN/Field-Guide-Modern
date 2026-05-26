package team.terrafirmgreg.fieldguide.export.resources;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.LanguageManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import team.terrafirmgreg.fieldguide.export.FieldGuideExportLanguages;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Dumps resolved in-game display names per registry entry and language.
 *
 * <p>Covers GregTech-style composed names (material + {@code tagprefix} pattern) that only exist
 * after runtime translation, even when the underlying keys live in {@code assets/.../lang/}.</p>
 */
public final class RegistryTranslationExporter {

    private static final Logger LOGGER = LogManager.getLogger("fieldguide");

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private RegistryTranslationExporter() {}

    public record Result(int languages, int itemEntries, int blockEntries, int fluidEntries) {}

    public static Result export(Path outputDir, Minecraft client) {
        Path labelsRoot = outputDir.resolve("extras").resolve("registry-labels");
        LanguageManager languageManager = client.getLanguageManager();
        String previous = languageManager.getSelected();

        int langCount = 0;
        int lastItems = 0;
        int lastBlocks = 0;
        int lastFluids = 0;

        Set<String> languages = resolveLanguages(languageManager);
        LOGGER.info("[registry-labels] exporting {} language(s) (opt-in; fieldguide.exportRegistryLabels=true)",
                languages.size());

        try {
            for (String code : languages) {
                if (!languageManager.getLanguages().containsKey(code)) {
                    LOGGER.warn("[registry-labels] skipping unknown language code: {}", code);
                    continue;
                }
                languageManager.setSelected(code);

                Map<String, Object> langFile = new LinkedHashMap<>();
                Map<String, String> items = collectItems();
                Map<String, String> blocks = collectBlocks();
                Map<String, String> fluids = collectFluids();

                langFile.put("language", code);
                langFile.put("items", items);
                langFile.put("blocks", blocks);
                langFile.put("fluids", fluids);

                try {
                    Files.createDirectories(labelsRoot);
                    Path out = labelsRoot.resolve(code + ".json");
                    Files.writeString(out, GSON.toJson(langFile));
                } catch (IOException e) {
                    LOGGER.error("[registry-labels] failed to write language {}", code, e);
                    continue;
                }

                langCount++;
                lastItems = items.size();
                lastBlocks = blocks.size();
                lastFluids = fluids.size();
                LOGGER.info("[registry-labels] {} — {} items, {} blocks, {} fluids",
                        code, items.size(), blocks.size(), fluids.size());
            }
        } finally {
            languageManager.setSelected(previous);
        }

        return new Result(langCount, lastItems, lastBlocks, lastFluids);
    }

    private static Set<String> resolveLanguages(LanguageManager languageManager) {
        Set<String> configured = FieldGuideExportLanguages.resolve();
        if (configured == null) {
            return languageManager.getLanguages().keySet();
        }
        return configured;
    }

    private static Map<String, String> collectItems() {
        Map<String, String> out = new TreeMap<>();
        for (Item item : BuiltInRegistries.ITEM) {
            if (item == null || item == Items.AIR) {
                continue;
            }
            String id = BuiltInRegistries.ITEM.getKey(item).toString();
            if (id.endsWith(":air")) {
                continue;
            }
            ItemStack stack = new ItemStack(item);
            out.put(id, stack.getHoverName().getString());
        }
        return out;
    }

    private static Map<String, String> collectBlocks() {
        Map<String, String> out = new TreeMap<>();
        for (Block block : BuiltInRegistries.BLOCK) {
            if (block == null || block == Blocks.AIR) {
                continue;
            }
            String id = BuiltInRegistries.BLOCK.getKey(block).toString();
            if (id.endsWith(":air")) {
                continue;
            }
            ItemStack stack = new ItemStack(block);
            out.put(id, stack.getHoverName().getString());
        }
        return out;
    }

    private static Map<String, String> collectFluids() {
        Map<String, String> out = new TreeMap<>();
        for (Fluid fluid : BuiltInRegistries.FLUID) {
            if (fluid == null || fluid.isSame(Fluids.EMPTY)) {
                continue;
            }
            ResourceLocation loc = BuiltInRegistries.FLUID.getKey(fluid);
            String id = loc.toString();
            String label = fluidLabel(fluid, loc);
            out.put(id, label);
        }
        return out;
    }

    private static String fluidLabel(Fluid fluid, ResourceLocation loc) {
        for (Item item : BuiltInRegistries.ITEM) {
            if (item instanceof BucketItem bucket && bucket.getFluid() == fluid) {
                return new ItemStack(item).getHoverName().getString();
            }
        }
        String key = "fluid." + loc.getNamespace() + "." + loc.getPath().replace('/', '.');
        return Component.translatable(key).getString();
    }
}
