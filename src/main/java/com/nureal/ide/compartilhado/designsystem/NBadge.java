package com.nureal.ide.compartilhado.designsystem;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.SwingConstants;

/**
 * Selo compacto (pilula) do Nureal Design System — contagem/status curto ao
 * lado de um objeto (ex.: numero de linhas, "Ativo", tipo de tabela). Fundo
 * translucido na cor semantica (ver {@link NTheme#accentBackground}), texto
 * solido na mesma cor — nunca uma cor fixa por fora do {@link NAccent}.
 */
public final class NBadge extends JLabel {

    private static final long serialVersionUID = 1L;

    private final Color fill;

    public NBadge(String text) {
        this(text, NAccent.NEUTRAL);
    }

    public NBadge(String text, NAccent accent) {
        super(text);
        this.fill = NTheme.accentBackground(accent);
        setOpaque(false);
        setHorizontalAlignment(SwingConstants.CENTER);
        setFont(getFont().deriveFont(Font.BOLD, 10f));
        setForeground(NTheme.accentColor(accent));
        setBorder(BorderFactory.createEmptyBorder(2, NTheme.SPACE_SM, 2, NTheme.SPACE_SM));
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(fill);
        g2.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
        g2.dispose();
        super.paintComponent(g);
    }
}
