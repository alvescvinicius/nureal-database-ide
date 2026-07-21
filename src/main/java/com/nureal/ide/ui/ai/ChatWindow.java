package com.nureal.ide.ui.ai;

import java.awt.BorderLayout;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.JDialog;

import com.nureal.ide.core.ai.agent.Agent;
import com.nureal.ide.core.ai.history.ChatHistoryStore;

/**
 * Janela nao-modal do chat de IA — mesmo padrao de {@code ServerStatusDialog}
 * (JDialog MODELESS, fabrica estatica {@code open(...)}, invocada de um
 * botao do {@code MainWindow}), com uma diferenca: e um SINGLETON (uma unica
 * janela de chat, nao uma por chamada) — chamadas repetidas so trazem a
 * janela existente pra frente e atualizam o {@link Agent} usado (caso as
 * configuracoes de IA tenham mudado desde a ultima vez que foi aberta).
 */
public final class ChatWindow {

    private static ChatWindow instance;

    private final JDialog dialog;
    private final ChatController controller;

    private ChatWindow(Window owner, Agent agent, ChatHistoryStore historyStore, String conversationId,
            Runnable onOpenSettings) {
        ChatPanel panel = new ChatPanel();
        panel.setOnOpenSettings(onOpenSettings);
        this.controller = new ChatController(panel, agent, historyStore, conversationId);

        dialog = new JDialog(owner, "Chat com IA (Ollama)", JDialog.ModalityType.MODELESS);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.setLayout(new BorderLayout());
        dialog.add(panel, BorderLayout.CENTER);
        dialog.setSize(440, 640);
        dialog.setLocationRelativeTo(owner);
        dialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                instance = null;
            }
        });
    }

    public static void open(Window owner, Agent agent, ChatHistoryStore historyStore, String conversationId,
            Runnable onOpenSettings) {
        if (instance != null) {
            instance.controller.updateAgent(agent);
            instance.dialog.setVisible(true);
            instance.dialog.toFront();
            instance.dialog.requestFocus();
            return;
        }
        instance = new ChatWindow(owner, agent, historyStore, conversationId, onOpenSettings);
        instance.dialog.setVisible(true);
    }

    /** Chamado apos salvar as configuracoes de IA, para a janela (se aberta) usar o {@link Agent} atualizado. */
    public static void updateAgent(Agent agent) {
        if (instance != null) {
            instance.controller.updateAgent(agent);
        }
    }

    /**
     * Mostra um status de sistema (ex.: progresso de inicializacao/download
     * do Ollama embutido) na janela, se estiver aberta — sem efeito (e sem
     * abrir a janela) caso contrario, ja que e so um retorno informativo de
     * uma tarefa de background que pode terminar antes/depois do usuario
     * abrir o chat.
     */
    public static void updateStatus(String text) {
        if (instance != null) {
            instance.controller.showSystemStatus(text);
        }
    }
}
