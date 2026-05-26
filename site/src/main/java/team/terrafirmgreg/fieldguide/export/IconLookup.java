package team.terrafirmgreg.fieldguide.export;

import java.util.Optional;

/**
 * Resolves item/block-item/fluid ids to generated atlas CSS references.
 */
public interface IconLookup {

    Optional<IconRef> resolveItem(String itemId);

    Optional<IconRef> resolveBlockItem(String itemId);

    Optional<IconRef> resolveFluid(String fluidId);
}
