package com.nureal.ide.modulos.iachat.apresentacao;

import java.util.function.Consumer;
import java.util.function.Supplier;

import com.nureal.ide.core.format.SqlFormatter;

/**
 * Acoes que um card SQL do chat pode disparar (ver {@link MessageRenderer}) —
 * "Copiar" nao precisa de nada externo (so a clipboard), mas "Executar",
 * "Formatar" e "Explicar" dependem de algo fora do pacote {@code modulos.iachat.apresentacao}
 * ({@code MainWindow} pra executar/formatar, o proprio {@link ChatController}
 * pra reenviar uma pergunta ao Agent) — agrupados aqui em vez de crescer a
 * lista de parametros de {@link MessageRenderer#render} a cada novo botao.
 * <p>
 * {@code activeSqlSupplier} (Fase 4) alimenta os presets de prompt do rodape
 * do chat (ver {@link ChatPanel}): SQL da aba de editor ativa (ou a ULTIMA
 * ativa, se o usuario estiver com a propria aba do Chat em foco — ver
 * {@code MainWindow#activeOrLastSqlForPresets}), ou {@code null} se nao
 * houver nenhuma.
 */
public record ChatActions(Consumer<String> onExecuteSql, Supplier<SqlFormatter> sqlFormatterSupplier,
        Consumer<String> onExplainSql, Supplier<String> activeSqlSupplier) {

    public static final ChatActions NONE = new ChatActions(sql -> { }, SqlFormatter::new, sql -> { }, () -> null);
}
