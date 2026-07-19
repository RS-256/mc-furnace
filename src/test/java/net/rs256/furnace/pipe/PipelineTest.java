package net.rs256.furnace.pipe;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import net.rs256.furnace.meta.VersionDetail;
import org.junit.jupiter.api.Test;

class PipelineTest {

    private static VersionDetail.Library lib(String path) {
        return new VersionDetail.Library(
                "g:a:v",
                new VersionDetail.LibraryDownloads(
                        new VersionDetail.Artifact(path, "0", "https://example.invalid/" + path)));
    }

    @Test
    void dedupesLibrariesSharingOneArtifactPath() {
        // old manifests list lwjgl once plain and once per natives rule, all
        // pointing at the same jar path
        var artifacts =
                Pipeline.dedupeArtifacts(
                        List.of(
                                lib("org/lwjgl/lwjgl/3.2.1/lwjgl-3.2.1.jar"),
                                lib("org/lwjgl/lwjgl/3.2.1/lwjgl-3.2.1.jar"),
                                lib("com/mojang/brigadier/1.0/brigadier-1.0.jar"),
                                new VersionDetail.Library("no-artifact", null)));
        assertEquals(2, artifacts.size());
        assertEquals(
                List.of(
                        "org/lwjgl/lwjgl/3.2.1/lwjgl-3.2.1.jar",
                        "com/mojang/brigadier/1.0/brigadier-1.0.jar"),
                artifacts.stream().map(VersionDetail.Artifact::path).toList());
    }

    @Test
    void toleratesMissingLibraryList() {
        assertEquals(0, Pipeline.dedupeArtifacts(null).size());
    }
}
