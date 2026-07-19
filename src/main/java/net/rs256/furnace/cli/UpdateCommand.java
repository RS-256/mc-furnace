package net.rs256.furnace.cli;

import java.util.concurrent.Callable;
import net.rs256.furnace.AppContext;
import net.rs256.furnace.Furnace;
import net.rs256.furnace.plan.Planner;
import picocli.CommandLine.Command;
import picocli.CommandLine.ParentCommand;

/** Ingests every version the manifest has that mc-terra lacks. */
@Command(
        name = "update",
        description = "Ingest all versions from the manifest that are not committed yet.")
public class UpdateCommand implements Callable<Integer> {

    @ParentCommand Furnace parent;

    @Override
    public Integer call() throws Exception {
        AppContext ctx = parent.createContext();
        ctx.prepare(true);
        var planned =
                Planner.plan(
                        ctx.meta().manifest(),
                        ctx.overrides(),
                        ctx.aprilFools(),
                        ctx.config().scopeFrom());
        return new BatchRunner(ctx).runBatch(planned);
    }
}
