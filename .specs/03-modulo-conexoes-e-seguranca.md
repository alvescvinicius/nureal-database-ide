# Módulo: Conexões e Segurança

## Objetivo

Especificar a migração de `core.connection.*` e `core.security.LocalVault` para o módulo `modulos/conexoes/`, com contratos explícitos entre domínio e infraestrutura de persistência/cifragem.

## Estado atual

- `ConnectionProfile` (record: name, host, port, schema, user, password, savePassword) — domínio limpo, sem mudança estrutural necessária.
- `ConnectionManager` (`implements AutoCloseable`) — abre/fecha `java.sql.Connection` via `DatabaseDialect.buildJdbcUrl()` + `DriverManager`; tem um `static testConnection(...)` que duplica a lógica de abertura fora da instância.
- `ConnectionStore` — persistência em arquivo `~/.nureal-ide/connections.conf`, formato chave=valor cifrado, depende diretamente da classe concreta `LocalVault` (sem interface).
- `SessionInitializer` — utilitário estático que roda SQL de sessão por vendor (só MySQL implementado; Oracle/Postgres/SQL Server comentados), recebe `java.sql.Connection` cru, sem passar pelo `DatabaseDialect`.
- `LocalVault` — AES-256/GCM, chave própria em `~/.nureal-ide/.connections.key`, sem interface.
- `ui.ConnectionsPanel` (659 linhas) — já limpo hoje: CRUD via `ConnectionStore`, sem JDBC direto.
- `ui.ConnectionDialog`/`ConnectionEditDialog` — formulários de criação/edição; `ConnectionEditDialog` importa `java.sql.*` (usado para o botão "Testar conexão").

## Estado alvo

```
modulos/conexoes/
  README.md
  interface/
    ConnectionsPanel.java          (movido de ui/, sem mudança de comportamento)
    ConnectionDialog.java
    ConnectionEditDialog.java      (usa o caso de uso TestarConexao em vez de JDBC direto)
    ConnectionStatusCard.java      (movido de ui/)
  aplicacao/
    casos-de-uso/
      testar-conexao/
        testar-conexao.input.java    (record: profile completo com senha resolvida)
        testar-conexao.output.java   (sucesso | erro: HOST_INACESSIVEL | CREDENCIAIS_INVALIDAS | TIMEOUT)
        testar-conexao.handler.java  (substitui ConnectionManager.testConnection estático)
      conectar/
        conectar.input.java
        conectar.output.java         (sucesso com um ConexaoAtivaPort | erro tipado)
        conectar.handler.java
      salvar-perfil-conexao/
        ... (Input/Output/Handler em torno de ConnectionStore.save)
  dominio/
    entidades/
      ConnectionProfile.java        (sem mudança)
    contratos/
      ConnectionRepository.java      (novo: interface — salvar/listar/excluir perfis)
      CredentialCipher.java          (novo: interface — cifrar/decifrar senha, contrato para LocalVault)
      ConexaoAtivaPort.java          (novo: o que o resto do app enxerga de uma conexão aberta — usado por execucao-consulta, edicao-grade, ia-chat)
  infraestrutura/
    ConnectionStoreArquivo.java      (implementa ConnectionRepository; era ConnectionStore)
    LocalVault.java                 (implementa CredentialCipher; sem mudança de algoritmo)
    ConnectionManagerJdbc.java       (implementa ConexaoAtivaPort; era ConnectionManager)
    SessionInitializer.java         (chamado pelo Handler de Conectar após abrir a conexão)
```

## Regras específicas deste módulo

1. `ConnectionRepository` e `CredentialCipher` são interfaces novas — nenhum módulo externo deve depender de `ConnectionStoreArquivo`/`LocalVault` por nome de classe concreta.
2. `ConexaoAtivaPort` é o contrato público que `execucao-consulta`, `edicao-grade`, `assistente-ddl`, `historico-e-consultas` e `ia-chat` usam para obter a conexão ativa — nenhum desses módulos deve importar `ConnectionManagerJdbc` diretamente (ver [01-Arquitetura/08-comunicacao-entre-modulos.md](../../nureal-development-standard/01-Arquitetura/08-comunicacao-entre-modulos.md)).
3. `SessionInitializer` passa a chamar `DatabaseDialect.sessionInitStatements()` (método novo a adicionar ao contrato `DatabaseDialect`) em vez de decidir por `switch` no nome do produto — alinhando-o ao ponto de extensão multi-banco já estabelecido, e removendo a violação identificada no diagnóstico (SessionInitializer bypassava o dialect).
4. O botão "Testar conexão" do `ConnectionEditDialog` passa a chamar o caso de uso `TestarConexao`, eliminando o import de `java.sql.*` desse arquivo.
5. Erros de conexão (host inacessível, credenciais inválidas, timeout) são modelados como valores do `Output` do caso de uso `Conectar`/`TestarConexao`, nunca como `SQLException` propagada até a UI — ver [02-Código/02-tratamento-de-excecoes.md](../../nureal-development-standard/02-Código/02-tratamento-de-excecoes.md).

## Migração incremental sugerida

1. Extrair as três interfaces (`ConnectionRepository`, `CredentialCipher`, `ConexaoAtivaPort`) mantendo as classes atuais como implementação, sem mover arquivos ainda — primeiro passo reversível e de baixo risco.
2. Mover os arquivos para `modulos/conexoes/` respeitando a nova estrutura, com testes de caracterização existentes (ou novos) passando antes e depois.
3. Só então introduzir os três casos de uso (`TestarConexao`, `Conectar`, `SalvarPerfilConexao`) como camada fina sobre o que já existe, adaptando `ConnectionEditDialog`/`ConnectionsPanel` para chamá-los.

## Critério de aceite

- [ ] `ConnectionEditDialog` não importa mais `java.sql.*`.
- [ ] `SessionInitializer` usa `DatabaseDialect` em vez de `switch` por nome de produto.
- [ ] `execucao-consulta`, `edicao-grade`, `assistente-ddl`, `historico-e-consultas`, `ia-chat` dependem apenas de `ConexaoAtivaPort`, não de `ConnectionManagerJdbc`.
- [ ] Todo comportamento observado hoje (salvar conexão, testar conexão, conectar/desconectar, indicador de status) permanece idêntico.
