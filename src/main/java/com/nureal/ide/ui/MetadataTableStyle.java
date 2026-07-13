package com.nureal.ide.ui;

import java.awt.Component;
import java.awt.Dimension;

import javax.swing.BorderFactory;
import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableModel;

/**
 * Mesmo visual (zebra, selecao, cabecalho) da grade de resultados
 * ({@link ResultGrid}/{@link GridTheme}), aplicado a tabelas de metadados
 * mais simples que NAO passam pelo {@link ResultGrid} de verdade — este e
 * acoplado a um {@link ResultTableModel}/ResultSet de JDBC, enquanto estas
 * tabelas (colunas/indices/chaves estrangeiras da propriedade de um objeto em
 * {@code MainWindow}, tabelas editaveis do assistente de DDL em
 * {@code DdlAssistantDialog}) recebem os dados prontos como
 * {@code DefaultTableModel}.
 * <p>
 * Pedido da revisao visual: "toda estrutura tabular da IDE deve utilizar o
 * grid de resultados como referencia" — antes cada uma dessas tabelas usava
 * o visual cru padrao do Swing/FlatLaf, bem mais pobre que a grade principal
 * e visivelmente "de outro app" dentro da mesma janela.
 */
final class MetadataTableStyle {

    private MetadataTableStyle() {
    }

    /**
     * Igual a {@code new JTable(model)} + {@link #apply}, so que o resultado
     * tambem reaplica {@link #apply} sozinho a cada troca de tema —
     * {@link #apply} setava selecao/fundo/cabecalho uma unica vez, CONGELADOS
     * na paleta de quando a tabela foi criada (mesma familia de bug ja
     * corrigida em {@code ResultGrid}$JTable, {@code ConnectionsPanel} etc.).
     * Preferir este metodo a {@code new JTable(model)} + {@link #apply}
     * separados em qualquer tabela de metadados NOVA (ver
     * {@code DdlAssistantDialog}, unico consumidor hoje).
     */
    static JTable createStyledTable(TableModel model) {
        JTable table = new JTable(model) {
            private static final long serialVersionUID = 1L;

            @Override
            public void updateUI() {
                super.updateUI();
                apply(this);
            }
        };
        apply(table);
        return table;
    }

    static void apply(JTable table) {
        table.setRowHeight(24);
        table.setShowGrid(true);
        table.setGridColor(GridTheme.GRID_LINE);
        table.setIntercellSpacing(new Dimension(0, 1));
        table.setSelectionBackground(GridTheme.SELECTION_BACKGROUND);
        table.setSelectionForeground(GridTheme.SELECTION_FOREGROUND);
        table.setBackground(GridTheme.ZEBRA_EVEN);
        table.setForeground(GridTheme.COLOR_TEXTUAL);
        table.getTableHeader().setBackground(GridTheme.HEADER_BACKGROUND);
        table.getTableHeader().setForeground(GridTheme.HEADER_FOREGROUND);
        table.getTableHeader().setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, GridTheme.HEADER_BORDER));
        table.setDefaultRenderer(Object.class, new ZebraTextRenderer());
    }

    /** Zebra simples (linhas pares/impares no mesmo tom da grade de resultados). */
    private static final class ZebraTextRenderer extends DefaultTableCellRenderer {
        private static final long serialVersionUID = 1L;

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
            if (!isSelected) {
                setBackground((row % 2 == 0) ? GridTheme.ZEBRA_EVEN : GridTheme.ZEBRA_ODD);
                setForeground(GridTheme.COLOR_TEXTUAL);
            }
            return this;
        }
    }
}
