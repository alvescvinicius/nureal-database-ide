package com.nureal.ide.core.update;

/**
 * Um release do GitHub, ja reduzido aos campos que o checador de atualizacao
 * usa (ver {@link UpdateChecker#fetchLatestRelease()}) — o resto do JSON
 * devolvido pela API (autor, reacoes, draft/prerelease, assets anexados,
 * etc.) e ignorado de proposito: a atualizacao e sempre MANUAL (ver
 * {@code MainWindow#onInstallUpdate}, que so abre {@link #htmlUrl} no
 * navegador — a pagina do Release no GitHub, onde o usuario baixa e roda o
 * instalador certo pra sua plataforma sozinho), entao nao ha necessidade de
 * modelar nem baixar nenhum asset individual daqui.
 *
 * @param tagName   a tag do Git (ex.: {@code "v0.5.0"}) — e o que
 *                  {@link SemVer} compara contra {@link AppVersion#current()}.
 * @param name      titulo do release (ex.: {@code "Nureal Database IDE v0.5.0"}).
 * @param htmlUrl   pagina do release no GitHub (usada por "Baixar" / "Ver notas" / "Abrir no GitHub").
 * @param body      notas do release, em Markdown cru (exibidas como texto simples, sem renderizar Markdown).
 */
public record GithubRelease(String tagName, String name, String htmlUrl, String body) {
}
