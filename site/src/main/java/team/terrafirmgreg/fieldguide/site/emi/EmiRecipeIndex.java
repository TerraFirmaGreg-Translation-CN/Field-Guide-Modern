package team.terrafirmgreg.fieldguide.site.emi;

import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import team.terrafirmgreg.fieldguide.gson.JsonUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Recipe ids from EMI bundle schema 2 ({@code recipes/<namespace>/*.json} with {@code id} field).
 */
@Slf4j
public final class EmiRecipeIndex {

    private static final String ROUTES_DIR = "routes";

    private final Set<String> recipeIds;

    private EmiRecipeIndex(Set<String> recipeIds) {
        this.recipeIds = recipeIds;
    }

    public static EmiRecipeIndex load(Path emiRoot) {
        if (emiRoot == null || !Files.isDirectory(emiRoot)) {
            log.warn("EMI bundle missing at {} — recipe availability will not be checked at build time", emiRoot);
            return new EmiRecipeIndex(Set.of());
        }
        Path bundleJson = emiRoot.resolve("bundle.json");
        if (!Files.isRegularFile(bundleJson)) {
            log.warn("No bundle.json under {} — skipping EMI recipe index", emiRoot);
            return new EmiRecipeIndex(Set.of());
        }
        int expectedCount = -1;
        try {
            JsonObject bundle = JsonUtils.readFile(bundleJson.toFile(), JsonObject.class);
            if (bundle.has("schema") && bundle.get("schema").getAsInt() != 2) {
                log.warn("Unsupported EMI bundle schema at {}", bundleJson);
                return new EmiRecipeIndex(Set.of());
            }
            if (bundle.has("recipeCount")) {
                expectedCount = bundle.get("recipeCount").getAsInt();
            }
        } catch (Exception e) {
            log.warn("Failed to read {}", bundleJson, e);
            return new EmiRecipeIndex(Set.of());
        }

        Path recipesDir = emiRoot.resolve("recipes");
        if (!Files.isDirectory(recipesDir)) {
            return new EmiRecipeIndex(Set.of());
        }

        Set<String> ids = new HashSet<>();
        try (Stream<Path> namespaces = Files.list(recipesDir)) {
            namespaces.filter(Files::isDirectory)
                    .filter(ns -> !ROUTES_DIR.equals(ns.getFileName().toString()))
                    .forEach(ns -> indexNamespaceRecipes(ns, ids));
        } catch (IOException e) {
            log.warn("Failed to list {}", recipesDir, e);
        }

        if (expectedCount > 0 && ids.size() < expectedCount / 2) {
            log.warn(
                    "EMI recipe index looks incomplete ({} ids, bundle.recipeCount={}); disabling build-time recipe filter",
                    ids.size(),
                    expectedCount);
            return new EmiRecipeIndex(Set.of());
        }

        log.info("EMI recipe index: {} recipe ids under {}", ids.size(), emiRoot);
        return new EmiRecipeIndex(Collections.unmodifiableSet(ids));
    }

    private static void indexNamespaceRecipes(Path namespaceDir, Set<String> ids) {
        try (Stream<Path> files = Files.list(namespaceDir)) {
            files.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".json"))
                    .forEach(p -> readRecipeMetaId(p, namespaceDir).ifPresent(ids::add));
        } catch (IOException e) {
            log.warn("Failed to list {}", namespaceDir, e);
        }
    }

    private static Optional<String> readRecipeMetaId(Path metaFile, Path namespaceDir) {
        try {
            JsonObject meta = JsonUtils.readFile(metaFile.toFile(), JsonObject.class);
            if (meta.has("id") && meta.get("id").isJsonPrimitive()) {
                String id = meta.get("id").getAsString().trim();
                if (!id.isEmpty()) {
                    return Optional.of(id);
                }
            }
            return recipeIdFromCardPath(metaFile, namespaceDir);
        } catch (Exception e) {
            log.debug("Skipping non recipe-meta json: {}", metaFile, e);
            return Optional.empty();
        }
    }

    /**
     * Inverse of export {@code pathSafe} when path segments contain no {@code _} themselves.
     * Prefer {@code meta.id}; export normally always writes it.
     */
    private static Optional<String> recipeIdFromCardPath(Path metaFile, Path namespaceDir) {
        String fileName = metaFile.getFileName().toString();
        if (!fileName.endsWith(".json")) {
            return Optional.empty();
        }
        String pathSafe = fileName.substring(0, fileName.length() - ".json".length());
        if (pathSafe.isEmpty()) {
            return Optional.empty();
        }
        String namespace = namespaceDir.getFileName().toString();
        if (namespace.isEmpty() || ROUTES_DIR.equals(namespace)) {
            return Optional.empty();
        }
        String path = pathSafe.replace('_', '/');
        return Optional.of(namespace + ":" + path);
    }

    public boolean contains(String recipeId) {
        if (recipeId == null || recipeId.isEmpty()) {
            return false;
        }
        if (recipeIds.isEmpty()) {
            return true;
        }
        return recipeIds.contains(recipeId);
    }

    public boolean isEmpty() {
        return recipeIds.isEmpty();
    }
}
