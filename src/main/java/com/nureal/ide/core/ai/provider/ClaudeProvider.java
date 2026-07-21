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
 * Claude (Anthropic Messages API). Diferencas reais do formato OpenAI, nao so
 * cosmeticas:
 * <ul>
 *   <li>O system prompt vai num campo {@code system} de topo — nunca como mensagem
 *       de role {@code system} dentro de {@code messages} (Claude so aceita
 *       {@code user}/{@code assistant} ali).</li>
 *   <li>Tools no formato {@code {"name":...,"description":...,"input_schema":{...}}}.</li>
 *   <li>Resultado de tool volta como uma mensagem {@code user} com um content block
 *       {@code tool_result} (referenciando o {@code tool_use_id}), nao uma mensagem de
 *       role {@code tool}.</li>
 *   <li>Streaming via SSE com EVENTOS tipados (nao um {@code delta.content} simples):
 *       {@code message_start} → {@code content_block_start}/{@code _delta}/{@code _stop}
 *       (texto ou {@code tool_use}, com argumentos chegando fragmentados como
 *       {@code input_json_delta}) → {@code message_delta} → {@code message_stop}.</li>
 * </ul>
 */
public final class ClaudeProvider extends AbstractStreamingProvider {

    private static final String BASE_URL = "https://api.anthropic.com";
    private static final String ANTHROPIC_VERSION = "2023-06-01";
    private static final int DEFAULT_MAX_TOKENS = 4096;

    private final String apiKey;
    private final String baseUrl;

    public ClaudeProvider(String apiKey, Duration timeout) {
        this(apiKey, BASE_URL, timeout);
    }

    /** Pacote-visivel: usado nos testes pra apontar pra um servidor fake local. */
    ClaudeProvider(String apiKey, String baseUrl, Duration timeout) {
        super(timeout);
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
    }

    @Override
    protected String providerName() {
        return "Claude";
    }

    @Override
    public boolean health() {
        try {
            HttpRequest request = baseRequest("/v1/models").timeout(timeout).GET().build();
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
        HttpRequest request = baseRequest("/v1/models").timeout(timeout).GET().build();
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
        HttpRequest httpRequest = baseRequest("/v1/messages")
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = send(httpRequest);
        checkStatus(response.statusCode(), response.body());

        Object parsed = parseBody(response.body());
        if (!(parsed instanceof Map<?, ?> root)) {
            throw new ProviderException.UnexpectedResponse("Resposta da Claude em formato inesperado.");
        }
        return toChatResponse(root);
    }

    @Override
    protected void doStream(String requestId, ChatRequest request, Consumer<AiEvent> onEvent, AtomicBoolean cancelled) {
        try {
            String json = JsonWriter.write(buildRequestBody(request, true));
            HttpRequest httpRequest = baseRequest("/v1/messages")
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

            StringBuilder fullText = new StringBuilder();
            Map<Integer, ToolUseBuilder> toolUseBuilders = new LinkedHashMap<>();
            String finishReason = "end_turn";
            long inputTokens = 0;
            long outputTokens = 0;

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
                    Object parsed;
                    try {
                        parsed = JsonParser.parse(payloadOpt.get());
                    } catch (JsonParser.JsonParseException e) {
                        onEvent.accept(new AiEvent.Failed(requestId,
                                new ProviderException.UnexpectedResponse("Resposta da Claude em formato inesperado.", e)));
                        return;
                    }
                    if (!(parsed instanceof Map<?, ?> event)) {
                        continue;
                    }
                    String type = event.get("type") instanceof String t ? t : "";
                    switch (type) {
                        case "message_start" -> {
                            if (event.get("message") instanceof Map<?, ?> message
                                    && message.get("usage") instanceof Map<?, ?> usage) {
                                inputTokens = asLong(usage.get("input_tokens"));
                            }
                        }
                        case "content_block_start" -> {
                            int index = event.get("index") instanceof Number n ? n.intValue() : 0;
                            if (event.get("content_block") instanceof Map<?, ?> block
                                    && "tool_use".equals(block.get("type"))) {
                                ToolUseBuilder builder = new ToolUseBuilder();
                                builder.id = block.get("id") instanceof String id ? id : null;
                                builder.name = block.get("name") instanceof String name ? name : null;
                                toolUseBuilders.put(index, builder);
                            }
                        }
                        case "content_block_delta" -> {
                            int index = event.get("index") instanceof Number n ? n.intValue() : 0;
                            if (event.get("delta") instanceof Map<?, ?> delta) {
                                String deltaType = delta.get("type") instanceof String dt ? dt : "";
                                if ("text_delta".equals(deltaType) && delta.get("text") instanceof String text) {
                                    fullText.append(text);
                                    onEvent.accept(new AiEvent.Chunk(requestId, text));
                                } else if ("input_json_delta".equals(deltaType)
                                        && delta.get("partial_json") instanceof String partial) {
                                    ToolUseBuilder builder = toolUseBuilders.get(index);
                                    if (builder != null) {
                                        builder.inputJson.append(partial);
                                    }
                                }
                            }
                        }
                        case "message_delta" -> {
                            if (event.get("delta") instanceof Map<?, ?> delta
                                    && delta.get("stop_reason") instanceof String sr) {
                                finishReason = sr;
                            }
                            if (event.get("usage") instanceof Map<?, ?> usage) {
                                outputTokens = asLong(usage.get("output_tokens"));
                            }
                        }
                        case "message_stop" -> {
                            onEvent.accept(new AiEvent.Completed(requestId, new ChatResponse(
                                    new ChatMessage(ChatMessage.ROLE_ASSISTANT, fullText.toString()), finishReason,
                                    new ChatUsage(inputTokens, outputTokens), buildToolCalls(toolUseBuilders))));
                            return;
                        }
                        case "error" -> {
                            String msg = event.get("error") instanceof Map<?, ?> err
                                    && err.get("message") instanceof String m ? m : "Erro desconhecido na Claude.";
                            onEvent.accept(new AiEvent.Failed(requestId, new ProviderException.UnexpectedResponse(msg)));
                            return;
                        }
                        default -> {
                            // content_block_stop, ping etc. — nada a fazer
                        }
                    }
                }
            }
            // Stream terminou sem "message_stop" explicito — ainda finaliza com o
            // que foi acumulado, em vez de deixar o turno pendurado.
            onEvent.accept(new AiEvent.Completed(requestId, new ChatResponse(
                    new ChatMessage(ChatMessage.ROLE_ASSISTANT, fullText.toString()), finishReason,
                    new ChatUsage(inputTokens, outputTokens), buildToolCalls(toolUseBuilders))));
        } catch (HttpTimeoutException e) {
            onEvent.accept(new AiEvent.Failed(requestId,
                    new ProviderException.Timeout("Claude demorou demais para responder.", e)));
        } catch (ConnectException | ClosedChannelException e) {
            onEvent.accept(new AiEvent.Failed(requestId, connectionError(e)));
        } catch (IOException e) {
            onEvent.accept(new AiEvent.Failed(requestId, new ProviderException.ConnectionError(
                    "Erro de comunicacao com Claude" + (e.getMessage() != null ? ": " + e.getMessage() : "."), e)));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            onEvent.accept(new AiEvent.Cancelled(requestId));
        }
    }

    private HttpRequest.Builder baseRequest(String path) {
        return HttpRequest.newBuilder(URI.create(baseUrl + path))
                .header("x-api-key", apiKey)
                .header("anthropic-version", ANTHROPIC_VERSION);
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (HttpTimeoutException e) {
            throw new ProviderException.Timeout("Claude demorou demais para responder.", e);
        } catch (ConnectException | ClosedChannelException e) {
            throw connectionError(e);
        } catch (IOException e) {
            throw new ProviderException.ConnectionError(
                    "Erro de comunicacao com Claude" + (e.getMessage() != null ? ": " + e.getMessage() : "."), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ProviderException.ConnectionError("Requisicao a Claude interrompida.", e);
        }
    }

    private ProviderException.ConnectionError connectionError(Throwable cause) {
        return new ProviderException.ConnectionError("Nao foi possivel conectar a Claude. Verifique sua internet.",
                cause);
    }

    private void checkStatus(int statusCode, String body) {
        if (statusCode != 200) {
            throw statusToException(statusCode, body);
        }
    }

    private ProviderException statusToException(int statusCode, String body) {
        String detail = extractErrorMessage(body);
        return switch (statusCode) {
            case 401 -> new ProviderException.MissingCredential(
                    "API key invalida para Claude" + (detail != null ? ": " + detail : ". Confira em Configuracoes de IA."));
            case 404 -> new ProviderException.InvalidModel(
                    detail != null ? detail : "Modelo nao encontrado na Claude.");
            case 429 -> new ProviderException.ProviderUnavailable(
                    "Claude limitou a taxa de requisicoes (429) — tente novamente em instantes.");
            case 500, 502, 503, 529 -> new ProviderException.ProviderUnavailable(
                    "Claude nao esta disponivel no momento (HTTP " + statusCode + ").");
            default -> new ProviderException.UnexpectedResponse(
                    "Claude respondeu com um erro inesperado (HTTP " + statusCode + ")"
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
            throw new ProviderException.UnexpectedResponse("Claude respondeu em formato inesperado.", e);
        }
    }

    private Map<String, Object> buildRequestBody(ChatRequest request, boolean stream) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", request.model());
        body.put("max_tokens", DEFAULT_MAX_TOKENS);

        String systemPrompt = null;
        List<Map<String, Object>> messages = new ArrayList<>();
        for (ChatMessage m : request.messages()) {
            if (ChatMessage.ROLE_SYSTEM.equals(m.role()) && systemPrompt == null) {
                systemPrompt = m.content();
                continue;
            }
            messages.add(toClaudeMessage(m));
        }
        if (systemPrompt != null) {
            body.put("system", systemPrompt);
        }
        body.put("messages", messages);
        body.put("stream", stream);
        if (request.temperature() != null) {
            body.put("temperature", request.temperature());
        }
        if (!request.tools().isEmpty()) {
            List<Map<String, Object>> tools = new ArrayList<>();
            for (ToolSpec tool : request.tools()) {
                Map<String, Object> t = new LinkedHashMap<>();
                t.put("name", tool.name());
                t.put("description", tool.description());
                t.put("input_schema", tool.parametersSchema());
                tools.add(t);
            }
            body.put("tools", tools);
        }
        return body;
    }

    /**
     * Mensagens {@code tool} viram uma mensagem {@code user} com um bloco
     * {@code tool_result}; mensagens {@code assistant} com tool calls viram blocos
     * {@code tool_use} (mais um bloco {@code text} se houver conteudo) — Claude nao
     * tem role {@code tool} nem um campo {@code tool_calls} separado, tudo e content
     * block dentro de {@code user}/{@code assistant}.
     */
    private Map<String, Object> toClaudeMessage(ChatMessage m) {
        if (ChatMessage.ROLE_TOOL.equals(m.role())) {
            Map<String, Object> block = new LinkedHashMap<>();
            block.put("type", "tool_result");
            block.put("tool_use_id", m.toolCallId());
            block.put("content", m.content());
            return Map.of("role", "user", "content", List.of(block));
        }
        if (ChatMessage.ROLE_ASSISTANT.equals(m.role()) && m.hasToolCalls()) {
            List<Map<String, Object>> blocks = new ArrayList<>();
            if (!m.content().isBlank()) {
                blocks.add(Map.of("type", "text", "text", m.content()));
            }
            for (ToolCall call : m.toolCalls()) {
                Map<String, Object> block = new LinkedHashMap<>();
                block.put("type", "tool_use");
                block.put("id", call.id());
                block.put("name", call.name());
                block.put("input", call.arguments());
                blocks.add(block);
            }
            return Map.of("role", "assistant", "content", blocks);
        }
        String role = ChatMessage.ROLE_ASSISTANT.equals(m.role()) ? "assistant" : "user";
        return Map.of("role", role, "content", m.content());
    }

    private ChatResponse toChatResponse(Map<?, ?> root) {
        if (!(root.get("content") instanceof List<?> contentBlocks)) {
            throw new ProviderException.UnexpectedResponse("Resposta da Claude sem \"content\".");
        }
        StringBuilder text = new StringBuilder();
        List<ToolCall> toolCalls = new ArrayList<>();
        for (Object raw : contentBlocks) {
            if (!(raw instanceof Map<?, ?> block)) {
                continue;
            }
            String type = block.get("type") instanceof String t ? t : "";
            if ("text".equals(type) && block.get("text") instanceof String txt) {
                text.append(txt);
            } else if ("tool_use".equals(type) && block.get("name") instanceof String name) {
                String id = block.get("id") instanceof String i ? i : null;
                Map<String, Object> input = extractInput(block.get("input"));
                toolCalls.add(new ToolCall(id, name, input));
            }
        }
        ChatMessage message = new ChatMessage(ChatMessage.ROLE_ASSISTANT, text.toString());
        String finishReason = root.get("stop_reason") instanceof String sr ? sr : "end_turn";
        ChatUsage usage = extractUsage(root.get("usage"));
        return new ChatResponse(message, finishReason, usage, toolCalls);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> extractInput(Object rawInput) {
        return rawInput instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }

    private ChatUsage extractUsage(Object usageObj) {
        if (!(usageObj instanceof Map<?, ?> usage)) {
            return ChatUsage.EMPTY;
        }
        return new ChatUsage(asLong(usage.get("input_tokens")), asLong(usage.get("output_tokens")));
    }

    @SuppressWarnings("unchecked")
    private static List<ToolCall> buildToolCalls(Map<Integer, ToolUseBuilder> builders) {
        List<ToolCall> calls = new ArrayList<>();
        for (ToolUseBuilder builder : builders.values()) {
            if (builder.name == null) {
                continue;
            }
            Map<String, Object> input = Map.of();
            if (builder.inputJson.length() > 0) {
                try {
                    Object parsed = JsonParser.parse(builder.inputJson.toString());
                    if (parsed instanceof Map<?, ?> m) {
                        input = (Map<String, Object>) m;
                    }
                } catch (JsonParser.JsonParseException ignored) {
                    // argumentos parciais/invalidos (stream cortado) — tool chamada sem input
                }
            }
            calls.add(new ToolCall(builder.id, builder.name, input));
        }
        return calls;
    }

    private static long asLong(Object value) {
        return value instanceof Number n ? n.longValue() : 0L;
    }

    private static final class ToolUseBuilder {
        String id;
        String name;
        final StringBuilder inputJson = new StringBuilder();
    }
}
