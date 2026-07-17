package net.rs256.furnace.util;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Locates tool jars on the application classpath. The distribution places all
 * dependencies in lib/, so tool versions can be derived from jar file names
 * for version.json (SPEC 3.4).
 */
public final class ClasspathJars {

    private ClasspathJars() {}

    public static Optional<Path> findJar(String artifactId) {
        String prefix = artifactId + "-";
        for (String entry : System.getProperty("java.class.path", "").split(java.io.File.pathSeparator)) {
            if (entry.isBlank()) {
                continue;
            }
            Path path = Path.of(entry);
            String name = path.getFileName() == null ? "" : path.getFileName().toString();
            if (name.startsWith(prefix) && name.endsWith(".jar") && Files.exists(path)) {
                return Optional.of(path);
            }
        }
        return Optional.empty();
    }

    public static String findVersion(String artifactId) {
        return findJar(artifactId)
                .map(p -> versionFromFileName(artifactId, p.getFileName().toString()))
                .orElse("unknown");
    }

    static String versionFromFileName(String artifactId, String fileName) {
        Matcher matcher =
                Pattern.compile(Pattern.quote(artifactId) + "-(.+)\\.jar").matcher(fileName);
        return matcher.matches() ? matcher.group(1) : "unknown";
    }
}
