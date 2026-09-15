package com.ytdlpgui.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ytdlpgui.model.FormatOption;
import com.ytdlpgui.model.VideoInfo;
import com.ytdlpgui.util.ProcessRunner;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Runs "yt-dlp -j <url>" and parses the resulting JSON into a VideoInfo with selectable formats. */
public class MetadataService {

    private final ObjectMapper mapper = new ObjectMapper();

    public VideoInfo fetch(Path ytDlpExe, String url) throws IOException, InterruptedException {
        List<String> command = List.of(
                ytDlpExe.toString(), "-j", "--no-playlist", "--no-warnings", url);

        StringBuilder stdout = new StringBuilder();
        int exitCode = ProcessRunner.run(command, null, line -> stdout.append(line).append('\n'), null);
        if (exitCode != 0) {
            throw new IOException("yt-dlp terminou com código " + exitCode + ":\n" + stdout);
        }

        JsonNode root = mapper.readTree(stdout.toString());
        String title = root.path("title").asText("Sem título");

        // Key by height: yt-dlp typically lists several codec/container variants per resolution,
        // keep only the one with the largest known filesize (usually the best-quality variant).
        Map<Integer, FormatOption> byHeight = new LinkedHashMap<>();
        Map<Integer, Long> bestSizeByHeight = new LinkedHashMap<>();
        for (JsonNode format : root.path("formats")) {
            String formatId = format.path("format_id").asText(null);
            String vcodec = format.path("vcodec").asText("none");
            if (formatId == null || "none".equals(vcodec)) {
                // Skip audio-only/unknown entries here; the synthetic "audio only mp3" option below covers that case.
                continue;
            }
            Integer height = format.hasNonNull("height") ? format.get("height").asInt() : null;
            if (height == null) {
                continue;
            }
            long size = format.hasNonNull("filesize") ? format.get("filesize").asLong()
                    : format.hasNonNull("filesize_approx") ? format.get("filesize_approx").asLong() : 0L;

            if (size < bestSizeByHeight.getOrDefault(height, -1L)) {
                continue;
            }
            String ext = format.path("ext").asText("mp4");
            String label = height + "p (" + ext + ")";
            byHeight.put(height, new FormatOption(formatId, label, height, ext, false));
            bestSizeByHeight.put(height, size);
        }

        List<FormatOption> options = new ArrayList<>(byHeight.values());
        options.sort((a, b) -> Integer.compare(b.getHeight(), a.getHeight()));

        options.add(FormatOption.audioOnlyMp3());

        return new VideoInfo(title, options);
    }
}
