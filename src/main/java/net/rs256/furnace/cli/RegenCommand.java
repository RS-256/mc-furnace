package net.rs256.furnace.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.stream.Stream;
import net.rs256.furnace.AppContext;
import net.rs256.furnace.Furnace;
import net.rs256.furnace.git.TerraRepo;
import net.rs256.furnace.meta.VersionDetail;
import net.rs256.furnace.pipe.Pipeline;
import net.rs256.furnace.util.MoreFiles;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

/**
 * SPEC 4.4: regenerates a version and compares it against the committed tree,
 * verifying reproducibility (SPEC 2: bit-identical regeneration).
 */
@Command(name = "regen", description = "Regenerate a version and diff it against the existing commit.")
public class RegenCommand implements Callable<Integer> {

    @ParentCommand Furnace parent;

    @Parameters(index = "0", paramLabel = "<version_id>", description = "piston-meta version id")
    String versionId;

    @Override
    public Integer call() throws Exception {
        AppContext ctx = parent.createContext();
        ctx.prepare(false);

        String ref = resolveRef(ctx);
        if (ref == null) {
            System.err.println("[furnace] version '" + versionId + "' is not committed in mc-terra yet");
            return 2;
        }

        VersionDetail detail = ctx.meta().detail(ctx.meta().requireEntry(versionId));
        Path regenerated = ctx.pipeline().generate(detail).tree();

        Path checkout =
                ctx.config().workDir().resolve("regen-checkout-" + Pipeline.sanitizeId(versionId));
        MoreFiles.deleteRecursively(checkout);
        ctx.terra().addWorktree(checkout, ref);
        try {
            List<String> differences = compareTrees(checkout, regenerated);
            if (differences.isEmpty()) {
                System.out.println(
                        "[furnace] " + versionId + ": regenerated tree is identical to commit " + ref);
                return 0;
            }
            System.out.println(
                    "[furnace] " + versionId + ": " + differences.size() + " paths differ from commit " + ref);
            differences.stream().limit(30).forEach(d -> System.out.println("  " + d));
            if (differences.size() > 30) {
                System.out.println("  ... and " + (differences.size() - 30) + " more");
            }
            if (differences.size() == 1 && differences.get(0).endsWith("version.json")) {
                System.out.println(
                        "  (only version.json differs; likely a changed pipelineCommit, not an output change)");
            }
            return 1;
        } finally {
            ctx.terra().removeWorktree(checkout);
            MoreFiles.deleteRecursively(checkout);
            MoreFiles.deleteRecursively(ctx.config().workDir().resolve(Pipeline.sanitizeId(versionId)));
        }
    }

    private String resolveRef(AppContext ctx) {
        String tag = versionId.replace('/', '-');
        if (ctx.terra().tagExists(tag)) {
            return tag;
        }
        for (String branch : List.of(TerraRepo.SNAPSHOTS, TerraRepo.RELEASES, "af/" + versionId)) {
            String sha = ctx.terra().findCommit(branch, versionId);
            if (sha != null) {
                return sha;
            }
        }
        return null;
    }

    private static List<String> compareTrees(Path committed, Path regenerated) throws IOException {
        TreeSet<String> paths = new TreeSet<>();
        collect(committed, paths);
        collect(regenerated, paths);
        List<String> differences = new ArrayList<>();
        for (String rel : paths) {
            Path a = committed.resolve(rel);
            Path b = regenerated.resolve(rel);
            boolean hasA = Files.isRegularFile(a);
            boolean hasB = Files.isRegularFile(b);
            if (!hasA) {
                differences.add("only regenerated: " + rel);
            } else if (!hasB) {
                differences.add("only committed:   " + rel);
            } else if (Files.mismatch(a, b) >= 0) {
                differences.add("content differs:  " + rel);
            }
        }
        return differences;
    }

    private static void collect(Path root, TreeSet<String> into) throws IOException {
        if (!Files.isDirectory(root)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(root)) {
            stream.filter(Files::isRegularFile)
                    .map(p -> root.relativize(p).toString().replace('\\', '/'))
                    .filter(rel -> !rel.startsWith(".git/") && !rel.equals(".git"))
                    .forEach(into::add);
        }
    }
}
