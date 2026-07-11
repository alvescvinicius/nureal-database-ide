package com.nureal.ide.ui;

import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;

import javax.swing.JList;
import javax.swing.JTree;

/**
 * Mesmo padrao de "hover" (destaque suave da linha sob o mouse, sem alterar a
 * selecao) que a grade de resultados ja tem — ver {@link SelectionManager}
 * (hoverRow) — agora tambem para a lista de Conexoes ({@link JList}) e a
 * arvore de Objetos ({@link JTree}): antes NENHUMA das duas tinha esse
 * estado, so normal/selecionado (faltava um dos 5 estados pedidos na revisao
 * visual: normal, hover, selecionado, expandido, desabilitado).
 * <p>
 * Guarda o indice da linha sob o mouse numa client property do proprio
 * componente (mesma tecnica do {@code SelectionManager}) — os renderers
 * ({@link ConnectionsPanel}'s {@code ConnectionRenderer}, {@link ObjectTreeCellRenderer})
 * consultam {@link #hoverRow} a cada pintura e pintam
 * {@link GridTheme#HOVER_BACKGROUND} quando a linha bate, contanto que ela
 * NAO esteja selecionada (selecao sempre tem prioridade visual sobre hover).
 */
final class TreeHoverTracker {

    private static final String HOVER_ROW_PROPERTY = "nureal.hoverRow";

    private TreeHoverTracker() {
    }

    static void installOnList(JList<?> list) {
        list.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                setHoverRow(list, list.locationToIndex(e.getPoint()));
            }
        });
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseExited(MouseEvent e) {
                setHoverRow(list, -1);
            }
        });
    }

    static void installOnTree(JTree tree) {
        tree.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                setHoverRow(tree, tree.getRowForLocation(e.getX(), e.getY()));
            }
        });
        tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseExited(MouseEvent e) {
                setHoverRow(tree, -1);
            }
        });
    }

    private static void setHoverRow(JList<?> list, int row) {
        int previous = hoverRow(list);
        if (previous == row) {
            return;
        }
        list.putClientProperty(HOVER_ROW_PROPERTY, row);
        repaintListRow(list, previous);
        repaintListRow(list, row);
    }

    private static void setHoverRow(JTree tree, int row) {
        int previous = hoverRow(tree);
        if (previous == row) {
            return;
        }
        tree.putClientProperty(HOVER_ROW_PROPERTY, row);
        repaintTreeRow(tree, previous);
        repaintTreeRow(tree, row);
    }

    private static void repaintListRow(JList<?> list, int row) {
        if (row < 0 || row >= list.getModel().getSize()) {
            return;
        }
        Rectangle rect = list.getCellBounds(row, row);
        if (rect != null) {
            list.repaint(rect);
        }
    }

    private static void repaintTreeRow(JTree tree, int row) {
        if (row < 0 || row >= tree.getRowCount()) {
            return;
        }
        Rectangle rect = tree.getRowBounds(row);
        if (rect != null) {
            rect.width = tree.getWidth();
            tree.repaint(rect);
        }
    }

    /** Linha (indice) sob o mouse no momento, ou -1. Usado pelos renderers para o hover suave. */
    static int hoverRow(JList<?> list) {
        Object value = list.getClientProperty(HOVER_ROW_PROPERTY);
        return (value instanceof Integer i) ? i : -1;
    }

    static int hoverRow(JTree tree) {
        Object value = tree.getClientProperty(HOVER_ROW_PROPERTY);
        return (value instanceof Integer i) ? i : -1;
    }
}
