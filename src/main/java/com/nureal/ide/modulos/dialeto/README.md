# Módulo: Dialeto (`modulos.dialeto`)

## Responsabilidade

Único ponto de extensão multi-banco do projeto: gera DDL/DML e consultas administrativas específicas de cada SGBD, sem que o resto do app precise saber qual banco está por trás. `MySqlDialect`, `PostgresDialect` e `SqliteDialect` são as implementações de hoje; SQL Server/Oracle entram como novas implementações de `DatabaseDialect`, sem alterar nenhum outro módulo.

`PostgresDialect` e `SqliteDialect` só implementam as 4 capacidades OBRIGATÓRIAS (conectar, sintaxe, metadados, DDL) — `security()`/`admin()`/`replication()` devolvem `Optional.empty()` (administração de usuários/roles, monitoramento de sessões e replicação são conceitos de banco cliente/servidor que nenhum dos dois tem do jeito do MySQL, ou não tem de jeito nenhum — SQLite é um arquivo local sem usuários nem processos concorrentes). Prova real de que a quebra em capacidades (ver abaixo) funciona: dois drivers "incompletos" convivem com o MySQL sem que `DatabaseDialect` precise de nenhum método a mais nem de nenhuma implementação vazia lançando exceção.

`SqliteDialect` também revela o limite da premissa "host/porta/schema" do formulário de conexão: SQLite é um arquivo único, sem servidor, porta, usuário, senha nem múltiplos schemas por conexão — `ConnectionProfile#host()` vira o CAMINHO do arquivo `.db` (rótulo do campo já muda para "Arquivo do banco (.db):" no formulário quando o SGBD selecionado é SQLite), o resto dos campos é ignorado, e `listSchemas` sempre devolve uma lista de um elemento só (`"main"`). Várias operações de `DdlCapability` também não têm equivalente real no SQLite (criar/apagar um "schema" novo numa conexão já aberta, adicionar/remover uma chave estrangeira de uma tabela existente, mudar tipo/nulo/default de uma coluna já criada, PROCEDURE/FUNCTION via SQL) — `SqliteDialect` lança `UnsupportedOperationException` com mensagem clara nesses casos, documentada no javadoc da classe, em vez de gerar SQL que o SQLite rejeitaria na hora de executar.

Nenhum outro ponto do app instancia um `DatabaseDialect` diretamente (`new MySqlDialect()`) — sempre resolvido através do `DriverRegistry` por um `ProviderType` (ver abaixo). Isso elimina o padrão "cada dialect novo precisa lembrar de mudar N lugares que faziam `new XxxDialect()` na unha".

## Estrutura interna

```
dominio/contratos/
    ConnectionCapability    conectar (URL JDBC, driver, sessão)      — OBRIGATÓRIA
    SqlSyntaxCapability     quoteIdentifier, keywords                — OBRIGATÓRIA
    MetadataCapability      schemas/tabelas/colunas/FK/PK            — OBRIGATÓRIA
    DdlCapability           CREATE/ALTER/DROP de tabela/view/trigger/rotina — OBRIGATÓRIA
    SecurityCapability      usuários/privilégios/roles               — OPCIONAL
    AdminCapability         PROCESSLIST/OPTIMIZE/SHOW VARIABLES      — OPCIONAL
    ReplicationCapability   CREATE EVENT/status de replicação        — OPCIONAL
    DatabaseDialect         estende as 4 obrigatórias + 3 Optional<Capacidade>
dominio/entidades/   ProviderType (MYSQL, POSTGRESQL, SQLITE, ORACLE — ORACLE ainda sem driver)
infraestrutura/       MySqlDialect, PostgresDialect, SqliteDialect, DriverRegistry
```

`DatabaseDialect` era uma única interface com ~60 métodos (todos obrigatórios) — quebrada em capacidades porque quase um terço desses métodos (eventos agendados, replicação, `PROCESSLIST`, `SHOW GRANTS`, roles...) são administração de **servidor MySQL** especificamente, sem equivalente direto em Postgres/Oracle/SQLite. Um driver novo só implementa as 4 capacidades obrigatórias pra já funcionar (conectar, navegar objetos, gerar DDL); as 3 opcionais só entram se o banco realmente tiver o recurso — sem isso, nenhum driver precisaria implementar ~20 métodos só para lançar `UnsupportedOperationException`.

`MetadataCapability` (uma das 4 obrigatórias) devolve OBJETOS DE DOMÍNIO prontos (`SchemaInfo`, `TableDetails`, `SchemaForeignKey`, ...), não SQL cru: cada driver lê o catálogo do jeito que o banco dele exigir (MySQL/Postgres via `information_schema`/catálogo próprio, SQLite via `PRAGMA table_info`/`sqlite_master`, um futuro driver Oracle via `ALL_TAB_COLUMNS`/`ALL_CONSTRAINTS`) e faz o parsing do `ResultSet` internamente, sem vazar nomes de coluna específicos (ex.: `COLUMN_TYPE`, `IS_NULLABLE`) para fora do driver. `MetadataService` (módulo `metadados`) virou um adaptador de pura delegação — só existe para preservar o port `MetadataRepository` que autocomplete/assistente de DDL/Explorador de Objetos já usam; toda a lógica de consulta e parsing que antes vivia lá agora está em cada driver. `definitionQuery`/`randomSampleQuery` continuam devolvendo SQL cru de propósito — não são lidos por nome de coluna fixo, então não têm o mesmo problema de acoplamento. `SqliteDialect#definitionQuery` é o único caso onde o DDL devolvido é sempre EXATO (o texto original do `CREATE` fica guardado literalmente em `sqlite_master.sql`), sem a aproximação que `PostgresDialect` precisa fazer para `TABLE` (ver javadoc de cada classe).

## Expõe (portas públicas)

- `DatabaseDialect` — usado por `conexoes` (URL JDBC, instruções de sessão), `metadados` (consultas ao `information_schema`), `assistente-ddl` (geração de DDL) e `ia-chat` (o `Specialist` de cada banco espelha este contrato). Consumidores que só precisam de uma capacidade OPCIONAL (ex.: `UserManagementDialog` só precisa de `SecurityCapability`) recebem essa interface específica, resolvida via `dialect.security()`/`.admin()`/`.replication()` (todas `Optional<T>` — vazio quando o driver ativo não suportar, e quem chama decide o que fazer: hoje, uma mensagem de status em vez de abrir o diálogo).
- `DriverRegistry` — resolve o `DatabaseDialect` certo por `ProviderType`. Montado uma única vez em `ComposicaoRaiz`; um driver novo só precisa de uma chamada a `register(provider, driver)` ali, nenhum outro módulo muda. Hoje `ProviderType.MYSQL`, `ProviderType.POSTGRESQL` e `ProviderType.SQLITE` estão registrados. `ConnectionProfile` (módulo `conexoes`) tem o campo `provider` — cada `Conexao`/workspace resolve seu **próprio** `DatabaseDialect` a partir dele ao conectar (`MainWindow#connect`, via `driverRegistry.driverFor(target.provider())`), não um dialeto global compartilhado por toda a IDE. O formulário de conexão (`ConnectionEditDialog`) já tem o seletor "Banco:", listando só os providers com driver registrado (`driverRegistry::isSupported`) — qualquer perfil salvo antes deste campo existir (`ConnectionStore` migra transparentemente para `MYSQL`) continua funcionando sem exigir edição.

## Dependências

Nenhuma dependência de outro módulo — `DatabaseDialect` é, propositalmente, o módulo mais "de fundação" do projeto.
