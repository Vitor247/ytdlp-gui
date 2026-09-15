package com.ytdlpgui.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ytdlpgui.model.FormatOption;
import com.ytdlpgui.util.ProcessRunner;
import javafx.concurrent.Task;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Runs a yt-dlp download in the background, reporting progress parsed from the JSON payload
 * emitted by --progress-template. Cancelling the Task kills the yt-dlp process tree (including
 * any ffmpeg child process it spawned for merging/extracting).
 */
public class DownloadTask extends Task<Void> {

    private final Path ytDlpExe;
    private final Path ffmpegBinDir; // nullable: null means ffmpeg is already resolvable on PATH
    private final String url;
    private final FormatOption format;
    private final Path outputDir;

    private final ObjectMapper mapper = new ObjectMapper();
    private final AtomicReference<ProcessRunner.RunningProcess> runningProcess = new AtomicReference<>();

    public DownloadTask(Path ytDlpExe, Path ffmpegBinDir, String url, FormatOption format, Path outputDir) {
        this.ytDlpExe = ytDlpExe;
        this.ffmpegBinDir = ffmpegBinDir;
        this.url = url;
        this.format = format;
        this.outputDir = outputDir;
    }

    @Override
    protected Void call() throws Exception {
        List<String> command = buildCommand();
        updateMessage("Iniciando yt-dlp...");

        int exitCode = ProcessRunner.run(
                command,
                null,
                this::handleLine,
                runningProcess::set);

        if (isCancelled()) {
            updateMessage("Cancelado.");
            return null;
        }
        if (exitCode != 0) {
            throw new IllegalStateException("yt-dlp terminou com código " + exitCode);
        }
        updateProgress(1, 1);
        updateMessage("Concluído.");
        return null;
    }

    @Override
    protected void cancelled() {
        ProcessRunner.RunningProcess proc = runningProcess.get();
        if (proc != null) {
            proc.killTree();
        }
    }

    private List<String> buildCommand() {
        List<String> command = new ArrayList<>();
        command.add(ytDlpExe.toString());

        if (format.isAudioOnly()) {
            command.add("-f");
            command.add("bestaudio");
            command.add("-x");
            command.add("--audio-format");
            command.add("mp3");
            command.add("--audio-quality");
            command.add("0");
        } else {
            command.add("-f");
            command.add(format.getFormatId() + "+bestaudio/best");
            command.add("--merge-output-format");
            command.add("mp4");
        }

        if (ffmpegBinDir != null) {
            command.add("--ffmpeg-location");
            command.add(ffmpegBinDir.toString());
        }

        command.add("--no-playlist");
        command.add("--windows-filenames");
        command.add("--newline");
        command.add("--progress-template");
        command.add("download:%(progress)j");
        command.add("-o");
        command.add(outputDir.resolve("%(title)s.%(ext)s").toString());
        command.add(url);
        return command;
    }

    private void handleLine(String line) {
        String prefix = "download:";
        if (!line.startsWith(prefix)) {
            return;
        }
        try {
            JsonNode progress = mapper.readTree(line.substring(prefix.length()));
            double downloaded = progress.path("downloaded_bytes").asDouble(-1);
            double total = progress.hasNonNull("total_bytes") ? progress.get("total_bytes").asDouble()
                    : progress.path("total_bytes_estimate").asDouble(-1);
            if (downloaded >= 0 && total > 0) {
                updateProgress(downloaded, total);
            }
            String status = progress.path("status").asText("");
            String speed = progress.path("_speed_str").asText("");
            String eta = progress.path("_eta_str").asText("");
            if ("finished".equals(status)) {
                updateMessage("Processando (ffmpeg)...");
            } else if (total > 0 && downloaded >= 0) {
                int pct = (int) Math.round(downloaded / total * 100);
                updateMessage(pct + "%" + (speed.isBlank() ? "" : " - " + speed) + (eta.isBlank() ? "" : " ETA " + eta));
            }
        } catch (Exception parseError) {
            // Not every line matches the expected JSON shape (e.g. warnings); ignore and keep reading.
        }
    }
}
