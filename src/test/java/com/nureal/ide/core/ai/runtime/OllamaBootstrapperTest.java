package com.nureal.ide.core.ai.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.nureal.ide.core.ai.config.AiPreferences;
import com.sun.net.httpserver.HttpServer;

/** Testes contra um Ollama fake local (mesmo padrao de {@code OllamaProviderTest}). */
class OllamaBootstrapperTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    private String startServer(Map<String, byte[]> jsonByPath) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        jsonByPath.forEach((path, body) -> server.createContext(path, exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        }));
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @Test
    void naoBaixaModeloQuandoJaConfigurado() throws IOException {
        String baseUrl = startServer(Map.of("/api/tags", "{}".getBytes(StandardCharsets.UTF_8)));
        AiPreferences.State prefs = new AiPreferences.State(baseUrl, "llama3.1", 0.2, 5, true, true);
        OllamaBootstrapper bootstrapper = new OllamaBootstrapper(new OllamaRuntimeManager());

        List<String> statuses = new ArrayList<>();
        String pulled = bootstrapper.ensureReady(prefs, statuses::add);

        assertNull(pulled, "modelo ja configurado - nao deveria disparar pull nenhum");
    }

    @Test
    void naoBaixaModeloQuandoJaHaAlgumInstalado() throws IOException {
        String tagsBody = "{\"models\":[{\"name\":\"qwen2.5:7b\"}]}";
        String baseUrl = startServer(Map.of("/api/tags", tagsBody.getBytes(StandardCharsets.UTF_8)));
        AiPreferences.State prefs = new AiPreferences.State(baseUrl, "", 0.2, 5, true, true);
        OllamaBootstrapper bootstrapper = new OllamaBootstrapper(new OllamaRuntimeManager());

        String pulled = bootstrapper.ensureReady(prefs, s -> { });

        assertNull(pulled, "ja ha um modelo instalado - nao deveria disparar pull automatico");
    }

    @Test
    void baixaModeloPadraoQuandoNaoHaNenhumInstaladoNemConfigurado() throws IOException {
        AtomicInteger tagsCalls = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/tags", exchange -> {
            byte[] body = "{\"models\":[]}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
            tagsCalls.incrementAndGet();
        });
        server.createContext("/api/pull", exchange -> {
            byte[] body = "{\"status\":\"success\"}\n".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/x-ndjson");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server.start();
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();

        AiPreferences.State prefs = new AiPreferences.State(baseUrl, "", 0.2, 5, true, true);
        OllamaBootstrapper bootstrapper = new OllamaBootstrapper(new OllamaRuntimeManager());

        List<String> statuses = new ArrayList<>();
        String pulled = bootstrapper.ensureReady(prefs, statuses::add);

        assertEquals(AiPreferences.DEFAULT_MODEL, pulled);
        assertTrue(statuses.stream().anyMatch(s -> s.contains(AiPreferences.DEFAULT_MODEL)));
    }
}
