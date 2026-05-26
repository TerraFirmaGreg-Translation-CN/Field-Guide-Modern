package team.terrafirmgreg.fieldguide.asset;

import lombok.Getter;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;

@Getter
public abstract class AssetSource {

    protected final Path rootPath;

    protected final String sourceId;

    public AssetSource(Path rootPath, String sourceId) {
        this.rootPath = rootPath;
        this.sourceId = sourceId;
    }

    public abstract boolean exists(String resourcePath);

    public abstract InputStream getInputStream(String resourcePath) throws IOException;

    public abstract List<Asset> listAssets(String resourcePath) throws IOException;

    public abstract boolean isDirectory(String resourcePath);

    @Override
    public String toString() {
        return sourceId;
    }
}