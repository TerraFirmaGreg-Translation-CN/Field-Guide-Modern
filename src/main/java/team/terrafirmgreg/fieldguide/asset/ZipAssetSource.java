package team.terrafirmgreg.fieldguide.asset;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Asset source for a ZIP file
 *
 * @author yanmaoyuan
 */
public class ZipAssetSource extends AssetSource {

    private final ZipFile zipFile;

    public ZipAssetSource(Path zipPath) throws IOException {
        super(zipPath, "zip:" + zipPath.getFileName().toString());
        this.zipFile = new ZipFile(zipPath.toFile());
    }

    @Override
    public boolean exists(String resourcePath) {
        return zipFile.getEntry(resourcePath) != null;
    }

    @Override
    public InputStream getInputStream(String resourcePath) throws IOException {
        ZipEntry entry = zipFile.getEntry(resourcePath);
        if (entry != null) {
            return zipFile.getInputStream(entry);
        }
        throw new IOException("Resource not found in ZIP: " + resourcePath);
    }

    @Override
    public List<Asset> listAssets(String resourcePath) throws IOException {
        List<Asset> assets = new ArrayList<>();
        String normalizedDir = normalizePath(resourcePath);

        Enumeration<? extends ZipEntry> entries = zipFile.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            String entryName = entry.getName();
            if (entryName.startsWith(normalizedDir)) {
                if (!entry.isDirectory()) {
                    assets.add(new Asset(entryName, zipFile.getInputStream(entry), this));
                }
            }
        }

        return assets;
    }

    @Override
    public boolean isDirectory(String resourcePath) {
        String normalizedPath = normalizePath(resourcePath);
        if (!normalizedPath.endsWith("/")) {
            normalizedPath += "/";
        }

        ZipEntry entry = zipFile.getEntry(normalizedPath);
        return entry != null && entry.isDirectory();
    }

    private String normalizePath(String path) {
        if (path.isEmpty()) {
            return "";
        }

        String normalized = path.replace("\\", "/");
        if (!normalized.endsWith("/")) {
            normalized += "/";
        }

        return normalized;
    }
}