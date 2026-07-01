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
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
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
        RendererFactory.installOn(table, model);

        this.sorter = new ColumnSorter(table);

        ColumnMetadataResolver resolver = new ColumnMetadataResolver(metadataCache, connectionManager, schema);
        ColumnHeaderRenderer.MetadataSource metadataSource =
                col -> resolver.resolve(model, col, () -> table.getTableHeader().repaint());

        SelectionManager selection = SelectionManager.install(table);

        JComponent corner = RowNumberGutter.corner();
        JTableHeader header = ResultTableHeader.install(table, sorter, selection, metadataSource);
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
        ResultContextMenu.install(table, sorter, metadataSource, filterController, exportExcel);
        ResultHeaderContextMenu.install(table, header, sorter, metadataSource, filterController, this::persistLayout);

        header.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseReleased(MouseEvent e) {
                persistLayout();
            }
        });
        sorter.rowSorter().addRowSorterListener(_ -> persistLayout());

        JScrollPane scroll = new JScrollPane(table);
        scroll.setRowHeaderView(RowNumberGutter.build(table, model, selection));
        scroll.setCorner(JScrollPane.UPPER_LEFT_CORNER, corner);

        add(buildFilterBar(model), BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
    }

    JTable table() {
        return table;
    }

    JComponent asComponent() {
        return this;
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
        filterColumnBox.addActionListener(_ -> apply.run());

        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 3));
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
            // Primeira vez que este "formato" de resultado (mesmas colunas)
            // e exibido: largura PADRAO uniforme, nao ajuste por conteudo —
            // ver a nota de classe em ColumnAutoFit. O ajuste por conteudo
            // continua a um duplo-clique/"AutoFit" de distancia.
            ColumnAutoFit.applyDefaultWidths(table);
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
                    // Coluna nova (nao existia quando o layout foi salvo): mesma largura
                    // padrao usada na primeira exibicao, por consistencia.
                    ColumnAutoFit.applyDefaultWidth(table, v);
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
