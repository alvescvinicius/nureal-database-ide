package com.nureal.ide.ui;

import javax.swing.JOptionPane;
import javax.swing.JTable;
import java.awt.Component;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;

/**
 * Todas as operacoes de copia da grade de resultados (celula, linha, coluna,
 * selecao com cabecalho, CSV, JSON, INSERT, UPDATE) — extraidas de
 * {@code MainWindow} para um unico lugar reutilizavel pelo
 * {@link ResultContextMenu}. Sempre le atraves do {@link JTable} (indices de
 * VIEW), portanto respeita ordenacao/filtro atuais.
 */
final class GridClipboard {

    private GridClipboard() {
    }

    /**
     * Copia SO a celula ativa (a que tem a borda de destaque — ver
     * {@link AbstractTypedCellRenderer#paintActiveCellBorder}), NAO
     * {@code t.getSelectedRow()}/{@code t.getSelectedColumn()}: como um
     * clique simples seleciona a LINHA INTEIRA (todas as colunas — ver
     * {@link SelectionManager#selectFullRow}), {@code getSelectedColumn()}
     * devolveria sempre a PRIMEIRA coluna selecionada (0), nao a coluna que
     * o usuario efetivamente clicou — copiando silenciosamente o valor
     * errado. O indice de LEAD de cada modelo de selecao (linha e coluna) e
     * que aponta para a celula clicada por ultimo, seja qual for a extensao
     * da selecao ao redor dela.
     */
    static void copyCell(JTable t) {
        int r = t.getSelectionModel().getLeadSelectionIndex();
        int c = t.getColumnModel().getSelectionModel().getLeadSelectionIndex();
        if (r >= 0 && r < t.getRowCount() && c >= 0 && c < t.getColumnCount()) {
            setClipboard(cellText(t, r, c));
        }
    }

    /**
     * Copia a selecao INTEIRA (todas as linhas x colunas atualmente
     * selecionadas), separada por TAB, sem linha de cabecalho — o
     * equivalente a copiar um intervalo no Excel. Usada quando
     * {@link SelectionManager#selectionScope} indica {@code MULTI} (usuario
     * selecionou explicitamente uma coluna pelo cabecalho, uma linha pela
     * numeracao, "Selecionar tudo", ou estendeu a selecao com Shift/Ctrl no
     * corpo) — ver {@link #copySelectionAuto}, que decide qual dos dois
     * metodos chamar.
     */
    static void copySelection(JTable t) {
        int[] rows = t.getSelectedRows();
        int[] cols = t.getSelectedColumns();
        if (rows.length == 0 || cols.length == 0) {
            return;
        }
        setClipboard(GridExporter.toTabSeparated(t, rows, cols, false));
    }

    /**
     * Ponto de entrada UNICO do Ctrl+C e do "Copiar" do menu de contexto:
     * copia so a celula ativa quando a selecao veio de um clique simples no
     * corpo ({@link SelectionManager.SelectionScope#CELL} — a linha toda so
     * fica destacada visualmente, estilo Excel, mas a intencao e uma
     * celula), ou a selecao inteira quando o usuario pediu explicitamente
     * mais de uma celula ({@link SelectionManager.SelectionScope#MULTI} —
     * coluna pelo cabecalho, linha pela numeracao, Shift/Ctrl no corpo,
     * "Selecionar tudo").
     */
    static void copySelectionAuto(JTable t) {
        if (SelectionManager.selectionScope(t) == SelectionManager.SelectionScope.MULTI) {
            copySelection(t);
        } else {
            copyCell(t);
        }
    }

    /** Copia as linhas selecionadas, TODAS as colunas, separadas por TAB (sem cabecalho). */
    static void copyRows(JTable t) {
        int[] rows = t.getSelectedRows();
        if (rows.length == 0) {
            return;
        }
        setClipboard(GridExporter.toTabSeparated(t, rows, allColumns(t), false));
    }

    /**
     * Copia os dados de uma ou mais colunas — TODAS as linhas atualmente
     * visiveis (respeita filtro/ordenacao), nao so as selecionadas — usada
     * pelo menu do CABECALHO ("Copiar dados da coluna"). Se o usuario tiver
     * varias colunas selecionadas (Ctrl+clique nos cabecalhos), copia todas
     * lado a lado (TAB); senao, usa a coluna sob o clique direito.
     */
    static void copyColumnsData(JTable t, int fallbackViewColumn) {
        int[] cols = t.getSelectedColumns();
        if (cols.length == 0) {
            if (fallbackViewColumn < 0) {
                return;
            }
            cols = new int[] { fallbackViewColumn };
        }
        setClipboard(GridExporter.toTabSeparated(t, allRows(t), cols, false));
    }

    /** Copia a selecao atual (linhas x colunas selecionadas) com uma linha de cabecalho, separada por TAB. */
    static void copySelectionWithHeader(JTable t) {
        int[] rows = t.getSelectedRows();
        int[] cols = t.getSelectedColumns();
        if (rows.length == 0 || cols.length == 0) {
            return;
        }
        setClipboard(GridExporter.toTabSeparated(t, rows, cols, true));
    }

    static void copyAsCsv(JTable t) {
        int[] rows = selectedOrAllRows(t);
        int[] cols = selectedOrAllColumns(t);
        setClipboard(GridExporter.toCsv(t, rows, cols, true));
    }

    static void copyAsJson(JTable t) {
        int[] rows = selectedOrAllRows(t);
        int[] cols = selectedOrAllColumns(t);
        setClipboard(GridExporter.toJson(t, rows, cols));
    }

    /**
     * Gera INSERT INTO <tabela> (cols) VALUES (...) para as linhas
     * selecionadas. A tabela e detectada AUTOMATICAMENTE a partir da consulta
     * (ver {@link #resolveTableName}) sempre que possivel — so pergunta ao
     * usuario quando a origem e ambigua ou desconhecida.
     */
    static void copyAsInsert(JTable t, Component parent) {
        int[] rows = t.getSelectedRows();
        if (rows.length == 0) {
            return;
        }
        String table = resolveTableName(t, parent);
        if (table == null) {
            return;
        }
        int cols = t.getColumnCount();
        StringBuilder colList = new StringBuilder();
        for (int c = 0; c < cols; c++) {
            if (c > 0) {
                colList.append(", ");
            }
            colList.append(columnNameFor(t, c));
        }
        StringBuilder sb = new StringBuilder();
        for (int ri = 0; ri < rows.length; ri++) {
            sb.append("INSERT INTO ").append(table).append(" (").append(colList).append(")")
                    .append(" VALUES (");
            for (int c = 0; c < cols; c++) {
                if (c > 0) {
                    sb.append(", ");
                }
                sb.append(sqlValue(t.getValueAt(rows[ri], c)));
            }
            sb.append(");");
            if (ri < rows.length - 1) {
                sb.append('\n');
            }
        }
        setClipboard(sb.toString());
    }

    /**
     * Gera UPDATE <tabela> SET ... WHERE <1a coluna> = ... para as linhas
     * selecionadas. Mesma deteccao automatica de tabela de {@link #copyAsInsert}.
     */
    static void copyAsUpdate(JTable t, Component parent) {
        int[] rows = t.getSelectedRows();
        int cols = t.getColumnCount();
        if (rows.length == 0 || cols == 0) {
            return;
        }
        String table = resolveTableName(t, parent);
        if (table == null) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (int ri = 0; ri < rows.length; ri++) {
            sb.append("UPDATE ").append(table).append(" SET ");
            if (cols == 1) {
                sb.append(columnNameFor(t, 0)).append(" = ")
                        .append(sqlValue(t.getValueAt(rows[ri], 0)));
            } else {
                for (int c = 1; c < cols; c++) { // 1a coluna vira chave no WHERE
                    if (c > 1) {
                        sb.append(", ");
                    }
                    sb.append(columnNameFor(t, c)).append(" = ")
                            .append(sqlValue(t.getValueAt(rows[ri], c)));
                }
            }
            sb.append(" WHERE ").append(columnNameFor(t, 0)).append(" = ")
                    .append(sqlValue(t.getValueAt(rows[ri], 0))).append(";");
            if (ri < rows.length - 1) {
                sb.append('\n');
            }
        }
        setClipboard(sb.toString());
    }

    /**
     * Copia os valores das linhas selecionadas, na coluna ativa (mesmo lead
     * de selecao usado por {@link #copyCell}), como uma lista SQL pronta
     * para colar direto num WHERE: {@code IN (1, 2, 3)} / {@code IN ('a', 'b')}.
     */
    static void copyAsIn(JTable t) {
        int[] rows = t.getSelectedRows();
        int col = t.getColumnModel().getSelectionModel().getLeadSelectionIndex();
        if (rows.length == 0 || col < 0 || col >= t.getColumnCount()) {
            return;
        }
        setClipboard(toInClause(t, rows, col));
    }

    /** Como {@link #copyAsIn}, mas para TODAS as linhas visiveis da coluna — usada pelo menu do cabecalho. */
    static void copyColumnAsIn(JTable t, int viewColumn) {
        if (viewColumn < 0 || viewColumn >= t.getColumnCount() || t.getRowCount() == 0) {
            return;
        }
        setClipboard(toInClause(t, allRows(t), viewColumn));
    }

    private static String toInClause(JTable t, int[] rows, int col) {
        StringBuilder sb = new StringBuilder("IN (");
        for (int i = 0; i < rows.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(sqlValue(t.getValueAt(rows[i], col)));
        }
        return sb.append(')').toString();
    }

    private static int[] selectedOrAllRows(JTable t) {
        int[] sel = t.getSelectedRows();
        return sel.length > 0 ? sel : sequence(t.getRowCount());
    }

    private static int[] selectedOrAllColumns(JTable t) {
        int[] sel = t.getSelectedColumns();
        return sel.length > 0 ? sel : sequence(t.getColumnCount());
    }

    private static int[] allColumns(JTable t) {
        return sequence(t.getColumnCount());
    }

    private static int[] allRows(JTable t) {
        return sequence(t.getRowCount());
    }

    /**
     * Tenta descobrir, a partir da origem JDBC de cada coluna (ver
     * {@link ResultTableModel#sourceTable}), a UNICA tabela fisica de onde os
     * dados vieram — o caso comum de {@code SELECT ... FROM tabela} (com ou
     * sem WHERE/alias de coluna). Se as colunas vierem de tabelas diferentes
     * (JOIN) ou a origem for desconhecida (expressao, funcao, agregacao),
     * devolve {@code null}: melhor perguntar do que arriscar gerar um
     * INSERT/UPDATE para a tabela errada.
     */
    private static String detectSourceTable(JTable t) {
        if (!(t.getModel() instanceof ResultTableModel model)) {
            return null;
        }
        String table = null;
        for (int c = 0; c < t.getColumnCount(); c++) {
            String src = model.sourceTable(t.convertColumnIndexToModel(c));
            if (src == null || src.isBlank()) {
                continue;
            }
            if (table == null) {
                table = src;
            } else if (!table.equals(src)) {
                return null;
            }
        }
        return table;
    }

    /**
     * Nome da tabela para o INSERT/UPDATE: detectado automaticamente sempre
     * que possivel (ver {@link #detectSourceTable}) — so pergunta ao usuario
     * quando a consulta nao deixa isso claro (JOIN, expressao, etc.).
     */
    private static String resolveTableName(JTable t, Component parent) {
        String detected = detectSourceTable(t);
        return (detected != null) ? detected : promptTableName(parent);
    }

    /** Nome FISICO da coluna (sem alias) quando o driver informa; senao usa o rotulo exibido. */
    private static String columnNameFor(JTable t, int viewCol) {
        if (t.getModel() instanceof ResultTableModel model) {
            String real = model.realColumnName(t.convertColumnIndexToModel(viewCol));
            if (real != null && !real.isBlank()) {
                return real;
            }
        }
        return t.getColumnName(viewCol);
    }

    private static int[] sequence(int n) {
        int[] arr = new int[n];
        for (int i = 0; i < n; i++) {
            arr[i] = i;
        }
        return arr;
    }

    private static String cellText(JTable t, int row, int col) {
        Object v = t.getValueAt(row, col);
        return v == null ? "" : v.toString();
    }

    /** Pergunta o nome da tabela (default "tabela"). null = cancelado. */
    private static String promptTableName(Component parent) {
        String name = JOptionPane.showInputDialog(parent, "Nome da tabela:", "tabela");
        if (name == null) {
            return null;
        }
        name = name.trim();
        return name.isEmpty() ? "tabela" : name;
    }

    /** Formata um valor para SQL: NULL, numero, booleano (1/0) ou string entre aspas. */
    private static String sqlValue(Object v) {
        if (v == null) {
            return "NULL";
        }
        if (v instanceof Number) {
            return v.toString();
        }
        if (v instanceof Boolean b) {
            return b ? "1" : "0";
        }
        return "'" + v.toString().replace("'", "''") + "'";
    }

    /** Visibilidade de pacote: reaproveitado por {@code MainWindow} para copiar a selecao da arvore de objetos. */
    static void setClipboard(String text) {
        Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection(text), null);
    }
}
