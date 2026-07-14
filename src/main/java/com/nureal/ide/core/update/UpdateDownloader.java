package com.nureal.ide.core.update;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.function.LongConsumer;

/**
 * Baixa um {@link GithubRelease.Asset} (o instalador .exe, tipicamente) para
 * um arquivo local, reportando progresso em bytes conforme baixa — usado por
 * {@code UpdateInstallDialog} para alimentar a barra de progresso (a mesma
 * ideia de {@code MySqlDumpRunner}, mas para download HTTP em vez de um
 * processo externo).
 *
 * Chamada BLOQUEANTE de proposito (le o stream inteiro antes de retornar) —
 * quem usa esta classe roda numa thread de fundo (ver {@code SwingWorker#doInBackground}
 * em {@code UpdateInstallDialog}), nunca na EDT.
 */
public final class UpdateDownloader {

    private static final Duration TIMEOUT = Duration.ofSeconds(15);
    /** 64 KB — grande o bastante pra nao martelar o callback de progresso a cada byte, pequeno o bastante pra a barra andar suave. */
    private static final int BUFFER_SIZE = 64 * 1024;

    private UpdateDownloader() {
    }

    /**
     * Baixa {@code url} para {@code dest} (sobrescrevendo se ja existir),
     * chamando {@code onBytesDownloaded} a cada bloco lido com o total
     * acumulado ate agora (nunca o percentual — quem chama sabe o tamanho
     * total esperado, ver {@link GithubRelease.Asset#sizeBytes()}, e calcula
     * o percentual sozinho). Lanca {@link IOException} em qualquer falha de
     * rede/disco, sem deixar um arquivo parcial para tras (baixa para um
     * arquivo temporario e so move para {@code dest} no final, com sucesso).
     */
    public static void download(String url, Path dest, LongConsumer onBytesDownloaded) throws IOException {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "nureal-database-ide-updater")
                .timeout(TIMEOUT)
                .GET()
                .build();
        HttpResponse<InputStream> response;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("Download interrompido.", ex);
        }
        if (response.statusCode() != 200) {
            throw new IOException("Servidor respondeu " + response.statusCode() + " ao baixar o instalador.");
        }

        Path parent = dest.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path tempFile = Files.createTempFile(parent, "nureal-update-", ".part");
        long total = 0;
        try (InputStream in = response.body();
                OutputStream out = Files.newOutputStream(tempFile)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
                total += read;
                onBytesDownloaded.accept(total);
            }
        } catch (IOException ex) {
            Files.deleteIfExists(tempFile);
            throw ex;
        }
        Files.move(tempFile, dest, StandardCopyOption.REPLACE_EXISTING);
    }
}
