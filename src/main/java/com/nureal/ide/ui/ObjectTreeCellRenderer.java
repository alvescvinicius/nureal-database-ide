package com.nureal.ide.ui;

import javax.swing.JTree;
import javax.swing.tree.DefaultTreeCellRenderer;
import java.awt.Component;

/**
 * Arvore de objetos SEM icones de tipo (schema/tabela/view/funcao/procedure/
 * trigger) e sem os icones padrao de pasta/arquivo do Swing — pedido
 * explicito do usuario: a lista estava poluida visualmente. O UNICO
 * indicador visual que resta e o triangulo de expandir/recolher, que nem
 * passa por aqui: e desenhado pelo proprio {@code JTree}/L&amp;F (FlatLaf),
 * independente do que este renderer faz com o icone da celula.
 */
final class ObjectTreeCellRenderer extends DefaultTreeCellRenderer {

    private static final long serialVersionUID = 1L;

    ObjectTreeCellRenderer() {
        // openIcon/closedIcon/leafIcon sao os icones-padrao que o
        // DefaultTreeCellRenderer usaria (pasta aberta/fechada, arquivo)
        // quando getTreeCellRendererComponent nao definir um icone proprio —
        // nulos aqui para nao sobrarem mesmo nesses casos.
        setLeafIcon(null);
        setOpenIcon(null);
        setClosedIcon(null);
    }

    @Override
    public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected,
            boolean expanded, boolean leaf, int row, boolean hasFocus) {
        super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
        setIcon(null);
        return this;
    }
}
