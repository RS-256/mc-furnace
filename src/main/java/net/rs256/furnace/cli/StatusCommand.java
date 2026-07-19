package net.rs256.furnace.cli;

import java.nio.file.Files;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import net.rs256.furnace.AppContext;
import net.rs256.furnace.Furnace;
import net.rs256.furnace.git.TerraRepo;
import net.rs256.furnace.plan.Planner;
import picocli.CommandLine.Command;
import picocli.CommandLine.ParentCommand;

/** Shows ingested, pending, and interrupted state without modifying it. */
@Command(name = "status", description = "Show ingested, pending, and interrupted state.")
public class StatusCommand implements Callable<Integer> {

    @ParentCommand Furnace parent;

    @Override
    public Integer call() throws Exception {
        AppContext ctx = parent.createContext();
        System.out.println("terra repo: " + ctx.config().terraRepo());
        if (!Files.isDirectory(ctx.config().terraRepo().resolve(".git"))) {
            System.out.println("  (not initialized yet; run 'furnace update' or 'furnace add')");
            return 0;
        }

        for (String branch : List.of(TerraRepo.SNAPSHOTS, TerraRepo.RELEASES)) {
            Set<String> taken = ctx.terra().takenIds(branch);
            String head = ctx.terra().headSubject(branch);
            System.out.println(
                    branch + ": " + taken.size() + " versions" + (head != null ? ", head = " + head : ""));
        }
        List<String> afBranches = ctx.terra().afBranches();
        System.out.println("af branches: " + (afBranches.isEmpty() ? "(none)" : String.join(", ", afBranches)));

        // Leftover intermediates indicate an interrupted run.
        if (Files.isDirectory(ctx.config().workDir())) {
            try (var entries = Files.newDirectoryStream(ctx.config().workDir())) {
                for (var entry : entries) {
                    System.out.println(
                            "interrupted leftovers: " + entry + " (removed automatically on next run)");
                }
            }
        }

        try {
            var planned =
                    Planner.plan(
                            ctx.meta().manifest(),
                            ctx.overrides(),
                            ctx.aprilFools(),
                            ctx.config().scopeFrom());
            var pending = new BatchRunner(ctx).pending(planned);
            System.out.println(
                    "scope: " + planned.size() + " versions from " + ctx.config().scopeFrom() + " onward");
            System.out.println("pending: " + pending.size());
            pending.stream()
                    .limit(10)
                    .forEach(
                            p ->
                                    System.out.println(
                                            "  " + p.id() + (p.isAprilFools() ? " -> af/" + p.id() : "")));
            if (pending.size() > 10) {
                System.out.println("  ... and " + (pending.size() - 10) + " more");
            }
        } catch (Exception e) {
            System.out.println("pending: (manifest unavailable: " + e.getMessage() + ")");
        }
        return 0;
    }
}
