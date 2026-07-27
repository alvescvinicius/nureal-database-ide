# Módulo: Backup e Exportação

> **Progresso**: concluído por completo — `BackupPort`/`TabelaExportavel`
> extraídos e implementados; `MySqlDumpRunner`/`ExcelExporter`/`CsvUtil`
> movidos fisicamente para `modulos.backupexportacao.{dominio,infraestrutura}`
> (pacote sem hífen — Java não aceita `-` em identificador; ver README do
> módulo). Verificado por `mvn clean test` — 162 testes, mesma única falha
> pré-existente.

## Objetivo

Especificar a migração de `core.backup.MySqlDumpRunner`, `core.export.ExcelExporter` e `core.csv.CsvUtil` para `modulos/backup-e-exportacao/`.

## Estado atual

- `MySqlDumpRunner` — invoca `mysqldump`/`mysql` via `ProcessBuilder`; classe estática, sem interface; records `BackupOptions`, `RestoreOptions`, `ConnectionTarget`, `RunResult`.
- `ExcelExporter` — depende diretamente do Apache POI (`SXSSFWorkbook`) **e** de `javax.swing.table.TableModel` (tipo Swing) na mesma classe "core" — duplo vazamento (infra externa + framework de UI).
- `CsvUtil` — utilitário estático puro (parse/escape/join RFC4180-ish), zero dependência. Sem mudança necessária.
- `ui.BackupRestoreDialog` (426 linhas) — coleta opções e invoca `MySqlDumpRunner`.
- `ui.GridExporter` — ponto de entrada de exportação a partir da grade de resultados.

## Estado alvo

```
modulos/backup-e-exportacao/
  README.md
  apresentacao/
    BackupRestoreDialog.java           (movido, sem mudança de fluxo)
  aplicacao/
    casos-de-uso/
      executar-backup/
        ...input/output/handler         (era MySqlDumpRunner.runBackup)
      executar-restauracao/
        ...input/output/handler
      exportar-resultado-para-excel/
        exportar-resultado-para-excel.input.java   (dados tabulares em forma de domínio, não TableModel)
        exportar-resultado-para-excel.output.java  (caminho do arquivo gerado | erro: FALHA_DE_ESCRITA)
        exportar-resultado-para-excel.handler.java
      exportar-resultado-para-csv/
  dominio/
    entidades/
      BackupOptions.java, RestoreOptions.java, ConnectionTarget.java, RunResult.java   (sem mudança)
    contratos/
      TabelaExportavel.java              (novo: interface pequena — linhas + nomes de coluna — desacopla o exportador de javax.swing.table.TableModel)
  infraestrutura/
    MySqlDumpRunner.java                 (sem mudança de lógica, apenas pacote)
    ExcelExporterPoi.java                (implementa o caso de uso; depende de POI aqui, isolado)
```

## Regras específicas

1. `TabelaExportavel` é uma interface mínima (ex.: `int linhas()`, `int colunas()`, `String nomeColuna(int)`, `Object valor(int linha, int coluna)`) que qualquer fonte tabular pode implementar — `ResultTableModel` (Swing) passa a implementá-la, mas `ExcelExporterPoi`/o exportador de CSV deixam de importar `javax.swing.table.TableModel` diretamente. Isso resolve o vazamento de framework identificado no diagnóstico sem exigir reescrever a lógica de exportação.
2. `MySqlDumpRunner` permanece dependente de binários externos (`mysqldump`/`mysql`) via `ProcessBuilder` — isso é uma característica de infraestrutura esperada, não um problema a resolver; apenas ganha uma interface (`BackupPort`) para permitir, no futuro, uma implementação alternativa por banco (ex.: `pg_dump` para Postgres).
3. Nenhuma mudança no formato de saída (.xlsx, .csv) ou no comportamento de streaming (SXSSF) é permitida nesta migração.

## Critério de aceite

- [ ] Nenhuma classe em `modulos/backup-e-exportacao/aplicacao` ou `dominio` importa `javax.swing.table.TableModel`.
- [ ] Exportação para Excel e CSV produz arquivos byte-a-byte equivalentes aos gerados hoje para a mesma consulta.
- [ ] Backup e restauração via `mysqldump`/`mysql` continuam funcionando de forma idêntica.
