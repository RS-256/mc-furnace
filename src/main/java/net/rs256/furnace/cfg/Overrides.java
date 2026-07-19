package net.rs256.furnace.cfg;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.yaml.snakeyaml.Yaml;

/** overrides.yaml: manual releaseTime corrections used for sorting only. */
public record Overrides(Map<String, String> releaseTimeOverrides) {

    public static Overrides load(Path file) {
        if (!Files.exists(file)) {
            return new Overrides(Map.of());
        }
        try (Reader reader = Files.newBufferedReader(file)) {
            Map<String, Object> raw = new Yaml().load(reader);
            Map<String, String> overrides = new LinkedHashMap<>();
            if (raw != null && raw.get("releaseTimeOverrides") instanceof Map<?, ?> map) {
                map.forEach((k, v) -> overrides.put(k.toString(), v.toString()));
            }
            return new Overrides(overrides);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read " + file, e);
        }
    }

    public String sortTime(String id, String releaseTime) {
        return releaseTimeOverrides.getOrDefault(id, releaseTime);
    }
}
