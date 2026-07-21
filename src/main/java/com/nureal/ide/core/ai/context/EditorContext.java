package com.nureal.ide.core.ai.context;

/** SQL do editor ativo — {@code sql} e a selecao quando {@code hasSelection}, senao o conteudo inteiro da aba. */
public record EditorContext(String sql, boolean hasSelection) {

    public static final EditorContext EMPTY = new EditorContext(null, false);
}
