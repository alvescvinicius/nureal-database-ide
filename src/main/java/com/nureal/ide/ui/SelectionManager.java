package com.nureal.ide.ui;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JTable;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;

/**
 * Toda a logica de selecao e hover da grade de resultados, estilo Excel:
 *
 * <ul>
 *   <li>Clique simples numa celula -&gt; seleciona a LINHA inteira (todas as
 *       colunas), mantendo a celula clicada como "ativa" (lead da selecao de
 *       coluna, usado por {@link AbstractTypedCellRenderer} para o destaque
 *       extra de celula ativa).</li>
 *   <li>Duplo clique -&gt; seleciona SOMENTE aquela celula; se ela NAO for editavel
*       (ver {@link GridEditController}), abre {@link CellContentViewer} para
*       selecionar/copiar um trecho do texto. Se FOR editavel, deixa o duplo
*       clique iniciar a edicao da propria celula normalmente (nao compete com
*       o visualizador).</li>
 *   <li>Ctrl+clique -&gt; adiciona a linha a selecao existente.</li>
 *   <li>Shift+clique -&gt; seleciona o intervalo de linhas a partir da ancora.</li>
 *   <li>Clique no cabecalho de uma coluna ({@link #selectColumn}, chamado por
 *       {@link ResultTableHeader}) -&gt; seleciona a coluna inteira (todas as
 *       linhas), com os mesmos modificadores Ctrl/Shift.</li>
 *   <li>Clique no canto -&gt; seleciona tudo; duplo-clique -&gt; cicla a largura
 *       de todas as colunas (ver {@link #cycleCornerWidth}).</li>
 *   <li>Esc limpa a selecao; Ctrl+A seleciona tudo; Ctrl+Home/Ctrl+End vao
 *       para a primeira/ultima CELULA da tabela inteira (nao so a linha).</li>
 *   <li>Perder o foco da tabela (clicar em qualquer outra area da aplicacao)
 *       limpa a selecao — nunca fica uma selecao "presa" numa grade que o
 *       usuario nem esta mais olhando.</li>
 *   <li>Ctrl+C / "Copiar" (menu de contexto) -&gt; copia so a celula ativa OU a
 *       selecao inteira, dependendo de COMO ela foi feita (ver
 *       {@link #SELECTION_SCOPE_PROPERTY} e {@link GridClipboard#copySelectionAuto}).</li>
 *   <li>Hover: destaque suave da linha sob o mouse, sem alterar a selecao
 *       (ver {@link #hoverRow}, consumido pelos renderers de celula).</li>
 * </ul>
 *
 * Tab/Shift+Tab/Enter/Shift+Enter/setas/PageUp/PageDown/Home/End ja
 * funcionam pelo comportamento PADRAO do {@link JTable} (com selecao de
 * celula habilitada) e nao sao reimplementados aqui — so os dois atalhos que
 * o JTable nao cobre da forma esperada (Ctrl+Home/Ctrl+End = celula, nao so
 * linha) sao sobrescritos.
 */
final class SelectionManager {

    private static final String HOVER_ROW_PROPERTY = "nureal.hoverRow";

    /**
     * Propriedade da JTable (mesmo padrao de {@link #HOVER_ROW_PROPERTY}) que
     * guarda a INTENCAO por tras da selecao atual — consultada por
     * {@link GridClipboard#copySelectionAuto} para decidir SE o Ctrl+C/"Copiar"
     * deve copiar so a celula ativa ou a selecao inteira:
     *
     * <ul>
     *   <li>{@link SelectionScope#CELL} — um clique simples no corpo, mesmo
     *       que visualmente destaque a linha toda (estilo Excel): a intencao
     *       do usuario e UMA celula, entao so ela e copiada.</li>
     *   <li>{@link SelectionScope#MULTI} — o usuario pediu explicitamente
     *       varias celulas: clicou no cabecalho de uma coluna (a coluna
     *       inteira), na numeracao de uma linha (a linha inteira), ou usou
     *       Shift/Ctrl no corpo para estender/somar linhas. Copia a selecao
     *       inteira (todas as linhas x colunas selecionadas), sem cabecalho.</li>
     * </ul>
     */
    private static final String SELECTION_SCOPE_PROPERTY = "nureal.selectionScope";

    enum SelectionScope { CELL, MULTI }

    private final JTable table;

    /**
     * Celula onde o clique simples (sem Shift/Ctrl, sem duplo-clique) COMECOU
     * — ancora para {@link #installBodyMouseHandling} decidir, durante o
     * arrasto, se a selecao continua sendo so a linha inteira (estilo Excel,
     * clique sem mover o mouse) ou vira um RETANGULO de celulas de verdade
     * (usuario arrastou para outra celula). So tem sentido enquanto
     * {@link #plainPress} for {@code true}; sem valor (-1) fora de um clique
     * simples em andamento.
     */
    private int pressRow = -1;
    private int pressCol = -1;
    /** {@code true} so durante um clique simples (sem Shift/Ctrl/duplo-clique) — ver {@link #pressRow}. */
    private boolean plainPress;

    private SelectionManager(JTable table) {
        this.table = table;
    }

    static SelectionManager install(JTable table) {
        SelectionManager manager = new SelectionManager(table);
        manager.installBodyMouseHandling();
        manager.installHoverTracking();
        manager.installKeyBindings();
        manager.installFocusClearing();
        return manager;
    }

    // ---------- Clique no corpo da tabela ----------

    private void installBodyMouseHandling() {
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                plainPress = false;
                int row = table.rowAtPoint(e.getPoint());
                int col = table.columnAtPoint(e.getPoint());
                if (row < 0 || col < 0) {
                    // Clique no espaco em branco do corpo da tabela (abaixo da
                    // ultima linha, ou a direita da ultima coluna quando ha
                    // scroll horizontal — table.setFillsViewportHeight(true)
                    // faz esse espaco fazer parte da propria JTable, entao o
                    // clique chega aqui, so sem linha/coluna valida). Antes,
                    // isto simplesmente nao fazia nada e a selecao anterior
                    // ficava "presa" — estilo Excel, clicar fora de qualquer
                    // celula limpa a selecao.
                    table.clearSelection();
                    return;
                }
                if (e.getClickCount() >= 2) {
                    selectSingleCell(row, col);
                    // Celula EDITAVEL (ver GridEditController/ResultTableModel):
                    // deixa o duplo-clique fazer o que ele sempre fez em
                    // qualquer grade estilo Excel — abrir o editor da propria
                    // celula. Abrir o visualizador por cima disso so atrapalha
                    // (foi exatamente o que o usuario reportou: o modal "Conteudo
                    // completo" competindo com a edicao da celula).
                    //
                    // Celula NAO editavel (grade ainda somente-leitura para
                    // este resultado — JOIN, sem PK, etc.): nao ha cursor de
                    // texto dentro da celula para selecionar um trecho e
                    // copiar, entao o duplo-clique continua abrindo o MESMO
                    // visualizador do menu de contexto "Ver conteudo completo"
                    // (JTextArea selecionavel + botao Copiar).
                    if (!table.isCellEditable(row, col)) {
                        CellContentViewer.show(table, table.getColumnName(col), table.getValueAt(row, col));
                    }
                } else if (e.isShiftDown()) {
                    extendRowRangeTo(row, col);
                } else if (e.isControlDown()) {
                    addRowToSelection(row, col);
                } else {
                    pressRow = row;
                    pressCol = col;
                    plainPress = true;
                    selectFullRow(row, col);
                }
            }
        });
        table.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                // So reage a arrasto que COMECOU com um clique simples (ver
                // pressRow/plainPress) — Shift/Ctrl/duplo-clique ja tem seu
                // proprio significado e nao devem virar um retangulo aqui.
                if (!plainPress) {
                    return;
                }
                int row = table.rowAtPoint(e.getPoint());
                int col = table.columnAtPoint(e.getPoint());
                if (row < 0 || col < 0 || (row == pressRow && col == pressCol)) {
                    return; // ainda na mesma celula do clique inicial: nao virou arrasto de verdade
                }
                // Arrasto de verdade: troca a selecao de "linha inteira" (o
                // clique simples estilo Excel) por um RETANGULO exato entre a
                // celula onde o clique comecou e a celula sob o mouse agora —
                // pedido explicito do usuario para poder marcar so algumas
                // celulas de UMA coluna, sem "vazar" pras colunas vizinhas.
                table.setRowSelectionInterval(pressRow, row);
                table.setColumnSelectionInterval(pressCol, col);
                // Mais de uma celula escolhida explicitamente: Ctrl+C deve
                // copiar o retangulo inteiro, nao so a celula ativa.
                setSelectionScope(SelectionScope.MULTI);
            }
        });
    }

    private void selectSingleCell(int row, int col) {
        table.setRowSelectionInterval(row, row);
        table.setColumnSelectionInterval(col, col);
        setSelectionScope(SelectionScope.CELL);
    }

    /** Seleciona a linha inteira, com a coluna clicada como celula "ativa" (lead). */
    private void selectFullRow(int row, int clickedCol) {
        table.setRowSelectionInterval(row, row);
        selectFullColumnRangeWithLead(clickedCol);
        // Clique simples, sem Shift/Ctrl: a intencao do usuario e UMA celula
        // (a linha toda so e destacada visualmente, estilo Excel) — ver
        // SELECTION_SCOPE_PROPERTY.
        setSelectionScope(SelectionScope.CELL);
    }

    private void addRowToSelection(int row, int clickedCol) {
        table.getSelectionModel().addSelectionInterval(row, row);
        selectFullColumnRangeWithLead(clickedCol);
        // Ctrl+clique soma outra linha a selecao — intencao explicita de
        // MAIS de uma celula.
        setSelectionScope(SelectionScope.MULTI);
    }

    /**
     * Shift+clique estende a selecao da ANCORA (celula do ultimo clique
     * simples) ate a celula clicada agora — um RETANGULO de verdade (linhas E
     * colunas), nao mais a linha inteira: pedido explicito do usuario para
     * conseguir marcar so um trecho de UMA coluna com Shift+clique, igual ao
     * arrasto (ver {@link #installBodyMouseHandling}). A ancora de COLUNA usa
     * o indice de LEAD (nao o de ancora) do modelo de selecao de colunas:
     * {@link #selectFullColumnRangeWithLead} (chamado pelo clique simples
     * anterior) sempre deixa esse indice apontando pra coluna que o usuario
     * de fato clicou, mesmo com TODAS as colunas visualmente selecionadas —
     * o indice de ancora, ali, aponta pra ultima coluna da grade, nao pra
     * coluna clicada.
     */
    private void extendRowRangeTo(int row, int clickedCol) {
        int rowAnchor = table.getSelectionModel().getAnchorSelectionIndex();
        int fromRow = (rowAnchor < 0) ? row : rowAnchor;
        table.setRowSelectionInterval(fromRow, row);

        int colAnchor = table.getColumnModel().getSelectionModel().getLeadSelectionIndex();
        int fromCol = (colAnchor < 0) ? clickedCol : colAnchor;
        table.setColumnSelectionInterval(fromCol, clickedCol);

        // Shift+clique estende a selecao — intencao explicita de MAIS de uma
        // celula.
        setSelectionScope(SelectionScope.MULTI);
    }

    /**
     * Seleciona TODAS as colunas mas preserva {@code leadCol} como lead da
     * selecao (para o destaque de "celula ativa"). {@code addSelectionInterval}
     * e uma UNIAO (nunca remove indices ja selecionados) — por isso duas
     * chamadas em sequencia selecionam o intervalo completo sem nunca perder
     * nenhuma coluna no meio do caminho, terminando com o lead exatamente em
     * {@code leadCol}.
     *
     * A ULTIMA linha ({@code setAnchorSelectionIndex}) e o que faz a
     * DIFERENCA: sem ela, a 2a chamada de {@code addSelectionInterval} deixa
     * a ANCORA presa em {@code lastCol} (o parametro ANTERIOR passado a ela),
     * nao em {@code leadCol} — MESMO com a selecao visual correta (linha
     * inteira destacada). Essa ancora "errada" so importa quando algo
     * ESTENDE a selecao a partir dela depois: Shift+seta (Cima/Baixo), que o
     * proprio JTable trata nativamente (fora do nosso controle — ver
     * {@code installBodyMouseHandling} para o equivalente tratado por NOS,
     * no mouse), usa exatamente esse indice de ancora — sem este fix, dava
     * pra reproduzir o bug so com teclado: clicar numa celula e depois segurar
     * Shift+Baixo estendia a selecao inteira "vazando" pras colunas vizinhas
     * em vez de ficar restrita a coluna clicada.
     */
    private void selectFullColumnRangeWithLead(int leadCol) {
        int lastCol = table.getColumnCount() - 1;
        if (lastCol < 0) {
            return;
        }
        ListSelectionModel csm = table.getColumnModel().getSelectionModel();
        csm.clearSelection();
        csm.addSelectionInterval(0, lastCol);
        csm.addSelectionInterval(lastCol, leadCol);
        csm.setAnchorSelectionIndex(leadCol);
    }

    // ---------- Selecao de linha (chamado pelo clique na coluna de numeracao) ----------

    /** Seleciona a linha {@code row} inteira (todas as colunas). Chamado pelo gutter de numeracao. */
    void selectRow(int row, boolean additive, boolean range) {
        ListSelectionModel rowModel = table.getSelectionModel();
        if (range) {
            int anchor = rowModel.getAnchorSelectionIndex();
            int from = (anchor < 0) ? row : anchor;
            rowModel.setSelectionInterval(from, row);
        } else if (additive) {
            rowModel.addSelectionInterval(row, row);
        } else {
            rowModel.setSelectionInterval(row, row);
        }
        int lastCol = table.getColumnCount() - 1;
        if (lastCol < 0) {
            return;
        }
        int prevLead = table.getColumnModel().getSelectionModel().getLeadSelectionIndex();
        int leadCol = (prevLead >= 0 && prevLead <= lastCol) ? prevLead : 0;
        selectFullColumnRangeWithLead(leadCol);
        // Clicar na numeracao e uma acao explicita sobre a LINHA inteira.
        setSelectionScope(SelectionScope.MULTI);
    }

    // ---------- Selecao de coluna (chamado pelo clique no cabecalho) ----------

    /** Seleciona a coluna {@code viewColumn} inteira (todas as linhas). Chamado por {@link ResultTableHeader}. */
    void selectColumn(int viewColumn, boolean additive, boolean range) {
        ListSelectionModel columnModel = table.getColumnModel().getSelectionModel();
        if (range) {
            int anchor = columnModel.getAnchorSelectionIndex();
            int from = (anchor < 0) ? viewColumn : anchor;
            columnModel.setSelectionInterval(from, viewColumn);
        } else if (additive) {
            columnModel.addSelectionInterval(viewColumn, viewColumn);
        } else {
            columnModel.setSelectionInterval(viewColumn, viewColumn);
        }
        selectAllRowsPreservingLead();
        // Clicar no cabecalho e uma acao explicita sobre a COLUNA inteira.
        setSelectionScope(SelectionScope.MULTI);
    }

    /** Mesmo fix de ancora de {@link #selectFullColumnRangeWithLead}, agora para linhas (coluna inteira pelo cabecalho). */
    private void selectAllRowsPreservingLead() {
        int lastRow = table.getRowCount() - 1;
        if (lastRow < 0) {
            return;
        }
        int prevLead = table.getSelectionModel().getLeadSelectionIndex();
        int leadRow = (prevLead >= 0 && prevLead <= lastRow) ? prevLead : 0;
        ListSelectionModel rsm = table.getSelectionModel();
        rsm.clearSelection();
        rsm.addSelectionInterval(0, lastRow);
        rsm.addSelectionInterval(lastRow, leadRow);
        rsm.setAnchorSelectionIndex(leadRow);
    }

    // ---------- Canto superior-esquerdo ----------

    /** Passo atual do ciclo de duplo-clique no canto — {@code null} = ainda nao ciclou nesta grade. */
    private CornerWidthMode cornerWidthMode;

    private enum CornerWidthMode { EXPANDED, MINIMIZED, DEFAULT }

    void installCorner(JComponent corner, Runnable onAutoFitAll) {
        if (corner == null) {
            return;
        }
        corner.setToolTipText(
                "Clique: selecionar tudo  ·  Duplo-clique: alternar largura de todas as colunas (ajustar ao conteudo -> minimo -> padrao)");
        corner.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() >= 2) {
                    cycleCornerWidth();
                    if (onAutoFitAll != null) {
                        onAutoFitAll.run();
                    }
                } else {
                    table.selectAll();
                    setSelectionScope(SelectionScope.MULTI);
                }
            }
        });
    }

    /**
     * Cada duplo-clique no canto avanca um passo, sempre no mesmo ciclo de 3
     * estados, voltando ao inicio depois do 3o: (1) {@code EXPANDED} — ajusta
     * todas as colunas ao conteudo, igual ao "AutoFit Todas" do menu de
     * contexto; (2) {@code MINIMIZED} — reduz todas a um tamanho compacto, mas ainda legivel (ver
     * {@link ColumnAutoFit#shrinkToMinimum}); (3) {@code DEFAULT} — volta a
     * largura padrao uniforme da primeira exibicao do resultado (ver
     * {@link ColumnAutoFit#applyDefaultWidths}). O estado vive so nesta
     * instancia de {@link SelectionManager} (uma grade nova, de uma consulta
     * nova, sempre comeca do zero, no passo 1).
     */
    private void cycleCornerWidth() {
        CornerWidthMode next = (cornerWidthMode == null) ? CornerWidthMode.EXPANDED
                : switch (cornerWidthMode) {
                    case EXPANDED -> CornerWidthMode.MINIMIZED;
                    case MINIMIZED -> CornerWidthMode.DEFAULT;
                    case DEFAULT -> CornerWidthMode.EXPANDED;
                };
        switch (next) {
            case EXPANDED -> ColumnAutoFit.packColumns(table);
            case MINIMIZED -> ColumnAutoFit.shrinkToMinimum(table);
            case DEFAULT -> ColumnAutoFit.applyDefaultWidths(table);
        }
        cornerWidthMode = next;
    }

    // ---------- Teclado ----------

    private void installKeyBindings() {
        InputMap im = table.getInputMap(JComponent.WHEN_FOCUSED);
        ActionMap am = table.getActionMap();

        im.put(KeyStroke.getKeyStroke("ESCAPE"), "nureal.clearSelection");
        am.put("nureal.clearSelection", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                table.clearSelection();
            }
        });

        im.put(KeyStroke.getKeyStroke("control A"), "nureal.selectAll");
        am.put("nureal.selectAll", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                table.selectAll();
                setSelectionScope(SelectionScope.MULTI);
            }
        });

        // Sobrescreve o "copy" nativo do JTable (que sempre copia TODA a
        // selecao como texto separado por TAB, mesmo quando ela e so o
        // resultado de destacar visualmente a linha inteira de um clique
        // simples) para decidir, via GridClipboard.copySelectionAuto, entre
        // copiar so a celula ATIVA (clique simples no corpo) ou a selecao
        // INTEIRA (coluna/linha/varias linhas escolhidas explicitamente pelo
        // usuario) — ver SELECTION_SCOPE_PROPERTY. O mesmo metodo e usado
        // pelo menu de contexto "Copiar", mantendo os dois caminhos
        // consistentes.
        im.put(KeyStroke.getKeyStroke("control C"), "nureal.copyCell");
        am.put("nureal.copyCell", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                GridClipboard.copySelectionAuto(table);
            }
        });

        // JTable, por padrao, so muda a LINHA no Ctrl+Home/Ctrl+End; aqui vale
        // para a celula inteira (primeira/ultima linha E coluna).
        im.put(KeyStroke.getKeyStroke("control HOME"), "nureal.selectFirstCell");
        am.put("nureal.selectFirstCell", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (table.getRowCount() > 0 && table.getColumnCount() > 0) {
                    table.changeSelection(0, 0, false, false);
                }
            }
        });

        im.put(KeyStroke.getKeyStroke("control END"), "nureal.selectLastCell");
        am.put("nureal.selectLastCell", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                int lastRow = table.getRowCount() - 1;
                int lastCol = table.getColumnCount() - 1;
                if (lastRow >= 0 && lastCol >= 0) {
                    table.changeSelection(lastRow, lastCol, false, false);
                }
            }
        });
    }

    // ---------- Perder foco limpa a selecao ----------

    private void installFocusClearing() {
        table.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                if (!e.isTemporary()) {
                    table.clearSelection();
                }
            }
        });
    }

    // ---------- Hover (nao altera selecao) ----------

    private void installHoverTracking() {
        table.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                setHoverRow(table.rowAtPoint(e.getPoint()));
            }
        });
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseExited(MouseEvent e) {
                setHoverRow(-1);
            }
        });
    }

    private void setHoverRow(int row) {
        int previous = hoverRow(table);
        if (previous == row) {
            return;
        }
        table.putClientProperty(HOVER_ROW_PROPERTY, row);
        repaintRow(previous);
        repaintRow(row);
    }

    private void repaintRow(int row) {
        if (row < 0 || row >= table.getRowCount()) {
            return;
        }
        Rectangle rect = table.getCellRect(row, 0, true);
        rect.width = table.getWidth();
        table.repaint(rect);
    }

    /** Linha (indice de VIEW) sob o mouse no momento, ou -1. Usado pelos renderers para o hover suave. */
    static int hoverRow(JTable table) {
        Object value = table.getClientProperty(HOVER_ROW_PROPERTY);
        return (value instanceof Integer i) ? i : -1;
    }

    // ---------- Escopo da selecao (consultado por GridClipboard) ----------

    private void setSelectionScope(SelectionScope scope) {
        table.putClientProperty(SELECTION_SCOPE_PROPERTY, scope);
    }

    /** Escopo da selecao atual — ver {@link #SELECTION_SCOPE_PROPERTY}. Padrao {@code CELL} se nunca definido. */
    static SelectionScope selectionScope(JTable table) {
        Object value = table.getClientProperty(SELECTION_SCOPE_PROPERTY);
        return (value instanceof SelectionScope scope) ? scope : SelectionScope.CELL;
    }
}
