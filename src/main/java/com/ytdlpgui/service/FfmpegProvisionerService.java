package com.ytdlpgui.service;

import com.ytdlpgui.settings.AppSettings;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.function.LongConsumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Resolves ffmpeg/ffprobe: cached path -> app-local folder -> system PATH -> auto-download
 * a static Windows build. Only ffmpeg.exe/ffprobe.exe are extracted from the release zip.
 */
public class FfmpegProvisionerService {

    private static final String DOWNLOAD_URL =
            "https://github.com/BtbN/FFmpeg-Builds/releases/download/latest/ffmpeg-master-latest-win64-gpl.zip";

    private final AppSettings settings;
    private final Path appLocalBinDir;
    private final Path downloadTmpDir;

    public FfmpegProvisionerService(AppSettings settings) {
        this.settings = settings;
        Path localAppData = Paths.get(System.getenv("LOCALAPPDATA") == null ? "." : System.getenv("LOCALAPPDATA"));
        this.appLocalBinDir = localAppData.resolve("ytdlp-gui").resolve("ffmpeg").resolve("bin");
        this.downloadTmpDir = localAppData.resolve("ytdlp-gui").resolve("downloads");
    }

    /** Outcome of a detection pass: EXPLICIT_DIR carries a --ffmpeg-location value, ON_PATH needs none, NOT_FOUND needs a download. */
    public enum Status { EXPLICIT_DIR, ON_PATH, NOT_FOUND }

    public record Detection(Status status, Path binDir) {
    }

    /** Non-blocking-safe detection only (cache -> app-local folder -> PATH). Does not download. */
    public Detection detectExisting() {
        String cached = settings.getCachedFfmpegBinDir();
        if (cached != null && isValidBinDir(Paths.get(cached))) {
            return new Detection(Status.EXPLICIT_DIR, Paths.get(cached));
        }

        if (isValidBinDir(appLocalBinDir)) {
            settings.setCachedFfmpegBinDir(appLocalBinDir.toString());
            return new Detection(Status.EXPLICIT_DIR, appLocalBinDir);
        }

        if (isOnPath()) {
            return new Detection(Status.ON_PATH, null);
        }

        return new Detection(Status.NOT_FOUND, null);
    }

    public boolean isOnPath() {
        try {
            Process p = new ProcessBuilder("ffmpeg", "-version").redirectErrorStream(true).start();
            boolean exitedOk = p.waitFor(5, java.util.concurrent.TimeUnit.SECONDS) && p.exitValue() == 0;
            return exitedOk;
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }

    private boolean isValidBinDir(Path binDir) {
        return Files.isExecutable(binDir.resolve("ffmpeg.exe")) && Files.isExecutable(binDir.resolve("ffprobe.exe"));
    }

    /**
     * Downloads and extracts ffmpeg to the app-local folder. Call only from a background thread.
     * onProgress reports bytes downloaded so far (total size may be unknown -> report -1 for total).
     */
    public Path downloadAndInstall(LongConsumer onBytesDownloaded) throws IOException, InterruptedException {
        Files.createDirectories(downloadTmpDir);
        Files.createDirectories(appLocalBinDir);
        Path zipPath = downloadTmpDir.resolve("ffmpeg.zip");

        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(DOWNLOAD_URL))
                .GET()
                .build();
        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() / 100 != 2) {
            throw new IOException("Falha ao baixar ffmpeg, HTTP " + response.statusCode());
        }

        long total = 0;
        try (InputStream in = response.body();
             var out = Files.newOutputStream(zipPath)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
                total += read;
                if (onBytesDownloaded != null) {
                    onBytesDownloaded.accept(total);
                }
            }
        }

        extractBinaries(zipPath, appLocalBinDir);
        Files.deleteIfExists(zipPath);

        settings.setCachedFfmpegBinDir(appLocalBinDir.toString());
        return appLocalBinDir;
    }

    private void extractBinaries(Path zipPath, Path destBinDir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipPath))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName().replace('\\', '/');
                if (!entry.isDirectory() && (name.endsWith("/bin/ffmpeg.exe") || name.endsWith("/bin/ffprobe.exe"))) {
                    String fileName = name.substring(name.lastIndexOf('/') + 1);
                    Path target = destBinDir.resolve(fileName);
                    Files.copy(zis, target, StandardCopyOption.REPLACE_EXISTING);
                }
                zis.closeEntry();
            }
        }
        if (!isValidBinDir(destBinDir)) {
            throw new IOException("ffmpeg.exe/ffprobe.exe não encontrados dentro do zip baixado");
        }
    }
}
