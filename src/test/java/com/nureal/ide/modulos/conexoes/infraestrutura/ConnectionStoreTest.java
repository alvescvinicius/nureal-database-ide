package com.nureal.ide.modulos.conexoes.infraestrutura;
import com.nureal.ide.modulos.conexoes.dominio.contratos.ConnectionRepository;
import com.nureal.ide.modulos.conexoes.dominio.entidades.ConnectionProfile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.nureal.ide.compartilhado.seguranca.CredentialCipher;
import com.nureal.ide.compartilhado.seguranca.LocalVault;

/**
 * Caracteriza {@link ConnectionStore} apos ele passar a implementar
 * {@link ConnectionRepository} e depender de {@link CredentialCipher} (a
 * abstracao, nao mais {@link LocalVault} especificamente) — ver
 * .specs/03-modulo-conexoes-e-seguranca.md.
 */
class ConnectionStoreTest {

	@Test
	void devolveListaVaziaQuandoArquivoNaoExisteAinda(@TempDir Path dir) throws IOException {
		ConnectionStore store = new ConnectionStore(dir.resolve("connections.conf"), identityCipher());

		assertTrue(store.load().isEmpty());
	}

	@Test
	void salvaERecarregaPerfisPreservandoTodosOsCampos(@TempDir Path dir) throws IOException {
		ConnectionStore store = new ConnectionStore(dir.resolve("connections.conf"), identityCipher());
		ConnectionProfile comSenha = new ConnectionProfile("prod", "db.exemplo.com", 3306, "app", "root", "s3nha",
				true);
		ConnectionProfile semSenha = new ConnectionProfile("dev", "localhost", 3306, "app", "root", "", false);

		store.save(List.of(comSenha, semSenha));
		List<ConnectionProfile> reloaded = store.load();

		assertEquals(2, reloaded.size());
		assertEquals(comSenha, reloaded.get(0));
		assertEquals(semSenha, reloaded.get(1));
	}

	@Test
	void naoGravaSenhaQuandoSavePasswordEhFalso(@TempDir Path dir) throws IOException {
		ConnectionStore store = new ConnectionStore(dir.resolve("connections.conf"), identityCipher());
		ConnectionProfile semSalvarSenha = new ConnectionProfile("dev", "localhost", 3306, "app", "root",
				"digitada-na-hora", false);

		store.save(List.of(semSalvarSenha));

		assertEquals("", store.load().get(0).password());
	}

	@Test
	void funcionaDePontaAPontaComOLocalVaultReal(@TempDir Path dir) throws IOException {
		LocalVault vault = new LocalVault(dir.resolve(".connections.key"));
		ConnectionStore store = new ConnectionStore(dir.resolve("connections.conf"), vault);
		ConnectionProfile profile = new ConnectionProfile("prod", "db.exemplo.com", 3306, "app", "root", "s3nha",
				true);

		store.save(List.of(profile));
		List<ConnectionProfile> reloaded = store.load();

		assertEquals(1, reloaded.size());
		assertEquals(profile, reloaded.get(0));
		// o arquivo em disco nao pode conter a senha em texto claro
		assertTrue(!Files.readString(store.location()).contains("s3nha"));
	}

	/**
	 * Cifrador fake (so Base64, sem AES real) — deixa o teste focado no
	 * formato de blocos do ConnectionStore. Precisa devolver uma unica linha
	 * (como a cifragem real faz) para nao quebrar a leitura de
	 * {@code ENCRYPTED_MAGIC + "\n" + cipherText} feita por {@link ConnectionStore#load()}.
	 */
	private static CredentialCipher identityCipher() {
		return new CredentialCipher() {
			@Override
			public String encrypt(String plainText) {
				return Base64.getEncoder().encodeToString(plainText.getBytes(StandardCharsets.UTF_8));
			}

			@Override
			public String decrypt(String base64) {
				return new String(Base64.getDecoder().decode(base64), StandardCharsets.UTF_8);
			}
		};
	}
}
