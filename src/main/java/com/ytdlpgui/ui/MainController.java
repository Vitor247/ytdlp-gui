package com.ytdlpgui.ui;

import com.ytdlpgui.model.FormatOption;
import com.ytdlpgui.model.VideoInfo;
import com.ytdlpgui.service.DownloadTask;
import com.ytdlpgui.service.FfmpegProvisionerService;
import com.ytdlpgui.service.FfmpegProvisionerService.Detection;
import com.ytdlpgui.service.FfmpegProvisionerService.Status;
import com.ytdlpgui.service.MetadataService;
import com.ytdlpgui.service.YtDlpLocatorService;
import com.ytdlpgui.settings.AppSettings;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextField;
import javafx.stage.DirectoryChooser;
import javafx.stage.Window;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

public class MainController {

    @FXML private TextField urlField;
    @FXML private Button fetchButton;
    @FXML private Label titleLabel;
    @FXML private ComboBox<FormatOption> qualityCombo;
    @FXML private TextField outputFolderField;
    @FXML private Button browseButton;
    @FXML private Button downloadButton;
    @FXML private Button cancelButton;
    @FXML private ProgressBar progressBar;
    @FXML private Label statusLabel;

    private final AppSettings settings = new AppSettings();
    private final YtDlpLocatorService ytDlpLocator = new YtDlpLocatorService(settings);
    private final FfmpegProvisionerService ffmpegProvisioner = new FfmpegProvisionerService(settings);
    private final MetadataService metadataService = new MetadataService();

    private Path ytDlpPath;
    private Detection ffmpegDetection;
    private Task<?> currentTask;

    @FXML
    private void initialize() {
        String savedFolder = settings.getOutputFolder();
        outputFolderField.setText(savedFolder != null ? savedFolder : defaultDownloadsFolder());

        downloadButton.setDisable(true);
        statusLabel.setText("Verificando yt-dlp e ffmpeg...");
        runEnvironmentCheck();
    }

    private String defaultDownloadsFolder() {
        return Paths.get(System.getProperty("user.home"), "Downloads").toString();
    }

    private void runEnvironmentCheck() {
        Task<Void> check = new Task<>() {
            @Override
            protected Void call() {
                ytDlpPath = ytDlpLocator.locate().orElse(null);
                ffmpegDetection = ffmpegProvisioner.detectExisting();
                return null;
            }
        };
        check.setOnSucceeded(e -> {
            if (ytDlpPath == null) {
                statusLabel.setText("yt-dlp não encontrado. Instale com 'winget install yt-dlp.yt-dlp' e reabra o app.");
                return;
            }
            String ffmpegNote = switch (ffmpegDetection.status()) {
                case ON_PATH, EXPLICIT_DIR -> "";
                case NOT_FOUND -> " ffmpeg será baixado automaticamente no primeiro download.";
            };
            statusLabel.setText("Pronto." + ffmpegNote);
            downloadButton.setDisable(false);
        });
        check.setOnFailed(e -> statusLabel.setText("Erro ao verificar ambiente: " + check.getException()));
        new Thread(check, "env-check").start();
    }

    @FXML
    private void onFetchInfo() {
        String url = urlField.getText() == null ? "" : urlField.getText().trim();
        if (url.isEmpty()) {
            statusLabel.setText("Cole uma URL antes de buscar.");
            return;
        }
        if (ytDlpPath == null) {
            statusLabel.setText("yt-dlp não está disponível.");
            return;
        }

        fetchButton.setDisable(true);
        titleLabel.setText("");
        qualityCombo.getItems().clear();
        statusLabel.setText("Buscando informações do vídeo...");

        Task<VideoInfo> task = new Task<>() {
            @Override
            protected VideoInfo call() throws Exception {
                return metadataService.fetch(ytDlpPath, url);
            }
        };
        task.setOnSucceeded(e -> {
            VideoInfo info = task.getValue();
            titleLabel.setText(info.getTitle());
            qualityCombo.getItems().setAll(info.getFormats());
            if (!qualityCombo.getItems().isEmpty()) {
                qualityCombo.getSelectionModel().selectFirst();
            }
            statusLabel.setText("Informações carregadas.");
            fetchButton.setDisable(false);
        });
        task.setOnFailed(e -> {
            statusLabel.setText("Falha ao buscar informações: " + task.getException().getMessage());
            fetchButton.setDisable(false);
        });
        new Thread(task, "fetch-metadata").start();
    }

    @FXML
    private void onBrowseFolder() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Escolha a pasta de destino");
        File current = new File(outputFolderField.getText());
        if (current.isDirectory()) {
            chooser.setInitialDirectory(current);
        }
        Window owner = browseButton.getScene().getWindow();
        File chosen = chooser.showDialog(owner);
        if (chosen != null) {
            outputFolderField.setText(chosen.getAbsolutePath());
            settings.setOutputFolder(chosen.getAbsolutePath());
        }
    }

    @FXML
    private void onDownload() {
        String url = urlField.getText() == null ? "" : urlField.getText().trim();
        FormatOption format = qualityCombo.getValue();
        String outputFolder = outputFolderField.getText();

        if (url.isEmpty() || format == null || outputFolder == null || outputFolder.isBlank()) {
            statusLabel.setText("Preencha URL, qualidade e pasta de destino antes de baixar.");
            return;
        }
        Path outputDir = Paths.get(outputFolder);
        if (!java.nio.file.Files.isDirectory(outputDir)) {
            statusLabel.setText("Pasta de destino inválida.");
            return;
        }

        downloadButton.setDisable(true);
        cancelButton.setDisable(false);
        progressBar.progressProperty().unbind();
        progressBar.setProgress(0);

        if (ffmpegDetection.status() == Status.NOT_FOUND) {
            statusLabel.setText("Baixando ffmpeg (configuração única, ~90MB)...");
            Task<Path> provisionTask = new Task<>() {
                @Override
                protected Path call() throws Exception {
                    return ffmpegProvisioner.downloadAndInstall(bytes ->
                            Platform.runLater(() -> statusLabel.setText(
                                    "Baixando ffmpeg... " + (bytes / (1024 * 1024)) + " MB")));
                }
            };
            provisionTask.setOnSucceeded(e -> {
                ffmpegDetection = new Detection(Status.EXPLICIT_DIR, provisionTask.getValue());
                startDownload(url, format, outputDir);
            });
            provisionTask.setOnFailed(e -> {
                statusLabel.setText("Falha ao baixar ffmpeg: " + provisionTask.getException().getMessage());
                resetDownloadButtons();
            });
            currentTask = provisionTask;
            new Thread(provisionTask, "ffmpeg-provision").start();
        } else {
            startDownload(url, format, outputDir);
        }
    }

    private void startDownload(String url, FormatOption format, Path outputDir) {
        Path ffmpegBinDir = ffmpegDetection.status() == Status.EXPLICIT_DIR ? ffmpegDetection.binDir() : null;
        statusLabel.setText("Baixando...");

        DownloadTask task = new DownloadTask(ytDlpPath, ffmpegBinDir, url, format, outputDir);
        progressBar.progressProperty().bind(task.progressProperty());
        statusLabel.textProperty().bind(task.messageProperty());

        task.setOnSucceeded(e -> {
            unbindAndReset();
            statusLabel.setText("Download concluído.");
        });
        task.setOnFailed(e -> {
            unbindAndReset();
            Throwable ex = task.getException();
            statusLabel.setText("Erro: " + (ex != null ? ex.getMessage() : "desconhecido"));
        });
        task.setOnCancelled(e -> {
            unbindAndReset();
            statusLabel.setText("Cancelado.");
        });

        currentTask = task;
        new Thread(task, "yt-dlp-download").start();
    }

    private void unbindAndReset() {
        progressBar.progressProperty().unbind();
        statusLabel.textProperty().unbind();
        resetDownloadButtons();
    }

    private void resetDownloadButtons() {
        downloadButton.setDisable(false);
        cancelButton.setDisable(true);
    }

    @FXML
    private void onCancel() {
        if (currentTask != null) {
            currentTask.cancel();
        }
    }
}
