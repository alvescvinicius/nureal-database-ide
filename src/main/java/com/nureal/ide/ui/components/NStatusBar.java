package com.nureal.ide.ui.components;

import java.awt.BorderLayout;
import java.awt.FlowLayout;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JPanel;

/**
 * Barra de status/rodape do Nureal Design System — dois slots (esquerda/
 * direita), sempre a MESMA estrutura em toda tela que precisar de um
 * rodape (janela principal, futuramente o editor SQL — ver especificacao
 * do NDS, secao "SQL Editor"/"Footer"). Extraido de
 * {@code MainWindow#buildFooter} (a mesma estrutura ja existia la, so
 * nao era reutilizavel por outra tela).
 */
public final class NStatusBar extends JPanel {

    private static final long serialVersionUID = 1L;

    private final JPanel left = flowPanel(FlowLayout.LEFT);
    private final JPanel right = flowPanel(FlowLayout.RIGHT);

    public NStatusBar() {
        super(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(NTheme.SPACE_XS + 1, NTheme.SPACE_MD, NTheme.SPACE_XS + 1, NTheme.SPACE_MD));
        add(left, BorderLayout.WEST);
        add(right, BorderLayout.EAST);
    }

    public NStatusBar addLeft(JComponent c) {
        left.add(c);
        return this;
    }

    public NStatusBar addRight(JComponent c) {
        right.add(c);
        return this;
    }

    private static JPanel flowPanel(int align) {
        JPanel panel = new JPanel(new FlowLayout(align, NTheme.SPACE_MD, 0));
        panel.setOpaque(false);
        return panel;
    }
}
