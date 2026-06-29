package com.nureal.ide.core.connection;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Persiste as conexoes do usuario em um arquivo de texto na pasta pessoal:
 *   ~/.nureal-ide/connections.conf
 *
 * Formato (simples e robusto, sem dependencias de parser):
 *   [connection]
 *   name=...
 *   host=...
 *   port=3306
 *   schema=...
 *   user=...
 *   savePassword=true|false
 *   password=&lt;base64&gt;        (somente quando savePassword=true)
 *
 * ATENCAO sobre a senha: quando salva, ela e apenas OFUSCADA em Base64 — isto
 * NAO e criptografia. Qualquer um com acesso ao arquivo consegue le-la.
 * Use "salvar senha" somente em maquinas confiaveis. A evolucao prevista e
 * integrar com o cofre de credenciais do SO (Windows Credential Manager).
 */
public class ConnectionStore {

    private static final String DIR_NAME = ".nureal-ide";
    private static final String FILE_NAME = "connections.conf";
    private static final String RECORD_HEADER = "[connection]";

    private final Path file;

    public ConnectionStore() {
        this(Paths.get(System.getProperty("user.home"), DIR_NAME, FILE_NAME));
    }

    public ConnectionStore(Path file) {
        this.file = file;
    }

    public Path location() {
        return file;
    }

    /** Le as conexoes. Retorna lista vazia se o arquivo nao existir. */
    public List<ConnectionProfile> load() throws IOException {
        List<ConnectionProfile> result = new ArrayList<>();
        if (!Files.exists(file)) {
            return result;
        }

        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        Builder current = null;
        for (String raw : lines) {
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

    /** Grava todas as conexoes, criando a pasta se necessario. */
    public void save(List<ConnectionProfile> connections) throws IOException {
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("# Nureal Database IDE - conexoes salvas\n");
        sb.append("# Senha (quando salva) esta apenas ofuscada em Base64, NAO criptografada.\n\n");

        for (ConnectionProfile c : connections) {
            sb.append(RECORD_HEADER).append('\n');
            sb.append("name=").append(nullToEmpty(c.name())).append('\n');
            sb.append("host=").append(nullToEmpty(c.host())).append('\n');
            sb.append("port=").append(c.port()).append('\n');
            sb.append("schema=").append(nullToEmpty(c.schema())).append('\n');
            sb.append("user=").append(nullToEmpty(c.user())).append('\n');
            sb.append("savePassword=").append(c.savePassword()).append('\n');
            if (c.savePassword() && c.password() != null && !c.password().isEmpty()) {
                sb.append("password=").append(encode(c.password())).append('\n');
            }
            sb.append('\n');
        }

        Files.write(file, sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static String encode(String plain) {
        return Base64.getEncoder().encodeToString(plain.getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(String encoded) {
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
                case "password" -> password = decode(value.trim());
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
