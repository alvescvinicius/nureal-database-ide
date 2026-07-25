# Módulo: Assistente de DDL

## Objetivo

Especificar a migração de `ui.DdlAssistantDialog` e `core.ddl.NormalizationAdvisor` para `modulos/assistente-ddl/`.

## Estado atual

Este é, segundo o diagnóstico, **o dialog mais bem-comportado do projeto**: `DdlAssistantDialog.java` (1090 linhas) já delega geração de SQL a `DatabaseDialect` (`createTableStatement`, `alterTableAddStatements`, `alterTableModifyStatements`) e análise estrutural a `NormalizationAdvisor.analyze(...)`. O que resta na dialog é coleta de dados de formulário (`collectNewColumns`, `collectForeignKeys`, `collectIndexes`, `collectModifiedColumns`, `collectDroppedColumns/ForeignKeys/Indexes`) e validação (`buildStatements`, lançando `SqlBuilderValidationException` para nome vazio/duplicado/sem colunas, via `SqlIdentifiers.isValid`).

## Estado alvo

```
modulos/assistente-ddl/
  README.md
  interface/
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

- [ ] `SqlBuilderValidationException` deixa de existir; `DdlAssistantDialog` trata o `Output` do caso de uso.
- [ ] `NormalizationAdvisor` movido sem mudança de comportamento (sugestões de PK ausente, tipos, grupos repetitivos, dependência parcial/transitiva, índices de FK ausentes idênticas).
- [ ] "Nova tabela..." e "Alterar tabela..." continuam produzindo exatamente o mesmo DDL de hoje para os mesmos inputs.
