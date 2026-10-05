package net.rs256.furnace.cfg;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.yaml.snakeyaml.Yaml;

/**
 * overrides.yaml: manual per-version corrections.
 *
 * @param releaseTimeOverrides releaseTime corrections used for sorting only
 * @param skipReportsOnDatagenFailure versions whose data generator crash is
 *     tolerated: the version is committed without reports
 */
public record Overrides(Map<String, String> releaseTimeOverrides, Set<String> skipReportsOnDatagenFailure) {

    public Overrides(Map<String, String> releaseTimeOverrides) {
        this(releaseTimeOverrides, Set.of());
    }

    public static Overrides load(Path file) {
        if (!Files.exists(file)) {
            return new Overrides(Map.of());
        }
        try (Reader reader = Files.newBufferedReader(file)) {
            Map<String, Object> raw = new Yaml().load(reader);
            Map<String, String> overrides = new LinkedHashMap<>();
            Set<String> datagenSkips = new LinkedHashSet<>();
            if (raw != null && raw.get("releaseTimeOverrides") instanceof Map<?, ?> map) {
                map.forEach((k, v) -> overrides.put(k.toString(), v.toString()));
            }
            if (raw != null && raw.get("skipReportsOnDatagenFailure") instanceof List<?> list) {
                list.forEach(v -> datagenSkips.add(v.toString()));
            }
            return new Overrides(overrides, datagenSkips);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read " + file, e);
        }
    }

    public String sortTime(String id, String releaseTime) {
        return releaseTimeOverrides.getOrDefault(id, releaseTime);
    }
}
