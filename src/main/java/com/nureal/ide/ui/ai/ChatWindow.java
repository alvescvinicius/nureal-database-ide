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
            Runnable onOpenSettings, ChatActions actions) {
        ChatPanel panel = new ChatPanel();
        panel.setOnOpenSettings(onOpenSettings);
        this.controller = new ChatController(panel, agent, historyStore, conversationId, actions);

        dialog = new JDialog(owner, "Chat com IA", JDialog.ModalityType.MODELESS);
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
            Runnable onOpenSettings, ChatActions actions) {
        if (instance != null) {
            instance.controller.updateAgent(agent);
            instance.dialog.setVisible(true);
            instance.dialog.toFront();
            instance.dialog.requestFocus();
            return;
        }
        instance = new ChatWindow(owner, agent, historyStore, conversationId, onOpenSettings, actions);
        instance.dialog.setVisible(true);
    }

    /** Chamado apos salvar as configuracoes de IA, para a janela (se aberta) usar o {@link Agent} atualizado. */
    public static void updateAgent(Agent agent) {
        if (instance != null) {
            instance.controller.updateAgent(agent);
        }
    }

    /**
     * Chamado por {@code MainWindow#toggleTheme} — os cards do chat (ver
     * {@code MessageRenderer}) tem cores proprias presas no tema de quando
     * foram renderizados, e esta janela e um SINGLETON que pode ficar aberta
     * atravessando uma troca de tema. Sem efeito se a janela nao estiver aberta.
     */
    public static void refreshTheme() {
        if (instance != null) {
            instance.controller.refreshTheme();
        }
    }
}
