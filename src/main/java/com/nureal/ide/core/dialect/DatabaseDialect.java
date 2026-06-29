package com.nureal.ide.core.dialect;

import com.nureal.ide.core.connection.ConnectionProfile;

import java.util.List;

/**
 * Contrato por banco de dados. MySQL e a primeira implementacao;
 * Postgres / SQL Server / Oracle entram depois sem alterar o resto do app.
 */
public interface DatabaseDialect {

    /** Identificador curto, ex: "mysql". */
    String id();

    /** Classe do driver JDBC. */
    String driverClassName();

    /** Monta a URL JDBC a partir do perfil de conexao. */
    String buildJdbcUrl(ConnectionProfile profile);

    /**
     * Lista os esquemas (databases) que o usuario pode acessar. Sem parametros;
     * retorna uma coluna com o nome do esquema.
     */
    String schemasQuery();

    /**
     * Consulta unica que traz TODAS as colunas de TODAS as tabelas de um schema.
     * Deve ter um unico parametro (?) para o nome do schema e retornar as colunas:
     * TABLE_NAME, COLUMN_NAME, COLUMN_TYPE, ORDINAL_POSITION.
     */
    String columnsQuery();

    /**
     * Lista tabelas e views do schema. Um parametro (?) para o schema; retorna
     * as colunas TABLE_NAME e TABLE_TYPE (ex.: "BASE TABLE" ou "VIEW").
     */
    String tablesQuery();

    /**
     * Lista procedures e functions do schema. Um parametro (?) para o schema;
     * retorna ROUTINE_NAME e ROUTINE_TYPE (ex.: "PROCEDURE" ou "FUNCTION").
     */
    String routinesQuery();

    /**
     * Lista triggers do schema. Um parametro (?) para o schema; retorna a
     * coluna TRIGGER_NAME.
     */
    String triggersQuery();

    /**
     * Consulta que retorna a definicao (DDL) de um objeto. {@code objectKind} e
     * o tipo do objeto (ex.: "TABLE", "VIEW", "PROCEDURE", "FUNCTION",
     * "TRIGGER"); {@code objectName} e o nome. O DDL fica em alguma coluna do
     * resultado (ex.: "Create Table").
     */
    String definitionQuery(String objectKind, String objectName);

    /**
     * Colunas detalhadas de UMA tabela. Dois parametros (?): schema e tabela.
     * Retorna ORDINAL_POSITION, COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE,
     * COLUMN_KEY, COLUMN_DEFAULT, EXTRA e COLUMN_COMMENT.
     */
    String columnDetailsQuery();

    /**
     * Indices de UMA tabela. Dois parametros (?): schema e tabela. Retorna
     * INDEX_NAME, NON_UNIQUE, INDEX_TYPE, SEQ_IN_INDEX e COLUMN_NAME.
     */
    String indexesQuery();

    /**
     * Chaves estrangeiras de UMA tabela. Dois parametros (?): schema e tabela.
     * Retorna CONSTRAINT_NAME, COLUMN_NAME, REFERENCED_TABLE_NAME,
     * REFERENCED_COLUMN_NAME, UPDATE_RULE e DELETE_RULE.
     */
    String foreignKeysQuery();

    /** Palavras-chave da linguagem para o autocomplete. */
    List<String> keywords();
}
