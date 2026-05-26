package team.terrafirmgreg.fieldguide.export.resources;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Expands item/block/fluid tag ids (as in recipes and Patchouli pages) to concrete registry ids
 * Member order in {@link #expandTagMembers} follows live registry tag holder iteration
 * (aligned with EMI {@code EmiUtil.values} / tag JSON load order), not alphabetical sort.
 */
public final class TagClosureExpander {

    private static final Logger LOGGER = LogManager.getLogger("fieldguide");

    private TagClosureExpander() {}

    public record Expansion(
            Set<String> items,
            Set<String> blocks,
            Set<String> fluids,
            /** Normalized tag ids {@code namespace:path} that were expanded (for filtered tag export). */
            Set<String> tags) {}

    /** Fully expanded registry members for a single tag id (recursive, per registry kind). */
    public record TagMembers(Set<String> items, Set<String> blocks, Set<String> fluids) {}

    /**
     * Expands one {@code namespace:path} tag against item, block, and fluid registries independently.
     * Member order follows live registry tag holder iteration (same source as EMI {@code EmiUtil.values}).
     */
    public static TagMembers expandTagMembers(MinecraftServer server, String tagRef) {
        ResourceLocation tagId = parseTagId(tagRef);
        if (tagId == null) {
            return new TagMembers(Set.of(), Set.of(), Set.of());
        }
        var access = server.registryAccess();
        Registry<Item> items = access.registryOrThrow(Registries.ITEM);
        Registry<Block> blocks = access.registryOrThrow(Registries.BLOCK);
        Registry<Fluid> fluids = access.registryOrThrow(Registries.FLUID);

        Set<String> outItems = new LinkedHashSet<>();
        Set<String> outBlocks = new LinkedHashSet<>();
        Set<String> outFluids = new LinkedHashSet<>();
        expandItemTag(items, tagId, new HashSet<>(), outItems, null);
        expandBlockTag(blocks, tagId, new HashSet<>(), outBlocks, null);
        expandFluidTag(fluids, tagId, new HashSet<>(), outFluids, null);
        return new TagMembers(outItems, outBlocks, outFluids);
    }

    public static Expansion expand(MinecraftServer server, Set<String> seedTagRefs) {
        var access = server.registryAccess();
        Registry<Item> items = access.registryOrThrow(Registries.ITEM);
        Registry<Block> blocks = access.registryOrThrow(Registries.BLOCK);
        Registry<Fluid> fluids = access.registryOrThrow(Registries.FLUID);

        Set<String> outItems = new TreeSet<>();
        Set<String> outBlocks = new TreeSet<>();
        Set<String> outFluids = new TreeSet<>();
        Set<String> outTags = new TreeSet<>();
        Set<ResourceLocation> visitedItemTags = new HashSet<>();
        Set<ResourceLocation> visitedBlockTags = new HashSet<>();
        Set<ResourceLocation> visitedFluidTags = new HashSet<>();

        for (String raw : seedTagRefs) {
            ResourceLocation tagId = parseTagId(raw);
            if (tagId == null) {
                continue;
            }
            expandItemTag(items, tagId, visitedItemTags, outItems, outTags);
            expandBlockTag(blocks, tagId, visitedBlockTags, outBlocks, outTags);
            expandFluidTag(fluids, tagId, visitedFluidTags, outFluids, outTags);
        }

        LOGGER.info("[tags] closure expand: {} seed tags → {} items, {} blocks, {} fluids ({} tag keys)",
                seedTagRefs.size(), outItems.size(), outBlocks.size(), outFluids.size(), outTags.size());

        return new Expansion(outItems, outBlocks, outFluids, outTags);
    }

    private static void expandItemTag(
            Registry<Item> registry,
            ResourceLocation tagId,
            Set<ResourceLocation> visited,
            Set<String> outItems,
            Set<String> outTags) {
        if (!visited.add(tagId)) {
            return;
        }
        if (outTags != null) {
            outTags.add(tagId.toString());
        }
        TagKey<Item> key = TagKey.create(Registries.ITEM, tagId);
        registry.getTag(key).ifPresent(set -> {
            for (Holder<Item> holder : set) {
                resolveHolder(registry, holder, visited, outItems, outTags, TagClosureExpander::expandItemTag);
            }
        });
    }

    private static void expandBlockTag(
            Registry<Block> registry,
            ResourceLocation tagId,
            Set<ResourceLocation> visited,
            Set<String> outBlocks,
            Set<String> outTags) {
        if (!visited.add(tagId)) {
            return;
        }
        if (outTags != null) {
            outTags.add(tagId.toString());
        }
        TagKey<Block> key = TagKey.create(Registries.BLOCK, tagId);
        registry.getTag(key).ifPresent(set -> {
            for (Holder<Block> holder : set) {
                resolveHolder(registry, holder, visited, outBlocks, outTags, TagClosureExpander::expandBlockTag);
            }
        });
    }

    private static void expandFluidTag(
            Registry<Fluid> registry,
            ResourceLocation tagId,
            Set<ResourceLocation> visited,
            Set<String> outFluids,
            Set<String> outTags) {
        if (!visited.add(tagId)) {
            return;
        }
        if (outTags != null) {
            outTags.add(tagId.toString());
        }
        TagKey<Fluid> key = TagKey.create(Registries.FLUID, tagId);
        registry.getTag(key).ifPresent(set -> {
            for (Holder<Fluid> holder : set) {
                resolveHolder(registry, holder, visited, outFluids, outTags, TagClosureExpander::expandFluidTag);
            }
        });
    }

    @FunctionalInterface
    private interface TagExpander<T> {
        void expand(Registry<T> registry, ResourceLocation tagId, Set<ResourceLocation> visited, Set<String> out, Set<String> outTags);
    }

    private static <T> void resolveHolder(
            Registry<T> registry,
            Holder<T> holder,
            Set<ResourceLocation> visited,
            Set<String> out,
            Set<String> outTags,
            TagExpander<T> expandNested) {
        Optional<ResourceKey<T>> key = holder.unwrapKey();
        if (key.isPresent() && !registry.containsKey(key.get())) {
            expandNested.expand(registry, key.get().location(), visited, out, outTags);
            return;
        }
        T value = holder.value();
        ResourceLocation id = registry.getKey(value);
        if (id != null) {
            out.add(id.toString());
        }
    }

    static ResourceLocation parseTagId(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String s = raw.trim();
        if (s.startsWith("#")) {
            s = s.substring(1);
        }
        return ResourceLocation.tryParse(s);
    }

    /** @return {@code null} if not a tag reference */
    static String normalizeTagRef(String raw) {
        ResourceLocation id = parseTagId(raw);
        return id != null ? id.toString() : null;
    }
}
