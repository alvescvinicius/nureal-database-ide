package com.nureal.ide.ui;
import com.nureal.ide.compartilhado.designsystem.Buttons;
import com.nureal.ide.compartilhado.designsystem.Spacing;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Window;
import java.net.URI;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

import com.nureal.ide.modulos.atualizacao.dominio.entidades.GithubRelease;

/**
 * Dialogo somente-leitura com as notas de um release do GitHub — aberto pelo
 * botao "Ver notas" da {@link UpdateBanner} (ou pelo resultado da checagem
 * manual de atualizacao, ver {@code MainWindow#checkForUpdates}).
 *
 * Mostra o corpo do release (Markdown CRU, sem renderizar — implementar um
 * renderizador Markdown so para isto seria desproporcional ao beneficio;
 * o texto continua legivel mesmo com a sintaxe `#`/`-`/`**` visivel) mais um
 * botao "Abrir no GitHub" para quem quiser ver a versao formatada.
 */
final class ReleaseNotesDialog {

    private ReleaseNotesDialog() {
    }

    static void open(Component parent, GithubRelease release) {
        Window owner = (DialogUtil.owner(parent) instanceof Window w) ? w : null;
        JDialog dialog = new JDialog(owner, release.name(), java.awt.Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setLayout(new BorderLayout());

        JLabel title = new JLabel(release.name());
        title.setFont(title.getFont().deriveFont(Font.BOLD, 15f));
        title.setBorder(BorderFactory.createEmptyBorder(Spacing.LG, Spacing.LG, Spacing.SM, Spacing.LG));
        dialog.add(title, BorderLayout.NORTH);

        JTextArea body = new JTextArea(release.body() == null || release.body().isBlank()
                ? "(este release nao tem notas escritas)" : release.body());
        body.setEditable(false);
        body.setLineWrap(true);
        body.setWrapStyleWord(true);
        body.setBorder(BorderFactory.createEmptyBorder(Spacing.SM, Spacing.LG, Spacing.SM, Spacing.LG));
        JScrollPane scroll = new JScrollPane(body);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setPreferredSize(new Dimension(520, 360));
        dialog.add(scroll, BorderLayout.CENTER);

        JButton openInBrowser = new JButton("Abrir no GitHub");
        Buttons.styleSecondary(openInBrowser);
        openInBrowser.addActionListener(e -> openUrl(dialog, release.htmlUrl()));

        JButton close = new JButton("Fechar");
        Buttons.stylePrimary(close);
        close.addActionListener(e -> dialog.dispose());

        JPanel footer = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, Spacing.SM, Spacing.SM));
        footer.setBorder(BorderFactory.createEmptyBorder(0, Spacing.LG, Spacing.LG, Spacing.LG));
        footer.add(openInBrowser);
        footer.add(close);
        dialog.add(footer, BorderLayout.SOUTH);

        dialog.pack();
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);
    }

    private static void openUrl(Component parent, String url) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(url));
            } else {
                JOptionPane.showMessageDialog(DialogUtil.owner(parent),
                        "Nao foi possivel abrir o navegador automaticamente. Link: " + url,
                        "Abrir no GitHub", JOptionPane.INFORMATION_MESSAGE);
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(DialogUtil.owner(parent),
                    "Nao foi possivel abrir o navegador: " + ex.getMessage(),
                    "Abrir no GitHub", JOptionPane.WARNING_MESSAGE);
        }
    }
}
