package com.nureal.ide.modulos.iachat.dominio.entidades;

/**
 * Identidade da conexao ativa. {@code label} nunca inclui senha (ver
 * {@code ConnectionProfile} — cuidado ao montar, nunca usar {@code toString()}
 * do record nele, que expoe todos os campos inclusive a senha).
 */
public record ConnectionContext(String label, String databaseProductName, String databaseVersion, String schema) {

    public static final ConnectionContext EMPTY = new ConnectionContext(null, null, null, null);
}
