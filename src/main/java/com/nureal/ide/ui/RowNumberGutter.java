package com.nureal.ide.ui;

import javax.swing.AbstractListModel;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.ListCellRenderer;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableModel;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Coluna fixa (gutter) com o numero de cada linha, a esquerda da grade, e o
 * painel que ocupa o canto superior-esquerdo do {@link javax.swing.JScrollPane}.
 * Clicar num numero seleciona a linha correspondente atraves do mesmo
 * {@link SelectionManager} usado pelo corpo da tabela — Ctrl/Shift funcionam
 * igual em ambos os lugares, sem duplicar a logica de selecao.
 */
final class RowNumberGutter {

    private RowNumberGutter() {
    }

    static JComponent build(JTable table, DefaultTableModel model, SelectionManager selection) {
        AbstractListModel<String> listModel = new AbstractListModel<>() {
            private static final long serialVersionUID = 1L;

            @Override
            public int getSize() {
                return table.getRowCount();
            }

            @Override
            public String getElementAt(int index) {
                return Integer.toString(index + 1);
            }
        };
        JList<String> list = new JList<>(listModel);
        list.setFixedCellHeight(table.getRowHeight());
        list.setFixedCellWidth(54);
        list.setFocusable(false);
        final Font font = list.getFont().deriveFont(Font.PLAIN);
        ListCellRenderer<Object> renderer = (lst, value, index, selected, focused) -> {
            JLabel l = new JLabel(value == null ? "" : value.toString());
            l.setHorizontalAlignment(SwingConstants.RIGHT);
            l.setOpaque(true);
            l.setBackground(table.isRowSelected(index) ? table.getSelectionBackground() : GridTheme.GUTTER_BACKGROUND);
            l.setForeground(GridTheme.GUTTER_FOREGROUND);
            l.setFont(font);
            l.setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 8));
            return l;
        };
        list.setCellRenderer(renderer);
        model.addTableModelListener(k -> {
            list.revalidate();
            list.repaint();
        });
        // mantem a numeracao/destaque em sincronia com ordenacao/filtro/selecao
        if (table.getRowSorter() != null) {
            table.getRowSorter().addRowSorterListener(k -> {
                list.revalidate();
                list.repaint();
            });
        }
        table.getSelectionModel().addListSelectionListener(k -> list.repaint());

        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                int row = list.locationToIndex(e.getPoint());
                if (row < 0 || table.getColumnCount() == 0) {
                    return;
                }
                table.requestFocusInWindow();
                selection.selectRow(row, e.isControlDown(), e.isShiftDown());
                table.scrollRectToVisible(table.getCellRect(row, 0, true));
            }
        });
        return list;
    }

    static JComponent corner() {
        JPanel corner = new JPanel();
        corner.setOpaque(true);
        corner.setBackground(GridTheme.HEADER_BACKGROUND);
        corner.setBorder(BorderFactory.createMatteBorder(0, 0, 2, 1, GridTheme.HEADER_BORDER));
        corner.setCursor(Cursor.getDefaultCursor());
        return corner;
    }
}
