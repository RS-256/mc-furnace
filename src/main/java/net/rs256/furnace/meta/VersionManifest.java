package net.rs256.furnace.meta;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/** piston-meta version_manifest_v2.json model. */
public record VersionManifest(Latest latest, List<Entry> versions) {

    public record Latest(String release, String snapshot) {}

    public record Entry(String id, String type, String url, String sha1, String releaseTime) {
        public OffsetDateTime releaseTimeParsed() {
            return OffsetDateTime.parse(releaseTime);
        }
    }

    public Optional<Entry> find(String id) {
        return versions.stream().filter(v -> v.id().equals(id)).findFirst();
    }
}
