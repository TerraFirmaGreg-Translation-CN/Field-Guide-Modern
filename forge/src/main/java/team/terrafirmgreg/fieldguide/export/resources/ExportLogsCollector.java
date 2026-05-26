package team.terrafirmgreg.fieldguide.export.resources;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Copies modpack / launcher logs into {@code guide-export/logs/} so CI artifacts include diagnostics.
 */
public final class ExportLogsCollector {

    private static final Logger LOGGER = LogManager.getLogger("fieldguide");

    private static final long MAX_FILE_BYTES = 32L * 1024 * 1024;

    private ExportLogsCollector() {}

    public record Result(int filesCopied, long bytesCopied, List<String> sources) {}

    private static final class CopyStats {
        int files;
        long bytes;
    }

    public static boolean isEnabled() {
        return !Boolean.getBoolean("fieldguide.skipExportLogs");
    }

    /**
     * @param gamedir HeadlessMC gamedir (e.g. Modpack-Modern)
     * @param workspace repo root (for HeadlessMC launcher logs)
     */
    public static Result collect(Path outputDir, Path gamedir, Path workspace) {
        Path destRoot = outputDir.resolve("logs");
        List<String> sources = new ArrayList<>();
        CopyStats stats = new CopyStats();

        try {
            Files.createDirectories(destRoot);
            if (gamedir != null) {
                Path gameLogs = gamedir.resolve("logs");
                if (Files.isDirectory(gameLogs)) {
                    merge(copyTree(gameLogs, destRoot.resolve("modpack"), sources), stats);
                }
                merge(copyIfExists(gamedir.resolve("debug.log"), destRoot.resolve("modpack/debug.log"), sources), stats);
            }
            if (workspace != null) {
                Path hmc = workspace.resolve("HeadlessMC");
                if (Files.isDirectory(hmc)) {
                    merge(copyTree(hmc, destRoot.resolve("headlessmc"), sources), stats);
                }
            }
            writeManifest(destRoot, sources);
        } catch (IOException e) {
            LOGGER.warn("[export-logs] collect failed: {}", e.getMessage());
        }

        LOGGER.info("[export-logs] copied {} files ({} bytes) into {}", stats.files, stats.bytes, destRoot);
        return new Result(stats.files, stats.bytes, sources);
    }

    private static void merge(CopyStats from, CopyStats into) {
        into.files += from.files;
        into.bytes += from.bytes;
    }

    private static void writeManifest(Path destRoot, List<String> sources) throws IOException {
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("schema", 1);
        manifest.put("sources", sources);
        Path manifestFile = destRoot.resolve("manifest.json");
        Files.writeString(manifestFile, new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(manifest));
    }

    private static CopyStats copyTree(Path sourceDir, Path destDir, List<String> sources) throws IOException {
        CopyStats stats = new CopyStats();
        Files.createDirectories(destDir);
        try (Stream<Path> walk = Files.walk(sourceDir, FileVisitOption.FOLLOW_LINKS)) {
            for (Path src : walk.filter(Files::isRegularFile).toList()) {
                if (Files.size(src) > MAX_FILE_BYTES) {
                    LOGGER.warn("[export-logs] skip large file {} ({} bytes)", src, Files.size(src));
                    continue;
                }
                Path rel = sourceDir.relativize(src);
                Path dest = destDir.resolve(rel.toString());
                Files.createDirectories(dest.getParent());
                Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
                sources.add(sourceDir + " -> " + rel);
                stats.files++;
                stats.bytes += Files.size(dest);
            }
        }
        return stats;
    }

    private static CopyStats copyIfExists(Path src, Path dest, List<String> sources) {
        CopyStats stats = new CopyStats();
        if (!Files.isRegularFile(src)) {
            return stats;
        }
        try {
            if (Files.size(src) > MAX_FILE_BYTES) {
                return stats;
            }
            Files.createDirectories(dest.getParent());
            Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
            sources.add(src.toString());
            stats.files = 1;
            stats.bytes = Files.size(dest);
        } catch (IOException e) {
            LOGGER.warn("[export-logs] failed to copy {}: {}", src, e.getMessage());
        }
        return stats;
    }
}
