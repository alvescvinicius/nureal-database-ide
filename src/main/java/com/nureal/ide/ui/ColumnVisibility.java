package com.nureal.ide.ui;

import javax.swing.JTable;
import javax.swing.table.TableColumn;
import java.util.ArrayList;
import java.util.List;

/**
 * Ocultar/mostrar colunas da grade de resultados. As colunas ocultas ficam
 * guardadas (na ordem em que foram removidas) numa propriedade cliente da
 * propria {@link JTable}, para nao exigir mais um mapa externo por tabela.
 *
 * Limitacao conhecida: como a reordenacao de colunas esta desabilitada na
 * grade, "Mostrar colunas" as devolve no FINAL do layout atual, nao
 * necessariamente na posicao original — aceitavel enquanto a grade nao
 * suportar reordenar colunas (ver relatorio final: melhoria futura).
 */
final class ColumnVisibility {

    private static final String HIDDEN_PROPERTY = "nureal.hiddenColumns";

    private ColumnVisibility() {
    }

    static void hide(JTable table, int viewColumn) {
        if (table.getColumnCount() <= 1) {
            return; // nunca oculta a ultima coluna visivel restante
        }
        TableColumn column = table.getColumnModel().getColumn(viewColumn);
        hiddenList(table).add(column);
        table.getColumnModel().removeColumn(column);
    }

    /** Mostra de volta SOMENTE a coluna oculta com este nome (usado pelo submenu "Mostrar colunas"). */
    static void show(JTable table, String columnName) {
        List<TableColumn> hidden = hiddenList(table);
        TableColumn found = null;
        for (TableColumn column : hidden) {
            if (String.valueOf(column.getHeaderValue()).equals(columnName)) {
                found = column;
                break;
            }
        }
        if (found != null) {
            hidden.remove(found);
            table.getColumnModel().addColumn(found);
        }
    }

    static void showAll(JTable table) {
        List<TableColumn> hidden = hiddenList(table);
        for (TableColumn column : hidden) {
            table.getColumnModel().addColumn(column);
        }
        hidden.clear();
    }

    static boolean hasHidden(JTable table) {
        return !hiddenList(table).isEmpty();
    }

    static List<String> hiddenNames(JTable table) {
        List<String> names = new ArrayList<>();
        for (TableColumn column : hiddenList(table)) {
            names.add(String.valueOf(column.getHeaderValue()));
        }
        return names;
    }

    @SuppressWarnings("unchecked")
    private static List<TableColumn> hiddenList(JTable table) {
        Object existing = table.getClientProperty(HIDDEN_PROPERTY);
        if (existing instanceof List<?>) {
            return (List<TableColumn>) existing;
        }
        List<TableColumn> created = new ArrayList<>();
        table.putClientProperty(HIDDEN_PROPERTY, created);
        return created;
    }
}
