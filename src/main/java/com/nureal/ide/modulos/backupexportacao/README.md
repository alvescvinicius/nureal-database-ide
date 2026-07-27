# Módulo: Backup e Exportação (`modulos.backupexportacao`)

## Responsabilidade

Backup/restauração de um schema via `mysqldump`/`mysql` (processos externos) e exportação de resultados para Excel (.xlsx). O parser/gerador de CSV usado pela importação de dados também vive aqui, por ser a mesma família de "transferência de dados em massa" (ver `ObjectDataTransfer`, único consumidor).

## Estrutura interna

```
dominio/
  contratos/    BackupPort, TabelaExportavel
  CsvUtil.java  (parsing/escaping RFC4180-ish, sem estado, sem dependencia externa)
infraestrutura/ MySqlDumpRunner (implementa BackupPort), ExcelExporter
```

## Expõe (portas públicas)

- `BackupPort` — usado por `ObjectDataTransfer` (UI) para rodar backup/restore sem depender do `MySqlDumpRunner` concreto.
- `TabelaExportavel` — contrato mínimo (linhas/colunas/valor) que `ResultTableModel` (UI) implementa para poder ser exportado sem `ExcelExporter` depender de `javax.swing.table.TableModel`.

## Dependências

Nenhuma dependência de outro módulo — `MySqlDumpRunner` depende apenas de binários externos (`mysqldump`/`mysql`) via `ProcessBuilder`.

## Nome do pacote

`backupexportacao` (sem hífen) — Java não aceita `-` em identificador de pacote; o nome do módulo nas specs (`backup-e-exportacao`) é só a forma de pasta conceitual do NDS, não o nome de pacote real.
