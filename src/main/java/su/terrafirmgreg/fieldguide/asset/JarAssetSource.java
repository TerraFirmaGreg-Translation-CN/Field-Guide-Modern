package su.terrafirmgreg.fieldguide.asset;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

/**
 * Asset source for a JAR file
 *
 * @author yanmaoyuan
 */
public class JarAssetSource extends AssetSource {

    private final JarFile jarFile;
    
    public JarAssetSource(Path jarPath) throws IOException {
        super(jarPath, "mod:" + jarPath.getFileName().toString());
        this.jarFile = new JarFile(jarPath.toFile());
    }

    @Override
    public boolean exists(String resourcePath) {
        return jarFile.getJarEntry(resourcePath) != null;
    }

    @Override
    public InputStream getInputStream(String resourcePath) throws IOException {
        JarEntry entry = jarFile.getJarEntry(resourcePath);
        if (entry != null) {
            return jarFile.getInputStream(entry);
        }
        throw new IOException("Resource not found in JAR: " + resourcePath);
    }

    @Override
    public List<Asset> listAssets(String resourcePath) throws IOException {
        List<Asset> assets = new ArrayList<>();
        String normalizedDir = normalizePath(resourcePath);

        Enumeration<? extends JarEntry> entries = jarFile.entries();
        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            String entryName = entry.getName();
            if (entryName.startsWith(normalizedDir)) {
                if (!entry.isDirectory()) {
                    assets.add(new Asset(entryName, jarFile.getInputStream(entry), this));
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

        ZipEntry entry = jarFile.getEntry(normalizedPath);
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