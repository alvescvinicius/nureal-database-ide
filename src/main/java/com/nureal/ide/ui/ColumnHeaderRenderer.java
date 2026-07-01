package com.nureal.ide.ui;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.SortOrder;
import javax.swing.SwingConstants;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;

/**
 * Renderer do cabecalho da grade de resultados. Cada celula de cabecalho tem
 * DUAS zonas fixas, lado a lado, para que nada se desloque ou sobreponha
 * conforme o texto do nome muda:
 *
 * <pre>NOME DA COLUNA   [ zona de ordenacao, sempre a direita ]</pre>
 *
 * A zona de ordenacao ({@link #SORT_ZONE_WIDTH} px de largura) tem DUAS
 * setinhas EMPILHADAS, nao uma so: {@link #upArrow} (metade de cima) ordena
 * CRESCENTE ao ser clicada, {@link #downArrow} (metade de baixo) ordena
 * DECRESCENTE — cada uma independente, clicavel por si so (ver
 * {@link ResultTableHeader#arrowAtPoint}). A setinha da direcao ATIVA fica
 * destacada (cor {@link GridTheme#SORT_INDICATOR_ACTIVE}); a outra continua
 * cinza. Sem nenhuma das duas ativa, a coluna mantem a ordem original da
 * consulta (sem ordenacao) — nao ha mais um "meio-termo" de ciclo implicito,
 * so os dois estados explicitos que o usuario escolhe clicando. A zona e
 * SEMPRE reservada, ordenada a coluna ou nao, para o texto nunca "pular"
 * quando uma ordenacao e aplicada/removida em qualquer coluna da grade.
 *
 * NAO ha icone de PK/FK aqui (pedido explicito do usuario — poluia o
 * cabecalho); essa informacao continua disponivel sob demanda no popup de
 * hover ({@link ColumnMetadataPopup}) e no dialogo "Informacoes da coluna",
 * ambos alimentados pelo mesmo {@link MetadataSource} independente desta
 * classe.
 */
final class ColumnHeaderRenderer implements TableCellRenderer {

    static final int SORT_ZONE_WIDTH = 18;

    /** Fonte de metadados (PK/FK) para o popup de hover/dialogo de informacoes da coluna. Pode devolver {@code null}. */
    @FunctionalInterface
    interface MetadataSource {
        ColumnMetadata metadataFor(int modelColumn);
    }

    private final ColumnSorter sorter;

    private final JPanel panel = new JPanel(new BorderLayout(4, 0));
    private final JLabel nameLabel = new JLabel();
    private final JPanel sortZone = new JPanel(new GridLayout(2, 1, 0, 0));
    private final JLabel upArrow = new JLabel("▲", SwingConstants.CENTER);   // ▲
    private final JLabel downArrow = new JLabel("▼", SwingConstants.CENTER); // ▼

    ColumnHeaderRenderer(ColumnSorter sorter) {
        this.sorter = sorter;

        panel.setOpaque(true);
        panel.setBackground(GridTheme.HEADER_BACKGROUND);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 2, 1, GridTheme.HEADER_BORDER),
                BorderFactory.createEmptyBorder(5, 10, 5, 10)));

        nameLabel.setForeground(GridTheme.HEADER_FOREGROUND);
        nameLabel.setHorizontalAlignment(SwingConstants.LEFT);

        Font arrowFont = upArrow.getFont().deriveFont(Font.BOLD, 8f);
        upArrow.setFont(arrowFont);
        downArrow.setFont(arrowFont);
        sortZone.setOpaque(false);
        sortZone.setPreferredSize(new Dimension(SORT_ZONE_WIDTH, 1));
        sortZone.add(upArrow);
        sortZone.add(downArrow);

        panel.add(nameLabel, BorderLayout.CENTER);
        panel.add(sortZone, BorderLayout.EAST);
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value,
            boolean isSelected, boolean hasFocus, int row, int viewColumn) {
        nameLabel.setText(value == null ? "" : value.toString());
        nameLabel.setFont(table.getTableHeader().getFont().deriveFont(Font.BOLD));

        int modelColumn = table.getColumnModel().getColumn(viewColumn).getModelIndex();
        applySortIndicator(modelColumn);

        Integer height = null;
        JTableHeader header = table.getTableHeader();
        if (header != null) {
            height = header.getHeight();
        }
        if (height != null && height > 0) {
            panel.setPreferredSize(new Dimension(panel.getPreferredSize().width, height));
        }
        return panel;
    }

    /**
     * Cada setinha reflete SO a sua propria direcao — nunca as duas ativas ao
     * mesmo tempo (a coluna esta ordenada ascendente OU descendente OU nem
     * uma das duas). O numero de prioridade (quando ha ordenacao multipla,
     * Ctrl+clique) aparece grudado na setinha ativa, a mesma convencao de
     * antes (ex.: "▲2").
     */
    private void applySortIndicator(int modelColumn) {
        SortOrder order = sorter.orderOf(modelColumn);
        int priority = sorter.priorityOf(modelColumn);
        String suffix = (sorter.isMultiSort() && priority > 0) ? String.valueOf(priority) : "";

        boolean ascActive = order == SortOrder.ASCENDING;
        boolean descActive = order == SortOrder.DESCENDING;

        upArrow.setText(ascActive ? "▲" + suffix : "▲");
        upArrow.setForeground(ascActive ? GridTheme.SORT_INDICATOR_ACTIVE : GridTheme.SORT_INDICATOR_INACTIVE);

        downArrow.setText(descActive ? "▼" + suffix : "▼");
        downArrow.setForeground(descActive ? GridTheme.SORT_INDICATOR_ACTIVE : GridTheme.SORT_INDICATOR_INACTIVE);
    }
}
