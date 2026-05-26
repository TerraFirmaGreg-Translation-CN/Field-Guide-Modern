package team.terrafirmgreg.fieldguide.render.components;

import team.terrafirmgreg.fieldguide.asset.ItemStackResult;

/**
 * 合成配方类
 */
public class CraftingRecipe {
    public Object[] grid = new Object[9]; // grid[x + 3 * y]
    public ItemStackResult output;
    public boolean shapeless = false;
}