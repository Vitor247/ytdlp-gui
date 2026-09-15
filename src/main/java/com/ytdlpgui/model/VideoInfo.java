package com.ytdlpgui.model;

import java.util.List;

public class VideoInfo {

    private final String title;
    private final List<FormatOption> formats;

    public VideoInfo(String title, List<FormatOption> formats) {
        this.title = title;
        this.formats = formats;
    }

    public String getTitle() {
        return title;
    }

    public List<FormatOption> getFormats() {
        return formats;
    }
}
