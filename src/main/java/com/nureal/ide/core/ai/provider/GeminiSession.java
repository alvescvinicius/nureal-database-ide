package com.nureal.ide.core.ai.provider;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * {@link ConversationSession} do Gemini: mantem o historico como uma lista de
 * {@code contents} JA no formato nativo do Gemini, montada uma unica vez a
 * partir do historico generico na criacao da sessao — nunca reconstruida a
 * partir de {@link ChatMessage} a cada rodada. Como o Gemini nao gera id de
 * function call, o {@code callToken} usado em
 * {@link #submitToolResult(String, String)} e o proprio NOME da function
 * (mesma limitacao ja documentada em {@link GeminiProvider}).
 */
final class GeminiSession implements ConversationSession {

    private final GeminiProvider provider;
    private final String model;
    private final Double temperature;
    private final String systemPrompt;
    private final List<ToolSpec> tools;
    private final List<Map<String, Object>> nativeContents;
    private final Consumer<AiEvent> onEvent;
    private final AtomicReference<String> activeRequestId = new AtomicReference<>();

    private volatile Set<String> pendingCallTokens = Set.of();

    GeminiSession(GeminiProvider provider, ChatRequest seed, Consumer<AiEvent> onEvent) {
        this.provider = provider;
        this.model = seed.model();
        this.temperature = seed.temperature();
        this.tools = seed.tools();
        this.onEvent = onEvent;

        String system = null;
        List<Map<String, Object>> contents = new ArrayList<>();
        for (ChatMessage m : seed.messages()) {
            if (ChatMessage.ROLE_SYSTEM.equals(m.role()) && system == null) {
                system = m.content();
                continue;
            }
            contents.add(provider.toNativeContent(m));
        }
        this.systemPrompt = system;
        this.nativeContents = contents;
    }

    @Override
    public void send(String userMessage) {
        nativeContents.add(provider.toNativeContent(new ChatMessage(ChatMessage.ROLE_USER, userMessage)));
        startRound();
    }

    @Override
    public void submitToolResult(String callToken, String content) {
        nativeContents.add(provider.toNativeContent(new ChatMessage(ChatMessage.ROLE_TOOL, content, callToken)));
        Set<String> stillPending = new HashSet<>(pendingCallTokens);
        stillPending.remove(callToken);
        pendingCallTokens = stillPending;
        if (stillPending.isEmpty()) {
            startRound();
        }
    }

    @Override
    public void cancel() {
        String requestId = activeRequestId.get();
        if (requestId != null) {
            provider.cancel(requestId);
        }
    }

    private void startRound() {
        Map<String, Object> body = provider.nativeRequestBody(systemPrompt, List.copyOf(nativeContents), temperature,
                tools);
        String requestId = provider.streamNative(model, body, this::handleProviderEvent);
        activeRequestId.set(requestId);
    }

    private void handleProviderEvent(AiEvent event) {
        if (event instanceof AiEvent.Completed completed && completed.response().hasToolCalls()) {
            List<ToolCall> calls = completed.response().toolCalls();
            String accompanyingText = completed.response().message().content();
            nativeContents.add(provider.toNativeContent(
                    new ChatMessage(ChatMessage.ROLE_ASSISTANT, accompanyingText, null, calls)));
            Set<String> tokens = new HashSet<>();
            for (ToolCall call : calls) {
                tokens.add(call.id());
            }
            pendingCallTokens = tokens;
            onEvent.accept(new AiEvent.ToolCallsRequested(event.requestId(), calls, accompanyingText));
            return;
        }
        onEvent.accept(event);
    }
}
