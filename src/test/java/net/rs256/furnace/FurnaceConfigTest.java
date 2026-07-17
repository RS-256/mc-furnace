package net.rs256.furnace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FurnaceConfigTest {

    @Test
    void localPropertiesOverrideCommittedDefaults(@TempDir Path tmp) throws Exception {
        Files.writeString(
                tmp.resolve("furnace.properties"),
                "terra.repo=../mc-terra\ndecompiler.heap=4g\nscope.from=1.14.4\n",
                StandardCharsets.UTF_8);
        Files.writeString(
                tmp.resolve("furnace.local.properties"),
                "terra.repo=D:/mc/mc-terra\ndecompiler.heap=8g\n",
                StandardCharsets.UTF_8);

        FurnaceConfig config = FurnaceConfig.load(tmp);
        assertEquals(Path.of("D:/mc/mc-terra").toAbsolutePath().normalize(), config.terraRepo());
        assertEquals("8g", config.decompilerHeap());
        // keys absent from the local file keep the shared default
        assertEquals("1.14.4", config.scopeFrom());
    }

    @Test
    void worksWithoutAnyLocalFile(@TempDir Path tmp) throws Exception {
        Files.writeString(
                tmp.resolve("furnace.properties"), "terra.repo=../mc-terra\n", StandardCharsets.UTF_8);
        FurnaceConfig config = FurnaceConfig.load(tmp);
        assertTrue(config.terraRepo().toString().endsWith("mc-terra"));
        assertEquals("4g", config.decompilerHeap());
    }
}
