package com.nureal.ide.modulos.iachat.apresentacao;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

import com.nureal.ide.modulos.iachat.dominio.contratos.Agent;
import com.nureal.ide.modulos.iachat.infraestrutura.ChatHistoryStore;
import com.nureal.ide.modulos.iachat.dominio.entidades.AiEvent;
import com.nureal.ide.modulos.iachat.dominio.entidades.ToolCall;
import com.nureal.ide.core.log.AppLogger;

/**
 * Fluxo UI -&gt; Agent -&gt; UI (ver {@code docs/034-ChatController.md}) —
 * nunca fala com {@code LLMProvider} diretamente, so com {@link Agent}.
 * Eventos do Agent chegam numa thread de background; todo evento e
 * processado via {@code SwingUtilities.invokeLater} antes de tocar o
 * {@link ChatPanel}.
 */
final class ChatController {

    private static final Pattern OPEN_SQL_FENCE = Pattern.compile("```(\\w*)");

    private final ChatPanel panel;
    private final ChatHistoryStore historyStore;
    /** Nao {@code final}: {@link #startNewConversation} troca pra um id novo, mesmo controller/painel (ver Fase 2). */
    private String conversationId;
    private Agent agent;
    private volatile String activeTurnId;

    ChatController(ChatPanel panel, Agent agent, ChatHistoryStore historyStore, String conversationId,
            ChatActions externalActions) {
        this.panel = panel;
        this.agent = agent;
        this.historyStore = historyStore;
        this.conversationId = conversationId;
        panel.setOnSend(this::sendMessage);
        panel.setOnCancel(this::cancelActive);
        panel.setActions(new ChatActions(externalActions.onExecuteSql(), externalActions.sqlFormatterSupplier(),
                this::explainSql, externalActions.activeSqlSupplier()));
        loadHistory();
    }

    /** Chamado quando as configuracoes de IA (base URL/modelo/etc.) sao salvas com a janela ja aberta. */
    void updateAgent(Agent agent) {
        this.agent = agent;
    }

    /**
     * Re-renderiza o historico com as cores do tema ATUAL — chamado apos
     * {@code MainWindow#toggleTheme}. So afeta mensagens ja concluidas (o
     * historico persistido); uma resposta em streaming no meio de uma troca
     * de tema fica com a bolha "ao vivo" (sem cor propria) ate terminar.
     */
    void refreshTheme() {
        panel.clearMessages();
        loadHistory();
    }

    /**
     * "+ Novo Chat" (ver {@code ChatPanel}): troca pra um {@code conversationId}
     * novo (o historico anterior CONTINUA salvo em {@link ChatHistoryStore},
     * so nao aparece mais nesta janela) e limpa a tela. Cancela um turno em
     * andamento primeiro — trocar de conversa no meio de uma resposta
     * deixaria o evento chegando depois tentando atualizar bolhas que nao
     * existem mais.
     */
    void startNewConversation() {
        if (activeTurnId != null) {
            cancelActive();
        }
        conversationId = UUID.randomUUID().toString();
        panel.clearMessages();
    }

    private void loadHistory() {
        try {
            historyStore.find(conversationId).ifPresent(conversation -> {
                for (ChatHistoryStore.StoredMessage message : conversation.messages()) {
                    panel.addMessage(message.role(), message.content());
                }
            });
        } catch (IOException e) {
            AppLogger.warning("Falha ao carregar historico do chat de IA", e);
        }
    }

    /**
     * {@code agent.chat(...)} PARECE so submeter trabalho a uma thread de
     * fundo (o streaming de verdade e assincrono, ver
     * {@code AbstractStreamingProvider}), mas o METODO em si roda coisa
     * sincrona ANTES disso — {@code DefaultAgent#chat} chama
     * {@code contextProvider.collect()}, que inclui
     * {@code Connection.getMetaData().getDatabaseProductName()/Version()}
     * (ver {@code MainWindow#databaseProductNameForAi}) — uma chamada JDBC
     * que PODE ir a rede, dependendo do driver/estado da conexao. Chamar
     * {@code agent.chat(...)} direto da EDT (como era antes) trava a UI
     * inteira por esse tempo, inclusive a bolha da PROPRIA mensagem do
     * usuario que acabou de ser adicionada (ja no component tree, mas sem
     * repintar ate a EDT ficar livre de novo) — sintoma relatado pelo
     * usuario: "a pergunta que fiz so aparece depois de carregar tudo".
     * {@link SwingWorker} tira so essa preparacao da EDT; os eventos que
     * chegam depois (via {@code onEvent}) ja voltam pra EDT sozinhos, sem
     * mudar nada nesse fluxo.
     */
    private void sendMessage(String text) {
        panel.addMessage("user", text);
        panel.setSending(true);
        panel.setStatus("Pensando...");
        ChatPanel.LiveBubble bubble = panel.beginAssistantMessage();
        StringBuilder liveText = new StringBuilder();
        Map<String, ChatPanel.ToolCardHandle> toolCards = new HashMap<>();
        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() {
                return agent.chat(conversationId, text,
                        event -> SwingUtilities.invokeLater(() -> handleEvent(event, bubble, liveText, toolCards)));
            }

            @Override
            protected void done() {
                try {
                    activeTurnId = get();
                } catch (Exception ex) {
                    AppLogger.warning("Falha ao iniciar a conversa com a IA", ex);
                    bubble.fail(ex.getMessage());
                    panel.setSending(false);
                    panel.setStatus(null);
                }
            }
        }.execute();
    }

    /** "Explicar" de um card SQL — reusa o mesmo fluxo de {@link #sendMessage}, sem o usuario precisar copiar/colar. */
    private void explainSql(String sql) {
        if (activeTurnId != null) {
            return;
        }
        sendMessage("Explique esta consulta:\n\n```sql\n" + sql.strip() + "\n```");
    }

    private void cancelActive() {
        String turnId = activeTurnId;
        if (turnId != null) {
            agent.cancel(turnId);
        }
    }

    private void handleEvent(AiEvent event, ChatPanel.LiveBubble bubble, StringBuilder liveText,
            Map<String, ChatPanel.ToolCardHandle> toolCards) {
        if (event instanceof AiEvent.Started) {
            panel.setStatus("Pensando...");
        } else if (event instanceof AiEvent.Chunk chunk) {
            liveText.append(chunk.delta());
            bubble.appendDelta(chunk.delta());
            panel.setStatus(insideOpenSqlFence(liveText) ? "Gerando SQL..." : "Recebendo resposta...");
        } else if (event instanceof AiEvent.ToolCallsRequested requested) {
            panel.setStatus(statusForTools(requested.calls()));
            for (ToolCall call : requested.calls()) {
                toolCards.put(call.id(), panel.beginToolCard(labelForTool(call.name())));
            }
        } else if (event instanceof AiEvent.ToolCallResult result) {
            ChatPanel.ToolCardHandle card = toolCards.remove(result.call().id());
            if (card != null) {
                card.complete(result.success(), result.summary(), result.structuredData());
            }
        } else if (event instanceof AiEvent.Completed completed) {
            bubble.finish(completed.response().message().content());
            finishTurn();
        } else if (event instanceof AiEvent.Failed failed) {
            bubble.fail(failed.error().getMessage());
            finishTurn();
        } else if (event instanceof AiEvent.Cancelled) {
            bubble.cancelled();
            finishTurn();
        }
    }

    private void finishTurn() {
        activeTurnId = null;
        panel.setSending(false);
        panel.setStatus(null);
    }

    private static boolean insideOpenSqlFence(StringBuilder text) {
        Matcher matcher = OPEN_SQL_FENCE.matcher(text);
        int fenceCount = 0;
        String lastLanguage = null;
        while (matcher.find()) {
            fenceCount++;
            if (fenceCount % 2 == 1) {
                lastLanguage = matcher.group(1);
            }
        }
        return fenceCount % 2 == 1 && "sql".equalsIgnoreCase(lastLanguage);
    }

    private static String statusForTools(List<ToolCall> calls) {
        return calls.stream().anyMatch(c -> isMetadataTool(c.name())) ? "Consultando metadata..."
                : "Executando ferramenta...";
    }

    private static String labelForTool(String toolName) {
        if (isMetadataTool(toolName)) {
            return "Consultando metadata...";
        }
        if ("execute_sql".equals(toolName)) {
            return "Executando consulta SQL...";
        }
        return "Executando " + toolName + "...";
    }

    private static boolean isMetadataTool(String toolName) {
        return "list_tables".equals(toolName) || "describe_table".equals(toolName);
    }
}
