# ytdlp-gui

Interface gráfica em Java + JavaFX para o [yt-dlp](https://github.com/yt-dlp/yt-dlp), feita para Windows.

## Requisitos

- Windows 10/11
- [yt-dlp](https://github.com/yt-dlp/yt-dlp) instalado e no PATH (ex: `winget install yt-dlp.yt-dlp`)
- Java 21+ e Maven apenas para build/desenvolvimento (o `.exe` empacotado já leva o runtime Java embutido)
- `ffmpeg` **não** precisa ser instalado manualmente, o app baixa sozinho se não encontrar

## Rodando em modo desenvolvimento

```bash
mvn javafx:run
```

O resultado fica em `target/dist/YtDlpGui/YtDlpGui.exe`.

## Gerando um instalador (.msi)

Requer o [WiX Toolset v3.x](https://wixtoolset.org/) instalado (`winget install WiXToolset.WiXToolset`):

```bash
mvn package -P installer
```

## Status

Primeira versão funcional (v1.0.0): baixa um vídeo por vez, na qualidade escolhida,
com o ffmpeg provisionado automaticamente quando necessário. Ainda não tem suporte a
playlists, testes automatizados nem instalador assinado digitalmente — o projeto deve
continuar evoluindo a partir daqui.
