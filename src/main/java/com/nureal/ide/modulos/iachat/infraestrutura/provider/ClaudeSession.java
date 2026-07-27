package com.nureal.ide.modulos.iachat.infraestrutura.provider;
import com.nureal.ide.modulos.iachat.dominio.entidades.ChatRequest;
import com.nureal.ide.modulos.iachat.dominio.contratos.ConversationSession;
import com.nureal.ide.modulos.iachat.dominio.entidades.ToolCall;
import com.nureal.ide.modulos.iachat.dominio.entidades.ToolSpec;
import com.nureal.ide.modulos.iachat.dominio.entidades.AiEvent;
import com.nureal.ide.modulos.iachat.dominio.entidades.ChatMessage;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * {@link ConversationSession} da Claude: mantem o historico como uma lista de
 * mensagens JA no formato nativo da Anthropic Messages API (content blocks
 * com {@code tool_use}/{@code tool_result} usando os ids REAIS que a Claude
 * devolve), montada uma unica vez a partir do historico generico na criacao
 * da sessao — nunca reconstruida a partir de {@link ChatMessage} a cada
 * rodada de tool-calling, ao contrario do {@link GenericSession}.
 */
final class ClaudeSession implements ConversationSession {

    private final ClaudeProvider provider;
    private final String model;
    private final Double temperature;
    private final String systemPrompt;
    private final List<ToolSpec> tools;
    private final List<Map<String, Object>> nativeMessages;
    private final Consumer<AiEvent> onEvent;
    private final AtomicReference<String> activeRequestId = new AtomicReference<>();

    private volatile Set<String> pendingCallTokens = Set.of();

    ClaudeSession(ClaudeProvider provider, ChatRequest seed, Consumer<AiEvent> onEvent) {
        this.provider = provider;
        this.model = seed.model();
        this.temperature = seed.temperature();
        this.tools = seed.tools();
        this.onEvent = onEvent;

        String system = null;
        List<Map<String, Object>> messages = new ArrayList<>();
        for (ChatMessage m : seed.messages()) {
            if (ChatMessage.ROLE_SYSTEM.equals(m.role()) && system == null) {
                system = m.content();
                continue;
            }
            messages.add(provider.toNativeMessage(m));
        }
        this.systemPrompt = system;
        this.nativeMessages = messages;
    }

    @Override
    public void send(String userMessage) {
        nativeMessages.add(provider.toNativeMessage(new ChatMessage(ChatMessage.ROLE_USER, userMessage)));
        startRound();
    }

    @Override
    public void submitToolResult(String callToken, String content) {
        nativeMessages.add(provider.toNativeMessage(new ChatMessage(ChatMessage.ROLE_TOOL, content, callToken)));
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
        Map<String, Object> body = provider.nativeRequestBody(model, temperature, systemPrompt,
                List.copyOf(nativeMessages), tools);
        String requestId = provider.streamNative(body, this::handleProviderEvent);
        activeRequestId.set(requestId);
    }

    private void handleProviderEvent(AiEvent event) {
        if (event instanceof AiEvent.Completed completed && completed.response().hasToolCalls()) {
            List<ToolCall> calls = completed.response().toolCalls();
            String accompanyingText = completed.response().message().content();
            nativeMessages.add(provider.toNativeMessage(
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
