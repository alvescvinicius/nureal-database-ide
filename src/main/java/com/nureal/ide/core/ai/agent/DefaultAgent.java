package com.nureal.ide.core.ai.agent;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.nureal.ide.core.ai.config.AiPreferences;
import com.nureal.ide.core.ai.context.AgentContext;
import com.nureal.ide.core.ai.context.ContextProvider;
import com.nureal.ide.core.ai.history.ChatHistoryStore;
import com.nureal.ide.core.ai.prompt.PromptComposer;
import com.nureal.ide.core.ai.provider.AiEvent;
import com.nureal.ide.core.ai.provider.ChatMessage;
import com.nureal.ide.core.ai.provider.ChatRequest;
import com.nureal.ide.core.ai.provider.ChatResponse;
import com.nureal.ide.core.ai.provider.ChatUsage;
import com.nureal.ide.core.ai.provider.ConversationSession;
import com.nureal.ide.core.ai.provider.LLMProvider;
import com.nureal.ide.core.ai.provider.ProviderException;
import com.nureal.ide.core.ai.provider.ToolCall;
import com.nureal.ide.core.ai.provider.ToolSpec;
import com.nureal.ide.core.ai.tool.ToolExecutor;
import com.nureal.ide.core.ai.tool.ToolRequest;
import com.nureal.ide.core.ai.tool.ToolResult;
import com.nureal.ide.core.log.AppLogger;

/**
 * Implementacao unica de {@link Agent} do MVP. Fala com o provider sempre
 * atraves de uma {@link ConversationSession} (ver {@link LLMProvider#createSession}):
 * o Agent so reage a {@link AiEvent.ToolCallsRequested} (executa a tool e
 * devolve o resultado via {@link ConversationSession#submitToolResult}) e a
 * {@link AiEvent.Completed} (resposta final) — nunca constroi {@link ChatMessage}
 * de tool-calling nem conhece {@code tool_use}/{@code tool_call_id}/
 * {@code functionCall} de nenhum provider especifico; cada
 * {@link ConversationSession} cuida disso sozinha.
 */
public final class DefaultAgent implements Agent {

    /** Rodadas modelo->tool->modelo permitidas antes de aceitar a resposta como final, mesmo com tool pendente. */
    private static final int MAX_TOOL_ROUNDS = 3;

    private final LLMProvider provider;
    private final ContextProvider contextProvider;
    private final ToolExecutor toolExecutor;
    private final ChatHistoryStore historyStore;
    private final Supplier<AiPreferences.State> preferencesSupplier;
    private final Map<String, Turn> activeTurns = new ConcurrentHashMap<>();

    public DefaultAgent(LLMProvider provider, ContextProvider contextProvider, ToolExecutor toolExecutor,
            ChatHistoryStore historyStore, Supplier<AiPreferences.State> preferencesSupplier) {
        this.provider = provider;
        this.contextProvider = contextProvider;
        this.toolExecutor = toolExecutor;
        this.historyStore = historyStore;
        this.preferencesSupplier = preferencesSupplier;
    }

    @Override
    public String chat(String conversationId, String userMessage, Consumer<AiEvent> onEvent) {
        String turnId = UUID.randomUUID().toString();
        AiPreferences.State prefs = preferencesSupplier.get();

        if (prefs.model() == null || prefs.model().isBlank()) {
            onEvent.accept(new AiEvent.Started(turnId));
            onEvent.accept(new AiEvent.Failed(turnId, new ProviderException.InvalidModel(
                    "Nenhum modelo selecionado. Abra as configuracoes de IA e escolha um modelo.")));
            return turnId;
        }

        AgentContext context = contextProvider.collect();
        List<ChatMessage> seedMessages = new ArrayList<>();
        seedMessages.add(new ChatMessage(ChatMessage.ROLE_SYSTEM, PromptComposer.compose(context)));
        seedMessages.addAll(loadHistoryMessages(conversationId));

        saveHistory(conversationId, userMessage, ChatMessage.ROLE_USER);

        List<ToolSpec> toolSpecs = toolExecutor.tools().stream()
                .map(t -> new ToolSpec(t.getName(), t.getDescription(), t.getParametersSchema()))
                .toList();

        ChatRequest seed = new ChatRequest(prefs.model(), seedMessages, prefs.temperature(), toolSpecs);
        Turn turn = new Turn(turnId, conversationId, onEvent, context);
        turn.session = provider.createSession(seed, event -> handleEvent(turn, event));
        activeTurns.put(turnId, turn);
        turn.session.send(userMessage);
        return turnId;
    }

    @Override
    public void cancel(String turnId) {
        Turn turn = activeTurns.get(turnId);
        if (turn == null) {
            return;
        }
        turn.cancelled.set(true);
        if (turn.session != null) {
            turn.session.cancel();
        }
    }

    /**
     * Eventos de cada rodada chegam sempre na thread de streaming daquela
     * rodada (ver {@code AbstractStreamingProvider}); {@code turn.round} so e
     * mutado aqui, e a proxima rodada e disparada pela propria
     * {@link ConversationSession} via {@code ExecutorService.submit}, que
     * garante happens-before para a thread da rodada seguinte — por isso nao
     * precisa ser {@code volatile}/sincronizado mesmo trocando de thread a
     * cada rodada de tool-calling.
     */
    private void handleEvent(Turn turn, AiEvent event) {
        if (turn.cancelled.get() && !(event instanceof AiEvent.Cancelled)) {
            return;
        }
        if (event instanceof AiEvent.ToolCallsRequested requested) {
            if (turn.round >= MAX_TOOL_ROUNDS - 1) {
                // Limite de rodadas atingido: aceita o texto que veio junto do
                // pedido de tool (se houver) como resposta final, sem executar
                // mais nenhuma tool — mesmo fallback que o fluxo antigo tinha.
                finalizeWithSyntheticText(turn, requested.requestId(), requested.accompanyingText());
                return;
            }
            turn.round++;
            turn.onEvent.accept(event);
            for (ToolCall call : requested.calls()) {
                ToolResult result = toolExecutor.execute(new ToolRequest(call.name(), call.arguments(),
                        turn.conversationId, call.id(), turn.context));
                String toolContent = result.success() ? result.content() : ("Erro: " + result.error());
                turn.onEvent.accept(new AiEvent.ToolCallResult(requested.requestId(), call, result.success(),
                        toolContent, result.structuredData()));
                turn.session.submitToolResult(call.id(), toolContent);
            }
            return;
        }
        if (event instanceof AiEvent.Completed completed) {
            String finalContent = completed.response().message().content();
            if (!finalContent.isBlank()) {
                saveHistory(turn.conversationId, finalContent, ChatMessage.ROLE_ASSISTANT);
            }
            activeTurns.remove(turn.turnId);
            turn.onEvent.accept(event);
            return;
        }
        if (event instanceof AiEvent.Failed || event instanceof AiEvent.Cancelled) {
            activeTurns.remove(turn.turnId);
        }
        turn.onEvent.accept(event);
    }

    /** So usado no fallback de limite de rodadas (ver acima) — sem {@link ChatUsage} real disponivel ali. */
    private void finalizeWithSyntheticText(Turn turn, String requestId, String finalContent) {
        if (!finalContent.isBlank()) {
            saveHistory(turn.conversationId, finalContent, ChatMessage.ROLE_ASSISTANT);
        }
        activeTurns.remove(turn.turnId);
        turn.onEvent.accept(new AiEvent.Completed(requestId,
                new ChatResponse(new ChatMessage(ChatMessage.ROLE_ASSISTANT, finalContent), "stop", ChatUsage.EMPTY,
                        List.of())));
    }

    private List<ChatMessage> loadHistoryMessages(String conversationId) {
        try {
            return historyStore.find(conversationId)
                    .map(c -> c.messages().stream()
                            .map(m -> new ChatMessage(m.role(), m.content()))
                            .toList())
                    .orElse(List.of());
        } catch (IOException e) {
            AppLogger.warning("Falha ao carregar historico da conversa " + conversationId, e);
            return List.of();
        }
    }

    private void saveHistory(String conversationId, String content, String role) {
        try {
            historyStore.appendMessage(conversationId, deriveTitle(content), role, content);
        } catch (IOException e) {
            AppLogger.warning("Falha ao salvar historico da conversa " + conversationId, e);
        }
    }

    private static String deriveTitle(String firstMessage) {
        String trimmed = firstMessage == null ? "" : firstMessage.strip().replace('\n', ' ');
        if (trimmed.isEmpty()) {
            return "Nova conversa";
        }
        return trimmed.length() > 60 ? trimmed.substring(0, 60) + "..." : trimmed;
    }

    private static final class Turn {
        final String turnId;
        final String conversationId;
        final Consumer<AiEvent> onEvent;
        final AgentContext context;
        final AtomicBoolean cancelled = new AtomicBoolean(false);
        /** Atribuida logo apos a construcao, antes de qualquer evento poder chegar (ver {@link #chat}). */
        ConversationSession session;
        int round = 0;

        Turn(String turnId, String conversationId, Consumer<AiEvent> onEvent, AgentContext context) {
            this.turnId = turnId;
            this.conversationId = conversationId;
            this.onEvent = onEvent;
            this.context = context;
        }
    }
}
