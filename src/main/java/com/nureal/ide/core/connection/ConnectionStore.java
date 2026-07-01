package com.nureal.ide.core.connection;

import com.nureal.ide.core.security.LocalVault;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Persiste as conexoes do usuario (host, porta, usuario e — se marcado para
 * salvar — a senha) em um arquivo cifrado na pasta pessoal:
 *   ~/.nureal-ide/connections.conf
 *
 * O conteudo inteiro do arquivo e cifrado com AES-256/GCM atraves do
 * {@link LocalVault}, usando uma chave gerada localmente na primeira
 * gravacao (~/.nureal-ide/.connections.key, com permissoes restritas ao
 * dono do perfil). Sem essa chave, o arquivo nao e legivel por ninguem —
 * nem por um editor de texto, nem por outro programa. So a Nureal Database
 * IDE, nesta maquina/perfil, consegue abrir as conexoes salvas.
 *
 * A senha continua guardada em Base64 *dentro* do conteudo cifrado (alem da
 * cifragem do arquivo inteiro): isso evita que uma senha com quebra de linha
 * quebre o parser linha-a-linha, e mantem o formato interno identico ao
 * antigo, simplificando a migracao.
 *
 * Compatibilidade: se o arquivo encontrado ainda estiver no formato antigo
 * (texto puro, nao cifrado), ele e lido e migrado automaticamente para o
 * novo formato cifrado no proximo save().
 */
public class ConnectionStore {

    private static final String DIR_NAME = ".nureal-ide";
    private static final String FILE_NAME = "connections.conf";
    private static final String RECORD_HEADER = "[connection]";
    private static final String ENCRYPTED_MAGIC = "NUREAL-ENC-V1";

    private final Path file;
    private final LocalVault vault;

    public ConnectionStore() {
        this(Paths.get(System.getProperty("user.home"), DIR_NAME, FILE_NAME));
    }

    public ConnectionStore(Path file) {
        this(file, new LocalVault());
    }

    public ConnectionStore(Path file, LocalVault vault) {
        this.file = file;
        this.vault = vault;
    }

    public Path location() {
        return file;
    }

    /** Le as conexoes (cifradas ou, se ainda no formato antigo, migra ao salvar). Vazio se nao existir. */
    public List<ConnectionProfile> load() throws IOException {
        if (!Files.exists(file)) {
            return new ArrayList<>();
        }
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        if (!lines.isEmpty() && ENCRYPTED_MAGIC.equals(lines.get(0).trim())) {
            String cipherText = lines.size() > 1 ? lines.get(1).trim() : "";
            if (cipherText.isEmpty()) {
                return new ArrayList<>();
            }
            try {
                String plain = vault.decrypt(cipherText);
                return parseBlocks(plain);
            } catch (GeneralSecurityException ex) {
                throw new IOException("Nao foi possivel decifrar as conexoes salvas "
                        + "(chave local ausente, trocada ou arquivo corrompido).", ex);
            }
        }
        // Formato antigo (texto puro): migra para cifrado.
        List<ConnectionProfile> legacy = parseBlocks(String.join("\n", lines));
        try {
            save(legacy);
        } catch (IOException ignore) {
            // Se a migracao falhar agora, tenta de novo no proximo save() explicito.
        }
        return legacy;
    }

    /** Grava todas as conexoes cifradas, criando a pasta se necessario. */
    public void save(List<ConnectionProfile> connections) throws IOException {
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        StringBuilder sb = new StringBuilder();
        for (ConnectionProfile c : connections) {
            sb.append(RECORD_HEADER).append('\n');
            sb.append("name=").append(nullToEmpty(c.name())).append('\n');
            sb.append("host=").append(nullToEmpty(c.host())).append('\n');
            sb.append("port=").append(c.port()).append('\n');
            sb.append("schema=").append(nullToEmpty(c.schema())).append('\n');
            sb.append("user=").append(nullToEmpty(c.user())).append('\n');
            sb.append("savePassword=").append(c.savePassword()).append('\n');
            if (c.savePassword() && c.password() != null && !c.password().isEmpty()) {
                sb.append("password=").append(encodeBase64(c.password())).append('\n');
            }
            sb.append('\n');
        }

        try {
            String cipherText = vault.encrypt(sb.toString());
            String out = ENCRYPTED_MAGIC + "\n" + cipherText + "\n";
            Files.write(file, out.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException ex) {
            throw new IOException("Falha ao cifrar as conexoes: " + ex.getMessage(), ex);
        }
    }

    /** Interpreta o conteudo (ja decifrado, ou o arquivo antigo em texto puro) em blocos. */
    private static List<ConnectionProfile> parseBlocks(String content) {
        List<ConnectionProfile> result = new ArrayList<>();
        Builder current = null;
        for (String raw : content.split("\n", -1)) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (line.equals(RECORD_HEADER)) {
                if (current != null) {
                    result.add(current.build());
                }
                current = new Builder();
                continue;
            }
            if (current == null) {
                continue; // ignora conteudo antes do primeiro [connection]
            }
            int eq = line.indexOf('=');
            if (eq < 0) {
                continue;
            }
            String key = line.substring(0, eq).trim();
            String value = line.substring(eq + 1); // mantem '=' do padding base64
            current.set(key, value);
        }
        if (current != null) {
            result.add(current.build());
        }
        return result;
    }

    private static String encodeBase64(String plain) {
        return Base64.getEncoder().encodeToString(plain.getBytes(StandardCharsets.UTF_8));
    }

    private static String decodeBase64(String encoded) {
        try {
            return new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return "";
        }
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    /** Acumula os campos de uma conexao durante a leitura. */
    private static final class Builder {
        private String name = "";
        private String host = "localhost";
        private int port = 3306;
        private String schema = "";
        private String user = "";
        private boolean savePassword = false;
        private String password = "";

        void set(String key, String value) {
            switch (key) {
                case "name" -> name = value.trim();
                case "host" -> host = value.trim();
                case "port" -> port = parsePort(value.trim());
                case "schema" -> schema = value.trim();
                case "user" -> user = value.trim();
                case "savePassword" -> savePassword = Boolean.parseBoolean(value.trim());
                case "password" -> password = decodeBase64(value.trim());
                default -> { /* ignora chaves desconhecidas */ }
            }
        }

        private static int parsePort(String s) {
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException e) {
                return 3306;
            }
        }

        ConnectionProfile build() {
            return new ConnectionProfile(name, host, port, schema, user, password, savePassword);
        }
    }
}
