package team.terrafirmgreg.fieldguide.export;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import team.terrafirmgreg.fieldguide.asset.Asset;
import team.terrafirmgreg.fieldguide.data.minecraft.blockmodel.BlockModel;
import team.terrafirmgreg.fieldguide.data.minecraft.blockstate.BlockState;
import team.terrafirmgreg.fieldguide.data.minecraft.blockstate.BlockVariant;
import team.terrafirmgreg.fieldguide.data.minecraft.blockstate.Variant;
import team.terrafirmgreg.fieldguide.exception.AssetNotFoundException;
import team.terrafirmgreg.fieldguide.exception.InternalException;
import team.terrafirmgreg.fieldguide.gson.JsonUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Export-only asset/model loader: reads {@code guide-export/assets/} and {@code meta.json} blockstates.
 * No mod jars or datapack tag recursion.
 */
@Slf4j
@Getter
public class ExportModelLoader {

    private final Path exportRoot;
    private Path outputDir;
    private final FsAssetSource source;
    private final TagMemberIndex tagMembers;
    private final ExportAssetStats assetStats = new ExportAssetStats();

    private final Map<String, AssetSourceCache> resourceCache = new HashMap<>();
    private final Map<String, BlockModel> blockModelCache = new TreeMap<>();
    private final Map<String, BlockModel> itemModelCache = new TreeMap<>();

    public ExportModelLoader(Path exportRoot, TagMemberIndex tagMembers) {
        this(exportRoot, tagMembers, exportRoot.resolve("dist"));
    }

    public ExportModelLoader(Path exportRoot, TagMemberIndex tagMembers, Path outputDir) {
        this.exportRoot = exportRoot.normalize().toAbsolutePath();
        this.outputDir = outputDir;
        this.tagMembers = tagMembers != null ? tagMembers : TagMemberIndex.load(exportRoot);
        this.source = new FsAssetSource(this.exportRoot);
        initBuiltinModels();
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
        itemModelCache.put("minecraft:item/generated", itemGenerated);
        itemModelCache.put("minecraft:builtin/generated", builtinGenerated);
    }

    public List<Asset> listAssets(String resourcePath) throws IOException {
        return source.listAssets(resourcePath);
    }

    public Asset getAsset(String resourcePath) {
        if (resourceCache.containsKey(resourcePath)) {
            AssetSourceCache cached = resourceCache.get(resourcePath);
            try {
                return new Asset(resourcePath, source.getInputStream(resourcePath), source);
            } catch (IOException e) {
                resourceCache.remove(resourcePath);
            }
        }
        if (source.exists(resourcePath)) {
            try {
                resourceCache.put(resourcePath, new AssetSourceCache());
                return new Asset(resourcePath, source.getInputStream(resourcePath), source);
            } catch (IOException e) {
                log.error("Error reading resource: {}", resourcePath, e);
            }
        }
        return null;
    }

    public List<Asset> getAssets(String resourcePath) {
        List<Asset> assets = new ArrayList<>();
        if (source.exists(resourcePath)) {
            try {
                assets.add(new Asset(resourcePath, source.getInputStream(resourcePath), source));
            } catch (IOException e) {
                log.error("Error reading resource: {}", resourcePath, e);
            }
        }
        return assets;
    }

    public Asset loadResource(String resourceLocation, String resourceType, String resourceRoot, String resourceSuffix) {
        ExportAssetKey assetKey = new ExportAssetKey(resourceLocation, resourceType, resourceRoot, resourceSuffix);
        Asset asset = getAsset(assetKey.getResourcePath());
        if (asset == null) {
            throw new AssetNotFoundException("Resource not found: " + resourceLocation + " in " + assetKey.getResourcePath());
        }
        return asset;
    }

    public BufferedImage loadTexture(ExportAssetKey assetKey) {
        Asset asset = getAsset(assetKey.getResourcePath());
        if (asset == null) {
            log.error("Texture not found: {}", assetKey);
            assetStats.addMissingTexture(assetKey.getId());
            throw new AssetNotFoundException("Texture not found: " + assetKey.getResourcePath());
        }
        try {
            return ImageIO.read(asset.getInputStream());
        } catch (IOException e) {
            throw new InternalException("Error loading texture: " + assetKey);
        }
    }

    public BufferedImage loadTexture(String resourceLocation) {
        return loadTexture(getTextureKey(resourceLocation));
    }

    public ExportAssetKey getTextureKey(String path) {
        if (path.endsWith(".png")) {
            return new ExportAssetKey(path, null, "assets", ".png");
        }
        return new ExportAssetKey(path, "textures", "assets", ".png");
    }

    public void setOutputDir(Path outputDir) {
        this.outputDir = outputDir.normalize().toAbsolutePath();
    }

    public List<String> loadBlockTag(String tagId) {
        List<String> members = tagMembers.getBlockMembers(tagId);
        if (members.isEmpty()) {
            log.warn("No block tag members in export index for {}", tagId);
        }
        return members;
    }

    public List<String> loadItemTag(String tagId) {
        return tagMembers.getItemMembers(tagId);
    }

    public List<String> loadFluidTag(String tagId) {
        return tagMembers.getFluidMembers(tagId);
    }

    public BlockModel loadItemModel(String itemId) {
        String resourceLocation = itemId.indexOf(':') < 0 ? "minecraft:" + itemId : itemId;
        if (itemModelCache.containsKey(resourceLocation)) {
            return itemModelCache.get(resourceLocation);
        }
        Asset asset = loadResource(itemId, "models/item", "assets", ".json");
        try {
            BlockModel model = JsonUtils.readFile(asset.getInputStream(), BlockModel.class);
            model.getInherits().add(resourceLocation);
            String parent = model.getParent();
            if (parent != null && !parent.isEmpty()) {
                model.setParentModel(loadModel(parent));
            }
            model.mergeWithParent();
            itemModelCache.put(resourceLocation, model);
            return model;
        } catch (Exception e) {
            throw new InternalException("Failed to load item model: " + itemId);
        }
    }

    public BlockModel loadBlockModelWithState(String modelId) {
        BlockVariant blockVariant = parseBlockState(modelId);
        if (!blockVariant.hasProperties()) {
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
            throw new AssetNotFoundException("No blockstates found: " + modelId);
        }

        for (BlockState blockState : list) {
            if (blockState.hasVariants()) {
                List<Variant> variants = blockState.selectByVariants(state);
                if (variants != null && !variants.isEmpty()) {
                    blockVariant.setVariants(variants);
                    blockVariant.setVariant(BlockState.selectByWeight(variants));
                    break;
                }
            } else if (blockState.hasMultipart()) {
                List<Variant> variants = blockState.selectByMultipart(state);
                if (variants != null && !variants.isEmpty()) {
                    blockVariant.setVariants(variants);
                    break;
                }
            }
        }

        BlockModel model = null;
        if (blockVariant.getVariant() != null) {
            model = loadModel(blockVariant.getVariant().getModel());
        } else if (blockVariant.getVariants() != null && !blockVariant.getVariants().isEmpty()) {
            List<Variant> variants = blockVariant.getVariants();
            Variant variant = variants.size() > 1 ? BlockState.selectByWeight(variants) : variants.get(0);
            model = loadModel(variant.getModel());
        } else {
            for (BlockState b : list) {
                List<Variant> defaultVariant = b.getDefault();
                if (defaultVariant != null && !defaultVariant.isEmpty()) {
                    Variant defaultVar = BlockState.selectByWeight(defaultVariant);
                    model = loadModel(defaultVar.getModel());
                    break;
                }
            }
            if (model == null) {
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
            variant.setBlock(block);
            variant.setProperties(parseBlockProperties(props));
        } else {
            variant.setBlock(blockStateId);
            variant.setProperties(new HashMap<>());
        }
        return variant;
    }

    public static Map<String, String> parseBlockProperties(String properties) {
        Map<String, String> state = new HashMap<>();
        if (properties.contains("=")) {
            for (String pair : properties.split(",")) {
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
            throw new InternalException("Failed to read blockstate: " + id);
        }
    }

    public List<BlockState> loadBlockStates(String id) {
        List<BlockState> list = new ArrayList<>();
        ExportAssetKey assetKey = new ExportAssetKey(id, "blockstates", "assets", ".json");
        for (Asset asset : getAssets(assetKey.getResourcePath())) {
            try {
                list.add(JsonUtils.readFile(asset.getInputStream(), BlockState.class));
            } catch (IOException e) {
                log.error("Failed to read blockstate: {}", asset.getPath(), e);
            }
        }
        return list;
    }

    public BlockModel loadModel(String modelId) {
        String resourceLocation = modelId.indexOf(':') < 0 ? "minecraft:" + modelId : modelId;
        if (blockModelCache.containsKey(resourceLocation)) {
            return blockModelCache.get(resourceLocation);
        }
        Asset asset = loadResource(resourceLocation, "models", "assets", ".json");
        try {
            BlockModel model = JsonUtils.readFile(asset.getInputStream(), BlockModel.class);
            model.getInherits().add(resourceLocation);
            String parent = model.getParent();
            if (parent != null && !parent.isEmpty()) {
                model.setParentModel(loadModel(parent));
            }
            model.mergeWithParent();
            blockModelCache.put(resourceLocation, model);
            return model;
        } catch (Exception e) {
            throw new InternalException("Load model failed: " + resourceLocation);
        }
    }

    public BlockModel loadBlockModel(String blockId) {
        String resourceLocation = blockId.indexOf(':') < 0 ? "minecraft:" + blockId : blockId;
        if (blockModelCache.containsKey(resourceLocation)) {
            return blockModelCache.get(resourceLocation);
        }
        Asset asset = loadResource(blockId, "models/block", "assets", ".json");
        try {
            BlockModel model = JsonUtils.readFile(asset.getInputStream(), BlockModel.class);
            model.getInherits().add(resourceLocation);
            String parent = model.getParent();
            if (parent != null && !parent.isEmpty()) {
                model.setParentModel(loadModel(parent));
            }
            model.mergeWithParent();
            blockModelCache.put(resourceLocation, model);
            return model;
        } catch (Exception e) {
            throw new InternalException("Failed to load block model: " + blockId);
        }
    }

    private record AssetSourceCache() {}
}
