package net.rs256.furnace.git;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.rs256.furnace.InterruptHandler;
import net.rs256.furnace.util.MoreFiles;

/**
 * The generated mc-terra repository: releases/snapshots branches, one
 * version per commit, commit dates pinned to releaseTime, af/<id> dead-end
 * branches for april fools versions.
 */
public final class TerraRepo {

    public static final String RELEASES = "releases";
    public static final String SNAPSHOTS = "snapshots";

    private final Path root;
    private final GitRunner git;

    public TerraRepo(Path root) {
        this.root = root;
        this.git = new GitRunner(root);
    }

    public Path root() {
        return root;
    }

    public void ensureInitialized() throws IOException {
        if (Files.isDirectory(root.resolve(".git"))) {
            return;
        }
        Files.createDirectories(root);
        git.run("init");
        git.run("config", "user.name", "mc-furnace");
        git.run("config", "user.email", "mc-furnace@invalid");
        git.run("config", "core.autocrlf", "false");
        git.run("config", "core.longpaths", "true");
        git.run("config", "commit.gpgsign", "false");
        // Recommended diff settings documented in README.
        git.run("config", "diff.renames", "true");
        git.run("config", "diff.algorithm", "histogram");
        System.out.println("[furnace] initialized mc-terra repository at " + root);
    }

    /** After a forced kill the worktree may be dirty; hard reset before resuming. */
    public void recoverIfDirty() throws IOException {
        if (!Files.isDirectory(root.resolve(".git"))) {
            return;
        }
        ensureLocalExcludes();
        String status = git.run("status", "--porcelain");
        if (status.isBlank()) {
            return;
        }
        System.out.println("[furnace] mc-terra worktree is dirty (previous abnormal exit); resetting");
        if (git.tryRun("rev-parse", "--verify", "HEAD")) {
            git.run("reset", "--hard", "HEAD");
        } else {
            git.tryRun("rm", "-r", "--cached", ".");
        }
        // -x is deliberately absent: locally excluded files (IDE metadata) survive
        git.run("clean", "-fd");
    }

    /**
     * Local-only ignores (.git/info/exclude): lets users open mc-terra in an
     * IDE without the recovery cleanup deleting IDE metadata. Not part of the
     * committed tree, so generated content stays deterministic.
     */
    private void ensureLocalExcludes() throws IOException {
        Path exclude = root.resolve(".git").resolve("info").resolve("exclude");
        List<String> wanted = List.of(".idea/", "*.iml", ".vscode/");
        List<String> existing = Files.exists(exclude) ? Files.readAllLines(exclude) : List.of();
        StringBuilder missing = new StringBuilder();
        for (String line : wanted) {
            if (!existing.contains(line)) {
                missing.append(line).append('\n');
            }
        }
        if (!missing.isEmpty()) {
            Files.createDirectories(exclude.getParent());
            Files.writeString(
                    exclude,
                    (existing.isEmpty() ? "" : String.join("\n", existing) + "\n") + missing,
                    java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    public boolean branchExists(String branch) {
        return git.tryRun("rev-parse", "--verify", "--quiet", "refs/heads/" + branch);
    }

    public boolean tagExists(String tag) {
        return git.tryRun("rev-parse", "--verify", "--quiet", "refs/tags/" + tag);
    }

    /** Version ids already committed on a branch, parsed from commit subjects. */
    public Set<String> takenIds(String branch) {
        Set<String> ids = new LinkedHashSet<>();
        if (!branchExists(branch)) {
            return ids;
        }
        for (String subject : git.run("log", "--format=%s", branch).lines().toList()) {
            String id = parseSubjectId(subject);
            if (id != null) {
                ids.add(id);
            }
        }
        return ids;
    }

    public List<String> afBranches() {
        if (!Files.isDirectory(root.resolve(".git"))) {
            return List.of();
        }
        return git.run("branch", "--list", "--format=%(refname:short)", "af/*").lines()
                .filter(s -> !s.isBlank())
                .toList();
    }

    /** Finds the commit of a version id on a branch, or null. */
    public String findCommit(String branch, String id) {
        if (!branchExists(branch)) {
            return null;
        }
        String prefix = id + " (";
        for (String line : git.run("log", "--format=%H %s", branch).lines().toList()) {
            int space = line.indexOf(' ');
            if (space > 0 && line.substring(space + 1).startsWith(prefix)) {
                return line.substring(0, space);
            }
        }
        return null;
    }

    /** Subject of the branch head commit, or null when the branch is missing. */
    public String headSubject(String branch) {
        if (!branchExists(branch)) {
            return null;
        }
        String subject = git.run("log", "-1", "--format=%s", branch).strip();
        return subject.isEmpty() ? null : subject;
    }

    /** Extracts the releaseTime recorded in a commit subject "<id> (<time>)", or null. */
    public static String parseSubjectTime(String subject) {
        int open = subject.indexOf(" (");
        int close = subject.lastIndexOf(')');
        return (open > 0 && close > open + 2) ? subject.substring(open + 2, close) : null;
    }

    public static String parseSubjectId(String subject) {
        int paren = subject.indexOf(" (");
        return paren > 0 ? subject.substring(0, paren) : null;
    }

    /**
     * Replaces the branch worktree with {@code tree} and
     * commits with author/committer dates pinned to releaseTime. The whole
     * git section runs inside the interrupt critical section.
     */
    public void commitVersion(
            String branch,
            String startPoint,
            Path tree,
            String id,
            String releaseTime,
            String body,
            boolean tag)
            throws IOException {
        InterruptHandler.enterCritical();
        try {
            if (branchExists(branch)) {
                git.run("switch", "--force", branch);
            } else if (startPoint != null) {
                git.run("switch", "--force", "-c", branch, startPoint);
            } else {
                git.run("switch", "--orphan", branch);
            }
            MoreFiles.clearDirectory(root, ".git");
            MoreFiles.copyTree(tree, root);
            git.run("add", "-A");

            Path messageFile = Files.createTempFile("furnace-commit", ".txt");
            try {
                Files.writeString(
                        messageFile,
                        id + " (" + releaseTime + ")\n\n" + body + "\n",
                        StandardCharsets.UTF_8);
                Map<String, String> env =
                        Map.of(
                                "GIT_AUTHOR_DATE", releaseTime,
                                "GIT_COMMITTER_DATE", releaseTime);
                git.runWithEnv(env, "commit", "--quiet", "--allow-empty", "-F", messageFile.toString());
            } finally {
                Files.deleteIfExists(messageFile);
            }
            if (tag) {
                String tagName = id.replace('/', '-');
                if (!tagExists(tagName)) {
                    git.run("tag", tagName);
                }
            }
        } finally {
            InterruptHandler.exitCritical();
        }
    }

    /** Checks out a ref into a temporary worktree for comparison (regen). */
    public Path addWorktree(Path dir, String ref) {
        git.run("worktree", "add", "--detach", dir.toString(), ref);
        return dir;
    }

    public void removeWorktree(Path dir) {
        git.tryRun("worktree", "remove", "--force", dir.toString());
    }
}
