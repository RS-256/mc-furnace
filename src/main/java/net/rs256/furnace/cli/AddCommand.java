package net.rs256.furnace.cli;

import java.util.concurrent.Callable;
import net.rs256.furnace.AppContext;
import net.rs256.furnace.Furnace;
import net.rs256.furnace.plan.Planner;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

/** SPEC 4.4: generates and commits a single version. */
@Command(name = "add", description = "Generate and commit one version.")
public class AddCommand implements Callable<Integer> {

    @ParentCommand Furnace parent;

    @Parameters(index = "0", paramLabel = "<version_id>", description = "piston-meta version id")
    String versionId;

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
        Planner.Planned target =
                planned.stream()
                        .filter(p -> p.id().equals(versionId))
                        .findFirst()
                        .orElse(null);
        if (target == null) {
            System.err.println(
                    "[furnace] version '"
                            + versionId
                            + "' is not in scope (unknown id, or older than "
                            + ctx.config().scopeFrom()
                            + ", SPEC: 1.14.4+ only)");
            return 2;
        }
        BatchRunner runner = new BatchRunner(ctx);
        if (!runner.needsWork(target)) {
            System.out.println("[furnace] " + versionId + " is already committed on all target branches");
            return 0;
        }
        return runner.processOne(target) ? 0 : 1;
    }
}
