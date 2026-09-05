package com.nureal.ide.modulos.populador.dominio;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * "varchar(255)" -&gt; base=VARCHAR, tamanho=255; "decimal(10,2)" -&gt;
 * base=DECIMAL, tamanho=10, escala=2; "int" -&gt; base=INT, sem tamanho.
 * Mesma ideia do parsing que {@code DdlAssistantDialog#parseType} ja faz
 * (nao reaproveitado diretamente por ser privado a uma classe de UI) —
 * usado por {@link FakeDataGenerator} para respeitar tamanho/precisao ao
 * gerar um valor.
 */
public record TipoColuna(String base, Integer tamanho, Integer escala) {

    private static final Pattern PARTES = Pattern.compile("^([a-zA-Z]+)\\s*(?:\\((\\d+)(?:,(\\d+))?\\))?");

    public static TipoColuna parse(String columnType) {
        if (columnType == null || columnType.isBlank()) {
            return new TipoColuna("VARCHAR", null, null);
        }
        Matcher m = PARTES.matcher(columnType.trim());
        if (!m.find()) {
            return new TipoColuna(columnType.toUpperCase(java.util.Locale.ROOT), null, null);
        }
        String base = m.group(1).toUpperCase(java.util.Locale.ROOT);
        Integer tamanho = m.group(2) != null ? Integer.valueOf(m.group(2)) : null;
        Integer escala = m.group(3) != null ? Integer.valueOf(m.group(3)) : null;
        return new TipoColuna(base, tamanho, escala);
    }
}
