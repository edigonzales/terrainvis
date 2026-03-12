package ch.so.agi.terrainvis.cli;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import ch.so.agi.terrainvis.testsupport.CapturedCommandOutput;
import picocli.CommandLine;

class MainCommandTest {
    @Test
    void rootHelpListsCommandFamilies() {
        CapturedCommandOutput output = new CapturedCommandOutput();
        CommandLine commandLine = new CommandLine(new MainCommand());
        commandLine.setOut(output.stdoutWriter());
        commandLine.setErr(output.stderrWriter());

        int exitCode = commandLine.execute("--help");

        assertThat(exitCode).isZero();
        assertThat(output.stdout()).contains("occlusion").contains("rvt").contains("render");
    }

    @Test
    void occlusionExactHelpWorksWithoutRequiredArguments() {
        CapturedCommandOutput output = new CapturedCommandOutput();
        CommandLine commandLine = new CommandLine(new MainCommand());
        commandLine.setOut(output.stdoutWriter());
        commandLine.setErr(output.stderrWriter());

        int exitCode = commandLine.execute("occlusion", "exact", "--help");

        assertThat(exitCode).isZero();
        assertThat(output.stdout()).contains("--input").contains("--output-mode");
    }

    @Test
    void rvtVatHelpWorksWithoutRequiredArguments() {
        CapturedCommandOutput output = new CapturedCommandOutput();
        CommandLine commandLine = new CommandLine(new MainCommand());
        commandLine.setOut(output.stdoutWriter());
        commandLine.setErr(output.stderrWriter());

        int exitCode = commandLine.execute("rvt", "vat", "--help");

        assertThat(exitCode).isZero();
        assertThat(output.stdout()).contains("--terrain").contains("--general-opacity");
    }
}
