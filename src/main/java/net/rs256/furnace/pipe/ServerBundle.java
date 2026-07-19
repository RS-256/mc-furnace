package net.rs256.furnace.pipe;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Unpacks the bundled server jar (1.18+ bundler format).
 * For the legacy layout (libraries shaded into server.jar) the jar is
 * filtered down to the classes covered by the server mappings plus non-class
 * resources, so shaded libraries never reach the merged jar.
 */
public final class ServerBundle {

    private static final String VERSIONS_LIST = "META-INF/versions.list";

    private ServerBundle() {}

    /** Returns a jar containing only Minecraft's own classes and resources. */
    public static Path extract(Path serverJar, Path workDir, Set<String> officialServerClasses)
            throws IOException {
        try (ZipFile zip = new ZipFile(serverJar.toFile())) {
            ZipEntry versionsList = zip.getEntry(VERSIONS_LIST);
            if (versionsList != null) {
                return extractBundled(zip, versionsList, workDir);
            }
        }
        return filterLegacy(serverJar, workDir, officialServerClasses);
    }

    private static Path extractBundled(ZipFile zip, ZipEntry versionsList, Path workDir)
            throws IOException {
        List<String> lines;
        try (InputStream in = zip.getInputStream(versionsList)) {
            lines = new String(in.readAllBytes(), StandardCharsets.UTF_8).lines().toList();
        }
        if (lines.isEmpty()) {
            throw new IOException("empty " + VERSIONS_LIST + " in server jar");
        }
        // format: <sha1>\t<id>\t<path>
        String[] fields = lines.get(0).split("\t");
        String innerPath = "META-INF/versions/" + fields[fields.length - 1];
        ZipEntry inner = zip.getEntry(innerPath);
        if (inner == null) {
            throw new IOException("bundled server jar entry not found: " + innerPath);
        }
        Path out = workDir.resolve("server-inner.jar");
        try (InputStream in = zip.getInputStream(inner)) {
            Files.copy(in, out, StandardCopyOption.REPLACE_EXISTING);
        }
        return out;
    }

    private static Path filterLegacy(Path serverJar, Path workDir, Set<String> officialServerClasses)
            throws IOException {
        Path out = workDir.resolve("server-filtered.jar");
        try (ZipFile zip = new ZipFile(serverJar.toFile());
                ZipOutputStream zos =
                        new ZipOutputStream(Files.newOutputStream(out))) {
            List<? extends ZipEntry> entries =
                    new ArrayList<>(zip.stream().sorted(java.util.Comparator.comparing(ZipEntry::getName)).toList());
            for (ZipEntry entry : entries) {
                String name = entry.getName();
                if (entry.isDirectory() || name.startsWith("META-INF/")) {
                    continue;
                }
                if (name.endsWith(".class")) {
                    String className = name.substring(0, name.length() - ".class".length());
                    // inner classes of mapped classes are mapped too, so a direct lookup suffices
                    if (!officialServerClasses.contains(className)) {
                        continue;
                    }
                }
                ZipEntry copy = new ZipEntry(name);
                zos.putNextEntry(copy);
                try (InputStream in = zip.getInputStream(entry)) {
                    in.transferTo(zos);
                }
                zos.closeEntry();
            }
        }
        return out;
    }
}
