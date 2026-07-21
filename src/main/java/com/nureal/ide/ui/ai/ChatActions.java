package com.nureal.ide.ui.ai;

import java.util.function.Consumer;
import java.util.function.Supplier;

import com.nureal.ide.core.format.SqlFormatter;

/**
 * Acoes que um card SQL do chat pode disparar (ver {@link MessageRenderer}) —
 * "Copiar" nao precisa de nada externo (so a clipboard), mas "Executar",
 * "Formatar" e "Explicar" dependem de algo fora do pacote {@code ui.ai}
 * ({@code MainWindow} pra executar/formatar, o proprio {@link ChatController}
 * pra reenviar uma pergunta ao Agent) — agrupados aqui em vez de crescer a
 * lista de parametros de {@link MessageRenderer#render} a cada novo botao.
 */
public record ChatActions(Consumer<String> onExecuteSql, Supplier<SqlFormatter> sqlFormatterSupplier,
        Consumer<String> onExplainSql) {

    public static final ChatActions NONE = new ChatActions(sql -> { }, SqlFormatter::new, sql -> { });
}
