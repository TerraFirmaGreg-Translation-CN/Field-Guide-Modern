package io.github.tfgcn.fieldguide.asset;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.TrueFileFilter;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

/**
 * Asset source that reads from the filesystem.
 *
 * @author yanmaoyuan
 */
public class FsAssetSource extends AssetSource {

    public FsAssetSource(Path rootPath, String sourceId) {
        super(rootPath, sourceId);
    }
    
    @Override
    public boolean exists(String resourcePath) {
        Path fullPath = rootPath.resolve(resourcePath);
        return Files.exists(fullPath);
    }
    
    @Override
    public InputStream getInputStream(String resourcePath) throws IOException {
        Path fullPath = rootPath.resolve(resourcePath);
        return Files.newInputStream(fullPath);
    }
    
    @Override
    public List<String> findPatchouliBooks() {
        List<String> books = new ArrayList<>();
        if (!Files.exists(rootPath)) {
            return books;
        }

        // Search in assets directory
        Path assetsPath = rootPath.resolve("assets");
        if (Files.exists(assetsPath)) {
            findBooksInDirectory(assetsPath, "assets", books);
        }

        // Search in data directory
        Path dataPath = rootPath.resolve("data");
        if (Files.exists(dataPath)) {
            findBooksInDirectory(dataPath, "data", books);
        }

        return books;
    }

    @Override
    public List<Asset> listAssets(String resourcePath) throws IOException {
        List<Asset> assets = new ArrayList<>();
        Path fullPath = rootPath.resolve(resourcePath);

        if (!Files.exists(fullPath) || !Files.isDirectory(fullPath)) {
            return assets;
        }

        Collection<File> files = FileUtils.listFiles(fullPath.toFile(), TrueFileFilter.INSTANCE, TrueFileFilter.INSTANCE);
        for (File file : files) {
            String relativePath = file.toPath().subpath(rootPath.getNameCount(), file.toPath().getNameCount()).toString();
            relativePath = relativePath.replace("\\", "/");
            assets.add(new Asset(relativePath, Files.newInputStream(file.toPath()), this));
        }

        return assets;
    }

    @Override
    public boolean isDirectory(String resourcePath) {
        Path fullPath = rootPath.resolve(resourcePath);
        return Files.exists(fullPath) && Files.isDirectory(fullPath);
    }

    private void findBooksInDirectory(Path basePath, String prefix, List<String> books) {
        try (Stream<Path> walk = Files.walk(basePath)) {
            walk.filter(path -> path.toString().contains("patchouli_books"))
                .filter(Files::isDirectory)
                .forEach(bookPath -> {
                    String relativePath = basePath.relativize(bookPath).toString();
                    String fullPath = prefix + "/" + relativePath;
                    books.add(fullPath);
                });
        } catch (IOException e) {
            System.err.println("Error scanning directory: " + basePath + " - " + e.getMessage());
        }
    }
}