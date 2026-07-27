# Módulo: Metadados (`modulos.metadados`)

## Responsabilidade

Leitura da estrutura do banco (schemas, tabelas, colunas, chaves estrangeiras, índices) — uma única consulta ao `information_schema` por operação, cache em memória por sessão. Base para autocomplete, o assistente de DDL e o navegador de objetos.

## Estrutura interna

```
dominio/
  contratos/    MetadataRepository
  entidades/    ColumnDetail, ColumnInfo, DbUserInfo, ForeignKeyInfo, IndexInfo,
                NewColumnSpec, NewTableSpec, SchemaForeignKey, SchemaInfo, TableDetails, TableInfo
infraestrutura/ MetadataService (implementa MetadataRepository), MetadataCache
```

## Expõe (portas públicas)

- `MetadataRepository` — usado por `autocomplete`, `assistente-ddl`, `ia-chat` (via `IdeStateAccessor`/Tools) e pelos painéis de objetos da UI.

## Dependências

- `dialeto` (via `DatabaseDialect`) — todas as consultas ao `information_schema` são montadas pelo dialeto ativo, nunca com SQL hardcoded aqui.

## Lacunas conhecidas

- `MetadataCache` ainda é injetado/lido diretamente pelos consumidores em vez de ter seu ciclo de vida (invalidação ao reconectar/trocar schema) formalizado dentro de um caso de uso — ver spec 04, regra 3.
