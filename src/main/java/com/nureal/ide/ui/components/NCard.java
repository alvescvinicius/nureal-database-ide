package com.nureal.ide.ui.components;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

import com.nureal.ide.ui.Typography;

/**
 * Superficie base do Nureal Design System (NDS): painel com cantos
 * arredondados, fundo elevado (ver {@link NTheme#surfaceBackground()}) e um
 * cabecalho opcional tintado na cor semantica ({@link NAccent}). Substitui
 * {@code JPanel + Border} como forma de agrupar conteudo — Swing nao pinta
 * cantos arredondados sozinho, por isso a pintura customizada em
 * {@link #paintComponent}.
 * <p>
 * Extraido de {@code MessageRenderer} (era um {@code RoundedPanel} privado,
 * so do chat) — primeiro componente reutilizavel do NDS. {@code MessageCard},
 * {@code SqlCard}, {@code WarningCard} etc. (ver especificacao do NDS) devem
 * ser variacoes de USO deste componente (accent + conteudo), nao
 * reimplementacoes da pintura arredondada.
 * <p>
 * Uso: {@code new NCard(NAccent.INFO, "Dica")}, depois {@code add(conteudo)}
 * normalmente (e um {@code JPanel} com {@code BoxLayout.Y_AXIS}).
 */
public class NCard extends JPanel {

    private static final long serialVersionUID = 1L;

    private final Color fill = NTheme.surfaceBackground();

    /** Card sem cabecalho (ex.: paragrafo de texto simples) — so a superficie elevada, sem tinta de tipo. */
    public NCard() {
        this(NAccent.NEUTRAL, null);
    }

    /** {@code header} nulo/vazio pula o cabecalho (so a superficie), mesmo com um {@code accent} definido. */
    public NCard(NAccent accent, String header) {
        setOpaque(false);
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createEmptyBorder(NTheme.SPACE_SM, NTheme.SPACE_SM, NTheme.SPACE_SM, NTheme.SPACE_SM));
        setAlignmentX(LEFT_ALIGNMENT);
        if (header != null && !header.isBlank()) {
            JLabel headerLabel = new JLabel(header);
            // Typography.primary() da o PESO (Bold) — a cor semantica do card
            // (info/aviso/erro/sql/tool) sobrescreve a cor generica de cabecalho
            // que Typography aplicaria, sao dois eixos independentes (peso
            // hierarquico vs. tinta semantica), ver javadoc de Typography.
            Typography.primary(headerLabel);
            headerLabel.setFont(headerLabel.getFont().deriveFont(11f));
            headerLabel.setForeground(NTheme.accentColor(accent));
            headerLabel.setAlignmentX(LEFT_ALIGNMENT);
            add(headerLabel);
            add(Box.createVerticalStrut(NTheme.SPACE_XS));
        }
    }

    /** Acrescenta o conteudo do corpo do card, ja alinhado a esquerda (conveniencia sobre {@link #add(java.awt.Component)}). */
    public void addContent(JComponent content) {
        content.setAlignmentX(LEFT_ALIGNMENT);
        add(content);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(fill);
        g2.fillRoundRect(0, 0, getWidth(), getHeight(), NTheme.CARD_ARC, NTheme.CARD_ARC);
        g2.dispose();
        super.paintComponent(g);
    }
}
