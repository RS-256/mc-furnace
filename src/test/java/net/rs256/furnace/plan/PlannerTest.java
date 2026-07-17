package net.rs256.furnace.plan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import net.rs256.furnace.cfg.AprilFools;
import net.rs256.furnace.cfg.Overrides;
import net.rs256.furnace.meta.VersionManifest;
import org.junit.jupiter.api.Test;

class PlannerTest {

    private static VersionManifest.Entry entry(String id, String type, String releaseTime) {
        return new VersionManifest.Entry(id, type, "https://example.invalid/" + id, "0", releaseTime);
    }

    // manifest order is newest-first, like piston-meta
    private final VersionManifest manifest =
            new VersionManifest(
                    new VersionManifest.Latest("1.15", "19w40a"),
                    List.of(
                            entry("1.15", "release", "2019-12-09T13:13:38+00:00"),
                            entry("19w40a", "snapshot", "2019-10-02T13:40:26+00:00"),
                            entry("19w99af", "snapshot", "2019-09-04T11:19:34+00:00"),
                            entry("19w34a", "snapshot", "2019-08-22T12:06:21+00:00"),
                            entry("1.14.4", "release", "2019-07-19T09:25:47+00:00"),
                            entry("1.14.3", "release", "2019-06-24T12:52:52+00:00"),
                            entry("3D Shareware v1.34", "old_beta", "2019-04-01T11:18:08+00:00")));

    private final AprilFools aprilFools = new AprilFools(Map.of("19w99af", "19w34a"));

    @Test
    void scopeStartsAtFromVersionAndSortsOldestFirst() {
        List<Planner.Planned> planned =
                Planner.plan(manifest, new Overrides(Map.of()), aprilFools, "1.14.4");
        assertEquals(List.of("1.14.4", "19w34a", "19w99af", "19w40a", "1.15"),
                planned.stream().map(Planner.Planned::id).toList());
    }

    @Test
    void flagsAprilFoolsAndReleases() {
        List<Planner.Planned> planned =
                Planner.plan(manifest, new Overrides(Map.of()), aprilFools, "1.14.4");
        Planner.Planned af = planned.stream().filter(p -> p.id().equals("19w99af")).findFirst().orElseThrow();
        assertTrue(af.isAprilFools());
        assertEquals("19w34a", af.afBase());
        Planner.Planned release = planned.stream().filter(p -> p.id().equals("1.15")).findFirst().orElseThrow();
        assertTrue(release.isRelease());
        assertNull(release.afBase());
    }

    @Test
    void excludesOlderVersionsAndNonScopeTypes() {
        List<Planner.Planned> planned =
                Planner.plan(manifest, new Overrides(Map.of()), aprilFools, "1.14.4");
        assertTrue(planned.stream().noneMatch(p -> p.id().equals("1.14.3")));
        assertTrue(planned.stream().noneMatch(p -> p.id().equals("3D Shareware v1.34")));
    }

    @Test
    void releaseTimeOverrideChangesSortOnly() {
        // push 19w34a after 19w40a for sorting purposes
        Overrides overrides =
                new Overrides(Map.of("19w34a", "2019-10-03T00:00:00+00:00"));
        List<Planner.Planned> planned = Planner.plan(manifest, overrides, aprilFools, "1.14.4");
        assertEquals(List.of("1.14.4", "19w99af", "19w40a", "19w34a", "1.15"),
                planned.stream().map(Planner.Planned::id).toList());
        // the recorded releaseTime stays as published
        Planner.Planned moved = planned.stream().filter(p -> p.id().equals("19w34a")).findFirst().orElseThrow();
        assertEquals("2019-08-22T12:06:21+00:00", moved.entry().releaseTime());
    }
}
