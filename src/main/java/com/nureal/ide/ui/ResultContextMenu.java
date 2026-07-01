package com.nureal.ide.ui;

import javax.swing.JFileChooser;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.JTable;
import javax.swing.SortOrder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;

/**
 * Menu de contexto (clique direito) de uma CELULA da grade de resultados:
 * copiar (varios formatos), exportar, filtrar por valor e atalhos de
 * ordenacao/informacoes da coluna sob o cursor.
 */
final class ResultContextMenu {

    private ResultContextMenu() {
    }

    /** Recebe o pedido de filtrar/limpar filtro por valor de celula — implementado por {@link ResultGrid}. */
    interface FilterController {
        void filterByValue(int modelColumn, String value);
        void clearFilter();
    }

    static void install(JTable table, ColumnSorter sorter, ColumnHeaderRenderer.MetadataSource metadataSource,
            FilterController filter, Runnable exportExcel) {
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                maybeShow(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                maybeShow(e);
            }

            private void maybeShow(MouseEvent e) {
                if (!e.isPopupTrigger()) {
                    return;
                }
                int row = table.rowAtPoint(e.getPoint());
                int col = table.columnAtPoint(e.getPoint());
                if (row >= 0 && col >= 0 && !table.isCellSelected(row, col)) {
                    table.changeSelection(row, col, false, false);
                }
                buildMenu(table, sorter, metadataSource, filter, exportExcel, row, col)
                        .show(table, e.getX(), e.getY());
            }
        });
    }

    private static JPopupMenu buildMenu(JTable table, ColumnSorter sorter,
            ColumnHeaderRenderer.MetadataSource metadataSource, FilterController filter,
            Runnable exportExcel, int row, int col) {
        JPopupMenu menu = new JPopupMenu();

        menu.add(item("Copiar", () -> GridClipboard.copySelectionAuto(table)));
        menu.add(item("Copiar com cabecalhos", () -> GridClipboard.copySelectionWithHeader(table)));
        menu.add(item("Copiar linha", () -> GridClipboard.copyRows(table)));
        menu.add(item("Copiar como INSERT", () -> GridClipboard.copyAsInsert(table, table)));
        menu.add(item("Copiar como UPDATE", () -> GridClipboard.copyAsUpdate(table, table)));
        menu.add(item("Copiar como JSON", () -> GridClipboard.copyAsJson(table)));
        menu.add(item("Copiar como CSV", () -> GridClipboard.copyAsCsv(table)));
        menu.addSeparator();

        menu.add(item("Exportar Excel...", exportExcel));
        menu.add(item("Exportar CSV...", () -> exportToFile(table, "csv")));
        menu.add(item("Exportar JSON...", () -> exportToFile(table, "json")));
        menu.addSeparator();

        int modelColumn = (col >= 0) ? table.getColumnModel().getColumn(col).getModelIndex() : -1;
        if (row >= 0 && col >= 0) {
            Object value = table.getValueAt(row, col);
            String text = (value == null) ? "" : value.toString();
            menu.add(item("Filtrar por este valor", () -> filter.filterByValue(modelColumn, text)));
            menu.add(item("Ver conteudo completo", () ->
                    CellContentViewer.show(table, table.getColumnName(col), value)));
        }
        menu.add(item("Limpar filtro", filter::clearFilter));
        menu.addSeparator();

        menu.add(item("Ordenar crescente",
                () -> sorter.setSingleSort(modelColumn, SortOrder.ASCENDING)));
        menu.add(item("Ordenar decrescente",
                () -> sorter.setSingleSort(modelColumn, SortOrder.DESCENDING)));
        menu.addSeparator();

        menu.add(item("Informacoes da coluna", () -> {
            if (modelColumn >= 0 && metadataSource != null) {
                ColumnMetadataPopup.showDialog(table, metadataSource.metadataFor(modelColumn));
            }
        }));

        if (modelColumn < 0) {
            disableAll(menu);
        }
        return menu;
    }

    private static void exportToFile(JTable table, String extension) {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Exportar " + extension.toUpperCase(java.util.Locale.ROOT));
        fc.setSelectedFile(new File("resultado." + extension));
        fc.setFileFilter(new FileNameExtensionFilter(extension.toUpperCase(java.util.Locale.ROOT), extension));
        if (fc.showSaveDialog(table) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File file = fc.getSelectedFile();
        if (!file.getName().toLowerCase(java.util.Locale.ROOT).endsWith("." + extension)) {
            file = new File(file.getParentFile(), file.getName() + "." + extension);
        }
        try {
            if ("csv".equals(extension)) {
                GridExporter.exportCsv(table, file.toPath());
            } else {
                GridExporter.exportJson(table, file.toPath());
            }
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(table, "Falha ao exportar: " + ex.getMessage(),
                    "Exportar", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static JMenuItem item(String text, Runnable action) {
        JMenuItem item = new JMenuItem(text);
        item.addActionListener(e -> action.run());
        return item;
    }

    private static void disableAll(JPopupMenu menu) {
        for (var comp : menu.getComponents()) {
            comp.setEnabled(false);
        }
    }
}
