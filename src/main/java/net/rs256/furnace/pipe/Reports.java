package net.rs256.furnace.pipe;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.rs256.furnace.FurnaceConfig;
import net.rs256.furnace.InterruptHandler;
import net.rs256.furnace.meta.VersionDetail;
import net.rs256.furnace.util.MoreFiles;
import net.rs256.furnace.util.Procs;

/**
 * SPEC 4.1 step 6: runs the vanilla data generator (--reports) with a Java
 * runtime matching the version's requirement (SPEC 6) and copies
 * generated/reports into the output tree.
 */
public final class Reports {

    private Reports() {}

    public static void run(
            FurnaceConfig config,
            VersionDetail detail,
            Path serverJarOuter,
            boolean bundler,
            Path workDir,
            Path outReports)
            throws IOException, InterruptedException {
        Path javaExe = selectJava(config, detail);
        Path datagenDir = Files.createDirectories(workDir.resolve("datagen"));
        Path generated = datagenDir.resolve("generated");

        List<String> command = new ArrayList<>();
        command.add(javaExe.toString());
        if (bundler) {
            command.add("-DbundlerMainClass=net.minecraft.data.Main");
            command.add("-jar");
            command.add(serverJarOuter.toString());
        } else {
            command.add("-cp");
            command.add(serverJarOuter.toString());
            command.add("net.minecraft.data.Main");
        }
        command.add("--reports");
        command.add("--output");
        command.add(generated.toString());

        Path logFile = workDir.resolve("datagen.log");
        int exit = Procs.runKillable(datagenDir, command, logFile);
        InterruptHandler.checkAbort();
        if (exit != 0) {
            throw new IOException("data generator exited with code " + exit + "; see " + logFile);
        }
        Path reports = generated.resolve("reports");
        if (!Files.isDirectory(reports)) {
            throw new IOException("data generator produced no reports directory: " + reports);
        }
        Files.createDirectories(outReports);
        MoreFiles.copyTree(reports, outReports);
    }

    static Path selectJava(FurnaceConfig config, VersionDetail detail) {
        int required = detail.requiredJavaMajor();
        Path configured = config.jres().get(required);
        if (configured != null) {
            return configured;
        }
        int current = Runtime.version().feature();
        if (current >= required) {
            if (current != required) {
                System.out.println(
                        "[furnace]   reports: version wants Java "
                                + required
                                + ", using current Java "
                                + current
                                + " (configure config/jres.properties for an exact match)");
            }
            return Procs.currentJavaExecutable();
        }
        throw new IllegalStateException(
                "version "
                        + detail.id()
                        + " requires Java "
                        + required
                        + " for the data generator; add an entry to config/jres.properties or set reports.enabled=false");
    }
}
