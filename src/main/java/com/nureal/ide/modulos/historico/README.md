# Módulo: Histórico e Consultas Salvas (`modulos.historico`)

## Responsabilidade

Três formas de persistência relacionadas ao dia a dia de uso da IDE: histórico automático de execuções (`ExecutionHistoryStore`), biblioteca de consultas salvas deliberadamente pelo usuário (`SavedQueryStore`) e sessão do editor por conexão — quais abas estavam abertas e o que continham (`SessionStore`).

## Estrutura interna

```
infraestrutura/  ExecutionHistoryStore, SavedQueryStore, SessionStore
```

Sem `dominio/` ainda: as três classes não têm contratos (`Repository`) próprios — cada uma mistura, no mesmo arquivo, o `record` de entidade (`Entry`, `Query`, `Tab`/`Session`) e a lógica de persistência. Extrair contratos e mover as entidades para `dominio/` é trabalho pendente (ver spec 08).

## Dependências

- `compartilhado.persistencia` (via `ArquivoChaveValorUtil`) — as três stores compartilham a mesma cifragem Base64 de campo e parsing de número, extraída para eliminar a triplicação de código idêntico que existia antes.

## Lacunas conhecidas

- Nenhum contrato (`HistoricoRepository`, `ConsultaSalvaRepository`, `SessaoRepository`) foi extraído ainda — as três classes continuam sendo dependidas pelo tipo concreto onde quer que sejam usadas.
- `SessionStore` tem um formato de arquivo com blocos aninhados (`[conn]` contendo múltiplos `[tab]`), diferente do formato flat de `ExecutionHistoryStore`/`SavedQueryStore` — por isso a unificação do parser de blocos completo (não só o codec Base64) não foi tentada, ver spec 08.
