package su.terrafirmgreg.fieldguide.asset;

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
}