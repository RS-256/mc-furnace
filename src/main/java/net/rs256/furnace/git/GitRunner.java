package net.rs256.furnace.git;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.rs256.furnace.util.Procs;

/** Thin wrapper around the git CLI, rooted at one repository. */
public final class GitRunner {

    private final Path repo;

    public GitRunner(Path repo) {
        this.repo = repo;
    }

    public Path repo() {
        return repo;
    }

    /** Runs git, throwing on a non-zero exit. */
    public String run(String... args) {
        return runWithEnv(null, args);
    }

    public String runWithEnv(Map<String, String> env, String... args) {
        Procs.Result result = exec(env, args);
        if (result.exitCode() != 0) {
            throw new IllegalStateException(
                    "git " + String.join(" ", args) + " failed (" + result.exitCode() + "):\n" + result.stderr());
        }
        return result.stdout();
    }

    /** Runs git and reports success instead of throwing. */
    public boolean tryRun(String... args) {
        return exec(null, args).exitCode() == 0;
    }

    private Procs.Result exec(Map<String, String> env, String... args) {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.add("-C");
        command.add(repo.toString());
        command.addAll(List.of(args));
        try {
            return Procs.run(null, command, env);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to run git", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while running git", e);
        }
    }
}
