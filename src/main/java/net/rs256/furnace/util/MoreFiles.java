package net.rs256.furnace.util;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;

public final class MoreFiles {

    private MoreFiles() {}

    public static void deleteRecursively(Path root) {
        if (!Files.exists(root)) {
            return;
        }
        try {
            Files.walkFileTree(
                    root,
                    new SimpleFileVisitor<>() {
                        @Override
                        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                                throws IOException {
                            Files.deleteIfExists(file);
                            return FileVisitResult.CONTINUE;
                        }

                        @Override
                        public FileVisitResult postVisitDirectory(Path dir, IOException exc)
                                throws IOException {
                            if (exc != null) {
                                throw exc;
                            }
                            Files.deleteIfExists(dir);
                            return FileVisitResult.CONTINUE;
                        }
                    });
        } catch (IOException e) {
            throw new UncheckedIOException("failed to delete " + root, e);
        }
    }

    /** Deletes all children of {@code dir} except entries named in {@code keep}. */
    public static void clearDirectory(Path dir, String... keep) throws IOException {
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (var entries = Files.newDirectoryStream(dir)) {
            outer:
            for (Path entry : entries) {
                for (String k : keep) {
                    if (entry.getFileName().toString().equals(k)) {
                        continue outer;
                    }
                }
                deleteRecursively(entry);
            }
        }
    }

    public static void copyTree(Path source, Path target) throws IOException {
        Files.walkFileTree(
                source,
                new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
                            throws IOException {
                        Files.createDirectories(target.resolve(source.relativize(dir).toString()));
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                            throws IOException {
                        Files.copy(
                                file,
                                target.resolve(source.relativize(file).toString()),
                                StandardCopyOption.REPLACE_EXISTING);
                        return FileVisitResult.CONTINUE;
                    }
                });
    }

    public static Path freshDirectory(Path dir) throws IOException {
        deleteRecursively(dir);
        Files.createDirectories(dir);
        return dir;
    }
}
