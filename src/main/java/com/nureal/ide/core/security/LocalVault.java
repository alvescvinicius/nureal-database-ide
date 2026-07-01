package com.nureal.ide.core.security;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.EnumSet;
import java.util.Set;
import java.nio.file.attribute.PosixFilePermission;

/**
 * Cofre local: gera (no primeiro uso) e guarda uma chave AES-256 propria
 * desta instalacao, em {@code ~/.nureal-ide/.connections.key}, e usa essa
 * chave para cifrar/decifrar dados sensiveis com AES/GCM/NoPadding.
 *
 * IMPORTANTE — o que isto protege e o que NAO protege:
 *   - O arquivo de conexoes deixa de ser texto legivel: abrir num editor, ou
 *     copiar o arquivo para outra maquina (sem a chave), nao revela nada.
 *     Outro programa que nao seja a Nureal Database IDE tambem nao consegue
 *     ler os dados sem essa chave.
 *   - Isto NAO e um cofre do sistema operacional (Windows Credential
 *     Manager/DPAPI, Keychain etc.): a chave fica em disco, no perfil do
 *     usuario, com permissoes restritas ao dono. Quem tiver acesso total ao
 *     perfil do usuario (ex.: outro processo rodando com a mesma conta)
 *     consegue, em teoria, achar a chave e decifrar. A evolucao prevista e
 *     migrar a guarda da chave para o Windows Credential Manager (via DPAPI).
 */
public final class LocalVault {

    private static final String DIR_NAME = ".nureal-ide";
    private static final String KEY_FILE_NAME = ".connections.key";
    private static final int KEY_LENGTH_BYTES = 32; // AES-256
    private static final int GCM_IV_LENGTH_BYTES = 12;
    private static final int GCM_TAG_LENGTH_BITS = 128;

    private final Path keyFile;
    private SecretKey cachedKey;

    public LocalVault() {
        this(Paths.get(System.getProperty("user.home"), DIR_NAME, KEY_FILE_NAME));
    }

    public LocalVault(Path keyFile) {
        this.keyFile = keyFile;
    }

    /** Cifra um texto; retorna o resultado pronto para gravar (Base64 de IV+ciphertext+tag). */
    public synchronized String encrypt(String plainText) throws GeneralSecurityException, IOException {
        SecretKey key = loadOrCreateKey();
        byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
        new SecureRandom().nextBytes(iv);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
        byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

        byte[] combined = new byte[iv.length + cipherText.length];
        System.arraycopy(iv, 0, combined, 0, iv.length);
        System.arraycopy(cipherText, 0, combined, iv.length, cipherText.length);
        return Base64.getEncoder().encodeToString(combined);
    }

    /** Decifra um texto gerado por {@link #encrypt}. */
    public synchronized String decrypt(String base64) throws GeneralSecurityException, IOException {
        byte[] combined;
        try {
            combined = Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException ex) {
            throw new GeneralSecurityException("Conteudo cifrado invalido.", ex);
        }
        if (combined.length <= GCM_IV_LENGTH_BYTES) {
            throw new GeneralSecurityException("Conteudo cifrado invalido (tamanho).");
        }
        byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
        byte[] cipherText = new byte[combined.length - GCM_IV_LENGTH_BYTES];
        System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH_BYTES);
        System.arraycopy(combined, GCM_IV_LENGTH_BYTES, cipherText, 0, cipherText.length);

        SecretKey key = loadOrCreateKey();
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
        byte[] plain = cipher.doFinal(cipherText);
        return new String(plain, StandardCharsets.UTF_8);
    }

    /** Verdadeiro se ja existe uma chave gravada nesta maquina/perfil. */
    public boolean keyExists() {
        return Files.exists(keyFile);
    }

    private synchronized SecretKey loadOrCreateKey() throws IOException, GeneralSecurityException {
        if (cachedKey != null) {
            return cachedKey;
        }
        if (Files.exists(keyFile)) {
            byte[] raw = Files.readAllBytes(keyFile);
            if (raw.length == KEY_LENGTH_BYTES) {
                cachedKey = new SecretKeySpec(raw, "AES");
                return cachedKey;
            }
            // tamanho inesperado: arquivo corrompido — gera uma nova chave abaixo
            // (dados cifrados com a chave antiga ficam irrecuperaveis).
        }
        byte[] raw = new byte[KEY_LENGTH_BYTES];
        new SecureRandom().nextBytes(raw);
        writeKeyFile(raw);
        cachedKey = new SecretKeySpec(raw, "AES");
        return cachedKey;
    }

    private void writeKeyFile(byte[] raw) throws IOException {
        Path parent = keyFile.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.write(keyFile, raw);
        restrictPermissions();
    }

    /** Melhor esforco para deixar a chave legivel/gravavel apenas pelo dono do perfil. */
    private void restrictPermissions() {
        try {
            Set<PosixFilePermission> perms = EnumSet.of(
                    PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
            Files.setPosixFilePermissions(keyFile, perms);
        } catch (UnsupportedOperationException | IOException posixUnsupported) {
            // Windows (NTFS) nao suporta permissoes POSIX; usa a API legada,
            // que no Windows remove o acesso de "todos" e mantem o do dono.
            File f = keyFile.toFile();
            f.setReadable(false, false);
            f.setReadable(true, true);
            f.setWritable(false, false);
            f.setWritable(true, true);
        }
        try {
            Files.setAttribute(keyFile, "dos:hidden", true);
        } catch (Exception notWindows) {
            // nao e NTFS/Windows: ignora, nao ha atributo "hidden" no POSIX puro
        }
    }
}
