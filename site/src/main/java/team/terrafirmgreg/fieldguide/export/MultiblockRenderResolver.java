package team.terrafirmgreg.fieldguide.export;

import lombok.extern.slf4j.Slf4j;
import team.terrafirmgreg.fieldguide.data.patchouli.page.PageMultiblock;
import team.terrafirmgreg.fieldguide.data.patchouli.page.PageMultiblockData;
import team.terrafirmgreg.fieldguide.data.tfc.page.PageMultiMultiblock;
import team.terrafirmgreg.fieldguide.data.tfc.page.TFCMultiblockData;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Builds renderable multiblock pages from Patchouli JSON plus export {@code multiblockDefs} / blockstates.
 */
@Slf4j
public final class MultiblockRenderResolver {

    private final MultiblockRegistry registry;
    private final BlockstateRefResolver blockstates;

    public MultiblockRenderResolver(MultiblockRegistry registry, BlockstateRefResolver blockstates) {
        this.registry = registry;
        this.blockstates = blockstates;
    }

    public Optional<PageMultiblockData> resolve(PageMultiblock page) {
        if (page == null) {
            return Optional.empty();
        }
        if (page.getMultiblock() != null) {
            return Optional.of(resolveEmbedded(page.getMultiblock()));
        }
        if (page.getMultiblockId() != null && !page.getMultiblockId().isBlank()) {
            return registry.resolve(page.getMultiblockId()).flatMap(this::fromExportDef);
        }
        return Optional.empty();
    }

    public List<PageMultiblockData> resolve(PageMultiMultiblock page) {
        if (page == null || page.getMultiblocks() == null) {
            return List.of();
        }
        List<PageMultiblockData> out = new ArrayList<>();
        for (TFCMultiblockData block : page.getMultiblocks()) {
            if (block == null) {
                continue;
            }
            if (block.getMultiblockId() != null && !block.getMultiblockId().isBlank()) {
                registry.resolve(block.getMultiblockId())
                        .flatMap(this::fromExportDef)
                        .ifPresent(out::add);
            } else if (block.getPattern() != null && block.getMapping() != null) {
                PageMultiblockData data = new PageMultiblockData();
                data.setPattern(block.getPattern());
                data.setMapping(blockstates.resolveMapping(block.getMapping()));
                out.add(data);
            }
        }
        return List.copyOf(out);
    }

    private PageMultiblockData resolveEmbedded(PageMultiblockData source) {
        PageMultiblockData data = new PageMultiblockData();
        data.setPattern(source.getPattern());
        data.setMapping(blockstates.resolveMapping(source.getMapping()));
        data.setSymmetrical(source.getSymmetrical());
        data.setOffset(source.getOffset());
        return data;
    }

    private Optional<PageMultiblockData> fromExportDef(ResolvedMultiblock def) {
        if (!def.isOk()) {
            log.warn("Export multiblock {} not ok: {}", def.id(), def.error());
            return Optional.empty();
        }
        String[][] pattern = def.toPatternArray();
        if (pattern.length == 0) {
            log.warn("Export multiblock {} has no pattern", def.id());
            return Optional.empty();
        }
        Map<String, String> mapping = def.exportMapping().isEmpty()
                ? blockstates.resolveMapping(def.mapping())
                : blockstates.resolveExportMapping(def.exportMapping());
        if (mapping.isEmpty()) {
            log.warn("Export multiblock {} has no mapping", def.id());
            return Optional.empty();
        }
        PageMultiblockData data = new PageMultiblockData();
        data.setPattern(pattern);
        data.setMapping(mapping);
        return Optional.of(data);
    }
}
