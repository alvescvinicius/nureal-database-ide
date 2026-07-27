# Módulo: IA Chat (`modulos.iachat`)

## Responsabilidade

Chat com IA integrado à IDE: mantém uma conversa com um modelo de linguagem (Claude, Gemini, Ollama, OpenAI ou compatível, OpenRouter), com acesso somente-leitura ao estado da conexão/schema/editor ativos e a um conjunto de ferramentas (`Tool`) que permitem à IA listar tabelas, descrever colunas e executar SQL de leitura — sempre validado pelo mesmo `SqlRiskAnalyzer` usado na execução manual.

## Estrutura interna

```
dominio/
  contratos/    Agent, LLMProvider, ContextProvider, Tool, Specialist, IdeStateAccessor, ConversationSession
  entidades/    AgentContext e sub-contextos, ChatMessage/ChatRequest/ChatResponse/ChatUsage,
                AiEvent, ToolCall, ToolSpec, ToolRequest, ToolResult, ProviderException, ProviderType
aplicacao/      DefaultAgent, DefaultContextProvider, PromptComposer, ToolExecutor
infraestrutura/
  provider/     ClaudeProvider, GeminiProvider, OllamaProvider, OpenAiProvider, OpenRouterProvider,
                OpenAiCompatibleProvider, AbstractStreamingProvider, GenericSession, SseUtil
  tool/         ListTablesTool, DescribeTableTool, ExecuteSqlTool, SqlQueryResult
  specialist/   MySqlSpecialist, SpecialistRegistry
  ChatHistoryStore, AiCredentialsStore, AiPreferences, LLMProviderFactory
apresentacao/   ChatWindow, ChatPanel, ChatController, ChatActions, MessageRenderer,
                IdeContextAccessor, AiSettingsDialog   (movidos de com.nureal.ide.ui.ai)
```

## Expõe (portas públicas)

- `Agent` — `chat(conversationId, mensagem, onEvent)` e `cancel(turnId)`. Única porta que `apresentacao` consome; `MainWindow` (fora do módulo) monta o `Agent` hoje dentro de `openAiChat()` e injeta na janela.
- `IdeStateAccessor` — contrato que `apresentacao.IdeContextAccessor` implementa para dar ao módulo acesso somente-leitura ao estado vivo da IDE (conexão ativa, schema, editor), sem o `dominio`/`aplicacao` deste módulo depender de Swing (só `apresentacao` depende).

## Publica / Consome

Não publica nem consome eventos (ver [01-Arquitetura/06-eventos.md](../../../../../../../../nureal-development-standard/01-Arquitetura/06-eventos.md) do NDS) — a comunicação com `MainWindow` (fora do módulo) é síncrona via `Agent`/`IdeStateAccessor`.

## Dependências

- `conexoes` (via `ConexaoAtivaPort`, indiretamente através de `IdeStateAccessor`) e `metadados` (via `MetadataRepository`) — usados pelas Tools (`ListTablesTool`, `DescribeTableTool`, `ExecuteSqlTool`).
- `compartilhado/persistencia` — `ChatHistoryStore` já usa `ArquivoChaveValorUtil` (encode/decode Base64 e parseLong) para o parsing de campo, compartilhado com `ConnectionStore`/`ExecutionHistoryStore`/`SavedQueryStore`/`SessionStore` (ver spec 08).

## Lacunas conhecidas (ver `.specs/11-modulo-ia-chat.md` para detalhe completo)

1. Composition root: o grafo de objetos deste módulo ainda é montado dentro de `MainWindow.openAiChat()`, não em `App.java`.
2. `LLMProvider.createSession()` (contrato de domínio) tem um método `default` que instancia `GenericSession` (infraestrutura) diretamente — funcionava por acidente enquanto ambos estavam no mesmo pacote; hoje `GenericSession` precisou virar pública para o `default` continuar compilando. O acoplamento em si não foi resolvido.
