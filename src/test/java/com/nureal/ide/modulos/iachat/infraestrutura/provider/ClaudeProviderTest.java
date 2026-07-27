package com.nureal.ide.modulos.iachat.infraestrutura.provider;
import com.nureal.ide.modulos.iachat.dominio.entidades.ChatRequest;
import com.nureal.ide.modulos.iachat.dominio.entidades.ToolSpec;
import com.nureal.ide.modulos.iachat.dominio.entidades.AiEvent;
import com.nureal.ide.modulos.iachat.dominio.entidades.ChatMessage;
import com.nureal.ide.modulos.iachat.dominio.entidades.ProviderException;
import com.nureal.ide.modulos.iachat.dominio.entidades.ChatResponse;

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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.nureal.ide.core.json.JsonParser;
import com.sun.net.httpserver.HttpServer;

/**
 * Testes do {@link ClaudeProvider} — nao usa {@link OpenAiCompatibleProvider} (formato
 * de wire proprio), entao precisa da sua propria suite completa (nao so um teste leve
 * de subclasse como {@code OpenRouterProviderTest}). Usa o construtor pacote-visivel
 * {@code ClaudeProvider(apiKey, baseUrl, timeout)} pra apontar pro servidor fake local
 * (a API oficial da Anthropic e fixa no construtor publico, usado em producao).
 */
class ClaudeProviderTest {

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
    void listModelsParseiaDataId() throws IOException {
        String json = "{\"data\":[{\"id\":\"claude-sonnet-4-20250514\"},{\"id\":\"claude-opus-4-20250514\"}]}";
        String baseUrl = startServer("/v1/models", json.getBytes(StandardCharsets.UTF_8), 200);
        ClaudeProvider provider = new ClaudeProvider("sk-ant-teste", baseUrl, Duration.ofSeconds(5));

        assertEquals(List.of("claude-sonnet-4-20250514", "claude-opus-4-20250514"), provider.listModels());
    }

    @Test
    void chatNaoStreamingParseiaTextoEUsage() throws IOException {
        String json = "{\"content\":[{\"type\":\"text\",\"text\":\"Ola!\"}],"
                + "\"stop_reason\":\"end_turn\",\"usage\":{\"input_tokens\":10,\"output_tokens\":3}}";
        String baseUrl = startServer("/v1/messages", json.getBytes(StandardCharsets.UTF_8), 200);
        ClaudeProvider provider = new ClaudeProvider("sk-ant-teste", baseUrl, Duration.ofSeconds(5));

        ChatRequest request = new ChatRequest("claude-sonnet-4", List.of(new ChatMessage("user", "oi")), null);
        ChatResponse response = provider.chat(request);

        assertEquals("Ola!", response.message().content());
        assertEquals("end_turn", response.finishReason());
        assertEquals(10, response.usage().promptTokens());
        assertEquals(3, response.usage().completionTokens());
        assertFalse(response.hasToolCalls());
    }

    @Test
    void chatNaoStreamingParseiaToolUse() throws IOException {
        String json = "{\"content\":[{\"type\":\"tool_use\",\"id\":\"toolu_1\",\"name\":\"list_tables\",\"input\":{}}],"
                + "\"stop_reason\":\"tool_use\",\"usage\":{\"input_tokens\":8,\"output_tokens\":2}}";
        String baseUrl = startServer("/v1/messages", json.getBytes(StandardCharsets.UTF_8), 200);
        ClaudeProvider provider = new ClaudeProvider("sk-ant-teste", baseUrl, Duration.ofSeconds(5));

        ChatRequest request = new ChatRequest("claude-sonnet-4", List.of(new ChatMessage("user", "quais tabelas?")),
                null, List.of(new ToolSpec("list_tables", "lista tabelas", java.util.Map.of())));
        ChatResponse response = provider.chat(request);

        assertTrue(response.hasToolCalls());
        assertEquals("list_tables", response.toolCalls().get(0).name());
        assertEquals("toolu_1", response.toolCalls().get(0).id());
    }

    @Test
    void requestSeparaSystemPromptDoArrayDeMessages() throws IOException {
        ByteArrayOutputStream capturedBody = new ByteArrayOutputStream();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/messages", exchange -> {
            exchange.getRequestBody().transferTo(capturedBody);
            byte[] body = "{\"content\":[{\"type\":\"text\",\"text\":\"ok\"}],\"stop_reason\":\"end_turn\"}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server.start();
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        ClaudeProvider provider = new ClaudeProvider("sk-ant-teste", baseUrl, Duration.ofSeconds(5));

        ChatRequest request = new ChatRequest("claude-sonnet-4",
                List.of(new ChatMessage(ChatMessage.ROLE_SYSTEM, "voce e um assistente de banco de dados"),
                        new ChatMessage(ChatMessage.ROLE_USER, "oi")),
                null);
        provider.chat(request);

        @SuppressWarnings("unchecked")
        var parsedBody = (java.util.Map<String, Object>) JsonParser.parse(capturedBody.toString(StandardCharsets.UTF_8));
        assertEquals("voce e um assistente de banco de dados", parsedBody.get("system"));
        @SuppressWarnings("unchecked")
        var messages = (List<Object>) parsedBody.get("messages");
        assertEquals(1, messages.size(), "a mensagem de system nao deveria sobrar no array messages");
    }

    @Test
    void chatErro401ViraMissingCredential() throws IOException {
        String json = "{\"type\":\"error\",\"error\":{\"type\":\"authentication_error\",\"message\":\"invalid x-api-key\"}}";
        String baseUrl = startServer("/v1/messages", json.getBytes(StandardCharsets.UTF_8), 401);
        ClaudeProvider provider = new ClaudeProvider("sk-invalida", baseUrl, Duration.ofSeconds(5));

        ChatRequest request = new ChatRequest("claude-sonnet-4", List.of(new ChatMessage("user", "oi")), null);
        ProviderException.MissingCredential ex = org.junit.jupiter.api.Assertions.assertThrows(
                ProviderException.MissingCredential.class, () -> provider.chat(request));
        assertTrue(ex.getMessage().contains("invalid x-api-key"));
    }

    @Test
    void streamAcumulaTextDeltasEEncerraNoMessageStop() throws IOException, InterruptedException {
        String sse = String.join("\n",
                "data: {\"type\":\"message_start\",\"message\":{\"usage\":{\"input_tokens\":7}}}",
                "data: {\"type\":\"content_block_start\",\"index\":0,\"content_block\":{\"type\":\"text\",\"text\":\"\"}}",
                "data: {\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"text_delta\",\"text\":\"Ol\"}}",
                "data: {\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"text_delta\",\"text\":\"a!\"}}",
                "data: {\"type\":\"content_block_stop\",\"index\":0}",
                "data: {\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"end_turn\"},\"usage\":{\"output_tokens\":2}}",
                "data: {\"type\":\"message_stop\"}") + "\n";
        String baseUrl = startServer("/v1/messages", sse.getBytes(StandardCharsets.UTF_8), 200);
        ClaudeProvider provider = new ClaudeProvider("sk-ant-teste", baseUrl, Duration.ofSeconds(5));

        List<AiEvent> events = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);
        ChatRequest request = new ChatRequest("claude-sonnet-4", List.of(new ChatMessage("user", "oi")), null);

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
            assertEquals("end_turn", response.finishReason());
            assertEquals(7, response.usage().promptTokens());
            assertEquals(2, response.usage().completionTokens());
        }
    }

    @Test
    void streamAcumulaToolUseComInputJsonDeltaFragmentado() throws IOException, InterruptedException {
        String sse = String.join("\n",
                "data: {\"type\":\"content_block_start\",\"index\":0,\"content_block\":"
                        + "{\"type\":\"tool_use\",\"id\":\"toolu_1\",\"name\":\"describe_table\"}}",
                "data: {\"type\":\"content_block_delta\",\"index\":0,\"delta\":"
                        + "{\"type\":\"input_json_delta\",\"partial_json\":\"{\\\"table\\\":\"}}",
                "data: {\"type\":\"content_block_delta\",\"index\":0,\"delta\":"
                        + "{\"type\":\"input_json_delta\",\"partial_json\":\"\\\"orders\\\"}\"}}",
                "data: {\"type\":\"content_block_stop\",\"index\":0}",
                "data: {\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"tool_use\"}}",
                "data: {\"type\":\"message_stop\"}") + "\n";
        String baseUrl = startServer("/v1/messages", sse.getBytes(StandardCharsets.UTF_8), 200);
        ClaudeProvider provider = new ClaudeProvider("sk-ant-teste", baseUrl, Duration.ofSeconds(5));

        AtomicReference<AiEvent.Completed> completed = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        ChatRequest request = new ChatRequest("claude-sonnet-4", List.of(new ChatMessage("user", "descreva orders")),
                null, List.of(new ToolSpec("describe_table", "descreve", java.util.Map.of())));

        provider.stream(request, event -> {
            if (event instanceof AiEvent.Completed c) {
                completed.set(c);
                latch.countDown();
            }
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        ChatResponse response = completed.get().response();
        assertTrue(response.hasToolCalls());
        assertEquals("describe_table", response.toolCalls().get(0).name());
        assertEquals("orders", response.toolCalls().get(0).arguments().get("table"));
    }

    @Test
    void streamComEndpointInexistenteFalhaComConnectionError() throws InterruptedException {
        ClaudeProvider provider = new ClaudeProvider("sk-ant-teste", "http://127.0.0.1:1",
                Duration.ofSeconds(2));

        AtomicReference<AiEvent> terminal = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        ChatRequest request = new ChatRequest("claude-sonnet-4", List.of(new ChatMessage("user", "oi")), null);

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
