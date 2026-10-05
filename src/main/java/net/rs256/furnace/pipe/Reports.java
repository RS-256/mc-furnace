package net.rs256.furnace.pipe;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarFile;
import net.rs256.furnace.FurnaceConfig;
import net.rs256.furnace.InterruptHandler;
import net.rs256.furnace.meta.VersionDetail;
import net.rs256.furnace.util.MoreFiles;
import net.rs256.furnace.util.Procs;

/**
 * Runs the vanilla data generator (--reports) with a Java runtime matching the
 * version's requirement and copies
 * generated/reports into the output tree.
 */
public final class Reports {

    private static final String LAUNCHER_CLASS = "net.rs256.furnace.launcher.DatagenLauncher";
    private static final String FAILURE_MARKER = "[furnace-datagen] uncaught exception in thread ";
    private static final int EXCERPT_MAX_LINES = 60;

    /** The data generator itself crashed (non-zero exit). Other failures stay plain IOExceptions. */
    public static final class DatagenFailedException extends IOException {

        private final String excerpt;

        DatagenFailedException(String message, String excerpt) {
            super(message);
            this.excerpt = excerpt;
        }

        /** The failure report from the datagen log, or null if none was found. */
        public String excerpt() {
            return excerpt;
        }
    }

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
        Path launcherDir = writeLauncher(workDir.resolve("datagen-launcher"));

        List<String> command = new ArrayList<>();
        command.add(javaExe.toString());
        if (bundler) {
            command.add("-DbundlerMainClass=net.minecraft.data.Main");
        }
        command.add("-cp");
        command.add(launcherDir + java.io.File.pathSeparator + serverJarOuter);
        command.add(LAUNCHER_CLASS);
        command.add(bundler ? manifestMainClass(serverJarOuter) : "net.minecraft.data.Main");
        command.add("--reports");
        command.add("--output");
        command.add(generated.toString());

        Path logFile = workDir.resolve("datagen.log");
        int exit = Procs.runKillable(datagenDir, command, logFile);
        InterruptHandler.checkAbort();
        if (exit != 0) {
            throw new DatagenFailedException(
                    "data generator exited with code " + exit + "; see " + logFile, failureExcerpt(logFile));
        }
        Path reports = generated.resolve("reports");
        if (!Files.isDirectory(reports)) {
            throw new IOException("data generator produced no reports directory: " + reports);
        }
        Files.createDirectories(outReports);
        MoreFiles.copyTree(reports, outReports);
    }

    private static Path writeLauncher(Path dir) throws IOException {
        String resource = LAUNCHER_CLASS.replace('.', '/') + ".class";
        Path target = dir.resolve(resource);
        Files.createDirectories(target.getParent());
        try (InputStream in = Reports.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IOException("datagen launcher missing from the classpath: " + resource);
            }
            Files.copy(in, target);
        }
        return dir;
    }

    private static String manifestMainClass(Path jar) throws IOException {
        try (JarFile file = new JarFile(jar.toFile())) {
            String mainClass = null;
            if (file.getManifest() != null) {
                mainClass = file.getManifest().getMainAttributes().getValue("Main-Class");
            }

            if (mainClass == null) {
                throw new IOException("server jar has no Main-Class: " + jar);
            }
            return mainClass;
        }
    }

    private static String failureExcerpt(Path logFile) throws IOException {
        List<String> lines = Files.readAllLines(logFile, StandardCharsets.UTF_8);
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).startsWith(FAILURE_MARKER)) {
                List<String> excerpt =
                        lines.subList(i, Math.min(lines.size(), i + EXCERPT_MAX_LINES));
                return String.join("\n", excerpt) + "\n";
            }
        }
        return null;
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
