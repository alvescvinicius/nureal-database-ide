package com.nureal.ide.modulos.conexoes.dominio.entidades;

import com.nureal.ide.modulos.dialeto.dominio.entidades.ProviderType;

/**
 * Dados de uma conexao com o banco. Imutavel.
 *
 * savePassword indica se a senha deve ser PERSISTIDA no arquivo de conexoes.
 * Ele nao afeta a sessao atual: se houver senha em memoria, ela e usada.
 *
 * provider identifica o SGBD desta conexao (ver {@code DriverRegistry}) —
 * cada {@code Conexao}/workspace resolve seu proprio {@code DatabaseDialect}
 * a partir deste campo, em vez de todas compartilharem um dialeto global
 * fixo. So MYSQL tem driver registrado hoje, entao toda conexao (nova ou
 * carregada de um arquivo salvo antes deste campo existir, ver
 * {@code ConnectionStore}) resolve para MYSQL — zero mudanca visivel ate um
 * segundo driver entrar e o formulario ganhar um seletor de banco.
 */
public record ConnectionProfile(
        String name,
        String host,
        int port,
        String schema,
        String user,
        String password,
        boolean savePassword,
        ProviderType provider) {

    public static ConnectionProfile mysqlDefault() {
        return new ConnectionProfile("Local MySQL", "localhost", 3306, "test", "root", "", false, ProviderType.MYSQL);
    }

    /** Copia trocando apenas a senha (usado ao informar a senha em tempo de conexao). */
    public ConnectionProfile withPassword(String newPassword) {
        return new ConnectionProfile(name, host, port, schema, user, newPassword, savePassword, provider);
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
