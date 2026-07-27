package com.nureal.ide.ui;

import com.nureal.ide.core.log.AppLogger;
import com.nureal.ide.modulos.metadados.dominio.entidades.ForeignKeyInfo;

import javax.swing.JFileChooser;
import javax.swing.JMenu;
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

    /**
     * Recebe o pedido de "Visualizar Origem" (Inspetor Flutuante de FK) —
     * implementado por {@link ResultGrid}, que ja tem a conexao/schema/cache
     * de metadados a mao para abrir {@link FkInspectorWindow}. {@code row} e
     * o indice de VIEW da linha clicada (o handler converte para modelo e le
     * o valor de cada coluna local da FK, suportando FKs compostas).
     */
    interface FkOriginHandler {
        void openOrigin(ColumnMetadata metadata, int viewRow);
    }

    static void install(JTable table, ColumnSorter sorter, ColumnHeaderRenderer.MetadataSource metadataSource,
            FilterController filter, Runnable exportExcel) {
        install(table, sorter, metadataSource, filter, exportExcel, null);
    }

    static void install(JTable table, ColumnSorter sorter, ColumnHeaderRenderer.MetadataSource metadataSource,
            FilterController filter, Runnable exportExcel, FkOriginHandler fkOrigin) {
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
                buildMenu(table, sorter, metadataSource, filter, exportExcel, row, col, fkOrigin)
                        .show(table, e.getX(), e.getY());
            }
        });
    }

    /**
     * Igual a {@link #install}, so que para clique direito na NUMERACAO de
     * linha (gutter a esquerda da grade, ver {@link RowNumberGutter}) — sem
     * coluna especifica (os itens de coluna somem sozinhos em
     * {@link #buildMenu}, ver alteracao la), mas com copiar/exportar/limpar
     * filtro disponiveis, atuando sobre a linha sob o cursor (selecionada
     * primeiro, se ainda nao estivesse).
     */
    static void installOnRowGutter(javax.swing.JList<String> gutter, JTable table, ColumnSorter sorter,
            ColumnHeaderRenderer.MetadataSource metadataSource, FilterController filter, Runnable exportExcel,
            SelectionManager selection) {
        gutter.addMouseListener(new MouseAdapter() {
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
                int row = gutter.locationToIndex(e.getPoint());
                if (row >= 0 && !table.isRowSelected(row)) {
                    selection.selectRow(row, false, false);
                }
                buildMenu(table, sorter, metadataSource, filter, exportExcel, row, -1, null)
                        .show(gutter, e.getX(), e.getY());
            }
        });
    }

    /**
     * Igual a {@link #install}, so que para clique direito no CANTO
     * superior-esquerdo da grade (representa "selecionar tudo" — ver
     * {@link SelectionManager#installCorner}): clique direito ali seleciona
     * TODAS as linhas (se ainda nao estivessem todas selecionadas) e mostra o
     * mesmo menu, pronto pra copiar/exportar a grade inteira.
     */
    static void installOnCorner(javax.swing.JComponent corner, JTable table, ColumnSorter sorter,
            ColumnHeaderRenderer.MetadataSource metadataSource, FilterController filter, Runnable exportExcel) {
        corner.addMouseListener(new MouseAdapter() {
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
                if (table.getRowCount() > 0 && table.getSelectedRowCount() < table.getRowCount()) {
                    table.selectAll();
                }
                buildMenu(table, sorter, metadataSource, filter, exportExcel, -1, -1, null)
                        .show(corner, e.getX(), e.getY());
            }
        });
    }

    private static JPopupMenu buildMenu(JTable table, ColumnSorter sorter,
            ColumnHeaderRenderer.MetadataSource metadataSource, FilterController filter,
            Runnable exportExcel, int row, int col, FkOriginHandler fkOrigin) {
        JPopupMenu menu = new JPopupMenu();

        // "Copiar" (atalho direto, o mais usado) fica solto no topo; as
        // demais variantes (com cabecalhos, como INSERT/UPDATE/IN/JSON/CSV)
        // ficam agrupadas num submenu — eram 7 itens soltos antes, dificultando
        // escanear o menu rapido (pedido explicito: menu flat longo demais).
        menu.add(item("Copiar", () -> GridClipboard.copySelectionAuto(table)));
        JMenu copyMore = new JMenu("Copiar como...");
        copyMore.add(item("Com cabecalhos", () -> GridClipboard.copySelectionWithHeader(table)));
        copyMore.add(item("Linha", () -> GridClipboard.copyRows(table)));
        copyMore.add(item("INSERT", () -> GridClipboard.copyAsInsert(table, table)));
        copyMore.add(item("UPDATE", () -> GridClipboard.copyAsUpdate(table, table)));
        copyMore.add(item("IN (...)", () -> GridClipboard.copyAsIn(table)));
        copyMore.add(item("JSON", () -> GridClipboard.copyAsJson(table)));
        copyMore.add(item("CSV", () -> GridClipboard.copyAsCsv(table)));
        menu.add(copyMore);
        menu.addSeparator();

        JMenu exportMenu = new JMenu("Exportar");
        exportMenu.add(item("Excel...", exportExcel));
        exportMenu.add(item("CSV...", () -> exportToFile(table, "csv")));
        exportMenu.add(item("JSON...", () -> exportToFile(table, "json")));
        menu.add(exportMenu);
        menu.addSeparator();

        int modelColumn = (col >= 0) ? table.getColumnModel().getColumn(col).getModelIndex() : -1;
        if (row >= 0 && col >= 0) {
            Object value = table.getValueAt(row, col);
            String text = (value == null) ? "" : value.toString();
            menu.add(item("Filtrar por este valor", () -> filter.filterByValue(modelColumn, text)));
            menu.add(item("Ver conteudo completo", () ->
                    CellContentViewer.show(table, col, value)));
            if (fkOrigin != null && metadataSource != null) {
                ColumnMetadata meta = metadataSource.metadataFor(modelColumn);
                if (meta != null && meta.hasForeignKey()) {
                    ForeignKeyInfo fk = meta.foreignKey();
                    final int viewRow = row;
                    menu.add(item("Visualizar Origem (" + fk.referencedTable() + ")",
                            () -> fkOrigin.openOrigin(meta, viewRow)));
                }
            }
        }
        menu.add(item("Limpar filtro", filter::clearFilter));

        if (modelColumn >= 0) {
            menu.addSeparator();
            menu.add(item("Ordenar crescente",
                    () -> sorter.setSingleSort(modelColumn, SortOrder.ASCENDING)));
            menu.add(item("Ordenar decrescente",
                    () -> sorter.setSingleSort(modelColumn, SortOrder.DESCENDING)));
            menu.addSeparator();
            menu.add(item("Informacoes da coluna", () -> {
                if (metadataSource != null) {
                    ColumnMetadataPopup.showDialog(table, metadataSource.metadataFor(modelColumn));
                }
            }));
        }

        // So desabilita o menu INTEIRO quando nao ha absolutamente nada pra
        // agir (grade vazia) — clique na numeracao de linha (gutter) ou no
        // canto (selecionar tudo) chega aqui com col=-1 (sem coluna
        // especifica), mas ainda faz total sentido copiar/exportar a
        // selecao de linhas, so os itens de coluna (filtro/ordenacao/
        // informacoes) e que ficam de fora (ver acima).
        if (table.getRowCount() == 0) {
            disableAll(menu);
        }
        return menu;
    }

    private static void exportToFile(JTable table, String extension) {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Exportar " + extension.toUpperCase(java.util.Locale.ROOT));
        fc.setSelectedFile(new File("resultado." + extension));
        fc.setFileFilter(new FileNameExtensionFilter(extension.toUpperCase(java.util.Locale.ROOT), extension));
        // Centraliza na JANELA (nao na grade em si) — ver DialogUtil.
        if (fc.showSaveDialog(DialogUtil.owner(table)) != JFileChooser.APPROVE_OPTION) {
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
            JOptionPane.showMessageDialog(DialogUtil.owner(table), "Falha ao exportar: " + ex.getMessage(),
                    "Exportar", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static JMenuItem item(String text, Runnable action) {
        JMenuItem item = new JMenuItem(text);
        // Sem este try/catch, uma excecao de runtime dentro de "action" (ex.:
        // NullPointerException ao montar o Inspetor de FK) escapava direto
        // para o tratador padrao do EDT — que, num app empacotado sem console
        // anexado (o caso normal do usuario final), nao mostra NADA: o clique
        // simplesmente "nao fazia nada" aos olhos de quem usa a IDE, sem
        // qualquer pista do motivo (bug relatado: "a visualizacao de tabelas
        // origem nao esta funcionando", sem nenhum erro visivel). Agora
        // qualquer falha em QUALQUER item deste menu (Visualizar Origem,
        // copiar, exportar, filtrar, ordenar, informacoes da coluna) fica
        // visivel na hora E registrada em ~/.nureal-ide/nureal-ide.log.
        item.addActionListener(e -> {
            try {
                action.run();
            } catch (Exception ex) {
                AppLogger.severe("Falha ao executar acao do menu de contexto: " + text, ex);
                JOptionPane.showMessageDialog(e.getSource() instanceof java.awt.Component c ? DialogUtil.owner(c) : null,
                        "Nao foi possivel completar \"" + text + "\":\n" + ex.getMessage(),
                        "Erro", JOptionPane.ERROR_MESSAGE);
            }
        });
        return item;
    }

    private static void disableAll(JPopupMenu menu) {
        for (var comp : menu.getComponents()) {
            comp.setEnabled(false);
        }
    }
}
