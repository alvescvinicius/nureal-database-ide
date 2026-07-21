package com.nureal.ide.ui.ai;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.UIManager;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rtextarea.RTextScrollPane;

/**
 * Renderiza o conteudo (markdown minimo) de uma mensagem do chat: texto
 * simples + blocos de codigo ```sql/```java/``` com highlight (reusa
 * {@code RSyntaxTextArea}, ja dependencia do projeto) e botao "Copiar" — ver
 * {@code docs/035-Message-Renderer.md}. Sem suporte a markdown completo
 * (negrito/listas/etc.) no MVP — fora do escopo da fase 1 do chat.
 */
final class MessageRenderer {

    private static final Pattern CODE_FENCE = Pattern.compile("```(\\w*)\\r?\\n([\\s\\S]*?)```");

    private MessageRenderer() {
    }

    static JComponent render(String role, String content) {
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

        String text = content == null ? "" : content;
        Matcher matcher = CODE_FENCE.matcher(text);
        int last = 0;
        boolean foundAny = false;
        while (matcher.find()) {
            foundAny = true;
            String before = text.substring(last, matcher.start());
            if (!before.isBlank()) {
                bubble.add(textBlock(before.strip()));
            }
            bubble.add(codeBlock(matcher.group(2), matcher.group(1)));
            last = matcher.end();
        }
        String rest = text.substring(last);
        if (!rest.isBlank() || !foundAny) {
            bubble.add(textBlock(rest.strip()));
        }
        return bubble;
    }

    private static String roleLabel(String role) {
        return switch (role) {
            case "user" -> "Você";
            case "assistant" -> "Assistente";
            case "tool" -> "Ferramenta";
            default -> role;
        };
    }

    private static JComponent textBlock(String text) {
        JTextArea area = new JTextArea(text);
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setOpaque(false);
        area.setFont(UIManager.getFont("Label.font"));
        area.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));
        area.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        return area;
    }

    private static JComponent codeBlock(String code, String language) {
        RSyntaxTextArea area = new RSyntaxTextArea(code.stripTrailing());
        area.setEditable(false);
        area.setSyntaxEditingStyle(styleFor(language));
        area.setCodeFoldingEnabled(false);
        area.setRows(Math.min(20, Math.max(1, code.stripTrailing().split("\n", -1).length)));
        area.setHighlightCurrentLine(false);

        RTextScrollPane scroll = new RTextScrollPane(area);
        scroll.setLineNumbersEnabled(false);
        scroll.setFoldIndicatorEnabled(false);
        scroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, scroll.getPreferredSize().height));
        scroll.setAlignmentX(JComponent.LEFT_ALIGNMENT);

        JButton copyButton = new JButton("Copiar");
        copyButton.addActionListener(e -> Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection(code.stripTrailing()), null));

        JPanel toolbar = new JPanel(new BorderLayout());
        toolbar.setOpaque(false);
        toolbar.add(copyButton, BorderLayout.EAST);
        toolbar.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        toolbar.setMaximumSize(new Dimension(Integer.MAX_VALUE, copyButton.getPreferredSize().height));

        JPanel wrapper = new JPanel();
        wrapper.setLayout(new BoxLayout(wrapper, BoxLayout.Y_AXIS));
        wrapper.setOpaque(false);
        wrapper.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        wrapper.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));
        wrapper.add(toolbar);
        wrapper.add(scroll);
        return wrapper;
    }

    private static String styleFor(String language) {
        if (language == null) {
            return SyntaxConstants.SYNTAX_STYLE_NONE;
        }
        return switch (language.toLowerCase()) {
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
}
