package team.terrafirmgreg.fieldguide.export;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TagMemberIndexTest {

    @Test
    void loadsTagMembersIndex(@TempDir Path root) throws Exception {
        Files.createDirectories(root.resolve("index"));
        Files.writeString(root.resolve("index/tag-members.json"), """
                {
                  "items": {
                    "forge:ingots/copper": ["minecraft:copper_ingot"]
                  },
                  "blocks": {},
                  "fluids": {}
                }
                """);

        TagMemberIndex idx = TagMemberIndex.load(root);
        assertEquals(List.of("minecraft:copper_ingot"), idx.getItemMembers("forge:ingots/copper"));
        assertTrue(idx.getBlockMembers("forge:ingots/copper").isEmpty());
    }
}
