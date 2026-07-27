# Módulo: Histórico, Consultas Salvas e Sessão

> **Progresso**: `ArquivoChaveValorUtil` (codec Base64 + parseLong/parseInt)
> extraído para `compartilhado.persistencia` e usado pelas três stores —
> concluído. Movimentação física das três classes para
> `modulos.historico.infraestrutura` — concluída. **Pendente**: extração dos
> contratos (`HistoricoRepository`, `ConsultaSalvaRepository`,
> `SessaoRepository`) e das entidades para `dominio/`, e a unificação do
> parser de blocos completo (não feita — `SessionStore` tem formato aninhado
> incompatível com o formato flat de `ExecutionHistoryStore`/`SavedQueryStore`,
> ver README do módulo). Verificado por `mvn clean test` — 162 testes, mesma
> única falha pré-existente.

## Objetivo

Especificar a unificação de `core.history.ExecutionHistoryStore`, `core.queries.SavedQueryStore` e `core.session.SessionStore` — três implementações quase idênticas de persistência em arquivo — em um único módulo com uma infraestrutura de armazenamento compartilhada.

## Estado atual

As três classes reimplementam, cada uma independentemente, o mesmo formato chave=valor em blocos, gravado em arquivos separados dentro de `~/.nureal-ide/`, cada uma com seu próprio parsing/serialização e seu próprio `record` de entrada (`Entry`, `Query`, `Tab`). Nenhuma interface comum. `ChatHistoryStore` (em `core.ai.history`) segue o mesmo padrão, mas pertence ao módulo `ia-chat` (ver [11](11-modulo-ia-chat.md)) — não é unificada aqui para não misturar o domínio de conversas de IA com o de histórico de execução SQL, mas ambas devem compartilhar a mesma infraestrutura de arquivo-chave-valor descrita abaixo.

## Estado alvo

```
modulos/historico-e-consultas/
  README.md
  apresentacao/
    HistoryPanel.java                  (movido de ui/, sem mudança)
    SavedQueriesPanel.java             (movido de ui/, sem mudança)
  aplicacao/
    casos-de-uso/
      registrar-execucao/            (era ExecutionHistoryStore.append)
      listar-historico/
      salvar-consulta/                (era SavedQueryStore.save)
      listar-consultas-salvas/
      salvar-sessao-workspace/        (era SessionStore.save)
      restaurar-sessao-workspace/
  dominio/
    entidades/
      EntradaDeHistorico.java, ConsultaSalva.java, AbaDeWorkspace.java  (ex-Entry/Query/Tab, sem mudança de campos)
    contratos/
      HistoricoRepository.java, ConsultaSalvaRepository.java, SessaoRepository.java

compartilhado/persistencia-arquivo/
  ArmazenamentoChaveValor.java          (novo: parsing/serialização de blocos chave=valor extraído das três/quatro stores existentes — elimina a duplicação identificada no diagnóstico)
```

## Regras específicas

1. `ArmazenamentoChaveValor` é extraído para `compartilhado/` porque é usado por **quatro** stores diferentes (histórico, consultas salvas, sessão, e `ChatHistoryStore` do módulo `ia-chat`) — atende à "regra dos três usos" de [02-Código/05-componentes-reutilizaveis.md](../../nureal-development-standard/02-Código/05-componentes-reutilizaveis.md) do NDS com folga.
2. Cada repositório (`HistoricoRepository`, `ConsultaSalvaRepository`, `SessaoRepository`) continua gravando em arquivos separados (não há motivo de negócio para consolidar os arquivos) — apenas o mecanismo de parsing/serialização é compartilhado.
3. Nenhuma mudança de formato de arquivo em disco é permitida sem plano de migração de dados do usuário — arquivos existentes em `~/.nureal-ide/` de instalações já feitas devem continuar sendo lidos corretamente (ver [01-Arquitetura/10-evolucao-arquitetural.md](../../nureal-development-standard/01-Arquitetura/10-evolucao-arquitetural.md) sobre mudanças que quebram compatibilidade).

## Migração incremental sugerida

1. Extrair `ArmazenamentoChaveValor` comparando as quatro implementações existentes linha a linha, garantindo que o formato gerado é byte-a-byte idêntico ao atual antes de qualquer store passar a usá-lo.
2. Migrar uma store por vez (`ExecutionHistoryStore` primeiro, por ser a mais simples), validando que arquivos gravados pela versão antiga continuam sendo lidos corretamente pela nova.
3. Só então mover os quatro repositórios para seus módulos finais.

## Critério de aceite

- [ ] `ArmazenamentoChaveValor` é a única implementação de parsing chave=valor no projeto — as quatro stores o utilizam.
- [ ] Arquivos gravados pela versão anterior (`connections.conf`, `history.conf`, `saved-queries.conf`, `session.conf`, o arquivo de `ChatHistoryStore`) continuam sendo lidos sem perda de dados após a migração.
- [ ] Histórico, consultas salvas e restauração de sessão ao reabrir a IDE funcionam de forma idêntica ao comportamento atual.
