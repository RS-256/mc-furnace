package net.rs256.furnace;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/** Loads config/furnace.properties and exposes resolved paths and settings. */
public record FurnaceConfig(
        Path configDir,
        Path terraRepo,
        Path cacheDir,
        Path workDir,
        String manifestUrl,
        String decompilerHeap,
        boolean reportsEnabled,
        String scopeFrom,
        Map<Integer, Path> jres) {

    public static FurnaceConfig load(Path configDir) {
        Properties props = loadProps(configDir.resolve("furnace.properties"));
        Map<Integer, Path> jres = new LinkedHashMap<>();
        Path jresFile = configDir.resolve("jres.properties");
        if (Files.exists(jresFile)) {
            Properties jp = loadProps(jresFile);
            for (String key : jp.stringPropertyNames()) {
                jres.put(Integer.parseInt(key.trim()), Path.of(jp.getProperty(key).trim()));
            }
        }
        return new FurnaceConfig(
                configDir,
                Path.of(props.getProperty("terra.repo", "../mc-terra")).toAbsolutePath().normalize(),
                Path.of(props.getProperty("cache.dir", "cache")).toAbsolutePath().normalize(),
                Path.of(props.getProperty("work.dir", "work")).toAbsolutePath().normalize(),
                props.getProperty(
                        "manifest.url",
                        "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"),
                props.getProperty("decompiler.heap", "4g"),
                Boolean.parseBoolean(props.getProperty("reports.enabled", "true")),
                props.getProperty("scope.from", "1.14.4"),
                jres);
    }

    private static Properties loadProps(Path file) {
        Properties props = new Properties();
        if (Files.exists(file)) {
            try (Reader reader = Files.newBufferedReader(file)) {
                props.load(reader);
            } catch (IOException e) {
                throw new UncheckedIOException("failed to read " + file, e);
            }
        }
        return props;
    }
}
