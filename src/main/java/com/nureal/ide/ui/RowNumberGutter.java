package com.nureal.ide.ui;
import com.nureal.ide.compartilhado.designsystem.GridTheme;

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
import java.awt.FontMetrics;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;

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

    static JList<String> build(JTable table, DefaultTableModel model, SelectionManager selection) {
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
        // Largura RECALCULADA pelo numero de digitos da MAIOR linha (nao
        // mais um valor fixo de 54px): com "Carregar tudo"/paginacao sob
        // demanda, uma grade pode facilmente passar de 1000/10000 linhas —
        // 54px so cabia 3 digitos confortavelmente, entao numeros maiores
        // (ex.: "10000") ficavam cortados com "..." pelo JLabel (bug
        // relatado pelo usuario com captura de tela). Recalculada TODA VEZ
        // que o numero de linhas muda (ver o TableModelListener abaixo),
        // nao so uma vez na construcao.
        Runnable updateGutterWidth = () -> {
            int digits = Integer.toString(Math.max(table.getRowCount(), 1)).length();
            FontMetrics fm = list.getFontMetrics(font);
            int textWidth = fm.stringWidth("0".repeat(digits));
            list.setFixedCellWidth(textWidth + 6 + 8 + 6); // + padding do renderer (6,8) + folga
        };
        updateGutterWidth.run();
        model.addTableModelListener(e -> {
            updateGutterWidth.run();
            list.revalidate();
            list.repaint();
        });
        // mantem a numeracao/destaque em sincronia com ordenacao/filtro/selecao
        if (table.getRowSorter() != null) {
            table.getRowSorter().addRowSorterListener(e -> {
                list.revalidate();
                list.repaint();
            });
        }
        table.getSelectionModel().addListSelectionListener(e -> list.repaint());

        // Ancora do arrasto "estilo Excel" (clicar na numeracao e arrastar
        // pra cima/baixo estende a selecao por todas as linhas no caminho,
        // ao vivo — mesmo padrao usado no cabecalho para colunas, ver
        // ResultTableHeader). -1 = nenhum arrasto em andamento; so comeca a
        // partir de um clique SIMPLES (sem Ctrl/Shift, que ja tem seu
        // proprio significado e nao encadeiam com arrasto aqui).
        int[] dragAnchorRow = { -1 };

        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                int row = list.locationToIndex(e.getPoint());
                if (row < 0 || table.getColumnCount() == 0) {
                    dragAnchorRow[0] = -1;
                    return;
                }
                table.requestFocusInWindow();
                selection.selectRow(row, e.isControlDown(), e.isShiftDown());
                table.scrollRectToVisible(table.getCellRect(row, 0, true));
                dragAnchorRow[0] = (e.isControlDown() || e.isShiftDown()) ? -1 : row;
            }
        });
        list.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                if (dragAnchorRow[0] < 0) {
                    return;
                }
                int row = list.locationToIndex(e.getPoint());
                if (row < 0) {
                    return;
                }
                selection.selectRow(row, false, true); // true = estende da ancora (dragAnchorRow) ate aqui
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
