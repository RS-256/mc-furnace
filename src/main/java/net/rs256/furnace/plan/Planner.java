package net.rs256.furnace.plan;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import net.rs256.furnace.cfg.AprilFools;
import net.rs256.furnace.cfg.Overrides;
import net.rs256.furnace.meta.VersionManifest;

/**
 * Turns the piston-meta manifest into the ordered work list (SPEC 4.4):
 * releases and snapshots from the scope start onward, sorted by releaseTime
 * (with manual overrides, SPEC 6), april fools versions flagged for af/
 * branches (SPEC 3.2.1).
 */
public final class Planner {

    private static final Set<String> IN_SCOPE_TYPES = Set.of("release", "snapshot");

    /** One version to process; afBase is null for regular versions. */
    public record Planned(VersionManifest.Entry entry, OffsetDateTime sortTime, String afBase) {
        public String id() {
            return entry.id();
        }

        public boolean isRelease() {
            return "release".equals(entry.type());
        }

        public boolean isAprilFools() {
            return afBase != null;
        }
    }

    private Planner() {}

    public static List<Planned> plan(
            VersionManifest manifest, Overrides overrides, AprilFools aprilFools, String fromId) {
        VersionManifest.Entry from =
                manifest.find(fromId)
                        .orElseThrow(() -> new IllegalArgumentException("unknown scope start version: " + fromId));
        OffsetDateTime fromTime = sortTime(overrides, from);
        return manifest.versions().stream()
                .filter(v -> IN_SCOPE_TYPES.contains(v.type()))
                .map(v -> new Planned(v, sortTime(overrides, v), aprilFools.baseById().get(v.id())))
                .filter(p -> !p.sortTime().isBefore(fromTime))
                .sorted(
                        Comparator.comparing(Planned::sortTime)
                                .thenComparing(p -> p.entry().id()))
                .toList();
    }

    private static OffsetDateTime sortTime(Overrides overrides, VersionManifest.Entry entry) {
        return OffsetDateTime.parse(overrides.sortTime(entry.id(), entry.releaseTime()));
    }
}
