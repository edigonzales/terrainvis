package ch.so.agi.terrainvis.testsupport;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

public final class CapturedCommandOutput {
    private final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
    private final ByteArrayOutputStream stderr = new ByteArrayOutputStream();
    private final PrintWriter outWriter = new PrintWriter(stdout, true, StandardCharsets.UTF_8);
    private final PrintWriter errWriter = new PrintWriter(stderr, true, StandardCharsets.UTF_8);

    public PrintWriter stdoutWriter() {
        return outWriter;
    }

    public PrintWriter stderrWriter() {
        return errWriter;
    }

    public String stdout() {
        outWriter.flush();
        return stdout.toString(StandardCharsets.UTF_8);
    }

    public String stderr() {
        errWriter.flush();
        return stderr.toString(StandardCharsets.UTF_8);
    }
}
