package com.nureal.ide.ui.ai;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * So layout e interacao com o {@link ChatController} (ver
 * {@code docs/033-ChatPanel.md}) — nenhuma chamada a {@code Agent}/
 * {@code LLMProvider} acontece aqui.
 */
final class ChatPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    private final JPanel messagesContainer = new JPanel();
    private final JScrollPane scrollPane;
    private final JTextArea input = new JTextArea(3, 40);
    private final JButton sendButton = new JButton("Enviar");
    private final JLabel statusLabel = new JLabel(" ");

    private Consumer<String> onSend = s -> { };
    private Runnable onCancel = () -> { };
    private Runnable onOpenSettings = () -> { };
    private ChatActions actions = ChatActions.NONE;
    private boolean sending;

    ChatPanel() {
        super(new BorderLayout());

        JButton settingsButton = new JButton("⚙", null);
        settingsButton.setToolTipText("Configuracoes de IA (modelo, base URL, streaming...)");
        settingsButton.setHorizontalAlignment(SwingConstants.CENTER);
        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setBorder(BorderFactory.createEmptyBorder(4, 6, 0, 6));
        JPanel settingsWrap = new JPanel();
        settingsWrap.add(settingsButton);
        topBar.add(settingsWrap, BorderLayout.EAST);
        add(topBar, BorderLayout.NORTH);
        settingsButton.addActionListener(e -> onOpenSettings.run());

        messagesContainer.setLayout(new BoxLayout(messagesContainer, BoxLayout.Y_AXIS));
        messagesContainer.setOpaque(false);
        messagesContainer.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

        JPanel messagesWrapper = new JPanel(new BorderLayout());
        messagesWrapper.setOpaque(false);
        messagesWrapper.add(messagesContainer, BorderLayout.NORTH);

        scrollPane = new JScrollPane(messagesWrapper);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        add(scrollPane, BorderLayout.CENTER);

        input.setLineWrap(true);
        input.setWrapStyleWord(true);
        input.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UIManager.getColor("Component.borderColor")),
                BorderFactory.createEmptyBorder(6, 8, 6, 8)));
        input.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER && e.isControlDown()) {
                    e.consume();
                    triggerSend();
                }
            }
        });

        sendButton.addActionListener(e -> {
            if (sending) {
                onCancel.run();
            } else {
                triggerSend();
            }
        });

        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.PLAIN, 11f));
        Color muted = UIManager.getColor("Label.disabledForeground");
        if (muted != null) {
            statusLabel.setForeground(muted);
        }

        JPanel inputWrapper = new JPanel(new BorderLayout(6, 4));
        inputWrapper.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        inputWrapper.add(new JScrollPane(input), BorderLayout.CENTER);
        JPanel sendWrapper = new JPanel(new BorderLayout());
        sendWrapper.add(sendButton, BorderLayout.NORTH);
        inputWrapper.add(sendWrapper, BorderLayout.EAST);
        inputWrapper.add(statusLabel, BorderLayout.SOUTH);

        add(inputWrapper, BorderLayout.SOUTH);
        setPreferredSize(new Dimension(420, 560));
    }

    void setOnSend(Consumer<String> onSend) {
        this.onSend = onSend;
    }

    void setOnCancel(Runnable onCancel) {
        this.onCancel = onCancel;
    }

    void setOnOpenSettings(Runnable onOpenSettings) {
        this.onOpenSettings = onOpenSettings;
    }

    /** Acoes dos cards SQL (Executar/Formatar/Explicar) — ver {@link ChatActions}. */
    void setActions(ChatActions actions) {
        this.actions = actions;
    }

    private void triggerSend() {
        String text = input.getText().strip();
        if (text.isEmpty() || sending) {
            return;
        }
        input.setText("");
        onSend.accept(text);
    }

    /** Mensagem final (usuario, ou historico ja carregado) — sempre renderizada de uma vez. */
    void addMessage(String role, String content) {
        messagesContainer.add(MessageRenderer.render(role, content, actions));
        messagesContainer.add(javax.swing.Box.createVerticalStrut(2));
        revalidateAndScrollToBottom();
    }

    /**
     * Card de status de uma tool em execucao (ex.: "Buscando tabelas..."),
     * atualizado pro resultado final quando a tool terminar — ver
     * {@link ToolCardHandle#complete}.
     */
    ToolCardHandle beginToolCard(String label) {
        MessageRenderer.ToolCard card = MessageRenderer.toolCard(label);
        messagesContainer.add(card.component());
        messagesContainer.add(javax.swing.Box.createVerticalStrut(2));
        revalidateAndScrollToBottom();
        return new ToolCardHandle(card.statusLabel());
    }

    /** Inicia uma bolha de resposta do assistente que vai crescendo enquanto os chunks chegam. */
    LiveBubble beginAssistantMessage() {
        JTextArea liveArea = new JTextArea();
        liveArea.setEditable(false);
        liveArea.setLineWrap(true);
        liveArea.setWrapStyleWord(true);
        liveArea.setOpaque(false);
        liveArea.setFont(UIManager.getFont("Label.font"));
        liveArea.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));

        JLabel roleLabel = new JLabel("Assistente");
        roleLabel.setFont(roleLabel.getFont().deriveFont(Font.BOLD, 11f));
        Color muted = UIManager.getColor("Label.disabledForeground");
        if (muted != null) {
            roleLabel.setForeground(muted);
        }

        JPanel bubble = new JPanel();
        bubble.setLayout(new BoxLayout(bubble, BoxLayout.Y_AXIS));
        bubble.setOpaque(false);
        bubble.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        bubble.add(roleLabel);
        bubble.add(liveArea);

        messagesContainer.add(bubble);
        revalidateAndScrollToBottom();
        return new LiveBubble(bubble, liveArea);
    }

    void setSending(boolean sending) {
        this.sending = sending;
        input.setEnabled(!sending);
        sendButton.setText(sending ? "Cancelar" : "Enviar");
    }

    void setStatus(String text) {
        statusLabel.setText(text == null ? " " : text);
    }

    private void revalidateAndScrollToBottom() {
        messagesContainer.revalidate();
        messagesContainer.repaint();
        SwingUtilities.invokeLater(() -> {
            JScrollBar bar = scrollPane.getVerticalScrollBar();
            bar.setValue(bar.getMaximum());
        });
    }

    /** Alca de uma mensagem do assistente em construcao (streaming). */
    final class LiveBubble {
        private final JComponent bubble;
        private final JTextArea liveArea;
        private final StringBuilder content = new StringBuilder();

        private LiveBubble(JComponent bubble, JTextArea liveArea) {
            this.bubble = bubble;
            this.liveArea = liveArea;
        }

        void appendDelta(String delta) {
            content.append(delta);
            liveArea.setText(content.toString());
            revalidateAndScrollToBottom();
        }

        /** Troca a bolha "ao vivo" pela versao final renderizada (com cards/blocos de codigo). */
        void finish(String finalContent) {
            replaceWith(MessageRenderer.render("assistant", finalContent, actions));
        }

        void fail(String errorMessage) {
            replaceWith(MessageRenderer.renderError(errorMessage));
        }

        void cancelled() {
            String text = content.length() > 0 ? content.toString() + "\n\n(cancelado)" : "(cancelado)";
            replaceWith(MessageRenderer.render("assistant", text, actions));
        }

        private void replaceWith(JComponent replacement) {
            int index = -1;
            for (int i = 0; i < messagesContainer.getComponentCount(); i++) {
                if (messagesContainer.getComponent(i) == bubble) {
                    index = i;
                    break;
                }
            }
            if (index < 0) {
                return;
            }
            messagesContainer.remove(index);
            messagesContainer.add(replacement, index);
            revalidateAndScrollToBottom();
        }
    }

    /** Alca de um card de tool em execucao — ver {@link #beginToolCard}. */
    final class ToolCardHandle {
        private final JLabel statusLabel;

        private ToolCardHandle(JLabel statusLabel) {
            this.statusLabel = statusLabel;
        }

        void complete(boolean success, String summary) {
            statusLabel.setText((success ? "✓ " : "❌ ") + summary);
            revalidateAndScrollToBottom();
        }
    }
}
