# Módulo: Assistente de DDL

## Objetivo

Especificar a migração de `ui.DdlAssistantDialog` e `core.ddl.NormalizationAdvisor` para `modulos/assistente-ddl/`.

## Estado atual

Este é, segundo o diagnóstico, **o dialog mais bem-comportado do projeto**: `DdlAssistantDialog.java` (1090 linhas) já delega geração de SQL a `DatabaseDialect` (`createTableStatement`, `alterTableAddStatements`, `alterTableModifyStatements`) e análise estrutural a `NormalizationAdvisor.analyze(...)`. O que resta na dialog é coleta de dados de formulário (`collectNewColumns`, `collectForeignKeys`, `collectIndexes`, `collectModifiedColumns`, `collectDroppedColumns/ForeignKeys/Indexes`) e validação (`buildStatements`, lançando `SqlBuilderValidationException` para nome vazio/duplicado/sem colunas, via `SqlIdentifiers.isValid`).

## Estado alvo

```
modulos/assistente-ddl/
  README.md
  apresentacao/
    DdlAssistantDialog.java           (mantém coleta de formulário; passa a chamar o Handler abaixo em vez de lançar SqlBuilderValidationException)
  aplicacao/
    casos-de-uso/
      construir-ddl-de-tabela/
        construir-ddl-de-tabela.input.java   (NewTableSpec ou NewColumnSpec[]/FKs/indices coletados do formulario)
        construir-ddl-de-tabela.output.java  (lista de instrucoes DDL prontas para preview | erro: NOME_INVALIDO | NOME_DUPLICADO | SEM_COLUNAS)
        construir-ddl-de-tabela.handler.java (absorve a validação hoje em DdlAssistantDialog.buildStatements + chama DatabaseDialect)
      analisar-normalizacao/
        ... (encapsula NormalizationAdvisor.analyze, sem mudança de lógica)
  dominio/
    NormalizationAdvisor.java          (sem mudança, apenas mover pacote)
```

## Regras específicas

1. `SqlBuilderValidationException` é substituída por um `Output` tipado do caso de uso `ConstruirDdlDeTabela` (`NOME_INVALIDO`, `NOME_DUPLICADO`, `SEM_COLUNAS`) — aplicando [02-Código/02-tratamento-de-excecoes.md](../../nureal-development-standard/02-Código/02-tratamento-de-excecoes.md): validação de formulário é erro de negócio esperado, não exceção.
2. `NormalizationAdvisor` não muda de lógica — é o melhor exemplo de "serviço de domínio puro" já existente fora de `core.ai`; a migração é puramente de pacote.
3. A pré-visualização do DDL formatado antes de executar (já existente) continua funcionando a partir do `Output` de `ConstruirDdlDeTabela`, sem mudança de UX.

## Critério de aceite

- [x] `DdlAssistantDialog` para de lançar/capturar `SqlBuilderValidationException` — trata o `Output` tipado do caso de uso `ConstruirDdlDeTabela`.
- [x] `NormalizationAdvisor` movido sem mudança de comportamento (sugestões de PK ausente, tipos, grupos repetitivos, dependência parcial/transitiva, índices de FK ausentes idênticas).
- [x] "Nova tabela..." e "Alterar tabela..." continuam produzindo exatamente o mesmo DDL de hoje para os mesmos inputs.

> **Progresso**: implementado o essencial desta spec. `NormalizationAdvisor` movido para `modulos.assistenteddl.dominio` sem mudança de lógica (só pacote), teste `NormalizationAdvisorTest` movido junto. Criado `modulos.assistenteddl.aplicacao.ConstruirDdlDeTabelaHandler` (+ `Input`/`Output`), com 4 códigos de erro (`NOME_INVALIDO`, `NOME_DUPLICADO`, `SEM_COLUNAS`, `DADOS_INCOMPLETOS` — este último cobre os erros de linha da grade, detectados por `DdlAssistantDialog` antes de chamar o Handler, já que só quem tem acesso à `JTable` sabe apontar "linha X"). `DdlAssistantDialog.buildStatements()` mudou de `throws SqlBuilderValidationException` para `retorna ConstruirDdlDeTabelaOutput`; `buildDdlPreview()`/`onExecute()` passaram a checar `output.sucesso()` em vez de `catch`. Mesmas mensagens de erro em todos os casos (nenhuma string mudou). Testado com `ConstruirDdlDeTabelaHandlerTest` (6 casos: cada código de erro + create + alter com sucesso) e `mvn clean test`: 173 testes, 1 falha pré-existente (`SqlFormatterTest`, não relacionada).
>
> **Desvio deliberado do critério original**: `SqlBuilderValidationException` (em `ui/`) **não foi excluída** — `ViewBuilderDialog`, `TriggerBuilderDialog` e `RoutineBuilderDialog` (fora do escopo desta fase) ainda dependem dela. Só `DdlAssistantDialog` parou de usá-la. Pelo mesmo motivo, `SqlIdentifiers` (também compartilhada pelos 4 assistentes) foi movida para `compartilhado.validacao` em vez de para dentro de `modulos.assistenteddl`, para o Handler não precisar depender de `ui` nem forçar os outros 3 assistentes a depender deste módulo. A movimentação física de `DdlAssistantDialog.java` em si para `apresentacao/` continua pendente (puramente mecânica, sem risco — pode ser feita junto com os 3 assistentes irmãos numa fase futura). Ver [modulos/assistenteddl/README.md](../src/main/java/com/nureal/ide/modulos/assistenteddl/README.md).
