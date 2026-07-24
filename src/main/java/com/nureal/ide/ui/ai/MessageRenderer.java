package com.nureal.ide.ui.ai;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.UIManager;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rsyntaxtextarea.Theme;

import com.nureal.ide.core.ai.tool.SqlQueryResult;
import com.nureal.ide.ui.GridExporter;
import com.nureal.ide.ui.MetadataTableStyle;
import com.nureal.ide.ui.SqlEditorPane;
import com.nureal.ide.ui.components.NAccent;
import com.nureal.ide.ui.components.NButton;
import com.nureal.ide.ui.components.NCard;
import com.nureal.ide.ui.components.NCodeBlock;
import com.nureal.ide.ui.components.NTheme;

/**
 * Renderiza o conteudo de uma mensagem do chat como uma pilha de "cards"
 * tipados — texto, SQL (com Copiar/Executar/Explicar/Formatar), codigo
 * generico, informacao/aviso (blocos de admonicao {@code > [!TIP]}/
 * {@code > [!WARNING]}, convencao GFM que os modelos ja conhecem), erro e
 * tool — em vez de um bloco de texto corrido. Cada card e um {@link NCard}
 * (Nureal Design System) — cor/fundo/cantos vem do componente compartilhado,
 * nao mais reimplementados aqui (era um {@code RoundedPanel} privado antes
 * do NDS existir). Blocos SQL reusam {@link SqlEditorPane#styleAsReadOnlySql}
 * — o MESMO tema/paleta semantica do editor principal, em vez de deixar o
 * {@code RSyntaxTextArea} no tema claro padrao da biblioteca. Continua sem
 * suporte a markdown completo (negrito/listas/tabelas/links): so titulos
 * (linhas {@code #}/{@code ##}/...), blocos de codigo e os cards acima.
 */
final class MessageRenderer {

    private static final Pattern CODE_FENCE = Pattern.compile("```(\\w*)\\r?\\n([\\s\\S]*?)```");
    private static final Pattern ADMONITION_START = Pattern.compile("^>\\s?\\[!(\\w+)]\\s*$");
    private static final Pattern HEADING = Pattern.compile("^(#{1,6})\\s+(.*)$");

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
        roleLabel.setForeground(NTheme.mutedColor());
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
        NCard card = new NCard(NAccent.ERROR, "❌ Erro");
        card.addContent(textArea(message));
        bubble.add(card);
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
        NCard card = new NCard(NAccent.TOOL, "🔧 Ferramenta");
        card.addContent(status);
        bubble.add(card);
        return new ToolCard(bubble, status);
    }

    /**
     * Card de resultado tabular de {@code ExecuteSqlTool} — substitui o card
     * de status "pendente" (ver {@link #toolCard}) quando a tool termina com
     * sucesso (ver {@code ChatPanel.ToolCardHandle#complete}). Tabela no
     * MESMO visual da grade de resultados (ver {@link MetadataTableStyle}).
     * "Ver SQL" so expande/colapsa a instrucao ja rodada (nao reexecuta
     * nada); o bloco expandido reusa {@link NCodeBlock}, entao ja ganha
     * "Copiar"/"Formatar"/"Executar" de graca, igual a qualquer outro bloco
     * SQL do chat. "Exportar resultado" reusa {@link GridExporter} — nenhuma
     * logica de exportacao nova.
     */
    static JComponent sqlResultCard(SqlQueryResult data, ChatActions actions) {
        JPanel bubble = new JPanel();
        bubble.setLayout(new BoxLayout(bubble, BoxLayout.Y_AXIS));
        bubble.setOpaque(false);
        bubble.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));

        NCard card = new NCard(NAccent.SQL, "📊 Resultado");

        DefaultTableModel model = new DefaultTableModel(data.columns().toArray(), 0) {
            private static final long serialVersionUID = 1L;

            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        for (List<Object> row : data.rows()) {
            model.addRow(row.toArray());
        }
        JTable table = MetadataTableStyle.createStyledTable(model);
        JScrollPane scroll = new JScrollPane(table);
        scroll.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        int rowsShown = Math.max(1, Math.min(data.rows().size(), 8));
        scroll.setPreferredSize(new Dimension(520, rowsShown * 24 + 28));

        JLabel summary = new JLabel(data.rows().size() + " linha(s)" + (data.truncated() ? " (limitado)" : ""));
        summary.setFont(summary.getFont().deriveFont(Font.PLAIN, 11f));
        summary.setForeground(NTheme.mutedColor());
        summary.setAlignmentX(JComponent.LEFT_ALIGNMENT);

        NCodeBlock sqlBlock = new NCodeBlock(data.sql(), NAccent.SQL, "SQL", SqlEditorPane::styleAsReadOnlySql);
        sqlBlock.addAction("Formatar",
                () -> sqlBlock.setCode(actions.sqlFormatterSupplier().get().format(sqlBlock.getCode())));
        sqlBlock.addAction("Executar", () -> actions.onExecuteSql().accept(sqlBlock.getCode()));
        sqlBlock.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        sqlBlock.setVisible(false);

        JButton toggleSql = new NButton("Ver SQL", NButton.Kind.GHOST);
        JButton exportButton = new NButton("Exportar resultado", NButton.Kind.GHOST);
        toggleSql.addActionListener(e -> {
            boolean showing = !sqlBlock.isVisible();
            sqlBlock.setVisible(showing);
            toggleSql.setText(showing ? "Ocultar SQL" : "Ver SQL");
            card.revalidate();
            card.repaint();
        });
        exportButton.addActionListener(e -> exportSqlResult(card, table));

        JPanel actionsRow = new JPanel();
        actionsRow.setLayout(new BoxLayout(actionsRow, BoxLayout.X_AXIS));
        actionsRow.setOpaque(false);
        actionsRow.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        actionsRow.add(toggleSql);
        actionsRow.add(exportButton);
        actionsRow.add(Box.createHorizontalGlue());

        card.addContent(scroll);
        card.addContent(summary);
        card.addContent(actionsRow);
        card.addContent(sqlBlock);

        bubble.add(card);
        return bubble;
    }

    private static void exportSqlResult(JComponent parent, JTable table) {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Exportar resultado");
        fc.setSelectedFile(new File("resultado.csv"));
        fc.setFileFilter(new FileNameExtensionFilter("CSV (*.csv)", "csv"));
        if (fc.showSaveDialog(parent) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File file = fc.getSelectedFile();
        if (!file.getName().toLowerCase(Locale.ROOT).endsWith(".csv")) {
            file = new File(file.getParentFile(), file.getName() + ".csv");
        }
        try {
            GridExporter.exportCsv(table, file.toPath());
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(parent, "Falha ao exportar resultado:\n" + ex.getMessage(),
                    "Exportar resultado", JOptionPane.ERROR_MESSAGE);
        }
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
            NCard card = new NCard();
            card.addContent(textArea(text));
            bubble.add(spaced(card));
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
        NCard card = new NCard(warning ? NAccent.WARNING : NAccent.INFO, warning ? "⚠ Atenção" : "ℹ Dica");
        card.addContent(textArea(body));
        return card;
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
        NCodeBlock.CodeStyler styler = isSql
                ? SqlEditorPane::styleAsReadOnlySql
                : area -> {
                    area.setEditable(false);
                    area.setSyntaxEditingStyle(styleFor(language));
                    area.setCodeFoldingEnabled(false);
                    themeGenericCode(area);
                };

        NCodeBlock block = new NCodeBlock(code, isSql ? NAccent.SQL : NAccent.NEUTRAL,
                isSql ? "SQL" : displayLanguage(language), styler);
        if (isSql) {
            block.addAction("Formatar", () -> block.setCode(actions.sqlFormatterSupplier().get().format(block.getCode())));
            block.addAction("Explicar", () -> actions.onExplainSql().accept(block.getCode()));
            block.addAction("Executar", () -> actions.onExecuteSql().accept(block.getCode()));
        }
        return block;
    }

    /**
     * Blocos de codigo NAO-sql (json/java/etc.) nao passam por
     * {@link SqlEditorPane#styleAsReadOnlySql} (especifico de SQL: tokenizer
     * proprio, paleta semantica por tipo de dado) — so garante que o tema
     * embutido do RSyntaxTextArea (fundo/texto) acompanhe claro/escuro, em
     * vez de ficar preso no branco padrao da biblioteca.
     */
    private static void themeGenericCode(RSyntaxTextArea area) {
        boolean dark = NTheme.isDark();
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

    private static JComponent spaced(JComponent card) {
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setOpaque(false);
        wrapper.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));
        wrapper.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        wrapper.add(card, BorderLayout.CENTER);
        return wrapper;
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
}
