package su.terrafirmgreg.fieldguide.asset;

import com.google.gson.Gson;
import su.terrafirmgreg.fieldguide.localization.Language;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

import static su.terrafirmgreg.fieldguide.Constants.CACHE;

@Slf4j
public class MCMeta {

    private static final String VERSION_MANIFEST_URL = "https://piston-meta.mojang.com/mc/game/version_manifest.json";
    private static final String RESOURCES_URL = "https://resources.download.minecraft.net/";

    public static String getClientJarName(String mcVersion) {
        return "client-" + mcVersion + ".jar";
    }
    
    public static String getForgeJarName(String forgeVersion) {
        return "forge-" + forgeVersion + ".jar";
    }
    
    public static String getForgeJarUrl(String mcVersion, String forgeVersion) {
        if (mcVersion.equals("1.20.1")) {
            return String.format("https://maven.creeperhost.net/net/minecraftforge/forge/%s-%s/forge-%s-%s-universal.jar", mcVersion, forgeVersion, mcVersion, forgeVersion);
        } else {
            return String.format("https://maven.neoforged.net/#/releases/net/neoforged/neoforge/%s/neoforge-%s-universal.jar", forgeVersion, forgeVersion);
        }
    }

    @SuppressWarnings("unchecked")
    public static void loadCache(String mcVersion, String forgeVersion, List<Language> languages) {
        log.debug("Loading Cache");
        try {
            Files.createDirectories(Paths.get(CACHE, "assets", "minecraft", "lang"));
        } catch (IOException e) {
            log.error("Failed to create cache directory", e);
            return;
        }
        
        Path clientJarPath = Paths.get(CACHE, getClientJarName(mcVersion));
        if (!Files.exists(clientJarPath)) {
            try {
                // Load Manifest
                byte[] manifestData = download(VERSION_MANIFEST_URL);
                Map<String, Object> manifest = parseJson(manifestData);
                
                List<Map<String, Object>> versions = (List<Map<String, Object>>) manifest.get("versions");
                Map<String, Object> targetVersion = null;
                
                for (Map<String, Object> version : versions) {
                    if (mcVersion.equals(version.get("id"))) {
                        targetVersion = version;
                        break;
                    }
                }
                
                if (targetVersion == null) {
                    throw new IllegalArgumentException("Version " + mcVersion + " not found in manifest");
                }
                
                // Load Version Manifest
                String versionUrl = (String) targetVersion.get("url");
                byte[] versionData = download(versionUrl);
                Map<String, Object> versionManifest = parseJson(versionData);
                
                Map<String, Object> downloads = (Map<String, Object>) versionManifest.get("downloads");
                Map<String, Object> client = (Map<String, Object>) downloads.get("client");
                String clientJarUrl = (String) client.get("url");
                byte[] clientJar = download(clientJarUrl);
                
                // Load Languages
                Map<String, Object> assetIndex = (Map<String, Object>) versionManifest.get("assetIndex");
                String assetIndexUrl = (String) assetIndex.get("url");
                byte[] assetIndexData = download(assetIndexUrl);
                Map<String, Object> assets = parseJson(assetIndexData);
                Map<String, Object> objects = (Map<String, Object>) assets.get("objects");
                
                for (Language lang : languages) {
                    if (lang.getKey().equals("en_us")) {
                        continue; // This file is in the main client jar
                    }
                    
                    String langKey = "minecraft/lang/" + lang.getKey() + ".json";
                    Map<String, Object> langObject = (Map<String, Object>) objects.get(langKey);
                    if (langObject == null) {
                        log.warn("Language file not found: " + langKey);
                        continue;
                    }
                    
                    String languageHash = (String) langObject.get("hash");
                    String languageUrl = RESOURCES_URL + languageHash.substring(0, 2) + "/" + languageHash;
                    byte[] languageData = download(languageUrl);
                    Map<String, Object> languageJson = parseJson(languageData);
                    
                    Path langPath = Paths.get(CACHE, "assets", "minecraft", "lang", lang.getKey() + ".json");
                    try (FileWriter fw = new FileWriter(langPath.toFile(), StandardCharsets.UTF_8)) {
                        writeJson(fw, languageJson);
                    }
                }
                
                Files.write(clientJarPath, clientJar);
                
            } catch (Exception e) {
                log.error("Failed to load client jar: " + e.getMessage());
                return;
            }
        }
        
        Path forgeJarPath = Paths.get(CACHE, getForgeJarName(forgeVersion));
        if (!Files.exists(forgeJarPath)) {
            try {
                byte[] forgeJar = download(getForgeJarUrl(mcVersion, forgeVersion));
                Files.write(forgeJarPath, forgeJar);
            } catch (Exception e) {
                log.error("Failed to load forge jar: " + e.getMessage());
                return;
            }
        }
        
        log.debug("Cache Loaded");
    }
    
    public static byte[] download(String urlString) throws IOException {
        log.debug("Downloading " + urlString);
        try {
            URL url = new URI(urlString).toURL();
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            
            try (InputStream is = connection.getInputStream();
                 ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = is.read(buffer)) != -1) {
                    baos.write(buffer, 0, bytesRead);
                }
                
                return baos.toByteArray();
            }
        } catch (URISyntaxException | IOException e) {
            throw new IOException("Requested " + urlString, e);
        }
    }
    
    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseJson(byte[] data) {
        try {
            String jsonString = new String(data, StandardCharsets.UTF_8);
            return new Gson().fromJson(jsonString, Map.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse JSON", e);
        }
    }
    
    private static void writeJson(FileWriter writer, Map<String, Object> data) {
        try {
            String json = new Gson().toJson(data);
            writer.write(json);
        } catch (Exception e) {
            throw new RuntimeException("Failed to write JSON", e);
        }
    }
}