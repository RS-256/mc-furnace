package net.rs256.furnace.cli;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import net.rs256.furnace.AppContext;
import net.rs256.furnace.Furnace;
import net.rs256.furnace.meta.VersionDetail;
import net.rs256.furnace.pipe.Pipeline;
import net.rs256.furnace.pipe.SkipVersionException;
import net.rs256.furnace.util.MoreFiles;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

/**
 * Runs the full pipeline for one version (remap, decompile, extract, datagen)
 * without touching mc-terra, for testing the pipeline itself. On failure the
 * intermediates are kept for inspection (datagen.log etc.).
 */
@Command(name = "generate", description = "Generate one version into a local directory without committing.")
public class GenerateCommand implements Callable<Integer> {

    @ParentCommand Furnace parent;

    @Parameters(index = "0", paramLabel = "<version_id>", description = "piston-meta version id")
    String versionId;

    @Option(
            names = {"--dir"},
            description = "Directory that receives <version_id>/ (default: generated)")
    Path outDir = Path.of("generated");

    @Option(
            names = {"--force"},
            description = "Replace an existing output directory for this version")
    boolean force;

    @Option(
            names = {"--keep-work"},
            description = "Keep the intermediates under the work directory after success")
    boolean keepWork;

    @Override
    public Integer call() throws Exception {
        AppContext ctx = parent.createContext();
        Path target = outDir.resolve(Pipeline.sanitizeId(versionId));
        if (Files.exists(target) && !force) {
            System.err.println("[furnace] " + target + " already exists; pass --force to replace it");
            return 2;
        }

        VersionDetail detail = ctx.meta().detail(ctx.meta().requireEntry(versionId));
        Path work = ctx.config().workDir().resolve(Pipeline.sanitizeId(versionId));
        Path tree;
        try {
            tree = ctx.pipeline().generate(detail).tree();
        } catch (SkipVersionException e) {
            System.out.println("[furnace] " + versionId + ": skipped; " + e.getMessage());
            MoreFiles.deleteRecursively(work);
            return 1;
        } catch (Exception e) {
            System.err.println("[furnace] " + versionId + ": failed; intermediates kept in " + work);
            throw e;
        }

        MoreFiles.deleteRecursively(target);
        Files.createDirectories(outDir);
        try {
            Files.move(tree, target);
        } catch (java.io.IOException e) {
            // e.g. --dir on another drive: directories cannot be moved across file stores
            MoreFiles.copyTree(tree, target);
        }
        if (!keepWork) {
            MoreFiles.deleteRecursively(work);
        }
        System.out.println("[furnace] " + versionId + ": generated into " + target);
        return 0;
    }
}
