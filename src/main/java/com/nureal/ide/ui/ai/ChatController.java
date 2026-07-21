package com.nureal.ide.ui.ai;

import java.io.IOException;

import javax.swing.SwingUtilities;

import com.nureal.ide.core.ai.agent.Agent;
import com.nureal.ide.core.ai.history.ChatHistoryStore;
import com.nureal.ide.core.ai.provider.AiEvent;
import com.nureal.ide.core.log.AppLogger;

/**
 * Fluxo UI -&gt; Agent -&gt; UI (ver {@code docs/034-ChatController.md}) —
 * nunca fala com {@code LLMProvider} diretamente, so com {@link Agent}.
 * Eventos do Agent chegam numa thread de background; todo evento e
 * processado via {@code SwingUtilities.invokeLater} antes de tocar o
 * {@link ChatPanel}.
 */
final class ChatController {

    private final ChatPanel panel;
    private final ChatHistoryStore historyStore;
    private final String conversationId;
    private Agent agent;
    private volatile String activeTurnId;

    ChatController(ChatPanel panel, Agent agent, ChatHistoryStore historyStore, String conversationId) {
        this.panel = panel;
        this.agent = agent;
        this.historyStore = historyStore;
        this.conversationId = conversationId;
        panel.setOnSend(this::sendMessage);
        panel.setOnCancel(this::cancelActive);
        loadHistory();
    }

    /** Chamado quando as configuracoes de IA (base URL/modelo/etc.) sao salvas com a janela ja aberta. */
    void updateAgent(Agent agent) {
        this.agent = agent;
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

    private void sendMessage(String text) {
        panel.addMessage("user", text);
        panel.setSending(true);
        panel.setStatus("Pensando...");
        ChatPanel.LiveBubble bubble = panel.beginAssistantMessage();
        activeTurnId = agent.chat(conversationId, text,
                event -> SwingUtilities.invokeLater(() -> handleEvent(event, bubble)));
    }

    private void cancelActive() {
        String turnId = activeTurnId;
        if (turnId != null) {
            agent.cancel(turnId);
        }
    }

    private void handleEvent(AiEvent event, ChatPanel.LiveBubble bubble) {
        if (event instanceof AiEvent.Started) {
            panel.setStatus("Pensando...");
        } else if (event instanceof AiEvent.Chunk chunk) {
            bubble.appendDelta(chunk.delta());
            panel.setStatus("Recebendo resposta...");
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
}
