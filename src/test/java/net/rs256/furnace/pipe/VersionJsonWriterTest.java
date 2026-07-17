package net.rs256.furnace.pipe;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import net.rs256.furnace.meta.VersionDetail;
import org.junit.jupiter.api.Test;

class VersionJsonWriterTest {

    @Test
    void rendersStableKeyOrderWithTrailingNewline() {
        VersionDetail detail =
                new VersionDetail(
                        "1.21.1",
                        "release",
                        "2024-08-08T12:24:45+00:00",
                        new VersionDetail.JavaVersion("java-runtime-delta", 21),
                        Map.of(),
                        null);
        var toolchain =
                new VersionJsonWriter.Toolchain(
                        "vineflower-1.11.2", "mojang-official", "stitch@0.6.2", "mc-furnace@abc123");
        String json = VersionJsonWriter.render(detail, 3955, 767, toolchain);
        assertEquals(
                """
                {
                  "id": "1.21.1",
                  "type": "release",
                  "releaseTime": "2024-08-08T12:24:45+00:00",
                  "javaVersion": 21,
                  "worldVersion": 3955,
                  "protocolVersion": 767,
                  "toolchain": {
                    "decompiler": "vineflower-1.11.2",
                    "mappings": "mojang-official",
                    "merger": "stitch@0.6.2",
                    "pipelineCommit": "mc-furnace@abc123"
                  }
                }
                """,
                json);
    }
}
