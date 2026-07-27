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
 * Testes do {@link OllamaProvider} contra um servidor HTTP fake local
 * ({@code com.sun.net.httpserver.HttpServer}, incluso no JDK — sem
 * dependencia nova so para testar), simulando as respostas do Ollama.
 */
class OllamaProviderTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    private String startServer(String path, String contentType, byte[] body, int status) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(path, exchange -> {
            exchange.getResponseHeaders().add("Content-Type", contentType);
            exchange.sendResponseHeaders(status, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @Test
    void listModelsParseiaNomesDoTags() throws IOException {
        String json = "{\"models\":[{\"name\":\"llama3.1:8b\"},{\"name\":\"qwen2.5:7b\"}]}";
        String baseUrl = startServer("/api/tags", "application/json", json.getBytes(StandardCharsets.UTF_8), 200);
        OllamaProvider provider = new OllamaProvider(baseUrl, Duration.ofSeconds(5));

        assertEquals(List.of("llama3.1:8b", "qwen2.5:7b"), provider.listModels());
    }

    @Test
    void healthTrueQuandoServidorResponde200() throws IOException {
        String baseUrl = startServer("/api/tags", "application/json", "{}".getBytes(StandardCharsets.UTF_8), 200);
        OllamaProvider provider = new OllamaProvider(baseUrl, Duration.ofSeconds(5));

        assertTrue(provider.health());
    }

    @Test
    void healthFalseQuandoNaoHaServidor() {
        OllamaProvider provider = new OllamaProvider("http://127.0.0.1:1", Duration.ofSeconds(2));

        assertFalse(provider.health());
    }

    @Test
    void chatNaoStreamingParseiaMensagemEUsage() throws IOException {
        String json = "{\"message\":{\"role\":\"assistant\",\"content\":\"Ola!\"},"
                + "\"done_reason\":\"stop\",\"prompt_eval_count\":10,\"eval_count\":3}";
        String baseUrl = startServer("/api/chat", "application/json", json.getBytes(StandardCharsets.UTF_8), 200);
        OllamaProvider provider = new OllamaProvider(baseUrl, Duration.ofSeconds(5));

        ChatRequest request = new ChatRequest("llama3.1", List.of(new ChatMessage("user", "oi")), 0.2);
        ChatResponse response = provider.chat(request);

        assertEquals("Ola!", response.message().content());
        assertEquals("stop", response.finishReason());
        assertEquals(10, response.usage().promptTokens());
        assertEquals(3, response.usage().completionTokens());
        assertFalse(response.hasToolCalls());
    }

    @Test
    void chatModeloInexistenteLanca404ComoInvalidModel() throws IOException {
        String json = "{\"error\":\"model 'nope' not found\"}";
        String baseUrl = startServer("/api/chat", "application/json", json.getBytes(StandardCharsets.UTF_8), 404);
        OllamaProvider provider = new OllamaProvider(baseUrl, Duration.ofSeconds(5));

        ChatRequest request = new ChatRequest("nope", List.of(new ChatMessage("user", "oi")), null);
        ProviderException ex = org.junit.jupiter.api.Assertions.assertThrows(ProviderException.InvalidModel.class,
                () -> provider.chat(request));
        assertTrue(ex.getMessage().contains("not found"));
    }

    @Test
    void streamEntregaChunksEEventoCompletedComConteudoAcumulado() throws IOException, InterruptedException {
        String ndjson = String.join("\n",
                "{\"message\":{\"role\":\"assistant\",\"content\":\"Ol\"},\"done\":false}",
                "{\"message\":{\"role\":\"assistant\",\"content\":\"a!\"},\"done\":false}",
                "{\"message\":{\"role\":\"assistant\",\"content\":\"\"},\"done\":true,\"done_reason\":\"stop\","
                        + "\"prompt_eval_count\":5,\"eval_count\":2}") + "\n";
        String baseUrl = startServer("/api/chat", "application/x-ndjson", ndjson.getBytes(StandardCharsets.UTF_8), 200);
        OllamaProvider provider = new OllamaProvider(baseUrl, Duration.ofSeconds(5));

        List<AiEvent> events = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);
        ChatRequest request = new ChatRequest("llama3.1", List.of(new ChatMessage("user", "oi")), null);

        provider.stream(request, event -> {
            synchronized (events) {
                events.add(event);
            }
            if (event instanceof AiEvent.Completed || event instanceof AiEvent.Failed) {
                latch.countDown();
            }
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS), "esperava evento terminal dentro do timeout");
        synchronized (events) {
            assertInstanceOf(AiEvent.Started.class, events.get(0));
            AiEvent last = events.get(events.size() - 1);
            assertInstanceOf(AiEvent.Completed.class, last);
            ChatResponse response = ((AiEvent.Completed) last).response();
            assertEquals("Ola!", response.message().content());
            assertEquals(5, response.usage().promptTokens());
            assertEquals(2, response.usage().completionTokens());

            long chunkCount = events.stream().filter(e -> e instanceof AiEvent.Chunk).count();
            assertEquals(2, chunkCount);
        }
    }

    @Test
    void streamExtraiToolCallsDoChunkFinal() throws IOException, InterruptedException {
        String ndjson = String.join("\n",
                "{\"message\":{\"role\":\"assistant\",\"content\":\"\",\"tool_calls\":[{\"id\":\"call1\","
                        + "\"function\":{\"name\":\"list_tables\",\"arguments\":{}}}]},\"done\":false}",
                "{\"message\":{\"role\":\"assistant\",\"content\":\"\"},\"done\":true,\"done_reason\":\"stop\"}")
                + "\n";
        String baseUrl = startServer("/api/chat", "application/x-ndjson", ndjson.getBytes(StandardCharsets.UTF_8), 200);
        OllamaProvider provider = new OllamaProvider(baseUrl, Duration.ofSeconds(5));

        AtomicReference<AiEvent.Completed> completed = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        ChatRequest request = new ChatRequest("llama3.1",
                List.of(new ChatMessage("user", "quais tabelas existem?")), null,
                List.of(new ToolSpec("list_tables", "lista tabelas", Map.of())));

        provider.stream(request, event -> {
            if (event instanceof AiEvent.Completed c) {
                completed.set(c);
                latch.countDown();
            }
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS), "esperava AiEvent.Completed dentro do timeout");
        ChatResponse response = completed.get().response();
        assertTrue(response.hasToolCalls());
        assertEquals(1, response.toolCalls().size());
        assertEquals("list_tables", response.toolCalls().get(0).name());
        assertEquals("call1", response.toolCalls().get(0).id());
    }

    @Test
    void streamComEndpointInexistenteFalhaComConnectionError() throws InterruptedException {
        OllamaProvider provider = new OllamaProvider("http://127.0.0.1:1", Duration.ofSeconds(2));
        AtomicReference<AiEvent> terminal = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        ChatRequest request = new ChatRequest("llama3.1", List.of(new ChatMessage("user", "oi")), null);
        provider.stream(request, event -> {
            if (event instanceof AiEvent.Failed) {
                terminal.set(event);
                latch.countDown();
            }
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS), "esperava AiEvent.Failed dentro do timeout");
        AiEvent.Failed failed = (AiEvent.Failed) terminal.get();
        assertInstanceOf(ProviderException.ConnectionError.class, failed.error());
    }
}
