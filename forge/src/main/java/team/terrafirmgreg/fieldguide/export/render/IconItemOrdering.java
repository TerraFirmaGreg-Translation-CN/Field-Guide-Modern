package team.terrafirmgreg.fieldguide.export.render;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import team.terrafirmgreg.fieldguide.export.scan.BookScanResult;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Orders item ids for atlas packing so high-traffic icons land on {@code atlas-000.png}.
 */
public final class IconItemOrdering {

    /** Book page mentions (each {@link BookScanResult#addItem} hit). */
    private static final int WEIGHT_BOOK_REF = 100;
    /** Item appears in at least one exported recipe JSON. */
    private static final int WEIGHT_RECIPE_REF = 50;
    /** Item only reached via tag closure expansion. */
    private static final int WEIGHT_TAG_CLOSURE = 1;

    private IconItemOrdering() {}

    public static Map<String, Integer> buildUsageWeights(
            BookScanResult scan,
            Collection<String> recipeReferencedItems,
            Collection<String> tagClosureItems) {
        Map<String, Integer> weights = new HashMap<>();
        if (scan != null) {
            for (Map.Entry<String, Integer> e : scan.getItemReferenceCounts().entrySet()) {
                weights.merge(e.getKey(), e.getValue() * WEIGHT_BOOK_REF, Integer::sum);
            }
        }
        if (recipeReferencedItems != null) {
            for (String id : recipeReferencedItems) {
                weights.merge(id, WEIGHT_RECIPE_REF, Integer::sum);
            }
        }
        if (tagClosureItems != null) {
            for (String id : tagClosureItems) {
                weights.merge(id, WEIGHT_TAG_CLOSURE, Integer::sum);
            }
        }
        return weights;
    }

    public static List<ResourceLocation> orderedForPass(
            Set<String> onlyItemIds,
            Predicate<Item> include,
            Map<String, Integer> usageWeights) {
        List<ResourceLocation> ids = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            if (item == null || item == Items.AIR || !include.test(item)) {
                continue;
            }
            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
            if (itemId == null || !BuiltInRegistries.ITEM.containsKey(itemId)) {
                continue;
            }
            if (onlyItemIds != null && !onlyItemIds.contains(itemId.toString())) {
                continue;
            }
            ids.add(itemId);
        }
        Comparator<ResourceLocation> byUsage = Comparator
                .comparingInt((ResourceLocation id) -> usageWeight(usageWeights, id.toString()))
                .reversed()
                .thenComparing(ResourceLocation::toString);
        ids.sort(byUsage);
        return ids;
    }

    private static int usageWeight(Map<String, Integer> usageWeights, String itemId) {
        if (usageWeights == null || usageWeights.isEmpty()) {
            return 0;
        }
        return usageWeights.getOrDefault(itemId, 0);
    }
}
