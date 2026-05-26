package team.terrafirmgreg.fieldguide.export;

import lombok.Getter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Single entry point for opening a guide-export directory.
 */
@Getter
public class ExportBundle {

    private final Path exportRoot;
    private final Map<String, Object> manifest;
    private final Map<String, Object> meta;
    private final RecipeBundleLoader recipes;
    private final TagBundleLoader tags;
    private final TagMemberIndex tagMembers;
    private final LangCatalog langs;
    private final IconCatalog icons;
    private final RecipeImageCatalog recipeImages;
    private final MultiblockRegistry multiblocks;
    private final ExportAssetAccess assets;
    private final ExportBookLoader books;

    private ExportBundle(
            Path exportRoot,
            Map<String, Object> manifest,
            Map<String, Object> meta,
            RecipeBundleLoader recipes,
            TagBundleLoader tags,
            TagMemberIndex tagMembers,
            LangCatalog langs,
            IconCatalog icons,
            RecipeImageCatalog recipeImages,
            MultiblockRegistry multiblocks,
            ExportAssetAccess assets,
            ExportBookLoader books) {
        this.exportRoot = exportRoot;
        this.manifest = manifest;
        this.meta = meta;
        this.recipes = recipes;
        this.tags = tags;
        this.tagMembers = tagMembers;
        this.langs = langs;
        this.icons = icons;
        this.recipeImages = recipeImages;
        this.multiblocks = multiblocks;
        this.assets = assets;
        this.books = books;
    }

    public static ExportBundle open(Path exportDir) throws IOException {
        Path root = exportDir.normalize().toAbsolutePath();
        Path manifestFile = root.resolve("manifest.json");
        Path assetsDir = root.resolve("assets");
        if (!Files.isRegularFile(manifestFile)) {
            throw new IllegalArgumentException("Missing manifest.json under " + root);
        }
        if (!Files.isDirectory(assetsDir)) {
            throw new IllegalArgumentException("Missing assets/ under " + root);
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> manifest = team.terrafirmgreg.fieldguide.gson.JsonUtils.GSON.fromJson(
                Files.readString(manifestFile),
                Map.class);
        Map<String, Object> meta = readMeta(root.resolve("meta.json"));
        TagMemberIndex tagMembers = TagMemberIndex.load(root);
        RecipeBundleLoader recipeLoader = RecipeBundleLoader.load(root);
        ExportModelLoader modelLoader = new ExportModelLoader(root, tagMembers, recipeLoader, root.resolve("dist"));
        ExportAssetAccess assetAccess = new ExportAssetAccess(root, modelLoader);
        return new ExportBundle(
                root,
                manifest != null ? manifest : Map.of(),
                meta,
                RecipeBundleLoader.load(root),
                TagBundleLoader.load(root),
                tagMembers,
                LangCatalog.load(root),
                IconCatalog.load(root),
                RecipeImageCatalog.load(root),
                MultiblockRegistry.load(root),
                assetAccess,
                new ExportBookLoader(modelLoader));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> readMeta(Path metaFile) throws IOException {
        if (!Files.isRegularFile(metaFile)) {
            return Map.of();
        }
        Map<String, Object> meta = team.terrafirmgreg.fieldguide.gson.JsonUtils.GSON.fromJson(
                Files.readString(metaFile), Map.class);
        return meta != null ? meta : Map.of();
    }
}
