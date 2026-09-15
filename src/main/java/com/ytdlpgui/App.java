package com.ytdlpgui;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class App extends Application {

    @Override
    public void start(Stage stage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main-view.fxml"));
        Parent root = loader.load();

        stage.setTitle("yt-dlp GUI");
        stage.setScene(new Scene(root, 640, 420));
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
