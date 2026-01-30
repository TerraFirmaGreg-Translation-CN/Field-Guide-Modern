package team.terrafirmgreg.fieldguide.data.fml;

import team.terrafirmgreg.fieldguide.Constants;
import team.terrafirmgreg.fieldguide.asset.MCMeta;
import lombok.extern.slf4j.Slf4j;
import net.vieiro.toml.TOML;
import net.vieiro.toml.TOMLParser;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.DirectedAcyclicGraph;
import org.jgrapht.traverse.TopologicalOrderIterator;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.jar.*;
import java.util.stream.Stream;

import static team.terrafirmgreg.fieldguide.Constants.CACHE;

@Slf4j
public class ModLoader {
    private final List<ModInfo> loadedMods = new ArrayList<>();
    private final List<ModInfo> mods = new ArrayList<>();
    private final Map<String, ModInfo> modIdMap = new HashMap<>();
    
    public ModLoader(Path modsDir) throws IOException {
        if (!Files.exists(modsDir)) {
            log.info("Mods directory not found: {}", modsDir);
            return;
        }

        // 1 scan all modes
        scanMods(modsDir);
        
        // 2- build mod dependencies
        List<ModInfo> sortedMods = sortModsByDependencies();
        
        // 3- load mods
        loadSortedMods(sortedMods);
        
        log.debug("Loaded {} mods in correct dependency order", loadedMods.size());
    }

    private void add(ModInfo modInfo) {
        if (modInfo != null) {
            ModInfo exists = modIdMap.get(modInfo.getModId());
            if (exists == null) {
                mods.add(modInfo);
                modIdMap.put(modInfo.getModId(), modInfo);
            } else {
                if (modInfo.getVersion().compareTo(exists.getVersion()) > 0) {
                    mods.remove(exists);
                    mods.add(modInfo);
                    modIdMap.put(modInfo.getModId(), modInfo);
                } else {
                    log.debug("ignore {} version: {}, exist version: {}", modInfo.getModId(), modInfo.getVersion(), exists.getVersion());
                }
            }
        }
    }
    
    private void scanMods(Path modsDir) throws IOException {

        // add root
        ModInfo mc = new ModInfo("minecraft", "Minecraft", Constants.MC_VERSION, Collections.emptyList(), Paths.get(CACHE, MCMeta.getClientJarName(Constants.MC_VERSION)), -999);
        mods.add(mc);
        modIdMap.put("minecraft", mc);
        ModInfo forge = new ModInfo("forge", "Forge", Constants.FORGE_VERSION, List.of(new Dependency("minecraft", true, DependencyOrdering.NONE)), Paths.get(CACHE, MCMeta.getForgeJarName(Constants.FORGE_VERSION)), -998);
        mods.add(forge);
        modIdMap.put("forge", forge);

        try (Stream<Path> jars = Files.list(modsDir)) {
            List<Path> jarList = jars
                .filter(p -> p.toString().endsWith(".jar"))
                .toList();
            
            for (Path jar : jarList) {
                try {
                    extractJarJar(jar);
                    parseModInfo(jar);
                } catch (Exception e) {
                    log.error("Failed to parse mod info from: {}", jar.getFileName(), e);
                }
            }
        }
    }

    private void extractJarJar(Path jarPath) throws IOException {
        Path cacheDir = Paths.get(CACHE, "lib");
        Files.createDirectories(cacheDir);

        try (JarFile jar = new JarFile(jarPath.toFile())) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String entryName = entry.getName();

                if (entryName.endsWith(".jar")) {
                    String nestedJarName = Paths.get(entryName).getFileName().toString();
                    Path outputPath = cacheDir.resolve(nestedJarName);

                    if (Files.exists(outputPath)) {
                        log.debug("Nested JAR already exists: {}", nestedJarName);
                        parseModInfo(outputPath);
                        continue;
                    }

                    byte[] jarData;
                    try (InputStream is = jar.getInputStream(entry)) {
                        jarData = is.readAllBytes();
                    }

                    if (!hasModsToml(jarData)) {
                        log.debug("Skipping nested JAR without mods.toml: {}", nestedJarName);
                        //continue;
                    }

                    // save jar in jar
                    Files.write(outputPath, jarData);

                    parseModInfo(outputPath);
                    log.debug("Extracted nested JAR: {}", nestedJarName);

                    extractJarJar(outputPath);
                }
            }
        }
    }

    private boolean hasModsToml(byte[] jarData) {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(jarData);
             JarInputStream jarStream = new JarInputStream(bis)) {

            JarEntry entry;
            while ((entry = jarStream.getNextJarEntry()) != null) {
                if ("META-INF/mods.toml".equals(entry.getName())) {
                    return true;
                }
            }
        } catch (IOException e) {
            log.warn("Failed to check mods.toml in nested JAR data", e);
        }
        return false;
    }

    private void parseModInfo(Path jarPath) throws IOException {
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            JarEntry modsTomlEntry = jar.getJarEntry("META-INF/mods.toml");
            if (modsTomlEntry == null) {
                log.debug("No mods.toml found in: {}", jarPath.getFileName());
                return;
            }

            try (InputStream is = jar.getInputStream(modsTomlEntry)) {
                ModInfo modInfo = parseModsToml(is, jarPath);
                if (modInfo != null) {
                    if ("${file.jarVersion}".equals(modInfo.getVersion())) {
                        Manifest manifest = jar.getManifest();
                        String jarVersion = manifest.getMainAttributes().getValue("Implementation-Version");
                        modInfo.setVersion(jarVersion);
                    }
                    add(modInfo);
                }
            }
        }
    }

    private ModInfo parseModsToml(InputStream is, Path jarPath) throws IOException {
        TOML toml = TOMLParser.parseFromInputStream(is);

        String modId = toml.getString("mods/0/modId").orElse(null);
        String name = toml.getString("mods/0/displayName").orElse(null);
        int loadOrder = parseLoadOrder(toml.getString("mods/0/loadOrder").orElse("NONE"));
        String version = toml.getString("mods/0/version").orElse(null);
        if (modId == null) {
            log.warn("No modId found in mods.toml for: {}", jarPath.getFileName());
            return null;
        }

        // 设置默认值
        if (name == null) {
            name = modId;
        }

        Set<String> modIds = new HashSet<>();
        List<Dependency> dependencies = new ArrayList<>();

        @SuppressWarnings({"unchecked", "raw"})
        Map<String, Object> dependencyMap = (Map) toml.getRoot().get("dependencies");
        if (dependencyMap == null || dependencyMap.isEmpty()) {
            for (String key : toml.getRoot().keySet()) {
                if (key.startsWith("dependencies")) {
                    int size = toml.getArray(key).orElse(List.of()).size();
                    for (int i = 0; i < size; i++) {
                        String id = toml.getString(key + "/" + i + "/modId").orElse(null);
                        String ordering = toml.getString(key + "/" + i + "/ordering").orElse("NONE");
                        Boolean mandatory = toml.getBoolean(key + "/" + i + "/mandatory").orElse(false);

                        modIds.add(id);
                        dependencies.add( new Dependency(id, mandatory, parseDependencyOrdering(ordering)) );
                    }
                }
            }
        } else {
            Set<String> groupNames = dependencyMap.keySet();

            for (String group : groupNames) {
                int size = toml.getArray("/dependencies/" + group).orElse(List.of()).size();
                for (int i = 0; i < size; i++) {
                    String id = toml.getString("/dependencies/" + group + "/" + i + "/modId").orElse(null);
                    String ordering = toml.getString("/dependencies/" + group + "/" + i + "/ordering").orElse("NONE");
                    Boolean mandatory = toml.getBoolean("/dependencies/" + group + "/" + i + "/mandatory").orElse(false);

                    modIds.add(id);
                    dependencies.add( new Dependency(id, mandatory, parseDependencyOrdering(ordering)) );
                }
            }
        }

        if (dependencies.isEmpty()) {
            log.debug("Dependencies is empty, mod: {}", modId);
        }

        if (!modIds.contains("minecraft")) {
            dependencies.add(new Dependency("minecraft", true, DependencyOrdering.NONE));
        }
        if (!modIds.contains("forge")) {
            dependencies.add(new Dependency("forge", true, DependencyOrdering.NONE));
        }

        return new ModInfo(modId, name, version, dependencies, jarPath, loadOrder);
    }

    private int parseLoadOrder(String order) {
        return switch (order.toLowerCase()) {
            case "before" -> -1;
            case "after" -> 1;
            default -> 0;
        };
    }

    private DependencyOrdering parseDependencyOrdering(String order) {
        return switch (order.toLowerCase()) {
            case "before" -> DependencyOrdering.BEFORE;
            case "after" -> DependencyOrdering.AFTER;
            default -> DependencyOrdering.NONE;
        };
    }

    private List<ModInfo> sortModsByDependencies() {
        DirectedAcyclicGraph<ModInfo, DefaultEdge> graph = new DirectedAcyclicGraph<>(DefaultEdge.class);

        for (ModInfo mod : mods) {
            graph.addVertex(mod);
        }
        
        for (ModInfo mod : mods) {
            for (Dependency dep : mod.getDependencies()) {
                ModInfo targetMod = modIdMap.get(dep.getModId());
                if (targetMod != null) {
                    try {
                        switch (dep.getOrdering()) {
                            case BEFORE -> graph.addEdge(mod, targetMod);
                            case AFTER -> graph.addEdge(targetMod, mod);
                            case NONE -> {
                                if (dep.isMandatory()) {
                                    graph.addEdge(targetMod, mod);
                                }
                            }
                        }
                    } catch (IllegalArgumentException e) {
                        log.warn("Cycle detected in mod dependencies: {} -> {}", mod.getModId(), targetMod.getModId());
                    }
                } else if (dep.isMandatory()) {
                    log.warn("Missing mandatory dependency: {} requires {}", mod.getModId(), dep.getModId());
                }
            }
        }

        TopologicalOrderIterator<ModInfo, DefaultEdge> iterator = new TopologicalOrderIterator<>(graph, Comparator.comparingInt(ModInfo::getLoadOrder).thenComparing(ModInfo::getModId));

        List<ModInfo> sortedMods = new ArrayList<>();
        while (iterator.hasNext()) {
            sortedMods.add(iterator.next());
        }
        return sortedMods;
    }
    
    private void loadSortedMods(List<ModInfo> sortedMods) {
        for (ModInfo mod : sortedMods) {
            try {
                log.debug("Loading mod:{}, depends: {}", mod.getModId(), mod.getDependencies().stream().map(Dependency::getModId).toList());
                loadedMods.add(mod);
            } catch (Exception e) {
                log.error("Failed to load mod: {}", mod.getModId(), e);
            }
        }
    }

    public List<ModInfo> getLoadedMods() {
        return Collections.unmodifiableList(loadedMods);
    }

    public ModInfo getMod(String modId) {
        return modIdMap.get(modId);
    }
}