# Módulo: Conexões (`modulos.conexoes`)

## Responsabilidade

Gerencia os perfis de conexão salvos pelo usuário e o ciclo de vida da conexão JDBC ativa. A senha, quando marcada para salvar, é cifrada (ver `compartilhado.seguranca`) antes de ir para o disco.

## Estrutura interna

```
dominio/
  entidades/    ConnectionProfile
  contratos/    ConnectionRepository, ConexaoAtivaPort
infraestrutura/ ConnectionStore (implementa ConnectionRepository), ConnectionManager (implementa ConexaoAtivaPort),
                SessionInitializer
```

## Expõe (portas públicas)

- `ConexaoAtivaPort` — usado por `execucao-consulta`, `edicao-grade` (ainda não fisicamente movidos), `metadados`, `assistente-ddl`, `historico-e-consultas`, `backup-e-exportacao` e `ia-chat` para obter a conexão ativa sem depender do tipo concreto `ConnectionManager`.
- `ConnectionRepository` — persistência de perfis salvos, consumida pelo painel de conexões da UI.

## Dependências

- `dialeto` (via `DatabaseDialect`/`ProviderType`) — monta a URL JDBC e as instruções de inicialização de sessão; `ConnectionProfile.provider()` identifica o SGBD da conexão, resolvido para o `DatabaseDialect` certo via `DriverRegistry` (ver `MainWindow#connect`). Cada `ConnectionManager` guarda o dialeto da SUA própria conexão (não um dialeto global compartilhado) — dois workspaces abertos a bancos diferentes cada um usa o driver certo.
- `compartilhado.seguranca` (via `CredentialCipher`) — cifra/decifra a senha salva.

## Lacunas conhecidas

- `SessionInitializer`/`ConnectionManager` ainda não passaram pela extração de casos de uso explícitos (`Conectar`, `TestarConexao`, `SalvarPerfilConexao`) descrita na spec 03 — hoje são só as classes de infraestrutura originais, com as interfaces já extraídas por cima.
