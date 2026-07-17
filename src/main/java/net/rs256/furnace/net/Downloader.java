package net.rs256.furnace.net;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import net.rs256.furnace.InterruptHandler;
import net.rs256.furnace.util.Hashes;

/**
 * SHA1-verified downloads with a persistent cache (SPEC 4.5): a partially
 * downloaded file fails verification and is re-fetched; verified files are
 * reused across runs.
 */
public final class Downloader {

    private final HttpClient client =
            HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .connectTimeout(Duration.ofSeconds(30))
                    .build();

    /**
     * Ensures {@code target} exists with the expected SHA1 (nullable to skip
     * verification), downloading from {@code url} when missing or corrupt.
     */
    public Path fetch(String url, Path target, String expectedSha1) throws IOException, InterruptedException {
        InterruptHandler.checkAbort();
        if (Files.exists(target) && expectedSha1 != null && expectedSha1.equalsIgnoreCase(Hashes.sha1(target))) {
            return target;
        }
        Files.createDirectories(target.getParent());
        Path tmp = target.resolveSibling(target.getFileName() + ".part");
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).GET().build();
        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) {
            throw new IOException("HTTP " + response.statusCode() + " for " + url);
        }
        try (InputStream in = response.body()) {
            Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
        }
        if (expectedSha1 != null) {
            String actual = Hashes.sha1(tmp);
            if (!expectedSha1.equalsIgnoreCase(actual)) {
                Files.deleteIfExists(tmp);
                throw new IOException("SHA1 mismatch for " + url + ": expected " + expectedSha1 + ", got " + actual);
            }
        }
        Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        return target;
    }

    /** Fetches a small text resource without caching. */
    public String fetchText(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).GET().build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("HTTP " + response.statusCode() + " for " + url);
        }
        return response.body();
    }
}
