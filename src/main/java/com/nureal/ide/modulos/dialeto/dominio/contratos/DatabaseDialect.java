package com.nureal.ide.modulos.dialeto.dominio.contratos;

import java.util.Optional;

/**
 * Contrato por banco de dados. MySQL e a primeira implementacao;
 * Postgres / SQL Server / Oracle entram depois sem alterar o resto do app.
 * <p>
 * Quebrado em capacidades menores (ver auditoria de arquitetura multi-banco)
 * em vez de uma unica interface com ~60 metodos: {@link ConnectionCapability},
 * {@link SqlSyntaxCapability}, {@link MetadataCapability} e
 * {@link DdlCapability} sao OBRIGATORIAS — todo driver precisa delas pra IDE
 * funcionar minimamente (conectar, ler metadados, gerar DDL). {@link
 * SecurityCapability} (usuarios/roles/grants), {@link AdminCapability}
 * (PROCESSLIST/OPTIMIZE/SHOW VARIABLES) e {@link ReplicationCapability}
 * (eventos agendados/replicacao) sao administracao de SERVIDOR especifica do
 * MySQL, sem equivalente direto em Postgres/Oracle/SQLite — por isso viram
 * {@link Optional}, nunca metodos diretos aqui: um driver que nao tiver
 * aquele recurso devolve {@code Optional.empty()} em vez de implementar ~20
 * metodos so pra lancar {@code UnsupportedOperationException}.
 * <p>
 * {@code MySqlDialect} implementa as 4 capacidades obrigatorias MAIS as 3
 * opcionais (MySQL suporta tudo isso hoje), entao {@code security()}/
 * {@code admin()}/{@code replication()} nela sempre devolvem
 * {@code Optional.of(this)} — nenhuma mudanca de comportamento pra quem ja
 * usa {@code DatabaseDialect} hoje.
 */
public interface DatabaseDialect
        extends ConnectionCapability, SqlSyntaxCapability, MetadataCapability, DdlCapability {

    /** Administracao de usuarios/privilegios/roles — {@code Optional.empty()} se o driver nao suportar (ver {@link SecurityCapability}). */
    Optional<SecurityCapability> security();

    /** Monitoramento de sessoes e manutencao de tabela — {@code Optional.empty()} se o driver nao suportar (ver {@link AdminCapability}). */
    Optional<AdminCapability> admin();

    /** Eventos agendados e replicacao — {@code Optional.empty()} se o driver nao suportar (ver {@link ReplicationCapability}). */
    Optional<ReplicationCapability> replication();
}
