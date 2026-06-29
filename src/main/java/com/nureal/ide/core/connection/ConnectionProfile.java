package com.nureal.ide.core.connection;

/**
 * Dados de uma conexao com o banco. Imutavel.
 *
 * savePassword indica se a senha deve ser PERSISTIDA no arquivo de conexoes.
 * Ele nao afeta a sessao atual: se houver senha em memoria, ela e usada.
 */
public record ConnectionProfile(
        String name,
        String host,
        int port,
        String schema,
        String user,
        String password,
        boolean savePassword) {

    public static ConnectionProfile mysqlDefault() {
        return new ConnectionProfile("Local MySQL", "localhost", 3306, "test", "root", "", false);
    }

    /** Copia trocando apenas a senha (usado ao informar a senha em tempo de conexao). */
    public ConnectionProfile withPassword(String newPassword) {
        return new ConnectionProfile(name, host, port, schema, user, newPassword, savePassword);
    }

    /** Precisa pedir a senha antes de conectar quando nao ha senha em memoria. */
    public boolean needsPasswordPrompt() {
        return password == null || password.isEmpty();
    }

    /** Rotulo curto para listas. */
    public String label() {
        return name + "  (" + user + "@" + host + ":" + port + "/" + schema + ")";
    }
}
