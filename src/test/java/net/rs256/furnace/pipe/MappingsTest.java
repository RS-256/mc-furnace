package net.rs256.furnace.pipe;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MappingsTest {

    private static final String PROGUARD =
            """
            # comment
            net.minecraft.util.RandomSource -> a:
                int nextInt() -> a
                1:3:void consume(int) -> b
            net.minecraft.world.item.Item -> bwj:
                java.lang.String description -> a
            """;

    @Test
    void convertsProguardToTinyWithOfficialAsSource(@TempDir Path tmp) throws Exception {
        Path proguard = tmp.resolve("client.txt");
        Files.writeString(proguard, PROGUARD, StandardCharsets.UTF_8);

        var tree = Mappings.readProguard(proguard);
        Set<String> official = Mappings.officialClassNames(tree);
        assertTrue(official.contains("a"), "official names: " + official);
        assertTrue(official.contains("bwj"));

        Path tiny = Mappings.writeTiny(tree, tmp.resolve("client.tiny"));
        String content = Files.readString(tiny, StandardCharsets.UTF_8);
        assertTrue(content.startsWith("tiny\t2\t0\tofficial\tnamed"), content.lines().findFirst().orElse(""));
        assertTrue(content.contains("c\ta\tnet/minecraft/util/RandomSource"));
        assertTrue(content.contains("c\tbwj\tnet/minecraft/world/item/Item"));
    }
}
