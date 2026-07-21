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
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Stream;

import com.nureal.ide.core.json.JsonParser;
import com.nureal.ide.core.json.JsonWriter;

/**
 * Gemini (Google Generative Language API) — o formato mais distante dos outros
 * providers:
 * <ul>
 *   <li>{@code contents:[{role:"user"|"model", parts:[{text:"..."}]}]} em vez de
 *       {@code messages} (role {@code model} no lugar de {@code assistant}).</li>
 *   <li>System prompt num campo {@code systemInstruction} de topo (extraido da
 *       primeira mensagem de role {@code system}, como no Claude).</li>
 *   <li>Tools como {@code {"functionDeclarations":[{name,description,parameters}]}}.</li>
 *   <li>O modelo vai NA URL ({@code /v1beta/models/{model}:generateContent} ou
 *       {@code :streamGenerateContent?alt=sse}), nao no corpo.</li>
 *   <li>{@code functionCall}/{@code functionResponse} sao identificados por NOME, nao
 *       por um id (Gemini nao gera id de tool call) — usamos o proprio nome da
 *       function como {@link ToolCall#id()} pra manter a mesma forma de
 *       {@link ChatMessage#toolCallId()} usada pelos outros providers.</li>
 * </ul>
 */
public final class GeminiProvider extends AbstractStreamingProvider {

    private static final String BASE_URL = "https://generativelanguage.googleapis.com";

    private final String apiKey;
    private final String baseUrl;

    public GeminiProvider(String apiKey, Duration timeout) {
        this(apiKey, BASE_URL, timeout);
    }

    /** Pacote-visivel: usado nos testes pra apontar pra um servidor fake local. */
    GeminiProvider(String apiKey, String baseUrl, Duration timeout) {
        super(timeout);
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
    }

    @Override
    protected String providerName() {
        return "Gemini";
    }

    @Override
    public boolean health() {
        try {
            HttpRequest request = baseRequest("/v1beta/models").timeout(timeout).GET().build();
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
        HttpRequest request = baseRequest("/v1beta/models").timeout(timeout).GET().build();
        HttpResponse<String> response = send(request);
        checkStatus(response.statusCode(), response.body());

        Object parsed = parseBody(response.body());
        List<String> names = new ArrayList<>();
        if (parsed instanceof Map<?, ?> root && root.get("models") instanceof List<?> models) {
            for (Object m : models) {
                if (m instanceof Map<?, ?> model && model.get("name") instanceof String name) {
                    names.add(name.startsWith("models/") ? name.substring("models/".length()) : name);
                }
            }
        }
        return names;
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        String json = JsonWriter.write(buildRequestBody(request));
        HttpRequest httpRequest = baseRequest("/v1beta/models/" + request.model() + ":generateContent")
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = send(httpRequest);
        checkStatus(response.statusCode(), response.body());

        Object parsed = parseBody(response.body());
        if (!(parsed instanceof Map<?, ?> root)) {
            throw new ProviderException.UnexpectedResponse("Resposta do Gemini em formato inesperado.");
        }
        return toChatResponse(root);
    }

    @Override
    protected void doStream(String requestId, ChatRequest request, Consumer<AiEvent> onEvent, AtomicBoolean cancelled) {
        doStreamNative(requestId, request.model(), buildRequestBody(request), onEvent, cancelled);
    }

    @Override
    public ConversationSession createSession(ChatRequest seed, Consumer<AiEvent> onEvent) {
        return new GeminiSession(this, seed, onEvent);
    }

    /**
     * Dispara uma rodada com um corpo de requisicao JA no formato nativo do
     * Gemini (usado por {@link GeminiSession}, que mantem seu proprio
     * historico de {@code contents} entre rodadas em vez de reconstruir a
     * partir de {@link ChatMessage} a cada chamada). Reusa o mesmo
     * executor/cancelamento de {@link #stream}, via
     * {@link AbstractStreamingProvider#submitStreamTask}.
     */
    String streamNative(String model, Map<String, Object> nativeBody, Consumer<AiEvent> onEvent) {
        return submitStreamTask(onEvent,
                (requestId, ev, cancelled) -> doStreamNative(requestId, model, nativeBody, ev, cancelled));
    }

    /** Monta o corpo de requisicao do Gemini a partir de {@code contents} JA no formato nativo (ver {@link #streamNative}). */
    Map<String, Object> nativeRequestBody(String systemPrompt, List<Map<String, Object>> nativeContents,
            Double temperature, List<ToolSpec> tools) {
        Map<String, Object> body = new LinkedHashMap<>();
        if (systemPrompt != null) {
            body.put("systemInstruction", Map.of("parts", List.of(Map.of("text", systemPrompt))));
        }
        body.put("contents", nativeContents);
        if (temperature != null) {
            body.put("generationConfig", Map.of("temperature", temperature));
        }
        if (!tools.isEmpty()) {
            List<Map<String, Object>> functionDeclarations = new ArrayList<>();
            for (ToolSpec tool : tools) {
                Map<String, Object> fn = new LinkedHashMap<>();
                fn.put("name", tool.name());
                fn.put("description", tool.description());
                fn.put("parameters", tool.parametersSchema());
                functionDeclarations.add(fn);
            }
            body.put("tools", List.of(Map.of("functionDeclarations", functionDeclarations)));
        }
        return body;
    }

    /** Converte uma mensagem generica pro {@code content} nativo (ver {@link #toGeminiContent}) — usado por {@link GeminiSession}. */
    Map<String, Object> toNativeContent(ChatMessage m) {
        return toGeminiContent(m);
    }

    private void doStreamNative(String requestId, String model, Map<String, Object> requestBody,
            Consumer<AiEvent> onEvent, AtomicBoolean cancelled) {
        try {
            String json = JsonWriter.write(requestBody);
            HttpRequest httpRequest = baseRequest(
                    "/v1beta/models/" + model + ":streamGenerateContent?alt=sse")
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
            List<ToolCall> toolCalls = new ArrayList<>();
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
                    Object parsed;
                    try {
                        parsed = JsonParser.parse(payloadOpt.get());
                    } catch (JsonParser.JsonParseException e) {
                        onEvent.accept(new AiEvent.Failed(requestId,
                                new ProviderException.UnexpectedResponse("Resposta do Gemini em formato inesperado.", e)));
                        return;
                    }
                    if (!(parsed instanceof Map<?, ?> chunk)) {
                        continue;
                    }
                    if (chunk.get("error") instanceof Map<?, ?> err && err.get("message") instanceof String msg) {
                        onEvent.accept(new AiEvent.Failed(requestId, new ProviderException.UnexpectedResponse(msg)));
                        return;
                    }
                    if (chunk.get("usageMetadata") instanceof Map<?, ?> usage) {
                        promptTokens = asLong(usage.get("promptTokenCount"));
                        completionTokens = asLong(usage.get("candidatesTokenCount"));
                    }
                    if (!(chunk.get("candidates") instanceof List<?> candidates) || candidates.isEmpty()
                            || !(candidates.get(0) instanceof Map<?, ?> candidate)) {
                        continue;
                    }
                    if (candidate.get("finishReason") instanceof String fr) {
                        finishReason = fr.toLowerCase(Locale.ROOT);
                    }
                    if (candidate.get("content") instanceof Map<?, ?> content
                            && content.get("parts") instanceof List<?> parts) {
                        for (Object rawPart : parts) {
                            if (!(rawPart instanceof Map<?, ?> part)) {
                                continue;
                            }
                            if (part.get("text") instanceof String textDelta && !textDelta.isEmpty()) {
                                fullText.append(textDelta);
                                onEvent.accept(new AiEvent.Chunk(requestId, textDelta));
                            } else if (part.get("functionCall") instanceof Map<?, ?> fc
                                    && fc.get("name") instanceof String name) {
                                toolCalls.add(new ToolCall(name, name, extractArgs(fc.get("args"))));
                            }
                        }
                    }
                }
            }
            // Gemini nao manda um marcador terminal explicito no streaming (o
            // proprio fim da conexao HTTP encerra o loop) — sempre finaliza com o
            // que foi acumulado.
            onEvent.accept(new AiEvent.Completed(requestId, new ChatResponse(
                    new ChatMessage(ChatMessage.ROLE_ASSISTANT, fullText.toString()), finishReason,
                    new ChatUsage(promptTokens, completionTokens), toolCalls)));
        } catch (HttpTimeoutException e) {
            onEvent.accept(new AiEvent.Failed(requestId,
                    new ProviderException.Timeout("Gemini demorou demais para responder.", e)));
        } catch (ConnectException | ClosedChannelException e) {
            onEvent.accept(new AiEvent.Failed(requestId, connectionError(e)));
        } catch (IOException e) {
            onEvent.accept(new AiEvent.Failed(requestId, new ProviderException.ConnectionError(
                    "Erro de comunicacao com Gemini" + (e.getMessage() != null ? ": " + e.getMessage() : "."), e)));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            onEvent.accept(new AiEvent.Cancelled(requestId));
        }
    }

    private HttpRequest.Builder baseRequest(String pathAndQuery) {
        return HttpRequest.newBuilder(URI.create(baseUrl + pathAndQuery))
                .header("x-goog-api-key", apiKey);
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (HttpTimeoutException e) {
            throw new ProviderException.Timeout("Gemini demorou demais para responder.", e);
        } catch (ConnectException | ClosedChannelException e) {
            throw connectionError(e);
        } catch (IOException e) {
            throw new ProviderException.ConnectionError(
                    "Erro de comunicacao com Gemini" + (e.getMessage() != null ? ": " + e.getMessage() : "."), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ProviderException.ConnectionError("Requisicao ao Gemini interrompida.", e);
        }
    }

    private ProviderException.ConnectionError connectionError(Throwable cause) {
        return new ProviderException.ConnectionError("Nao foi possivel conectar ao Gemini. Verifique sua internet.",
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
            case 401, 403 -> new ProviderException.MissingCredential(
                    "API key invalida ou sem permissao para Gemini"
                            + (detail != null ? ": " + detail : ". Confira em Configuracoes de IA."));
            case 404 -> new ProviderException.InvalidModel(
                    detail != null ? detail : "Modelo nao encontrado no Gemini.");
            case 429 -> new ProviderException.ProviderUnavailable(
                    "Gemini limitou a taxa de requisicoes (429) — tente novamente em instantes.");
            case 500, 503 -> new ProviderException.ProviderUnavailable(
                    "Gemini nao esta disponivel no momento (HTTP " + statusCode + ").");
            default -> new ProviderException.UnexpectedResponse(
                    "Gemini respondeu com um erro inesperado (HTTP " + statusCode + ")"
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
            throw new ProviderException.UnexpectedResponse("Gemini respondeu em formato inesperado.", e);
        }
    }

    private Map<String, Object> buildRequestBody(ChatRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        String systemPrompt = null;
        List<Map<String, Object>> contents = new ArrayList<>();
        for (ChatMessage m : request.messages()) {
            if (ChatMessage.ROLE_SYSTEM.equals(m.role()) && systemPrompt == null) {
                systemPrompt = m.content();
                continue;
            }
            contents.add(toGeminiContent(m));
        }
        if (systemPrompt != null) {
            body.put("systemInstruction", Map.of("parts", List.of(Map.of("text", systemPrompt))));
        }
        body.put("contents", contents);
        if (request.temperature() != null) {
            body.put("generationConfig", Map.of("temperature", request.temperature()));
        }
        if (!request.tools().isEmpty()) {
            List<Map<String, Object>> functionDeclarations = new ArrayList<>();
            for (ToolSpec tool : request.tools()) {
                Map<String, Object> fn = new LinkedHashMap<>();
                fn.put("name", tool.name());
                fn.put("description", tool.description());
                fn.put("parameters", tool.parametersSchema());
                functionDeclarations.add(fn);
            }
            body.put("tools", List.of(Map.of("functionDeclarations", functionDeclarations)));
        }
        return body;
    }

    /**
     * Mensagens {@code tool} viram um {@code functionResponse} (referenciado por
     * NOME — {@code toolCallId} guarda o nome, ver javadoc da classe); mensagens
     * {@code assistant} com tool calls viram parts {@code functionCall}.
     */
    private Map<String, Object> toGeminiContent(ChatMessage m) {
        if (ChatMessage.ROLE_TOOL.equals(m.role())) {
            Map<String, Object> functionResponse = new LinkedHashMap<>();
            functionResponse.put("name", m.toolCallId());
            functionResponse.put("response", Map.of("content", m.content()));
            return Map.of("role", "user", "parts", List.of(Map.of("functionResponse", functionResponse)));
        }
        if (ChatMessage.ROLE_ASSISTANT.equals(m.role()) && m.hasToolCalls()) {
            List<Map<String, Object>> parts = new ArrayList<>();
            if (!m.content().isBlank()) {
                parts.add(Map.of("text", m.content()));
            }
            for (ToolCall call : m.toolCalls()) {
                parts.add(Map.of("functionCall", Map.of("name", call.name(), "args", call.arguments())));
            }
            return Map.of("role", "model", "parts", parts);
        }
        String role = ChatMessage.ROLE_ASSISTANT.equals(m.role()) ? "model" : "user";
        return Map.of("role", role, "parts", List.of(Map.of("text", m.content())));
    }

    private ChatResponse toChatResponse(Map<?, ?> root) {
        if (!(root.get("candidates") instanceof List<?> candidates) || candidates.isEmpty()
                || !(candidates.get(0) instanceof Map<?, ?> candidate)) {
            throw new ProviderException.UnexpectedResponse("Resposta do Gemini sem \"candidates\".");
        }
        StringBuilder text = new StringBuilder();
        List<ToolCall> toolCalls = new ArrayList<>();
        if (candidate.get("content") instanceof Map<?, ?> content && content.get("parts") instanceof List<?> parts) {
            for (Object rawPart : parts) {
                if (!(rawPart instanceof Map<?, ?> part)) {
                    continue;
                }
                if (part.get("text") instanceof String t) {
                    text.append(t);
                } else if (part.get("functionCall") instanceof Map<?, ?> fc && fc.get("name") instanceof String name) {
                    toolCalls.add(new ToolCall(name, name, extractArgs(fc.get("args"))));
                }
            }
        }
        ChatMessage message = new ChatMessage(ChatMessage.ROLE_ASSISTANT, text.toString());
        String finishReason = candidate.get("finishReason") instanceof String fr ? fr.toLowerCase(Locale.ROOT) : "stop";
        ChatUsage usage = extractUsage(root.get("usageMetadata"));
        return new ChatResponse(message, finishReason, usage, toolCalls);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> extractArgs(Object rawArgs) {
        return rawArgs instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }

    private ChatUsage extractUsage(Object usageObj) {
        if (!(usageObj instanceof Map<?, ?> usage)) {
            return ChatUsage.EMPTY;
        }
        return new ChatUsage(asLong(usage.get("promptTokenCount")), asLong(usage.get("candidatesTokenCount")));
    }

    private static long asLong(Object value) {
        return value instanceof Number n ? n.longValue() : 0L;
    }
}
