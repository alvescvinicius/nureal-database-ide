package com.nureal.ide.modulos.atualizacao.dominio.contratos;
import com.nureal.ide.modulos.atualizacao.infraestrutura.UpdateChecker;
import com.nureal.ide.modulos.atualizacao.dominio.entidades.GithubRelease;

import java.io.IOException;

/**
 * Fonte do release mais recente publicado, usada pela checagem de
 * atualizacao (ver .specs/10-modulo-atualizacao-app.md). {@link UpdateChecker}
 * e, por enquanto, a unica implementacao (consulta o GitHub Releases); esta
 * porta existe para permitir, no futuro, trocar a fonte (ex.: um espelho
 * interno) sem alterar quem consome.
 */
public interface RepositorioDeReleasesPort {

    /**
     * Busca o release mais recente publicado. Lanca {@link IOException} em
     * qualquer falha (rede indisponivel, timeout, resposta inesperada) — o
     * chamador decide como tratar.
     */
    GithubRelease fetchLatestRelease() throws IOException;
}
