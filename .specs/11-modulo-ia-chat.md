# Módulo: IA Chat

> **Progresso**: concluído por completo — pacote raiz movido de
> `com.nureal.ide.core.ai` para `com.nureal.ide.modulos.iachat`, com a
> reorganização interna em `dominio/contratos`, `dominio/entidades`,
> `aplicacao` e `infraestrutura/{provider,tool,specialist}`, **e** a camada
> de apresentação (`com.nureal.ide.ui.ai.*`, 7 arquivos) movida para
> `modulos.iachat.apresentacao` (decisão revista — inicialmente adiada por
> uma sessão, depois feita por ser uma renomeação mecânica de pacote tão
> segura quanto a do restante do módulo, sem mudança de comportamento).
> Tudo verificado por `mvn clean test` — 162 testes, mesma única falha
> pré-existente não relacionada. Duas descobertas registradas durante a
> execução:
> 1. `interface` é palavra reservada em Java — não pode ser nome de pacote;
>    por isso a camada de apresentação usa `apresentacao/`, não
>    `interface/` (o restante desta spec já foi ajustado para refletir
>    isso).
> 2. `LLMProvider.createSession()` (método `default` na interface de
>    domínio) instanciava diretamente `GenericSession`, uma classe de
>    infraestrutura — funcionava por acidente enquanto ambas estavam no
>    mesmo pacote `core.ai.provider`. Separar as camadas expôs esse
>    acoplamento; a correção mínima aplicada foi tornar `GenericSession`
>    pública (era package-private). O acoplamento em si — uma interface de
>    domínio com um `default` que constrói uma classe de infraestrutura
>    concreta — continua existindo e é uma lacuna a resolver depois (ver
>    [09-Evolução/02-lacunas-identificadas.md](../../nureal-development-standard/09-Evolução/02-lacunas-identificadas.md)
>    do NDS), não corrigida agora para não misturar reorganização de pacote
>    com mudança de design.
>
> A **Lacuna 1** (composition root) e a **Lacuna 2** (`DefaultAgent`
> segmentado em Input/Handler/Output literal) descritas abaixo continuam
> pendentes.

## Objetivo

Formalizar `core.ai.*` + `ui.ai.*` como o **módulo de referência** da migração — a parte do código que já mais se aproxima do Backbone Pattern do NDS — e fechar as duas lacunas reais que restam: a construção lazy do grafo de objetos dentro de `MainWindow.openAiChat()`, e o fato de `DefaultAgent` ainda misturar validação de entrada, orquestração e tradução de evento em uma única classe.

## Estado atual

Já descrito em detalhe no [diagnóstico](01-diagnostico-arquitetural-atual.md#o-que-já-está-alinhado-ao-nds-preservar). Resumo:

- `Agent` (interface) + `DefaultAgent` (impl) — DI por construtor de `LLMProvider`, `ContextProvider`, `ToolExecutor`, `ChatHistoryStore`, `Supplier<AiPreferences.State>`. `chat(conversationId, userMessage, onEvent)` emite `AiEvent`s tipados (`Started`, `ToolCallsRequested`, `ToolCallResult`, `Completed`, `Failed`, `Cancelled`) em vez de lançar exceção.
- `ContextProvider`/`DefaultContextProvider` — snapshot somente-leitura do estado da IDE (`AgentContext` com `ConnectionContext`, `MetadataContext`, `EditorContext`, `ExecutionContext`), documentado como "sem dependência de UI".
- `LLMProvider` (interface) + `AbstractStreamingProvider` + 6 implementações (`Claude`, `Gemini`, `Ollama`, `OpenAi`, `OpenRouter`, `OpenAiCompatible`) — Strategy pattern genuíno. **Tratado como estável/congelado pelo próprio `AI-CHAT-MASTER-PLAN.md` do projeto** ("nenhuma fase toca `core.ai.provider`") — esta migração respeita a mesma decisão.
- `Tool`/`ToolExecutor` — registro nome→Tool, exceções de `tool.execute()` convertidas em `ToolResult.failure(...)` tipado. Implementações (`ListTablesTool`, `DescribeTableTool`, `ExecuteSqlTool`) são adaptadores finos sobre `MetadataService`/`ConnectionManager` — já seguem "tools nunca contêm regra de negócio própria".
- `Specialist`/`MySqlSpecialist`/`SpecialistRegistry` — Strategy por vendor, espelha `DatabaseDialect`.
- `ui.ai.IdeContextAccessor implements modulos.iachat.dominio.contratos.IdeStateAccessor` — exemplo real de inversão de dependência já em produção.
- **Lacuna 1**: o grafo de objetos inteiro (`AiPreferences`, `AiCredentialsStore`, `ChatHistoryStore`, o `Agent` montado por `buildAiAgent(...)`) é construído sob demanda dentro de `MainWindow.openAiChat()`, não em um composition root.
- **Lacuna 2**: `DefaultAgent.chat()` faz validação de entrada, orquestração de tool-calling e tradução de evento tudo em uma classe — funcional, mas não segmentado como Input/Handler/Output literal.

## Estado alvo

```
modulos/ia-chat/
  README.md
  apresentacao/
    ChatWindow.java, ChatPanel.java, ChatController.java, ChatActions.java,
    MessageRenderer.java, IdeContextAccessor.java, AiSettingsDialog.java   (movidos de ui/ai, sem mudança de lógica)
  aplicacao/
    casos-de-uso/
      conversar-com-agente/
        conversar-com-agente.input.java    (conversationId, mensagem do usuario)
        conversar-com-agente.output.java   (stream de AiEvent — ver nota abaixo sobre Output assíncrono)
        conversar-com-agente.handler.java  (DefaultAgent, com a validação de entrada extraída para um passo explícito antes da orquestração)
  dominio/
    contratos/
      Agent.java, LLMProvider.java, ContextProvider.java, Tool.java,
      Specialist.java, IdeStateAccessor.java, ConversationSession.java     (sem mudança de assinatura)
    entidades/
      AgentContext.java, ConnectionContext.java, EditorContext.java, ExecutionContext.java, MetadataContext.java,
      ChatMessage.java, ChatRequest.java, ChatResponse.java, ChatUsage.java, AiEvent.java, ToolCall.java, ToolSpec.java,
      ProviderException.java, ProviderType.java, ToolRequest.java, ToolResult.java   (sem mudança de campos)
  aplicacao/
    DefaultAgent.java, DefaultContextProvider.java, PromptComposer.java, ToolExecutor.java
    (ToolExecutor entrou aqui, não em dominio/contratos como a versão anterior desta spec previa —
     é um orquestrador/registro que converte exceção de Tool em ToolResult tipado, não um contrato puro)
  infraestrutura/
    provider/  (ClaudeProvider, GeminiProvider, OllamaProvider, OpenAiProvider, OpenRouterProvider,
                 OpenAiCompatibleProvider, AbstractStreamingProvider, GenericSession, SseUtil —
                 sem mudança de lógica, tratados como estáveis)
    tool/      (ListTablesTool, DescribeTableTool, ExecuteSqlTool, SqlQueryResult — sem mudança)
    specialist/ (MySqlSpecialist, SpecialistRegistry — sem mudança)
    ChatHistoryStore.java   (já usa ArquivoChaveValorUtil de compartilhado/persistencia, ver 08)
    AiCredentialsStore.java, AiPreferences.java, LLMProviderFactory.java
```

## Regras específicas

1. **Nota sobre "Output assíncrono"**: diferente do Backbone Pattern padrão (Output único de retorno síncrono), este caso de uso é inerentemente de streaming — o "Output" é o fluxo de `AiEvent`s entregue via callback (`onEvent`), não um valor de retorno único. Isso é uma variação documentada e aceita do Backbone Pattern para casos de uso assíncronos/streaming — não uma violação. Outros módulos com necessidade semelhante (ex.: execução de lote de instruções SQL com progresso incremental) podem seguir o mesmo formato.
2. `LLMProvider` e suas 6 implementações **não são tocados** por esta migração — apenas movidos de pacote (`core.ai.provider` → `modulos.iachat.infraestrutura.provider`). Respeita a decisão já registrada em `AI-CHAT-MASTER-PLAN.md` de tratar este ponto como estável.
3. A composição do `Agent` (via `LLMProviderFactory`, `ContextProvider`, `ToolExecutor`, `ChatHistoryStore`) passa a acontecer no composition root (`App.java`, ver [13](13-composition-root-e-bootstrap.md)) na inicialização da aplicação, não lazily dentro de `openAiChat()`. Isso fecha a Lacuna 1 do diagnóstico sem alterar quando visualmente a janela de chat aparece ao usuário (a janela continua sendo aberta sob demanda; apenas os objetos que ela usa já existem prontos). **Ainda pendente** — só a reorganização de pacote foi feita nesta fase.
4. Regras de segurança já estabelecidas em `AI-CHAT-MASTER-PLAN.md` continuam valendo e são herdadas por esta spec sem modificação: toda consulta gerada pela IA passa pelo mesmo `SqlRiskAnalyzer` usado na execução manual; nenhuma senha de conexão é exposta na saída de uma tool.
5. `ui.ai.*` (a camada de apresentação, 7 arquivos: `ChatWindow`, `ChatPanel`, `ChatController`, `ChatActions`, `MessageRenderer`, `IdeContextAccessor`, `AiSettingsDialog`) foi movida para `modulos.iachat.apresentacao` — renomeação mecânica de pacote, mesma classe de risco que o resto do módulo (verificada por `mvn clean test`), sem mudança de comportamento.

## Exemplos bons

Este módulo, hoje, é o exemplo positivo citado por quase todas as outras specs (`03` a `10`) — usar `DefaultAgent`/`ContextProvider`/`IdeContextAccessor` como referência ao revisar se um módulo novo está de fato seguindo DI por construtor e erro-como-saída-tipada.

## Critério de aceite

- [ ] `LLMProvider` e as 6 implementações permanecem sem alteração de lógica, apenas de pacote.
- [ ] O grafo de objetos da IA é construído no composition root, não mais dentro de `MainWindow.openAiChat()`.
- [ ] `ChatHistoryStore` usa a infraestrutura compartilhada de armazenamento chave=valor de [08-modulo-historico-consultas-sessao.md](08-modulo-historico-consultas-sessao.md), lendo sem perda os arquivos de histórico de conversa já existentes.
- [ ] Comportamento do chat (streaming, tool-calling, histórico persistente, configuração via ⚙) idêntico ao atual.
