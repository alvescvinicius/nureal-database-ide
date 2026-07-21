package com.nureal.ide.ui.ai;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.io.IOException;
import java.io.InputStream;
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

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rsyntaxtextarea.Theme;
import org.fife.ui.rtextarea.RTextScrollPane;

import com.formdev.flatlaf.FlatLaf;
import com.nureal.ide.ui.SqlEditorPane;

/**
 * Renderiza o conteudo de uma mensagem do chat como uma pilha de "cards"
 * tipados — texto, SQL (com Copiar/Executar/Explicar/Formatar), codigo
 * generico, informacao/aviso (blocos de admonicao {@code > [!TIP]}/
 * {@code > [!WARNING]}, convencao GFM que os modelos ja conhecem), erro e
 * tool — em vez de um bloco de texto corrido. Cada card e um painel com
 * cantos arredondados e fundo levemente destacado do fundo da janela (via
 * {@link #cardBackground()}, sempre derivado do tema atual — nunca uma cor
 * fixa), com o cabecalho tintado na cor do tipo. Blocos SQL reusam
 * {@link SqlEditorPane#styleAsReadOnlySql} — o MESMO tema/paleta semantica
 * do editor principal, em vez de deixar o {@code RSyntaxTextArea} no tema
 * claro padrao da biblioteca (destoava do resto da IDE no tema escuro).
 * Continua sem suporte a markdown completo (negrito/listas/tabelas/links):
 * so titulos (linhas {@code #}/{@code ##}/...), blocos de codigo e os
 * cards acima.
 */
final class MessageRenderer {

    private static final Pattern CODE_FENCE = Pattern.compile("```(\\w*)\\r?\\n([\\s\\S]*?)```");
    private static final Pattern ADMONITION_START = Pattern.compile("^>\\s?\\[!(\\w+)]\\s*$");
    private static final Pattern HEADING = Pattern.compile("^(#{1,6})\\s+(.*)$");
    private static final int CARD_ARC = 10;

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
        bubble.add(Box.createVerticalStrut(3));

        String text = content == null ? "" : content;
        Matcher matcher = CODE_FENCE.matcher(text);
        int last = 0;
        boolean foundAny = false;
        while (matcher.find()) {
            foundAny = true;
            String before = text.substring(last, matcher.start());
            if (!before.isBlank()) {
                addParsedTextCards(bubble, before.strip());
            }
            bubble.add(spaced(codeCard(matcher.group(2), matcher.group(1), actions)));
            last = matcher.end();
        }
        String rest = text.substring(last);
        if (!rest.isBlank() || !foundAny) {
            addParsedTextCards(bubble, rest.strip());
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

    /** Titulos ({@code #}..{@code ######}), blocos de admonicao e paragrafos comuns — nessa ordem de deteccao por linha. */
    private static void addParsedTextCards(JPanel bubble, String text) {
        String[] lines = text.split("\r?\n", -1);
        StringBuilder plain = new StringBuilder();
        int i = 0;
        while (i < lines.length) {
            Matcher heading = HEADING.matcher(lines[i]);
            Matcher admonition = ADMONITION_START.matcher(lines[i]);
            if (heading.matches()) {
                flushPlain(bubble, plain);
                bubble.add(spaced(headingLabel(heading.group(1).length(), heading.group(2))));
                i++;
                continue;
            }
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
            bubble.add(spaced(card(null, null, textArea(text))));
        }
        plain.setLength(0);
    }

    private static JComponent headingLabel(int level, String text) {
        JLabel label = new JLabel(text.strip());
        float size = switch (level) {
            case 1 -> 15f;
            case 2 -> 14f;
            default -> 13f;
        };
        label.setFont(label.getFont().deriveFont(Font.BOLD, size));
        label.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        label.setBorder(BorderFactory.createEmptyBorder(4, 2, 2, 2));
        return label;
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
        if (isSql) {
            SqlEditorPane.styleAsReadOnlySql(area);
        } else {
            area.setEditable(false);
            area.setSyntaxEditingStyle(styleFor(language));
            area.setCodeFoldingEnabled(false);
            themeGenericCode(area);
        }
        area.setRows(Math.min(20, Math.max(1, trimmedCode.split("\n", -1).length)));
        area.setHighlightCurrentLine(false);

        RTextScrollPane scroll = new RTextScrollPane(area);
        scroll.setLineNumbersEnabled(false);
        scroll.setFoldIndicatorEnabled(false);
        scroll.setBackground(area.getBackground());
        scroll.getGutter().setBackground(area.getBackground());
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
        body.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        body.add(toolbar);
        body.add(Box.createVerticalStrut(4));
        body.add(scroll);
        return card(accentColor(isSql ? "sql" : "code"), isSql ? "SQL" : displayLanguage(language), body);
    }

    /**
     * Blocos de codigo NAO-sql (json/java/etc.) nao passam por
     * {@link SqlEditorPane#styleAsReadOnlySql} (especifico de SQL: tokenizer
     * proprio, paleta semantica por tipo de dado) — so garante que o tema
     * embutido do RSyntaxTextArea (fundo/texto) acompanhe claro/escuro, em
     * vez de ficar preso no branco padrao da biblioteca.
     */
    private static void themeGenericCode(RSyntaxTextArea area) {
        boolean dark = FlatLaf.isLafDark();
        String resource = dark ? "/org/fife/ui/rsyntaxtextarea/themes/dark.xml"
                : "/org/fife/ui/rsyntaxtextarea/themes/default.xml";
        try (InputStream in = MessageRenderer.class.getResourceAsStream(resource)) {
            if (in != null) {
                Theme.load(in).apply(area);
            }
        } catch (IOException ignored) {
            // Segue com o que a area ja tinha.
        }
        area.setBackground(dark ? new Color(0x1E, 0x1E, 0x1E) : Color.WHITE);
        area.setForeground(dark ? new Color(0xD4, 0xD4, 0xD4) : Color.BLACK);
    }

    private static String displayLanguage(String language) {
        return language == null || language.isBlank() ? "Código" : language.toUpperCase(Locale.ROOT);
    }

    /**
     * Container comum de todo card: painel arredondado com fundo levemente
     * destacado do fundo da janela (ver {@link #cardBackground()}) e
     * cabecalho opcional tintado na cor do tipo. {@code accent}/{@code header}
     * nulos (paragrafo de texto simples) pulam o cabecalho, so o fundo.
     */
    private static JComponent card(Color accent, String header, JComponent content) {
        RoundedPanel panel = new RoundedPanel(cardBackground());
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(7, 10, 7, 10));
        panel.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        content.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        if (header != null && !header.isBlank()) {
            JLabel headerLabel = new JLabel(header);
            headerLabel.setFont(headerLabel.getFont().deriveFont(Font.BOLD, 11f));
            headerLabel.setForeground(accent);
            headerLabel.setAlignmentX(JComponent.LEFT_ALIGNMENT);
            panel.add(headerLabel);
            panel.add(Box.createVerticalStrut(3));
        }
        panel.add(content);
        return panel;
    }

    private static JComponent spaced(JComponent card) {
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setOpaque(false);
        wrapper.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));
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
     * Cores de cabecalho por tipo de card. Sempre via {@code UIManager}/mistura
     * com cores neutras do tema atual (nunca RGB fixo de um unico tema) pra nao
     * destoar entre o tema claro e o escuro do FlatLaf.
     */
    private static Color accentColor(String kind) {
        return switch (kind) {
            case "error" -> themed(new Color(0xE5484D), new Color(0xF87171));
            case "warning" -> themed(new Color(0xB38600), new Color(0xF5C842));
            case "info" -> themed(new Color(0x2563EB), new Color(0x60A5FA));
            case "sql" -> themed(new Color(0x0F766E), new Color(0x2DD4BF));
            case "tool" -> themed(new Color(0x7C3AED), new Color(0xA78BFA));
            default -> mutedColor();
        };
    }

    private static Color themed(Color light, Color dark) {
        return isDarkTheme() ? dark : light;
    }

    /** Fundo do card: {@code Panel.background} do tema atual, um pouco mais claro (escuro) ou mais escuro (claro) — nunca fixo. */
    private static Color cardBackground() {
        Color base = UIManager.getColor("Panel.background");
        if (base == null) {
            return new Color(0, 0, 0, 20);
        }
        int delta = isDarkTheme() ? 18 : -10;
        return new Color(clamp(base.getRed() + delta), clamp(base.getGreen() + delta), clamp(base.getBlue() + delta));
    }

    private static boolean isDarkTheme() {
        Color background = UIManager.getColor("Panel.background");
        return background != null
                && (background.getRed() * 0.299 + background.getGreen() * 0.587 + background.getBlue() * 0.114) < 128;
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }

    /** Painel com cantos arredondados e fundo proprio — Swing nao tem isso pronto sem pintura customizada. */
    private static final class RoundedPanel extends JPanel {
        private static final long serialVersionUID = 1L;
        private final Color fill;

        RoundedPanel(Color fill) {
            this.fill = fill;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(fill);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), MessageRenderer.CARD_ARC, MessageRenderer.CARD_ARC);
            g2.dispose();
            super.paintComponent(g);
        }
    }
}
