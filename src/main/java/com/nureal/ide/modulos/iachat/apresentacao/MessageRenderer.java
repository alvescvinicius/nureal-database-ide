package com.nureal.ide.modulos.iachat.apresentacao;

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

import com.nureal.ide.modulos.iachat.infraestrutura.tool.SqlQueryResult;
import com.nureal.ide.ui.GridExporter;
import com.nureal.ide.ui.MetadataTableStyle;
import com.nureal.ide.ui.SqlEditorPane;
import com.nureal.ide.compartilhado.designsystem.GridTheme;
import com.nureal.ide.compartilhado.designsystem.IconType;
import com.nureal.ide.compartilhado.designsystem.NAccent;
import com.nureal.ide.compartilhado.designsystem.NButton;
import com.nureal.ide.compartilhado.designsystem.NCard;
import com.nureal.ide.compartilhado.designsystem.NCodeBlock;
import com.nureal.ide.compartilhado.designsystem.NTheme;

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
 * {@code RSyntaxTextArea} no tema claro padrao da biblioteca. Suporta
 * titulos (linhas {@code #}/{@code ##}/...), blocos de codigo, tabelas GFM
 * (renderizadas como {@link JTable} de verdade — ver {@link #tableCard}) e
 * os cards acima; ainda sem listas/links (paragrafo comum trata tudo o
 * resto como texto corrido).
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
        // Tarja lateral (neutra, NAO o verde da marca — pedido explicito do
        // usuario: verde fica reservado so pro que "e" verde de verdade,
        // como status de conexao; usar como decoracao generica no chat
        // "cansa") so na resposta do ASSISTENTE — achado na revisao de UX:
        // "Você" e "Assistente" eram visualmente IDENTICOS, so o rotulo de
        // texto acima mudava, dificil de escanear rapido numa conversa longa
        // rolando a lista. Mensagem do usuario fica sem tarja (neutra, "voce
        // ja sabe que escreveu isso"); 8px de recuo esquerdo viram 3px de
        // tarja + 5px de respiro (nao 8+3, pra nao empurrar o conteudo do
        // assistente mais pra direita que o do usuario).
        boolean assistant = "assistant".equals(role);
        bubble.setBorder(assistant
                ? BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0, 3, 0, 0, GridTheme.HEADER_BORDER),
                        BorderFactory.createEmptyBorder(6, 5, 6, 8))
                : BorderFactory.createEmptyBorder(6, 8, 6, 8));
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
        NCard card = new NCard(NAccent.ERROR, IconType.ERROR, "Erro");
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
        NCard card = new NCard(NAccent.TOOL, IconType.SETTINGS, "Ferramenta");
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

        NCard card = new NCard(NAccent.SQL, IconType.TABLE, "Resultado");

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

    /**
     * Titulos ({@code #}..{@code ######}), blocos de admonicao, tabelas GFM
     * ({@code | col | col |} + linha separadora {@code |---|---|}) e
     * paragrafos comuns — nessa ordem de deteccao por linha. Tabelas viram
     * um {@link JTable} de verdade (ver {@link #tableCard}), nao mais texto
     * cru com barras verticais — achado na revisao de UX do chat: respostas
     * com tabela (comum em "liste as colunas de X") apareciam com o
     * markdown sem processar, pipes e tudo, ilegivel.
     */
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
            if (isTableHeaderRow(lines, i)) {
                flushPlain(bubble, plain);
                List<String> headers = splitTableRow(lines[i]);
                i += 2; // pula o cabecalho e a linha separadora (|---|---|)
                List<List<String>> rows = new java.util.ArrayList<>();
                while (i < lines.length && looksLikeTableRow(lines[i])) {
                    rows.add(splitTableRow(lines[i]));
                    i++;
                }
                bubble.add(spaced(tableCard(headers, rows)));
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

    static boolean isTableHeaderRow(String[] lines, int i) {
        return looksLikeTableRow(lines[i]) && i + 1 < lines.length && isTableSeparatorRow(lines[i + 1]);
    }

    static boolean looksLikeTableRow(String line) {
        return line != null && line.contains("|") && !line.isBlank();
    }

    /** {@code |---|:--:|--:|} etc. — so os caracteres {@code - : | espaço}, com pelo menos um {@code -} e um {@code |}. */
    static boolean isTableSeparatorRow(String line) {
        String t = line.trim();
        if (!t.contains("|") || !t.contains("-")) {
            return false;
        }
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (c != '-' && c != ':' && c != '|' && c != ' ' && c != '\t') {
                return false;
            }
        }
        return true;
    }

    /** Divide {@code | a | b |} (barras nas pontas opcionais, GFM) em celulas, ja sem negrito/codigo inline. */
    static List<String> splitTableRow(String line) {
        String t = line.trim();
        if (t.startsWith("|")) {
            t = t.substring(1);
        }
        if (t.endsWith("|")) {
            t = t.substring(0, t.length() - 1);
        }
        List<String> cells = new java.util.ArrayList<>();
        for (String part : t.split("\\|", -1)) {
            cells.add(stripInlineMarkdown(part.trim()));
        }
        return cells;
    }

    /** Remove so a marcacao (negrito/codigo) inline mais comum — nao e um parser de markdown completo, so o suficiente pra celula de tabela ficar legivel. */
    static String stripInlineMarkdown(String s) {
        return s.replaceAll("\\*\\*(.*?)\\*\\*", "$1").replaceAll("`(.*?)`", "$1");
    }

    /** Tabela GFM renderizada como {@link JTable} de verdade — mesmo visual/estilo de {@link #sqlResultCard}. */
    private static JComponent tableCard(List<String> headers, List<List<String>> rows) {
        DefaultTableModel model = new DefaultTableModel(headers.toArray(), 0) {
            private static final long serialVersionUID = 1L;

            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        for (List<String> row : rows) {
            Object[] cells = new Object[headers.size()];
            for (int c = 0; c < headers.size(); c++) {
                cells[c] = c < row.size() ? row.get(c) : "";
            }
            model.addRow(cells);
        }
        JTable table = MetadataTableStyle.createStyledTable(model);
        JScrollPane scroll = new JScrollPane(table);
        scroll.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        int rowsShown = Math.max(1, Math.min(rows.size(), 8));
        scroll.setPreferredSize(new Dimension(520, rowsShown * 24 + 28));

        NCard card = new NCard();
        card.addContent(scroll);
        return card;
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
        NCard card = new NCard(warning ? NAccent.WARNING : NAccent.INFO,
                warning ? IconType.WARNING : IconType.INFO, warning ? "Atenção" : "Dica");
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
