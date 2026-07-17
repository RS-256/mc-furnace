package net.rs256.furnace;

import java.nio.file.Path;
import net.rs256.furnace.cli.AddCommand;
import net.rs256.furnace.cli.BackfillCommand;
import net.rs256.furnace.cli.RegenCommand;
import net.rs256.furnace.cli.StatusCommand;
import net.rs256.furnace.cli.UpdateCommand;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/**
 * Standalone CLI entry point (SPEC 4.2.1 / 4.4). Built with the Gradle
 * application plugin; run via build/install/furnace/bin/furnace.
 */
@Command(
        name = "furnace",
        mixinStandardHelpOptions = true,
        versionProvider = Furnace.BuildVersionProvider.class,
        description = "Builds the mc-terra repository: one Minecraft version per commit.",
        subcommands = {
            UpdateCommand.class,
            AddCommand.class,
            RegenCommand.class,
            BackfillCommand.class,
            StatusCommand.class
        })
public final class Furnace implements Runnable {

    @Option(
            names = {"--config-dir"},
            description = "Directory holding the pipeline configuration files (default: config)")
    public Path configDir = Path.of("config");

    @Spec CommandSpec spec;

    @Override
    public void run() {
        spec.commandLine().usage(System.out);
    }

    public AppContext createContext() {
        return AppContext.load(configDir);
    }

    public static void main(String[] args) {
        InterruptHandler.install();
        int code = new CommandLine(new Furnace()).execute(args);
        System.exit(code);
    }

    public static final class BuildVersionProvider implements CommandLine.IVersionProvider {
        @Override
        public String[] getVersion() {
            BuildInfo info = BuildInfo.load();
            return new String[] {"furnace " + info.furnaceVersion() + " (" + info.pipelineCommit() + ")"};
        }
    }
}
