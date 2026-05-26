package team.terrafirmgreg.fieldguide.export.render;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.stack.serializer.EmiIngredientSerializer;
import dev.emi.emi.api.widget.AnimatedTextureWidget;
import dev.emi.emi.api.widget.Bounds;
import dev.emi.emi.api.widget.ButtonWidget;
import dev.emi.emi.api.widget.FillingArrowWidget;
import dev.emi.emi.api.widget.SlotWidget;
import dev.emi.emi.api.widget.TankWidget;
import dev.emi.emi.api.widget.TextWidget;
import dev.emi.emi.api.widget.TextureWidget;
import dev.emi.emi.api.widget.TooltipWidget;
import dev.emi.emi.api.widget.Widget;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Serializes EMI recipe widgets to JSON for emi.js (schema v2).
 */
final class EmiWidgetSerializer {

    private static final Logger LOGGER = LogManager.getLogger("fieldguide");

    record Context(
            Minecraft client,
            Path chromeRoot,
            Map<String, String> chromeHashToRelative,
            int[] chromeWritten,
            int[] chromeDeduped,
            Set<String> referencedItems,
            Set<String> referencedFluids,
            Set<String> referencedTags,
            Map<String, ItemStack> iconVariants) {}

    private EmiWidgetSerializer() {}

    static void serializeWidgets(
            EmiRecipe recipe,
            List<Widget> widgets,
            Set<String> textureIds,
            Context ctx,
            Consumer<JsonObject> sink) {
        for (Widget widget : widgets) {
            if (shouldSkipWidget(widget)) {
                continue;
            }
            JsonObject json = serialize(recipe, widget, textureIds, ctx);
            if (json != null) {
                sink.accept(json);
            }
        }
    }

    private static boolean shouldSkipWidget(Widget widget) {
        if (widget instanceof ButtonWidget) {
            return true;
        }
        String name = widget.getClass().getSimpleName();
        return name.contains("Button") && name.contains("Recipe");
    }

    private static JsonObject serialize(EmiRecipe recipe, Widget widget, Set<String> textureIds, Context ctx) {
        try {
            if (WidgetChromeRasterizer.isRootWidget(widget)) {
                return rootChrome(widget, ctx);
            }
            if (WidgetChromeRasterizer.isDrawableWidget(widget)) {
                return drawableChrome(widget, ctx);
            }
            if (widget instanceof TankWidget tank) {
                return slotLike(recipe, tank, "tank", textureIds, true, ctx);
            }
            if (widget instanceof SlotWidget slot) {
                return slotLike(recipe, slot, "slot", textureIds, false, ctx);
            }
            if (widget instanceof FillingArrowWidget arrow) {
                return fillingArrow(arrow, textureIds);
            }
            if (widget instanceof AnimatedTextureWidget animated) {
                return animatedTexture(animated, textureIds);
            }
            if (widget instanceof TextureWidget texture) {
                return texture(texture, textureIds);
            }
            if (widget instanceof TextWidget text) {
                return textWidget(text);
            }
            if (widget instanceof TooltipWidget tooltip) {
                return tooltip(tooltip);
            }
            return rasterChrome(widget, ctx, "raster");
        } catch (Exception e) {
            LOGGER.warn("[emi-layout] widget {} failed: {}", widget.getClass().getName(), e.toString());
            throw new RuntimeException("widget export failed: " + widget.getClass().getName(), e);
        }
    }

    private static JsonObject rootChrome(Widget widget, Context ctx) throws Exception {
        JsonObject o = boundsObject(widget.getBounds());
        o.addProperty("type", "root_chrome");
        attachChrome(o, widget, ctx);
        o.addProperty("javaClass", widget.getClass().getName());
        return o;
    }

    private static JsonObject drawableChrome(Widget widget, Context ctx) throws Exception {
        JsonObject o = boundsObject(widget.getBounds());
        o.addProperty("type", "drawable_raster");
        attachChrome(o, widget, ctx);
        o.addProperty("javaClass", widget.getClass().getName());
        return o;
    }

    private static JsonObject rasterChrome(Widget widget, Context ctx, String type) throws Exception {
        Bounds b = widget.getBounds();
        if (b == null || b.empty()) {
            return null;
        }
        JsonObject o = boundsObject(b);
        o.addProperty("type", type);
        attachChrome(o, widget, ctx);
        o.addProperty("javaClass", widget.getClass().getName());
        return o;
    }

    private static void attachChrome(JsonObject o, Widget widget, Context ctx) throws Exception {
        WidgetChromeRasterizer.ChromeAsset asset = WidgetChromeRasterizer.rasterizeWidget(
                ctx.client(), widget, ctx.chromeRoot(), ctx.chromeHashToRelative());
        o.addProperty("chrome", asset.exportPath());
        ctx.chromeWritten()[0]++;
        if (asset.deduplicated()) {
            ctx.chromeDeduped()[0]++;
        }
    }

    private static JsonObject slotLike(
            EmiRecipe recipe,
            SlotWidget slot,
            String type,
            Set<String> textureIds,
            boolean tank,
            Context ctx) {
        Bounds b = slot.getBounds();
        JsonObject o = boundsObject(b);
        o.addProperty("type", type);
        o.addProperty("role", inferSlotRole(recipe, slot));
        o.addProperty("large", readBooleanField(slot, "output"));
        o.addProperty("catalyst", readBooleanField(slot, "catalyst"));
        o.addProperty("drawBack", readBooleanField(slot, "drawBack"));
        o.addProperty("custom", readBooleanField(slot, "custom"));

        ResourceLocation customTex = readField(slot, "textureId", ResourceLocation.class);
        if (customTex != null) {
            o.addProperty("backgroundTexture", customTex.toString());
            textureIds.add(customTex.toString());
            Integer u = readField(slot, "u", Integer.class);
            Integer v = readField(slot, "v", Integer.class);
            if (u != null) {
                o.addProperty("backgroundU", u);
            }
            if (v != null) {
                o.addProperty("backgroundV", v);
            }
        }

        if (tank) {
            Long cap = readField(slot, "capacity", Long.class);
            if (cap != null) {
                o.addProperty("capacity", cap);
            }
        }

        EmiIngredient stack = slot.getStack();
        if (stack != null && !stack.isEmpty()) {
            JsonElement ing = EmiIngredientSerializer.getSerialized(stack);
            enrichIngredientIconKeys(ing, stack);
            o.add("ingredient", ing);
            collectSerializedTagRef(ing, ctx.referencedTags());
            collectReferenced(stack, ctx.referencedItems(), ctx.referencedFluids(), ctx.iconVariants());
            attachRemainderHint(o, stack);
            attachTagDisplayItem(o, stack);
        }
        return o;
    }

    /** EMI renders widgets.png (4,252) or catalyst (12,252) when stacks carry a remainder. */
    private static void attachRemainderHint(JsonObject o, EmiIngredient ingredient) {
        for (EmiStack stack : ingredient.getEmiStacks()) {
            EmiStack remainder = stack.getRemainder();
            if (!remainder.isEmpty()) {
                o.addProperty("remainderIcon", remainder.equals(stack) ? "self" : "other");
                return;
            }
        }
    }

    /** TagEmiIngredient display stack (index 0) for emi.js when tag-members index is unavailable. */
    private static void attachTagDisplayItem(JsonObject o, EmiIngredient ingredient) {
        for (EmiStack stack : ingredient.getEmiStacks()) {
            String key = IconStackKey.forEmiStack(stack);
            if (key != null) {
                o.addProperty("tagDisplayItem", key);
                return;
            }
        }
    }

    /** Adds {@code iconKey} to serialized ingredient JSON for NBT variant lookup in emi.js. */
    private static void enrichIngredientIconKeys(JsonElement ing, EmiIngredient ingredient) {
        if (ing == null || ingredient == null) {
            return;
        }
        List<EmiStack> stacks = ingredient.getEmiStacks();
        if (ing.isJsonObject()) {
            attachIconKeyToObject(ing.getAsJsonObject(), stacks.isEmpty() ? null : stacks.get(0));
        } else if (ing.isJsonArray()) {
            JsonArray arr = ing.getAsJsonArray();
            for (int i = 0; i < arr.size() && i < stacks.size(); i++) {
                if (arr.get(i).isJsonObject()) {
                    attachIconKeyToObject(arr.get(i).getAsJsonObject(), stacks.get(i));
                }
            }
        }
    }

    private static void attachIconKeyToObject(JsonObject obj, EmiStack stack) {
        if (stack == null) {
            return;
        }
        String key = IconStackKey.forEmiStack(stack);
        if (key != null && IconStackKey.isVariantKey(key)) {
            obj.addProperty("iconKey", key);
        }
    }

    private static void collectReferenced(
            EmiIngredient ingredient,
            Set<String> items,
            Set<String> fluids,
            Map<String, ItemStack> iconVariants) {
        for (EmiStack emiStack : ingredient.getEmiStacks()) {
            ResourceLocation id = emiStack.getId();
            if (id == null) {
                continue;
            }
            if (emiStack.getKey() instanceof Fluid) {
                fluids.add(id.toString());
            } else {
                items.add(id.toString());
                String iconKey = IconStackKey.forEmiStack(emiStack);
                if (iconKey != null && IconStackKey.isVariantKey(iconKey)) {
                    ItemStack stack = IconStackKey.toItemStack(emiStack);
                    if (!stack.isEmpty()) {
                        iconVariants.put(iconKey, stack);
                    }
                }
            }
        }
    }

    private static void collectSerializedTagRef(JsonElement ing, Set<String> tags) {
        if (ing == null || !ing.isJsonPrimitive()) {
            return;
        }
        String raw = ing.getAsString();
        if (raw.startsWith("#item:")) {
            tags.add(raw.substring(6));
        }
    }

    private static String inferSlotRole(EmiRecipe recipe, SlotWidget slot) {
        if (readBooleanField(slot, "catalyst")) {
            return "catalyst";
        }
        EmiIngredient stack = slot.getStack();
        if (stack == null || stack.isEmpty()) {
            return "input";
        }
        for (EmiStack out : recipe.getOutputs()) {
            for (EmiStack candidate : stack.getEmiStacks()) {
                if (out.equals(candidate)) {
                    return "output";
                }
            }
        }
        for (EmiIngredient catalyst : recipe.getCatalysts()) {
            if (catalyst.equals(stack)) {
                return "catalyst";
            }
        }
        return "input";
    }

    private static JsonObject texture(TextureWidget widget, Set<String> textureIds) {
        ResourceLocation tex = readField(widget, "texture", ResourceLocation.class);
        if (tex != null) {
            textureIds.add(tex.toString());
        }
        JsonObject o = boundsObject(widget.getBounds());
        o.addProperty("type", "texture");
        if (tex != null) {
            o.addProperty("texture", tex.toString());
        }
        putTextureFields(widget, o);
        return o;
    }

    private static JsonObject animatedTexture(AnimatedTextureWidget widget, Set<String> textureIds) {
        ResourceLocation tex = readField(widget, "texture", ResourceLocation.class);
        if (tex != null) {
            textureIds.add(tex.toString());
        }
        JsonObject o = boundsObject(widget.getBounds());
        o.addProperty("type", "animated_texture");
        if (tex != null) {
            o.addProperty("texture", tex.toString());
        }
        putTextureFields(widget, o);
        Integer time = readField(widget, "time", Integer.class);
        if (time != null) {
            o.addProperty("time", time);
        }
        o.addProperty("horizontal", readBooleanField(widget, "horizontal"));
        o.addProperty("endToStart", readBooleanField(widget, "endToStart"));
        o.addProperty("fullToEmpty", readBooleanField(widget, "fullToEmpty"));
        return o;
    }

    private static JsonObject fillingArrow(FillingArrowWidget widget, Set<String> textureIds) {
        textureIds.add("emi:textures/gui/widgets.png");
        JsonObject o = boundsObject(widget.getBounds());
        o.addProperty("type", "filling_arrow");
        Integer time = readField(widget, "time", Integer.class);
        if (time != null) {
            o.addProperty("time", time);
        }
        return o;
    }

    private static JsonObject textWidget(TextWidget widget) {
        JsonObject o = boundsObject(widget.getBounds());
        o.addProperty("type", "text");
        FormattedCharSequence text = readField(widget, "text", FormattedCharSequence.class);
        if (text != null) {
            String plain = formattedCharSequenceToString(text);
            if (!plain.isEmpty()) {
                o.addProperty("text", plain);
            }
        }
        Integer color = readField(widget, "color", Integer.class);
        if (color != null) {
            o.addProperty("color", color);
        }
        o.addProperty("shadow", readBooleanField(widget, "shadow"));
        TextWidget.Alignment h = readField(widget, "horizontalAlignment", TextWidget.Alignment.class);
        TextWidget.Alignment v = readField(widget, "verticalAlignment", TextWidget.Alignment.class);
        if (h != null) {
            o.addProperty("horizontalAlign", h.name());
        }
        if (v != null) {
            o.addProperty("verticalAlign", v.name());
        }
        Integer baseX = readField(widget, "x", Integer.class);
        Integer baseY = readField(widget, "y", Integer.class);
        if (baseX != null) {
            o.addProperty("baseX", baseX);
        }
        if (baseY != null) {
            o.addProperty("baseY", baseY);
        }
        return o;
    }

    private static JsonObject tooltip(TooltipWidget widget) {
        JsonObject o = boundsObject(widget.getBounds());
        o.addProperty("type", "tooltip");
        return o;
    }

    private static void putTextureFields(TextureWidget widget, JsonObject o) {
        Integer u = readField(widget, "u", Integer.class);
        Integer v = readField(widget, "v", Integer.class);
        Integer texW = readField(widget, "textureWidth", Integer.class);
        Integer texH = readField(widget, "textureHeight", Integer.class);
        Integer regionW = readField(widget, "regionWidth", Integer.class);
        Integer regionH = readField(widget, "regionHeight", Integer.class);
        if (u != null) {
            o.addProperty("u", u);
        }
        if (v != null) {
            o.addProperty("v", v);
        }
        if (texW != null) {
            o.addProperty("texW", texW);
        }
        if (texH != null) {
            o.addProperty("texH", texH);
        }
        if (regionW != null) {
            o.addProperty("regionW", regionW);
        }
        if (regionH != null) {
            o.addProperty("regionH", regionH);
        }
    }

    private static JsonObject boundsObject(Bounds b) {
        JsonObject o = new JsonObject();
        o.addProperty("x", b.x());
        o.addProperty("y", b.y());
        o.addProperty("w", b.width());
        o.addProperty("h", b.height());
        return o;
    }

    private static String formattedCharSequenceToString(FormattedCharSequence text) {
        StringBuilder sb = new StringBuilder();
        text.accept((index, style, codePoint) -> {
            sb.appendCodePoint(codePoint);
            return true;
        });
        return sb.toString();
    }

    private static boolean readBooleanField(Object target, String name) {
        Boolean v = readField(target, name, Boolean.class);
        return v != null && v;
    }

    @SuppressWarnings("unchecked")
    private static <T> T readField(Object target, String name, Class<T> type) {
        Class<?> c = target.getClass();
        while (c != null) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                Object v = f.get(target);
                if (v == null) {
                    return null;
                }
                return type.cast(v);
            } catch (NoSuchFieldException ignored) {
                c = c.getSuperclass();
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }
}
