package net.rs256.furnace.pipe;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import net.rs256.furnace.InterruptHandler;
import net.rs256.furnace.util.ClasspathJars;
import net.rs256.furnace.util.Procs;

/**
 * Decompiles the merged jar with Vineflower launched as a separate process for
 * heap isolation and termination on graceful stop. All options come from
 * config/decompiler.properties.
 */
public final class Decompile {

    private Decompile() {}

    /**
     * Decompiles {@code mergedJar} into {@code srcOut}. Returns the list of
     * error lines from the decompiler log (for reports/decompile_errors.txt).
     */
    public static List<String> run(
            Path mergedJar,
            Path srcOut,
            List<String[]> options,
            List<Path> libraries,
            String heap,
            Path workDir)
            throws IOException, InterruptedException {
        Path vineflowerJar =
                ClasspathJars.findJar("vineflower")
                        .orElseThrow(() -> new IllegalStateException("vineflower jar not found on classpath"));
        Path vfOutDir = Files.createDirectories(workDir.resolve("vf-out"));

        List<String> command = new ArrayList<>();
        command.add(Procs.currentJavaExecutable().toString());
        command.add("-Xmx" + heap);
        command.add("-jar");
        command.add(vineflowerJar.toString());
        for (String[] option : options) {
            command.add("-" + option[0] + "=" + option[1]);
        }
        for (Path library : libraries) {
            command.add("-e=" + library);
        }
        command.add(mergedJar.toString());
        command.add(vfOutDir.toString());

        Path logFile = workDir.resolve("vineflower.log");
        int exit = Procs.runKillable(workDir, command, logFile);
        InterruptHandler.checkAbort();
        if (exit != 0) {
            throw new IOException(
                    "vineflower exited with code " + exit + "; see " + logFile);
        }

        // Depending on the Vineflower version the destination directory holds
        // either a mirrored archive (vf-out/merged.jar) or the exploded tree.
        Path resultJar = vfOutDir.resolve(mergedJar.getFileName().toString());
        if (Files.isRegularFile(resultJar)) {
            extractJavaSources(resultJar, srcOut);
        } else {
            copyJavaSources(vfOutDir, srcOut);
        }
        return errorLines(logFile);
    }

    private static void extractJavaSources(Path archive, Path srcOut) throws IOException {
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            List<? extends ZipEntry> entries =
                    zip.stream().sorted(java.util.Comparator.comparing(ZipEntry::getName)).toList();
            for (ZipEntry entry : entries) {
                if (entry.isDirectory() || !entry.getName().endsWith(".java")) {
                    continue;
                }
                Path target = srcOut.resolve(entry.getName());
                Files.createDirectories(target.getParent());
                try (InputStream in = zip.getInputStream(entry)) {
                    Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private static void copyJavaSources(Path vfOutDir, Path srcOut) throws IOException {
        boolean[] any = {false};
        try (var stream = Files.walk(vfOutDir)) {
            for (Path file : stream.filter(Files::isRegularFile).toList()) {
                if (!file.getFileName().toString().endsWith(".java")) {
                    continue;
                }
                Path target = srcOut.resolve(vfOutDir.relativize(file).toString());
                Files.createDirectories(target.getParent());
                Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING);
                any[0] = true;
            }
        }
        if (!any[0]) {
            throw new IOException("vineflower produced no .java output under " + vfOutDir);
        }
    }

    private static List<String> errorLines(Path logFile) throws IOException {
        if (!Files.exists(logFile)) {
            return List.of();
        }
        Set<String> lines = new LinkedHashSet<>();
        for (String line : Files.readAllLines(logFile, StandardCharsets.UTF_8)) {
            if (line.contains("ERROR")) {
                lines.add(line);
            }
        }
        return List.copyOf(lines);
    }

    /**
     * Parses decompiler.properties preserving file order. Values are trimmed;
     * wrap a value in double quotes to keep leading or trailing whitespace
     * (needed for whitespace-only options such as ind).
     */
    public static List<String[]> loadOptions(Path propertiesFile) throws IOException {
        List<String[]> options = new ArrayList<>();
        if (!Files.exists(propertiesFile)) {
            return options;
        }
        for (String raw : Files.readAllLines(propertiesFile, StandardCharsets.UTF_8)) {
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            int eq = line.indexOf('=');
            if (eq < 0) {
                throw new IOException("invalid decompiler option line: " + raw);
            }
            String value = line.substring(eq + 1).strip();
            if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
                value = value.substring(1, value.length() - 1);
            }
            if (value.isEmpty()) {
                throw new IOException("empty decompiler option value: " + raw);
            }
            options.add(new String[] {line.substring(0, eq).strip(), value});
        }
        return options;
    }
}
