package com.nureal.ide.core.ai.agent;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.nureal.ide.core.ai.config.AiPreferences;
import com.nureal.ide.core.ai.context.AgentContext;
import com.nureal.ide.core.ai.context.ContextProvider;
import com.nureal.ide.core.ai.history.ChatHistoryStore;
import com.nureal.ide.core.ai.provider.AiEvent;
import com.nureal.ide.core.ai.provider.ChatMessage;
import com.nureal.ide.core.ai.provider.ChatRequest;
import com.nureal.ide.core.ai.provider.LLMProvider;
import com.nureal.ide.core.ai.provider.ProviderException;
import com.nureal.ide.core.ai.provider.ToolCall;
import com.nureal.ide.core.ai.provider.ToolSpec;
import com.nureal.ide.core.ai.tool.ToolExecutor;
import com.nureal.ide.core.ai.tool.ToolRequest;
import com.nureal.ide.core.ai.tool.ToolResult;
import com.nureal.ide.core.log.AppLogger;

/**
 * Implementacao unica de {@link Agent} do MVP. Sempre usa
 * {@link LLMProvider#stream}, mesmo nas rodadas em que o modelo pode optar
 * por chamar uma tool: o Ollama traz {@code tool_calls} no ultimo chunk
 * (ver {@code OllamaProvider#runStream}) tanto quanto na resposta nao
 * streamed, entao nao ha motivo para um caminho separado sem streaming so
 * para o turno de decisao — evita gerar a resposta duas vezes e mantem a UX
 * de streaming mesmo quando uma tool acaba sendo usada.
 */
public final class DefaultAgent implements Agent {

    /** Rodadas modelo->tool->modelo permitidas antes de aceitar a resposta como final, mesmo com tool_calls pendente. */
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
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatMessage(ChatMessage.ROLE_SYSTEM, buildSystemPrompt(context)));
        messages.addAll(loadHistoryMessages(conversationId));
        messages.add(new ChatMessage(ChatMessage.ROLE_USER, userMessage));

        saveHistory(conversationId, userMessage, ChatMessage.ROLE_USER);

        List<ToolSpec> toolSpecs = toolExecutor.tools().stream()
                .map(t -> new ToolSpec(t.getName(), t.getDescription(), t.getParametersSchema()))
                .toList();

        Turn turn = new Turn(turnId, conversationId, onEvent, prefs, context, toolSpecs, messages);
        activeTurns.put(turnId, turn);
        startRound(turn);
        return turnId;
    }

    @Override
    public void cancel(String turnId) {
        Turn turn = activeTurns.get(turnId);
        if (turn == null) {
            return;
        }
        turn.cancelled.set(true);
        String activeProviderRequestId = turn.activeProviderRequestId.get();
        if (activeProviderRequestId != null) {
            provider.cancel(activeProviderRequestId);
        }
    }

    private void startRound(Turn turn) {
        if (turn.cancelled.get()) {
            turn.onEvent.accept(new AiEvent.Cancelled(turn.turnId));
            activeTurns.remove(turn.turnId);
            return;
        }
        ChatRequest request = new ChatRequest(turn.prefs.model(), List.copyOf(turn.messages),
                turn.prefs.temperature(), turn.toolSpecs);
        String providerRequestId = provider.stream(request, event -> handleEvent(turn, event));
        turn.activeProviderRequestId.set(providerRequestId);
    }

    /**
     * Eventos de cada rodada chegam sempre na thread de streaming daquela
     * rodada (ver {@code OllamaProvider}); {@code turn.messages}/{@code
     * turn.round} so sao mutados aqui, e a proxima rodada e disparada via
     * {@code ExecutorService.submit} (dentro de {@link #startRound}), que
     * garante happens-before para a thread da rodada seguinte — por isso
     * nao precisam ser {@code volatile}/sincronizados mesmo trocando de
     * thread a cada rodada de tool-calling.
     */
    private void handleEvent(Turn turn, AiEvent event) {
        if (turn.cancelled.get() && !(event instanceof AiEvent.Cancelled)) {
            return;
        }
        if (event instanceof AiEvent.Completed completed
                && completed.response().hasToolCalls()
                && turn.round < MAX_TOOL_ROUNDS - 1) {
            turn.round++;
            turn.messages.add(new ChatMessage(ChatMessage.ROLE_ASSISTANT, completed.response().message().content()));
            for (ToolCall call : completed.response().toolCalls()) {
                ToolResult result = toolExecutor.execute(new ToolRequest(call.name(), call.arguments(),
                        turn.conversationId, call.id(), turn.context));
                String toolContent = result.success() ? result.content() : ("Erro: " + result.error());
                turn.messages.add(new ChatMessage(ChatMessage.ROLE_TOOL, toolContent));
            }
            startRound(turn);
            return;
        }
        if (event instanceof AiEvent.Completed completed) {
            String finalContent = completed.response().message().content();
            if (!finalContent.isBlank()) {
                saveHistory(turn.conversationId, finalContent, ChatMessage.ROLE_ASSISTANT);
            }
            activeTurns.remove(turn.turnId);
        } else if (event instanceof AiEvent.Failed || event instanceof AiEvent.Cancelled) {
            activeTurns.remove(turn.turnId);
        }
        turn.onEvent.accept(event);
    }

    private String buildSystemPrompt(AgentContext context) {
        StringBuilder sb = new StringBuilder();
        sb.append("Voce e o assistente de IA integrado a Nureal Database IDE, uma IDE para bancos MySQL. ");
        sb.append("Responda em portugues do Brasil, de forma direta e tecnica. ");
        sb.append("Quando precisar de informacoes reais do banco conectado (tabelas, colunas, indices, chaves ");
        sb.append("estrangeiras), use as tools disponiveis em vez de adivinhar.");
        if (context.connectionLabel() != null) {
            sb.append("\n\nConexao ativa: ").append(context.connectionLabel());
        }
        if (context.activeSchema() != null) {
            sb.append("\nSchema ativo: ").append(context.activeSchema());
        }
        if (context.currentEditorSql() != null && !context.currentEditorSql().isBlank()) {
            sb.append("\n\nSQL atual no editor:\n").append(context.currentEditorSql());
        }
        return sb.toString();
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
        final AiPreferences.State prefs;
        final AgentContext context;
        final List<ToolSpec> toolSpecs;
        final List<ChatMessage> messages;
        final AtomicReference<String> activeProviderRequestId = new AtomicReference<>();
        final AtomicBoolean cancelled = new AtomicBoolean(false);
        int round = 0;

        Turn(String turnId, String conversationId, Consumer<AiEvent> onEvent, AiPreferences.State prefs,
                AgentContext context, List<ToolSpec> toolSpecs, List<ChatMessage> messages) {
            this.turnId = turnId;
            this.conversationId = conversationId;
            this.onEvent = onEvent;
            this.prefs = prefs;
            this.context = context;
            this.toolSpecs = toolSpecs;
            this.messages = messages;
        }
    }
}
