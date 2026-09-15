package com.ytdlpgui.settings;

import java.util.prefs.Preferences;

/** Thin wrapper over java.util.prefs.Preferences for the few values the app needs to remember. */
public class AppSettings {

    private static final String KEY_OUTPUT_FOLDER = "outputFolder";
    private static final String KEY_FFMPEG_BIN_DIR = "ffmpegBinDir";
    private static final String KEY_YTDLP_PATH = "ytdlpPath";

    private final Preferences prefs = Preferences.userNodeForPackage(AppSettings.class);

    public String getOutputFolder() {
        return prefs.get(KEY_OUTPUT_FOLDER, null);
    }

    public void setOutputFolder(String path) {
        prefs.put(KEY_OUTPUT_FOLDER, path);
    }

    public String getCachedFfmpegBinDir() {
        return prefs.get(KEY_FFMPEG_BIN_DIR, null);
    }

    public void setCachedFfmpegBinDir(String path) {
        prefs.put(KEY_FFMPEG_BIN_DIR, path);
    }

    public String getCachedYtDlpPath() {
        return prefs.get(KEY_YTDLP_PATH, null);
    }

    public void setCachedYtDlpPath(String path) {
        prefs.put(KEY_YTDLP_PATH, path);
    }
}
