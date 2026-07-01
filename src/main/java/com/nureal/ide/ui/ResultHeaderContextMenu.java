package com.nureal.ide.ui;

import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.JTable;
import javax.swing.SortOrder;
import javax.swing.table.JTableHeader;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

/**
 * Menu de contexto (clique direito) do CABECALHO da grade de resultados:
 * ordenar, filtrar, autofit (uma coluna / todas), ocultar/mostrar colunas,
 * congelar coluna (estrutura preparada, ainda desabilitada) e informacoes da
 * coluna.
 */
final class ResultHeaderContextMenu {

    private ResultHeaderContextMenu() {
    }

    static void install(JTable table, JTableHeader header, ColumnSorter sorter,
            ColumnHeaderRenderer.MetadataSource metadataSource, ResultContextMenu.FilterController filter,
            Runnable onLayoutChanged) {
        header.addMouseListener(new MouseAdapter() {
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
                int viewColumn = header.columnAtPoint(e.getPoint());
                if (viewColumn < 0) {
                    return;
                }
                buildMenu(table, sorter, metadataSource, filter, onLayoutChanged, viewColumn)
                        .show(header, e.getX(), e.getY());
            }
        });
    }

    private static JPopupMenu buildMenu(JTable table, ColumnSorter sorter,
            ColumnHeaderRenderer.MetadataSource metadataSource, ResultContextMenu.FilterController filter,
            Runnable onLayoutChanged, int viewColumn) {
        int modelColumn = table.getColumnModel().getColumn(viewColumn).getModelIndex();
        String columnName = table.getColumnName(viewColumn);

        JPopupMenu menu = new JPopupMenu();

        JMenu sortMenu = new JMenu("Ordenar");
        sortMenu.add(item("Crescente", () -> sorter.setSingleSort(modelColumn, SortOrder.ASCENDING)));
        sortMenu.add(item("Decrescente", () -> sorter.setSingleSort(modelColumn, SortOrder.DESCENDING)));
        sortMenu.add(item("Remover ordenacao", sorter::clear));
        menu.add(sortMenu);

        menu.add(item("Filtrar por esta coluna...", () -> promptFilter(table, filter, modelColumn, columnName)));
        menu.add(item("Limpar filtro", filter::clearFilter));
        menu.addSeparator();

        menu.add(item("AutoFit", () -> runAndNotify(() -> ColumnAutoFit.packColumn(table, viewColumn), onLayoutChanged)));
        menu.add(item("AutoFit Todas", () -> runAndNotify(() -> ColumnAutoFit.packColumns(table), onLayoutChanged)));
        // Saida deliberada para o caso em que uma largura JA FOI SALVA para
        // este conjunto de colunas (ver GridPreferences — a persistencia e
        // por fingerprint de nomes, entao qualquer clique anterior no
        // cabecalho, mesmo so para ordenar, ja grava a largura atual em
        // disco): sem isto, o usuario nao tem como voltar para a largura
        // padrao uniforme depois de uma largura customizada/antiga ter sido
        // persistida, a nao ser apagando o arquivo de preferencias inteiro.
        menu.add(item("Redefinir para largura padrao (todas)",
                () -> runAndNotify(() -> ColumnAutoFit.applyDefaultWidths(table), onLayoutChanged)));
        menu.addSeparator();

        menu.add(item("Ocultar coluna", () -> runAndNotify(() -> ColumnVisibility.hide(table, viewColumn), onLayoutChanged)));
        menu.add(buildShowColumnsMenu(table, onLayoutChanged));
        menu.addSeparator();

        JMenuItem freeze = item("Congelar coluna", () -> { /* estrutura preparada; sem efeito ainda */ });
        freeze.setEnabled(false);
        freeze.setToolTipText("Ainda nao implementado — reservado para uma proxima versao");
        menu.add(freeze);
        menu.addSeparator();

        menu.add(item("Informacoes da coluna", () -> {
            if (metadataSource != null) {
                ColumnMetadataPopup.showDialog(table, metadataSource.metadataFor(modelColumn));
            }
        }));
        return menu;
    }

    private static JMenu buildShowColumnsMenu(JTable table, Runnable onLayoutChanged) {
        JMenu showMenu = new JMenu("Mostrar colunas");
        List<String> hidden = ColumnVisibility.hiddenNames(table);
        if (hidden.isEmpty()) {
            showMenu.setEnabled(false);
            return showMenu;
        }
        JMenuItem showAll = item("Mostrar todas", () -> runAndNotify(() -> ColumnVisibility.showAll(table), onLayoutChanged));
        showMenu.add(showAll);
        showMenu.addSeparator();
        for (String name : hidden) {
            JCheckBoxMenuItem item = new JCheckBoxMenuItem(name, false);
            item.addActionListener(_ -> runAndNotify(() -> ColumnVisibility.show(table, name), onLayoutChanged));
            showMenu.add(item);
        }
        return showMenu;
    }

    private static void runAndNotify(Runnable action, Runnable onLayoutChanged) {
        action.run();
        if (onLayoutChanged != null) {
            onLayoutChanged.run();
        }
    }

    private static void promptFilter(JTable table, ResultContextMenu.FilterController filter,
            int modelColumn, String columnName) {
        String value = JOptionPane.showInputDialog(table, "Filtrar \"" + columnName + "\" contendo:");
        if (value != null) {
            filter.filterByValue(modelColumn, value);
        }
    }

    private static JMenuItem item(String text, Runnable action) {
        JMenuItem item = new JMenuItem(text);
        item.addActionListener(_ -> action.run());
        return item;
    }
}
