package com.nureal.ide.core.ai.provider;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Stream;

import com.nureal.ide.core.json.JsonParser;
import com.nureal.ide.core.json.JsonWriter;

/**
 * Implementacao inicial de {@link LLMProvider} para o Ollama local (ver
 * {@code docs/023-OllamaProvider.md}). Usa {@code java.net.http.HttpClient}
 * (JDK 17, sem dependencia nova) contra:
 *
 * <pre>
 *   GET  /api/tags   -&gt; health() / listModels()
 *   POST /api/chat    -&gt; chat() / stream()
 * </pre>
 *
 * {@code stream()} le a resposta como NDJSON (um objeto JSON por linha, com
 * {@code "done":false} ate a ultima, que traz {@code "done":true} e as
 * estatisticas finais) via {@code HttpResponse.BodyHandlers.ofLines()}.
 */
public final class OllamaProvider implements LLMProvider {

    private final String baseUrl;
    private final Duration timeout;
    private final HttpClient httpClient;
    private final ExecutorService streamExecutor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "ollama-stream");
        t.setDaemon(true);
        return t;
    });
    private final Map<String, AtomicBoolean> inFlight = new ConcurrentHashMap<>();

    public OllamaProvider(String baseUrl, Duration timeout) {
        this.baseUrl = stripTrailingSlash(baseUrl);
        this.timeout = timeout;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .build();
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    @Override
    public boolean health() {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/api/tags"))
                    .timeout(timeout)
                    .GET()
                    .build();
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            return response.statusCode() == 200;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return false;
        }
    }

    @Override
    public List<String> listModels() {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/api/tags"))
                .timeout(timeout)
                .GET()
                .build();
        HttpResponse<String> response = send(request);
        checkStatus(response.statusCode(), response.body());

        Object parsed = parseBody(response.body());
        List<String> names = new ArrayList<>();
        if (parsed instanceof Map<?, ?> root && root.get("models") instanceof List<?> models) {
            for (Object m : models) {
                if (m instanceof Map<?, ?> model && model.get("name") instanceof String name) {
                    names.add(name);
                }
            }
        }
        return names;
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        String json = JsonWriter.write(buildRequestBody(request, false));
        HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(baseUrl + "/api/chat"))
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = send(httpRequest);
        checkStatus(response.statusCode(), response.body());

        Object parsed = parseBody(response.body());
        if (!(parsed instanceof Map<?, ?> root)) {
            throw new ProviderException.UnexpectedResponse("Resposta do Ollama em formato inesperado.");
        }
        return toChatResponse(root);
    }

    @Override
    public String stream(ChatRequest request, Consumer<AiEvent> onEvent) {
        String requestId = UUID.randomUUID().toString();
        AtomicBoolean cancelled = new AtomicBoolean(false);
        inFlight.put(requestId, cancelled);
        streamExecutor.submit(() -> runStream(requestId, request, onEvent, cancelled));
        return requestId;
    }

    @Override
    public void cancel(String requestId) {
        AtomicBoolean flag = inFlight.get(requestId);
        if (flag != null) {
            flag.set(true);
        }
    }

    private void runStream(String requestId, ChatRequest request, Consumer<AiEvent> onEvent, AtomicBoolean cancelled) {
        onEvent.accept(new AiEvent.Started(requestId));
        try {
            String json = JsonWriter.write(buildRequestBody(request, true));
            HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(baseUrl + "/api/chat"))
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<Stream<String>> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofLines());
            if (response.statusCode() != 200) {
                String body = response.body().reduce("", (a, b) -> a + b);
                onEvent.accept(new AiEvent.Failed(requestId, statusToException(response.statusCode(), body)));
                return;
            }
            StringBuilder fullContent = new StringBuilder();
            List<ToolCall> lastToolCalls = List.of();
            try (Stream<String> lines = response.body()) {
                for (String line : (Iterable<String>) lines::iterator) {
                    if (cancelled.get()) {
                        onEvent.accept(new AiEvent.Cancelled(requestId));
                        return;
                    }
                    if (line.isBlank()) {
                        continue;
                    }
                    Object parsed;
                    try {
                        parsed = JsonParser.parse(line);
                    } catch (JsonParser.JsonParseException e) {
                        onEvent.accept(new AiEvent.Failed(requestId,
                                new ProviderException.UnexpectedResponse("Resposta do Ollama em formato inesperado.", e)));
                        return;
                    }
                    if (!(parsed instanceof Map<?, ?> chunk)) {
                        continue;
                    }
                    if (chunk.get("error") instanceof String errorMsg) {
                        onEvent.accept(new AiEvent.Failed(requestId, new ProviderException.UnexpectedResponse(errorMsg)));
                        return;
                    }
                    if (chunk.get("message") instanceof Map<?, ?> message) {
                        if (message.get("content") instanceof String delta && !delta.isEmpty()) {
                            fullContent.append(delta);
                            onEvent.accept(new AiEvent.Chunk(requestId, delta));
                        }
                        // Ollama pode trazer tool_calls num chunk intermediario ou so no
                        // chunk final (done=true) — guardamos o ultimo visto, o que
                        // cobre os dois casos sem assumir qual deles o servidor usa.
                        List<ToolCall> calls = extractToolCalls(message);
                        if (!calls.isEmpty()) {
                            lastToolCalls = calls;
                        }
                    }
                    if (Boolean.TRUE.equals(chunk.get("done"))) {
                        ChatMessage finalMessage = new ChatMessage(ChatMessage.ROLE_ASSISTANT, fullContent.toString());
                        String finishReason = chunk.get("done_reason") instanceof String r ? r : "stop";
                        ChatUsage usage = extractUsage(chunk);
                        onEvent.accept(new AiEvent.Completed(requestId,
                                new ChatResponse(finalMessage, finishReason, usage, lastToolCalls)));
                        return;
                    }
                }
            }
        } catch (HttpTimeoutException e) {
            onEvent.accept(new AiEvent.Failed(requestId,
                    new ProviderException.Timeout("O Ollama demorou demais para responder.", e)));
        } catch (ConnectException e) {
            onEvent.accept(new AiEvent.Failed(requestId, connectionError(e)));
        } catch (IOException e) {
            onEvent.accept(new AiEvent.Failed(requestId,
                    new ProviderException.ConnectionError("Erro de comunicacao com o Ollama: " + e.getMessage(), e)));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            onEvent.accept(new AiEvent.Cancelled(requestId));
        } finally {
            inFlight.remove(requestId);
        }
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (HttpTimeoutException e) {
            throw new ProviderException.Timeout("O Ollama demorou demais para responder.", e);
        } catch (ConnectException e) {
            throw connectionError(e);
        } catch (IOException e) {
            throw new ProviderException.ConnectionError("Erro de comunicacao com o Ollama: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ProviderException.ConnectionError("Requisicao ao Ollama interrompida.", e);
        }
    }

    private ProviderException.ConnectionError connectionError(Throwable cause) {
        return new ProviderException.ConnectionError(
                "Nao foi possivel conectar ao Ollama em " + baseUrl + ". Ele esta rodando?", cause);
    }

    private void checkStatus(int statusCode, String body) {
        if (statusCode != 200) {
            throw statusToException(statusCode, body);
        }
    }

    private ProviderException statusToException(int statusCode, String body) {
        String detail = extractErrorMessage(body);
        return switch (statusCode) {
            case 404 -> new ProviderException.InvalidModel(
                    detail != null ? detail : "Modelo nao encontrado no Ollama. Ele foi baixado (\"ollama pull\")?");
            case 503 -> new ProviderException.ProviderUnavailable("O Ollama nao esta disponivel no momento.");
            default -> new ProviderException.UnexpectedResponse(
                    "O Ollama respondeu com um erro inesperado (HTTP " + statusCode + ")"
                            + (detail != null ? ": " + detail : "."));
        };
    }

    private String extractErrorMessage(String body) {
        try {
            Object parsed = JsonParser.parse(body);
            if (parsed instanceof Map<?, ?> root && root.get("error") instanceof String error) {
                return error;
            }
        } catch (RuntimeException ignored) {
            // corpo nao e JSON valido — sem detalhe extra
        }
        return null;
    }

    private Object parseBody(String body) {
        try {
            return JsonParser.parse(body);
        } catch (JsonParser.JsonParseException e) {
            throw new ProviderException.UnexpectedResponse("Resposta do Ollama em formato inesperado.", e);
        }
    }

    private Map<String, Object> buildRequestBody(ChatRequest request, boolean stream) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", request.model());
        List<Map<String, Object>> messages = new ArrayList<>();
        for (ChatMessage m : request.messages()) {
            Map<String, Object> msg = new LinkedHashMap<>();
            msg.put("role", m.role());
            msg.put("content", m.content());
            messages.add(msg);
        }
        body.put("messages", messages);
        body.put("stream", stream);
        if (request.temperature() != null) {
            body.put("options", Map.of("temperature", request.temperature()));
        }
        if (!request.tools().isEmpty()) {
            List<Map<String, Object>> tools = new ArrayList<>();
            for (ToolSpec tool : request.tools()) {
                Map<String, Object> function = new LinkedHashMap<>();
                function.put("name", tool.name());
                function.put("description", tool.description());
                function.put("parameters", tool.parametersSchema());
                tools.add(Map.of("type", "function", "function", function));
            }
            body.put("tools", tools);
        }
        return body;
    }

    private ChatResponse toChatResponse(Map<?, ?> root) {
        Map<?, ?> messageObj = root.get("message") instanceof Map<?, ?> m ? m : Map.of();
        String role = messageObj.get("role") instanceof String r ? r : ChatMessage.ROLE_ASSISTANT;
        String content = messageObj.get("content") instanceof String c ? c : "";
        ChatMessage message = new ChatMessage(role, content);

        String finishReason = root.get("done_reason") instanceof String r ? r : "stop";
        ChatUsage usage = extractUsage(root);
        List<ToolCall> toolCalls = extractToolCalls(messageObj);
        return new ChatResponse(message, finishReason, usage, toolCalls);
    }

    private ChatUsage extractUsage(Map<?, ?> root) {
        long promptTokens = asLong(root.get("prompt_eval_count"));
        long completionTokens = asLong(root.get("eval_count"));
        return new ChatUsage(promptTokens, completionTokens);
    }

    @SuppressWarnings("unchecked")
    private List<ToolCall> extractToolCalls(Map<?, ?> messageObj) {
        if (!(messageObj.get("tool_calls") instanceof List<?> rawCalls)) {
            return List.of();
        }
        List<ToolCall> calls = new ArrayList<>();
        for (Object rawCall : rawCalls) {
            if (!(rawCall instanceof Map<?, ?> call)) {
                continue;
            }
            String id = call.get("id") instanceof String i ? i : null;
            if (!(call.get("function") instanceof Map<?, ?> function)) {
                continue;
            }
            String name = function.get("name") instanceof String n ? n : null;
            if (name == null) {
                continue;
            }
            Object argumentsRaw = function.get("arguments");
            Map<String, Object> arguments = argumentsRaw instanceof Map<?, ?> a
                    ? (Map<String, Object>) a
                    : Map.of();
            calls.add(new ToolCall(id, name, arguments));
        }
        return calls;
    }

    private static long asLong(Object value) {
        if (value instanceof Number n) {
            return n.longValue();
        }
        return 0L;
    }
}
