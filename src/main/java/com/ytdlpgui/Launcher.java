package com.ytdlpgui;

/**
 * Separate entry point that does not itself extend javafx.application.Application.
 * When a fat jar's main class directly extends Application and is launched via classpath
 * (as jpackage's app-image does, with no module-path), the JVM refuses to start with
 * "JavaFX runtime components are missing" even though the classes are present on the
 * classpath. Routing through a plain main() avoids that check.
 */
public class Launcher {
    public static void main(String[] args) {
        App.main(args);
    }
}
