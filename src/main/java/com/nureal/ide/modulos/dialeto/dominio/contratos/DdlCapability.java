package com.nureal.ide.modulos.dialeto.dominio.contratos;

import com.nureal.ide.modulos.metadados.dominio.entidades.ForeignKeyInfo;
import com.nureal.ide.modulos.metadados.dominio.entidades.IndexInfo;
import com.nureal.ide.modulos.metadados.dominio.entidades.NewColumnSpec;
import com.nureal.ide.modulos.metadados.dominio.entidades.NewTableSpec;

import java.util.List;

/**
 * Geracao de DDL — schemas, tabelas, views, triggers, procedures/functions.
 * Parte OBRIGATORIA de {@link DatabaseDialect}: sem isto o assistente de
 * DDL e os builders de view/trigger/rotina nao tem como montar SQL nenhum.
 */
public interface DdlCapability {

    /**
     * Comando para criar um novo esquema (banco). {@code name} ja deve vir
     * validado/limpo pelo chamador (ver {@code MainWindow#createSchema}) — o
     * identificador e apenas envolvido em aspas seguras via
     * {@link SqlSyntaxCapability#quoteIdentifier}, nao ha parametro (?)
     * possivel aqui: DDL de nome de esquema nao aceita bind variable em
     * nenhum banco.
     */
    String createSchemaStatement(String name);

    /**
     * Comando para APAGAR um esquema (banco) inteiro — DESTRUTIVO e
     * IRREVERSIVEL: remove todas as tabelas, dados, views, triggers,
     * procedures/functions e privilegios concedidos sobre esse esquema.
     * {@code name} ja deve vir validado/limpo pelo chamador (mesmo criterio
     * de {@link #createSchemaStatement}); o chamador (ver
     * {@code MainWindow#deleteSchema}) e responsavel por exigir uma
     * confirmacao forte do usuario (digitar o nome do esquema) ANTES de
     * chegar aqui — esta interface so monta o SQL, nao decide se e seguro
     * executar.
     */
    String dropSchemaStatement(String name);

    /**
     * Comando para criar uma tabela nova a partir da especificacao coletada
     * pelo {@code DdlAssistantDialog} (com.nureal.ide.ui) — nome da tabela,
     * colunas (tipo, tamanho, nulo, chave primaria, auto increment, default,
     * comentario) e comentario da tabela. Todo identificador (tabela/coluna)
     * ja deve sair envolvido em {@link SqlSyntaxCapability#quoteIdentifier}
     * pela implementacao; valores literais (DEFAULT, COMMENT) devem ser
     * escapados por ela.
     */
    String createTableStatement(NewTableSpec spec);

    /**
     * Monta um (ou mais) comando(s) ALTER TABLE para ADICIONAR colunas,
     * chaves estrangeiras e/ou indices novos a uma tabela EXISTENTE — usado
     * pelo assistente de DDL ({@code com.nureal.ide.ui.DdlAssistantDialog})
     * no modo "alterar tabela". E SEMPRE ADITIVO (ADD COLUMN/ADD CONSTRAINT/
     * ADD INDEX): nunca gera MODIFY/DROP aqui — ver
     * {@link #alterTableModifyStatements} para mudar/remover algo que ja
     * existe (a UI oferece as duas coisas lado a lado, cada uma com seu
     * proprio nivel de confirmacao). Qualquer uma das 3 listas pode vir
     * vazia; se as 3 vierem vazias, retorna lista vazia (nada a fazer). Todo
     * identificador (tabela/coluna) ja deve sair envolvido em
     * {@link SqlSyntaxCapability#quoteIdentifier} pela implementacao.
     */
    List<String> alterTableAddStatements(String tableName, List<NewColumnSpec> newColumns,
            List<ForeignKeyInfo> newForeignKeys, List<IndexInfo> newIndexes);

    /**
     * Monta um (ou mais) comando(s) ALTER TABLE para MODIFICAR colunas
     * existentes (tipo/tamanho/nulo/default/comentario) e/ou REMOVER colunas,
     * chaves estrangeiras e indices ja existentes de uma tabela — usado pelo
     * assistente de DDL ({@code com.nureal.ide.ui.DdlAssistantDialog}) no modo
     * "alterar tabela" quando o usuario pede explicitamente uma mudanca
     * destrutiva/sensivel (ao contrario de {@link #alterTableAddStatements},
     * que e sempre aditivo). A UI exige confirmacao extra do usuario antes de
     * enviar qualquer um destes tres tipos de operacao (ver
     * DdlAssistantDialog), justamente por serem irreversiveis/com risco de
     * perda de dados.
     * <p>
     * Ordem das clausulas dentro do ALTER TABLE montado: DROP FOREIGN KEY,
     * depois DROP INDEX, depois DROP COLUMN, depois MODIFY COLUMN — nesta
     * ordem para que remover uma coluna que participa de uma FK/indice nunca
     * seja rejeitado pelo banco por a constraint/indice ainda existir (o
     * chamador deve pedir a remocao da FK/indice ANTES da coluna na mesma
     * chamada, o que esta ordem ja garante independente da ordem de entrada
     * nas listas). Renomear coluna nao e suportado (mesma limitacao do resto
     * do assistente — ver {@code DdlAssistantDialog#nameField}).
     * <p>
     * Cada uma das 4 listas pode vir vazia; se todas vierem vazias, retorna
     * lista vazia (nada a fazer). Todo identificador (tabela/coluna/
     * constraint/indice) ja deve sair envolvido em
     * {@link SqlSyntaxCapability#quoteIdentifier} pela implementacao. Nao
     * remove a chave primaria da tabela (essa e uma operacao a parte,
     * {@code DROP PRIMARY KEY}, fora do escopo guiado por ser sensivel
     * demais — mesmo criterio ja usado para nao oferecer PK/AI em colunas
     * novas no modo alterar).
     */
    List<String> alterTableModifyStatements(String tableName, List<NewColumnSpec> modifiedColumns,
            List<String> droppedColumns, List<String> droppedForeignKeys, List<String> droppedIndexes);

    // ====================================================================
    // Views — pedido explicito do usuario: "preciso de tudo que necessario
    // para construcao de esquemas, tabelas, views, triggers e etc". Ver
    // com.nureal.ide.ui.ViewBuilderDialog.
    // ====================================================================

    /**
     * Cria uma VIEW nova. {@code selectSql} e so o corpo do SELECT (sem
     * "CREATE VIEW ... AS" — quem monta o cabecalho e a implementacao).
     */
    String createViewStatement(String name, String selectSql);

    /**
     * Recria uma VIEW existente com um novo corpo de SELECT ({@code CREATE OR
     * REPLACE VIEW}) — usado para "editar" uma view (nao ha um ALTER VIEW
     * parcial no MySQL; a view inteira e substituida). Renomear a view nao e
     * suportado por este caminho (mesmo criterio do assistente de tabela).
     */
    String replaceViewStatement(String name, String selectSql);

    /** Remove uma view existente. */
    String dropViewStatement(String name);

    // ====================================================================
    // Triggers — mesmo pedido explicito acima. Ver
    // com.nureal.ide.ui.TriggerBuilderDialog.
    // ====================================================================

    /**
     * Cria um TRIGGER novo. {@code timing} e {@code BEFORE}/{@code AFTER};
     * {@code event} e {@code INSERT}/{@code UPDATE}/{@code DELETE};
     * {@code body} e o corpo do trigger (o que fica entre {@code BEGIN...END},
     * sem essas duas palavras-chave — a implementacao as adiciona). Nao ha
     * "CREATE OR REPLACE TRIGGER" portavel no MySQL: editar um trigger
     * existente e sempre DROP + CREATE (ver {@link #dropTriggerStatement}),
     * feito pela UI como duas instrucoes separadas, nunca por esta unica.
     */
    String createTriggerStatement(String triggerName, String timing, String event, String tableName, String body);

    /** Remove um trigger existente. */
    String dropTriggerStatement(String triggerName);

    // ====================================================================
    // Procedures e Functions — mesmo pedido explicito acima. Ver
    // com.nureal.ide.ui.RoutineBuilderDialog.
    // ====================================================================

    /**
     * Cria uma PROCEDURE nova. {@code parameters} e uma lista de definicoes
     * de parametro JA FORMATADAS pelo chamador (ex.: {@code "IN p_id INT"},
     * {@code "OUT p_total DECIMAL(10,2)"}) — a implementacao so as junta com
     * virgula; {@code body} e o corpo (entre {@code BEGIN...END}, sem essas
     * palavras-chave).
     */
    String createProcedureStatement(String name, List<String> parameters, String body);

    /**
     * Cria uma FUNCTION nova. Parametros de FUNCTION nao tem modo (sempre
     * "IN" implicito no MySQL — diferente de PROCEDURE); {@code deterministic}
     * controla a caracteristica {@code DETERMINISTIC}/{@code NOT
     * DETERMINISTIC} (bancos com {@code log_bin_trust_function_creators}
     * desligado recusam criar uma FUNCTION sem uma dessas duas — por isso a UI
     * sempre pede uma escolha explicita em vez de deixar a criacao falhar sem
     * explicacao).
     */
    String createFunctionStatement(String name, List<String> parameters, String returnType, boolean deterministic,
            String body);

    /** Remove uma procedure existente. */
    String dropProcedureStatement(String name);

    /** Remove uma function existente. */
    String dropFunctionStatement(String name);
}
