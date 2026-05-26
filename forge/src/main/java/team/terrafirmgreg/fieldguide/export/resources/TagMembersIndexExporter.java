package team.terrafirmgreg.fieldguide.export.resources;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.server.MinecraftServer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import team.terrafirmgreg.fieldguide.generated.EmiBundlePaths;

import java.util.TreeSet;

/**
 * Closure-only index: {@code emi/tags/members.json} maps each expanded tag id to fully
 * resolved item/block/fluid registry members (recursive runtime expansion).
 * Member lists preserve registry tag iteration order (EMI-compatible).
 */
public final class TagMembersIndexExporter {

    private static final Logger LOGGER = LogManager.getLogger("fieldguide");

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private TagMembersIndexExporter() {}

    public record Result(
            int tagsIndexed,
            int itemTagEntries,
            int blockTagEntries,
            int fluidTagEntries,
            int totalMemberRefs,
            long indexBytes) {}

    public static boolean isEnabled() {
        return !Boolean.getBoolean("fieldguide.skipTagMembersIndexExport");
    }

    public static Result export(Path outputDir, MinecraftServer server, Set<String> tagIds) throws IOException {
        Path outFile = EmiBundlePaths.resolve(outputDir, EmiBundlePaths.TAG_MEMBERS_FILE);
        Files.createDirectories(outFile.getParent());

        Map<String, List<String>> itemsByTag = new TreeMap<>();
        Map<String, List<String>> blocksByTag = new TreeMap<>();
        Map<String, List<String>> fluidsByTag = new TreeMap<>();

        int memberRefs = 0;
        for (String tagId : new TreeSet<>(tagIds)) {
            TagClosureExpander.TagMembers members = TagClosureExpander.expandTagMembers(server, tagId);
            if (!members.items().isEmpty()) {
                List<String> list = new ArrayList<>(members.items());
                itemsByTag.put(tagId, list);
                memberRefs += list.size();
            }
            if (!members.blocks().isEmpty()) {
                List<String> list = new ArrayList<>(members.blocks());
                blocksByTag.put(tagId, list);
                memberRefs += list.size();
            }
            if (!members.fluids().isEmpty()) {
                List<String> list = new ArrayList<>(members.fluids());
                fluidsByTag.put(tagId, list);
                memberRefs += list.size();
            }
        }

        Map<String, Object> index = new LinkedHashMap<>();
        index.put("schema", 1);
        index.put("description", "closure tag id -> fully expanded registry members (runtime, recursive)");
        index.put("items", itemsByTag);
        index.put("blocks", blocksByTag);
        index.put("fluids", fluidsByTag);

        String json = GSON.toJson(index);
        Files.writeString(outFile, json);

        LOGGER.info("[index] tag-members: {} tags → {} item, {} block, {} fluid entries ({} member refs, {} bytes)",
                tagIds.size(),
                itemsByTag.size(),
                blocksByTag.size(),
                fluidsByTag.size(),
                memberRefs,
                json.length());

        return new Result(
                tagIds.size(),
                itemsByTag.size(),
                blocksByTag.size(),
                fluidsByTag.size(),
                memberRefs,
                json.length());
    }
}
