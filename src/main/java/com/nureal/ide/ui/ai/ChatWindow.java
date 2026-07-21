package com.nureal.ide.ui.ai;

import java.awt.BorderLayout;
import java.awt.GraphicsConfiguration;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;

import javax.swing.JDialog;
import javax.swing.Timer;

import com.nureal.ide.core.ai.agent.Agent;
import com.nureal.ide.core.ai.history.ChatHistoryStore;
import com.nureal.ide.core.log.AppLogger;
import com.nureal.ide.core.ui.ChatWindowPreferences;

/**
 * Janela nao-modal do chat de IA — mesmo padrao de {@code ServerStatusDialog}
 * (JDialog MODELESS, fabrica estatica {@code open(...)}, invocada de um
 * botao do {@code MainWindow}), com uma diferenca: e um SINGLETON (uma unica
 * janela de chat, nao uma por chamada) — chamadas repetidas so trazem a
 * janela existente pra frente e atualizam o {@link Agent} usado (caso as
 * configuracoes de IA tenham mudado desde a ultima vez que foi aberta).
 * <p>
 * Posicao/tamanho: por padrao (primeira vez que o usuario abre, nenhum bound
 * salvo ainda) abre encostada na borda direita da {@code MainWindow}, com a
 * MESMA altura — um "docking" visual simples sem precisar reestruturar o
 * layout raiz da IDE (ver {@link #defaultBounds}). Qualquer redimensionamento/
 * movimento feito pelo usuario e persistido via {@link ChatWindowPreferences}
 * (com um pequeno debounce, ver {@code saveDebounce}) e reaplicado
 * EXATAMENTE na proxima abertura, mesmo depois de fechar a IDE.
 */
public final class ChatWindow {

    private static ChatWindow instance;

    private final JDialog dialog;
    private final ChatController controller;
    private final ChatWindowPreferences preferences = new ChatWindowPreferences();
    private final Timer saveBoundsDebounce;

    private ChatWindow(Window owner, Agent agent, ChatHistoryStore historyStore, String conversationId,
            Runnable onOpenSettings, ChatActions actions) {
        ChatPanel panel = new ChatPanel();
        panel.setOnOpenSettings(onOpenSettings);
        this.controller = new ChatController(panel, agent, historyStore, conversationId, actions);

        dialog = new JDialog(owner, "Chat com IA", JDialog.ModalityType.MODELESS);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.setLayout(new BorderLayout());
        dialog.add(panel, BorderLayout.CENTER);
        dialog.setBounds(loadBoundsOrDefault(owner));

        saveBoundsDebounce = new Timer(400, e -> persistBounds());
        saveBoundsDebounce.setRepeats(false);
        dialog.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                saveBoundsDebounce.restart();
            }

            @Override
            public void componentMoved(ComponentEvent e) {
                saveBoundsDebounce.restart();
            }
        });
        dialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                instance = null;
                saveBoundsDebounce.stop();
                persistBounds();
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

    private Rectangle loadBoundsOrDefault(Window owner) {
        try {
            ChatWindowPreferences.State saved = preferences.load();
            if (saved.isSet()) {
                return new Rectangle(saved.x(), saved.y(), saved.width(), saved.height());
            }
        } catch (IOException e) {
            AppLogger.warning("Falha ao carregar posicao salva do chat de IA", e);
        }
        return defaultBounds(owner);
    }

    private void persistBounds() {
        Rectangle bounds = dialog.getBounds();
        try {
            preferences.save(new ChatWindowPreferences.State(bounds.x, bounds.y, bounds.width, bounds.height));
        } catch (IOException e) {
            AppLogger.warning("Falha ao salvar posicao do chat de IA", e);
        }
    }

    /**
     * Encostada na borda direita da {@code MainWindow}, com a mesma altura —
     * "dockado" visualmente sem depender de nenhum {@code JSplitPane} novo no
     * layout raiz da IDE. Se isso estourar a tela (monitor pequeno/janela
     * principal maximizada perto da borda), recua pra caber dentro da area
     * visivel do MESMO monitor da janela principal, em vez de nascer cortada
     * ou num monitor errado.
     */
    private static Rectangle defaultBounds(Window owner) {
        Rectangle ownerBounds = owner.getBounds();
        int width = 440;
        int x = ownerBounds.x + ownerBounds.width;
        int y = ownerBounds.y;
        int height = ownerBounds.height;

        Rectangle screen = screenBoundsFor(owner);
        if (x + width > screen.x + screen.width) {
            x = Math.max(screen.x, screen.x + screen.width - width);
        }
        return new Rectangle(x, y, width, height);
    }

    private static Rectangle screenBoundsFor(Window owner) {
        GraphicsConfiguration gc = owner.getGraphicsConfiguration();
        if (gc != null) {
            return gc.getBounds();
        }
        return new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
    }
}
