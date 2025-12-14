package su.terrafirmgreg.fieldguide.asset;

import lombok.Data;

@Data
public class FluidResult {
    private final String fluid;
    private final int amount;

    public FluidResult(String fluid, int amount) {
        this.fluid = fluid;
        this.amount = amount;
    }
}
