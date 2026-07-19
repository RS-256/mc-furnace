package net.rs256.furnace.git;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TerraRepoTest {

    private static Path tree(Path tmp, String name, String content) throws Exception {
        Path dir = tmp.resolve(name);
        Files.createDirectories(dir.resolve("src"));
        Files.writeString(dir.resolve("src").resolve("A.java"), content, StandardCharsets.UTF_8);
        Files.writeString(dir.resolve("version.json"), "{}\n", StandardCharsets.UTF_8);
        return dir;
    }

    @Test
    void commitsVersionsWithPinnedDatesAndParsesThemBack(@TempDir Path tmp) throws Exception {
        TerraRepo repo = new TerraRepo(tmp.resolve("terra"));
        repo.ensureInitialized();
        repo.recoverIfDirty();

        repo.commitVersion(
                TerraRepo.SNAPSHOTS,
                null,
                tree(tmp, "t1", "class A { int x = 1; }\n"),
                "24w14a",
                "2024-04-03T11:49:39+00:00",
                "decompiler: vineflower-test",
                true);
        repo.commitVersion(
                TerraRepo.SNAPSHOTS,
                null,
                tree(tmp, "t2", "class A { int x = 2; }\n"),
                "1.21",
                "2024-06-13T08:24:03+00:00",
                "decompiler: vineflower-test",
                false);
        repo.commitVersion(
                TerraRepo.RELEASES,
                null,
                tree(tmp, "t3", "class A { int x = 2; }\n"),
                "1.21",
                "2024-06-13T08:24:03+00:00",
                "decompiler: vineflower-test",
                true);

        Set<String> snapshots = repo.takenIds(TerraRepo.SNAPSHOTS);
        assertEquals(Set.of("24w14a", "1.21"), snapshots);
        assertEquals(Set.of("1.21"), repo.takenIds(TerraRepo.RELEASES));
        assertTrue(repo.tagExists("24w14a"));
        assertTrue(repo.tagExists("1.21"));

        String head = repo.headSubject(TerraRepo.SNAPSHOTS);
        assertEquals("1.21 (2024-06-13T08:24:03+00:00)", head);
        assertEquals("2024-06-13T08:24:03+00:00", TerraRepo.parseSubjectTime(head));

        // Author date is pinned to releaseTime.
        GitRunner git = new GitRunner(tmp.resolve("terra"));
        String authorDate = git.run("log", "-1", "--format=%aI", TerraRepo.SNAPSHOTS).strip();
        assertEquals(
                java.time.OffsetDateTime.parse("2024-06-13T08:24:03+00:00").toInstant(),
                java.time.OffsetDateTime.parse(authorDate).toInstant());

        assertNotNull(repo.findCommit(TerraRepo.SNAPSHOTS, "24w14a"));
        assertNull(repo.findCommit(TerraRepo.SNAPSHOTS, "9.9.9"));
    }

    @Test
    void afBranchStartsFromBaseCommitAndIsADeadEnd(@TempDir Path tmp) throws Exception {
        TerraRepo repo = new TerraRepo(tmp.resolve("terra"));
        repo.ensureInitialized();

        repo.commitVersion(
                TerraRepo.SNAPSHOTS, null, tree(tmp, "base", "class A {}\n"),
                "24w12a", "2024-03-20T14:07:09+00:00", "x", true);
        repo.commitVersion(
                TerraRepo.SNAPSHOTS, null, tree(tmp, "next", "class A { int y; }\n"),
                "24w13a", "2024-03-27T13:24:07+00:00", "x", true);

        String baseSha = repo.findCommit(TerraRepo.SNAPSHOTS, "24w12a");
        repo.commitVersion(
                "af/24w14potato", baseSha, tree(tmp, "af", "class A { void potato() {} }\n"),
                "24w14potato", "2024-04-01T11:07:19+00:00", "x", true);

        assertEquals(java.util.List.of("af/24w14potato"), repo.afBranches());
        GitRunner git = new GitRunner(tmp.resolve("terra"));
        String parent = git.run("log", "-1", "--format=%P", "af/24w14potato").strip();
        assertEquals(baseSha, parent);
        // snapshots is unaffected by the af commit
        assertFalse(repo.takenIds(TerraRepo.SNAPSHOTS).contains("24w14potato"));
    }
}
