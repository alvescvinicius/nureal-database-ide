package com.nureal.ide.compartilhado.seguranca;
import com.nureal.ide.modulos.conexoes.infraestrutura.ConnectionStore;

import java.io.IOException;
import java.security.GeneralSecurityException;

/**
 * Contrato de cifragem/decifragem de texto sensivel (senhas de conexao,
 * chaves de API) — extraido de {@link LocalVault} (ver
 * .specs/03-modulo-conexoes-e-seguranca.md, regra 1) para que
 * {@code ConnectionStore}/{@code AiCredentialsStore} dependam de "algo que
 * cifra e decifra", nao especificamente de AES-256/GCM em arquivo local.
 * {@link LocalVault} continua sendo, por enquanto, a unica implementacao.
 */
public interface CredentialCipher {

    /** Cifra {@code plainText}, devolvendo um texto codificado (Base64) seguro para gravar em disco. */
    String encrypt(String plainText) throws GeneralSecurityException, IOException;

    /** Decifra um texto produzido por {@link #encrypt(String)}. */
    String decrypt(String base64) throws GeneralSecurityException, IOException;
}
