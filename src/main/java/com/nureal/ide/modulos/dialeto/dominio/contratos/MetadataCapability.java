package com.nureal.ide.modulos.dialeto.dominio.contratos;

import com.nureal.ide.modulos.metadados.dominio.entidades.SchemaForeignKey;
import com.nureal.ide.modulos.metadados.dominio.entidades.SchemaInfo;
import com.nureal.ide.modulos.metadados.dominio.entidades.TableDetails;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Leitura de metadados (schemas/tabelas/colunas/FK/PK) — parte OBRIGATORIA
 * de {@link DatabaseDialect}: sem isto o Explorador de Objetos, o
 * autocomplete e o assistente de DDL nao tem como saber o que existe no
 * banco.
 * <p>
 * Devolve OBJETOS DE DOMINIO prontos (nao SQL cru) — cada driver consulta o
 * catalogo do jeito que o banco dele exigir (MySQL via
 * {@code information_schema}, Oracle via {@code ALL_TAB_COLUMNS}/
 * {@code ALL_CONSTRAINTS}, SQLite via {@code PRAGMA table_info}/
 * {@code sqlite_master}) e faz o PARSING do {@code ResultSet} internamente,
 * sem vazar nomes de coluna (ex.: {@code COLUMN_TYPE}, {@code IS_NULLABLE})
 * pra fora do driver. Antes desta capacidade existir, estes 5 metodos
 * devolviam SQL cru cujo {@code ResultSet} era lido por
 * {@code MetadataService} com nomes de coluna do {@code information_schema}
 * do MySQL — acoplando a FORMA da consulta ao formato esperado por quem lia
 * o resultado (ver auditoria de arquitetura multi-banco). Assinatura
 * identica a {@code MetadataRepository} (modulo {@code metadados}) de
 * proposito: {@code MetadataService} hoje so delega pra ca, sem logica
 * propria de parsing — o PORT que o resto do app usa (autocomplete,
 * assistente de DDL, Explorador de Objetos) nao mudou nada.
 * <p>
 * {@link #definitionQuery} e {@link #randomSampleQuery} continuam devolvendo
 * SQL cru de proposito: nenhum dos dois e lido por NOME de coluna fixo
 * (o primeiro usa um heuristica de busca por "create"/"statement" no
 * rotulo — ver {@code ObjectExplorerController#pickDefinitionColumn} — o
 * segundo le so a PRIMEIRA coluna por posicao), entao nao tem o mesmo
 * problema de acoplamento que motivou a mudanca dos outros 5.
 */
public interface MetadataCapability {

    /** Lista os esquemas (databases) acessiveis ao usuario conectado. */
    List<String> listSchemas(Connection conn) throws SQLException;

    /**
     * Carrega tabelas, views, procedures, functions, triggers e eventos
     * agendados de um schema — a base do Explorador de Objetos e do
     * autocomplete. "Eventos" e um conceito exclusivo do MySQL (ver
     * auditoria multi-banco); continua aqui, no grupo OBRIGATORIO, so
     * porque {@code SchemaInfo} ainda tem um campo fixo pra isso hoje — uma
     * evolucao futura torna a lista de categorias do Explorador de Objetos
     * data-driven por driver.
     */
    SchemaInfo loadSchema(Connection conn, String schema) throws SQLException;

    /**
     * Detalhe completo de UMA tabela/view: colunas (com nulo, chave,
     * default, extra e comentario), indices e chaves estrangeiras — usado
     * pela tela de propriedades e pelo assistente de DDL (modo "alterar
     * tabela").
     */
    TableDetails loadTableDetails(Connection conn, String schema, String table) throws SQLException;

    /**
     * Chaves estrangeiras de TODO o schema, de uma so vez — usada pelo
     * Diagrama ER ({@code ErDiagramWindow}), que precisa das relacoes entre
     * TODAS as tabelas para desenhar o grafo completo, sem uma consulta por
     * tabela (ver {@link #loadTableDetails}, que traz FK so de uma tabela
     * por vez).
     */
    List<SchemaForeignKey> loadSchemaForeignKeys(Connection conn, String schema) throws SQLException;

    /**
     * Colunas de chave PRIMARIA de TODO o schema, agrupadas por tabela —
     * usada pelo Diagrama ER para destacar a(s) coluna(s) PK de cada caixa
     * (mesma motivacao de {@link #loadSchemaForeignKeys}: evitar uma
     * consulta por tabela).
     */
    Map<String, Set<String>> loadSchemaPrimaryKeys(Connection conn, String schema) throws SQLException;

    /**
     * Consulta que retorna a definicao (DDL) de um objeto. {@code objectKind} e
     * o tipo do objeto (ex.: "TABLE", "VIEW", "PROCEDURE", "FUNCTION",
     * "TRIGGER"); {@code objectName} e o nome. O DDL fica em alguma coluna do
     * resultado (ex.: "Create Table") — o chamador acha a coluna certa por
     * heuristica de nome (ver {@code ObjectExplorerController#pickDefinitionColumn}),
     * nunca por um nome fixo, entao esta consulta continua livre.
     */
    String definitionQuery(String objectKind, String objectName);

    /**
     * Amostra ALEATORIA de ate {@code limit} valores de {@code column} numa
     * tabela — usado pelo Populador de Tabelas para sortear valores de FK
     * ja existentes na tabela pai (nunca cria linha nova nela, ver
     * {@code modulos.populador}). Identificadores ja devem sair envolvidos
     * em {@link SqlSyntaxCapability#quoteIdentifier} pela implementacao; sem
     * parametro (?), ja que tabela/coluna nunca sao entrada de usuario livre
     * neste fluxo (vem de metadados do proprio schema). Le so a PRIMEIRA
     * coluna por POSICAO, nunca por nome — continua livre pelo mesmo motivo
     * de {@link #definitionQuery}.
     */
    String randomSampleQuery(String table, String column, int limit);
}
