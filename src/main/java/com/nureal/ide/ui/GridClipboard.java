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

    static void copyColumn(JTable t) {
        int c = t.getSelectedColumn();
        if (c < 0) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        int rows = t.getRowCount();
        for (int r = 0; r < rows; r++) {
            if (r > 0) {
                sb.append('\n');
            }
            sb.append(cellText(t, r, c));
        }
        setClipboard(sb.toString());
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

    /** Gera INSERT INTO <tabela> (cols) VALUES (...) para as linhas selecionadas. */
    static void copyAsInsert(JTable t, Component parent) {
        int[] rows = t.getSelectedRows();
        if (rows.length == 0) {
            return;
        }
        String table = promptTableName(parent);
        if (table == null) {
            return;
        }
        int cols = t.getColumnCount();
        StringBuilder colList = new StringBuilder();
        for (int c = 0; c < cols; c++) {
            if (c > 0) {
                colList.append(", ");
            }
            colList.append(t.getColumnName(c));
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

    /** Gera UPDATE <tabela> SET ... WHERE <1a coluna> = ... para as linhas selecionadas. */
    static void copyAsUpdate(JTable t, Component parent) {
        int[] rows = t.getSelectedRows();
        int cols = t.getColumnCount();
        if (rows.length == 0 || cols == 0) {
            return;
        }
        String table = promptTableName(parent);
        if (table == null) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (int ri = 0; ri < rows.length; ri++) {
            sb.append("UPDATE ").append(table).append(" SET ");
            if (cols == 1) {
                sb.append(t.getColumnName(0)).append(" = ")
                        .append(sqlValue(t.getValueAt(rows[ri], 0)));
            } else {
                for (int c = 1; c < cols; c++) { // 1a coluna vira chave no WHERE
                    if (c > 1) {
                        sb.append(", ");
                    }
                    sb.append(t.getColumnName(c)).append(" = ")
                            .append(sqlValue(t.getValueAt(rows[ri], c)));
                }
            }
            sb.append(" WHERE ").append(t.getColumnName(0)).append(" = ")
                    .append(sqlValue(t.getValueAt(rows[ri], 0))).append(";");
            if (ri < rows.length - 1) {
                sb.append('\n');
            }
        }
        setClipboard(sb.toString());
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

    private static void setClipboard(String text) {
        Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection(text), null);
    }
}
