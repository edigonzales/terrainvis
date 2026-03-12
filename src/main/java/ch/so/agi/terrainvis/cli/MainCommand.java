package ch.so.agi.terrainvis.cli;

import picocli.CommandLine.Command;

@Command(
        name = "terrainvis",
        mixinStandardHelpOptions = true,
        description = "terrainvis terrain visualizations with DSM occlusion and RVT-style products.",
        subcommands = {OcclusionCommands.class, RvtCommands.class, RenderCommands.class})
public final class MainCommand {
}
