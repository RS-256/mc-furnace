package net.rs256.furnace.cfg;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.yaml.snakeyaml.Yaml;

/** april_fools.yaml: exclusion list + af/ branch base definitions (SPEC 3.2.1). */
public record AprilFools(Map<String, String> baseById) {

    public static AprilFools load(Path file) {
        if (!Files.exists(file)) {
            return new AprilFools(Map.of());
        }
        try (Reader reader = Files.newBufferedReader(file)) {
            List<Map<String, Object>> raw = new Yaml().load(reader);
            Map<String, String> baseById = new LinkedHashMap<>();
            if (raw != null) {
                for (Map<String, Object> entry : raw) {
                    Object id = entry.get("id");
                    Object base = entry.get("base");
                    if (id == null || base == null) {
                        throw new IllegalArgumentException(
                                "april_fools.yaml entries need both 'id' and 'base': " + entry);
                    }
                    baseById.put(id.toString(), base.toString());
                }
            }
            return new AprilFools(baseById);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read " + file, e);
        }
    }

    public boolean isAprilFools(String id) {
        return baseById.containsKey(id);
    }
}
