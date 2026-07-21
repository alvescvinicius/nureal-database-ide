package com.nureal.ide.core.ai.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.nureal.ide.core.json.JsonParser;
import com.sun.net.httpserver.HttpServer;

/**
 * Testes de {@link GeminiSession} — a {@link ConversationSession} do Gemini,
 * que referencia tools por NOME (o Gemini nao gera id de function call) e
 * mantem os {@code contents} nativos entre rodadas sem passar por
 * {@link ChatMessage} generico.
 */
class GeminiSessionTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void submitToolResultReenviaComFunctionResponsePorNome() throws IOException, InterruptedException {
        String firstRoundSse = "data: {\"candidates\":[{\"content\":{\"role\":\"model\",\"parts\":["
                + "{\"functionCall\":{\"name\":\"list_tables\",\"args\":{}}}]},\"finishReason\":\"STOP\"}]}\n";
        String secondRoundSse = "data: {\"candidates\":[{\"content\":{\"role\":\"model\",\"parts\":["
                + "{\"text\":\"Pronto.\"}]},\"finishReason\":\"STOP\"}]}\n";

        AtomicInteger requestCount = new AtomicInteger(0);
        List<String> capturedBodies = new ArrayList<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1beta/models/gemini-2.0-flash:streamGenerateContent", exchange -> {
            ByteArrayOutputStream captured = new ByteArrayOutputStream();
            exchange.getRequestBody().transferTo(captured);
            synchronized (capturedBodies) {
                capturedBodies.add(captured.toString(StandardCharsets.UTF_8));
            }
            byte[] body = (requestCount.getAndIncrement() == 0 ? firstRoundSse : secondRoundSse)
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server.start();
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        GeminiProvider provider = new GeminiProvider("key-teste", baseUrl, Duration.ofSeconds(5));

        ChatRequest seed = new ChatRequest("gemini-2.0-flash",
                List.of(new ChatMessage(ChatMessage.ROLE_SYSTEM, "voce e um assistente de banco de dados")), null,
                List.of(new ToolSpec("list_tables", "lista tabelas", Map.of())));

        AtomicReference<AiEvent.ToolCallsRequested> toolCallsRequested = new AtomicReference<>();
        AtomicReference<AiEvent.Completed> finalCompleted = new AtomicReference<>();
        CountDownLatch toolLatch = new CountDownLatch(1);
        CountDownLatch finalLatch = new CountDownLatch(1);

        ConversationSession session = provider.createSession(seed, event -> {
            if (event instanceof AiEvent.ToolCallsRequested requested) {
                toolCallsRequested.set(requested);
                toolLatch.countDown();
            } else if (event instanceof AiEvent.Completed completed) {
                finalCompleted.set(completed);
                finalLatch.countDown();
            }
        });

        session.send("quais tabelas existem?");
        assertTrue(toolLatch.await(5, TimeUnit.SECONDS));

        AiEvent.ToolCallsRequested requested = toolCallsRequested.get();
        assertEquals(1, requested.calls().size());
        assertEquals("list_tables", requested.calls().get(0).id(), "Gemini nao da id -- usamos o proprio nome");

        session.submitToolResult(requested.calls().get(0).id(), "orders, customers");
        assertTrue(finalLatch.await(5, TimeUnit.SECONDS));
        assertEquals("Pronto.", finalCompleted.get().response().message().content());

        assertEquals(2, capturedBodies.size(), "uma chamada pra decidir a tool, outra apos o resultado");
        @SuppressWarnings("unchecked")
        var secondBody = (Map<String, Object>) JsonParser.parse(capturedBodies.get(1));
        @SuppressWarnings("unchecked")
        var contents = (List<Map<String, Object>>) secondBody.get("contents");
        assertEquals(3, contents.size(),
                "user original + model com functionCall + user com functionResponse, sem reconstruir do zero");

        Map<String, Object> modelContent = contents.get(1);
        assertEquals("model", modelContent.get("role"));
        @SuppressWarnings("unchecked")
        var modelParts = (List<Map<String, Object>>) modelContent.get("parts");
        @SuppressWarnings("unchecked")
        var functionCall = (Map<String, Object>) modelParts.get(0).get("functionCall");
        assertEquals("list_tables", functionCall.get("name"));

        Map<String, Object> functionResponseContent = contents.get(2);
        assertEquals("user", functionResponseContent.get("role"));
        @SuppressWarnings("unchecked")
        var responseParts = (List<Map<String, Object>>) functionResponseContent.get("parts");
        @SuppressWarnings("unchecked")
        var functionResponse = (Map<String, Object>) responseParts.get(0).get("functionResponse");
        assertEquals("list_tables", functionResponse.get("name"));
    }

    @Test
    void semToolCallEncerraDireto() throws IOException, InterruptedException {
        String sse = "data: {\"candidates\":[{\"content\":{\"role\":\"model\",\"parts\":["
                + "{\"text\":\"Ola!\"}]},\"finishReason\":\"STOP\"}]}\n";
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1beta/models/gemini-2.0-flash:streamGenerateContent", exchange -> {
            byte[] body = sse.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server.start();
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        GeminiProvider provider = new GeminiProvider("key-teste", baseUrl, Duration.ofSeconds(5));

        ChatRequest seed = new ChatRequest("gemini-2.0-flash",
                List.of(new ChatMessage(ChatMessage.ROLE_SYSTEM, "voce e um assistente")), null);

        List<AiEvent> events = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);
        ConversationSession session = provider.createSession(seed, event -> {
            synchronized (events) {
                events.add(event);
            }
            if (event instanceof AiEvent.Completed) {
                latch.countDown();
            }
        });

        session.send("oi");
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        synchronized (events) {
            assertTrue(events.stream().noneMatch(e -> e instanceof AiEvent.ToolCallsRequested));
            AiEvent last = events.get(events.size() - 1);
            assertInstanceOf(AiEvent.Completed.class, last);
            assertEquals("Ola!", ((AiEvent.Completed) last).response().message().content());
        }
    }
}
