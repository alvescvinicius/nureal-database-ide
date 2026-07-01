package com.nureal.ide.ui;

import javax.swing.JTable;
import javax.swing.SortOrder;
import javax.swing.table.JTableHeader;
import java.awt.Cursor;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;

/**
 * Cabecalho da grade de resultados: instala o {@link ColumnHeaderRenderer}
 * (nome + duas setinhas de ordenacao sempre a direita), trata os cliques
 * (metade de cima da zona de ordenacao -&gt; {@link ColumnSorter} crescente;
 * metade de baixo -&gt; decrescente; resto do cabecalho -&gt;
 * {@link SelectionManager#selectColumn}) e o duplo-clique na divisoria
 * (-&gt; {@link ColumnAutoFit}), e liga o popup de metadados no hover
 * ({@link ColumnMetadataPopup}).
 *
 * Substitui o antigo {@code ForeignKeyHeaderSupport}: o indicador de FK
 * continua existindo (agora no {@link ColumnHeaderRenderer}, via
 * {@link ColumnMetadata}), mas o antigo popup de FK por CLIQUE foi
 * substituido pelo popup de metadados por HOVER, mais completo.
 */
final class ResultTableHeader {

    private static final int RESIZE_HANDLE_PX = 4;

    private ResultTableHeader() {
    }

    static JTableHeader install(JTable table, ColumnSorter sorter, SelectionManager selection,
            ColumnHeaderRenderer.MetadataSource metadataSource) {
        JTableHeader header = new JTableHeader(table.getColumnModel());
        header.setReorderingAllowed(false);
        header.setDefaultRenderer(new ColumnHeaderRenderer(sorter));

        header.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                // Sem isto, um clique no cabecalho (selecionar coluna, ordenar,
                // autofit na divisoria) NUNCA move o foco de teclado para a
                // JTable — o header e um Component separado, e o foco fica
                // onde estava antes (outra aba, o editor SQL, etc). Como
                // Ctrl+C/Ctrl+A/Esc/setas sao KeyBindings WHEN_FOCUSED
                // instalados NA TABELA (ver SelectionManager#installKeyBindings),
                // eles simplesmente nao disparavam depois de selecionar uma
                // coluna pelo cabecalho — o mesmo padrao ja usado no clique da
                // coluna de numeracao (ver RowNumberGutter#build).
                table.requestFocusInWindow();
                if (e.getClickCount() == 2) {
                    int dividerColumn = columnAtDivider(header, e.getPoint());
                    if (dividerColumn >= 0) {
                        ColumnAutoFit.packColumn(table, dividerColumn);
                        return;
                    }
                }
                if (e.getClickCount() != 1) {
                    return;
                }
                int viewColumn = header.columnAtPoint(e.getPoint());
                if (viewColumn < 0) {
                    return;
                }
                SortOrder arrow = arrowAtPoint(header, viewColumn, e.getPoint());
                if (arrow != null) {
                    int modelColumn = table.getColumnModel().getColumn(viewColumn).getModelIndex();
                    sorter.setDirection(modelColumn, arrow, e.isControlDown());
                    header.repaint();
                } else {
                    selection.selectColumn(viewColumn, e.isControlDown(), e.isShiftDown());
                }
            }
        });
        header.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int viewColumn = header.columnAtPoint(e.getPoint());
                boolean overDivider = columnAtDivider(header, e.getPoint()) >= 0;
                boolean overSortZone = !overDivider && viewColumn >= 0
                        && arrowAtPoint(header, viewColumn, e.getPoint()) != null;
                header.setCursor(Cursor.getPredefinedCursor(overDivider
                        ? Cursor.E_RESIZE_CURSOR
                        : (overSortZone ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR)));
            }
        });

        table.setTableHeader(header);
        ColumnMetadataPopup.install(table, header, metadataSource);
        return header;
    }

    /**
     * Qual das duas setinhas (ver {@link ColumnHeaderRenderer}) o ponto esta
     * em cima, ou {@code null} se estiver fora da zona de ordenacao (fixa,
     * sempre a direita). A zona inteira e dividida ao MEIO na vertical:
     * metade de cima -&gt; {@code ASCENDING} (setinha ▲), metade de baixo -&gt;
     * {@code DESCENDING} (setinha ▼) — mesma divisao usada pelo
     * {@code GridLayout(2, 1)} do renderer, entao o clique sempre acerta a
     * setinha que o usuario esta vendo.
     */
    private static SortOrder arrowAtPoint(JTableHeader header, int viewColumn, Point p) {
        Rectangle rect = header.getHeaderRect(viewColumn);
        if (p.x < rect.x + rect.width - ColumnHeaderRenderer.SORT_ZONE_WIDTH - 6) {
            return null;
        }
        return (p.y < rect.y + rect.height / 2.0) ? SortOrder.ASCENDING : SortOrder.DESCENDING;
    }

    /** Indice (view) da coluna cuja divisoria DIREITA esta proxima do ponto, ou -1. */
    private static int columnAtDivider(JTableHeader header, Point p) {
        int col = header.columnAtPoint(p);
        if (col < 0) {
            return -1;
        }
        Rectangle rect = header.getHeaderRect(col);
        if (Math.abs(p.x - (rect.x + rect.width)) <= RESIZE_HANDLE_PX) {
            return col;
        }
        if (Math.abs(p.x - rect.x) <= RESIZE_HANDLE_PX && col > 0) {
            return col - 1; // divisoria a esquerda pertence a coluna anterior
        }
        return -1;
    }
}
