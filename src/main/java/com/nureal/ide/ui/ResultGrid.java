package com.nureal.ide.ui;

import com.nureal.ide.core.connection.ConnectionManager;

import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.RowSorter;
import javax.swing.SortOrder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.JTableHeader;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.IntUnaryOperator;

/**
 * Componente PROPRIO da IDE para exibir o resultado de uma consulta — nao um
 * {@code JTable} "customizado na mao" dentro de {@code MainWindow}, mas uma
 * unidade coesa que monta e possui toda a experiencia da grade: tabela,
 * cabecalho, numeracao de linhas, filtro, selecao, ordenacao, menus de
 * contexto e persistencia de layout. {@code MainWindow} so precisa
 * instanciar esta classe e encaixar o componente retornado por
 * {@link #asComponent()} — o resto (barra de paginacao, abas) continua sendo
 * responsabilidade dela, pois depende do ciclo de vida do cursor JDBC.
 *
 * Cada instancia e independente; nao ha estado compartilhado entre grades
 * alem do {@link TableMetadataCache} (compartilhado de proposito, ver
 * construtor) e do arquivo de {@link GridPreferences} (por fingerprint de
 * colunas).
 */
final class ResultGrid extends JPanel {

    private static final long serialVersionUID = 1L;

    private final JTable table;
    private final ColumnSorter sorter;
    private final String fingerprint;
    private final JComboBox<String> filterColumnBox = new JComboBox<>();
    private final JTextField filterField = new JTextField(20);
    /** Campo curto e independente do filtro de valores: so navega ate a coluna, nunca restringe linhas (ver {@link #searchColumn}). */
    private final JTextField columnSearchField = new JTextField(10);
    private final GridEditController editController;
    private final ColumnHeaderRenderer headerRenderer;
    /**
     * Notificado sempre que a selecao de celulas muda, com a contagem de
     * celulas selecionadas e a soma dos valores numericos entre elas
     * ({@code null} quando nenhuma e numerica) — ver {@link #updateSelectionSummary()}.
     * {@code MainWindow} liga isto a {@link ResultStatusBar#updateSelectionSummary}.
     * Default no-op: a grade funciona sozinha (testes, uso fora do MainWindow)
     * mesmo sem ninguem ouvindo.
     */
    private java.util.function.BiConsumer<Integer, java.math.BigDecimal> onSelectionSummary = (count, sum) -> { };

    /**
     * @param model            dados + metadados da consulta (ver {@link ResultTableModel})
     * @param connectionManager conexao usada para carregar PK/FK/indices sob demanda
     * @param schema           schema atual (pode ser {@code null})
     * @param metadataCache    cache de metadados de tabela, COMPARTILHADO entre todas as grades da sessao
     * @param exportExcel      acao "Exportar Excel" (delegada a MainWindow, que ja sabe exportar varias abas)
     * @param scale            funcao de escala de UI (zoom) — mesma usada pelo resto da janela
     */
    ResultGrid(ResultTableModel model, ConnectionManager connectionManager, String schema,
            TableMetadataCache metadataCache, Runnable exportExcel, IntUnaryOperator scale) {
        super(new BorderLayout());

        // Sempre criado (nunca null) mas so vira editavel de fato quando
        // MainWindow chama editController().enable(target) apos resolver que
        // este resultado e um SELECT simples de uma tabela com PK conhecida
        // — ver MainWindow#tryEnableEditing. Enquanto isso, isEditable()
        // volta false e a grade permanece somente-leitura, como sempre foi.
        this.editController = new GridEditController(model);
        model.setEditController(editController);

        this.table = new JTable(model) {
            private static final long serialVersionUID = 1L;

            @Override
            public String getToolTipText(MouseEvent e) {
                return cellTooltip(this, e);
            }
        };
        // getToolTipText(MouseEvent) sozinho nao basta: o ToolTipManager so
        // consulta um componente depois que ele foi registrado nele (o que
        // setToolTipText(...) faz implicitamente) — sem isto, a celula nunca
        // mostraria tooltip mesmo com o metodo sobrescrito corretamente.
        javax.swing.ToolTipManager.sharedInstance().registerComponent(table);
        styleTable(table, scale);

        // Resolver de metadados (PK/FK/indices/comentario) desta grade
        // especifica — guardado como client property ANTES de instalar os
        // renderers para que AbstractTypedCellRenderer consiga destacar
        // colunas que sao chave primaria/estrangeira de verdade (ver
        // RendererFactory#KEY_METADATA_RESOLVER), o mesmo resolver usado pelo
        // indicador de FK do cabecalho e pelo popup de metadados.
        ColumnMetadataResolver resolver = new ColumnMetadataResolver(metadataCache, connectionManager, schema);
        table.putClientProperty(RendererFactory.KEY_METADATA_RESOLVER, resolver);
        RendererFactory.installOn(table, model);

        this.sorter = new ColumnSorter(table);

        ColumnHeaderRenderer.MetadataSource metadataSource =
                col -> resolver.resolve(model, col, () -> table.getTableHeader().repaint());

        SelectionManager selection = SelectionManager.install(table);

        // Resumo de selecao (contagem + soma): ouve os DOIS modelos de
        // selecao (linha E coluna) separadamente — um arrasto horizontal
        // dentro de uma unica linha, por exemplo, muda so o modelo de
        // coluna, nunca o de linha (ver SelectionManager#installBodyMouseHandling),
        // entao ouvir so um dos dois deixaria casos assim sem atualizar.
        javax.swing.event.ListSelectionListener selectionSummaryListener = e -> {
            if (!e.getValueIsAdjusting()) {
                updateSelectionSummary();
            }
        };
        table.getSelectionModel().addListSelectionListener(selectionSummaryListener);
        table.getColumnModel().getSelectionModel().addListSelectionListener(selectionSummaryListener);

        JComponent corner = RowNumberGutter.corner();
        this.headerRenderer = new ColumnHeaderRenderer(sorter);
        JTableHeader header = ResultTableHeader.install(table, sorter, selection, metadataSource, headerRenderer);
        applyHeaderHeight(header, scale);
        selection.installCorner(corner, this::persistLayout);

        this.fingerprint = GridPreferences.fingerprint(columnNames(model));
        applyPersistedLayoutOrAutoFit(model);

        ResultContextMenu.FilterController filterController = new ResultContextMenu.FilterController() {
            @Override
            public void filterByValue(int modelColumn, String value) {
                setFilter(modelColumn, "=" + value);
            }

            @Override
            public void clearFilter() {
                clearFilterUi();
            }
        };
        // "Visualizar Origem" (Inspetor Flutuante de FK): monta os valores
        // das coluna(s) LOCAIS da chave estrangeira NESTA linha (na MESMA
        // ordem de ForeignKeyInfo#columns() — suporta FK composta) e abre o
        // FkInspectorWindow ja filtrado por eles. Uma coluna local que nao
        // esteja presente neste resultado (SELECT nao trouxe aquele campo)
        // vira null na lista — o inspetor so filtra pelas que resolveu.
        ResultContextMenu.FkOriginHandler fkOrigin = (colMeta, viewRow) -> {
            int modelRow = table.convertRowIndexToModel(viewRow);
            List<Object> localValues = new ArrayList<>();
            for (String localCol : colMeta.foreignKey().columns()) {
                localValues.add(findColumnValue(model, colMeta.sourceTable(), localCol, modelRow));
            }
            FkInspectorWindow.open(DialogUtil.owner(table), connectionManager, schema, metadataCache, scale,
                    colMeta.foreignKey(), localValues);
        };
        ResultContextMenu.install(table, sorter, metadataSource, filterController, exportExcel, fkOrigin);
        ResultHeaderContextMenu.install(table, header, sorter, metadataSource, filterController, this::persistLayout);
        ResultContextMenu.installOnCorner(corner, table, sorter, metadataSource, filterController, exportExcel);

        header.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseReleased(MouseEvent e) {
                persistLayout();
            }
        });
        sorter.rowSorter().addRowSorterListener(e -> persistLayout());

        JScrollPane scroll = new JScrollPane(table);
        javax.swing.JList<String> rowGutter = RowNumberGutter.build(table, model, selection);
        ResultContextMenu.installOnRowGutter(rowGutter, table, sorter, metadataSource, filterController, exportExcel,
                selection);
        scroll.setRowHeaderView(rowGutter);
        scroll.setCorner(JScrollPane.UPPER_LEFT_CORNER, corner);

        add(buildFilterBar(model), BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
    }

    /**
     * Acha, entre as colunas do resultado, aquela cuja tabela/coluna REAIS de
     * origem (ver {@code ResultTableModel#sourceTable}/{@code #realColumnName})
     * batem com {@code sourceTable}/{@code localColumnName} (case-insensitive)
     * e devolve o valor dela em {@code modelRow} — usado por "Visualizar
     * Origem" para montar o filtro do {@link FkInspectorWindow}. {@code null}
     * se a coluna nao estiver neste resultado (SELECT parcial).
     */
    private static Object findColumnValue(ResultTableModel model, String sourceTable, String localColumnName,
            int modelRow) {
        for (int c = 0; c < model.getColumnCount(); c++) {
            String rc = model.realColumnName(c);
            String st = model.sourceTable(c);
            if (rc != null && rc.equalsIgnoreCase(localColumnName)
                    && (sourceTable == null || st == null || st.equalsIgnoreCase(sourceTable))) {
                return model.getValueAt(modelRow, c);
            }
        }
        return null;
    }

    JTable table() {
        return table;
    }

    /** Liga o callback de resumo de selecao (ver campo {@link #onSelectionSummary}). */
    void onSelectionSummary(java.util.function.BiConsumer<Integer, java.math.BigDecimal> listener) {
        this.onSelectionSummary = listener;
    }

    JComponent asComponent() {
        return this;
    }

    GridEditController editController() {
        return editController;
    }

    /** Linhas selecionadas convertidas para indices de MODELO (ver {@link GridEditController}). */
    int[] selectedModelRows() {
        int[] viewRows = table.getSelectedRows();
        int[] modelRows = new int[viewRows.length];
        for (int i = 0; i < viewRows.length; i++) {
            modelRows[i] = table.convertRowIndexToModel(viewRows[i]);
        }
        return modelRows;
    }

    /**
     * Adiciona uma linha nova (via {@link GridEditController#addNewRow()}) e
     * garante que o usuario a VEJA: limpa qualquer filtro ativo (uma linha em
     * branco recem-criada quase nunca bate com um filtro de texto/numero em
     * vigor — sem isto ela some da vista assim que criada, parecendo que o
     * botao "Nova linha" nao fez nada) e rola/seleciona ate ela.
     */
    void addNewRowAndReveal() {
        clearFilterUi();
        int modelRow = editController.addNewRow();
        int viewRow = table.convertRowIndexToView(modelRow);
        if (viewRow >= 0) {
            table.setRowSelectionInterval(viewRow, viewRow);
            table.scrollRectToVisible(table.getCellRect(viewRow, 0, true));
        }
    }

    // ---------- Estilo base da tabela ----------

    private static void styleTable(JTable table, IntUnaryOperator scale) {
        table.setRowHeight(scale.applyAsInt(22));
        table.setShowGrid(true);
        table.setGridColor(GridTheme.GRID_LINE);
        table.setIntercellSpacing(new Dimension(0, 1));
        table.setFillsViewportHeight(true);
        // CRITICO para o redimensionamento "estilo Excel": sem isto, o modo
        // PADRAO do JTable (AUTO_RESIZE_SUBSEQUENT_COLUMNS) forca a SOMA das
        // larguras de todas as colunas a caber sempre no viewport — com
        // varias colunas, isso espreme cada uma de volta ate a largura
        // minima (inclusive desfazendo o autofit) e faz qualquer arraste no
        // divisor do cabecalho parecer "nao fazer nada" (nao ha como roubar
        // espaco de vizinhos que ja estao no minimo). Com AUTO_RESIZE_OFF,
        // cada coluna mantem exatamente a largura que o autofit ou o usuario
        // definiu, e o JScrollPane que envolve a tabela mostra barra de
        // rolagem horizontal quando a soma excede o viewport — exatamente
        // como o Excel se comporta.
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.setAutoCreateRowSorter(false); // ColumnSorter cuida da ordenacao (evita 2 mecanismos concorrentes)
        table.setCellSelectionEnabled(true);
        // Selecao em cinza neutro: as cores por tipo de dado continuam visiveis por cima.
        table.setSelectionBackground(GridTheme.SELECTION_BACKGROUND);
        table.setSelectionForeground(GridTheme.SELECTION_FOREGROUND);
        // minWidth e um limite DURO do proprio Swing: passado disto, o
        // usuario simplesmente NAO CONSEGUE arrastar a divisoria do
        // cabecalho. De proposito um valor MINUSCULO e INDEPENDENTE da
        // largura padrao (ColumnAutoFit.DEFAULT_WIDTH_CHARS) — o padrao e so
        // o ponto de partida visual; a partir dai o usuario tem controle
        // TOTAL para aumentar OU diminuir qualquer coluna, inclusive para
        // bem menos que o padrao. So existe para a coluna nunca desaparecer
        // por completo (ficar impossivel de agarrar para redimensionar de
        // volta).
        int hardMinWidth = scale.applyAsInt(24);
        for (int c = 0; c < table.getColumnModel().getColumnCount(); c++) {
            table.getColumnModel().getColumn(c).setMinWidth(hardMinWidth);
        }
    }

    /**
     * Recalcula quantas celulas estao selecionadas e a soma dos valores
     * numericos entre elas, e repassa para quem estiver ouvindo (ver
     * {@link #onSelectionSummary}). Iterar {@code isCellSelected} sobre o
     * produto linhas-selecionadas x colunas-selecionadas (em vez de assumir
     * que toda combinacao esta selecionada) e o jeito robusto de contar a
     * selecao de fato — no Swing, linhas e colunas selecionadas sao dois
     * modelos independentes, e o retangulo que os dois juntos "sugerem" nem
     * sempre e o que esta realmente marcado.
     */
    private void updateSelectionSummary() {
        int[] rows = table.getSelectedRows();
        int[] cols = table.getSelectedColumns();
        int count = 0;
        java.math.BigDecimal sum = java.math.BigDecimal.ZERO;
        boolean hasNumeric = false;
        for (int row : rows) {
            for (int col : cols) {
                if (!table.isCellSelected(row, col)) {
                    continue;
                }
                count++;
                java.math.BigDecimal n = numericValue(table.getValueAt(row, col));
                if (n != null) {
                    sum = sum.add(n);
                    hasNumeric = true;
                }
            }
        }
        onSelectionSummary.accept(count, hasNumeric ? sum : null);
    }

    /** {@code null} para qualquer coisa que nao seja um numero (texto, data, nulo, booleano...). */
    private static java.math.BigDecimal numericValue(Object value) {
        if (value instanceof java.math.BigDecimal bd) {
            return bd;
        }
        if (!(value instanceof Number number)) {
            return null;
        }
        try {
            return new java.math.BigDecimal(number.toString());
        } catch (NumberFormatException ex) {
            return null; // NaN/Infinity de um Double/Float, por exemplo
        }
    }

    /**
     * Tooltip da CELULA (nao do cabecalho — ver {@link ColumnMetadataPopup}):
     * so aparece quando o texto exibido esta truncado (ver {@link CellText}),
     * e mostra o mesmo texto que {@link AbstractTypedCellRenderer#formatValue}
     * produziu — que, para Strings comuns, JA E o valor inteiro (barato,
     * sem custo extra), e para BLOB/CLOB e o resumo/previa curta e segura
     * que o renderer decidiu mostrar (evita ler o banco a cada hover; o
     * conteudo de fato completo fica no {@link CellContentViewer}).
     */
    private static String cellTooltip(JTable table, MouseEvent e) {
        int row = table.rowAtPoint(e.getPoint());
        int col = table.columnAtPoint(e.getPoint());
        if (row < 0 || col < 0) {
            return null;
        }
        Object value = table.getValueAt(row, col);
        if (value == null) {
            return null;
        }
        String display = (table.getCellRenderer(row, col) instanceof AbstractTypedCellRenderer typed)
                ? typed.formatValue(value) : value.toString();
        if (!CellText.isTruncated(display)) {
            return null; // cabe na celula, tooltip so atrapalharia
        }
        return "<html><div style='width:420px;'>" + escapeHtml(display) + "</div></html>";
    }

    private static String escapeHtml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\n", "<br>");
    }

    private static void applyHeaderHeight(JTableHeader header, IntUnaryOperator scale) {
        Dimension d = header.getPreferredSize();
        d.height = Math.max(d.height, scale.applyAsInt(30));
        header.setPreferredSize(d);
    }

    // ---------- Filtro ----------

    private JComponent buildFilterBar(ResultTableModel model) {
        filterColumnBox.addItem("Todas as colunas");
        for (int c = 0; c < model.getColumnCount(); c++) {
            filterColumnBox.addItem(model.getColumnName(c));
        }
        filterField.putClientProperty("JTextField.placeholderText", "Filtrar...  (ex: >= 2026-06-01)");
        filterField.putClientProperty("JTextField.showClearButton", true);
        filterField.setToolTipText("<html>Filtro inteligente:<br>"
                + "&bull; texto: <b>contem</b> (ex: silva)<br>"
                + "&bull; operadores: <b>&gt;= &lt;= &gt; &lt; = &lt;&gt;</b> (ex: &gt;= 2026-06-01, &gt; 100)<br>"
                + "&bull; intervalo: <b>a..b</b> (ex: 2026-01-01..2026-06-30)<br>"
                + "&bull; prefixo/sufixo: <b>^abc</b> / <b>abc$</b><br>"
                + "&bull; <b>NULL</b> / <b>NOT NULL</b><br>"
                + "Entende data e numero mesmo em colunas de texto.</html>");

        Runnable apply = () -> {
            int modelColumn = filterColumnBox.getSelectedIndex() - 1;
            sorter.rowSorter().setRowFilter(SmartCellFilter.build(filterField.getText(), modelColumn));
        };
        filterField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { apply.run(); }
            @Override public void removeUpdate(DocumentEvent e) { apply.run(); }
            @Override public void changedUpdate(DocumentEvent e) { apply.run(); }
        });
        // O combo de colunas ja funciona como "busca de coluna" (JComboBox
        // pesquisa por digitacao nativamente): alem de restringir o filtro a
        // ela, leva a visao ate a coluna escolhida e marca seu cabecalho —
        // pedido explicito do usuario para achar uma coluna em grades largas.
        filterColumnBox.addActionListener(e -> {
            apply.run();
            highlightSelectedColumn();
        });

        // Campo dedicado SO para achar uma coluna (nao filtra linhas, nao
        // depende de abrir o combo e rolar por uma lista enorme): a cada
        // tecla, localiza a coluna cujo nome mais se aproxima do texto
        // digitado e rola/realca o cabecalho dela — pedido explicito do
        // usuario para tabelas com muitas colunas.
        columnSearchField.putClientProperty("JTextField.placeholderText", "Buscar coluna...");
        columnSearchField.putClientProperty("JTextField.showClearButton", true);
        columnSearchField.setToolTipText("Digite parte do nome da coluna: a grade rola ate ela e destaca o cabecalho.");
        columnSearchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { searchColumn(); }
            @Override public void removeUpdate(DocumentEvent e) { searchColumn(); }
            @Override public void changedUpdate(DocumentEvent e) { searchColumn(); }
        });

        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 3));
        JLabel columnLabel = new JLabel("Coluna:");
        columnLabel.setForeground(GridTheme.MUTED_TEXT);
        bar.add(columnLabel);
        bar.add(columnSearchField);
        JLabel label = new JLabel("Filtro:");
        label.setForeground(GridTheme.MUTED_TEXT);
        bar.add(label);
        bar.add(filterColumnBox);
        bar.add(filterField);
        return bar;
    }

    private void setFilter(int modelColumn, String value) {
        filterColumnBox.setSelectedIndex(modelColumn + 1);
        filterField.setText(value);
    }

    private void clearFilterUi() {
        filterField.setText("");
        filterColumnBox.setSelectedIndex(0);
        columnSearchField.setText("");
    }

    /**
     * Rola a grade ate a coluna escolhida no {@link #filterColumnBox} e marca
     * seu cabecalho (ver {@link ColumnHeaderRenderer#setHighlight}) — assim o
     * usuario acha visualmente a coluna pesquisada mesmo com muitas colunas
     * fora da area visivel. Selecionar "Todas as colunas" (indice 0) limpa a
     * marcacao. Coluna oculta (ver {@link ColumnVisibility}) so ganha a
     * marcacao — nao ha o que rolar ate, pois nao esta na view.
     */
    private void highlightSelectedColumn() {
        int modelColumn = filterColumnBox.getSelectedIndex() - 1;
        headerRenderer.setHighlight(modelColumn);
        table.getTableHeader().repaint();
        if (modelColumn < 0) {
            return;
        }
        int viewColumn = table.convertColumnIndexToView(modelColumn);
        if (viewColumn >= 0) {
            scrollToColumn(viewColumn);
        }
    }

    /**
     * Localiza a coluna mais proxima do texto de {@link #columnSearchField} a
     * CADA tecla digitada e rola/realca ate ela — independente do
     * {@link #filterColumnBox} (nao muda o filtro de linhas em vigor, so
     * ajuda a "achar" a coluna em tabelas com muitas colunas, onde navegar
     * pelo combo rolando um por um e penoso). Texto vazio ou sem
     * correspondencia so limpa o realce.
     */
    private void searchColumn() {
        String query = columnSearchField.getText().trim();
        if (query.isEmpty()) {
            clearColumnHighlight();
            return;
        }
        int viewColumn = findVisibleColumn(query);
        if (viewColumn < 0) {
            clearColumnHighlight();
            return;
        }
        headerRenderer.setHighlight(table.convertColumnIndexToModel(viewColumn));
        table.getTableHeader().repaint();
        scrollToColumn(viewColumn);
    }

    /**
     * Indice de VIEW da coluna visivel que melhor bate com {@code query}
     * (case-insensitive): igualdade exata > comeca com > contem — nessa
     * ordem de preferencia, para digitar as primeiras letras ja pular direto
     * pra coluna certa mesmo quando o texto tambem aparece no meio de outros
     * nomes. So considera colunas visiveis (rolar ate uma oculta nao faz
     * sentido); {@code -1} se nenhuma bater.
     */
    private int findVisibleColumn(String query) {
        String needle = query.toLowerCase(Locale.ROOT);
        int prefixMatch = -1;
        int containsMatch = -1;
        for (int v = 0; v < table.getColumnCount(); v++) {
            String lower = table.getColumnName(v).toLowerCase(Locale.ROOT);
            if (lower.equals(needle)) {
                return v;
            }
            if (prefixMatch < 0 && lower.startsWith(needle)) {
                prefixMatch = v;
            }
            if (containsMatch < 0 && lower.contains(needle)) {
                containsMatch = v;
            }
        }
        return prefixMatch >= 0 ? prefixMatch : containsMatch;
    }

    private void clearColumnHighlight() {
        headerRenderer.setHighlight(-1);
        table.getTableHeader().repaint();
    }

    /** Rola so no eixo horizontal ate a coluna informada, preservando a posicao vertical atual. */
    private void scrollToColumn(int viewColumn) {
        Rectangle visible = table.getVisibleRect();
        Rectangle cell = table.getCellRect(0, viewColumn, true);
        table.scrollRectToVisible(new Rectangle(cell.x, visible.y, cell.width, visible.height));
    }

    // ---------- Persistencia (largura/ocultas/ordenacao) ----------

    private static List<String> columnNames(ResultTableModel model) {
        List<String> names = new ArrayList<>();
        for (int c = 0; c < model.getColumnCount(); c++) {
            names.add(model.getColumnName(c));
        }
        return names;
    }

    private void applyPersistedLayoutOrAutoFit(ResultTableModel model) {
        GridPreferences.Snapshot snapshot = GridPreferences.load(fingerprint);

        if (snapshot.widths().isEmpty()) {
            // Primeira vez que este "formato" de resultado (mesmas colunas) e
            // exibido: cada coluna ja nasce ajustada ao proprio conteudo
            // (cabecalho OU celula, o que for maior — igual ao "AutoFit
            // Todas" do menu de contexto), em vez da largura uniforme antiga
            // que cortava nomes de coluna longos por padrao — pedido
            // explicito do usuario para o resultado ja vir "expandido".
            ColumnAutoFit.packColumns(table);
        } else {
            for (int v = 0; v < table.getColumnCount(); v++) {
                Integer saved = snapshot.widths().get(table.getColumnName(v));
                if (saved != null) {
                    // Respeita a largura salva EXATAMENTE como o usuario
                    // deixou — inclusive menor que o padrao: o usuario tem
                    // controle total para diminuir uma coluna, e isso nao
                    // pode ser desfeito sozinho na proxima vez que a mesma
                    // consulta rodar. O unico limite e o minimo DURO,
                    // minusculo, do proprio Swing (ver ResultGrid#styleTable),
                    // que TableColumn.setWidth/setPreferredWidth ja aplicam
                    // sozinhos se o valor salvo for absurdo.
                    ColumnAutoFit.applyWidth(table, v, saved);
                } else {
                    // Coluna nova (nao existia quando o layout foi salvo):
                    // mesmo ajuste ao conteudo da primeira exibicao, por
                    // consistencia (nao a largura uniforme antiga).
                    ColumnAutoFit.packColumn(table, v);
                }
            }
        }

        for (String hiddenName : snapshot.hidden()) {
            hideByName(hiddenName);
        }

        if (!snapshot.sortSpec().isEmpty()) {
            List<RowSorter.SortKey> keys = new ArrayList<>();
            for (String token : snapshot.sortSpec()) {
                int colon = token.indexOf(':');
                if (colon < 0) {
                    continue;
                }
                int modelIndex = model.findColumn(token.substring(0, colon));
                if (modelIndex < 0) {
                    continue;
                }
                SortOrder order = "DESC".equalsIgnoreCase(token.substring(colon + 1))
                        ? SortOrder.DESCENDING : SortOrder.ASCENDING;
                keys.add(new RowSorter.SortKey(modelIndex, order));
            }
            if (!keys.isEmpty()) {
                sorter.rowSorter().setSortKeys(keys);
            }
        }
    }

    private void hideByName(String columnName) {
        for (int v = 0; v < table.getColumnCount(); v++) {
            if (table.getColumnName(v).equals(columnName)) {
                ColumnVisibility.hide(table, v);
                return;
            }
        }
    }

    private void persistLayout() {
        Map<String, Integer> widths = new LinkedHashMap<>();
        for (int v = 0; v < table.getColumnCount(); v++) {
            widths.put(table.getColumnName(v), table.getColumnModel().getColumn(v).getWidth());
        }
        Set<String> hidden = new LinkedHashSet<>(ColumnVisibility.hiddenNames(table));
        List<String> sortSpec = new ArrayList<>();
        for (RowSorter.SortKey key : sorter.rowSorter().getSortKeys()) {
            if (key.getSortOrder() == SortOrder.UNSORTED) {
                continue;
            }
            String name = table.getModel().getColumnName(key.getColumn());
            sortSpec.add(name + ":" + (key.getSortOrder() == SortOrder.DESCENDING ? "DESC" : "ASC"));
        }
        GridPreferences.save(fingerprint, new GridPreferences.Snapshot(widths, hidden, sortSpec));
    }
}
