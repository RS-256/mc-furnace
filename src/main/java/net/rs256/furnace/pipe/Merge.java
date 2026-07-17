package net.rs256.furnace.pipe;

import java.io.IOException;
import java.nio.file.Path;
import net.fabricmc.stitch.merge.JarMerger;

/**
 * SPEC 4.1 step 3: merges the named client and server jars into one, adding
 * side annotations to client-/server-only classes and members.
 */
public final class Merge {

    private Merge() {}

    public static void merge(Path clientNamed, Path serverNamed, Path mergedJar) throws IOException {
        try (JarMerger merger =
                new JarMerger(clientNamed.toFile(), serverNamed.toFile(), mergedJar.toFile())) {
            merger.merge();
        }
    }
}
