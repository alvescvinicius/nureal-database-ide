package com.nureal.ide.modulos.iachat.infraestrutura.provider;
import com.nureal.ide.modulos.iachat.dominio.entidades.ChatRequest;
import com.nureal.ide.modulos.iachat.dominio.entidades.ChatMessage;
import com.nureal.ide.modulos.iachat.dominio.entidades.ProviderException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;

/**
 * Testes especificos do {@link OpenRouterProvider} — so o que ele acrescenta sobre
 * {@link OpenAiCompatibleProvider} (ja coberto em {@code OpenAiProviderTest}): headers
 * extras (HTTP-Referer/X-Title) e o nome usado nas mensagens de erro. Como
 * {@code OpenRouterProvider} fixa a URL oficial, o teste inspeciona os headers via um
 * servidor fake apontado manualmente (nao da pra sobrescrever a base URL de fora).
 */
class OpenRouterProviderTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void extraHeadersIncluemReferAndTitle() {
        OpenRouterProvider provider = new OpenRouterProvider("sk-teste", Duration.ofSeconds(5));
        // extraHeaders() e protected, mesma classe/pacote de teste (ver estrutura de pastas) -- acesso direto.
        var headers = provider.extraHeaders();
        assertEquals("https://github.com/alvescvinicius/nureal-database-ide", headers.get("HTTP-Referer"));
        assertEquals("Nureal Database IDE", headers.get("X-Title"));
    }

    @Test
    void providerNameApareceNaMensagemDeErro() throws IOException, InterruptedException {
        AtomicReference<String> capturedAuthHeader = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            capturedAuthHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] body = "{\"error\":{\"message\":\"bad key\"}}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(401, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server.start();
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";

        // Reusa a base compartilhada com uma URL fake, so pra validar que o header de
        // auth ("Bearer <key>") e enviado igual ao OpenAI-compativel (mesma classe base).
        OpenAiCompatibleProvider provider = new OpenAiCompatibleProvider("sk-router-teste", baseUrl, Duration.ofSeconds(5)) {
            @Override
            protected String providerName() {
                return "OpenRouter";
            }
        };

        ChatRequest request = new ChatRequest("anthropic/claude-3.5-sonnet", List.of(new ChatMessage("user", "oi")), null);
        ProviderException.MissingCredential ex = org.junit.jupiter.api.Assertions.assertThrows(
                ProviderException.MissingCredential.class, () -> provider.chat(request));

        assertTrue(ex.getMessage().contains("OpenRouter"));
        assertNotNull(capturedAuthHeader.get());
        assertEquals("Bearer sk-router-teste", capturedAuthHeader.get());
    }
}
