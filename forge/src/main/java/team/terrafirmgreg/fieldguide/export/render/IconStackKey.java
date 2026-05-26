package team.terrafirmgreg.fieldguide.export.render;

import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.stack.ItemEmiStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Atlas index keys for item icons. Plain items use {@code namespace:path}; stacks with NBT use
 * {@code namespace:path@<sha256-prefix>} so SNBT is not embedded in CSS selectors.
 */
public final class IconStackKey {

    private static final HexFormat HEX = HexFormat.of();
    /** Length of hex digest used in keys (full SHA-256 is 64). */
    public static final int HASH_LEN = 16;

    private IconStackKey() {}

    public static String forEmiStack(EmiStack stack) {
        ResourceLocation id = stack.getId();
        if (id == null) {
            return null;
        }
        if (!stack.hasNbt()) {
            return id.toString();
        }
        return forItemIdAndNbtSnbt(id, nbtSnbt(stack.getNbt()));
    }

    /** Uses the SNBT string from serialized layout JSON when available (matches EMI export). */
    public static String forItemIdAndNbtSnbt(ResourceLocation id, String nbtSnbt) {
        if (id == null) {
            return null;
        }
        if (nbtSnbt == null || nbtSnbt.isEmpty()) {
            return id.toString();
        }
        return id + "@" + hashString(nbtSnbt);
    }

    public static boolean isVariantKey(String key) {
        return key != null && key.indexOf('@') > 0;
    }

    public static String hashString(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HEX.formatHex(hash, 0, HASH_LEN / 2);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static String nbtSnbt(CompoundTag nbt) {
        return nbt == null || nbt.isEmpty() ? "" : nbt.toString();
    }

    public static ItemStack toItemStack(EmiStack emiStack) {
        if (emiStack instanceof ItemEmiStack itemEmi) {
            return itemEmi.getItemStack();
        }
        if (emiStack.getKey() instanceof Item item && item != Items.AIR) {
            ItemStack stack = new ItemStack(item, Math.max(1, (int) emiStack.getAmount()));
            if (emiStack.hasNbt()) {
                stack.setTag(emiStack.getNbt().copy());
            }
            return stack;
        }
        return ItemStack.EMPTY;
    }
}
