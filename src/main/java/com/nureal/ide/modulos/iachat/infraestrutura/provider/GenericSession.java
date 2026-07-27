package com.nureal.ide.modulos.iachat.infraestrutura.provider;
import com.nureal.ide.modulos.iachat.dominio.contratos.LLMProvider;
import com.nureal.ide.modulos.iachat.dominio.entidades.ChatRequest;
import com.nureal.ide.modulos.iachat.dominio.contratos.ConversationSession;
import com.nureal.ide.modulos.iachat.dominio.entidades.ToolCall;
import com.nureal.ide.modulos.iachat.dominio.entidades.ToolSpec;
import com.nureal.ide.modulos.iachat.dominio.entidades.AiEvent;
import com.nureal.ide.modulos.iachat.aplicacao.DefaultAgent;
import com.nureal.ide.modulos.iachat.dominio.entidades.ChatMessage;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * {@link ConversationSession} padrao, usada por qualquer {@link LLMProvider}
 * que nao sobrescreva {@link LLMProvider#createSession} (hoje: OpenAI,
 * OpenRouter, Ollama — os tres toleram bem o formato generico de
 * {@link ChatMessage}, ja que e essencialmente o formato nativo da OpenAI).
 * E exatamente a logica que antes vivia dentro do {@code DefaultAgent}: monta
 * o historico como uma {@link List} de {@link ChatMessage} (incluindo as
 * mensagens {@code assistant}/{@code tool} de uma rodada de tool-calling) e
 * reenvia via {@link LLMProvider#stream} a cada rodada.
 */
public final class GenericSession implements ConversationSession {

    private final LLMProvider provider;
    private final String model;
    private final Double temperature;
    private final List<ToolSpec> tools;
    private final List<ChatMessage> messages;
    private final Consumer<AiEvent> onEvent;
    private final AtomicReference<String> activeRequestId = new AtomicReference<>();

    private volatile Set<String> pendingCallTokens = Set.of();

    public GenericSession(LLMProvider provider, ChatRequest seed, Consumer<AiEvent> onEvent) {
        this.provider = provider;
        this.model = seed.model();
        this.temperature = seed.temperature();
        this.tools = seed.tools();
        this.messages = new ArrayList<>(seed.messages());
        this.onEvent = onEvent;
    }

    @Override
    public void send(String userMessage) {
        messages.add(new ChatMessage(ChatMessage.ROLE_USER, userMessage));
        startRound();
    }

    @Override
    public void submitToolResult(String callToken, String content) {
        messages.add(new ChatMessage(ChatMessage.ROLE_TOOL, content, callToken));
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
        ChatRequest request = new ChatRequest(model, List.copyOf(messages), temperature, tools);
        String requestId = provider.stream(request, this::handleProviderEvent);
        activeRequestId.set(requestId);
    }

    private void handleProviderEvent(AiEvent event) {
        if (event instanceof AiEvent.Completed completed && completed.response().hasToolCalls()) {
            List<ToolCall> calls = completed.response().toolCalls();
            String accompanyingText = completed.response().message().content();
            messages.add(new ChatMessage(ChatMessage.ROLE_ASSISTANT, accompanyingText, null, calls));
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
