package com.nureal.ide.modulos.dialeto.dominio.contratos;

/**
 * Monitoramento de sessoes e manutencao de tabela (fase 2 do
 * GAP_ANALYSIS_DBA_DEV.md) — ver {@code com.nureal.ide.ui.ProcessListDialog},
 * {@code ServerStatusDialog} e o submenu "Manutencao" do Explorador de
 * Objetos. Capacidade OPCIONAL de {@link DatabaseDialect} (ver
 * {@link DatabaseDialect#admin()}): {@code PROCESSLIST}/{@code SHOW
 * VARIABLES}/{@code OPTIMIZE TABLE} sao primitivas de administracao
 * ESPECIFICAS do MySQL — Postgres usa {@code pg_stat_activity}/
 * {@code pg_terminate_backend}, {@code VACUUM}/{@code ANALYZE}, formatos
 * bem diferentes. Um driver sem equivalente devolve
 * {@code Optional.empty()} em {@code admin()}.
 */
public interface AdminCapability {

    /**
     * Sessoes ativas do servidor — ID, USER, HOST, DB, COMMAND, TIME
     * (segundos), STATE, INFO (o SQL rodando agora nessa sessao, ou nulo se
     * ociosa), nesta ordem, sem parametros.
     */
    String processListQuery();

    /** Encerra a sessao com este ID (aborta qualquer instrucao em andamento nela, com rollback). */
    String killStatement(long processId);

    /** {@code OPTIMIZE TABLE} — reorganiza o armazenamento fisico da tabela, recuperando espaco de linhas apagadas/atualizadas. */
    String optimizeTableStatement(String schema, String table);

    /** {@code ANALYZE TABLE} — recalcula as estatisticas de indice que o otimizador de consultas usa para escolher planos. */
    String analyzeTableStatement(String schema, String table);

    /** {@code CHECK TABLE} — verifica a integridade da tabela (detecta corrupcao). */
    String checkTableStatement(String schema, String table);

    /**
     * Variaveis de configuracao do servidor (nome + valor atual) — mistura
     * variaveis de sessao e globais; a UI (ver {@code ServerStatusDialog})
     * so exibe, nao distingue as que aceitam {@code SET GLOBAL} em runtime
     * das que exigem reiniciar o servidor (isso varia por variavel e nao vem
     * de forma confiavel no proprio {@code SHOW VARIABLES}).
     */
    String globalVariablesQuery();

    /** Contadores/estado do servidor (nome + valor) — throughput, conexoes, uso do buffer pool etc. */
    String globalStatusQuery();
}
