# Módulo: Assistente de DDL (`modulos.assistenteddl`)

## Responsabilidade

Motor de geração de DDL guiada (CREATE TABLE / ALTER TABLE) e análise de normalização, usados por `ui.DdlAssistantDialog`.

## Estrutura interna

```
dominio/         NormalizationAdvisor — serviço de domínio puro, sem mudança de lógica na migração
aplicacao/       ConstruirDdlDeTabelaInput/Output/Handler — Backbone Pattern (ver
                 .specs/07-modulo-assistente-ddl.md): recebe o formulário já coletado
                 (colunas/FKs/índices novos, e no modo alterar também os modificados/
                 removidos) e devolve as instruções DDL prontas OU um erro de validação
                 tipado (NOME_INVALIDO | NOME_DUPLICADO | SEM_COLUNAS | DADOS_INCOMPLETOS)
```

`ui.DdlAssistantDialog` (ainda não movido para `apresentacao/` — ver nota de escopo) continua responsável por: montar a UI, coletar os dados das grades Swing e — só ela tem acesso às linhas da grade para apontar "linha X" numa mensagem de erro — validar campo a campo antes de chamar o Handler.

## Expõe (portas públicas)

- `NormalizationAdvisor.analyze(...)` — chamado direto pela dialog (sem Handler próprio: é uma única chamada estática, sem estado nem orquestração — um wrapper de caso de uso aqui seria ceremônia sem benefício; revisar se um segundo consumidor aparecer).
- `ConstruirDdlDeTabelaHandler` — construído com o `DatabaseDialect` da conexão ativa, chamado por `DdlAssistantDialog.buildStatements()`.

## Dependências

Depende de `modulos.dialeto.dominio.contratos.DatabaseDialect`, `modulos.metadados.dominio.entidades` (specs de coluna/FK/índice/tabela) e `compartilhado.validacao.SqlIdentifiers`.

## Nota de escopo

`SqlBuilderValidationException` (`ui.SqlBuilderValidationException`) **não foi removida**: ainda é usada por `ViewBuilderDialog`, `TriggerBuilderDialog` e `RoutineBuilderDialog` (fora do escopo desta fase, que cobre só o assistente de tabela). `DdlAssistantDialog` parou de usá-la — os outros três assistentes migram na mesma linha quando forem tratados. Pelo mesmo motivo, `SqlIdentifiers` foi movida para `compartilhado.validacao` (já tinha 4 usos idênticos entre os quatro assistentes) em vez de para dentro deste módulo, para não forçar os outros três a depender de `modulos.assistenteddl`.

A movimentação física de `ui.DdlAssistantDialog` para `apresentacao/` (spec: `modulos/assistente-ddl/apresentacao/DdlAssistantDialog.java`) continua pendente — o dialog em si (1090 linhas de Swing) não foi tocado nesta fase, só a validação/geração de DDL que ele chama; mover o arquivo de pacote é puramente mecânico (sem mudança de lógica) e pode ser feito depois, junto com os outros 3 assistentes irmãos (view/trigger/routine), sem risco adicional.
