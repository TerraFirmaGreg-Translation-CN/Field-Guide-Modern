package io.github.tfgcn.fieldguide.asset;

import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import io.github.tfgcn.fieldguide.JsonUtils;
import io.github.tfgcn.fieldguide.MCMeta;
import io.github.tfgcn.fieldguide.exception.AssetNotFoundException;
import io.github.tfgcn.fieldguide.mc.BlockModel;
import io.github.tfgcn.fieldguide.mc.TagElement;
import io.github.tfgcn.fieldguide.mc.Tags;
import io.github.tfgcn.fieldguide.patchouli.Book;
import io.github.tfgcn.fieldguide.Versions;
import io.github.tfgcn.fieldguide.exception.InternalException;
import lombok.extern.slf4j.Slf4j;

import javax.imageio.ImageIO;
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
import java.util.function.Function;
import java.util.stream.Stream;

@Slf4j
public class AssetLoader {

    private final Path instanceRoot;
    private final List<AssetSource> sources;
    private final Map<String, AssetSource> resourceCache;
    private final Map<String, BlockModel> modelCache;
    private final Map<String, List<Tags>> tagsCache;

    public AssetLoader(Path instanceRoot) {
        this.instanceRoot = instanceRoot;
        this.sources = new ArrayList<>();
        this.resourceCache = new HashMap<>();
        this.modelCache = new TreeMap<>();
        this.tagsCache = new TreeMap<>();
        initializeSources();
    }

    private void initializeSources() {
        log.info("Initializing AssetManager for: {}", instanceRoot);
        
        // KubeJS
        Path kubejsPath = instanceRoot.resolve("kubejs");
        if (Files.exists(kubejsPath)) {
            log.info("Found KubeJS directory");
            sources.add(new FsAssetSource(kubejsPath, "file:kubejs"));
        }

        // Mod JARs
        Path modsPath = instanceRoot.resolve("mods");
        if (Files.exists(modsPath)) {
            try (Stream<Path> jars = Files.list(modsPath)) {
                List<Path> jarList = jars
                    .filter(p -> p.toString().endsWith(".jar"))
                    .sorted()
                    .toList();

                for (Path jar : jarList) {
                    try {
                        sources.add(new JarAssetSource(jar));
                    } catch (IOException e) {
                        log.error("Error loading JAR: {}", jar, e);
                    }
                }
            } catch (IOException e) {
                log.error("Error scanning mods", e);
            }
        }

        log.info("Total sources: {}", sources.size());
    }

    public void addMcClientSource() {
        if (!MCMeta.ENABLED) {
            log.info("Skipping client JAR loading because MCMeta is disabled");
            return;
        }
        Path forgeJar = Paths.get(MCMeta.CACHE, MCMeta.getForgeJarName(Versions.MC_VERSION));
        if (Files.exists(forgeJar)) {
            try {
                sources.add(new JarAssetSource(forgeJar));
            } catch (IOException e) {
                log.error("Error loading Forge JAR: {}", forgeJar, e);
            }
        }

        Path clientJar = Paths.get(MCMeta.CACHE, MCMeta.getClientJarName(Versions.MC_VERSION));
        if (Files.exists(clientJar)) {
            try {
                sources.add(new JarAssetSource(clientJar));
            } catch (IOException e) {
                log.error("Error loading client JAR: {}", clientJar, e);
            }
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

    public Map<String, List<Book>> findAllPatchouliBooks() {
        Map<String, List<Book>> booksByMod = new HashMap<>();
        
        for (AssetSource source : sources) {
            List<String> bookPaths = source.findPatchouliBooks();
            for (String bookPath : bookPaths) {
                try {
                    String[] parts = bookPath.split("/");
                    if (parts.length < 4) {
                        continue;
                    }
                    String modId = parts[1]; // data/modid/... or assets/modid/...
                    String bookId = parts[3]; // .../patchouli_books/bookid/...

                    Book book = new Book(modId, bookId, source.getSourceId());
                    booksByMod.computeIfAbsent(modId, k -> new ArrayList<>()).add(book);

                    System.out.println("Found book: " + book);
                } catch (Exception e) {
                    System.err.println("Error parsing book path: " + bookPath + " - " + e.getMessage());
                }
            }
        }
        
        return booksByMod;
    }

    public void generateSourceReport() {
        System.out.println("\n=== Resource Source Report ===");
        System.out.println("Instance Root: " + instanceRoot);
        System.out.println("\nSources (by priority):");
        
        for (AssetSource source : sources) {
            List<String> books = source.findPatchouliBooks();
            System.out.println("  " + source + " - " + books.size() + " books");
            
            for (String book : books) {
                System.out.println("    - " + book);
            }
        }
        
        Map<String, List<Book>> allBooks = findAllPatchouliBooks();
        System.out.println("\nTotal books by mod:");
        for (Map.Entry<String, List<Book>> entry : allBooks.entrySet()) {
            System.out.println("  " + entry.getKey() + ": " + entry.getValue().size() + " books");
        }
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

    public BufferedImage loadTexture(String path) {
        Asset asset;
        if (path.endsWith(".png")) {
            asset = loadResource(path, null, "assets", ".png");
        } else {
            asset = loadResource(path, "textures", "assets", ".png");
        }
        if (asset == null) {
            log.error("Texture not found: {}", path);
            return null;
        }

        try {
            return ImageIO.read(asset.getInputStream());
        } catch (IOException e) {
            log.error("Error loading texture: {}", path, e);
        }
        return null;
    }

    public Asset loadResource(String path, String resourceRoot, String resourceSuffix) {
        return loadResource(path, null, resourceRoot, resourceSuffix);
    }

    public Asset loadResource(String resourceLocation, String resourceType, String resourceRoot, String resourceSuffix) {
        AssetKey assetKey = new AssetKey(resourceLocation, resourceType, resourceRoot, resourceSuffix);

        Asset asset = getAsset(assetKey.getResourcePath());
        if (asset == null) {
            log.error("Resource not found: {}, in {}", resourceLocation, assetKey);
            throw new AssetNotFoundException("Resource not found: " + resourceLocation + " in " + assetKey.getResourcePath());
        }
        return asset;
    }

    public void loadModelByType(String path) {

        // Block States
        // assets/{namespace}/blockstates/{res}.json
        // Block Model
        // assets/{namespace}/models/block/{res}.json
        // Item Model
        // assets/{namespace}/models/item/{res}.json
    }

    public BlockModel loadModel(String modelId) {
        String path;
        if (modelId.indexOf(':') < 0) {
            path = "minecraft:" + modelId;
        } else {
            path = modelId;
        }
        if (modelCache.containsKey(path)) {
            return modelCache.get(path);
        }

        Asset asset = loadResource(path, "models", "assets", ".json");
        try {
            BlockModel model = JsonUtils.readFile(asset.getInputStream(), BlockModel.class);

            String parent = model.getParent();
            if (parent != null && !parent.isEmpty()) {
                log.info("model parent: {}", parent);
                BlockModel parentModel = loadModel(parent);
                model.setParentModel(parentModel);
            } else {
                log.info("no parent for: {}", path);
            }

            modelCache.put(path, model);
            return model;
        } catch (Exception e) {
            throw new InternalException("");
        }
    }

    public BlockModel loadItemModel(String itemId) {
        Asset asset = loadResource(itemId, "models/item", "assets", ".json");
        try {
            return JsonUtils.readFile(asset.getInputStream(), BlockModel.class);
        } catch (Exception e) {
            log.warn("Failed to load item model: {}", itemId, e);
            throw new InternalException("Failed to load item model: " + itemId);
        }
    }

    Map<String, Map<String, Object>> recipeCache = new TreeMap<>();
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

    public BufferedImage loadExplicitTexture(String path) {
        Asset asset = loadResource(path, "", "assets", ".png");
        if (asset == null) {
            log.error("Texture not found: {}", path);
            throw new InternalException("Texture not found: " + path);
        }
        try {
            return ImageIO.read(asset.getInputStream());
        } catch (IOException e) {
            log.error("Error loading texture: {}", path, e);
            throw new InternalException("Error loading texture: " + path);
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
                log.info("Load tag, path: {}, source: {}", asset.getPath(), asset.getSource());
                tagsList.add(tags);
            } catch (Exception e) {
                log.error("Failed to parse tag asset: {}", asset.getPath(), e);
            }
        }

        tagsCache.put(assetKey.getId(), tagsList);
        return tagsList;
    }
}