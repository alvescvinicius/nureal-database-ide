package com.nureal.ide.modulos.exportacaorelacional.infraestrutura;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Literal SQL de um valor de celula — mesma ideia de
 * {@code GridClipboard#sqlValue} (usado por "Copiar como INSERT"), com o
 * gap de data/hora corrigido aqui: {@code GridClipboard} usa {@code
 * toString()} cru pra qualquer tipo desconhecido, o que para
 * {@link Timestamp} produz algo como "2024-01-01 10:00:00.0" (o ".0" de
 * nanossegundos zerados nao e um problema para o MySQL, mas fica feio/
 * inconsistente) — aqui {@link Timestamp}/{@code java.sql.Date} sao
 * tratados explicitamente. {@code GridClipboard} pode passar a reusar esta
 * classe depois (fora do escopo desta entrega, ver plano).
 */
public final class SqlValueFormatter {

    private SqlValueFormatter() {
    }

    public static String format(Object v) {
        if (v == null) {
            return "NULL";
        }
        if (v instanceof Number) {
            return v.toString();
        }
        if (v instanceof Boolean b) {
            return b ? "1" : "0";
        }
        if (v instanceof Timestamp ts) {
            LocalDateTime dt = ts.toLocalDateTime();
            return "'" + dt.toString().replace('T', ' ') + "'";
        }
        if (v instanceof java.sql.Date d) {
            LocalDate ld = d.toLocalDate();
            return "'" + ld + "'";
        }
        return "'" + v.toString().replace("'", "''") + "'";
    }
}
