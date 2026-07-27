package com.nureal.ide.modulos.iachat.dominio.entidades;

/**
 * Ultima execucao SQL registrada na conexao ativa (ver {@code ExecutionHistoryStore}).
 * {@code lastErrorMessage} so e preenchido quando {@code lastSuccess} e falso.
 */
public record ExecutionContext(String lastSql, boolean lastSuccess, String lastErrorMessage, long lastDurationMs) {

    public static final ExecutionContext EMPTY = new ExecutionContext(null, true, null, 0);
}
