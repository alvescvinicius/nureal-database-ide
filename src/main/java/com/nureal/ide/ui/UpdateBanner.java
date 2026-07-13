package com.nureal.ide.ui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.MatteBorder;

import com.nureal.ide.core.update.GithubRelease;

/**
 * Faixa discreta e dispensavel no topo da janela principal, mostrada quando o
 * checador de atualizacao (ver {@code MainWindow#checkForUpdates}) encontra
 * uma versao mais nova no GitHub — pedido explicito do usuario: nao
 * interromper o uso com um dialogo modal no startup, so avisar de forma
 * discreta e deixar o usuario decidir quando agir.
 *
 * Comeca INVISIVEL (ver {@link #hideBanner()}, chamado no construtor) — so
 * aparece quando {@link #showUpdate} e chamado com um release de verdade.
 * Nao tem estado proprio de "versao ignorada"/"checagem automatica ligada":
 * isso e responsabilidade de {@code UpdatePreferences}, lido/escrito por
 * quem instancia esta classe (MainWindow) atraves dos callbacks do
 * construtor.
 */
final class UpdateBanner extends JPanel {

    private static final long serialVersionUID = 1L;

    private final JLabel iconLabel = new JLabel();
    private final JLabel messageLabel = new JLabel();
    private final JButton installButton = new JButton("Baixar e instalar");
    private final JButton notesButton = new JButton("Ver notas");
    private final JButton skipButton = new JButton("Ignorar esta versao");
    private final JButton closeButton;

    UpdateBanner(Runnable onInstall, Runnable onViewNotes, Runnable onSkip, Runnable onDismiss) {
        super(new BorderLayout());
        setOpaque(true);

        iconLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, Spacing.SM));
        messageLabel.setFont(messageLabel.getFont().deriveFont(Font.PLAIN, 12.5f));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        left.setOpaque(false);
        left.add(iconLabel);
        left.add(messageLabel);
        add(left, BorderLayout.WEST);

        Buttons.stylePrimary(installButton);
        installButton.addActionListener(e -> onInstall.run());

        Buttons.styleSecondary(notesButton);
        notesButton.addActionListener(e -> onViewNotes.run());

        Buttons.styleSecondary(skipButton);
        skipButton.addActionListener(e -> onSkip.run());

        closeButton = Buttons.iconButton(IconType.CLOSE, 14, () -> GridTheme.MUTED_TEXT);
        closeButton.setToolTipText("Fechar (continua avisando na proxima checagem)");
        closeButton.addActionListener(e -> onDismiss.run());

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, Spacing.SM, 0));
        right.setOpaque(false);
        right.add(notesButton);
        right.add(skipButton);
        right.add(installButton);
        right.add(closeButton);
        add(right, BorderLayout.EAST);

        refreshTheme();
        hideBanner();
    }

    /** Preenche o texto/icone com os dados de {@code release} e exibe a faixa. */
    void showUpdate(GithubRelease release) {
        iconLabel.setIcon(Icons.get(IconType.INFO, 16, MainWindow.ACCENT));
        messageLabel.setText("Nova versao disponivel: " + release.name()
                + " (voce esta na " + com.nureal.ide.core.update.AppVersion.current() + ")");
        setVisible(true);
        revalidate();
        repaint();
    }

    /** Esconde a faixa (fechamento manual ou apos o usuario agir) sem apagar preferencias — ver javadoc da classe. */
    void hideBanner() {
        setVisible(false);
        revalidate();
    }

    /**
     * Reaplica cor de fundo/borda/texto — chamado no construtor e de novo por
     * {@code MainWindow#toggleTheme} (mesmo padrao ja usado para
     * {@code connStatusLabel}/grade/editor: um {@link JPanel} com
     * {@code setBackground} explicito NAO e alcancado por
     * {@code FlatLaf.updateUI()}, que so redesenha componentes com o fundo
     * PADRAO do Look and Feel).
     */
    void refreshTheme() {
        setBackground(GridTheme.HOVER_BACKGROUND);
        messageLabel.setForeground(GridTheme.COLOR_DEFAULT_TEXT);
        // Duas bordas com cores diferentes (MatteBorder so aceita UMA cor por
        // instancia): faixa verde da marca a esquerda (identifica a faixa
        // como uma "oportunidade", mesma linguagem do botao Executar) + linha
        // fina de separacao embaixo (mesma cor de GRID_LINE usada em toda
        // divisoria discreta do app).
        Border bottomLine = new MatteBorder(0, 0, 1, 0, GridTheme.GRID_LINE);
        Border leftStripe = new MatteBorder(0, 3, 0, 0, MainWindow.ACCENT);
        Border edges = new CompoundBorder(bottomLine, leftStripe);
        Border padding = BorderFactory.createEmptyBorder(Spacing.SM, Spacing.MD, Spacing.SM, Spacing.MD);
        setBorder(new CompoundBorder(edges, padding));
    }
}
