package com.nureal.ide.ui.components;

import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

import com.nureal.ide.ui.Buttons;
import com.nureal.ide.ui.IconType;

/**
 * Rail de navegacao do Nureal Design System — tira estreita e vertical de
 * icones, UM selecionado por vez (estilo "activity bar"), cada um com
 * icone + rotulo curto. Substitui um {@code JTabbedPane} quando a tela
 * precisa alternar entre VARIOS paineis que nao cabem visiveis ao mesmo
 * tempo (ver migracao de {@code MainWindow#buildLeftSide}).
 * <p>
 * O item selecionado ganha destaque por FUNDO (ver
 * {@link NTheme#surfaceBackground()}), nunca recolorindo o icone — evita
 * duplicar a logica de "icone preso na cor do tema anterior" que
 * {@link Buttons#bindThemedIcon} ja existe pra resolver (aqui so reage a
 * troca de TEMA, nunca a troca de SELECAO).
 */
public final class NIconRail extends JPanel {

    private static final long serialVersionUID = 1L;
    private static final int ICON_SIZE = 20;
    private static final int ITEM_WIDTH = 64;

    private final List<RailItem> items = new ArrayList<>();
    private Consumer<String> onSelect = id -> { };
    private String selectedId;

    public NIconRail() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createEmptyBorder(NTheme.SPACE_SM, 0, NTheme.SPACE_SM, 0));
    }

    public NIconRail addItem(String id, IconType icon, String label) {
        RailItem item = new RailItem(id, icon, label);
        items.add(item);
        add(item);
        if (selectedId == null) {
            selectedId = id;
            item.setSelected(true);
        }
        return this;
    }

    /** Chamado com o {@code id} do item toda vez que a selecao muda (por clique, nunca programaticamente sozinho). */
    public NIconRail onSelect(Consumer<String> listener) {
        this.onSelect = listener;
        return this;
    }

    public void select(String id) {
        if (id.equals(selectedId)) {
            return;
        }
        selectedId = id;
        for (RailItem item : items) {
            item.setSelected(item.id.equals(id));
        }
        onSelect.accept(id);
    }

    private final class RailItem extends JPanel {

        private static final long serialVersionUID = 1L;

        private final String id;

        RailItem(String id, IconType icon, String label) {
            this.id = id;
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setBorder(BorderFactory.createEmptyBorder(NTheme.SPACE_SM, NTheme.SPACE_XS, NTheme.SPACE_SM, NTheme.SPACE_XS));
            setAlignmentX(CENTER_ALIGNMENT);
            setMaximumSize(new Dimension(ITEM_WIDTH, Integer.MAX_VALUE));
            setPreferredSize(new Dimension(ITEM_WIDTH, getPreferredSize().height));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

            JLabel iconLabel = new JLabel();
            iconLabel.setHorizontalAlignment(SwingConstants.CENTER);
            iconLabel.setAlignmentX(CENTER_ALIGNMENT);
            Buttons.bindThemedIcon(iconLabel, icon, ICON_SIZE, NTheme::mutedColor);

            JLabel textLabel = new JLabel(label);
            textLabel.setHorizontalAlignment(SwingConstants.CENTER);
            textLabel.setAlignmentX(CENTER_ALIGNMENT);
            textLabel.setFont(textLabel.getFont().deriveFont(9f));
            // Mesmo motivo de Buttons#bindThemedIcon: GridTheme.MUTED_TEXT e um
            // campo MUTAVEL (troca em MainWindow#toggleTheme) -- sem reagir ao
            // evento "UI", o texto ficaria preso na cor do tema anterior.
            textLabel.setForeground(NTheme.mutedColor());
            textLabel.addPropertyChangeListener("UI", e -> textLabel.setForeground(NTheme.mutedColor()));

            add(iconLabel);
            add(textLabel);

            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    select(id);
                }
            });
        }

        void setSelected(boolean selected) {
            setOpaque(selected);
            setBackground(selected ? NTheme.surfaceBackground() : null);
            repaint();
        }
    }
}
