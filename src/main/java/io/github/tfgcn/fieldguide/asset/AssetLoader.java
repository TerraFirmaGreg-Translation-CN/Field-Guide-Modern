package io.github.tfgcn.fieldguide.asset;

import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import io.github.tfgcn.fieldguide.data.fml.ModInfo;
import io.github.tfgcn.fieldguide.data.fml.ModLoader;
import io.github.tfgcn.fieldguide.localization.Language;
import io.github.tfgcn.fieldguide.data.gtceu.utils.ResourceHelper;
import io.github.tfgcn.fieldguide.data.minecraft.tag.TagElement;
import io.github.tfgcn.fieldguide.data.minecraft.tag.Tags;
import io.github.tfgcn.fieldguide.data.patchouli.Book;
import io.github.tfgcn.fieldguide.data.patchouli.BookCategory;
import io.github.tfgcn.fieldguide.data.patchouli.BookEntry;
import io.github.tfgcn.fieldguide.data.tfc.TFCWood;
import io.github.tfgcn.fieldguide.gson.JsonUtils;
import io.github.tfgcn.fieldguide.exception.AssetNotFoundException;
import io.github.tfgcn.fieldguide.data.minecraft.blockstate.BlockState;
import io.github.tfgcn.fieldguide.data.minecraft.blockstate.BlockVariant;
import io.github.tfgcn.fieldguide.data.minecraft.blockstate.Variant;
import io.github.tfgcn.fieldguide.data.minecraft.blockmodel.BlockModel;
import io.github.tfgcn.fieldguide.Constants;
import io.github.tfgcn.fieldguide.exception.InternalException;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.List;
import java.util.function.Function;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

import static io.github.tfgcn.fieldguide.Constants.CACHE;
import static io.github.tfgcn.fieldguide.render.TextureRenderer.multiplyImageByColor;

@Slf4j
public class AssetLoader {

    private final Path instanceRoot;
    private final List<AssetSource> sources;
    private final Map<String, AssetSource> resourceCache;
    private final Map<String, BlockModel> blockModelCache;
    private final Map<String, BlockModel> itemModelCache;
    private final Map<String, List<Tags>> tagsCache;

    public Map<String, Map<String, Object>> recipeCache = new TreeMap<>();

    private final Map<String, BufferedImage> registeredImage;

    @Getter
    private final Set<String> missingAssets = new TreeSet<>();

    public AssetLoader(Path instanceRoot) {
        this.instanceRoot = instanceRoot;
        this.sources = new ArrayList<>();
        this.resourceCache = new HashMap<>();
        this.blockModelCache = new TreeMap<>();
        this.itemModelCache = new TreeMap<>();
        this.tagsCache = new TreeMap<>();
        this.registeredImage = new HashMap<>();

        initializeSources();

        ResourceHelper.assetLoader = this;

        initBuiltinModels();
        initGtceuIngots();
        initTFCWoods();
    }

    private void initBuiltinModels() {
        BlockModel itemGenerated = new BlockModel();
        itemGenerated.setTextures(Map.of("particle", "#layer0"));
        itemGenerated.setGuiLight("front");

        BlockModel builtinGenerated = new BlockModel();
        builtinGenerated.setTextures(Map.of("particle", "#layer0"));
        builtinGenerated.setGuiLight("front");

        blockModelCache.put("minecraft:builtin/entity", new BlockModel());
        blockModelCache.put("minecraft:builtin/generated", builtinGenerated);
        blockModelCache.put("minecraft:item/generated", itemGenerated);
        blockModelCache.put("forge:item/bucket", new BlockModel());
        blockModelCache.put("forge:item/default", new BlockModel());

        itemModelCache.put("minecraft:item/generated", itemGenerated);
        itemModelCache.put("minecraft:builtin/generated", builtinGenerated);
        itemModelCache.put("forge:item/bucket", new BlockModel());
        itemModelCache.put("forge:item/default", new BlockModel());
    }

    private void initGtceuIngots() {
        registerIngotImage("bronze", 0xffc370, 0x806752, "metallic");
        registerIngotImage("bismuth_bronze", 0xffd26f, 0x895f3d, "metallic");
        registerIngotImage("black_bronze", 0x8b7c70, 0x4b3d32, "metallic");
        registerIngotImage("rose_gold", 0xecd5b8, 0xd85f2d, "shiny");
        registerIngotImage("sterling_silver", 0xfaf4dc, 0x484434, "shiny");
    }

    private void registerIngotImage(String id, int colorMain, int colorSecondary, String iconSet) {
        String item = "gtceu:" + id + "_ingot";
        BufferedImage itemIcon = createIngot(colorMain, colorSecondary, iconSet);

        registeredImage.put(item, itemIcon);

        BlockModel model = new BlockModel();
        model.setParent("item/generated");
        model.setTextures(Map.of("layer0", item));
        itemModelCache.put(item, model);
    }

    private void initTFCWoods() {
        BufferedImage lumberBase = loadTexture("tfc:item/wood/lumber");
        BufferedImage twigBase = loadTexture("tfc:item/wood/twig");

        for (TFCWood wood : TFCWood.values()) {

            String name = wood.getSerializedName();
            Color woodColor = new Color(wood.getWoodColor().getCol());

            BufferedImage lumber = multiplyImageByColor(lumberBase, woodColor);
            registeredImage.put("tfc:item/wood/lumber_" + name, lumber);
            BufferedImage twig = multiplyImageByColor(twigBase, woodColor);
            registeredImage.put("tfc:item/wood/twig_" + name, twig);
        }
    }

    private BufferedImage createIngot(int colorMain, int colorSecondary, String iconSet) {
        String ingotKey = "gtceu:item/material_sets/%s/ingot".formatted(iconSet);
        String ingotOverlayKey = "gtceu:item/material_sets/%s/ingot_overlay".formatted(iconSet);
        String ingotSecondaryKey = "gtceu:item/material_sets/%s/ingot_secondary".formatted(iconSet);
        BufferedImage ingot = loadTexture(ingotKey);
        BufferedImage ingotOverlay = loadTexture(ingotOverlayKey);
        BufferedImage ingotSecondary = loadTexture(ingotSecondaryKey);

        Color color = new Color(colorMain);
        Color secondary = new Color(colorSecondary);

        BufferedImage base = multiplyImageByColor(ingot, color);
        BufferedImage secondaryOverlay = multiplyImageByColor(ingotSecondary, secondary);

        BufferedImage combined = new BufferedImage(base.getWidth(), base.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = combined.createGraphics();
        g.drawImage(base, 0, 0, null);
        g.drawImage(ingotOverlay, 0, 0, null);
        g.drawImage(secondaryOverlay, 0, 0, null);
        g.dispose();
        return combined;
    }

    private void initializeSources() {
        log.info("Initializing AssetManager for: {}", instanceRoot);

        List<AssetSource> sources = new ArrayList<>();

        // download minecraft and forge
        MCMeta.loadCache(Constants.MC_VERSION, Constants.FORGE_VERSION, Language.asList());

        addCacheFolder(sources);// 0- Cache
        addModsJars(sources);// 1- Mod JARs
        //addResourcePacks(sources);// 2- Resource Packs
        addKubejs(sources);// 3- KubeJS

        // Reverse the list
        int size = sources.size();
        for (int i = 0; i < size; i++) {
            this.sources.add(sources.get(size - i - 1));
        }
        log.info("Total sources: {}", size);
    }

    private void addCacheFolder(List<AssetSource> sources) {
        Path cachePath = Paths.get(CACHE, "assets");
        if (Files.exists(cachePath)) {
            sources.add(new FsAssetSource(Paths.get(CACHE), "file:cache"));
            log.info("Found Cache directory");
        }
    }

    private void addModsJars(List<AssetSource> sources) {
        Path modsPath = instanceRoot.resolve("mods");
        if (Files.exists(modsPath)) {
            log.info("Found Mods directory");
            try {
                ModLoader modLoader = new ModLoader(modsPath);
                for (ModInfo mod : modLoader.getLoadedMods()) {
                    if (hasAssets(mod.getJarPath())) {
                        JarAssetSource source = new JarAssetSource(mod.getJarPath());
                        sources.add(source);
                    } else {
                        log.debug("Ignore mod: {} {} @ {}", mod.getModId(), mod.getName(), mod.getJarPath());
                    }
                }
            } catch (IOException e) {
                log.error("Error scanning mods", e);
            }
        }
    }

    private boolean hasAssets(Path jar) throws IOException {
        JarFile jarFile = new JarFile(jar.toFile());
        Enumeration<? extends JarEntry> entries = jarFile.entries();
        while (entries.hasMoreElements()) {
            JarEntry jarEntry = entries.nextElement();
            String name = jarEntry.getName();
            if (name.startsWith("assets") || name.startsWith("data")) {
                return true;
            }
        }
        return false;
    }

    public void addResourcePacks(List<AssetSource> sources) {
        Path resourcePacksPath = instanceRoot.resolve("resourcepacks");
        if (Files.exists(resourcePacksPath)) {
            log.info("Found Resource Packs directory");
            try (Stream<Path> files = Files.list(resourcePacksPath)) {
                List<Path> zipList = files.filter(p -> p.toString().endsWith(".zip")).sorted().toList();
                for (Path zip : zipList) {
                    try {
                        sources.add(new ZipAssetSource(zip));
                    } catch (IOException e) {
                        log.error("Error loading resource pack: {}", zip, e);
                    }
                }
            } catch (IOException e) {
                log.error("Error scanning resource packs", e);
            }
        }
    }

    public void addKubejs(List<AssetSource> sources) {
        Path kubejsPath = instanceRoot.resolve("kubejs");
        if (Files.exists(kubejsPath)) {
            log.info("Found KubeJS directory");
            sources.add(new FsAssetSource(kubejsPath, "file:kubejs"));
        }
    }

    public List<Asset> listAssets(String resourcePath) throws IOException {
        List<Asset> assets = new ArrayList<>();
        for (AssetSource source : sources) {
            assets.addAll(source.listAssets(resourcePath));
        }
        return assets;
    }

    public Asset getAsset(AssetKey assetKey) {
        return getAsset(assetKey.getResourcePath());
    }

    public Asset getAsset(String resourcePath) {
        // Check cache first
        if (resourceCache.containsKey(resourcePath)) {
            AssetSource source = resourceCache.get(resourcePath);
            try {
                return new Asset(resourcePath, source.getInputStream(resourcePath), source);
            } catch (IOException e) {
                // Cache invalid, remove and continue
                resourceCache.remove(resourcePath);
            }
        }

        for (AssetSource source : sources) {
            if (source.exists(resourcePath)) {
                try {
                    InputStream stream = source.getInputStream(resourcePath);
                    resourceCache.put(resourcePath, source);
                    return new Asset(resourcePath, stream, source);
                } catch (IOException e) {
                    log.error("Error reading resource: {} from {}", resourcePath, source, e);
                }
            }
        }
        return null;
    }

    public List<Asset> getAssets(AssetKey assetKey) {
        return getAssets(assetKey.getResourcePath());
    }

    public List<Asset> getAssets(String resourcePath) {
        List<Asset> assets = new ArrayList<>();
        // 逆序加载，然后者有机会被覆盖
        for (int i = sources.size() - 1; i >= 0; i--) {
            AssetSource source = sources.get(i);
            if (source.exists(resourcePath)) {
                try {
                    InputStream stream = source.getInputStream(resourcePath);
                    resourceCache.put(resourcePath, source);
                    assets.add( new Asset(resourcePath, stream, source) );
                } catch (IOException e) {
                    log.error("Error reading resource: {} from {}", resourcePath, source, e);
                }
            }
        }
        return assets;
    }

    /**
     * load book by id
     * @param bookId The book id. e.g. field_guide
     * @return The patchouli book
     * @throws IOException
     */
    public Book loadBook(String bookId) throws IOException {
        Language lang = Language.EN_US;
        // load book
        String bookPath = Constants.getBookPath(bookId);
        Asset bookAsset = getAsset(bookPath);
        if (bookAsset == null) {
            log.error("Book not found: {}", bookPath);
            throw new AssetNotFoundException("Book not found: " + bookPath);
        }

        Book book = JsonUtils.readFile(bookAsset.getInputStream(), Book.class);
        book.setLanguage(lang);
        book.setAssetSource(bookAsset);

        // load categories
        String categoryDir = Constants.getCategoryDir(bookId, lang.getKey());
        List<Asset> assets = listAssets(categoryDir);
        for (Asset asset : assets) {
            BookCategory category = JsonUtils.readFile(asset.getInputStream(), BookCategory.class);
            category.setAssetSource(categoryDir, asset);

            book.addCategory(category);
        }

        // load entries
        String entryDir = Constants.getEntryDir(bookId, lang.getKey());
        assets = listAssets(entryDir);
        for (Asset asset : assets) {
            BookEntry entry = JsonUtils.readFile(asset.getInputStream(), BookEntry.class);
            entry.setAssetSource(entryDir, asset);

            book.addEntry(entry);
        }

        book.sort();
        return book;
    }

    public Book loadBook(String bookId, Language lang, Book fallback) throws IOException {
        String bookPath = Constants.getBookPath(bookId);
        Asset bookAsset = getAsset(bookPath);

        Book book = JsonUtils.readFile(bookAsset.getInputStream(), Book.class);
        book.setLanguage(lang);
        book.setAssetSource(bookAsset);

        String categoryDir = Constants.getCategoryDir(bookId, lang.getKey());
        String fallbackCategoryDir = Constants.getCategoryDir();
        for (BookCategory category : fallback.getCategories()) {
            String path = Constants.getCategoryPath(lang.getKey(), category.getId());
            Asset asset = getAsset(path);
            if (asset != null) {
                BookCategory localizedCategory = JsonUtils.readFile(asset.getInputStream(), BookCategory.class);
                localizedCategory.setAssetSource(categoryDir, asset);
                book.addCategory(localizedCategory);
            } else {
                // fallback
                path = Constants.getCategoryPath(category.getId());
                asset = getAsset(path);
                BookCategory fallbackCategory = JsonUtils.readFile(asset.getInputStream(), BookCategory.class);
                fallbackCategory.setAssetSource(fallbackCategoryDir, asset);
                book.addCategory(fallbackCategory);
            }
        }

        String entryDir = Constants.getEntryDir(lang.getKey());
        String fallbackEntryDir = Constants.getEntryDir();
        for (BookEntry entry : fallback.getEntries()) {
            String path = Constants.getEntryPath(lang.getKey(), entry.getId());
            Asset asset = getAsset(path);
            if (asset != null) {
                BookEntry localizedEntry = JsonUtils.readFile(asset.getInputStream(), BookEntry.class);
                localizedEntry.setAssetSource(entryDir, asset);
                book.addEntry(localizedEntry);
            } else {
                // fallback
                path = Constants.getEntryPath(entry.getId());
                asset = getAsset(path);
                BookEntry fallbackEntry = JsonUtils.readFile(asset.getInputStream(), BookEntry.class);
                fallbackEntry.setAssetSource(fallbackEntryDir, asset);
                book.addEntry(fallbackEntry);
            }
        }

        book.sort();
        return book;
    }

    public Map<String, String> loadLang(String namespace, String lang) {
        Map<String, String> map = new HashMap<>();
        AssetKey assetKey = new AssetKey(namespace + ":" + lang, "lang", "assets", ".json");
        List<Asset> assets = getAssets(assetKey);
        for (Asset asset : assets) {
            try (JsonReader reader = new JsonReader(new InputStreamReader(asset.getInputStream(), StandardCharsets.UTF_8))) {
                Map<String, String> data = new HashMap<>();

                reader.beginObject();
                while (reader.hasNext()) {
                    String key = reader.nextName();
                    String value = reader.nextString();

                    if (data.containsKey(key)) {
                        log.warn("Duplicate lang key '{}' in {}: '{}' -> '{}'",
                                key, asset.getPath(), data.get(key), value);
                    }

                    data.put(key, value);
                }
                reader.endObject();

                map.putAll(data);
                log.debug("Loaded {} entries from {}", data.size(), asset.getPath());

            } catch (Exception e) {
                log.error("Error loading lang: {}", assetKey, e);
            }
        }
        return map;
    }

    public AssetKey getTextureKey(String path) {
        AssetKey assetKey;
        if (path.endsWith(".png")) {
            assetKey = new AssetKey(path, null, "assets", ".png");
        } else {
            assetKey = new AssetKey(path, "textures", "assets", ".png");
        }
        return assetKey;
    }

    public BufferedImage loadTexture(String path) {
        if (path != null && registeredImage.containsKey(path)) {
            return registeredImage.get(path);
        }

        AssetKey assetKey;
        if (path.endsWith(".png")) {
            assetKey = new AssetKey(path, null, "assets", ".png");
        } else {
            assetKey = new AssetKey(path, "textures", "assets", ".png");
        }

        BufferedImage image = loadTexture(assetKey);
        registeredImage.put(path, image);
        return image;
    }

    public BufferedImage loadTexture(AssetKey assetKey) {
        Asset asset = getAsset(assetKey);
        if (asset == null) {
            log.error("Texture not found: {}", assetKey);
            missingAssets.add(assetKey.getId());
            throw new AssetNotFoundException("Texture not found: " + assetKey.getResourcePath());
        }

        try {
            return ImageIO.read(asset.getInputStream());
        } catch (IOException e) {
            log.error("Error loading texture: {}", assetKey, e);
            throw new InternalException("Error loading texture: " + assetKey);
        }
    }

    public Asset loadResource(String resourceLocation, String resourceType, String resourceRoot, String resourceSuffix) {
        AssetKey assetKey = new AssetKey(resourceLocation, resourceType, resourceRoot, resourceSuffix);

        Asset asset = getAsset(assetKey.getResourcePath());
        if (asset == null) {
            missingAssets.add(resourceLocation);
            throw new AssetNotFoundException("Resource not found: " + resourceLocation + " in " + assetKey.getResourcePath());
        }
        return asset;
    }

    public BlockModel loadBlockModelWithState(String modelId) {
        // FIXME 这样对吗？有一些blockState文件中没有定义默认模型，导致 modelId 中没有 [] 时，无法正确找到映射的方块。
        BlockVariant blockVariant = parseBlockState(modelId);
        if (!blockVariant.hasProperties()) {
            // FIXME what to do if no variants found ?
            log.debug("No properties for blockStateId:{}", modelId);
            try {
                return loadBlockModel(modelId);
            } catch (Exception e) {
                log.error("Failed to load model: {}", modelId);
            }
        }

        if (blockModelCache.containsKey(modelId)) {
            return blockModelCache.get(modelId);
        }

        String blockStateId = blockVariant.getBlock();
        Map<String, String> state = blockVariant.getProperties();
        List<BlockState> list = loadBlockStates(blockVariant.getBlock());
        if (list == null || list.isEmpty()) {
            log.info("No blockstate found for {}", modelId);
            throw new AssetNotFoundException("No blockstates found: " + modelId);
        }

        for (BlockState blockState : list) {
            if (blockState.hasVariants()) {
                List<Variant> variants = blockState.selectByVariants(state);
                if (variants == null || variants.isEmpty()) {
                    log.info("BlockState: No matching variant found for '{}'", blockStateId);
                } else {
                    blockVariant.setVariants(variants);
                    Variant variant = BlockState.selectByWeight(variants);
                    blockVariant.setVariant(variant);
                    break;
                }
            } else if (blockState.hasMultipart()) {
                List<Variant> variants = blockState.selectByMultipart(state);

                if (variants == null || variants.isEmpty()) {
                    log.info("BlockState: No matching multipart case found for '{}'", blockStateId);
                } else {
                    blockVariant.setVariants(variants);
                    break;
                }
            } else {
                log.info("BlockState : Must be a 'variants' or 'multipart' block state: {}", blockStateId);
            }
        }

        BlockModel model = null;
        if (blockVariant.getVariant() != null) {
            model = loadModel(blockVariant.getVariant().getModel());
        } else if (blockVariant.getVariants() != null && !blockVariant.getVariants().isEmpty()){
            // FIXME 这里不太对，应该是同时返回多个model
            List<Variant> variants = blockVariant.getVariants();
            Variant variant;
            if (variants.size() > 1) {
                log.info("multi variants found, {} -> {}", modelId, variants);
                variant = BlockState.selectByWeight(variants);
            } else {
                variant = variants.getFirst();
            }
            model = loadModel(variant.getModel());
        } else {
            // 这是一段兼容逻辑。因为存在一些模型完全找不到任何匹配的数据
            for (BlockState b : list) {
                List<Variant> defaultVariant = b.getDefault();
                if (defaultVariant != null && !defaultVariant.isEmpty()) {
                    log.info("Use default variant for {}, {}", modelId, defaultVariant);

                    Variant defaultVar = BlockState.selectByWeight(defaultVariant);
                    model = loadModel(defaultVar.getModel());
                    break;
                }
            }
            if (model == null) {
                log.warn("No variants found for: {}", blockVariant);
                throw new InternalException("BlockVariants not found:" + modelId);
            }
        }

        blockModelCache.put(modelId, model);
        return model;
    }

    public static BlockVariant parseBlockState(String blockStateId) {
        BlockVariant variant = new BlockVariant();

        int index = blockStateId.indexOf('[');
        if (index > 0) {
            String block = blockStateId.substring(0, index);
            String props = blockStateId.substring(index + 1, blockStateId.length() - 1);
            Map<String, String> properties = parseBlockProperties(props);
            variant.setBlock(block);
            variant.setProperties(properties);
        } else {
            variant.setBlock(blockStateId);
            variant.setProperties(new HashMap<>());
        }

        return variant;
    }

    public static Map<String, String> parseBlockProperties(String properties) {
        Map<String, String> state = new HashMap<>();
        if (properties.contains("=")) {
            String[] pairs = properties.split(",");
            for (String pair : pairs) {
                String[] keyValue = pair.split("=");
                state.put(keyValue[0], keyValue[1]);
            }
        }
        return state;
    }

    public BlockState loadBlockState(String id) {
        Asset asset = loadResource(id, "blockstates", "assets", ".json");
        try {
            return JsonUtils.readFile(asset.getInputStream(), BlockState.class);
        } catch (IOException e) {
            log.error("Failed to read blockstate:{}, message: {}", id, e.getMessage());
            throw new InternalException("Failed to read id: " + id);
        }
    }

    public List<BlockState> loadBlockStates(String id) {
        List<BlockState> list = new ArrayList<>();
        AssetKey assetKey = new AssetKey(id, "blockstates", "assets", ".json");

        List<Asset> assets = getAssets(assetKey);
        for (Asset asset : assets) {
            try {
                BlockState blockState = JsonUtils.readFile(asset.getInputStream(), BlockState.class);
                list.add(blockState);
            } catch (IOException e) {
                log.error("Failed to read blockstate:{}, message: {}", asset.getPath(), e.getMessage());
            }
        }

        return list;
    }

    public BlockVariant loadBlockVariant(String blockStateId) {
        BlockVariant blockVariant = parseBlockState(blockStateId);
        String block = blockVariant.getBlock();
        if (!blockVariant.hasProperties()) {
            // FIXME what to do if no variants found ?
            log.debug("No properties for blockStateId:{}", blockStateId);
        }

        Map<String, String> state = blockVariant.getProperties();

        BlockState blockState = loadBlockState(blockVariant.getBlock());

        if (blockState.hasVariants()) {
            List<Variant> variants = blockState.selectByVariants(state);
            blockVariant.setVariants(variants);

            if (variants == null || variants.isEmpty()) {
                throw new RuntimeException("BlockState: No matching variant found for '" + blockStateId + "'");
            }

            Variant variant = blockState.selectByWeight(variants);
            blockVariant.setVariant(variant);
            return blockVariant;
        } else if (blockState.hasMultipart()) {
            log.debug("blockstate: {}, multipart: {}", block, blockState.getMultipart());
            List<Variant> variants = blockState.selectByMultipart(state);

            if (variants == null || variants.isEmpty()) {
                throw new RuntimeException("BlockState: No matching multipart case found for '" + blockStateId + "'");
            }

            blockVariant.setVariants(variants);
            return blockVariant;
        } else {
            throw new RuntimeException("BlockState : Must be a 'variants' or 'multipart' block state: '" + blockStateId + "'");
        }
    }

    /**
     * 根据权重选择变体
     */
    private Variant selectVariantByWeight(List<Variant> variants) {
        if (variants.size() == 1) {
            return variants.getFirst();
        }

        // 计算总权重
        int totalWeight = variants.stream().mapToInt(Variant::getWeight).sum();

        // 随机选择
        Random random = new Random();
        int randomValue = random.nextInt(totalWeight);
        int currentWeight = 0;

        for (Variant variant : variants) {
            currentWeight += variant.getWeight();
            if (randomValue < currentWeight) {
                return variant;
            }
        }

        // 如果权重计算有问题，返回第一个
        return variants.getFirst();
    }

    public BlockModel loadModel(String modelId) {
        String resourceLocation;
        if (modelId.indexOf(':') < 0) {
            resourceLocation = "minecraft:" + modelId;
        } else {
            resourceLocation = modelId;
        }
        if (blockModelCache.containsKey(resourceLocation)) {
            log.debug("Hitting block model cache: {}", resourceLocation);
            return blockModelCache.get(resourceLocation);
        }

        Asset asset = loadResource(resourceLocation, "models", "assets", ".json");
        try {
            BlockModel model = JsonUtils.readFile(asset.getInputStream(), BlockModel.class);
            model.getInherits().add(resourceLocation);

            String parent = model.getParent();
            if (parent != null && !parent.isEmpty()) {
                BlockModel parentModel = loadModel(parent);
                model.setParentModel(parentModel);
            }

            model.mergeWithParent();// important

            blockModelCache.put(resourceLocation, model);
            return model;
        } catch (Exception e) {
            log.error("Load model failed, {}, message: {}", resourceLocation, e.getMessage());
            throw new InternalException("Load model failed");
        }
    }

    public BlockModel loadBlockModel(String blockId) {
        String resourceLocation;
        if (blockId.indexOf(':') < 0) {
            resourceLocation = "minecraft:" + blockId;
        } else {
            resourceLocation = blockId;
        }
        if (blockModelCache.containsKey(resourceLocation)) {
            log.debug("Hitting block model cache: {}", resourceLocation);
            return blockModelCache.get(resourceLocation);
        }

        // TODO 支持blockstate，例如 // tfc:charcoal_forge[heat_level=7]

        Asset asset = loadResource(blockId, "models/block", "assets", ".json");
        try {
            BlockModel model = JsonUtils.readFile(asset.getInputStream(), BlockModel.class);
            model.getInherits().add(resourceLocation);

            String parent = model.getParent();
            if (parent != null && !parent.isEmpty()) {
                BlockModel parentModel = loadModel(parent);
                model.setParentModel(parentModel);
            }

            model.mergeWithParent();// important
            blockModelCache.put(resourceLocation, model);
            return model;
        } catch (Exception e) {
            log.warn("Failed to load block model: {}", blockId, e);
            throw new InternalException("Failed to load block model: " + blockId);
        }
    }

    public BlockModel loadItemModel(String itemId) {
        String resourceLocation;
        if (itemId.indexOf(':') < 0) {
            resourceLocation = "minecraft:" + itemId;
        } else {
            resourceLocation = itemId;
        }
        if (itemModelCache.containsKey(resourceLocation)) {
            log.debug("Hitting item model cache: {}", resourceLocation);
            return itemModelCache.get(resourceLocation);
        }

        Asset asset = loadResource(itemId, "models/item", "assets", ".json");
        try {
            BlockModel model = JsonUtils.readFile(asset.getInputStream(), BlockModel.class);
            model.getInherits().add(resourceLocation);

            String parent = model.getParent();
            if (parent != null && !parent.isEmpty()) {
                BlockModel parentModel = loadModel(parent);
                model.setParentModel(parentModel);
            }

            model.mergeWithParent();// important
            itemModelCache.put(resourceLocation, model);
            return model;
        } catch (Exception e) {
            log.warn("Failed to load item model: {}", itemId, e);
            throw new InternalException("Failed to load item model: " + itemId);
        }
    }

    public Map<String, Object> loadRecipe(String recipeId) {
        if (recipeCache.containsKey(recipeId)) {
            return recipeCache.get(recipeId);
        }
        Asset asset = loadResource(recipeId, "recipes", "data", ".json");
        if (asset == null) {
            log.error("Recipe not found: {}", recipeId);
            throw new InternalException("Recipe not found: " + recipeId);
        }

        Type mapType = new TypeToken<Map<String, Object>>() {}.getType();
        try {
            Map<String, Object> data = JsonUtils.readFile(asset.getInputStream(), mapType);
            recipeCache.put(recipeId, data);
            return data;
        } catch (IOException e) {
            log.error("Error loading recipe: {}", recipeId, e);
            throw new InternalException("Error loading recipe: " + recipeId);
        }
    }

    //////////// tags

    /**
     * 加载流体标签
     */
    public List<String> loadFluidTag(String identifier) {
        return sortTagElements(identifier, this::loadFluidTagData);
    }

    /**
     * 加载物品标签
     */
    public List<String> loadItemTag(String identifier) {
        return sortTagElements(identifier, this::loadItemTagData);
    }

    /**
     * 加载方块标签
     */
    public List<String> loadBlockTag(String identifier) {
        return sortTagElements(identifier, this::loadBlockTagData);
    }

    /**
     * 排序并去重标签元素
     */
    private List<String> sortTagElements(String identifier,
                                         Function<String, List<Tags>> loadFunc) {
        Set<String> tagSet = new LinkedHashSet<>(); // 保持插入顺序但去重
        loadTagElementsRecursive(identifier, loadFunc, tagSet, new HashSet<>());
        return new ArrayList<>(tagSet);
    }

    /**
     * 递归加载标签元素
     */
    private void loadTagElementsRecursive(String identifier,
                                          Function<String, List<Tags>> loadFunc,
                                          Set<String> result,
                                          Set<String> visited) {
        // 防止循环引用
        if (visited.contains(identifier)) {
            return;
        }
        visited.add(identifier);

        try {
            List<Tags> tagsList = loadFunc.apply(identifier);

            for (Tags json : tagsList) {
                List<TagElement> values = json.getValues();
                for (TagElement element : values) {
                    String id = element.getId();
                    if (id.startsWith("#")) {
                        String nestedTag = id.substring(1);
                        loadTagElementsRecursive(nestedTag, loadFunc, result, visited);
                    } else {
                        result.add(id);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to load tag: {}", identifier, e);
        }
    }

    /// `data/<namespace>/tags/<数据包路径>` for datapack
    /// `data/<namespace>/tags/blocks` for blocks
    /// `data/<namespace>/tags/entity_types` for entity_types
    /// `data/<namespace>/tags/fluids` for fluids
    /// `data/<namespace>/tags/items` for items
    public List<Tags> loadBlockTagData(String tag) {
        AssetKey assetKey = new AssetKey(tag, "tags/blocks", "data", ".json");
        return parseTagAsset(assetKey);
    }

    public List<Tags> loadItemTagData(String tag) {
        AssetKey assetKey = new AssetKey(tag, "tags/items", "data", ".json");
        return parseTagAsset(assetKey);
    }

    public List<Tags> loadFluidTagData(String tag)  {
        AssetKey assetKey = new AssetKey(tag, "tags/fluids", "data", ".json");
        return parseTagAsset(assetKey);
    }

    private List<Tags> parseTagAsset(AssetKey assetKey) {
        if (tagsCache.containsKey(assetKey.getId())) {
            return tagsCache.get(assetKey.getId());
        }
        List<Tags> tagsList = new ArrayList<>();
        List<Asset> assets = getAssets(assetKey);
        for (Asset asset : assets) {
            try {
                Tags tags = JsonUtils.readFile(asset.getInputStream(), Tags.class);
                tagsList.add(tags);
            } catch (Exception e) {
                log.error("Failed to parse tag asset: {}", asset.getPath(), e);
            }
        }

        tagsCache.put(assetKey.getId(), tagsList);
        return tagsList;
    }
}