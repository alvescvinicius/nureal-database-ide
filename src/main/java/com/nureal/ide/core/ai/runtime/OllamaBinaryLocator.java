package com.nureal.ide.core.ai.runtime;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Optional;

/**
 * Resolve o caminho do binario do Ollama embutido no instalador (dentro do
 * app-image gerado pelo jpackage), quando existir. Layout esperado dentro do
 * {@code --input} do jpackage (ver {@code .github/workflows/release.yml}):
 *
 * <pre>
 *   Windows: &lt;raiz do app-image&gt;/app/ollama-bin/ollama.exe
 *   macOS:   &lt;App&gt;.app/Contents/app/ollama-bin/ollama
 *   Linux:   &lt;raiz do app-image&gt;/app/ollama-bin/ollama
 * </pre>
 *
 * {@code jpackage.app-path} e uma propriedade de sistema que o LAUNCHER
 * NATIVO gerado pelo jpackage define sozinho em runtime (aponta pro proprio
 * executavel principal) — nao existe rodando via {@code mvn exec:java} (dev),
 * caso em que este locator sempre devolve {@link Optional#empty()} e quem
 * chama cai pro fallback de {@code PATH} (mesmo padrao do
 * {@code MySqlDumpRunner} pro mysqldump/mysql).
 */
public final class OllamaBinaryLocator {

    private OllamaBinaryLocator() {
    }

    public static Optional<Path> locate() {
        return locate(System.getProperty("jpackage.app-path"), System.getProperty("os.name", ""));
    }

    /** Pacote-visivel (nao private) para ser testavel sem depender de System properties reais. */
    static Optional<Path> locate(String appPath, String osName) {
        if (appPath == null || appPath.isBlank()) {
            return Optional.empty();
        }
        Path appDir = resolveAppDir(Paths.get(appPath), osName);
        if (appDir == null) {
            return Optional.empty();
        }
        Path binary = appDir.resolve("ollama-bin").resolve(binaryName(osName));
        return Files.isRegularFile(binary) ? Optional.of(binary) : Optional.empty();
    }

    private static Path resolveAppDir(Path launcher, String osName) {
        Path parent = launcher.getParent();
        if (parent == null) {
            return null;
        }
        if (isMac(osName)) {
            // launcher fica em .../Contents/MacOS/<AppName>; o --input do
            // jpackage foi copiado pra .../Contents/app/.
            Path contents = parent.getParent();
            return contents == null ? null : contents.resolve("app");
        }
        // Windows/Linux: launcher fica na raiz do app-image; --input foi
        // copiado pra <raiz>/app/.
        return parent.resolve("app");
    }

    private static String binaryName(String osName) {
        return isWindows(osName) ? "ollama.exe" : "ollama";
    }

    private static boolean isWindows(String osName) {
        return osName.toLowerCase(Locale.ROOT).contains("win");
    }

    private static boolean isMac(String osName) {
        return osName.toLowerCase(Locale.ROOT).contains("mac");
    }
}
