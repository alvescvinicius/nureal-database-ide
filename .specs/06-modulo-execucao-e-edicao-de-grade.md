# Módulos: Execução de Consulta e Edição de Grade

## Objetivo

Especificar a extração do motor de execução SQL hoje embutido em `MainWindow.java` e do motor de edição transacional hoje embutido em `GridEditController.java`, transformando-os em dois módulos de aplicação/domínio reais — a mudança de maior impacto e maior risco de toda esta migração.

## Motivação

Este é o item mais crítico do [diagnóstico](01-diagnostico-arquitetural-atual.md): `MainWindow.executeStatements`/`buildStatementResult`/`createModel`/`readJdbcMeta`/`appendPage` e a classe aninhada `ResultCursor` formam, juntos, um motor completo de "executar lote de instruções SQL contra uma conexão e paginar o resultado" usando `java.sql.*` cru dentro de uma classe de janela Swing. `GridEditController` (568 linhas) é um motor de CRUD transacional completo (dirty-tracking, `INSERT`/`UPDATE`/`DELETE` parametrizados via `DatabaseDialect`, rollback, recuperação de chave gerada) igualmente embutido em `ui/`. Nenhum dos dois tem hoje um equivalente em `core/` — são lógica de aplicação pura, sem lar.

## Estado atual (ver diagnóstico completo em [01](01-diagnostico-arquitetural-atual.md))

- `MainWindow.connectTo`/`disconnectFrom`: ciclo de vida de conexão via `SwingWorker`.
- `MainWindow.confirmRiskyStatements`/`onRun`/`onExplain`/`runStatements`/`statementsToRun`: orquestração pré-execução (usa `SqlStatementSplitter`, `UnquotedDateGuard`, `SqlRiskAnalyzer` — esses três já são domínio puro, preservados).
- `MainWindow.executeStatements`/`buildStatementResult`: execução real via `Connection.createStatement()`/`Statement.execute()`.
- `MainWindow.createModel(ResultSet)`/`readJdbcMeta`/`resolveColumnClass`/`appendPage`: mapeamento JDBC → modelo de grade paginado.
- `MainWindow.ResultCursor` (classe aninhada): mantém `Statement`/`ResultSet` abertos como estado de UI para paginação sob demanda.
- `ResultsAreaController.loadPage`/`loadAll`: consome o `ResultCursor` acima para paginação incremental.
- `GridEditController`: dirty-tracking de células, `executeInsert`/`executeUpdate`/`executeDelete`, `safeRollback`, `fillGeneratedKey`, `bindPk` — tudo com `java.sql.Connection/PreparedStatement/ResultSet/SQLException` importado.
- `ObjectExplorerController.runQuery`/`runQueryWithColumns` (via `QueryRunner`/`ColumnQueryRunner`): um segundo motor de execução de leitura, informal, reusado por `ProcessListDialog`, `ServerStatusDialog`, `EventsReplicationDialog`, `UserManagementDialog`.

## Estado alvo

```
modulos/execucao-consulta/
  README.md
  interface/
    (nenhuma tela própria — consumido por MainWindow/ResultsAreaController)
  aplicacao/
    casos-de-uso/
      executar-lote-de-instrucoes/
        executar-lote-de-instrucoes.input.java   (conexaoAtiva, lista de instrucoes SQL já validadas/divididas)
        executar-lote-de-instrucoes.output.java  (lista de ResultadoPorInstrucao: linhas afetadas | conjunto de resultado paginável | erro de negocao: ERRO_SQL(mensagem, instrucaoIndex))
        executar-lote-de-instrucoes.handler.java (era MainWindow.executeStatements/buildStatementResult)
      paginar-resultado/
        ... (era MainWindow.createModel/readJdbcMeta/appendPage + ResultCursor)
      executar-consulta-administrativa/
        ... (generaliza ObjectExplorerController.runQuery/runQueryWithColumns — usado por ProcessListDialog, ServerStatusDialog, EventsReplicationDialog, UserManagementDialog em vez de cada um receber um QueryRunner ad hoc)
  dominio/
    entidades/
      ResultadoPorInstrucao.java, CursorDeResultado.java (contrato, não a implementação com Statement/ResultSet cru)
    contratos/
      ExecutorDeConsultaPort.java
  infraestrutura/
    ExecutorDeConsultaJdbc.java        (implementa ExecutorDeConsultaPort; é onde java.sql.* efetivamente aparece)

modulos/edicao-grade/
  README.md
  aplicacao/
    casos-de-uso/
      aplicar-edicoes-da-grade/
        aplicar-edicoes-da-grade.input.java   (tabela alvo, PK, lista de celulas alteradas/inseridas/removidas)
        aplicar-edicoes-da-grade.output.java  (sucesso com chaves geradas | erro: CONFLITO_DE_CONCORRENCIA | VIOLACAO_DE_RESTRICAO | FALHA_DESCONHECIDA)
        aplicar-edicoes-da-grade.handler.java (era GridEditController.executeInsert/executeUpdate/executeDelete/safeRollback/fillGeneratedKey/bindPk)
  dominio/
    contratos/
      EditorDeGradePort.java
  infraestrutura/
    EditorDeGradeJdbc.java
```

## Regras específicas

1. **Este é o módulo piloto da migração inteira** (ver [14-plano-de-migracao-fases.md](14-plano-de-migracao-fases.md)) — por ser o de maior risco e maior ganho, é o primeiro a ser extraído, com o maior investimento em teste de caracterização antes de tocar no código.
2. `ExecutarLoteDeInstrucoes` retorna erros de SQL como `Output` tipado (`ERRO_SQL(mensagem, indiceDaInstrucao)`), nunca propaga `SQLException` até `MainWindow` — hoje `buildStatementResult` já captura e formata erro por instrução; a mudança é apenas mover essa lógica para o Handler e tipar o resultado, sem mudar a mensagem exibida ao usuário.
3. `CursorDeResultado` no domínio é uma interface/contrato (ex.: `proximaPagina(): List<Linha>`, `temMais(): boolean`, `fechar(): void`) — a implementação que efetivamente segura um `Statement`/`ResultSet` aberto vive só em `infraestrutura/`. Isso resolve o problema de "cursor de JDBC cru como estado de UI" descrito no diagnóstico, sem mudar o comportamento de paginação sob demanda que já existe.
4. `ExecutarConsultaAdministrativa` substitui `QueryRunner`/`ColumnQueryRunner` como o único caminho pelo qual `ProcessListDialog`, `ServerStatusDialog`, `EventsReplicationDialog`, `UserManagementDialog` executam consultas de leitura — eliminando a duplicação informal hoje concentrada em `ObjectExplorerController`.
5. `AplicarEdicoesDaGrade` preserva exatamente a semântica transacional atual (uma transação por lote de edições aplicado, rollback total em caso de qualquer falha) — isso é comportamento observável e não pode mudar durante a extração.

## Migração incremental sugerida

1. Escrever testes de caracterização cobrindo: executar um `SELECT` simples, executar múltiplas instruções separadas por `;`, executar uma instrução com erro de sintaxe no meio do lote, paginar um resultado grande, inserir/atualizar/excluir uma linha na grade com sucesso e com violação de restrição.
2. Extrair `ExecutorDeConsultaPort`/`EditorDeGradePort` como interfaces, mantendo `MainWindow`/`GridEditController` como implementação temporária delegando para elas internamente (passo reversível).
3. Mover a implementação real para `infraestrutura/ExecutorDeConsultaJdbc`/`EditorDeGradeJdbc`, com `MainWindow`/`ResultsAreaController`/`GridEditController` passando a chamar os casos de uso via o Handler injetado.
4. Só então reduzir `MainWindow`/`GridEditController` para as cascas de UI que restarem (montagem de toolbar, tradução de `Output` em diálogo de erro/atualização de grade).

## Critério de aceite

- [ ] `MainWindow.java` não contém mais `java.sql.*` importado.
- [ ] `GridEditController` não contém mais `java.sql.*` importado (ou deixa de existir como classe de `ui/`, substituído por uma casca fina que chama o Handler).
- [ ] `ProcessListDialog`, `ServerStatusDialog`, `EventsReplicationDialog`, `UserManagementDialog` usam `ExecutarConsultaAdministrativa` em vez de `QueryRunner`/`ColumnQueryRunner` ad hoc.
- [ ] Todos os testes de caracterização do passo 1 passam antes e depois da extração, com resultado idêntico.
- [ ] Nenhuma mudança perceptível de comportamento: mesma mensagem de erro por instrução, mesma paginação, mesma semântica transacional de edição de grade.
