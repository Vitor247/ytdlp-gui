package com.ytdlpgui.service;

import com.ytdlpgui.settings.AppSettings;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

/** Resolves the yt-dlp.exe location: cached path -> known winget install path -> PATH. */
public class YtDlpLocatorService {

    // Default winget install location for the yt-dlp.yt-dlp package on Windows.
    private static final Path WINGET_DEFAULT_PATH = Paths.get(
            System.getenv("LOCALAPPDATA") == null ? "" : System.getenv("LOCALAPPDATA"),
            "Microsoft", "WinGet", "Packages",
            "yt-dlp.yt-dlp_Microsoft.Winget.Source_8wekyb3d8bbwe", "yt-dlp.exe");

    private final AppSettings settings;

    public YtDlpLocatorService(AppSettings settings) {
        this.settings = settings;
    }

    public Optional<Path> locate() {
        String cached = settings.getCachedYtDlpPath();
        if (cached != null && Files.isExecutable(Paths.get(cached))) {
            return Optional.of(Paths.get(cached));
        }

        if (Files.isExecutable(WINGET_DEFAULT_PATH)) {
            settings.setCachedYtDlpPath(WINGET_DEFAULT_PATH.toString());
            return Optional.of(WINGET_DEFAULT_PATH);
        }

        Optional<Path> onPath = findOnPath("yt-dlp.exe");
        if (onPath.isEmpty()) {
            onPath = findOnPath("yt-dlp");
        }
        onPath.ifPresent(path -> settings.setCachedYtDlpPath(path.toString()));
        return onPath;
    }

    private Optional<Path> findOnPath(String exeName) {
        String pathEnv = System.getenv("PATH");
        if (pathEnv == null) {
            return Optional.empty();
        }
        for (String dir : pathEnv.split(java.io.File.pathSeparator)) {
            Path candidate = Paths.get(dir, exeName);
            if (Files.isExecutable(candidate)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }
}
