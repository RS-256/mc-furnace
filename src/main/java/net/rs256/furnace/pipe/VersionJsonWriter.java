package net.rs256.furnace.pipe;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.rs256.furnace.BuildInfo;
import net.rs256.furnace.meta.VersionDetail;

/** SPEC 3.4: writes the per-commit version.json with a stable key order. */
public final class VersionJsonWriter {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private VersionJsonWriter() {}

    public record Toolchain(String decompiler, String mappings, String merger, String pipelineCommit) {
        public static Toolchain current(
                String vineflowerVersion, String stitchVersion, BuildInfo build, String mappings) {
            return new Toolchain(
                    "vineflower-" + vineflowerVersion,
                    mappings,
                    "stitch@" + stitchVersion,
                    "mc-furnace@" + build.pipelineCommit());
        }
    }

    public static String render(
            VersionDetail detail, Integer worldVersion, Integer protocolVersion, Toolchain toolchain) {
        JsonObject root = new JsonObject();
        root.addProperty("id", detail.id());
        root.addProperty("type", detail.type());
        root.addProperty("releaseTime", detail.releaseTime());
        root.addProperty("javaVersion", detail.requiredJavaMajor());
        if (worldVersion != null) {
            root.addProperty("worldVersion", worldVersion);
        }
        if (protocolVersion != null) {
            root.addProperty("protocolVersion", protocolVersion);
        }
        JsonObject tc = new JsonObject();
        tc.addProperty("decompiler", toolchain.decompiler());
        tc.addProperty("mappings", toolchain.mappings());
        tc.addProperty("merger", toolchain.merger());
        tc.addProperty("pipelineCommit", toolchain.pipelineCommit());
        root.add("toolchain", tc);
        return GSON.toJson(root) + "\n";
    }

    public static void write(
            Path outFile,
            VersionDetail detail,
            Integer worldVersion,
            Integer protocolVersion,
            Toolchain toolchain)
            throws IOException {
        Files.writeString(
                outFile, render(detail, worldVersion, protocolVersion, toolchain), StandardCharsets.UTF_8);
    }
}
