package net.rs256.furnace.meta;

import java.util.List;
import java.util.Map;

/** piston-meta per-version json model (only the fields the pipeline needs). */
public record VersionDetail(
        String id,
        String type,
        String releaseTime,
        JavaVersion javaVersion,
        Map<String, DownloadInfo> downloads,
        List<Library> libraries) {

    public record JavaVersion(String component, int majorVersion) {}

    public record DownloadInfo(String sha1, long size, String url) {}

    public record Library(String name, LibraryDownloads downloads) {}

    public record LibraryDownloads(Artifact artifact) {}

    public record Artifact(String path, String sha1, String url) {}

    public DownloadInfo requireDownload(String key) {
        DownloadInfo info = downloads == null ? null : downloads.get(key);
        if (info == null) {
            throw new IllegalStateException(
                    "version " + id + " has no '" + key + "' download; Mojang mappings are required (SPEC scope: 1.14.4+)");
        }
        return info;
    }

    public int requiredJavaMajor() {
        return javaVersion == null ? 8 : javaVersion.majorVersion();
    }
}
