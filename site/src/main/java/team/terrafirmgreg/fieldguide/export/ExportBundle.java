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
    private final TagBundleLoader tags;
    private final TagMemberIndex tagMembers;
    private final LangCatalog langs;
    private final IconCatalog icons;
    private final MultiblockRegistry multiblocks;
    private final ExportAssetAccess assets;
    private final ExportBookLoader books;
    private final Map<String, String> recipeMountIds;

    private ExportBundle(
            Path exportRoot,
            Map<String, Object> manifest,
            Map<String, Object> meta,
            TagBundleLoader tags,
            TagMemberIndex tagMembers,
            LangCatalog langs,
            IconCatalog icons,
            MultiblockRegistry multiblocks,
            ExportAssetAccess assets,
            ExportBookLoader books,
            Map<String, String> recipeMountIds) {
        this.exportRoot = exportRoot;
        this.manifest = manifest;
        this.meta = meta;
        this.tags = tags;
        this.tagMembers = tagMembers;
        this.langs = langs;
        this.icons = icons;
        this.multiblocks = multiblocks;
        this.assets = assets;
        this.books = books;
        this.recipeMountIds = recipeMountIds != null ? recipeMountIds : Map.of();
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
        ExportModelLoader modelLoader = new ExportModelLoader(root, tagMembers, root.resolve("dist"));
        ExportAssetAccess assetAccess = new ExportAssetAccess(root, modelLoader);
        return new ExportBundle(
                root,
                manifest != null ? manifest : Map.of(),
                meta,
                TagBundleLoader.load(root),
                tagMembers,
                LangCatalog.load(root),
                IconCatalog.load(root),
                MultiblockRegistry.load(root),
                assetAccess,
                new ExportBookLoader(modelLoader),
                RecipeMountIds.fromMeta(meta));
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
