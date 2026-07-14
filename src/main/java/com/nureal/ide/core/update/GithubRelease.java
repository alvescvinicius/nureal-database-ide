package com.nureal.ide.core.update;

import java.util.List;

/**
 * Um release do GitHub, ja reduzido aos campos que o checador de atualizacao
 * usa (ver {@link UpdateChecker#fetchLatestRelease()}) — o resto do JSON
 * devolvido pela API (autor, reacoes, draft/prerelease, etc.) e ignorado de
 * proposito, nao ha necessidade de modelar a resposta inteira.
 *
 * @param tagName   a tag do Git (ex.: {@code "v0.5.0"}) — e o que
 *                  {@link SemVer} compara contra {@link AppVersion#current()}.
 * @param name      titulo do release (ex.: {@code "Nureal Database IDE v0.5.0"}).
 * @param htmlUrl   pagina do release no GitHub (usada por "Ver notas" / "Abrir no GitHub").
 * @param body      notas do release, em Markdown cru (exibidas como texto simples, sem renderizar Markdown).
 * @param assets    arquivos anexados ao release (o instalador .exe, o fat jar, etc.).
 */
public record GithubRelease(String tagName, String name, String htmlUrl, String body, List<Asset> assets) {

    /**
     * Um arquivo anexado ao release.
     *
     * @param name           nome do arquivo (ex.: {@code "Nureal Database IDE-0.5.0.exe"}).
     * @param downloadUrl    URL direta de download (o {@code browser_download_url} da API).
     * @param sizeBytes      tamanho em bytes (0 se a API nao informou).
     */
    public record Asset(String name, String downloadUrl, long sizeBytes) {
    }

    /** Primeiro asset cujo nome termina em ".exe" (o instalador Windows gerado pelo jpackage), ou {@code null} se nao houver. */
    public Asset findExeAsset() {
        for (Asset a : assets) {
            if (a.name() != null && a.name().toLowerCase(java.util.Locale.ROOT).endsWith(".exe")) {
                return a;
            }
        }
        return null;
    }
}
