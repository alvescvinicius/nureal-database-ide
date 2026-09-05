package com.nureal.ide.modulos.dialeto.dominio.contratos;

/**
 * Eventos agendados e replicacao (fase 4 do GAP_ANALYSIS_DBA_DEV.md) — ver
 * {@code com.nureal.ide.ui.EventsReplicationDialog}, o UNICO consumidor
 * hoje, por isso os dois temas (eventos agendados e replicacao) moram na
 * MESMA capacidade em vez de duas separadas. Capacidade OPCIONAL de
 * {@link DatabaseDialect} (ver {@link DatabaseDialect#replication()}):
 * {@code CREATE EVENT} e um conceito exclusivo do MySQL (sem equivalente em
 * Postgres/Oracle/SQLite), e o formato de status de replicacao tambem varia
 * completamente por banco. Um driver sem isso devolve
 * {@code Optional.empty()} em {@code replication()}.
 */
public interface ReplicationCapability {

    /**
     * Eventos agendados ({@code CREATE EVENT}) do schema informado. UM
     * parametro embutido diretamente na consulta (nao um {@code ?} — ver
     * {@code MainWindow#runQuery}, que executa via {@link java.sql.Statement}
     * puro, sem bind de parametros): nome, status (ENABLED/DISABLED/...),
     * tipo (RECURRING/ONE TIME), proxima execucao, intervalo, janela de
     * validade, o que fazer ao completar e a ultima execucao. Retorna
     * EVENT_NAME, STATUS, EVENT_TYPE, EXECUTE_AT, INTERVAL_VALUE,
     * INTERVAL_FIELD, STARTS, ENDS, ON_COMPLETION, LAST_EXECUTED, DEFINER.
     */
    String eventsQuery(String schema);

    /**
     * Status desta instancia como REPLICA de outro servidor. As colunas
     * VARIAM por versao do MySQL (por isso {@code ColumnQueryRunner}, nao
     * {@code QueryRunner} — ver {@code EventsReplicationDialog}); devolve
     * zero linhas se a instancia nao estiver configurada como replica.
     */
    String replicaStatusQuery();

    /**
     * Status desta instancia como ORIGEM de replicacao (posicao atual do
     * binary log) — util para configurar uma replica nova a partir daqui.
     * Mesma observacao de {@link #replicaStatusQuery()} sobre colunas
     * variaveis.
     */
    String sourceStatusQuery();
}
