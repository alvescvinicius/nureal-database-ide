package com.nureal.ide.core.ai.provider;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.channels.ClosedChannelException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Stream;

import com.nureal.ide.core.json.JsonParser;
import com.nureal.ide.core.json.JsonWriter;

/**
 * Base pro formato de wire "OpenAI Chat Completions" — usado pela OpenAI em si e
 * (por compatibilidade total) pelo OpenRouter, que so muda base URL/headers (ver
 * {@link OpenAiProvider}/{@link OpenRouterProvider}). Streaming via SSE
 * (<code>data: {...}</code>, termina em <code>data: [DONE]</code>), diferente do
 * NDJSON puro do Ollama — usa {@link SseUtil}.
 *
 * Tool calls chegam fragmentados no streaming (nome/argumentos picados por
 * {@code index} entre chunks) — {@link ToolCallBuilder} acumula ate montar a
 * {@link ToolCall} final.
 */
abstract class OpenAiCompatibleProvider extends AbstractStreamingProvider {

    protected final String apiKey;
    protected final String baseUrl;

    protected OpenAiCompatibleProvider(String apiKey, String baseUrl, Duration timeout) {
        super(timeout);
        this.apiKey = apiKey;
        this.baseUrl = stripTrailingSlash(baseUrl);
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    /** Headers extras especificos do provider (ex.: OpenRouter recomenda HTTP-Referer/X-Title). */
    protected Map<String, String> extraHeaders() {
        return Map.of();
    }

    @Override
    public boolean health() {
        try {
            HttpRequest request = baseRequest("/models").timeout(timeout).GET().build();
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
        HttpRequest request = baseRequest("/models").timeout(timeout).GET().build();
        HttpResponse<String> response = send(request);
        checkStatus(response.statusCode(), response.body());

        Object parsed = parseBody(response.body());
        List<String> names = new ArrayList<>();
        if (parsed instanceof Map<?, ?> root && root.get("data") instanceof List<?> models) {
            for (Object m : models) {
                if (m instanceof Map<?, ?> model && model.get("id") instanceof String id) {
                    names.add(id);
                }
            }
        }
        return names;
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        String json = JsonWriter.write(buildRequestBody(request, false));
        HttpRequest httpRequest = baseRequest("/chat/completions")
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = send(httpRequest);
        checkStatus(response.statusCode(), response.body());

        Object parsed = parseBody(response.body());
        if (!(parsed instanceof Map<?, ?> root)) {
            throw new ProviderException.UnexpectedResponse("Resposta de " + providerName() + " em formato inesperado.");
        }
        return toChatResponse(root);
    }

    @Override
    protected void doStream(String requestId, ChatRequest request, Consumer<AiEvent> onEvent, AtomicBoolean cancelled) {
        try {
            String json = JsonWriter.write(buildRequestBody(request, true));
            HttpRequest httpRequest = baseRequest("/chat/completions")
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
            Map<Integer, ToolCallBuilder> toolCallBuilders = new LinkedHashMap<>();
            String finishReason = "stop";
            long promptTokens = 0;
            long completionTokens = 0;

            try (Stream<String> lines = response.body()) {
                for (String line : (Iterable<String>) lines::iterator) {
                    if (cancelled.get()) {
                        onEvent.accept(new AiEvent.Cancelled(requestId));
                        return;
                    }
                    Optional<String> payloadOpt = SseUtil.dataPayload(line);
                    if (payloadOpt.isEmpty()) {
                        continue;
                    }
                    String payload = payloadOpt.get();
                    if (SseUtil.isDone(payload)) {
                        onEvent.accept(new AiEvent.Completed(requestId, new ChatResponse(
                                new ChatMessage(ChatMessage.ROLE_ASSISTANT, fullContent.toString()), finishReason,
                                new ChatUsage(promptTokens, completionTokens), buildToolCalls(toolCallBuilders))));
                        return;
                    }
                    Object parsed;
                    try {
                        parsed = JsonParser.parse(payload);
                    } catch (JsonParser.JsonParseException e) {
                        onEvent.accept(new AiEvent.Failed(requestId, new ProviderException.UnexpectedResponse(
                                "Resposta de " + providerName() + " em formato inesperado.", e)));
                        return;
                    }
                    if (!(parsed instanceof Map<?, ?> chunk)) {
                        continue;
                    }
                    if (chunk.get("error") instanceof Map<?, ?> err && err.get("message") instanceof String msg) {
                        onEvent.accept(new AiEvent.Failed(requestId, new ProviderException.UnexpectedResponse(msg)));
                        return;
                    }
                    if (chunk.get("usage") instanceof Map<?, ?> usageMap) {
                        promptTokens = asLong(usageMap.get("prompt_tokens"));
                        completionTokens = asLong(usageMap.get("completion_tokens"));
                    }
                    if (!(chunk.get("choices") instanceof List<?> choices) || choices.isEmpty()
                            || !(choices.get(0) instanceof Map<?, ?> choice)) {
                        continue;
                    }
                    if (choice.get("finish_reason") instanceof String fr) {
                        finishReason = fr;
                    }
                    if (choice.get("delta") instanceof Map<?, ?> delta) {
                        if (delta.get("content") instanceof String contentDelta && !contentDelta.isEmpty()) {
                            fullContent.append(contentDelta);
                            onEvent.accept(new AiEvent.Chunk(requestId, contentDelta));
                        }
                        if (delta.get("tool_calls") instanceof List<?> deltaToolCalls) {
                            accumulateToolCalls(toolCallBuilders, deltaToolCalls);
                        }
                    }
                }
            }
            // Alguns servidores (proxies OpenAI-compativeis) fecham o stream sem
            // mandar "data: [DONE]" — ainda assim finaliza normalmente com o que
            // foi acumulado, em vez de deixar o turno pendurado sem evento terminal.
            onEvent.accept(new AiEvent.Completed(requestId, new ChatResponse(
                    new ChatMessage(ChatMessage.ROLE_ASSISTANT, fullContent.toString()), finishReason,
                    new ChatUsage(promptTokens, completionTokens), buildToolCalls(toolCallBuilders))));
        } catch (HttpTimeoutException e) {
            onEvent.accept(new AiEvent.Failed(requestId,
                    new ProviderException.Timeout(providerName() + " demorou demais para responder.", e)));
        } catch (ConnectException | ClosedChannelException e) {
            onEvent.accept(new AiEvent.Failed(requestId, connectionError(e)));
        } catch (IOException e) {
            onEvent.accept(new AiEvent.Failed(requestId, new ProviderException.ConnectionError(
                    "Erro de comunicacao com " + providerName() + (e.getMessage() != null ? ": " + e.getMessage() : "."), e)));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            onEvent.accept(new AiEvent.Cancelled(requestId));
        }
    }

    private HttpRequest.Builder baseRequest(String path) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .header("Authorization", "Bearer " + apiKey);
        extraHeaders().forEach(builder::header);
        return builder;
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (HttpTimeoutException e) {
            throw new ProviderException.Timeout(providerName() + " demorou demais para responder.", e);
        } catch (ConnectException | ClosedChannelException e) {
            throw connectionError(e);
        } catch (IOException e) {
            throw new ProviderException.ConnectionError(
                    "Erro de comunicacao com " + providerName() + (e.getMessage() != null ? ": " + e.getMessage() : "."), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ProviderException.ConnectionError("Requisicao a " + providerName() + " interrompida.", e);
        }
    }

    private ProviderException.ConnectionError connectionError(Throwable cause) {
        return new ProviderException.ConnectionError(
                "Nao foi possivel conectar a " + providerName() + ". Verifique sua internet.", cause);
    }

    private void checkStatus(int statusCode, String body) {
        if (statusCode != 200) {
            throw statusToException(statusCode, body);
        }
    }

    private ProviderException statusToException(int statusCode, String body) {
        String detail = extractErrorMessage(body);
        return switch (statusCode) {
            case 401, 403 -> new ProviderException.MissingCredential(
                    "API key invalida ou sem permissao para " + providerName()
                            + (detail != null ? ": " + detail : ". Confira em Configuracoes de IA."));
            case 404 -> new ProviderException.InvalidModel(
                    detail != null ? detail : "Modelo nao encontrado em " + providerName() + ".");
            case 429 -> new ProviderException.ProviderUnavailable(
                    providerName() + " limitou a taxa de requisicoes (429) — tente novamente em instantes.");
            case 500, 502, 503, 504 -> new ProviderException.ProviderUnavailable(
                    providerName() + " nao esta disponivel no momento (HTTP " + statusCode + ").");
            default -> new ProviderException.UnexpectedResponse(
                    providerName() + " respondeu com um erro inesperado (HTTP " + statusCode + ")"
                            + (detail != null ? ": " + detail : "."));
        };
    }

    private String extractErrorMessage(String body) {
        try {
            Object parsed = JsonParser.parse(body);
            if (parsed instanceof Map<?, ?> root && root.get("error") instanceof Map<?, ?> err
                    && err.get("message") instanceof String msg) {
                return msg;
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
            throw new ProviderException.UnexpectedResponse(providerName() + " respondeu em formato inesperado.", e);
        }
    }

    private Map<String, Object> buildRequestBody(ChatRequest request, boolean stream) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", request.model());
        List<Map<String, Object>> messages = new ArrayList<>();
        for (ChatMessage m : request.messages()) {
            messages.add(toOpenAiMessage(m));
        }
        body.put("messages", messages);
        body.put("stream", stream);
        if (stream) {
            body.put("stream_options", Map.of("include_usage", true));
        }
        if (request.temperature() != null) {
            body.put("temperature", request.temperature());
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

    /**
     * Message no formato OpenAI: mensagens {@code tool} carregam
     * {@code tool_call_id} (referencia ao pedido que respondem); mensagens
     * {@code assistant} que pediram tools carregam {@code tool_calls} (com
     * {@code arguments} como STRING JSON-encoded, nao objeto aninhado —
     * diferente do Ollama).
     */
    private Map<String, Object> toOpenAiMessage(ChatMessage m) {
        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("role", m.role());
        msg.put("content", m.content());
        if (m.toolCallId() != null) {
            msg.put("tool_call_id", m.toolCallId());
        }
        if (m.hasToolCalls()) {
            List<Map<String, Object>> toolCallsJson = new ArrayList<>();
            for (ToolCall call : m.toolCalls()) {
                Map<String, Object> function = new LinkedHashMap<>();
                function.put("name", call.name());
                function.put("arguments", JsonWriter.write(call.arguments()));
                Map<String, Object> toolCallJson = new LinkedHashMap<>();
                toolCallJson.put("id", call.id());
                toolCallJson.put("type", "function");
                toolCallJson.put("function", function);
                toolCallsJson.add(toolCallJson);
            }
            msg.put("tool_calls", toolCallsJson);
        }
        return msg;
    }

    private ChatResponse toChatResponse(Map<?, ?> root) {
        if (!(root.get("choices") instanceof List<?> choices) || choices.isEmpty()
                || !(choices.get(0) instanceof Map<?, ?> choice)) {
            throw new ProviderException.UnexpectedResponse("Resposta de " + providerName() + " sem \"choices\".");
        }
        Map<?, ?> messageObj = choice.get("message") instanceof Map<?, ?> m ? m : Map.of();
        String content = messageObj.get("content") instanceof String c ? c : "";
        ChatMessage message = new ChatMessage(ChatMessage.ROLE_ASSISTANT, content);
        String finishReason = choice.get("finish_reason") instanceof String fr ? fr : "stop";
        ChatUsage usage = extractUsage(root);
        List<ToolCall> toolCalls = extractToolCallsNonStream(messageObj);
        return new ChatResponse(message, finishReason, usage, toolCalls);
    }

    private ChatUsage extractUsage(Map<?, ?> root) {
        if (!(root.get("usage") instanceof Map<?, ?> usage)) {
            return ChatUsage.EMPTY;
        }
        return new ChatUsage(asLong(usage.get("prompt_tokens")), asLong(usage.get("completion_tokens")));
    }

    @SuppressWarnings("unchecked")
    private List<ToolCall> extractToolCallsNonStream(Map<?, ?> messageObj) {
        if (!(messageObj.get("tool_calls") instanceof List<?> rawCalls)) {
            return List.of();
        }
        List<ToolCall> calls = new ArrayList<>();
        for (Object raw : rawCalls) {
            if (!(raw instanceof Map<?, ?> call) || !(call.get("function") instanceof Map<?, ?> function)) {
                continue;
            }
            String id = call.get("id") instanceof String i ? i : null;
            String name = function.get("name") instanceof String n ? n : null;
            if (name == null) {
                continue;
            }
            Map<String, Object> arguments = Map.of();
            if (function.get("arguments") instanceof String argsJson && !argsJson.isBlank()) {
                arguments = parseArgumentsJson(argsJson);
            }
            calls.add(new ToolCall(id, name, arguments));
        }
        return calls;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseArgumentsJson(String argsJson) {
        try {
            Object parsedArgs = JsonParser.parse(argsJson);
            if (parsedArgs instanceof Map<?, ?> m) {
                return (Map<String, Object>) m;
            }
        } catch (JsonParser.JsonParseException ignored) {
            // argumentos parciais/invalidos (stream cortado) — tool chamada sem args
        }
        return Map.of();
    }

    private static void accumulateToolCalls(Map<Integer, ToolCallBuilder> builders, List<?> deltaToolCalls) {
        for (Object raw : deltaToolCalls) {
            if (!(raw instanceof Map<?, ?> item)) {
                continue;
            }
            int index = item.get("index") instanceof Number n ? n.intValue() : 0;
            ToolCallBuilder builder = builders.computeIfAbsent(index, k -> new ToolCallBuilder());
            if (item.get("id") instanceof String id) {
                builder.id = id;
            }
            if (item.get("function") instanceof Map<?, ?> function) {
                if (function.get("name") instanceof String name) {
                    builder.name = name;
                }
                if (function.get("arguments") instanceof String argsFragment) {
                    builder.argumentsJson.append(argsFragment);
                }
            }
        }
    }

    private static List<ToolCall> buildToolCalls(Map<Integer, ToolCallBuilder> builders) {
        List<ToolCall> calls = new ArrayList<>();
        for (ToolCallBuilder builder : builders.values()) {
            if (builder.name == null) {
                continue;
            }
            Map<String, Object> arguments = builder.argumentsJson.length() > 0
                    ? parseArgumentsJson(builder.argumentsJson.toString())
                    : Map.of();
            calls.add(new ToolCall(builder.id, builder.name, arguments));
        }
        return calls;
    }

    private static long asLong(Object value) {
        return value instanceof Number n ? n.longValue() : 0L;
    }

    private static final class ToolCallBuilder {
        String id;
        String name;
        final StringBuilder argumentsJson = new StringBuilder();
    }
}
