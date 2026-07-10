package com.nureal.ide.core.dialect;

import com.nureal.ide.core.connection.ConnectionProfile;
import com.nureal.ide.core.metadata.model.ForeignKeyInfo;
import com.nureal.ide.core.metadata.model.IndexInfo;
import com.nureal.ide.core.metadata.model.NewColumnSpec;
import com.nureal.ide.core.metadata.model.NewTableSpec;

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

    /**
     * Envolve um identificador (tabela ou coluna) nas aspas/crases do
     * dialeto, escapando ocorrencias internas do proprio caractere de aspa —
     * usado para montar UPDATE/INSERT/DELETE com nomes seguros ao aplicar
     * edicoes feitas direto na grade de resultados (ver GridEditController).
     */
    String quoteIdentifier(String ident);

    /**
     * Comando para criar um novo esquema (banco). {@code name} ja deve vir
     * validado/limpo pelo chamador (ver {@code MainWindow#createSchema}) — o
     * identificador e apenas envolvido em aspas seguras via
     * {@link #quoteIdentifier}, nao ha parametro (?) possivel aqui: DDL de
     * nome de esquema nao aceita bind variable em nenhum banco.
     */
    String createSchemaStatement(String name);

    /**
     * Comando para criar uma tabela nova a partir da especificacao coletada
     * pelo {@code DdlAssistantDialog} (com.nureal.ide.ui) — nome da tabela,
     * colunas (tipo, tamanho, nulo, chave primaria, auto increment, default,
     * comentario) e comentario da tabela. Todo identificador (tabela/coluna)
     * ja deve sair envolvido em {@link #quoteIdentifier} pela implementacao;
     * valores literais (DEFAULT, COMMENT) devem ser escapados por ela.
     */
    String createTableStatement(NewTableSpec spec);

    /**
     * Monta um (ou mais) comando(s) ALTER TABLE para ADICIONAR colunas,
     * chaves estrangeiras e/ou indices novos a uma tabela EXISTENTE — usado
     * pelo assistente de DDL ({@code com.nureal.ide.ui.DdlAssistantDialog})
     * no modo "alterar tabela". E SEMPRE ADITIVO (ADD COLUMN/ADD CONSTRAINT/
     * ADD INDEX): nunca gera MODIFY/DROP — mudar ou remover algo que ja
     * existe fica fora do escopo guiado do assistente, pois arrisca perda de
     * dados e merece revisao manual do usuario. Qualquer uma das 3 listas
     * pode vir vazia; se as 3 vierem vazias, retorna lista vazia (nada a
     * fazer). Todo identificador (tabela/coluna) ja deve sair envolvido em
     * {@link #quoteIdentifier} pela implementacao.
     */
    List<String> alterTableAddStatements(String tableName, List<NewColumnSpec> newColumns,
            List<ForeignKeyInfo> newForeignKeys, List<IndexInfo> newIndexes);

    /**
     * Consulta minima e barata para "keep-alive" da conexao (um SELECT de
     * teste, sem tocar em nenhuma tabela real) — usada para manter a sessao
     * viva enquanto a IDE esta aberta e a conexao fica ociosa por um tempo
     * (ver "Manter conexao viva" no menu de layout, em MainWindow). Default
     * {@code "SELECT 1"}, valido na maioria dos bancos; dialetos que exigem
     * uma clausula FROM (ex.: Oracle, {@code "SELECT 1 FROM DUAL"}) devem
     * sobrescrever.
     */
    default String keepAliveQuery() {
        return "SELECT 1";
    }
}
