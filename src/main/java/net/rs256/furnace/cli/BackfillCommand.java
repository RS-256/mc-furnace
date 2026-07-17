package net.rs256.furnace.cli;

import java.util.concurrent.Callable;
import net.rs256.furnace.AppContext;
import net.rs256.furnace.Furnace;
import net.rs256.furnace.plan.Planner;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

/** SPEC 4.4: bulk generation of past versions; interruptible and resumable (SPEC 4.5). */
@Command(name = "backfill", description = "Generate all versions from a starting version onward.")
public class BackfillCommand implements Callable<Integer> {

    @ParentCommand Furnace parent;

    @Option(
            names = "--from",
            description = "Oldest version to ingest (default: scope.from in furnace.properties)")
    String from;

    @Override
    public Integer call() throws Exception {
        AppContext ctx = parent.createContext();
        ctx.prepare(true);
        String fromId = from != null ? from : ctx.config().scopeFrom();
        var planned =
                Planner.plan(ctx.meta().manifest(), ctx.overrides(), ctx.aprilFools(), fromId);
        return new BatchRunner(ctx).runBatch(planned);
    }
}
