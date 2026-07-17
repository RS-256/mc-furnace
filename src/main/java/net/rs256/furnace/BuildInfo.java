package net.rs256.furnace;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Properties;

/** Build-time metadata embedded by Gradle (pipeline commit SHA, SPEC 3.4). */
public record BuildInfo(String furnaceVersion, String pipelineCommit) {

    public static BuildInfo load() {
        Properties props = new Properties();
        try (InputStream in = BuildInfo.class.getResourceAsStream("/furnace-build.properties")) {
            if (in != null) {
                props.load(in);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new BuildInfo(
                props.getProperty("furnaceVersion", "dev"),
                props.getProperty("pipelineCommit", "unknown"));
    }
}
