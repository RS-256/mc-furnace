package net.rs256.furnace.pipe;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import net.rs256.furnace.BuildInfo;
import net.rs256.furnace.FurnaceConfig;
import net.rs256.furnace.InterruptHandler;
import net.rs256.furnace.cfg.ExcludeList;
import net.rs256.furnace.cfg.Overrides;
import net.rs256.furnace.meta.VersionDetail;
import net.rs256.furnace.net.Downloader;
import net.rs256.furnace.util.ClasspathJars;
import net.rs256.furnace.util.MoreFiles;

/**
 * The full per-version flow. All intermediates live under
 * work/<id>/ and are deleted by the caller after a successful commit
 * so one version forms one transaction.
 */
public final class Pipeline {

    private static final Gson GSON = new Gson();

    private final FurnaceConfig config;
    private final Downloader downloader;
    private final Overrides overrides;
    private final ExcludeList excludes;
    private final List<String[]> decompilerOptions;
    private final String vineflowerVersion;
    private final String stitchVersion;
    private final BuildInfo buildInfo;

    public Pipeline(FurnaceConfig config, Downloader downloader, Overrides overrides) throws IOException {
        this.config = config;
        this.downloader = downloader;
        this.overrides = overrides;
        this.excludes = ExcludeList.load(config.configDir().resolve("excludes.txt"));
        this.decompilerOptions =
                Decompile.loadOptions(config.configDir().resolve("decompiler.properties"));
        this.vineflowerVersion = ClasspathJars.findVersion("vineflower");
        this.stitchVersion = ClasspathJars.findVersion("stitch");
        this.buildInfo = BuildInfo.load();
    }

    /** One generated version: the assembled tree plus the toolchain that produced it. */
    public record Generated(Path tree, VersionJsonWriter.Toolchain toolchain) {}

    public static String sanitizeId(String id) {
        return id.replace('/', '-');
    }

    /** Runs steps 1-7 for one version and returns the assembled output tree. */
    public Generated generate(VersionDetail detail) throws IOException, InterruptedException {
        String id = detail.id();
        Path work = MoreFiles.freshDirectory(config.workDir().resolve(sanitizeId(id)));
        Path out = Files.createDirectories(work.resolve("out"));
        Path jarCache = config.cacheDir().resolve("jars").resolve(sanitizeId(id));

        // From the 26.1 line on, Mojang ships unobfuscated jars and no longer
        // publishes mappings; remapping is unnecessary then.
        boolean mapped = detail.hasMojangMappings();
        log(id, mapped ? "downloading jars and mappings" : "downloading jars");
        VersionDetail.DownloadInfo client = detail.requireDownload("client");
        VersionDetail.DownloadInfo server = detail.requireDownload("server");
        Path clientJar = downloader.fetch(client.url(), jarCache.resolve("client.jar"), client.sha1());
        Path serverJar = downloader.fetch(server.url(), jarCache.resolve("server.jar"), server.sha1());
        List<Path> libraries = downloadLibraries(detail);
        InterruptHandler.checkAbort();

        if (!mapped && !isUnobfuscated(clientJar)) {
            throw new SkipVersionException(
                    "no Mojang mappings published and the jar is obfuscated (e.g. 19w34a/19w35a); out of scope");
        }

        boolean bundler = isBundler(serverJar);
        Path clientForMerge;
        Path serverForMerge;
        Path serverInner; // raw inner jar, keeps data/ resources for extraction
        if (mapped) {
            VersionDetail.DownloadInfo clientMap = detail.requireDownload("client_mappings");
            VersionDetail.DownloadInfo serverMap = detail.requireDownload("server_mappings");
            Path clientTxt =
                    downloader.fetch(clientMap.url(), jarCache.resolve("client.txt"), clientMap.sha1());
            Path serverTxt =
                    downloader.fetch(serverMap.url(), jarCache.resolve("server.txt"), serverMap.sha1());

            log(id, "converting mappings");
            var clientTree = Mappings.readProguard(clientTxt);
            var serverTree = Mappings.readProguard(serverTxt);
            Path clientTiny = Mappings.writeTiny(clientTree, work.resolve("client.tiny"));
            Path serverTiny = Mappings.writeTiny(serverTree, work.resolve("server.tiny"));
            InterruptHandler.checkAbort();

            log(id, "unpacking server jar");
            serverInner = ServerBundle.extract(serverJar, work, Mappings.officialClassNames(serverTree));
            InterruptHandler.checkAbort();

            log(id, "remapping client");
            clientForMerge = work.resolve("client-named.jar");
            Remap.remap(clientJar, clientForMerge, clientTiny, libraries);
            InterruptHandler.checkAbort();

            log(id, "remapping server");
            serverForMerge = work.resolve("server-named.jar");
            Remap.remap(serverInner, serverForMerge, serverTiny, libraries);
            InterruptHandler.checkAbort();
        } else {
            log(id, "jars ship unobfuscated; skipping remap");
            serverInner = ServerBundle.extract(serverJar, work, java.util.Set.of());
            clientForMerge = clientJar;
            serverForMerge = serverInner;
            InterruptHandler.checkAbort();
        }

        log(id, "merging client and server");
        Path merged = work.resolve("merged.jar");
        Merge.merge(clientForMerge, serverForMerge, merged);
        InterruptHandler.checkAbort();

        log(id, "decompiling (this takes a few minutes)");
        List<String> decompileErrors =
                Decompile.run(
                        merged,
                        out.resolve("src"),
                        decompilerOptions,
                        libraries,
                        config.decompilerHeap(),
                        work);
        InterruptHandler.checkAbort();

        log(id, "extracting data/ and assets/");
        Extract.extractPrefix(serverInner, "data/", out, excludes);
        Extract.extractPrefix(clientJar, "assets/", out, excludes);
        InterruptHandler.checkAbort();

        if (config.reportsEnabled()) {
            log(id, "running data generator (--reports)");
            try {
                Reports.run(config, detail, serverJar, bundler, work, out.resolve("reports"));
            } catch (Reports.DatagenFailedException e) {
                if (!overrides.skipReportsOnDatagenFailure().contains(id)) {
                    throw e;
                }
                log(id, "data generator crashed; continuing without reports (overrides.yaml)");
                writeDatagenFailure(out.resolve("reports"), e);
            }
            InterruptHandler.checkAbort();
        }
        if (!decompileErrors.isEmpty()) {
            Files.createDirectories(out.resolve("reports"));
            Files.writeString(
                    out.resolve("reports").resolve("decompile_errors.txt"),
                    String.join("\n", decompileErrors) + "\n",
                    StandardCharsets.UTF_8);
        }

        log(id, "writing version.json");
        VersionJsonWriter.Toolchain toolchain =
                VersionJsonWriter.Toolchain.current(
                        vineflowerVersion,
                        stitchVersion,
                        buildInfo,
                        mapped ? "mojang-official" : "unobfuscated");
        int[] versions = readClientVersionNumbers(clientJar);
        VersionJsonWriter.write(
                out.resolve("version.json"),
                detail,
                versions == null ? null : versions[0],
                versions == null ? null : versions[1],
                toolchain);
        writeTreeBoilerplate(out);
        return new Generated(out, toolchain);
    }

    /** Records a tolerated datagen crash in place of the reports it prevented. */
    private static void writeDatagenFailure(Path reports, Reports.DatagenFailedException e)
            throws IOException {
        Files.createDirectories(reports);
        String text =
                "The data generator crashed for this version, no reports were generated.\n"
                        + "Listed under skipReportsOnDatagenFailure in mc-furnace config/overrides.yaml.\n"
                        + (e.excerpt() == null ? "" : "\n" + e.excerpt());
        Files.writeString(reports.resolve("datagen_failed.txt"), text, StandardCharsets.UTF_8);
    }

    /** Unobfuscated builds keep real class names; Minecraft.class is a stable probe. */
    private static boolean isUnobfuscated(Path clientJar) throws IOException {
        return Extract.readEntry(clientJar, "net/minecraft/client/Minecraft.class") != null;
    }

    private List<Path> downloadLibraries(VersionDetail detail) throws IOException, InterruptedException {
        java.util.Collection<VersionDetail.Artifact> artifacts = dedupeArtifacts(detail.libraries());
        Path libCache = config.cacheDir().resolve("libraries");
        List<Path> result = new ArrayList<>();
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<Path>> futures = new ArrayList<>();
            for (VersionDetail.Artifact artifact : artifacts) {
                futures.add(
                        executor.submit(
                                () ->
                                        downloader.fetch(
                                                artifact.url(),
                                                libCache.resolve(artifact.path()),
                                                artifact.sha1())));
            }
            for (Future<Path> future : futures) {
                try {
                    result.add(future.get());
                } catch (java.util.concurrent.ExecutionException e) {
                    if (e.getCause() instanceof IOException io) {
                        throw io;
                    }
                    if (e.getCause() instanceof RuntimeException re) {
                        throw re;
                    }
                    throw new IOException("library download failed", e.getCause());
                }
            }
        }
        result.sort(java.util.Comparator.comparing(Path::toString));
        return result;
    }

    /**
     * Old manifests list the same artifact under several library entries
     * (e.g. lwjgl main + natives variants sharing one jar); deduplicate by
     * path so concurrent downloads never race on the same target file.
     */
    static java.util.Collection<VersionDetail.Artifact> dedupeArtifacts(
            List<VersionDetail.Library> libraries) {
        java.util.Map<String, VersionDetail.Artifact> byPath = new java.util.LinkedHashMap<>();
        if (libraries != null) {
            for (VersionDetail.Library library : libraries) {
                if (library.downloads() != null && library.downloads().artifact() != null) {
                    VersionDetail.Artifact artifact = library.downloads().artifact();
                    byPath.putIfAbsent(artifact.path(), artifact);
                }
            }
        }
        return byPath.values();
    }

    private static boolean isBundler(Path serverJar) throws IOException {
        try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(serverJar.toFile())) {
            return zip.getEntry("META-INF/versions.list") != null;
        }
    }

    /** Reads world/protocol versions from the version.json inside the client jar. */
    private static int[] readClientVersionNumbers(Path clientJar) throws IOException {
        byte[] raw = Extract.readEntry(clientJar, "version.json");
        if (raw == null) {
            return null;
        }
        JsonObject json = GSON.fromJson(new String(raw, StandardCharsets.UTF_8), JsonObject.class);
        if (!json.has("world_version") || !json.has("protocol_version")) {
            return null;
        }
        return new int[] {json.get("world_version").getAsInt(), json.get("protocol_version").getAsInt()};
    }

    private static void writeTreeBoilerplate(Path out) throws IOException {
        Files.writeString(
                out.resolve(".gitattributes"),
                "* text eol=lf\n",
                StandardCharsets.UTF_8);
        Files.writeString(
                out.resolve(".gitignore"),
                ".DS_Store\nThumbs.db\n",
                StandardCharsets.UTF_8);
    }

    private static void log(String id, String message) {
        System.out.println("[furnace] " + id + ": " + message);
    }
}
