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

    /**
     * Chaves estrangeiras de TODO o schema, de uma so vez — UM parametro (?):
     * schema. Usada pelo Diagrama ER ({@code ErDiagramWindow}), que precisa
     * das relacoes entre TODAS as tabelas para desenhar o grafo completo, sem
     * fazer uma consulta por tabela (ver {@link #foreignKeysQuery()}, que
     * exige a tabela como segundo parametro). Retorna CONSTRAINT_NAME,
     * TABLE_NAME (a tabela de ORIGEM), COLUMN_NAME, REFERENCED_TABLE_NAME,
     * REFERENCED_COLUMN_NAME, UPDATE_RULE e DELETE_RULE.
     */
    String foreignKeysQueryForSchema();

    /**
     * Colunas de chave PRIMARIA de TODO o schema, de uma so vez — UM
     * parametro (?): schema. Usada pelo Diagrama ER para destacar a(s)
     * coluna(s) PK de cada caixa (ver {@link #foreignKeysQueryForSchema()},
     * mesma motivacao de evitar uma consulta por tabela). Retorna TABLE_NAME
     * e COLUMN_NAME.
     */
    String primaryKeysQueryForSchema();

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
     * ja deve sair envolvido em {@link #quoteIdentifier} pela implementacao;
     * valores literais (DEFAULT, COMMENT) devem ser escapados por ela.
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
     * {@link #quoteIdentifier} pela implementacao.
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
     * constraint/indice) ja deve sair envolvido em {@link #quoteIdentifier}
     * pela implementacao. Nao remove a chave primaria da tabela (essa e uma
     * operacao a parte, {@code DROP PRIMARY KEY}, fora do escopo guiado por
     * ser sensivel demais — mesmo criterio ja usado para nao oferecer PK/AI
     * em colunas novas no modo alterar).
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

    // ====================================================================
    // Usuarios e privilegios (administracao do SERVIDOR, nao da aplicacao —
    // ver com.nureal.ide.ui.UserManagementDialog). Adicionado por pedido
    // explicito do usuario: "gerenciamento de usuario, permissoes... para
    // administradores das bases". Fica aqui, atras da mesma interface que ja
    // isola tudo que e MySQL hoje, porque o modelo de privilegios muda muito
    // de banco para banco (Postgres usa pg_roles/GRANT com sintaxe propria,
    // bem diferente do GRANT ... ON ... TO do MySQL) — quando entrar um
    // segundo dialeto, esta e a fronteira que ja existe para conter a
    // diferenca, sem mexer em UserManagementDialog.
    // ====================================================================

    /**
     * Lista os usuarios do SERVIDOR (nao do schema) — normalmente le uma
     * tabela de catalogo que exige privilegio administrativo para consultar
     * (ex.: {@code mysql.user}); o chamador deve tratar falha de permissao
     * como "conexao sem privilegio para administrar usuarios", nao como bug.
     * Retorna USER, HOST, ACCOUNT_LOCKED ('Y'/'N') e PASSWORD_EXPIRED
     * ('Y'/'N'), nesta ordem, sem parametros.
     */
    String listUsersQuery();

    /** {@code SHOW GRANTS} (ou equivalente) para um usuario+host especifico — saida crua, uma linha por GRANT. */
    String showGrantsQuery(String user, String host);

    /**
     * Cria um usuario novo. {@code expireNow} forca a troca de senha no
     * proximo login (politica comum ao entregar uma credencial nova a
     * alguem).
     */
    String createUserStatement(String user, String host, String password, boolean expireNow);

    /** Remove um usuario (e todos os privilegios/roles concedidos a ele junto). */
    String dropUserStatement(String user, String host);

    /** Troca a senha de um usuario existente. */
    String setPasswordStatement(String user, String host, String newPassword);

    /** Bloqueia/desbloqueia a conta (login recusado enquanto bloqueada, sem apagar usuario/privilegios). */
    String lockUserStatement(String user, String host, boolean lock);

    /** Forca a expiracao da senha atual agora (usuario precisa trocar no proximo login). */
    String expirePasswordStatement(String user, String host);

    /**
     * Monta o alvo de um GRANT/REVOKE ({@code *.*}, {@code `schema`.*},
     * {@code `schema`.`tabela`} ou {@code `schema`.`tabela` (`col1`, `col2`)})
     * a partir do nivel escolhido na UI — {@code schema}/{@code table} nulos
     * conforme o nivel (ambos nulos = global; so {@code table} nulo = schema
     * inteiro); {@code columns} vazio/nulo = privilegio na tabela inteira.
     */
    String privilegeTarget(String schema, String table, List<String> columns);

    /**
     * Concede privilegios a um usuario sobre o alvo montado por
     * {@link #privilegeTarget}. {@code withGrantOption} equivale a
     * {@code WITH GRANT OPTION} (o usuario passa a poder repassar esses
     * mesmos privilegios a outros).
     */
    String grantStatement(List<String> privileges, String target, String user, String host, boolean withGrantOption);

    /** Revoga privilegios de um usuario sobre o alvo montado por {@link #privilegeTarget}. */
    String revokeStatement(List<String> privileges, String target, String user, String host);

    /**
     * Privilegios validos SO no nivel global ({@code *.*}) — ex.:
     * administrar o servidor, criar outros usuarios, replicacao. Nao fazem
     * sentido presos a um schema/tabela especifico.
     */
    List<String> globalPrivilegeNames();

    /** Privilegios validos em nivel de schema ou tabela (o grosso do dia a dia: SELECT, INSERT, UPDATE, DELETE...). */
    List<String> objectPrivilegeNames();

    /** Subconjunto de {@link #objectPrivilegeNames()} que o banco aceita conceder POR COLUNA. */
    List<String> columnPrivilegeNames();

    /**
     * Cria uma role (MySQL 8+; conceito ausente em versoes antigas — a
     * execucao falha com uma mensagem clara do proprio servidor, que e
     * suficiente aqui). Uma role e um "pacote" de privilegios que pode ser
     * concedido a varios usuarios de uma vez, em vez de repetir o mesmo
     * conjunto de GRANTs usuario por usuario.
     */
    String createRoleStatement(String role);

    /** Remove uma role (usuarios que a tinham atribuida perdem os privilegios dela). */
    String dropRoleStatement(String role);

    /** Atribui uma role existente a um usuario (o usuario so GANHA os privilegios dela ao ativa-la — ver {@link #setDefaultRoleStatement}). */
    String grantRoleStatement(String role, String user, String host);

    /** Remove uma role de um usuario. */
    String revokeRoleStatement(String role, String user, String host);

    /**
     * Torna uma role ATIVA por padrao no login (sem isto, {@code GRANT role
     * TO user} so deixa a role DISPONIVEL — o usuario precisaria rodar
     * {@code SET ROLE} a cada sessao para os privilegios dela valerem).
     */
    String setDefaultRoleStatement(String role, String user, String host);

    /**
     * Roles conhecidas no servidor — melhor esforco: lista nomes distintos
     * que ja foram concedidos a pelo menos um usuario (nao ha, no MySQL, uma
     * marca confiavel e portatil entre versoes 8.x que diga "esta linha de
     * mysql.user e uma role e nao um usuario comum" fora dessa relacao).
     * Roles criadas mas ainda NUNCA atribuidas a ninguem nao aparecem aqui —
     * limitacao aceita, documentada na propria UI (ver UserManagementDialog).
     */
    String knownRolesQuery();

    // ====================================================================
    // Monitoramento de sessoes e manutencao (fase 2 do GAP_ANALYSIS_DBA_DEV.md).
    // ====================================================================

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

    // ====================================================================
    // Eventos agendados e replicacao (fase 4 do GAP_ANALYSIS_DBA_DEV.md).
    // ====================================================================

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
