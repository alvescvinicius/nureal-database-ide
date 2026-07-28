package com.nureal.ide.modulos.iachat.apresentacao;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JToggleButton;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import com.nureal.ide.modulos.iachat.infraestrutura.tool.SqlQueryResult;
import com.nureal.ide.compartilhado.designsystem.NToolbar;
import com.nureal.ide.compartilhado.designsystem.Buttons;
import com.nureal.ide.compartilhado.designsystem.GridTheme;
import com.nureal.ide.compartilhado.designsystem.Icons;
import com.nureal.ide.compartilhado.designsystem.IconType;

/**
 * So layout e interacao com o {@link ChatController} (ver
 * {@code docs/033-ChatPanel.md}) — nenhuma chamada a {@code Agent}/
 * {@code LLMProvider} acontece aqui.
 */
final class ChatPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    /**
     * Presets de prompt (Fase 4 do {@code AI-CHAT-MASTER-PLAN.md}): atalhos
     * de TEXTO, nunca tools novas — cada um (exceto {@code ASK}, neutro)
     * reescreve o campo de mensagem NA HORA com a instrucao + o SQL da aba
     * ativa (ver {@code ChatActions#activeSqlSupplier}), pra que o usuario
     * VEJA e possa editar o texto final antes de mandar (nunca uma
     * transformacao escondida so no envio).
     */
    private enum Preset {
        ASK("Perguntar", null),
        SQL("SQL", "Me ajude com esta consulta SQL:"),
        EXPLAIN("Explicar", "Explique esta consulta:"),
        OPTIMIZE("Otimizar", "Sugira otimizacoes (indices, reescrita) para esta consulta:"),
        DOCUMENT("Documentar", "Documente esta consulta (o que cada parte faz):");

        final String label;
        final String instruction;

        Preset(String label, String instruction) {
            this.label = label;
            this.instruction = instruction;
        }
    }

    private final JPanel messagesContainer = new JPanel();
    private final JScrollPane scrollPane;
    private final JTextArea input = new JTextArea(3, 40);
    private final JButton sendButton = new JButton("Enviar");
    private final JLabel statusLabel = new JLabel(" ");
    private final JComboBox<String> modelCombo = new JComboBox<>();
    private final JLabel schemaBadge = new JLabel();

    private Consumer<String> onSend = s -> { };
    private Runnable onCancel = () -> { };
    private Runnable onOpenSettings = () -> { };
    private Runnable onNewChat = () -> { };
    private Consumer<String> onModelChange = model -> { };
    private ChatActions actions = ChatActions.NONE;
    private boolean sending;
    /** Suprime {@link #onModelChange} enquanto {@link #setModelOptions} preenche o combo (nao e escolha do usuario). */
    private boolean populatingModels;

    ChatPanel() {
        super(new BorderLayout());

        // Icone do catalogo (Buttons.iconButton/IconType.SETTINGS) em vez do
        // emoji "⚙" como texto do botao — mesmo motivo do ajuste em
        // #complete/MainWindow#buildLayoutMenu: um glifo Unicode direto no
        // texto pode nao existir na fonte do sistema e aparecer como um
        // quadrado vazio ("tofu"), relatado pelo usuario em varios lugares.
        JButton settingsButton = Buttons.iconButton(IconType.SETTINGS, 16, () -> GridTheme.MUTED_TEXT);
        settingsButton.setToolTipText("Configuracoes de IA (modelo, base URL, streaming...)");
        settingsButton.addActionListener(e -> onOpenSettings.run());

        JButton newChatButton = new JButton("+ Novo Chat");
        newChatButton.setToolTipText("Comeca uma conversa nova (o historico atual continua salvo)");
        newChatButton.addActionListener(e -> onNewChat.run());

        modelCombo.setToolTipText("Modelo de IA usado nesta conversa");
        modelCombo.addActionListener(e -> {
            if (populatingModels) {
                return;
            }
            Object selected = modelCombo.getSelectedItem();
            if (selected != null) {
                onModelChange.accept(selected.toString());
            }
        });

        schemaBadge.setFont(schemaBadge.getFont().deriveFont(Font.PLAIN, 11f));
        Color mutedBadge = UIManager.getColor("Label.disabledForeground");
        if (mutedBadge != null) {
            schemaBadge.setForeground(mutedBadge);
        }

        NToolbar topBar = new NToolbar().setTitle("Chat com IA")
                .addPrimaryAction(schemaBadge)
                .addPrimaryAction(modelCombo)
                .addSecondaryAction(newChatButton)
                .addSecondaryAction(settingsButton);
        add(topBar, BorderLayout.NORTH);

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
        inputWrapper.add(buildPresetRow(), BorderLayout.NORTH);
        inputWrapper.add(new JScrollPane(input), BorderLayout.CENTER);
        JPanel sendWrapper = new JPanel(new BorderLayout());
        sendWrapper.add(sendButton, BorderLayout.NORTH);
        inputWrapper.add(sendWrapper, BorderLayout.EAST);
        inputWrapper.add(statusLabel, BorderLayout.SOUTH);

        add(inputWrapper, BorderLayout.SOUTH);
        setPreferredSize(new Dimension(420, 560));
    }

    /** Perguntar/SQL/Explicar/Otimizar/Documentar — so um ativo por vez (ver {@link Preset}). */
    private JPanel buildPresetRow() {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        row.setOpaque(false);
        ButtonGroup group = new ButtonGroup();
        for (Preset preset : Preset.values()) {
            JToggleButton button = new JToggleButton(preset.label);
            button.setSelected(preset == Preset.ASK);
            button.addActionListener(e -> applyPreset(preset));
            group.add(button);
            row.add(button);
        }
        return row;
    }

    /**
     * Reescreve o campo de mensagem AGORA (nao so no envio) com a instrucao
     * do preset + o SQL da aba ativa entre crases, se houver — o usuario
     * ainda pode editar livremente antes de clicar "Enviar", que continua
     * mandando exatamente o que estiver escrito, sem nenhuma transformacao
     * adicional (criterio de aceite da Fase 4: nada de "magica" escondida).
     * {@code ASK} (neutro) nao mexe no que ja estava digitado.
     */
    private void applyPreset(Preset preset) {
        if (preset.instruction == null) {
            return;
        }
        StringBuilder message = new StringBuilder(preset.instruction);
        String sql = actions.activeSqlSupplier().get();
        if (sql != null && !sql.isBlank()) {
            message.append("\n\n```sql\n").append(sql.strip()).append("\n```");
        }
        input.setText(message.toString());
        input.requestFocusInWindow();
        input.setCaretPosition(input.getText().length());
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

    /** Botao "+ Novo Chat" — quem chama decide o novo {@code conversationId} (ver {@code ChatHistoryStore}). */
    void setOnNewChat(Runnable onNewChat) {
        this.onNewChat = onNewChat;
    }

    /** Disparado quando o USUARIO escolhe um modelo diferente no combo (nunca ao popular via {@link #setModelOptions}). */
    void setOnModelChange(Consumer<String> onModelChange) {
        this.onModelChange = onModelChange;
    }

    /** Acoes dos cards SQL (Executar/Formatar/Explicar) — ver {@link ChatActions}. */
    void setActions(ChatActions actions) {
        this.actions = actions;
    }

    /** Preenche o combo de modelo (ex.: ao abrir, ou apos "Listar modelos" nas Configuracoes) e marca o atual. */
    void setModelOptions(List<String> models, String selected) {
        populatingModels = true;
        try {
            modelCombo.setModel(new DefaultComboBoxModel<>(models.toArray(new String[0])));
            if (selected != null) {
                modelCombo.setSelectedItem(selected);
            }
        } finally {
            populatingModels = false;
        }
    }

    /** Rotulo do esquema/conexao ativa (ver {@code AgentContext}), ou vazio se nenhuma conexao ativa. */
    void setSchemaLabel(String label) {
        schemaBadge.setText(label == null || label.isBlank() ? " " : label);
    }

    private void triggerSend() {
        String text = input.getText().strip();
        if (text.isEmpty() || sending) {
            return;
        }
        input.setText("");
        onSend.accept(text);
    }

    /** Esvazia a lista de mensagens renderizadas — ver {@code ChatController#refreshTheme}. */
    void clearMessages() {
        messagesContainer.removeAll();
        revalidateAndScrollToBottom();
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
        return new ToolCardHandle(card.component(), card.statusLabel());
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
            replaceComponent(bubble, replacement);
        }
    }

    /**
     * Alca de um card de tool em execucao — ver {@link #beginToolCard}. Tools
     * que devolvem so texto (ex.: {@code list_tables}) so atualizam o rotulo
     * de status; tools com dado tabular reconhecido (ex.: {@code SqlQueryResult}
     * de {@code ExecuteSqlTool}) trocam o card inteiro pela tabela renderizada
     * — mesma troca "ao vivo -&gt; final" que {@link LiveBubble#replaceWith} ja
     * faz pra mensagens do assistente.
     */
    final class ToolCardHandle {
        private final JComponent component;
        private final JLabel statusLabel;

        private ToolCardHandle(JComponent component, JLabel statusLabel) {
            this.component = component;
            this.statusLabel = statusLabel;
        }

        void complete(boolean success, String summary, Object structuredData) {
            if (success && structuredData instanceof SqlQueryResult data) {
                replaceComponent(component, MessageRenderer.sqlResultCard(data, actions));
                return;
            }
            // Icone do catalogo (Icons.get) em vez de um caractere Unicode
            // "✓ "/"❌ " colado no texto — mesmo motivo do ajuste em
            // MainWindow#buildLayoutMenu/ConnectionStatusCard: o caractere nao
            // existe em algumas fontes e aparecia como um quadrado vazio
            // ("tofu") no lugar do simbolo, relatado pelo usuario.
            statusLabel.setIcon(Icons.get(success ? IconType.SUCCESS : IconType.ERROR, 12));
            statusLabel.setText(summary);
            revalidateAndScrollToBottom();
        }
    }

    /** Troca {@code current} por {@code replacement} na mesma posicao da lista de mensagens — usado por {@link LiveBubble}/{@link ToolCardHandle}. */
    private void replaceComponent(JComponent current, JComponent replacement) {
        int index = -1;
        for (int i = 0; i < messagesContainer.getComponentCount(); i++) {
            if (messagesContainer.getComponent(i) == current) {
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
