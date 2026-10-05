package net.rs256.furnace;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.rs256.furnace.cfg.AprilFools;
import net.rs256.furnace.cfg.Overrides;
import net.rs256.furnace.git.TerraRepo;
import net.rs256.furnace.meta.PistonMeta;
import net.rs256.furnace.net.Downloader;
import net.rs256.furnace.pipe.Pipeline;
import net.rs256.furnace.util.MoreFiles;

/** Wires configuration and services for the CLI commands. */
public final class AppContext {

    private final FurnaceConfig config;
    private final PistonMeta meta;
    private final TerraRepo terra;
    private final AprilFools aprilFools;
    private final Overrides overrides;
    private final Pipeline pipeline;

    private AppContext(
            FurnaceConfig config,
            PistonMeta meta,
            TerraRepo terra,
            AprilFools aprilFools,
            Overrides overrides,
            Pipeline pipeline) {
        this.config = config;
        this.meta = meta;
        this.terra = terra;
        this.aprilFools = aprilFools;
        this.overrides = overrides;
        this.pipeline = pipeline;
    }

    public static AppContext load(Path configDir) {
        try {
            FurnaceConfig config = FurnaceConfig.load(configDir);
            Downloader downloader = new Downloader();
            Overrides overrides = Overrides.load(configDir.resolve("overrides.yaml"));
            return new AppContext(
                    config,
                    new PistonMeta(downloader, config.manifestUrl(), config.cacheDir()),
                    new TerraRepo(config.terraRepo()),
                    AprilFools.load(configDir.resolve("april_fools.yaml")),
                    overrides,
                    new Pipeline(config, downloader, overrides));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Startup housekeeping: initialize/repair the terra repo and
     * remove leftover intermediates from a previous abnormal exit.
     */
    public void prepare(boolean initRepo) throws IOException {
        if (initRepo) {
            terra.ensureInitialized();
        }
        terra.recoverIfDirty();
        cleanWorkResidue();
    }

    public void cleanWorkResidue() throws IOException {
        Path work = config.workDir();
        if (Files.isDirectory(work)) {
            try (var entries = Files.newDirectoryStream(work)) {
                for (Path entry : entries) {
                    System.out.println(
                            "[furnace] removing leftover intermediates from a previous abnormal exit: " + entry);
                    MoreFiles.deleteRecursively(entry);
                }
            }
        }
    }

    public FurnaceConfig config() {
        return config;
    }

    public PistonMeta meta() {
        return meta;
    }

    public TerraRepo terra() {
        return terra;
    }

    public AprilFools aprilFools() {
        return aprilFools;
    }

    public Overrides overrides() {
        return overrides;
    }

    public Pipeline pipeline() {
        return pipeline;
    }
}
