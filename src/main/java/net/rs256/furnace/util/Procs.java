package net.rs256.furnace.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.rs256.furnace.InterruptHandler;

/** Helper for external processes (git, java/Vineflower, data generator). */
public final class Procs {

    public record Result(int exitCode, String stdout, String stderr) {}

    private Procs() {}

    /** Runs a process, captures stdout/stderr as strings. */
    public static Result run(Path workingDir, List<String> command, java.util.Map<String, String> extraEnv)
            throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(command);
        if (workingDir != null) {
            builder.directory(workingDir.toFile());
        }
        if (extraEnv != null) {
            builder.environment().putAll(extraEnv);
        }
        Process process = builder.start();
        StringBuilder out = new StringBuilder();
        StringBuilder err = new StringBuilder();
        Thread outPump = pump(process.getInputStream(), out);
        Thread errPump = pump(process.getErrorStream(), err);
        int code = process.waitFor();
        outPump.join();
        errPump.join();
        return new Result(code, out.toString(), err.toString());
    }

    /**
     * Runs a long, killable process (decompiler / data generator), streaming its
     * combined output to {@code logFile}. The process is registered with the
     * interrupt handler so a graceful stop terminates it.
     */
    public static int runKillable(Path workingDir, List<String> command, Path logFile)
            throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
        if (workingDir != null) {
            builder.directory(workingDir.toFile());
        }
        Process process = builder.start();
        InterruptHandler.registerKillable(process);
        try (Writer log = Files.newBufferedWriter(logFile, StandardCharsets.UTF_8);
                BufferedReader reader =
                        new BufferedReader(
                                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                log.write(line);
                log.write('\n');
            }
            return process.waitFor();
        } finally {
            InterruptHandler.unregisterKillable(process);
        }
    }

    private static Thread pump(java.io.InputStream in, StringBuilder sink) {
        Thread thread =
                new Thread(
                        () -> {
                            try (BufferedReader reader =
                                    new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                                String line;
                                while ((line = reader.readLine()) != null) {
                                    synchronized (sink) {
                                        sink.append(line).append('\n');
                                    }
                                }
                            } catch (IOException ignored) {
                                // stream closed with the process
                            }
                        });
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    public static Path currentJavaExecutable() {
        return Path.of(System.getProperty("java.home"), "bin", isWindows() ? "java.exe" : "java");
    }

    public static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }
}
