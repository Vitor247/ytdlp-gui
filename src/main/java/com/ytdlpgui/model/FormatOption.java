package com.ytdlpgui.model;

/**
 * One selectable download option shown in the quality ComboBox.
 * For real yt-dlp formats, formatId is the exact format_id returned by "yt-dlp -j".
 * The synthetic audio-only entry uses AUDIO_ONLY_FORMAT_ID and is handled specially by DownloadTask.
 */
public class FormatOption {

    public static final String AUDIO_ONLY_FORMAT_ID = "__audio_only_mp3__";

    private final String formatId;
    private final String label;
    private final Integer height;
    private final String ext;
    private final boolean audioOnly;

    public FormatOption(String formatId, String label, Integer height, String ext, boolean audioOnly) {
        this.formatId = formatId;
        this.label = label;
        this.height = height;
        this.ext = ext;
        this.audioOnly = audioOnly;
    }

    public static FormatOption audioOnlyMp3() {
        return new FormatOption(AUDIO_ONLY_FORMAT_ID, "Somente áudio (MP3)", null, "mp3", true);
    }

    public String getFormatId() {
        return formatId;
    }

    public Integer getHeight() {
        return height;
    }

    public String getExt() {
        return ext;
    }

    public boolean isAudioOnly() {
        return audioOnly;
    }

    @Override
    public String toString() {
        return label;
    }
}
