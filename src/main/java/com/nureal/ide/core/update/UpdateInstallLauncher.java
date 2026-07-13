package com.nureal.ide.core.update;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Dispara o instalador ja baixado (ver {@link UpdateDownloader}) como um
 * PROCESSO SEPARADO e independente do processo atual da IDE — o instalador
 * so consegue substituir os arquivos em uso (jar, JRE empacotado pelo
 * jpackage) DEPOIS que este processo Java encerrar (Windows trava arquivos
 * abertos por um processo em execucao), entao quem chama {@link #launch}
 * SEMPRE precisa encerrar a aplicacao logo em seguida (ver
 * {@code UpdateInstallDialog}, que agenda {@code System.exit} apos avisar o
 * usuario).
 *
 * So sabe lidar com o instalador Windows (.msi, via {@code msiexec}) — hoje
 * o UNICO artefato que o workflow de release publica (ver
 * .github/workflows/release.yml, {@code jpackage --type msi}). Em qualquer
 * outro sistema operacional (ou se o asset baixado nao for um .msi por
 * algum motivo), {@link #canLaunch} devolve {@code false} e quem chama deve
 * cair no plano B (abrir a pagina do release no navegador — ver
 * {@code UpdateInstallDialog}/{@code ReleaseNotesDialog#openUrl}).
 */
public final class UpdateInstallLauncher {

    private UpdateInstallLauncher() {
    }

    /**
     * {@code true} quando este sistema operacional tem um mecanismo de
     * auto-instalacao implementado (hoje: so Windows, via {@code msiexec}) —
     * checagem de ALTO NIVEL usada pela UI (ver {@code MainWindow}) para
     * decidir, ANTES de baixar qualquer coisa, se oferece "Baixar e instalar"
     * ou cai direto no plano B (abrir a pagina do release no navegador).
     */
    public static boolean supportsAutoInstall() {
        return isWindows();
    }

    /** {@code true} quando este SO/arquivo tem um jeito conhecido de auto-instalar (hoje: Windows + .msi). */
    public static boolean canLaunch(Path installerFile) {
        return isWindows() && installerFile.toString().toLowerCase(Locale.ROOT).endsWith(".msi");
    }

    private static boolean isWindows() {
        String os = System.getProperty("os.name", "");
        return os.toLowerCase(Locale.ROOT).contains("win");
    }

    /**
     * Abre o instalador grafico do Windows ({@code msiexec /i <arquivo>}) —
     * SEM a flag {@code /quiet}: o usuario ve e confirma cada passo (pasta de
     * instalacao, UAC), igual a rodar o .msi manualmente a partir do
     * Explorer. Um instalador totalmente silencioso poderia surpreender o
     * usuario mudando arquivos do sistema sem nenhuma confirmacao visivel —
     * fora de escopo para esta primeira versao do auto-update.
     */
    public static void launch(Path installerFile) throws IOException {
        new ProcessBuilder("msiexec", "/i", installerFile.toAbsolutePath().toString())
                .inheritIO()
                .start();
    }
}
