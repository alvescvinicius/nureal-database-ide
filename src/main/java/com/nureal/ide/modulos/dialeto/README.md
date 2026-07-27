# Módulo: Dialeto (`modulos.dialeto`)

## Responsabilidade

Único ponto de extensão multi-banco do projeto: gera DDL/DML e consultas administrativas específicas de cada SGBD, sem que o resto do app precise saber qual banco está por trás. `MySqlDialect` é a única implementação hoje; Postgres/SQL Server/Oracle entram como novas implementações de `DatabaseDialect`, sem alterar nenhum outro módulo.

## Estrutura interna

```
dominio/contratos/   DatabaseDialect (interface, ~60 métodos)
infraestrutura/       MySqlDialect
```

## Expõe (portas públicas)

- `DatabaseDialect` — usado por `conexoes` (URL JDBC, instruções de sessão), `metadados` (consultas ao `information_schema`), `assistente-ddl` (geração de DDL) e `ia-chat` (o `Specialist` de cada banco espelha este contrato).

## Dependências

Nenhuma dependência de outro módulo — `DatabaseDialect` é, propositalmente, o módulo mais "de fundação" do projeto.
