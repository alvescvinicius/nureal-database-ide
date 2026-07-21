package com.nureal.ide.ui.components;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * Padrao unico de barra de acoes do Nureal Design System (NDS): Titulo →
 * Acoes primarias → Acoes secundarias, sempre nessa ordem (ver especificacao
 * do NDS) — nenhuma tela deve montar sua propria combinacao de
 * {@code JPanel + BorderLayout + FlowLayout} pra isso.
 * <p>
 * Sem linha divisoria embaixo (so espacamento) — "as divisorias deixam de
 * ser linhas, passam a ser espaco", principio do NDS.
 * <p>
 * Uso: {@code new NToolbar().setTitle("Chat com IA").addSecondaryAction(gear)}.
 */
public final class NToolbar extends JPanel {

    private static final long serialVersionUID = 1L;

    private final JPanel primaryActions = actionGroup();
    private final JPanel secondaryActions = actionGroup();
    private JLabel titleLabel;

    public NToolbar() {
        super(new BorderLayout(NTheme.SPACE_SM, 0));
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(NTheme.SPACE_XS, NTheme.SPACE_SM, NTheme.SPACE_XS, NTheme.SPACE_SM));

        JPanel actions = new JPanel(new BorderLayout());
        actions.setOpaque(false);
        actions.add(primaryActions, BorderLayout.WEST);
        actions.add(secondaryActions, BorderLayout.EAST);
        add(actions, BorderLayout.CENTER);
    }

    public NToolbar setTitle(String title) {
        if (titleLabel == null) {
            titleLabel = new JLabel();
            titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 13f));
            add(titleLabel, BorderLayout.WEST);
        }
        titleLabel.setText(title);
        return this;
    }

    /** Acao principal da tela (ex.: Executar, Enviar) — sempre antes das secundarias. */
    public NToolbar addPrimaryAction(JComponent action) {
        primaryActions.add(action);
        return this;
    }

    /** Acao de apoio (ex.: configuracoes, ajuda) — sempre depois das primarias, mais a direita. */
    public NToolbar addSecondaryAction(JComponent action) {
        secondaryActions.add(action);
        return this;
    }

    private static JPanel actionGroup() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, NTheme.SPACE_XS, 0));
        panel.setOpaque(false);
        return panel;
    }
}
