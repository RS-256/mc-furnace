package net.rs256.furnace.cli;

import java.io.IOException;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.rs256.furnace.AppContext;
import net.rs256.furnace.InterruptHandler;
import net.rs256.furnace.git.TerraRepo;
import net.rs256.furnace.meta.VersionDetail;
import net.rs256.furnace.pipe.Pipeline;
import net.rs256.furnace.pipe.VersionJsonWriter;
import net.rs256.furnace.plan.Planner;
import net.rs256.furnace.util.MoreFiles;

/**
 * Shared engine for update/backfill/add: computes the pending
 * set (planned minus already committed), processes versions oldest first, and
 * treats each version as one transaction so re-running the same command
 * resumes after an interruption.
 */
public final class BatchRunner {

    private final AppContext ctx;
    private final Set<String> takenSnapshots;
    private final Set<String> takenReleases;
    private final List<String> afBranches;

    public BatchRunner(AppContext ctx) {
        this.ctx = ctx;
        this.takenSnapshots = ctx.terra().takenIds(TerraRepo.SNAPSHOTS);
        this.takenReleases = ctx.terra().takenIds(TerraRepo.RELEASES);
        this.afBranches = new ArrayList<>(ctx.terra().afBranches());
    }

    public boolean needsWork(Planner.Planned planned) {
        if (planned.isAprilFools()) {
            return !afBranches.contains("af/" + planned.id());
        }
        boolean needSnapshots = !takenSnapshots.contains(planned.id());
        boolean needReleases = planned.isRelease() && !takenReleases.contains(planned.id());
        return needSnapshots || needReleases;
    }

    public List<Planner.Planned> pending(List<Planner.Planned> planned) {
        return planned.stream().filter(this::needsWork).toList();
    }

    /** Runs the full batch; returns a process exit code. */
    public int runBatch(List<Planner.Planned> planned) throws IOException, InterruptedException {
        List<Planner.Planned> pending = pending(planned);
        System.out.println(
                "[furnace] " + planned.size() + " versions in scope, " + pending.size() + " pending");
        int done = 0;
        boolean stopped = false;
        for (Planner.Planned version : pending) {
            if (InterruptHandler.stopRequested()) {
                stopped = true;
                break;
            }
            try {
                if (processOne(version)) {
                    done++;
                }
            } catch (InterruptHandler.AbortException e) {
                MoreFiles.deleteRecursively(
                        ctx.config().workDir().resolve(Pipeline.sanitizeId(version.id())));
                stopped = true;
                break;
            }
        }
        int remaining = pending.size() - done;
        if (stopped) {
            System.out.println(
                    "[furnace] stopped: "
                            + done
                            + " ingested / "
                            + remaining
                            + " remaining. Re-run the same command to resume.");
        } else {
            System.out.println("[furnace] done: " + done + " ingested, " + remaining + " skipped/remaining.");
        }
        return 0;
    }

    /** Generates and commits one version. Returns false when skipped. */
    public boolean processOne(Planner.Planned planned) throws IOException, InterruptedException {
        String id = planned.id();
        String releaseTime = planned.entry().releaseTime();

        String afBase = planned.afBase();
        String afBaseCommit = null;
        if (planned.isAprilFools()) {
            afBaseCommit = ctx.terra().findCommit(TerraRepo.SNAPSHOTS, afBase);
            if (afBaseCommit == null) {
                System.out.println(
                        "[furnace] "
                                + id
                                + ": skipped; af/ base '"
                                + afBase
                                + "' is not on the snapshots branch yet");
                return false;
            }
        } else if (!checkAppendOrder(planned)) {
            return false;
        }

        VersionDetail detail = ctx.meta().detail(planned.entry());
        Pipeline.Generated generated;
        try {
            generated = ctx.pipeline().generate(detail);
        } catch (net.rs256.furnace.pipe.SkipVersionException e) {
            System.out.println("[furnace] " + id + ": skipped; " + e.getMessage());
            MoreFiles.deleteRecursively(ctx.config().workDir().resolve(Pipeline.sanitizeId(id)));
            return false;
        }
        Path tree = generated.tree();
        String body = commitBody(generated.toolchain());
        // Self-document manual ordering corrections in the commit body; the
        // subject keeps the fixed parseable "<id> (<releaseTime>)" format.
        // Only the snapshots branch is reordered by overrides, so only its
        // commits carry the note; releases stay chronological as published.
        String sortOverride = ctx.overrides().releaseTimeOverrides().get(id);
        String snapshotsBody =
                sortOverride == null
                        ? body
                        : body + "\nsort-time: " + sortOverride + " (manual order override)";

        if (planned.isAprilFools()) {
            String branch = "af/" + id;
            System.out.println("[furnace] " + id + ": committing to " + branch + " (base " + afBase + ")");
            ctx.terra().commitVersion(branch, afBaseCommit, tree, id, releaseTime, body, true);
            afBranches.add(branch);
        } else {
            if (!takenSnapshots.contains(id)) {
                System.out.println("[furnace] " + id + ": committing to " + TerraRepo.SNAPSHOTS);
                ctx.terra()
                        .commitVersion(
                                TerraRepo.SNAPSHOTS,
                                null,
                                tree,
                                id,
                                releaseTime,
                                snapshotsBody,
                                !planned.isRelease());
                takenSnapshots.add(id);
            }
            if (planned.isRelease() && !takenReleases.contains(id)) {
                System.out.println("[furnace] " + id + ": committing to " + TerraRepo.RELEASES);
                ctx.terra().commitVersion(TerraRepo.RELEASES, null, tree, id, releaseTime, body, true);
                takenReleases.add(id);
            }
        }
        MoreFiles.deleteRecursively(ctx.config().workDir().resolve(Pipeline.sanitizeId(id)));
        System.out.println("[furnace] " + id + ": committed");
        return true;
    }

    /**
     * Branches are strictly append-only in releaseTime order;
     * inserting an older version on top of a newer head would corrupt the
     * timeline, so it is refused.
     */
    private boolean checkAppendOrder(Planner.Planned planned) {
        for (String branch :
                planned.isRelease()
                        ? List.of(TerraRepo.SNAPSHOTS, TerraRepo.RELEASES)
                        : List.of(TerraRepo.SNAPSHOTS)) {
            String head = ctx.terra().headSubject(branch);
            String headTime = head == null ? null : TerraRepo.parseSubjectTime(head);
            if (headTime != null
                    && OffsetDateTime.parse(planned.entry().releaseTime())
                            .isBefore(OffsetDateTime.parse(headTime))
                    && needsBranch(planned, branch)) {
                System.out.println(
                        "[furnace] "
                                + planned.id()
                                + ": refused; it is older than the head of '"
                                + branch
                                + "' ("
                                + head
                                + "). History is append-only; rebuild it to insert an older version.");
                return false;
            }
        }
        return true;
    }

    private boolean needsBranch(Planner.Planned planned, String branch) {
        return branch.equals(TerraRepo.SNAPSHOTS)
                ? !takenSnapshots.contains(planned.id())
                : planned.isRelease() && !takenReleases.contains(planned.id());
    }

    public static String commitBody(VersionJsonWriter.Toolchain toolchain) {
        return "decompiler: "
                + toolchain.decompiler()
                + "\nmappings: "
                + toolchain.mappings()
                + "\nmerger: "
                + toolchain.merger()
                + "\npipeline: "
                + toolchain.pipelineCommit();
    }
}
