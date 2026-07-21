package com.nureal.ide.core.ai.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;

/**
 * {@code ensureRunning} so e testado no caminho "ja saudavel -> nao sobe
 * processo nenhum" (via um servidor HTTP fake, mesmo padrao de
 * {@code OllamaProviderTest}) — o spawn de um Ollama de verdade e validacao
 * manual (documentado no plano), nao simulavel de forma confiavel/portavel
 * num teste automatizado.
 */
class OllamaRuntimeManagerTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    private String startHealthyServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/tags", exchange -> {
            byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @Test
    void naoSobeProcessoSeJaHouverOllamaSaudavel() throws IOException {
        String baseUrl = startHealthyServer();
        OllamaRuntimeManager manager = new OllamaRuntimeManager();

        List<String> statusMessages = new ArrayList<>();
        manager.ensureRunning(baseUrl, statusMessages::add);

        assertEquals(1, statusMessages.size());
        assertTrue(statusMessages.get(0).toLowerCase().contains("já está rodando"));
        // stopIfOwned nao deve reclamar/lancar mesmo sem nenhum processo ter sido iniciado
        manager.stopIfOwned();
    }

    @Test
    void resolveCommandUsaBinarioEmbutidoQuandoDisponivel() {
        Path bundled = Paths.get("C:", "app", "ollama-bin", "ollama.exe");
        assertEquals(List.of(bundled.toString(), "serve"), OllamaRuntimeManager.resolveCommand(bundled));
    }

    @Test
    void resolveCommandCaiParaPathQuandoNaoEmbutido() {
        assertEquals(List.of("ollama", "serve"), OllamaRuntimeManager.resolveCommand(null));
    }
}
