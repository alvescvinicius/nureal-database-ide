package com.nureal.ide.ui.ai;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.MatteBorder;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rtextarea.RTextScrollPane;

/**
 * Renderiza o conteudo de uma mensagem do chat como uma pilha de "cards"
 * tipados — texto, SQL (com Copiar/Executar/Explicar/Formatar), codigo
 * generico, informacao/aviso (blocos de admonicao {@code > [!TIP]}/
 * {@code > [!WARNING]}, convencao GFM que os modelos ja conhecem), erro e
 * tool — em vez de um bloco de texto corrido. Continua sem suporte a
 * markdown completo (negrito/listas/tabelas/links): so o que ja existia
 * (blocos de codigo) mais os tipos de card acima.
 */
final class MessageRenderer {

    private static final Pattern CODE_FENCE = Pattern.compile("```(\\w*)\\r?\\n([\\s\\S]*?)```");
    private static final Pattern ADMONITION_START = Pattern.compile("^>\\s?\\[!(\\w+)]\\s*$");

    private MessageRenderer() {
    }

    static JComponent render(String role, String content, ChatActions actions) {
        JPanel bubble = new JPanel();
        bubble.setLayout(new BoxLayout(bubble, BoxLayout.Y_AXIS));
        bubble.setOpaque(false);
        bubble.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        bubble.setAlignmentX(JComponent.LEFT_ALIGNMENT);

        JLabel roleLabel = new JLabel(roleLabel(role));
        roleLabel.setFont(roleLabel.getFont().deriveFont(Font.BOLD, 11f));
        roleLabel.setForeground(mutedColor());
        roleLabel.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        bubble.add(roleLabel);
        bubble.add(Box.createVerticalStrut(2));

        String text = content == null ? "" : content;
        Matcher matcher = CODE_FENCE.matcher(text);
        int last = 0;
        boolean foundAny = false;
        while (matcher.find()) {
            foundAny = true;
            String before = text.substring(last, matcher.start());
            if (!before.isBlank()) {
                addTextAndAdmonitionCards(bubble, before.strip());
            }
            bubble.add(spaced(codeCard(matcher.group(2), matcher.group(1), actions)));
            last = matcher.end();
        }
        String rest = text.substring(last);
        if (!rest.isBlank() || !foundAny) {
            addTextAndAdmonitionCards(bubble, rest.strip());
        }
        return bubble;
    }

    /** Card de erro (ex.: {@code AiEvent.Failed}) — nunca vem do texto do modelo, so montado direto pelo {@code ChatPanel}. */
    static JComponent renderError(String message) {
        JPanel bubble = new JPanel();
        bubble.setLayout(new BoxLayout(bubble, BoxLayout.Y_AXIS));
        bubble.setOpaque(false);
        bubble.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        bubble.add(card(accentColor("error"), "❌ Erro", textArea(message)));
        return bubble;
    }

    /** Card de status de uma execucao de tool, ainda pendente — {@code statusLabel} e atualizado ao vivo por {@code ChatPanel}. */
    record ToolCard(JComponent component, JLabel statusLabel) {
    }

    static ToolCard toolCard(String label) {
        JPanel bubble = new JPanel();
        bubble.setLayout(new BoxLayout(bubble, BoxLayout.Y_AXIS));
        bubble.setOpaque(false);
        bubble.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        JLabel status = new JLabel(label);
        status.setFont(status.getFont().deriveFont(Font.PLAIN, 12f));
        bubble.add(card(accentColor("tool"), "🔧 Ferramenta", status));
        return new ToolCard(bubble, status);
    }

    private static void addTextAndAdmonitionCards(JPanel bubble, String text) {
        String[] lines = text.split("\r?\n", -1);
        StringBuilder plain = new StringBuilder();
        int i = 0;
        while (i < lines.length) {
            Matcher admonition = ADMONITION_START.matcher(lines[i]);
            if (admonition.matches()) {
                flushPlain(bubble, plain);
                String kind = admonition.group(1).toUpperCase(Locale.ROOT);
                StringBuilder body = new StringBuilder();
                i++;
                while (i < lines.length && lines[i].startsWith(">")) {
                    String line = lines[i].substring(1).stripLeading();
                    if (!body.isEmpty()) {
                        body.append('\n');
                    }
                    body.append(line);
                    i++;
                }
                bubble.add(spaced(admonitionCard(kind, body.toString())));
                continue;
            }
            if (!plain.isEmpty()) {
                plain.append('\n');
            }
            plain.append(lines[i]);
            i++;
        }
        flushPlain(bubble, plain);
    }

    private static void flushPlain(JPanel bubble, StringBuilder plain) {
        String text = plain.toString().strip();
        if (!text.isEmpty()) {
            bubble.add(spaced(card(accentColor("text"), null, textArea(text))));
        }
        plain.setLength(0);
    }

    private static JComponent admonitionCard(String kind, String body) {
        boolean warning = kind.equals("WARNING") || kind.equals("CAUTION");
        String header = warning ? "⚠ Atenção" : "ℹ Dica";
        return card(accentColor(warning ? "warning" : "info"), header, textArea(body));
    }

    private static String roleLabel(String role) {
        return switch (role) {
            case "user" -> "Você";
            case "assistant" -> "Assistente";
            case "tool" -> "Ferramenta";
            default -> role;
        };
    }

    private static JTextArea textArea(String text) {
        JTextArea area = new JTextArea(text);
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setOpaque(false);
        area.setFont(UIManager.getFont("Label.font"));
        area.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        return area;
    }

    private static JComponent codeCard(String code, String language, ChatActions actions) {
        boolean isSql = "sql".equalsIgnoreCase(language);
        String trimmedCode = code.stripTrailing();

        RSyntaxTextArea area = new RSyntaxTextArea(trimmedCode);
        area.setEditable(false);
        area.setSyntaxEditingStyle(styleFor(language));
        area.setCodeFoldingEnabled(false);
        area.setRows(Math.min(20, Math.max(1, trimmedCode.split("\n", -1).length)));
        area.setHighlightCurrentLine(false);

        RTextScrollPane scroll = new RTextScrollPane(area);
        scroll.setLineNumbersEnabled(false);
        scroll.setFoldIndicatorEnabled(false);
        scroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, scroll.getPreferredSize().height));
        scroll.setAlignmentX(JComponent.LEFT_ALIGNMENT);

        JPanel toolbar = new JPanel();
        toolbar.setLayout(new BoxLayout(toolbar, BoxLayout.X_AXIS));
        toolbar.setOpaque(false);
        toolbar.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        toolbar.add(Box.createHorizontalGlue());
        toolbar.add(button("Copiar", () -> Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection(area.getText()), null)));
        if (isSql) {
            toolbar.add(button("Formatar", () -> area.setText(actions.sqlFormatterSupplier().get().format(area.getText()))));
            toolbar.add(button("Explicar", () -> actions.onExplainSql().accept(area.getText())));
            toolbar.add(button("Executar", () -> actions.onExecuteSql().accept(area.getText())));
        }

        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setOpaque(false);
        body.add(toolbar);
        body.add(scroll);
        return card(accentColor(isSql ? "sql" : "code"), isSql ? "SQL" : language, body);
    }

    /** Container comum de todo card: tarja colorida a esquerda + cabecalho opcional + conteudo. */
    private static JComponent card(Color accent, String header, JComponent content) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setOpaque(false);
        Border matte = new MatteBorder(0, 3, 0, 0, accent);
        Border padding = BorderFactory.createEmptyBorder(4, 8, 4, 8);
        panel.setBorder(new CompoundBorder(matte, padding));
        panel.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        content.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        if (header != null && !header.isBlank()) {
            JLabel headerLabel = new JLabel(header);
            headerLabel.setFont(headerLabel.getFont().deriveFont(Font.BOLD, 11f));
            headerLabel.setForeground(accent);
            headerLabel.setAlignmentX(JComponent.LEFT_ALIGNMENT);
            panel.add(headerLabel);
            panel.add(Box.createVerticalStrut(2));
        }
        panel.add(content);
        return panel;
    }

    private static JComponent spaced(JComponent card) {
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setOpaque(false);
        wrapper.setBorder(BorderFactory.createEmptyBorder(3, 0, 3, 0));
        wrapper.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        wrapper.add(card, BorderLayout.CENTER);
        return wrapper;
    }

    private static JButton button(String label, Runnable action) {
        JButton button = new JButton(label);
        button.putClientProperty("JButton.buttonType", "toolBarButton");
        button.addActionListener(e -> action.run());
        return button;
    }

    private static String styleFor(String language) {
        if (language == null) {
            return SyntaxConstants.SYNTAX_STYLE_NONE;
        }
        return switch (language.toLowerCase(Locale.ROOT)) {
            case "sql" -> SyntaxConstants.SYNTAX_STYLE_SQL;
            case "java" -> SyntaxConstants.SYNTAX_STYLE_JAVA;
            case "json" -> SyntaxConstants.SYNTAX_STYLE_JSON;
            default -> SyntaxConstants.SYNTAX_STYLE_NONE;
        };
    }

    private static Color mutedColor() {
        Color c = UIManager.getColor("Label.disabledForeground");
        return c != null ? c : Color.GRAY;
    }

    /**
     * Cores de tarja por tipo de card. Sempre via {@code UIManager}/mistura com
     * cores neutras do tema atual (nunca RGB fixo de um unico tema) pra nao
     * destoar entre o tema claro e o escuro do FlatLaf.
     */
    private static Color accentColor(String kind) {
        return switch (kind) {
            case "error" -> themed(new Color(0xE5484D), new Color(0xF87171));
            case "warning" -> themed(new Color(0xB38600), new Color(0xF5C842));
            case "info" -> themed(new Color(0x2563EB), new Color(0x60A5FA));
            case "sql" -> themed(new Color(0x0F766E), new Color(0x2DD4BF));
            case "tool" -> themed(new Color(0x7C3AED), new Color(0xA78BFA));
            case "code" -> mutedColor();
            default -> mutedColor();
        };
    }

    private static Color themed(Color light, Color dark) {
        Color background = UIManager.getColor("Panel.background");
        boolean isDark = background != null
                && (background.getRed() * 0.299 + background.getGreen() * 0.587 + background.getBlue() * 0.114) < 128;
        return isDark ? dark : light;
    }
}
