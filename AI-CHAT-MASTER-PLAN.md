# Master Plan — Evolução do Chat com IA (Nureal Database IDE)

> Este documento é a ÚNICA fonte de instrução para as próximas 4 fases de
> trabalho no módulo `core.ai`/`ui.ai`. Ele existe pra que baste UMA instrução
> do usuário ("execute o AI-CHAT-MASTER-PLAN.md") pra todo o trabalho rodar
> na ordem certa, com os gates certos, sem precisar de esclarecimento extra.
>
> Este arquivo NÃO substitui `.claude/CLAUDE.md`, `.claude/JAVA.md`,
> `.claude/UI.md`, `.claude/GIT.md` e `.claude/DATABASE.md` — todos continuam
> valendo em TODA fase, sem exceção. Este arquivo só adiciona a sequência e o
> escopo específico deste trabalho.

## 0. Meta-regras (valem para as 4 fases, sem exceção)

1. Antes de qualquer fase: `git status` limpo → commit de checkpoint
   (`checkpoint: antes da Fase N`) — ver `.claude/GIT.md`. Nunca `push`,
   nunca `merge`, nunca `rebase` automático.
2. Cada fase termina com o formato de `.claude/DEVELOPMENT.md`
   (Planning / Implementation / Validation / Result) antes de começar a
   próxima. Não pule a validação (`mvn -DskipTests package` no mínimo;
   `mvn test` quando a fase mexer em `core.ai`).
3. Reaproveitar serviços existentes é obrigatório, nunca duplicar:
   `SqlRiskAnalyzer`, `QueryRunner`, `GridExporter`/`ExcelExporter`,
   `AiPreferences`, `ChatHistoryStore`, `ProviderType`. Se parecer que
   precisa duplicar algo, PARE e pergunte antes de criar a duplicata.
4. Nenhuma fase altera a camada `core.ai.provider` (LLMProvider e as 5
   implementações) — está estável e fora de escopo deste plano.
5. Se qualquer fase exigir decisão de produto não coberta aqui
   (nome de botão, cor, texto exato), decida com o padrão visual já
   existente no app (ver `.claude/UI.md`) em vez de inventar um novo
   estilo — e registre a decisão no "Result" da fase.
6. Ordem de execução é FIXA e não pode ser reordenada por conveniência:
   **Fase 1 → Fase 2 → Fase 4 → Fase 3**. A Fase 3 tem um gate humano
   obrigatório (seção 3.0) — nenhuma exceção.

---

## Fase 1 — `ExecuteSqlTool` + tabela de resultado no chat

**Objetivo**: o chat responde perguntas quantitativas ("quais tabelas têm
mais de 1 milhão de registros") com uma tabela de dados real, não texto.

**Arquivos a criar**
- `core/ai/tool/ExecuteSqlTool.java` — implementa `Tool`. Reutiliza o
  mesmo caminho de execução do editor (o executor por trás de
  `QueryRunner`/`onRun` em `MainWindow`), não um novo `Connection`/
  `Statement` paralelo.

**Arquivos a alterar**
- `core/ai/tool/ToolResult.java` — sem mudança de schema (o campo
  `structuredData` já existe); passar a preencher com um objeto
  `QueryResult`-like (colunas + linhas + tempo de execução).
- `ui/ai/MessageRenderer.java` — novo tipo de card: tabela (`JTable`
  compacta, tema igual ao `ResultGrid`/`GridTheme` existente) com dois
  botões: **"Ver SQL"** (expande/colapsa a query que a tool rodou) e
  **"Exportar resultado"** (chama `GridExporter`/`ExcelExporter` já
  existentes — não reimplementar exportação).
- `core/ai/prompt/PromptComposer.java` — instruir o modelo a preferir
  `ExecuteSqlTool` para perguntas quantitativas/analíticas sobre dados ou
  metadados quando `ListTablesTool`/`DescribeTableTool` não bastarem.
- `ui/MainWindow.java` linha ~2458 — registrar a nova tool na lista
  passada ao `ToolExecutor` (`List.of(new ListTablesTool(...),
  new DescribeTableTool(...), new ExecuteSqlTool(...))`).

**Regras de segurança (não negociáveis)**
- Toda query gerada pela IA passa pelo MESMO `SqlRiskAnalyzer` usado pelo
  editor. Comandos de risco (`DELETE`/`UPDATE` sem `WHERE`, `DROP`,
  `TRUNCATE`, `ALTER`/`CREATE`/`RENAME`) exigem a MESMA confirmação visual
  que já existe — a IA nunca ganha um atalho que o usuário humano não tem.
- `ExecuteSqlTool` deve limitar linhas retornadas (mesma paginação/limite
  que o `ResultGrid` já usa) para não estourar o contexto do modelo nem a
  UI com resultados gigantes.
- Nunca expor senha de conexão em `structuredData` ou `content` (ver
  `docs/045-Connection-Context.md`).

**Critério de aceite**
- Perguntar no chat "quais tabelas têm mais de 1 milhão de registros?"
  produz uma tabela real (nome, registros, tamanho, atualização) com "Ver
  SQL" e "Exportar resultado" funcionando — igual ao mockup avaliado.
- Testes: cobrir `ExecuteSqlTool` com mock de conexão (sucesso, erro de
  SQL, comando de risco bloqueado) seguindo o padrão de
  `DefaultAgentTest`/`ClaudeProviderTest`.

---

## Fase 2 — Chat como aba integrada (fim do `JDialog` flutuante)

**Objetivo**: "Chat com IA" deixa de ser uma janela separada e passa a
viver dentro da área principal, com cabeçalho equivalente ao mockup
(seletor de modelo, "Novo Chat").

**Arquivos a alterar**
- `ui/ai/ChatWindow.java` — deixa de instanciar `JDialog`; o `ChatPanel`
  passa a ser exposto como componente reaproveitável embutível (manter a
  classe se ainda fizer sentido como fábrica, mas o destino muda).
- `ui/MainWindow.java` — novo destino para o `ChatPanel`: uma aba dentro
  de `editorTabs` (mesmo `JTabbedPane` das queries) OU um novo "card" que
  ocupa a área principal — **decidir isso é a única pergunta que esta
  fase pode fazer ao usuário antes de começar**, já que o mockup mostra
  ocupando a área principal inteira, não a área de resultado.
- `ui/ai/ChatPanel.java` — adicionar ao `NToolbar` do topo: combo de
  modelo (reaproveitar `ProviderType`/`AiPreferences` já usados em
  `AiSettingsDialog`, não criar enum novo), botão "Novo Chat" (gera novo
  `conversationId`, já suportado por `ChatHistoryStore`), badge de
  esquema ativo ("Alterar contexto" — reaproveitar o que
  `ConnectionStatusCard`/`AgentContext` já sabem).

**Critério de aceite**
- Abrir o chat não abre mais janela separada; ele aparece na área
  principal com modelo atual visível e "Novo Chat" funcional, mantendo
  histórico por `conversationId` como já funciona hoje.

---

## Fase 4 — Polimento de UX do chat

**Objetivo**: presets de prompt (Perguntar/SQL/Explicar/Otimizar/
Documentar) e anexos, igual ao rodapé do mockup.

**Escopo**
- Os 4 botões de presets (SQL/Explicar/Otimizar/Documentar) são atalhos
  de PROMPT, não tools novas — cada um pré-formata a mensagem enviada ao
  `Agent` existente (ex.: "Otimizar" empacota o SQL da aba ativa + pede
  sugestão de índice), sem novo caminho de execução.
- Anexos: reaproveitar o que já existe (SQL da aba ativa, grid de
  resultado atual) antes de inventar upload de arquivo arbitrário — se o
  mockup implicar upload de imagem/arquivo livre, tratar como sub-tarefa
  separada e perguntar antes de implementar (fora do escopo original do
  roadmap avaliado).

**Critério de aceite**
- Os presets alteram o conteúdo enviado ao Agent de forma visível e
  previsível (mostrar ao usuário o texto final antes de enviar, não
  enviar "mágica" escondida).

---

## Fase 3 — Sidebar unificada (árvore WORKSPACE + OBJETOS + FERRAMENTAS)

> ⚠️ **GATE OBRIGATÓRIO**: esta fase reverte uma decisão de design já
> documentada no próprio código (`MainWindow.buildLeftSide`, comentário
> sobre trocar rail+CardLayout por árvore única foi escolha consciente,
> ciente do tradeoff). **Claude Code NÃO deve iniciar esta fase sem
> confirmação explícita do usuário na conversa**, mesmo que este plano
> tenha sido "aprovado" de forma geral antes. Pergunte, literalmente:
> "Fase 3 reverte a decisão de rail+CardLayout documentada em
> buildLeftSide — confirma que quer essa reversão?" e espere resposta.

**Objetivo (se confirmado)**: substituir o rail de ícones + `CardLayout`
por uma árvore única e sempre visível (`WORKSPACE`: SQL Editors,
Consultas Salvas, Histórico, Favoritos, Chat com IA · `OBJETOS`: schemas/
tabelas/views/etc já existentes em `objectExplorer` · `FERRAMENTAS`:
Backup, Importar/Exportar Dados, Usuários e Privilégios, Monitor de
Conexão), com busca "Ctrl+K".

**Arquivos a alterar**
- `ui/MainWindow.java` — reescrever `buildLeftSide()` por completo.
- Manter `ConnectionStatusCard` sempre visível no topo (isso já está
  correto no mockup e no código atual — não mexer).
- Itens hoje desabilitados ("Favoritos", "Config.") continuam desabilitados
  até terem feature real por trás — não fingir contadores (o "7" do
  mockup ao lado de Favoritos não deve aparecer sem a feature existir).

**Critério de aceite**
- Árvore única visível, com os 3 grupos, busca funcional, sem perda de
  nenhuma funcionalidade hoje acessível via rail (conexões, objetos,
  consultas salvas, histórico).

---

## A instrução única

Depois deste arquivo estar no repositório, a única instrução que o
usuário precisa dar ao Claude Code é:

> "Execute o AI-CHAT-MASTER-PLAN.md a partir da Fase 1."

Nenhum outro contexto é necessário — este arquivo, mais os 5 arquivos de
`.claude/`, contêm todas as regras, ordem, arquivos afetados e critérios
de aceite necessários para as 4 fases.
