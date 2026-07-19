package net.rs256.furnace.pipe;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import net.rs256.furnace.cfg.ExcludeList;

/**
 * Extracts data/ from the server jar and text-only assets/
 * from the client jar, applying config/excludes.txt. Bytes are copied
 * verbatim; determinism follows from the fixed jar contents.
 */
public final class Extract {

    private Extract() {}

    /** Extracts all entries under {@code prefix} into {@code outRoot}, honoring excludes. */
    public static int extractPrefix(Path jar, String prefix, Path outRoot, ExcludeList excludes)
            throws IOException {
        int count = 0;
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            List<? extends ZipEntry> entries =
                    zip.stream().sorted(java.util.Comparator.comparing(ZipEntry::getName)).toList();
            for (ZipEntry entry : entries) {
                String name = entry.getName();
                if (entry.isDirectory() || !name.startsWith(prefix) || excludes.isExcluded(name)) {
                    continue;
                }
                Path target = outRoot.resolve(name);
                Files.createDirectories(target.getParent());
                try (InputStream in = zip.getInputStream(entry)) {
                    Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                }
                count++;
            }
        }
        return count;
    }

    /** Reads a single jar entry, or null when absent. */
    public static byte[] readEntry(Path jar, String entryName) throws IOException {
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            ZipEntry entry = zip.getEntry(entryName);
            if (entry == null) {
                return null;
            }
            try (InputStream in = zip.getInputStream(entry)) {
                return in.readAllBytes();
            }
        }
    }
}
