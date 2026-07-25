# Módulos: Dialeto e Metadados

## Objetivo

Especificar a migração de `core.dialect.*` e `core.metadata.*` para `modulos/dialeto/` e `modulos/metadados/`, preservando o que já é o melhor exemplo de contrato de domínio do projeto e apenas fechando a lacuna de acoplamento a JDBC em `MetadataService`.

## Estado atual

- `DatabaseDialect` (interface) + `MySqlDialect` (impl) — ~60 métodos de geração de DDL/DML/consultas administrativas, todos retornando texto SQL sem efeito colateral. **Já é o padrão-alvo.** Nenhuma mudança estrutural necessária, apenas realocação de pacote.
- `MetadataService` — lê estrutura do banco em uma única consulta ao `information_schema`, mas opera diretamente sobre `java.sql.Connection`/`PreparedStatement`/`ResultSet`; não tem interface própria.
- `MetadataCache` — holder trivial (`volatile SchemaInfo`), sem interface.
- `metadata.model.*` (`ColumnDetail`, `ColumnInfo`, `DbUserInfo`, `ForeignKeyInfo`, `IndexInfo`, `NewColumnSpec`, `NewTableSpec`, `SchemaForeignKey`, `SchemaInfo`, `TableDetails`, `TableInfo`) — todos `record`s limpos, zero import de framework. **Já são domínio no sentido pleno do termo.**

## Estado alvo

```
modulos/dialeto/
  README.md
  dominio/
    contratos/
      DatabaseDialect.java          (sem mudança de assinatura, exceto novo método sessionInitStatements())
  infraestrutura/
    MySqlDialect.java                (sem mudança de lógica)

modulos/metadados/
  README.md
  interface/
    (nenhuma tela própria — este módulo é consumido por outros: object explorer, autocomplete, ddl assistant)
  aplicacao/
    casos-de-uso/
      carregar-estrutura-do-banco/
        carregar-estrutura-do-banco.input.java   (conexao ativa)
        carregar-estrutura-do-banco.output.java  (SchemaInfo | erro: FALHA_LEITURA_METADADOS)
        carregar-estrutura-do-banco.handler.java (era MetadataService.loadSchema)
      carregar-detalhes-da-tabela/
        ... (era MetadataService.loadTableDetails, já reusado por DescribeTableTool em ia-chat)
  dominio/
    entidades/
      ColumnDetail.java, ColumnInfo.java, DbUserInfo.java, ForeignKeyInfo.java,
      IndexInfo.java, NewColumnSpec.java, NewTableSpec.java, SchemaForeignKey.java,
      SchemaInfo.java, TableDetails.java, TableInfo.java     (sem mudança, apenas mover)
    contratos/
      MetadataRepository.java        (novo: interface — ler estrutura/detalhes do banco)
  infraestrutura/
    MetadataRepositoryJdbc.java       (implementa MetadataRepository; era MetadataService)
    MetadataCache.java                (cache em memória, injetado nos casos de uso de leitura)
```

## Regras específicas

1. `DatabaseDialect` ganha um método novo `List<String> sessionInitStatements()` para fechar a lacuna descrita em [03-modulo-conexoes-e-seguranca.md](03-modulo-conexoes-e-seguranca.md) (hoje `SessionInitializer` decide por `switch`, bypassando o dialect). `MySqlDialect` implementa retornando as instruções hoje hardcoded no `SessionInitializer`. Implementações futuras (Postgres, SQL Server, Oracle) implementam o próprio conjunto.
2. `MetadataRepository` é a única forma de outros módulos (`autocomplete`, `assistente-ddl`, `ia-chat`) obterem `SchemaInfo`/`TableDetails` — nenhum deles chama `MetadataRepositoryJdbc` diretamente.
3. `MetadataCache` deixa de ser um singleton estático implícito e passa a ser injetado no Handler de `CarregarEstruturaDoBanco`, que decide quando invalidar (ex.: ao reconectar) — hoje o cache já existe, apenas formaliza-se seu ciclo de vida via DI em vez de acesso estático.
4. Os `record`s de `metadata.model` são reaproveitados como estão — nenhuma duplicação de modelo entre `dialeto` e `metadados` deve ser criada; `dialeto` apenas gera texto SQL a partir desses mesmos records (`NewTableSpec`, `NewColumnSpec`) já usados pelo assistente de DDL.

## Exemplos bons

- Um novo dialeto Postgres implementando `DatabaseDialect.sessionInitStatements()` retornando `SET search_path = ...` sem tocar em `ConnectionManager` ou `SessionInitializer`.

## Exemplos ruins

- Um módulo novo instanciando `MetadataRepositoryJdbc` diretamente "porque só precisa de uma consulta rápida", ignorando a porta `MetadataRepository`.

## Critério de aceite

- [ ] `DatabaseDialect` expõe `sessionInitStatements()`; `SessionInitializer` não usa mais `switch` por nome de produto.
- [ ] `MetadataService` foi renomeado/movido para `MetadataRepositoryJdbc`, implementando a interface `MetadataRepository`.
- [ ] Nenhum consumidor de metadados (autocomplete, ddl assistant, ia-chat) importa a implementação concreta.
- [ ] Autocomplete e assistente de DDL continuam funcionando de forma idêntica após a migração (mesma latência de leitura única de `information_schema`, mesmo cache em memória).
