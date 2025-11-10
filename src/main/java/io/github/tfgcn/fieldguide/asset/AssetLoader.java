package io.github.tfgcn.fieldguide.asset;

import com.google.gson.reflect.TypeToken;
import io.github.tfgcn.fieldguide.JsonUtils;
import io.github.tfgcn.fieldguide.MCMeta;
import io.github.tfgcn.fieldguide.book.Book;
import io.github.tfgcn.fieldguide.Versions;
import io.github.tfgcn.fieldguide.InternalError;
import lombok.extern.slf4j.Slf4j;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Stream;

@Slf4j
public class AssetLoader {
    private final List<AssetSource> sources;
    private final Map<String, AssetSource> resourceCache;
    private final Path instanceRoot;

    public AssetLoader(Path instanceRoot) {
        this.instanceRoot = instanceRoot;
        this.sources = new ArrayList<>();
        this.resourceCache = new HashMap<>();
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
        String domain;
        String res;

        int index = resourceLocation.indexOf(':');
        if (index <= 0) {
            domain = "minecraft";// 默认为minecraft命名空间
            res = resourceLocation;
            log.info("Assuming resource resourceLocation: {}", resourceLocation);
        } else {
            domain = resourceLocation.substring(0, index);
            res = resourceLocation.substring(index + 1);
        }

        StringBuilder sb = new StringBuilder();
        sb.append(resourceRoot).append("/").append(domain);
        if (resourceType != null && !resourceType.isEmpty()) {
            sb.append("/").append(resourceType);
        }
        sb.append("/").append(res);
        if (!res.endsWith(resourceSuffix)) {
            sb.append(resourceSuffix);
        }

        String resourcePath = sb.toString();
        Asset asset = getAsset(resourcePath);
        if (asset == null) {
            log.error("Resource not found: {}, in {}", resourceLocation, resourcePath);
        }
        return asset;
    }

    public void loadModel(String path) {

        // Block States
        // assets/{namespace}/blockstates/{res}.json
        // Block Model
        // assets/{namespace}/models/block/{res}.json
        // Item Model
        // assets/{namespace}/models/item/{res}.json
    }

    public Map<String, Object> loadRecipe(String recipeId) {
        // 1.20.1 "recipes", 1.20.1+ "recipe"
        Asset asset = loadResource(recipeId, "recipes", "data", ".json");
        if (asset == null) {
            log.error("Recipe not found: {}", recipeId);
            throw new InternalError("Recipe not found: " + recipeId);
        }

        Type mapType = new TypeToken<Map<String, Object>>() {}.getType();
        try {
            return JsonUtils.readFile(asset.getInputStream(), mapType);
        } catch (IOException e) {
            log.error("Error loading recipe: {}", recipeId, e);
            throw new InternalError("Error loading recipe: " + recipeId);
        }
    }

    public BufferedImage loadExplicitTexture(String path) {
        Asset asset = loadResource(path, "", "assets", ".png");
        if (asset == null) {
            log.error("Texture not found: {}", path);
            throw new InternalError("Texture not found: " + path);
        }
        try {
            return ImageIO.read(asset.getInputStream());
        } catch (IOException e) {
            log.error("Error loading texture: {}", path, e);
            throw new InternalError("Error loading texture: " + path);
        }
    }
}