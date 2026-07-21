package com.nureal.ide.core.ai.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.nureal.ide.core.json.JsonParser;
import com.sun.net.httpserver.HttpServer;

/** Testes do {@link GeminiProvider}, via o construtor pacote-visivel com URL injetavel. */
class GeminiProviderTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    private String startServer(String path, byte[] body, int status) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(path, exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @Test
    void listModelsTiraOPrefixoModels() throws IOException {
        String json = "{\"models\":[{\"name\":\"models/gemini-2.0-flash\"},{\"name\":\"models/gemini-1.5-pro\"}]}";
        String baseUrl = startServer("/v1beta/models", json.getBytes(StandardCharsets.UTF_8), 200);
        GeminiProvider provider = new GeminiProvider("key-teste", baseUrl, Duration.ofSeconds(5));

        assertEquals(List.of("gemini-2.0-flash", "gemini-1.5-pro"), provider.listModels());
    }

    @Test
    void chatNaoStreamingParseiaTextoEUsage() throws IOException {
        String json = "{\"candidates\":[{\"content\":{\"role\":\"model\",\"parts\":[{\"text\":\"Ola!\"}]},"
                + "\"finishReason\":\"STOP\"}],\"usageMetadata\":{\"promptTokenCount\":9,\"candidatesTokenCount\":2}}";
        String baseUrl = startServer("/v1beta/models/gemini-2.0-flash:generateContent",
                json.getBytes(StandardCharsets.UTF_8), 200);
        GeminiProvider provider = new GeminiProvider("key-teste", baseUrl, Duration.ofSeconds(5));

        ChatRequest request = new ChatRequest("gemini-2.0-flash", List.of(new ChatMessage("user", "oi")), null);
        ChatResponse response = provider.chat(request);

        assertEquals("Ola!", response.message().content());
        assertEquals("stop", response.finishReason());
        assertEquals(9, response.usage().promptTokens());
        assertEquals(2, response.usage().completionTokens());
        assertFalse(response.hasToolCalls());
    }

    @Test
    void chatNaoStreamingParseiaFunctionCall() throws IOException {
        String json = "{\"candidates\":[{\"content\":{\"role\":\"model\",\"parts\":["
                + "{\"functionCall\":{\"name\":\"list_tables\",\"args\":{}}}]},\"finishReason\":\"STOP\"}]}";
        String baseUrl = startServer("/v1beta/models/gemini-2.0-flash:generateContent",
                json.getBytes(StandardCharsets.UTF_8), 200);
        GeminiProvider provider = new GeminiProvider("key-teste", baseUrl, Duration.ofSeconds(5));

        ChatRequest request = new ChatRequest("gemini-2.0-flash", List.of(new ChatMessage("user", "quais tabelas?")),
                null, List.of(new ToolSpec("list_tables", "lista tabelas", Map.of())));
        ChatResponse response = provider.chat(request);

        assertTrue(response.hasToolCalls());
        assertEquals("list_tables", response.toolCalls().get(0).name());
        assertEquals("list_tables", response.toolCalls().get(0).id(), "Gemini nao da id -- usamos o nome");
    }

    @Test
    void requestSeparaSystemInstructionEUsaRoleModel() throws IOException {
        ByteArrayOutputStream capturedBody = new ByteArrayOutputStream();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1beta/models/gemini-2.0-flash:generateContent", exchange -> {
            exchange.getRequestBody().transferTo(capturedBody);
            byte[] body = ("{\"candidates\":[{\"content\":{\"role\":\"model\",\"parts\":[{\"text\":\"ok\"}]},"
                    + "\"finishReason\":\"STOP\"}]}").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server.start();
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        GeminiProvider provider = new GeminiProvider("key-teste", baseUrl, Duration.ofSeconds(5));

        ChatRequest request = new ChatRequest("gemini-2.0-flash",
                List.of(new ChatMessage(ChatMessage.ROLE_SYSTEM, "voce e um assistente de banco de dados"),
                        new ChatMessage(ChatMessage.ROLE_USER, "oi"),
                        new ChatMessage(ChatMessage.ROLE_ASSISTANT, "ola, como posso ajudar?")),
                null);
        provider.chat(request);

        @SuppressWarnings("unchecked")
        var parsedBody = (Map<String, Object>) JsonParser.parse(capturedBody.toString(StandardCharsets.UTF_8));
        assertTrue(parsedBody.containsKey("systemInstruction"));
        @SuppressWarnings("unchecked")
        var contents = (List<Map<String, Object>>) parsedBody.get("contents");
        assertEquals(2, contents.size(), "a mensagem de system nao deveria virar um content");
        assertEquals("model", contents.get(1).get("role"), "role assistant deve virar \"model\" pro Gemini");
    }

    @Test
    void chatErro401ViraMissingCredential() throws IOException {
        String json = "{\"error\":{\"code\":401,\"message\":\"API key not valid\",\"status\":\"UNAUTHENTICATED\"}}";
        String baseUrl = startServer("/v1beta/models/gemini-2.0-flash:generateContent",
                json.getBytes(StandardCharsets.UTF_8), 401);
        GeminiProvider provider = new GeminiProvider("key-invalida", baseUrl, Duration.ofSeconds(5));

        ChatRequest request = new ChatRequest("gemini-2.0-flash", List.of(new ChatMessage("user", "oi")), null);
        ProviderException.MissingCredential ex = org.junit.jupiter.api.Assertions.assertThrows(
                ProviderException.MissingCredential.class, () -> provider.chat(request));
        assertTrue(ex.getMessage().contains("API key not valid"));
    }

    @Test
    void streamAcumulaTextDeltas() throws IOException, InterruptedException {
        String sse = String.join("\n",
                "data: {\"candidates\":[{\"content\":{\"role\":\"model\",\"parts\":[{\"text\":\"Ol\"}]}}]}",
                "data: {\"candidates\":[{\"content\":{\"role\":\"model\",\"parts\":[{\"text\":\"a!\"}]},"
                        + "\"finishReason\":\"STOP\"}],\"usageMetadata\":{\"promptTokenCount\":4,\"candidatesTokenCount\":2}}")
                + "\n";
        String baseUrl = startServer("/v1beta/models/gemini-2.0-flash:streamGenerateContent",
                sse.getBytes(StandardCharsets.UTF_8), 200);
        GeminiProvider provider = new GeminiProvider("key-teste", baseUrl, Duration.ofSeconds(5));

        List<AiEvent> events = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);
        ChatRequest request = new ChatRequest("gemini-2.0-flash", List.of(new ChatMessage("user", "oi")), null);

        provider.stream(request, event -> {
            synchronized (events) {
                events.add(event);
            }
            if (event instanceof AiEvent.Completed || event instanceof AiEvent.Failed) {
                latch.countDown();
            }
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        synchronized (events) {
            AiEvent last = events.get(events.size() - 1);
            assertInstanceOf(AiEvent.Completed.class, last);
            ChatResponse response = ((AiEvent.Completed) last).response();
            assertEquals("Ola!", response.message().content());
            assertEquals("stop", response.finishReason());
            assertEquals(4, response.usage().promptTokens());
            assertEquals(2, response.usage().completionTokens());
        }
    }

    @Test
    void streamComEndpointInexistenteFalhaComConnectionError() throws InterruptedException {
        GeminiProvider provider = new GeminiProvider("key-teste", "http://127.0.0.1:1", Duration.ofSeconds(2));

        AtomicReference<AiEvent> terminal = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        ChatRequest request = new ChatRequest("gemini-2.0-flash", List.of(new ChatMessage("user", "oi")), null);

        provider.stream(request, event -> {
            if (event instanceof AiEvent.Failed) {
                terminal.set(event);
                latch.countDown();
            }
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        AiEvent.Failed failed = (AiEvent.Failed) terminal.get();
        assertInstanceOf(ProviderException.ConnectionError.class, failed.error());
    }
}
