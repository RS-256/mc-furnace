package net.rs256.furnace.meta;

import com.google.gson.Gson;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.rs256.furnace.net.Downloader;

/**
 * piston-meta client. The manifest is fetched fresh on every run and mirrored
 * into the cache so add/regen keep working offline; per-version json files are
 * cached by SHA1 (SPEC 4.1).
 */
public final class PistonMeta {

    private static final Gson GSON = new Gson();

    private final Downloader downloader;
    private final String manifestUrl;
    private final Path metaCacheDir;

    private VersionManifest manifest;

    public PistonMeta(Downloader downloader, String manifestUrl, Path cacheDir) {
        this.downloader = downloader;
        this.manifestUrl = manifestUrl;
        this.metaCacheDir = cacheDir.resolve("meta");
    }

    public synchronized VersionManifest manifest() throws IOException, InterruptedException {
        if (manifest != null) {
            return manifest;
        }
        Path cached = metaCacheDir.resolve("version_manifest_v2.json");
        String json;
        try {
            json = downloader.fetchText(manifestUrl);
            Files.createDirectories(metaCacheDir);
            Files.writeString(cached, json, StandardCharsets.UTF_8);
        } catch (IOException e) {
            if (!Files.exists(cached)) {
                throw e;
            }
            System.out.println("[furnace] manifest fetch failed, using cached copy: " + e.getMessage());
            json = Files.readString(cached, StandardCharsets.UTF_8);
        }
        manifest = GSON.fromJson(json, VersionManifest.class);
        return manifest;
    }

    public VersionDetail detail(VersionManifest.Entry entry) throws IOException, InterruptedException {
        Path file =
                downloader.fetch(
                        entry.url(), metaCacheDir.resolve(entry.id() + ".json"), entry.sha1());
        return GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), VersionDetail.class);
    }

    public VersionManifest.Entry requireEntry(String id) throws IOException, InterruptedException {
        return manifest()
                .find(id)
                .orElseThrow(() -> new IllegalArgumentException("unknown version id: " + id));
    }
}
