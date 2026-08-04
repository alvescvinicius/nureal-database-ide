package com.nureal.ide.ui;
import com.nureal.ide.compartilhado.designsystem.Typography;
import com.nureal.ide.compartilhado.designsystem.GridTheme;

import com.nureal.ide.modulos.conexoes.dominio.contratos.ConexaoAtivaPort;
import com.nureal.ide.compartilhado.designsystem.NSearchField;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.RowFilter;
import javax.swing.RowSorter;
import javax.swing.SortOrder;
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

    /** Altura de linha padrao (px, antes de {@code scale}) quando o chamador nao pede uma explicita — ver o construtor de 6 args. */
    private static final int DEFAULT_ROW_HEIGHT_BASE_PX = 22;

    private final JTable table;
    /** Modelo bruto (indices de LINHA/COLUNA de MODELO, nao de view) — usado por {@link #distinctValuesFor} para calcular a lista "em cascata" do autofiltro por coluna sem depender do filtro atual da view. */
    private final ResultTableModel model;
    /** Filtro "por valor" estilo Excel (autofiltro por coluna) — ver {@link ColumnFilterPopup}/{@link #onFilterIconClicked}. */
    private final ColumnValueFilter columnValueFilter = new ColumnValueFilter();
    /** Exposto via {@link #scrollPane()} pra quem precisar reagir a rolagem (ver {@code ResultsAreaController}, carregamento automatico de mais linhas). */
    private JScrollPane scrollPane;
    /** Alterna grade <-> {@link ResultRecordView} — ver {@link #toggleRecordView}. */
    private JPanel centerCards;
    private ResultRecordView recordView;
    private JCheckBox recordViewToggle;
    private boolean recordViewOn;
    private final ColumnSorter sorter;
    private final String fingerprint;
    private final JComboBox<String> filterColumnBox = new JComboBox<>();
    private final NSearchField filterField = new NSearchField("Filtrar...  (ex: >= 2026-06-01)");
    /** Campo curto e independente do filtro de valores: so navega ate a coluna, nunca restringe linhas (ver {@link #searchColumn}). */
    private final NSearchField columnSearchField = new NSearchField("Buscar coluna...");
    private final GridEditController editController;
    private final ColumnHeaderRenderer headerRenderer;
    private final SelectionManager selection;
    /**
     * Notificado sempre que a selecao de celulas muda, com a contagem de
     * celulas selecionadas e as funcoes agregadas (soma, media, minimo,
     * maximo) entre os valores numericos dela — ver {@link SelectionStats} e
     * {@link #updateSelectionSummary()}. {@code MainWindow} liga isto a
     * {@link ResultStatusBar#updateSelectionSummary}. Default no-op: a grade
     * funciona sozinha (testes, uso fora do MainWindow) mesmo sem ninguem
     * ouvindo.
     */
    private java.util.function.Consumer<SelectionStats> onSelectionSummary = stats -> { };

    /**
     * Reaplica o fundo/borda/cor de texto do {@link #buildFilterBar} — sao
     * setados explicitamente (nao ficam UIResource), entao NAO acompanham
     * sozinhos uma troca de tema com a grade ja aberta numa janela (mesma
     * causa do {@code updateUI()} de {@code table} logo abaixo). Comeca como
     * no-op: so vira o Runnable de verdade dentro de {@link #buildFilterBar},
     * chamado a partir do {@link #updateUI()} desta classe. O guard contra
     * {@code null} nao e so defensivo — o PRIMEIRO {@code updateUI()} desta
     * classe e disparado pelo proprio {@code super(new BorderLayout())} do
     * construtor, ANTES deste campo ser de fato inicializado.
     */
    private Runnable filterBarChromeRefresh;

    /**
     * @param model            dados + metadados da consulta (ver {@link ResultTableModel})
     * @param connectionManager conexao usada para carregar PK/FK/indices sob demanda
     * @param schema           schema atual (pode ser {@code null})
     * @param metadataCache    cache de metadados de tabela, COMPARTILHADO entre todas as grades da sessao
     * @param exportExcel      acao "Exportar Excel" (delegada a MainWindow, que ja sabe exportar varias abas)
     * @param scale            funcao de escala de UI (zoom + modo compacto) — mesma usada pelo resto da janela
     */
    ResultGrid(ResultTableModel model, ConexaoAtivaPort connectionManager, String schema,
            TableMetadataCache metadataCache, Runnable exportExcel, IntUnaryOperator scale) {
        this(model, connectionManager, schema, metadataCache, exportExcel, scale, DEFAULT_ROW_HEIGHT_BASE_PX);
    }

    /**
     * @param rowHeightBasePx altura de linha em px, ANTES de {@code scale} —
     *                        pedido explicito do usuario: um controle SEPARADO
     *                        do zoom da interface, so para o espacamento das
     *                        linhas da grade (ver {@code MainWindow#resultRowHeightBasePx}/
     *                        {@code ROW_SPACING_LEVELS}). {@code scale} continua
     *                        aplicado por cima (zoom/modo compacto tambem
     *                        escalam a altura final), os dois se combinam em
     *                        vez de um substituir o outro.
     */
    ResultGrid(ResultTableModel model, ConexaoAtivaPort connectionManager, String schema,
            TableMetadataCache metadataCache, Runnable exportExcel, IntUnaryOperator scale, int rowHeightBasePx) {
        super(new BorderLayout());

        // Sempre criado (nunca null) mas so vira editavel de fato quando
        // MainWindow chama editController().enable(target) apos resolver que
        // este resultado e um SELECT simples de uma tabela com PK conhecida
        // — ver MainWindow#tryEnableEditing. Enquanto isso, isEditable()
        // volta false e a grade permanece somente-leitura, como sempre foi.
        this.editController = new GridEditController(model);
        model.setEditController(editController);
        this.model = model;

        this.table = buildTable(model, scale, rowHeightBasePx);
        ColumnMetadataResolver resolver = installRenderers(model, connectionManager, schema, metadataCache);
        this.sorter = new ColumnSorter(table);

        ColumnHeaderRenderer.MetadataSource metadataSource =
                col -> resolver.resolve(model, col, () -> table.getTableHeader().repaint());

        this.selection = SelectionManager.install(table);
        installSelectionSummaryListeners();
        installColumnHighlightClearOnClick();

        JComponent corner = RowNumberGutter.corner();
        this.headerRenderer = new ColumnHeaderRenderer(sorter);
        headerRenderer.setFilterActiveSource(columnValueFilter::isActive);
        JTableHeader header = ResultTableHeader.install(table, sorter, selection, metadataSource, headerRenderer,
                this::onFilterIconClicked);
        applyHeaderHeight(header, scale);
        selection.installCorner(corner, this::persistLayout);

        this.fingerprint = GridPreferences.fingerprint(columnNames(model));
        applyPersistedLayoutOrAutoFit(model);

        wireContextMenusAndScroll(model, connectionManager, schema, metadataCache, exportExcel, scale,
                metadataSource, header, corner);
    }

    private JTable buildTable(ResultTableModel model, IntUnaryOperator scale, int rowHeightBasePx) {
        JTable newTable = new JTable(model) {
            private static final long serialVersionUID = 1L;

            @Override
            public String getToolTipText(MouseEvent e) {
                return cellTooltip(this, e);
            }

            /**
             * Sem isto, uma grade deste tipo aberta dentro de uma JANELA
             * PROPRIA (o Inspetor de FK, {@code FkInspectorWindow}, e o unico
             * caso hoje — nao acompanha o ciclo de vida da janela principal)
             * ficava "presa" nas cores de fundo/selecao/grade do tema de
             * quando foi ABERTA: alternar claro/escuro na janela principal
             * chama {@code FlatLaf.updateUI()}, que atualiza TODAS as janelas
             * abertas (inclusive esta) — mas so reinstala o Look and Feel
             * PADRAO do JTable, nunca reaplica sozinho as cores explicitas
             * que {@link #styleTable} ja tinha definido na mao (setBackground/
             * setSelectionBackground/etc nao viram UIResource so por serem
             * setados direto — o LookAndFeel nao mexe neles de novo).
             * Resultado relatado pelo usuario: reabrir/olhar um Inspetor
             * aberto antes da troca de tema mostrava a grade "bugada", com o
             * fundo/selecao do tema ANTIGO por baixo do cabecalho/filtro
             * (esses sim ja no tema novo, por usarem so o L&F padrao).
             * Reaplicar {@link #styleTable} aqui, toda vez que o L&F for
             * atualizado, resolve isso — funciona pra QUALQUER janela futura
             * que venha a usar {@link ResultGrid}, nao so o Inspetor de FK.
             */
            @Override
            public void updateUI() {
                super.updateUI();
                if (ResultGrid.this.table != null) {
                    styleTable(this, scale, rowHeightBasePx);
                }
            }
        };
        // getToolTipText(MouseEvent) sozinho nao basta: o ToolTipManager so
        // consulta um componente depois que ele foi registrado nele (o que
        // setToolTipText(...) faz implicitamente) — sem isto, a celula nunca
        // mostraria tooltip mesmo com o metodo sobrescrito corretamente.
        javax.swing.ToolTipManager.sharedInstance().registerComponent(newTable);
        styleTable(newTable, scale, rowHeightBasePx);
        return newTable;
    }

    /**
     * Resolver de metadados (PK/FK/indices/comentario) desta grade
     * especifica — guardado como client property ANTES de instalar os
     * renderers para que AbstractTypedCellRenderer consiga destacar colunas
     * que sao chave primaria/estrangeira de verdade (ver
     * RendererFactory#KEY_METADATA_RESOLVER), o mesmo resolver usado pelo
     * indicador de FK do cabecalho e pelo popup de metadados.
     */
    private ColumnMetadataResolver installRenderers(ResultTableModel model, ConexaoAtivaPort connectionManager,
            String schema, TableMetadataCache metadataCache) {
        ColumnMetadataResolver resolver = new ColumnMetadataResolver(metadataCache, connectionManager, schema);
        table.putClientProperty(RendererFactory.KEY_METADATA_RESOLVER, resolver);
        RendererFactory.installOn(table, model);
        return resolver;
    }

    /**
     * Resumo de selecao (contagem + soma): ouve os DOIS modelos de selecao
     * (linha E coluna) separadamente — um arrasto horizontal dentro de uma
     * unica linha, por exemplo, muda so o modelo de coluna, nunca o de linha
     * (ver SelectionManager#installBodyMouseHandling), entao ouvir so um dos
     * dois deixaria casos assim sem atualizar.
     */
    private void installSelectionSummaryListeners() {
        javax.swing.event.ListSelectionListener selectionSummaryListener = e -> {
            if (!e.getValueIsAdjusting()) {
                updateSelectionSummary();
            }
        };
        table.getSelectionModel().addListSelectionListener(selectionSummaryListener);
        table.getColumnModel().getSelectionModel().addListSelectionListener(selectionSummaryListener);
    }

    private void wireContextMenusAndScroll(ResultTableModel model, ConexaoAtivaPort connectionManager,
            String schema, TableMetadataCache metadataCache, Runnable exportExcel, IntUnaryOperator scale,
            ColumnHeaderRenderer.MetadataSource metadataSource, JTableHeader header, JComponent corner) {
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
        // Mesmo fkOrigin do menu de contexto, so que disparado pelo icone de
        // FK pintado na propria celula (ver AbstractTypedCellRenderer/
        // SelectionManager#installFkOriginHandler) — pedido explicito do
        // usuario para nao precisar do menu de contexto so pra isso.
        selection.installFkOriginHandler(metadataSource, (viewRow, viewCol) -> {
            int modelColumn = table.convertColumnIndexToModel(viewCol);
            ColumnMetadata meta = metadataSource.metadataFor(modelColumn);
            if (meta != null && meta.hasForeignKey()) {
                fkOrigin.openOrigin(meta, viewRow);
            }
        });
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
        this.scrollPane = scroll;

        // "Modo registro" (ver ResultRecordView) — pedido explicito do
        // usuario: "mostrar a grade na vertical tambem", uma linha por vez
        // com cada coluna empilhada verticalmente, pra ver todos os campos
        // de UM registro sem rolar horizontalmente. CardLayout alterna entre
        // a grade normal e o inspetor, sem afetar nenhuma das duas quando
        // nao esta visivel. Recebe os MESMOS metadataSource/fkOrigin ja
        // construidos acima pro menu de contexto/icone de FK da grade —
        // "Visualizar Origem" funciona igual nas duas visoes, sem duplicar a
        // logica de abrir o FkInspectorWindow.
        this.recordView = new ResultRecordView(model, table, this::syncTableSelectionToModelRow,
                metadataSource, fkOrigin);
        this.centerCards = new JPanel(new java.awt.CardLayout());
        centerCards.add(scroll, CARD_GRID);
        centerCards.add(recordView, CARD_RECORD);

        add(buildFilterBar(model), BorderLayout.NORTH);
        add(centerCards, BorderLayout.CENTER);
    }

    private static final String CARD_GRID = "grid";
    private static final String CARD_RECORD = "record";

    /**
     * Alterna entre a grade e o {@link #recordView} (botao "Ver como
     * registro" da barra de filtro, ver {@link #buildFilterBar}). Ao entrar
     * no modo registro, mostra a linha ATUALMENTE selecionada na grade (ou a
     * primeira linha visivel, se nenhuma estiver selecionada) — as duas
     * visoes ficam sincronizadas pela SELECAO da tabela, nunca por um
     * indice proprio duplicado.
     */
    private void toggleRecordView() {
        recordViewOn = !recordViewOn;
        java.awt.CardLayout layout = (java.awt.CardLayout) centerCards.getLayout();
        if (recordViewOn) {
            int viewRow = table.getSelectedRow();
            int modelRow;
            if (viewRow >= 0) {
                modelRow = table.convertRowIndexToModel(viewRow);
            } else if (table.getRowCount() > 0) {
                modelRow = table.convertRowIndexToModel(0);
            } else {
                modelRow = -1;
            }
            recordView.showRow(modelRow);
            layout.show(centerCards, CARD_RECORD);
        } else {
            layout.show(centerCards, CARD_GRID);
        }
        if (recordViewToggle != null) {
            recordViewToggle.setSelected(recordViewOn);
        }
    }

    /** Callback de navegacao (Anterior/Proximo) do {@link #recordView} — mantem a selecao da grade em dia, mesmo invisivel. */
    private void syncTableSelectionToModelRow(int modelRow) {
        int viewRow = table.convertRowIndexToView(modelRow);
        if (viewRow < 0) {
            return; // linha filtrada/fora da view — recordView continua mostrando pelo indice de modelo mesmo assim
        }
        table.setRowSelectionInterval(viewRow, viewRow);
        table.scrollRectToVisible(table.getCellRect(viewRow, 0, true));
    }

    /**
     * Reaplica {@link #filterBarChromeRefresh} sempre que o L&F desta grade
     * for atualizado — inclusive quando esta grade vive dentro de uma janela
     * QUE NAO E a MainWindow (Inspetor de FK, dialogo de propriedades de
     * objeto etc.): {@code FlatLaf.updateUI()} (chamado em
     * {@code MainWindow#toggleTheme}) percorre TODAS as janelas abertas, e
     * este {@code updateUI()} e quem recebe esse aviso aqui, sem precisar
     * fechar/reabrir a janela.
     */
    @Override
    public void updateUI() {
        super.updateUI();
        if (filterBarChromeRefresh != null) {
            filterBarChromeRefresh.run();
        }
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

    /** {@link JScrollPane} que envolve a tabela — usado por {@code ResultsAreaController} pra detectar rolagem perto do fim (carregamento automatico de mais linhas). */
    JScrollPane scrollPane() {
        return scrollPane;
    }

    /** Liga o callback de resumo de selecao (ver campo {@link #onSelectionSummary}). */
    void onSelectionSummary(java.util.function.Consumer<SelectionStats> listener) {
        this.onSelectionSummary = listener;
    }

    JComponent asComponent() {
        return this;
    }

    GridEditController editController() {
        return editController;
    }

    /**
     * Reconstroi a linha atualmente exibida no {@link #recordView} (ver
     * {@link ResultRecordView#refresh}) — chamado de fora (ver
     * {@code ResultsAreaController#wireGridEditing}) sempre que o modo de
     * edicao liga/desliga ou o estado de pendencias muda, pra alternar entre
     * campo so-leitura e campo editavel sem depender de {@link #toggleRecordView}
     * (que so roda quando o USUARIO troca de visao, nao quando o modo de
     * edicao muda com "Ver como registro" ja aberto).
     */
    void refreshRecordView() {
        if (recordView != null) {
            recordView.refresh();
        }
    }

    /**
     * Evita que clicar em {@code other} (tipicamente a barra de acoes deste
     * MESMO resultado — ver {@link ResultStatusBar#asComponent()}) limpe a
     * selecao da grade — ver {@link SelectionManager#keepSelectionOnFocusTo}
     * para o bug que isto corrige ("Excluir linha(s)" nao fazia nada porque o
     * proprio clique no botao ja limpava a selecao antes do botao le-la).
     */
    void keepSelectionOnFocusTo(JComponent other) {
        selection.keepSelectionOnFocusTo(other);
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

    private static void styleTable(JTable table, IntUnaryOperator scale, int rowHeightBasePx) {
        // rowHeightBasePx vem do controle de "Espacamento de linhas" do menu
        // Layout (ver MainWindow#ROW_SPACING_LEVELS) — scale (zoom/modo
        // compacto) continua se aplicando por cima do valor escolhido, os
        // dois se combinam em vez de um substituir o outro.
        table.setRowHeight(scale.applyAsInt(rowHeightBasePx));
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
        // O fundo/texto "puros" da JTable (nao usados na pintura normal —
        // AbstractTypedCellRenderer que manda no fundo de cada celula, ver
        // aplyRowBackground) SO importam pro editor de celula PADRAO do
        // Swing: DefaultCellEditor pinta o JTextField de edicao com
        // table.getBackground()/getForeground() quando a celula NAO esta
        // selecionada (e com as cores de selecao, ja tratadas acima, quando
        // esta). Sem isto, table.getBackground() ficava sem valor explicito
        // e o campo de edicao aparecia com o branco padrao do Swing por cima
        // de uma grade inteira no tema escuro — bug relatado pelo usuario
        // ("caixa branca" ao editar uma celula no tema escuro).
        table.setBackground(GridTheme.ZEBRA_EVEN);
        table.setForeground(GridTheme.COLOR_DEFAULT_TEXT);
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
     * Recalcula quantas celulas estao selecionadas e as funcoes agregadas
     * (soma, media, minimo, maximo) dos valores numericos entre elas, e
     * repassa para quem estiver ouvindo (ver {@link #onSelectionSummary}).
     * Iterar {@code isCellSelected} sobre o produto linhas-selecionadas x
     * colunas-selecionadas (em vez de assumir que toda combinacao esta
     * selecionada) e o jeito robusto de contar a selecao de fato — no Swing,
     * linhas e colunas selecionadas sao dois modelos independentes, e o
     * retangulo que os dois juntos "sugerem" nem sempre e o que esta
     * realmente marcado.
     */
    private void updateSelectionSummary() {
        int[] rows = table.getSelectedRows();
        int[] cols = table.getSelectedColumns();
        int count = 0;
        int numericCount = 0;
        java.math.BigDecimal sum = java.math.BigDecimal.ZERO;
        java.math.BigDecimal min = null;
        java.math.BigDecimal max = null;
        for (int row : rows) {
            for (int col : cols) {
                if (!table.isCellSelected(row, col)) {
                    continue;
                }
                count++;
                java.math.BigDecimal n = numericValue(table.getValueAt(row, col));
                if (n != null) {
                    sum = sum.add(n);
                    numericCount++;
                    min = (min == null || n.compareTo(min) < 0) ? n : min;
                    max = (max == null || n.compareTo(max) > 0) ? n : max;
                }
            }
        }
        java.math.BigDecimal average = numericCount > 0
                ? sum.divide(java.math.BigDecimal.valueOf(numericCount), java.math.MathContext.DECIMAL64)
                : null;
        onSelectionSummary.accept(new SelectionStats(count, numericCount,
                numericCount > 0 ? sum : null, average, min, max));
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
        filterField.setColumns(20);
        filterField.setToolTipText("<html>Filtro inteligente:<br>"
                + "&bull; texto: <b>contem</b> (ex: silva)<br>"
                + "&bull; operadores: <b>&gt;= &lt;= &gt; &lt; = &lt;&gt;</b> (ex: &gt;= 2026-06-01, &gt; 100)<br>"
                + "&bull; intervalo: <b>a..b</b> (ex: 2026-01-01..2026-06-30)<br>"
                + "&bull; prefixo/sufixo: <b>^abc</b> / <b>abc$</b><br>"
                + "&bull; <b>NULL</b> / <b>NOT NULL</b><br>"
                + "Entende data e numero mesmo em colunas de texto.</html>");

        Runnable apply = this::applyFilters;
        filterField.onTextChange(apply);
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
        columnSearchField.setColumns(10);
        columnSearchField.setToolTipText("Digite parte do nome da coluna: a grade rola ate ela e destaca o cabecalho.");
        columnSearchField.onTextChange(this::searchColumn);

        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 3));
        JLabel columnLabel = new JLabel("Coluna:");
        bar.add(columnLabel);
        bar.add(columnSearchField);
        JLabel label = new JLabel("Filtro:");
        bar.add(label);
        bar.add(filterColumnBox);
        bar.add(filterField);

        // "Ver como registro" (ResultRecordView) — pedido explicito do
        // usuario: mostrar a grade "na vertical tambem", uma linha por vez.
        // JCheckBox (nao mais JButton de texto que trocava "Ver como
        // grade"/"Ver como registro") — mesma linguagem visual do protótipo
        // (checkbox numa unica linha de barra de resultados), e o estado
        // ligado/desligado fica mais claro que um botao que muda de rotulo.
        recordViewToggle = new JCheckBox("Ver como registro");
        recordViewToggle.setOpaque(false);
        recordViewToggle.addActionListener(e -> toggleRecordView());
        bar.add(recordViewToggle);

        // ANTES esta barra nunca recebia fundo/borda proprios — so herdava o
        // cinza generico de "Panel.background" do L&F, um tom DIFERENTE (mais
        // claro no escuro) do resto do chrome desta grade (cabecalho, barra
        // de filtro do Inspetor de FK etc.), destacando-se como um "remendo"
        // fora do tema (queixa do usuario, com captura de tela: "continua com
        // pequenas partes" fora do padrao visual). Usa o MESMO tom de
        // cabecalho do resto da grade, com separador embaixo — e precisa ser
        // REAPLICADO (nao so setado uma vez) sempre que o tema mudar com a
        // grade ja aberta, ver #updateUI().
        filterBarChromeRefresh = () -> {
            bar.setOpaque(true);
            bar.setBackground(GridTheme.HEADER_BACKGROUND);
            bar.setBorder(javax.swing.BorderFactory.createMatteBorder(0, 0, 1, 0, GridTheme.HEADER_BORDER));
            Typography.tertiary(columnLabel);
            Typography.tertiary(label);
        };
        filterBarChromeRefresh.run();
        return bar;
    }

    private void setFilter(int modelColumn, String value) {
        filterColumnBox.setSelectedIndex(modelColumn + 1);
        filterField.setText(value);
    }

    private void clearFilterUi() {
        columnValueFilter.clearAll();
        filterField.setText("");
        filterColumnBox.setSelectedIndex(0);
        columnSearchField.setText("");
        applyFilters();
        table.getTableHeader().repaint();
    }

    /**
     * Recombina o filtro de texto "inteligente" (barra de filtro) com o
     * autofiltro por valores de cada coluna (ver {@link ColumnValueFilter}) —
     * os dois se somam (E logico), nunca um substitui o outro. Chamado sempre
     * que qualquer um dos dois muda.
     */
    private void applyFilters() {
        int modelColumn = filterColumnBox.getSelectedIndex() - 1;
        RowFilter<Object, Object> smart = SmartCellFilter.build(filterField.getText(), modelColumn);
        RowFilter<Object, Object> columns = columnValueFilter.buildRowFilter();
        List<RowFilter<Object, Object>> combined = new ArrayList<>();
        if (smart != null) {
            combined.add(smart);
        }
        if (columns != null) {
            combined.add(columns);
        }
        sorter.rowSorter().setRowFilter(combined.isEmpty() ? null
                : (combined.size() == 1 ? combined.get(0) : RowFilter.andFilter(combined)));
    }

    /**
     * Icone de funil clicado no cabecalho (ver {@link ResultTableHeader.FilterIconHandler}):
     * monta a lista "em cascata" de valores distintos desta coluna (ver
     * {@link #distinctValuesFor}) e abre o {@link ColumnFilterPopup} ancorado
     * logo abaixo do cabecalho.
     */
    private void onFilterIconClicked(int viewColumn, java.awt.Point anchor) {
        int modelColumn = table.convertColumnIndexToModel(viewColumn);
        Map<String, String> valueLabels = distinctValuesFor(modelColumn);
        Set<String> allPossible = valueLabels.keySet();
        Set<String> checked = columnValueFilter.isActive(modelColumn)
                ? columnValueFilter.allowedValues(modelColumn)
                : allPossible;
        ColumnFilterPopup.show(table.getTableHeader(), anchor, valueLabels, checked,
                selected -> {
                    columnValueFilter.setAllowedValues(modelColumn, selected, allPossible);
                    applyFilters();
                    table.getTableHeader().repaint();
                },
                () -> {
                    columnValueFilter.clear(modelColumn);
                    applyFilters();
                    table.getTableHeader().repaint();
                });
    }

    /**
     * Valores distintos de {@code targetModelColumn} (chave = valor bruto,
     * ver {@link ColumnValueFilter#stringValue}; texto exibido igual, exceto
     * "" -&gt; "(vazios)"), EM CASCATA: so conta linha que bate com os
     * filtros JA ATIVOS nas OUTRAS colunas (ver
     * {@link ColumnValueFilter#rowMatchesExcluding}) e com o filtro de texto
     * "inteligente" da barra de filtro — mesma logica de
     * {@link SmartCellFilter}, aplicada direto no MODELO (nao na view), pra
     * nao depender do RowFilter combinado ja estar montado.
     */
    private Map<String, String> distinctValuesFor(int targetModelColumn) {
        String filterText = filterField.getText();
        int filterColumn = filterColumnBox.getSelectedIndex() - 1;
        java.util.function.Predicate<String> smartPredicate =
                (filterText == null || filterText.isBlank()) ? null : SmartCellFilter.buildPredicate(filterText);

        Set<String> sortedKeys = ColumnValueFilter.newSortedSet();
        int rowCount = model.getRowCount();
        for (int row = 0; row < rowCount; row++) {
            if (!columnValueFilter.rowMatchesExcluding(model, row, targetModelColumn)) {
                continue;
            }
            if (smartPredicate != null && !rowMatchesSmartFilter(smartPredicate, filterColumn, row)) {
                continue;
            }
            sortedKeys.add(ColumnValueFilter.stringValue(model.getValueAt(row, targetModelColumn)));
        }

        Map<String, String> labels = new LinkedHashMap<>();
        for (String key : sortedKeys) {
            labels.put(key, key.isEmpty() ? "(vazios)" : key);
        }
        return labels;
    }

    private boolean rowMatchesSmartFilter(java.util.function.Predicate<String> predicate, int filterColumn, int row) {
        if (filterColumn < 0) {
            for (int c = 0; c < model.getColumnCount(); c++) {
                if (predicate.test(ColumnValueFilter.stringValue(model.getValueAt(row, c)))) {
                    return true;
                }
            }
            return false;
        }
        return predicate.test(ColumnValueFilter.stringValue(model.getValueAt(row, filterColumn)));
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

    /**
     * Clicar em qualquer celula do CORPO da grade limpa o realce de coluna
     * (ver {@link #highlightSelectedColumn}/{@link #searchColumn}) — antes
     * so sumia trocando o combo "Filtro" pra "Todas as colunas" ou apagando
     * o campo "Buscar coluna" a mao; clicar numa area neutra da grade nao
     * tirava o destaque, ficando "preso" ali (relatado pelo usuario, com
     * captura de tela). So limpa a marcacao VISUAL do cabecalho e o campo de
     * busca de coluna — nao mexe no combo "Filtro" nem no filtro de linhas
     * em vigor (SAO independentes: o realce e so um "olha aqui", nao reflete
     * o filtro ativo).
     */
    private void installColumnHighlightClearOnClick() {
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (headerRenderer.highlightedColumn() < 0) {
                    return;
                }
                clearColumnHighlight();
                columnSearchField.setText("");
            }
        });
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
