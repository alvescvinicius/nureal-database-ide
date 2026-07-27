package com.nureal.ide.modulos.iachat.infraestrutura.provider;
import com.nureal.ide.modulos.iachat.dominio.entidades.ChatRequest;
import com.nureal.ide.modulos.iachat.dominio.contratos.ConversationSession;
import com.nureal.ide.modulos.iachat.dominio.entidades.ToolSpec;
import com.nureal.ide.modulos.iachat.dominio.entidades.AiEvent;
import com.nureal.ide.modulos.iachat.dominio.entidades.ChatMessage;
import com.nureal.ide.modulos.iachat.dominio.contratos.Agent;

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
 * Testes de {@link ClaudeSession} — a {@link ConversationSession} que a
 * {@link ClaudeProvider} usa, mantendo os content blocks nativos (com o
 * {@code tool_use_id} REAL que a Claude devolve) entre rodadas, sem o Agent
 * (nem um {@link ChatMessage} generico) precisar conhecer esse protocolo.
 */
class ClaudeSessionTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void submitToolResultReenviaComOToolUseIdRealDaClaude() throws IOException, InterruptedException {
        String firstRoundSse = String.join("\n",
                "data: {\"type\":\"content_block_start\",\"index\":0,\"content_block\":"
                        + "{\"type\":\"tool_use\",\"id\":\"toolu_1\",\"name\":\"list_tables\"}}",
                "data: {\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"tool_use\"}}",
                "data: {\"type\":\"message_stop\"}") + "\n";
        String secondRoundSse = String.join("\n",
                "data: {\"type\":\"content_block_delta\",\"index\":0,\"delta\":"
                        + "{\"type\":\"text_delta\",\"text\":\"Pronto.\"}}",
                "data: {\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"end_turn\"}}",
                "data: {\"type\":\"message_stop\"}") + "\n";

        AtomicInteger requestCount = new AtomicInteger(0);
        List<String> capturedBodies = new ArrayList<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/messages", exchange -> {
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
        ClaudeProvider provider = new ClaudeProvider("sk-ant-teste", baseUrl, Duration.ofSeconds(5));

        ChatRequest seed = new ChatRequest("claude-sonnet-4",
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
        assertEquals("toolu_1", requested.calls().get(0).id(), "deve preservar o id REAL que a Claude devolveu");
        assertEquals("list_tables", requested.calls().get(0).name());

        session.submitToolResult(requested.calls().get(0).id(), "orders, customers");
        assertTrue(finalLatch.await(5, TimeUnit.SECONDS));
        assertEquals("Pronto.", finalCompleted.get().response().message().content());

        assertEquals(2, capturedBodies.size(), "uma chamada pra decidir a tool, outra apos o resultado");
        @SuppressWarnings("unchecked")
        var secondBody = (Map<String, Object>) JsonParser.parse(capturedBodies.get(1));
        @SuppressWarnings("unchecked")
        var messages = (List<Map<String, Object>>) secondBody.get("messages");
        assertEquals(3, messages.size(),
                "user original + assistant com tool_use + user com tool_result, sem reconstruir do zero");

        Map<String, Object> assistantMessage = messages.get(1);
        assertEquals("assistant", assistantMessage.get("role"));
        @SuppressWarnings("unchecked")
        var assistantBlocks = (List<Map<String, Object>>) assistantMessage.get("content");
        assertEquals("tool_use", assistantBlocks.get(0).get("type"));
        assertEquals("toolu_1", assistantBlocks.get(0).get("id"));

        Map<String, Object> toolResultMessage = messages.get(2);
        assertEquals("user", toolResultMessage.get("role"));
        @SuppressWarnings("unchecked")
        var toolResultBlocks = (List<Map<String, Object>>) toolResultMessage.get("content");
        assertEquals("tool_result", toolResultBlocks.get(0).get("type"));
        assertEquals("toolu_1", toolResultBlocks.get(0).get("tool_use_id"));
        assertEquals("orders, customers", toolResultBlocks.get(0).get("content"));
    }

    @Test
    void semToolCallEncerraDireto() throws IOException, InterruptedException {
        String sse = String.join("\n",
                "data: {\"type\":\"content_block_delta\",\"index\":0,\"delta\":"
                        + "{\"type\":\"text_delta\",\"text\":\"Ola!\"}}",
                "data: {\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"end_turn\"}}",
                "data: {\"type\":\"message_stop\"}") + "\n";
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/messages", exchange -> {
            byte[] body = sse.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server.start();
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        ClaudeProvider provider = new ClaudeProvider("sk-ant-teste", baseUrl, Duration.ofSeconds(5));

        ChatRequest seed = new ChatRequest("claude-sonnet-4",
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
