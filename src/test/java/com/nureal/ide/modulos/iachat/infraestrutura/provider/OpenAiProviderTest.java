package com.nureal.ide.modulos.iachat.infraestrutura.provider;
import com.nureal.ide.modulos.iachat.dominio.entidades.ChatRequest;
import com.nureal.ide.modulos.iachat.dominio.entidades.ToolSpec;
import com.nureal.ide.modulos.iachat.dominio.entidades.AiEvent;
import com.nureal.ide.modulos.iachat.dominio.entidades.ChatMessage;
import com.nureal.ide.modulos.iachat.dominio.entidades.ProviderException;
import com.nureal.ide.modulos.iachat.dominio.entidades.ChatResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

import com.sun.net.httpserver.HttpServer;

/**
 * Testes do {@link OpenAiProvider} — como ele so acrescenta base URL/nome ao que
 * {@link OpenAiCompatibleProvider} ja implementa, estes testes cobrem a base
 * compartilhada tambem (reusada por {@link OpenRouterProvider}).
 */
class OpenAiProviderTest {

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
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
    }

    /** Provider apontado pra base URL fake — {@link OpenAiProvider} sempre usa a URL oficial, entao subclassificamos so pro teste. */
    private static final class TestableOpenAi extends OpenAiCompatibleProvider {
        TestableOpenAi(String apiKey, String baseUrl, Duration timeout) {
            super(apiKey, baseUrl, timeout);
        }

        @Override
        protected String providerName() {
            return "OpenAI";
        }
    }

    @Test
    void listModelsParseiaDataId() throws IOException {
        String json = "{\"data\":[{\"id\":\"gpt-4o\"},{\"id\":\"gpt-4o-mini\"}]}";
        String baseUrl = startServer("/v1/models", json.getBytes(StandardCharsets.UTF_8), 200);
        TestableOpenAi provider = new TestableOpenAi("sk-teste", baseUrl, Duration.ofSeconds(5));

        assertEquals(List.of("gpt-4o", "gpt-4o-mini"), provider.listModels());
    }

    @Test
    void chatNaoStreamingParseiaMensagemEToolCallsComoStringJson() throws IOException {
        String json = "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":null,"
                + "\"tool_calls\":[{\"id\":\"call_1\",\"type\":\"function\",\"function\":"
                + "{\"name\":\"list_tables\",\"arguments\":\"{\\\"schema\\\":\\\"prod\\\"}\"}}]},"
                + "\"finish_reason\":\"tool_calls\"}],\"usage\":{\"prompt_tokens\":12,\"completion_tokens\":4}}";
        String baseUrl = startServer("/v1/chat/completions", json.getBytes(StandardCharsets.UTF_8), 200);
        TestableOpenAi provider = new TestableOpenAi("sk-teste", baseUrl, Duration.ofSeconds(5));

        ChatRequest request = new ChatRequest("gpt-4o", List.of(new ChatMessage("user", "quais tabelas?")), null);
        ChatResponse response = provider.chat(request);

        assertEquals("tool_calls", response.finishReason());
        assertEquals(12, response.usage().promptTokens());
        assertEquals(4, response.usage().completionTokens());
        assertTrue(response.hasToolCalls());
        assertEquals("list_tables", response.toolCalls().get(0).name());
        assertEquals("prod", response.toolCalls().get(0).arguments().get("schema"));
    }

    @Test
    void chatErro401ViraMissingCredential() throws IOException {
        String json = "{\"error\":{\"message\":\"Incorrect API key provided\"}}";
        String baseUrl = startServer("/v1/chat/completions", json.getBytes(StandardCharsets.UTF_8), 401);
        TestableOpenAi provider = new TestableOpenAi("sk-invalida", baseUrl, Duration.ofSeconds(5));

        ChatRequest request = new ChatRequest("gpt-4o", List.of(new ChatMessage("user", "oi")), null);
        ProviderException.MissingCredential ex = org.junit.jupiter.api.Assertions.assertThrows(
                ProviderException.MissingCredential.class, () -> provider.chat(request));
        assertTrue(ex.getMessage().contains("Incorrect API key"));
    }

    @Test
    void streamAcumulaContentDeltasEEncerraNoDone() throws IOException, InterruptedException {
        String sse = String.join("\n",
                "data: {\"choices\":[{\"delta\":{\"content\":\"Ol\"}}]}",
                "data: {\"choices\":[{\"delta\":{\"content\":\"a!\"}}]}",
                "data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}]}",
                "data: {\"usage\":{\"prompt_tokens\":5,\"completion_tokens\":2}}",
                "data: [DONE]") + "\n";
        String baseUrl = startServer("/v1/chat/completions", sse.getBytes(StandardCharsets.UTF_8), 200);
        TestableOpenAi provider = new TestableOpenAi("sk-teste", baseUrl, Duration.ofSeconds(5));

        List<AiEvent> events = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);
        ChatRequest request = new ChatRequest("gpt-4o", List.of(new ChatMessage("user", "oi")), null);

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
            assertEquals(5, response.usage().promptTokens());
            assertEquals(2, response.usage().completionTokens());
        }
    }

    /** Monta a linha SSE "data: {...}" de um delta de tool_calls com JsonWriter, em vez de escapar JSON a mao (propenso a erro). */
    private static String toolCallDeltaLine(int index, String id, String name, String argumentsFragment) {
        Map<String, Object> function = new java.util.LinkedHashMap<>();
        if (name != null) {
            function.put("name", name);
        }
        function.put("arguments", argumentsFragment);
        Map<String, Object> toolCall = new java.util.LinkedHashMap<>();
        toolCall.put("index", index);
        if (id != null) {
            toolCall.put("id", id);
        }
        toolCall.put("function", function);
        Map<String, Object> delta = Map.of("tool_calls", List.of(toolCall));
        Map<String, Object> choice = Map.of("delta", delta);
        return "data: " + com.nureal.ide.core.json.JsonWriter.write(Map.of("choices", List.of(choice)));
    }

    @Test
    void streamAcumulaToolCallsFragmentadosPorIndice() throws IOException, InterruptedException {
        String sse = String.join("\n",
                toolCallDeltaLine(0, "call_1", "describe_table", ""),
                toolCallDeltaLine(0, null, null, "{\"table\":"),
                toolCallDeltaLine(0, null, null, "\"orders\"}"),
                "data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"tool_calls\"}]}",
                "data: [DONE]") + "\n";
        String baseUrl = startServer("/v1/chat/completions", sse.getBytes(StandardCharsets.UTF_8), 200);
        TestableOpenAi provider = new TestableOpenAi("sk-teste", baseUrl, Duration.ofSeconds(5));

        AtomicReference<AiEvent.Completed> completed = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        ChatRequest request = new ChatRequest("gpt-4o", List.of(new ChatMessage("user", "descreva orders")), null,
                List.of(new ToolSpec("describe_table", "descreve tabela", Map.of())));

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
        TestableOpenAi provider = new TestableOpenAi("sk-teste", "http://127.0.0.1:1", Duration.ofSeconds(2));

        AtomicReference<AiEvent> terminal = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        ChatRequest request = new ChatRequest("gpt-4o", List.of(new ChatMessage("user", "oi")), null);

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
