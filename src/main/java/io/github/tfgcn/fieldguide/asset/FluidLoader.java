package io.github.tfgcn.fieldguide.asset;

import io.github.tfgcn.fieldguide.Context;
import io.github.tfgcn.fieldguide.I18n;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;

/**
 * desc:
 *
 * @author yanmaoyuan
 */
public class FluidLoader {

    private static final Map<String, FluidImageResult> CACHE = new HashMap<>();

    // 流体颜色映射
    private static final Map<String, String> FLUID_COLORS = Map.ofEntries(
            Map.entry("brine", "#DCD3C9"),
            Map.entry("curdled_milk", "#FFFBE8"),
            Map.entry("limewater", "#B4B4B4"),
            Map.entry("lye", "#feffde"),
            Map.entry("milk_vinegar", "#FFFBE8"),
            Map.entry("olive_oil", "#6A7537"),
            Map.entry("olive_oil_water", "#4A4702"),
            Map.entry("tannin", "#63594E"),
            Map.entry("tallow", "#EDE9CF"),
            Map.entry("vinegar", "#C7C2AA"),
            Map.entry("beer", "#C39E37"),
            Map.entry("cider", "#B0AE32"),
            Map.entry("rum", "#6E0123"),
            Map.entry("sake", "#B7D9BC"),
            Map.entry("vodka", "#DCDCDC"),
            Map.entry("whiskey", "#583719"),
            Map.entry("corn_whiskey", "#D9C7B7"),
            Map.entry("rye_whiskey", "#C77D51"),
            Map.entry("water", "#2245CB"),
            Map.entry("salt_water", "#4E63B9"),
            Map.entry("spring_water", "#8AA3FF"),
            Map.entry("yak_milk", "#E8E8E8"),
            Map.entry("goat_milk", "#E8E8E8"),
            Map.entry("chocolate", "#756745")
    );

    /**
     * 解码流体数据
     */
    public static FluidResult decodeFluid(Object item) {
        int amount = 0;
        String ingredient = null;

        if (item instanceof Map) {
            Map<?, ?> itemMap = (Map<?, ?>) item;
            if (itemMap.containsKey("ingredient")) {
                ingredient = decodeFluidIngredient(itemMap.get("ingredient"));
            } else if (itemMap.containsKey("fluid") || itemMap.containsKey("tag")) {
                ingredient = decodeFluidIngredient(item);
            }
            amount = itemMap.containsKey("amount") ? ((Number) itemMap.get("amount")).intValue() : 1000;
        } else if (item instanceof String) {
            ingredient = (String) item;
        }

        if (ingredient == null) {
            throw new RuntimeException("Invalid format for a fluid: '" + item + "'");
        } else {
            return new FluidResult(ingredient, amount);
        }
    }

    /**
     * 解码流体成分
     */
    @SuppressWarnings("unchecked")
    public static String decodeFluidIngredient(Object item) {
        if (item instanceof String) {
            return (String) item;
        } else if (item instanceof Map) {
            Map<String, Object> itemMap = (Map<String, Object>) item;
            if (itemMap.containsKey("fluid")) {
                return (String) itemMap.get("fluid");
            } else if (itemMap.containsKey("tag")) {
                return "#" + itemMap.get("tag");
            }
        }
        throw new RuntimeException("Could not decode fluid ingredient: " + item);
    }

    /**
     * 获取流体图像
     */
    public static FluidImageResult getFluidImage(Context context, Object fluidIn, boolean placeholder) {
        return getFluidImage(context, fluidIn, placeholder, true);
    }

    public static FluidImageResult getFluidImage(Context context, Object fluidIn, boolean placeholder, boolean includeAmount) {
        FluidResult decoded = decodeFluid(fluidIn);
        String fluid = decoded.getFluid();
        int amount = decoded.getAmount();

        if (CACHE.containsKey(fluid)) {
            FluidImageResult entry = CACHE.get(fluid);
            String name = entry.getName();
            if (entry.getKey() != null) {
                try {
                    // 必须每次都重新翻译，因为相同的图像会在不同的本地化环境中被请求
                    name = context.translate("fluid." + entry.getKey(), "block." + entry.getKey());
                } catch (Exception e) {
                    System.err.println("Warning: " + e.getMessage());
                }
            }
            String finalName = includeAmount && amount > 0 ?
                    String.format("%s mB %s", amount, name) : name;
            entry.setName(finalName);
            return entry;
        }

        String name = null;
        String key = null;
        List<String> fluids = new ArrayList<>();

        if (fluid.startsWith("#")) {
            name = String.format(context.translate(I18n.TAG), fluid);
            fluids = context.getLoader().loadFluidTag(fluid.substring(1));
        } else if (fluid.contains(",")) {
            fluids = Arrays.asList(fluid.split(","));
        } else {
            fluids = Collections.singletonList(fluid);
        }

        if (fluids.size() == 1) {
            key = fluids.get(0).replace("/", ".").replace(":", ".");
            try {
                name = context.translate("fluid." + key, "block." + key);
            } catch (Exception e) {
                System.err.println("Warning: " + e.getMessage());
            }
        }

        String path;
        try {
            List<BufferedImage> images = new ArrayList<>();
            for (String fluidId : fluids) {
                images.add(createFluidImage(fluidId));
            }

            if (images.size() == 1) {
                path = context.saveImage(context.nextId("fluid"), images.get(0));
            } else {
                path = context.saveGif(context.nextId("fluid"), images);
            }
        } catch (Exception e) {
            System.err.println("Warning: Fluid Image(s) - " + e.getMessage());

            if (placeholder) {
                // 回退到使用占位符图像
                path = "../../_images/fluid.png";
            } else {
                throw new RuntimeException(e);
            }
        }


        String finalName = includeAmount && amount > 0 ?
                String.format("%s mB %s", amount, name) : name;

        FluidImageResult result = new FluidImageResult(path, finalName, key);

        CACHE.put(fluid, result);
        return result;
    }

    /**
     * 创建流体图像
     */
    public static BufferedImage createFluidImage(String fluid) {
        String path = fluid;
        if (path.contains(":")) {
            path = path.split(":", 2)[1];
        }

        // 加载基础流体图像并调整大小
        BufferedImage base = loadBaseFluidImage();
        base = resizeImage(base, 64, 64);

        if (!FLUID_COLORS.containsKey(path)) {
            System.out.println("Fluid " + path + " has no color specified.");
            return base;
        } else {
            Color color = parseColor(FLUID_COLORS.get(path));
            return applyColorToImage(base, color);
        }
    }

    /**
     * 将颜色应用到图像的所有像素上
     */
    public static BufferedImage applyColorToImage(BufferedImage img, Color color) {
        return applyColorToImage(img, color, 50);
    }

    public static BufferedImage applyColorToImage(BufferedImage img, Color color, int darkThreshold) {
        BufferedImage result = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_ARGB);

        float[] hsv = rgbToHsv(color.getRed(), color.getGreen(), color.getBlue());
        float hue = hsv[0];
        float saturation = hsv[1];
        float value = hsv[2];

        for (int x = 0; x < img.getWidth(); x++) {
            for (int y = 0; y < img.getHeight(); y++) {
                int rgb = img.getRGB(x, y);
                Color pixelColor = new Color(rgb, true);

                float[] pixelHsv = rgbToHsv(pixelColor.getRed(), pixelColor.getGreen(), pixelColor.getBlue());
                float newValue = value > darkThreshold ? pixelHsv[2] : pixelHsv[2] * 0.5f;

                Color newColor = hsvToRgb(hue, saturation, newValue, pixelColor.getAlpha());
                result.setRGB(x, y, newColor.getRGB());
            }
        }

        return result;
    }

    // 辅助方法
    private static BufferedImage loadBaseFluidImage() {
        // 这里需要实现加载基础流体图像的逻辑
        // 返回一个 64x64 的默认流体图像
        return new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
    }

    private static BufferedImage resizeImage(BufferedImage original, int width, int height) {
        BufferedImage resized = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = resized.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(original, 0, 0, width, height, null);
        g.dispose();
        return resized;
    }

    private static Color parseColor(String hex) {
        if (hex.startsWith("#")) {
            hex = hex.substring(1);
        }
        return new Color(
                Integer.valueOf(hex.substring(0, 2), 16),
                Integer.valueOf(hex.substring(2, 4), 16),
                Integer.valueOf(hex.substring(4, 6), 16)
        );
    }

    private static float[] rgbToHsv(int r, int g, int b) {
        float[] hsv = new float[3];
        Color.RGBtoHSB(r, g, b, hsv);
        return hsv;
    }

    private static Color hsvToRgb(float h, float s, float v, int alpha) {
        int rgb = Color.HSBtoRGB(h, s, v);
        return new Color((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, alpha);
    }
}
