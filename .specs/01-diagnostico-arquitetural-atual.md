# Diagnóstico Arquitetural Atual

## Objetivo

Registrar, com base em investigação direta do código-fonte, o estado real da arquitetura do Nureal Database IDE hoje — o que já está alinhado ao NDS, o que precisa mudar, e onde estão concentrados os maiores riscos de uma migração malfeita.

## Motivação

Nenhuma migração de arquitetura deve começar por suposição. Este documento existe para que as decisões em `02` a `13` tenham base em fatos verificados (nomes de arquivo, linhas aproximadas, imports), não em impressão geral de "o código está desorganizado".

## Resumo executivo

| Área | Veredito |
|---|---|
| `core.ai/*` (agente, providers, tools, contexto) | **Já é o módulo mais próximo do padrão-alvo.** Interfaces limpas, DI por construtor, erros como saída tipada. Formalizar, não reescrever. |
| `core.dialect` (`DatabaseDialect` + `MySqlDialect`) | **Já é um bom contrato de domínio.** Um único ponto de extensão multi-banco, como o próprio README do projeto já documentava. |
| `core.metadata.model.*` | **Já são bons `record`s de domínio**, livres de framework. |
| `ui.components` (`NButton`, `NCard`, `NTheme`, etc.) | Design system real e em uso (não decorativo), mas incompleto — boa base para formalizar. |
| `ui.ai` (`ChatWindow`, `ChatController`, `IdeContextAccessor`) | Camada de interface bem desenhada, com um exemplo real de inversão de dependência (`IdeStateAccessor`). |
| `MainWindow.java` (3996 linhas) | **God class crítico.** Mistura 13+ responsabilidades, incluindo um motor de execução SQL via JDBC cru. |
| `GridEditController.java` (568 linhas) | **Motor de CRUD transacional inteiro** vivendo como classe de UI, com JDBC direto. |
| `ObjectExplorerController.java` (1393 linhas) | Duplica, informalmente, um "serviço de execução de consulta de leitura" usado por várias dialogs administrativas. |
| Armazenamento em arquivo plano (`ConnectionStore`, `FormatPreferences`, `ExecutionHistoryStore`, `SavedQueryStore`, `SessionStore`, `ChatHistoryStore`) | Seis implementações quase idênticas de persistência em arquivo, sem contrato/porta comum. |
| `App.java` | Bootstrap puro — **não existe composition root**; objetos são criados sob demanda dentro do `MainWindow`. |

## O que já está alinhado ao NDS (preservar)

### `DatabaseDialect` como contrato de domínio ([01-Arquitetura/07-persistencia.md](../../nureal-development-standard/01-Arquitetura/07-persistencia.md), [01-Arquitetura/01-arquitetura-em-camadas.md](../../nureal-development-standard/01-Arquitetura/01-arquitetura-em-camadas.md))

Uma interface, uma implementação (`MySqlDialect`), ~60 métodos que retornam texto de SQL/consultas sem efeito colateral. Já documentado no próprio código como "Postgres/SQL Server/Oracle entram depois sem alterar o resto do app" — exatamente o espírito do ponto de extensão multi-banco que o NDS pede via contratos de domínio.

### `core.ai` como quase-Backbone Pattern ([01-Arquitetura/02-backbone-pattern.md](../../nureal-development-standard/01-Arquitetura/02-backbone-pattern.md))

`DefaultAgent` recebe `LLMProvider`, `ContextProvider`, `ToolExecutor`, `ChatHistoryStore` via construtor (DI real, sem service locator). `chat(conversationId, userMessage, onEvent)` funciona como um Input → Handler → Output(stream de `AiEvent`), com erros de negócio (`ProviderException`, `ToolResult.failure`) retornados como dado tipado, nunca lançados até o chamador. `LLMProvider` + `AbstractStreamingProvider` + 6 implementações (`Claude`, `Gemini`, `Ollama`, `OpenAi`, `OpenRouter`, `OpenAiCompatible`) formam um Strategy pattern genuíno. Ver detalhamento em [11-modulo-ia-chat.md](11-modulo-ia-chat.md).

### `IdeStateAccessor` como exemplo real de inversão de dependência

`core.ai.context.IdeStateAccessor` é a porta; `ui.ai.IdeContextAccessor` é o adaptador que lê `ConnectionManager`/`ExecutionHistoryStore`/`MetadataService`. Este é o modelo a generalizar para todo o resto do app (ex.: a execução de SQL deveria seguir a mesma forma "core define a porta, ui implementa o adaptador").

### Utilitários de domínio puro sem dependência de framework

`core.safety.SqlRiskAnalyzer`, `core.sql.*` (`SqlStatementLocator`, `SqlStatementSplitter`, `SqlTypeKind`, `TableAliasGenerator`, `UnquotedDateGuard`), `core.csv.CsvUtil`, `core.ddl.NormalizationAdvisor` — todos estáticos/sem estado, zero import de framework. São candidatos diretos a "domínio" ou "serviço de domínio" sem qualquer mudança estrutural, apenas realocação de pacote.

### Hierarquia de renderers de célula (`ui/*CellRenderer.java`)

`AbstractTypedCellRenderer` como base + 6 subclasses pequenas e específicas + `RendererFactory` como único ponto de decisão — um template method bem aplicado, sem duplicação. Preservar como está.

## O que precisa mudar

### 1. `MainWindow.java` — 3996 linhas, 13 responsabilidades misturadas

Seções identificadas (linhas aproximadas): ciclo de vida de janela/keep-alive (~312–450), checagem de atualização (~451–604), preferências de formatação (~635–695), montagem de toolbar/menu (~735–1175), estado de layout/zoom/densidade (~1175–1540), status de conexão/contexto de workspace (~1540–1690), sidebar/object-explorer host (~1718–1962), gestão de abas do editor (~1962–2340), persistência de sessão (~2338–2549), **composition root improvisado da IA** (~2664–2905), alternância de tema (~2906–2972), **motor de execução SQL via JDBC cru** (~2972–3600: `connectTo`, `executeStatements`, `buildStatementResult`), consultas salvas/histórico (~3590–3757), encerramento (~3757–3823), **adaptador JDBC→modelo de grade** (~3843–3945: `createModel`, `readJdbcMeta`, `resolveColumnClass`, `appendPage`, classe aninhada `ResultCursor`).

O núcleo do problema: `executeStatements`/`buildStatementResult`/`createModel`/`readJdbcMeta`/`appendPage` formam um **motor de execução de lote de instruções SQL com paginação de cursor**, implementado com `java.sql.*` cru dentro da classe de janela. Isto é lógica de aplicação (caso de uso "executar instruções SQL") sem nenhum equivalente em `core/` hoje. Ver extração completa em [06-modulo-execucao-e-edicao-de-grade.md](06-modulo-execucao-e-edicao-de-grade.md).

### 2. `GridEditController.java` — motor de CRUD transacional em `ui/`

568 linhas: rastreio de célula suja, `executeInsert`/`executeUpdate`/`executeDelete` construindo `PreparedStatement` via `DatabaseDialect`, `safeRollback`, recuperação de chave gerada, binding de PK — tudo com `java.sql.Connection/PreparedStatement/ResultSet/SQLException` importado diretamente numa classe de pacote `ui`. É o segundo pior caso de vazamento de infraestrutura para a UI depois do `MainWindow`.

### 3. `ObjectExplorerController.java` — "serviço de execução de leitura" informal

`runQuery`/`runQueryWithColumns` (via as interfaces funcionais `QueryRunner`/`ColumnQueryRunner`) são usados por `ProcessListDialog`, `ServerStatusDialog`, `EventsReplicationDialog`, `UserManagementDialog` — ou seja, um controlador de UI virou, na prática, o serviço de consultas administrativas de meia dúzia de dialogs. `generateSelect` (linha ~994) monta texto de SQL inline, duplicando responsabilidade que deveria estar em `core.sql`/`core.dialect`.

### 4. Seis implementações quase idênticas de armazenamento em arquivo, sem contrato

`ConnectionStore`, `FormatPreferences`, `ExecutionHistoryStore`, `SavedQueryStore`, `SessionStore` (em `core`) e `ChatHistoryStore` (em `core.ai.history`) reimplementam, cada uma, o mesmo formato chave=valor em arquivo dentro de `~/.nureal-ide/`, sem nenhuma interface comum. Nenhuma delas tem porta (`Repository`) que permita a um caso de uso depender de uma abstração em vez do arquivo concreto.

### 5. Vazamento de `java.sql.*` para dentro de `ui/`

Import scan confirma `java.sql.*` em: `MainWindow`, `GridEditController`, `ObjectExplorerController`, `ObjectDdlActions`, `ResultsAreaController`, `FkInspectorWindow`, `TableMetadataCache`, `ObjectDataTransfer`, `ConnectionEditDialog`, `TemporalCellEditor`, `TemporalCellRenderer`, `CellContentViewer`, `BinaryCellRenderer`. Ponto positivo: nenhum `DriverManager` direto em `ui/` — a abertura de conexão já é centralizada em `ConnectionManager`. O problema é execução de `Statement`/`PreparedStatement`/`ResultSet` crus depois de obter a conexão, não a obtenção da conexão em si.

### 6. Vazamento de framework para dentro de `core/`

- `core.autocomplete.SqlCompletionProvider` estende `org.fife.ui.autocomplete.DefaultCompletionProvider` (tipo de biblioteca de UI) diretamente dentro de `core`.
- `core.editor.EditorUndoManager` estende `javax.swing.undo.UndoManager`, com `JTextComponent`/`Document`/`CaretEvent` — Swing dentro de `core`.
- `core.export.ExcelExporter` depende de `javax.swing.table.TableModel` (Swing) além do Apache POI.
- `core.metadata.MetadataService` e `core.connection.ConnectionManager`/`SessionInitializer` operam diretamente sobre `java.sql.Connection`/`SQLException`, sem nenhuma interface de repositório separando domínio de infraestrutura.

### 7. Duplicação mecânica em dialogs (12+ arquivos)

Nenhuma dialog (`ConnectionDialog`, `ConnectionEditDialog`, `CsvImportDialog`, `RoutineBuilderDialog`, `TriggerBuilderDialog`, `ViewBuilderDialog`, `BackupRestoreDialog`, `EventsReplicationDialog`, `ExplainDialog`, `ProcessListDialog`, `ServerStatusDialog`, `UserManagementDialog`, `DdlAssistantDialog`) estende uma base comum — cada uma reimplementa manualmente `new JDialog(owner, title, modality)`, layout de rodapé com botões, e exibição de erro de validação. Único utilitário compartilhado hoje: `DialogUtil.owner(Component)` (resolve o ancestral correto para centralizar). Baixo risco (mecânico), mas alto volume.

### 8. `App.java` não é um composition root

109 linhas: apenas bootstrap de Look-and-Feel, fonte, cursor global, e `new MainWindow().setVisible(true)`. Nenhum `ConnectionManager`, `MetadataService`, `AiPreferences` é construído aqui — tudo é instanciado sob demanda, em vários pontos, dentro do `MainWindow` (o pior exemplo: o grafo de objetos inteiro da IA só é criado na primeira vez que `openAiChat()` roda). Ver [13-composition-root-e-bootstrap.md](13-composition-root-e-bootstrap.md).

## O que já foi tentado (precedente a respeitar)

- `ObjectDdlActions.java` foi extraído de `ObjectExplorerController` explicitamente por causa de um limite de 1200 linhas citado no próprio comentário de cabeçalho do arquivo — evidência de que extração incremental já foi tentada informalmente antes deste processo formal.
- `SPEC-0008 — Architecture Cleanup` (histórico do Git, `.specs/` estava vazio no momento desta escrita mas o commit existe) já definia: nunca alterar comportamento durante refatoração, extrair por categoria (dead code, duplicatas, god classes, dialogs, utils, renderers), um commit por categoria, meta de "uma funcionalidade exigir no máximo 5 arquivos para ser entendida". Esse espírito é totalmente compatível com o NDS e deve ser preservado como prática operacional dentro da migração (ver [14-plano-de-migracao-fases.md](14-plano-de-migracao-fases.md)).
- `docs/002-Engineering-Principles.md`, `docs/005-Coding-Standards.md`, `docs/008-Dependency-Rules.md`, `docs/010-Development-Workflow.md` já articulam, em forma de esboço (1–8 linhas cada), princípios praticamente idênticos aos do NDS ("UI apenas apresentação", "Service First", "UI nunca acessa Provider diretamente"). Estas specs formalizam e expandem esses esboços — não os contradizem.

## Anti-patterns confirmados no código

- **God class multi-responsabilidade**: `MainWindow.java`.
- **Motor de negócio disfarçado de classe de UI**: `GridEditController`, partes de `MainWindow`, partes de `ObjectExplorerController`.
- **Repositório sem contrato**: as seis stores de arquivo plano.
- **Composition root ausente / DI lazy ad-hoc**: construção do grafo de objetos da IA dentro de `MainWindow.openAiChat()`.
- **Vazamento de framework para `core/`**: `SqlCompletionProvider`, `EditorUndoManager`, `ExcelExporter`.
- **Duplicação mecânica**: esqueleto de `JDialog` repetido em 10+ arquivos.

## Checklist deste diagnóstico

- [ ] Toda decisão de módulo em `03`–`13` cita a seção correspondente deste diagnóstico como evidência?
- [ ] Nenhuma parte "já alinhada" (dialect, ai, metadata models, renderers) está sendo redesenhada sem necessidade?
- [ ] O plano de migração em `14` prioriza os itens de maior risco (`MainWindow`, `GridEditController`) com módulo piloto antes de propagar o padrão?
