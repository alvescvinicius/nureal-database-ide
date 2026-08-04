package com.nureal.ide.compartilhado.designsystem;

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

    /**
     * Mesma familia de bug ja corrigida varias vezes nesta base (cor
     * "queimada" na construcao, nunca reaplicada ao trocar de tema — ver
     * {@code ConnectionStatusCard}/{@code SqlEditorsListRenderer} etc.):
     * como campo {@code final} calculado uma UNICA vez no construtor, um
     * card criado enquanto o app estava no tema ESCURO (ver {@code App#main})
     * continuava pintando o fundo escuro pra sempre, mesmo depois do usuario
     * trocar pro tema claro — a UNICA excecao no app inteiro que nao
     * acompanhava {@code MainWindow#toggleTheme()}, ficando uma caixa escura
     * "fora do tema" (ex.: o card de conexao ativa da sidebar) — relatado
     * pelo usuario com captura de tela. Recalculado em {@link #updateUI()}
     * (chamado por {@code FlatLaf.updateUI()} em toda a arvore de
     * componentes a cada troca de tema), nao mais {@code final}.
     */
    private Color fill = NTheme.surfaceBackground();

    @Override
    public void updateUI() {
        super.updateUI();
        fill = NTheme.surfaceBackground();
    }

    /** Card sem cabecalho (ex.: paragrafo de texto simples) — so a superficie elevada, sem tinta de tipo. */
    public NCard() {
        this(NAccent.NEUTRAL, null);
    }

    /** {@code header} nulo/vazio pula o cabecalho (so a superficie), mesmo com um {@code accent} definido. */
    public NCard(NAccent accent, String header) {
        this(accent, null, header);
    }

    /**
     * Igual a {@link #NCard(NAccent, String)}, com um icone vetorial do NDS
     * (ver {@link IconType}/{@link Icons}) antes do texto do cabecalho —
     * {@code icon} nulo omite o icone. Usado no lugar de prefixar o texto com
     * um emoji Unicode cru (❌/🔧/📊/⚠/ℹ️): a fonte padrao do Swing nao
     * garante ter esses glifos (apareciam como retangulo "tofu" em alguns
     * temas/SO — achado na revisao de UX do chat de IA), e um icone do
     * proprio design system sempre renderiza igual, em qualquer maquina.
     */
    public NCard(NAccent accent, IconType icon, String header) {
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
            if (icon != null) {
                JLabel iconLabel = new JLabel();
                Buttons.bindThemedIcon(iconLabel, icon, 13, () -> NTheme.accentColor(accent));
                JPanel headerRow = new JPanel();
                headerRow.setOpaque(false);
                headerRow.setLayout(new BoxLayout(headerRow, BoxLayout.X_AXIS));
                headerRow.setAlignmentX(LEFT_ALIGNMENT);
                headerRow.add(iconLabel);
                headerRow.add(Box.createHorizontalStrut(NTheme.SPACE_XS));
                headerRow.add(headerLabel);
                add(headerRow);
            } else {
                add(headerLabel);
            }
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
