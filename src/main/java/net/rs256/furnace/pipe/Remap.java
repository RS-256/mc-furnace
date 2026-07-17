package net.rs256.furnace.pipe;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.fabricmc.tinyremapper.OutputConsumerPath;
import net.fabricmc.tinyremapper.TinyRemapper;
import net.fabricmc.tinyremapper.TinyUtils;

/**
 * SPEC 4.1 step 2: remaps a jar from official (obfuscated) to named using
 * tiny-remapper. Non-class resources are intentionally not copied; data and
 * assets are extracted from the original jars instead (SPEC 4.1 step 5).
 */
public final class Remap {

    private Remap() {}

    public static void remap(Path inputJar, Path outputJar, Path tinyMappings, List<Path> classpath)
            throws IOException {
        Files.deleteIfExists(outputJar);
        TinyRemapper remapper =
                TinyRemapper.newRemapper()
                        .withMappings(
                                TinyUtils.createTinyMappingProvider(
                                        tinyMappings, Mappings.NS_OFFICIAL, Mappings.NS_NAMED))
                        .renameInvalidLocals(true)
                        .rebuildSourceFilenames(true)
                        .build();
        try (OutputConsumerPath output = new OutputConsumerPath.Builder(outputJar).build()) {
            remapper.readInputs(inputJar);
            if (!classpath.isEmpty()) {
                remapper.readClassPath(classpath.toArray(Path[]::new));
            }
            remapper.apply(output);
        } finally {
            remapper.finish();
        }
    }
}
