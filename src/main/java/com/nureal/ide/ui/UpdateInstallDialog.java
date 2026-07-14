package com.nureal.ide.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import java.net.URI;
import java.nio.file.Path;
import java.util.Locale;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingWorker;
import javax.swing.Timer;

import com.nureal.ide.core.update.GithubRelease;
import com.nureal.ide.core.update.UpdateDownloader;
import com.nureal.ide.core.update.UpdateInstallLauncher;

/**
 * Baixa o instalador .exe de {@code release} com uma barra de progresso e,
 * ao terminar, abre o instalador grafico do Windows e fecha a IDE (ver
 * {@link UpdateInstallLauncher}, javadoc de classe: o instalador so consegue
 * substituir os arquivos em uso depois que este processo encerrar).
 *
 * Se o release nao tiver um asset .exe, ou o sistema operacional atual nao
 * for Windows (ver {@link UpdateInstallLauncher#supportsAutoInstall()}), cai
 * no plano B automaticamente: abre a pagina do release no navegador padrao
 * (sem tentar um download automatico que nao teria como instalar sozinho) e
 * avisa o usuario o motivo.
 *
 * Mesmo idioma ja usado por {@code BackupRestoreDialog}/{@code CsvImportDialog}:
 * {@link SwingWorker} com {@code doInBackground} bloqueante numa thread de
 * fundo, {@code process}/{@code done} de volta na EDT.
 */
final class UpdateInstallDialog {

    private UpdateInstallDialog() {
    }

    static void open(Component parent, GithubRelease release) {
        GithubRelease.Asset asset = release.findExeAsset();
        if (asset == null || !UpdateInstallLauncher.supportsAutoInstall()) {
            String reason = asset == null
                    ? "Este release nao tem um instalador (.exe) anexado."
                    : "A instalacao automatica so esta disponivel no Windows.";
            JOptionPane.showMessageDialog(DialogUtil.owner(parent),
                    reason + " Vou abrir a pagina do release para voce baixar manualmente.",
                    "Baixar e instalar", JOptionPane.INFORMATION_MESSAGE);
            openInBrowser(parent, release.htmlUrl());
            return;
        }
        new Session(parent, asset).show();
    }

    private static void openInBrowser(Component parent, String url) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(url));
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(DialogUtil.owner(parent),
                    "Nao foi possivel abrir o navegador. Link: " + url,
                    "Baixar e instalar", JOptionPane.WARNING_MESSAGE);
        }
    }

    private static final class Session {

        private final Component parentComponent;
        private final GithubRelease.Asset asset;
        private final JDialog dialog;
        private final JProgressBar progress = new JProgressBar(0, 100);
        private final JLabel statusLabel = new JLabel("Preparando download...");
        private final JButton cancelButton = new JButton("Cancelar");
        private SwingWorker<Path, Long> worker;

        Session(Component parent, GithubRelease.Asset asset) {
            this.parentComponent = parent;
            this.asset = asset;
            Window owner = (DialogUtil.owner(parent) instanceof Window w) ? w : null;
            dialog = new JDialog(owner, "Baixar e instalar atualizacao", Dialog.ModalityType.APPLICATION_MODAL);
            dialog.setLayout(new BorderLayout());
            dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);

            JLabel title = new JLabel("Baixando " + asset.name());
            title.setBorder(BorderFactory.createEmptyBorder(Spacing.LG, Spacing.LG, Spacing.SM, Spacing.LG));
            dialog.add(title, BorderLayout.NORTH);

            JPanel center = new JPanel(new BorderLayout(0, Spacing.SM));
            center.setBorder(BorderFactory.createEmptyBorder(0, Spacing.LG, Spacing.LG, Spacing.LG));
            progress.setIndeterminate(asset.sizeBytes() <= 0);
            center.add(progress, BorderLayout.NORTH);
            center.add(statusLabel, BorderLayout.SOUTH);
            dialog.add(center, BorderLayout.CENTER);

            Buttons.styleSecondary(cancelButton);
            cancelButton.addActionListener(e -> cancel());
            JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, Spacing.SM, Spacing.SM));
            footer.setBorder(BorderFactory.createEmptyBorder(0, Spacing.LG, Spacing.LG, Spacing.LG));
            footer.add(cancelButton);
            dialog.add(footer, BorderLayout.SOUTH);

            dialog.setPreferredSize(new Dimension(420, 170));
            dialog.pack();
            dialog.setLocationRelativeTo(owner);
        }

        void show() {
            startDownload();
            dialog.setVisible(true);
        }

        private void cancel() {
            if (worker != null) {
                worker.cancel(true);
            }
            dialog.dispose();
        }

        private void startDownload() {
            Path dest = Path.of(System.getProperty("java.io.tmpdir"), "nureal-updates", safeFileName(asset.name()));
            worker = new SwingWorker<>() {
                @Override
                protected Path doInBackground() throws Exception {
                    UpdateDownloader.download(asset.downloadUrl(), dest, bytes -> publish(bytes));
                    return dest;
                }

                @Override
                protected void process(java.util.List<Long> chunks) {
                    long lastBytes = chunks.get(chunks.size() - 1);
                    updateProgress(lastBytes, asset.sizeBytes());
                }

                @Override
                protected void done() {
                    try {
                        Path downloaded = get();
                        onDownloadFinished(downloaded);
                    } catch (java.util.concurrent.CancellationException ex) {
                        // usuario cancelou — ja fechou o dialogo em cancel(), nada a fazer.
                    } catch (Exception ex) {
                        onDownloadFailed(ex);
                    }
                }
            };
            worker.execute();
        }

        private void updateProgress(long downloaded, long total) {
            if (total > 0) {
                int pct = (int) Math.min(100, (downloaded * 100) / total);
                progress.setIndeterminate(false);
                progress.setValue(pct);
                statusLabel.setText(humanSize(downloaded) + " de " + humanSize(total) + " (" + pct + "%)");
            } else {
                statusLabel.setText(humanSize(downloaded) + " baixados...");
            }
        }

        private void onDownloadFinished(Path installerFile) {
            dialog.dispose();
            if (!UpdateInstallLauncher.canLaunch(installerFile)) {
                JOptionPane.showMessageDialog(DialogUtil.owner(parentComponent),
                        "Download concluido em: " + installerFile,
                        "Baixar e instalar", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            try {
                UpdateInstallLauncher.launch(installerFile);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(DialogUtil.owner(parentComponent),
                        "O download terminou, mas nao consegui abrir o instalador automaticamente: "
                                + ex.getMessage() + "\nArquivo salvo em: " + installerFile,
                        "Baixar e instalar", JOptionPane.WARNING_MESSAGE);
                return;
            }
            JOptionPane.showMessageDialog(DialogUtil.owner(parentComponent),
                    "O instalador foi aberto em uma janela separada.\n"
                            + "O Nureal Database IDE vai fechar agora para liberar os arquivos em uso.",
                    "Baixar e instalar", JOptionPane.INFORMATION_MESSAGE);
            // Pequeno atraso so pra garantir que o JOptionPane ja fechou e o
            // instalador ja teve tempo de nascer como processo independente
            // antes deste processo encerrar.
            Timer exitTimer = new Timer(600, e -> System.exit(0));
            exitTimer.setRepeats(false);
            exitTimer.start();
        }

        private void onDownloadFailed(Exception ex) {
            dialog.dispose();
            JOptionPane.showMessageDialog(DialogUtil.owner(parentComponent),
                    "Nao foi possivel baixar a atualizacao: " + ex.getMessage(),
                    "Baixar e instalar", JOptionPane.ERROR_MESSAGE);
        }

        private static String safeFileName(String name) {
            return (name == null || name.isBlank()) ? "update.exe" : name.replaceAll("[\\\\/:*?\"<>|]", "_");
        }

        private static String humanSize(long bytes) {
            if (bytes < 1024) {
                return bytes + " B";
            }
            if (bytes < 1024 * 1024) {
                return String.format(Locale.ROOT, "%.1f KB", bytes / 1024.0);
            }
            return String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0));
        }
    }
}
