package com.ytdlpgui.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

/**
 * Shared helper for running an external process and streaming its stdout back line by line.
 * Used by MetadataService (one-shot "yt-dlp -j") and DownloadTask (long-running download).
 */
public class ProcessRunner {

    private ProcessRunner() {
    }

    public static class RunningProcess {
        public final Process process;

        public RunningProcess(Process process) {
            this.process = process;
        }

        /** Kills the process and any child processes it spawned (e.g. ffmpeg spawned by yt-dlp). */
        public void killTree() {
            process.descendants().forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
        }
    }

    /**
     * Starts the process with stdout redirected line-by-line to onLine, stderr merged into stdout.
     * Blocks the calling thread until the process exits; call from a background thread/Task only.
     */
    public static int run(List<String> command, Path workingDir, Consumer<String> onLine, Consumer<RunningProcess> onStart)
            throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);
        if (workingDir != null) {
            builder.directory(workingDir.toFile());
        }
        Process process = builder.start();
        if (onStart != null) {
            onStart.accept(new RunningProcess(process));
        }
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (onLine != null) {
                    onLine.accept(line);
                }
            }
        }
        return process.waitFor();
    }
}
