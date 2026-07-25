package com.nureal.ide.core.update;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Caracteriza {@link UpdateChecker#isUpdateAvailable(GithubRelease)} apos
 * {@code fetchLatestRelease()} passar a ser um metodo de instancia
 * implementando {@link RepositorioDeReleasesPort} (ver
 * .specs/10-modulo-atualizacao-app.md) — nao testa {@code fetchLatestRelease()}
 * em si, pois exige uma chamada de rede real ao GitHub (fica para uma suite
 * de integracao separada, ver .specs/16-estrategia-de-testes.md).
 *
 * <p>Em ambiente de teste (fora de um jar empacotado),
 * {@link AppVersion#current()} e deterministico: sempre {@link AppVersion#DEV_VERSION}
 * ("0.0.0-dev"), o que torna esta comparacao segura de caracterizar sem
 * depender de qual versao o projeto esta no momento.
 */
class UpdateCheckerTest {

	@Test
	void releaseNuloNuncaEhConsideradoAtualizacaoDisponivel() {
		assertFalse(UpdateChecker.isUpdateAvailable(null));
	}

	@Test
	void qualquerTagValidaEhMaisNovaQueUmaBuildDeDesenvolvimento() {
		assertTrue(AppVersion.isDevBuild(), "este teste assume rodar fora de um jar empacotado");

		GithubRelease release = new GithubRelease("v1.0.0", "Nureal Database IDE v1.0.0",
				"https://github.com/alvescvinicius/nureal-database-ide/releases/tag/v1.0.0", "notas");

		assertTrue(UpdateChecker.isUpdateAvailable(release));
	}

	@Test
	void tagMalFormadaNuncaLancaExcecaoESeTornaZeroZeroZero() {
		GithubRelease release = new GithubRelease("nao-e-uma-versao", "release estranho", "https://exemplo.com",
				"notas");

		// "nao-e-uma-versao" vira 0.0.0 (ver SemVer), que nunca e mais novo que
		// "0.0.0-dev" (tambem 0.0.0 apos truncar o sufixo) — logo, nenhuma
		// atualizacao "disponivel", sem lancar excecao por tag invalida.
		assertFalse(UpdateChecker.isUpdateAvailable(release));
	}
}
