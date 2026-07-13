# Design System — Nureal Database IDE

Este documento é a fonte única de verdade das regras visuais da aplicação,
pedida na revisão de padronização visual: *"criar um Design System para a
Nureal... em vez de desenhar telas, você passa a definir regras para os
componentes... cada nova funcionalidade reutiliza essas regras"*.

Qualquer componente novo deve reutilizar o que está aqui, nunca inventar uma
cor/fonte/raio/espaçamento próprio. Se uma regra abaixo não cobrir um caso
novo, o certo é **estender a regra** (adicionar um campo em `GridTheme`, um
método em `Typography`/`Buttons`), não criar uma exceção isolada.

Organizado pelas mesmas 15 seções da revisão original, cada uma com: a regra,
onde ela mora no código, e o status atual.

---

## 1. Tipografia

Três níveis, nunca escolhidos "no olho" por chamada — ver `Typography.java`:

| Nível | Uso | Peso | Cor |
|---|---|---|---|
| `Typography.primary(c)` | títulos, cabeçalhos de painel, nome de conexão/tabela, botões principais | Bold | `GridTheme.HEADER_FOREGROUND` (alto contraste) |
| `Typography.secondary(c)` | listas, árvores, grade, editor, menus | Regular | `GridTheme.COLOR_TEXTUAL` |
| `Typography.tertiary(c)` | informação auxiliar, descrição, status, placeholder | Regular | `GridTheme.MUTED_TEXT` (mais discreta) |

O **tamanho** do texto continua escolha de cada chamador (um título pequeno em
versalete de painel lateral e uma mensagem grande de estado vazio podem ser
igualmente "primário" em peso/contraste, só diferem no tamanho pelo
contexto) — `Typography` normaliza peso+cor, não tamanho.

**Status:** ✅ aplicado nos 4 títulos de painel (CONEXÕES/HISTÓRICO/QUERIES
SALVAS/OBJETOS/RESULTADOS). Labels mais contextuais (mensagens de estado
vazio, overlay de execução, rótulos de árvore com regra própria de negrito
condicional) ainda escolhem peso/cor pontualmente — não são bugs, são casos
com lógica própria documentada no código, mas podem migrar para `Typography`
se algum dia precisarem de ajuste em conjunto com o resto.

---

## 2. Sistema de cores

Fonte única: `GridTheme.java` (`applyPalette(boolean dark)` troca a paleta
inteira de uma vez). Campos principais:

- **Fundo/painéis:** `HEADER_BACKGROUND`, `ZEBRA_EVEN`/`ZEBRA_ODD` (editor e
  grade têm paleta própria — ver seção 11).
- **Hover:** `HOVER_BACKGROUND` (mesmo tom em grade, árvore, lista — ver
  `TreeHoverTracker`/`SelectionManager`).
- **Seleção:** `SELECTION_BACKGROUND` / `SELECTION_FOREGROUND` (mesmo tom em
  `FlatLaf.properties` para List/Tree/Table/Menu e em `GridTheme` para a
  grade — um só valor decide "qual é a cor de seleção do app").
- **Bordas:** `HEADER_BORDER`, `GRID_LINE`.
- **Texto:** `HEADER_FOREGROUND` (primário), `COLOR_TEXTUAL` (secundário),
  `MUTED_TEXT` (terciário), `COLOR_NULL` (valor nulo na grade).
- **Verde (marca/ação):** `MainWindow.ACCENT` — reutilizado em: botão
  primário (`Buttons.stylePrimary`), botão Executar, dot de conexão
  conectada, badges "ativo/aprovado". Atualizado nesta rodada para o verde
  institucional real da marca Nureal, `#1E9147` (era `#059669`, um
  verde-esmeralda genérico sem relação com a logo) — extraído visualmente da
  logo fornecida pelo usuário (não é um hex oficial de manual de marca; se um
  valor oficial aparecer depois, é uma troca de uma linha só, ver
  `MainWindow.ACCENT`). Auditado: nenhum literal duplicado (`new Color(0x...)`)
  fora deste único ponto — `Buttons`/`ConnectionsPanel`/
  `ObjectTreeCellRenderer`/`ResultStatusBar` já reutilizavam `ACCENT`.
  **Correção nesta rodada:** a auditoria anterior só cobriu código Java —
  `FlatLaf.properties` (`@accentColor`, `Component.focusColor`,
  `TextComponent.selectionBackground` claro/escuro) ainda tinha o verde
  antigo (`#059669`), já corrigido para `#1E9147`. Dois pontos de verdade
  (Java le `MainWindow.ACCENT`, FlatLaf le este arquivo) porque não há como
  o tema FlatLaf referenciar uma constante Java em tempo de build.
- **Amarelo institucional da marca:** decisão explícita do usuário — fica
  restrito ao ícone/logo da aplicação, **não** entra na UI de trabalho. O
  amarelo já em uso em `GridTheme` (`HEADER_HIGHLIGHT_BORDER`/
  `HEADER_HIGHLIGHT_BACKGROUND`) continua com seu significado atual de
  ALERTA — misturar "amarelo de marca" com "amarelo de aviso" no mesmo lugar
  confundiria os dois sinais.
- **Vermelho (erro):** `COLOR_LOGIC_FALSE` — dot de desconectado, badges
  "inativo/cancelado", `JComponent.outline=error` em campos inválidos.
- **Amarelo (alerta):** `HEADER_HIGHLIGHT_BORDER`/`HEADER_HIGHLIGHT_BACKGROUND`
  — dot de "conectando", destaque de coluna encontrada na grade, badges
  "pendente".
- **Azul (informação):** ainda **não existe** um campo semântico dedicado
  (gap conhecido — ver "Pendências" no fim deste documento).

**Regra prática:** todo novo `new Color(0x......)` fora de `GridTheme` deve
ser questionado — na maioria das vezes já existe um campo equivalente.
Exceções intencionais (não violam a regra, são subsistemas próprios e
documentados como tal no código): `SqlEditorPane` (paleta de syntax
highlight, estilo VS Code — seção 11 pede para não mexer além de
contraste), `IconTheme` (verde/amarelo/vermelho fixos para GLIFOS de ícone,
regra estrita e documentada, independente de tema), paleta categórica de
badges (`GridTheme.badgeColorsFor`) e a paleta de identidade de conexão
(`ConnectionsPanel.WORKSPACE_PALETTE`) — ambas propositalmente **grandes e
variadas**, pois representam categorias escolhidas pelo usuário, não tons de
cinza da interface.

**Correção nesta rodada:** o fallback por hash de `badgeColorsFor` (usado
para qualquer valor textual "tipo enum" sem palavra-chave conhecida — um
código, uma sigla) podia cair no índice 0 (vermelho) ou 3 (verde) do
`BADGE_PALETTE` só por coincidência de hash, dando uma falsa impressão de
erro/sucesso onde não havia nenhum (relato do usuário: "ainda tem coisas com
verde e vermelho indevido"). Agora o fallback usa `HASH_FALLBACK_INDEXES`
(um subconjunto do array sem os índices 0 e 3) — vermelho/verde só aparecem
quando o valor bate com uma palavra conhecida em `BADGE_KEYWORDS` (sinal
real), nunca por acaso.

**Status:** ✅ chrome/status/texto consolidados; ⚠️ ícones e badges
permanecem paletas próprias por design.

---

## 3. Componentes (raio, altura, espaçamento, hover, foco)

- **Raio de borda:** `8px` em tudo — `Component.arc`/`Button.arc`/
  `TextComponent.arc = 8` em `FlatLaf.properties`, e todo `putClientProperty`
  pontual usa `"arc: 8"` (nunca outro valor).
- **Botões:** três papéis, um método cada em `Buttons.java`:
  - `styleSecondary` — contorno fino, cantos arredondados, sem preenchimento
    (a maioria dos botões do app).
  - `stylePrimary` — preenchido na cor da marca (`ACCENT`), só ação
    principal/confirmação.
  - `styleIconButton` — só ícone, plano, mesma margem (`4,4,4,4`) em toda
    barra de ferramentas/cabeçalho/find-bar do app (antes cada barra tinha a
    sua própria margem: 5,5,5,5 / 4,4,4,4 / 3,3,3,3).
- **Campos:** ver seção 10.
- **Foco:** `Component.focusWidth/innerFocusWidth/focusColor` globais em
  `FlatLaf.properties` — mesmo anel de foco em qualquer input do app.

**Status:** ✅ resolvido para botões/campos/raio. Árvore/lista/grid: ver
seções 6, 7, 13.

---

## 4. Espaçamento

**Escala formal (nova, pedida na revisão de refinamento visual premium):**
`Spacing.java` — 6 passos únicos, nunca um número solto:

| Constante | Valor | Uso típico |
|---|---|---|
| `Spacing.XS` | 4px | espaçamento mínimo (ícone colado ao texto, margem vertical de botão) |
| `Spacing.SM` | 8px | o "passo" mais comum — padding interno, gap entre controles da mesma barra |
| `Spacing.MD` | 12px | agrupamento de seções dentro do mesmo painel/separação entre grupos de botões |
| `Spacing.LG` | 16px | separação entre blocos/painéis distintos |
| `Spacing.XL` | 24px | margens externas de diálogo/janela |
| `Spacing.XXL` | 32px | respiro grande — estados vazios, telas de destaque |

Qualquer tela nova (ou revisada a partir de agora) usa estas constantes em
vez de inventar um número próprio — mesmo espírito de `Typography`/
`GridTheme`, mas para espaçamento.

**Aplicado nesta rodada:** barra superior inteira (`MainWindow`, botão
Executar/Formatar/Explicar/Salvar/Histórico/ícones de layout) — todos os
`Insets`/margens que eram números soltos (3, 6, 10, 14) agora referenciam
`Spacing.XS/SM/MD/LG`. Como parte da troca, a margem vertical dos botões
caiu de 6 para `Spacing.XS` (4) — a barra ficou mais baixa, pedido explícito
da revisão ("reduzir altura" da barra superior).

**Ainda não migrado (número antigo, mas consistente dentro de si mesmo, não
aleatório):** o template de painel lateral abaixo continua com seus dois
números originais (8 externo, 6 interno) — o interno (6) não bate
exatamente com nenhum degrau da escala nova; migrar exige decidir se vira
`Spacing.XS` (4, mais apertado) ou `Spacing.SM` (8, igual ao externo, perdendo
a hierarquia "grupo mais unido") em 4 arquivos ao mesmo tempo — deixado para
uma próxima rodada dedicada aos painéis laterais, para não misturar duas
decisões (escala nova + qual grau escolher) numa mudança só.

```
painel: outer border (8,8,8,8), gap vertical 8
  bloco superior (título + busca): gap vertical 6
    título
    busca
  lista/árvore
```

Os quatro painéis usam exatamente esses dois números (8 externo, 6 interno)
— antes o painel de Objetos usava 8 no lugar do 6 interno, único fora do
padrão (corrigido).

**Status:** ⚠️ escala formal criada e aplicada à barra superior; demais
áreas (painéis laterais, diálogos, editor, grid) ainda usam os números
antigos — já consistentes dentro de si, mas não migrados para `Spacing`
ainda. Ver pendências no fim do documento.

---

## 5. Hierarquia visual

- **Árvore de Objetos:** Conexão → Schema (maiúsculo, dot de status) →
  Categoria → Objeto → Coluna, cada nível com peso/cor diferente
  (`ObjectTreeCellRenderer`), sem precisar ler o texto para saber o nível.
- **Editor SQL:** breadcrumb mostra `Conexão > Schema > Tabela (alias) >
  coluna`, sempre com o prefixo completo (antes só mostrava schema/tabela,
  nunca a conexão).
- **SQL syntax highlight:** hierarquia de cor VS Code Dark+ (keyword, string,
  número, comentário, função, identificador) — ver seção 11.

**Status:** ✅ resolvido.

---

## 6. Árvores (Conexões × Objetos)

Ainda são dois *widgets* Swing diferentes (`JList` vs `JTree`, pois Conexões
não tem uma hierarquia real de expandir/recolher), mas compartilham:

- **Hover:** `TreeHoverTracker` (mesmo mecanismo, mesma cor) nos dois.
- **Seleção:** `GridTheme.SELECTION_BACKGROUND/FOREGROUND` nos dois.
- **Altura de linha:** ambas passam por `scaledPx(...)` (zoom) a partir da
  MESMA base — `ConnectionsPanel.DEFAULT_ROW_HEIGHT` (`24`), única fonte de
  verdade para os dois componentes (ver `MainWindow#buildObjectBrowser`/
  `#refreshDynamicSizing`). Igualada numa rodada posterior: antes a árvore
  usava `22` e a lista `26` (a lista carregava ícone/dot e a árvore nem
  sempre), mas desde que a árvore ganhou ícone de tipo em toda linha abrivel
  (ver item de Ícones abaixo) as duas passaram a ter a mesma composição
  visual, então não fazia mais sentido manter alturas diferentes.
- **Estado vazio (busca sem resultado):** Conexões troca a lista por um card
  central (ícone + título + subtítulo, mesma receita de
  `MainWindow#buildEmptyState`/painel de Resultados) via `CardLayout`, com
  texto diferente para "nenhuma conexão cadastrada" vs. "busca sem resultado"
  (ver `ConnectionsPanel#updateEmptyState`). A árvore de Objetos, ao filtrar
  sem nenhum objeto encontrado, mostra uma linha sintética sem ícone/ação
  ("Nenhum objeto encontrado para ...", `NodeType.EMPTY_MESSAGE`) em vez de
  deixar só a raiz do schema sozinha e sem nenhuma pista.
- **Ícones:** árvore de Objetos **tinha sido deixada deliberadamente sem
  ícone** (pedido explícito antigo do usuário — "poluía", ver `ObjectTreeCellRenderer`
  javadoc, versão antiga) — **decisão revertida** numa rodada posterior, novo
  pedido explícito do usuário ("vamos melhorar e colocar ícones para tabelas,
  procedures, views etc"): categoria (Tabelas/Visualizações/Procedures/
  Functions/Triggers) e cada objeto dentro dela agora mostram um ícone de
  tipo (`IconType.TABLE/VIEW/PROCEDURE/FUNCTION/TRIGGER`, resolvido a partir
  de `ObjNode.kind()`), sempre na cor neutra `GridTheme.MUTED_TEXT` — SEM
  nenhum fundo colorido por categoria, que era o que "poluía" da vez
  anterior (ver `ObjectTreeCellRenderer#typeIcon`). Coluna continua sem
  ícone (o texto já mostra nome + tipo). A lista de Conexões usa um dot de
  status + ações de editar/excluir — diferença de conteúdo, não de padrão.
- **Fonte:** ambas herdam a fonte padrão do componente (nenhuma das duas
  define uma fonte própria fora do padrão).

**Status:** ✅ hover/seleção/altura/ícone de tipo/estado vazio unificados;
widget continua diferente (`JList` vs `JTree`) por causa do CONTEÚDO (árvore
vs lista de status), não por inconsistência de estilo.

---

## 7. Listas

A lista de Conexões segue o padrão visual do painel Objetos: mesmo
cabeçalho (seção 8), mesmo hover/seleção (seção 6), mesma ação de cabeçalho
(seção 8) — não parece mais "outra lista".

**Status:** ✅ resolvido.

---

## 8. Cabeçalhos

Todo painel lateral usa o mesmo layout de cabeçalho:

```
TÍTULO (Typography.primary, versalete pequeno)         [ações, ícone-só]
Campo de busca (placeholder + botão limpar)
```

- Título: mesmo tamanho/peso/alinhamento nos 4 painéis.
- Ações: sempre ícone-só (`Buttons.styleIconButton`) — Conexões usava um
  botão de TEXTO com contorno ("Nova"), único fora do padrão do painel de
  Objetos; convertido para ícone-só com o mesmo tamanho/cor.
- Histórico e Queries Salvas não têm ação de cabeçalho (não têm um conceito
  de "criar novo" — a ausência é esperada, não uma inconsistência).

**Status:** ✅ resolvido.

---

## 9. Barra superior

- Botão "Executar": verde institucional (`styleRunButton`, único botão
  preenchido da barra — a ação principal da tela).
- Todo o resto (mostrar/ocultar painel, resultados, layout, tema): ícone-só,
  neutro, `Buttons.styleIconButton` (mesma altura/raio/margem/tipografia).

**Status:** ✅ resolvido.

---

## 10. Campos de busca

Todo campo de busca/filtro do app (Conexões, Histórico, Queries Salvas,
Objetos, coluna/filtro da grade, filtro do Inspetor de FK) usa o mesmo par de
client properties do FlatLaf:

```java
field.putClientProperty("JTextField.placeholderText", "...");
field.putClientProperty("JTextField.showClearButton", true);
```

Fundo/borda/altura/foco vêm dos defaults globais de `FlatLaf.properties`
(`TextField.background`, `Component.arc`, `Component.focusWidth`) — nenhum
campo de busca sobrescreve isso individualmente.

**Status:** ✅ já satisfazia a regra (nenhuma divergência encontrada na
auditoria).

---

## 11. Editor SQL

Paleta própria (documentada como subsistema separado, não precisa reutilizar
`GridTheme` 1:1): estilo VS Code Dark+ no tema escuro (keyword `#569CD6`,
string `#CE9178`, número `#B5CEA8`, comentário `#6A9955`, função `#DCDCAA`,
fundo `#1E1E1E`) e o tema `default.xml` do RSyntaxTextArea no claro. Linha
atual, cursor e seleção calibrados para não cansar em uso prolongado
(`applyEditorPalette`/`refreshTheme` em `SqlEditorPane`).

**Status:** ✅ resolvido nesta rodada de revisões (contraste/hierarquia
ajustados); regra do documento é **não mexer mais** além de contraste.

---

## 12. Painéis laterais

Conexões e Objetos compartilham fundo, borda, cabeçalho, tipografia, ícones
(ver seções 4, 6, 7, 8) — parecem módulos do mesmo produto, não dois
componentes independentes.

**Status:** ✅ resolvido.

---

## 13. Grid

`ResultGrid`/`GridTheme` continuam sendo a **referência** — nenhuma
mudança de identidade própria da grade nesta rodada. Outros componentes
tabulares (propriedades de objeto: Colunas/Índices/Chaves estrangeiras) agora
usam o `ResultGrid` de verdade (não uma imitação visual) — qualquer ajuste
futuro na grade se propaga automaticamente para eles. Tabelas editáveis do
assistente de DDL (que não podem usar o `GridEditController` da grade, pois
editam uma definição de schema, não linhas de uma tabela real) usam
`MetadataTableStyle`, que replica zebra/cabeçalho/seleção da grade sem
reusar o componente inteiro.

**Status:** ✅ resolvido.

### Espaçamento de linhas (altura de linha configurável)

Pedido explícito do usuário: a grade "fica muito apertada" com muitas
colunas — precisa de um controle para aumentar o espaço entre linhas, com
alguns tamanhos predefinidos, escolha do usuário persistida entre sessões.

Controle **independente do Zoom** (que escala a interface inteira) e do
**Modo compacto** (que reduz tudo em ~40%): mexe SÓ na altura da linha das
grades de resultado. Menu Layout (ícone de engrenagem) → "Espaçamento de
linhas (grade)" → `MainWindow.ROW_SPACING_LEVELS` = `{18, 22, 28, 34}`px
("Compacta"/"Padrão"/"Confortável"/"Espaçosa"; 22px = "Padrão" é o valor que
a grade sempre usou antes deste controle existir, nenhuma mudança visual
para quem já usava a IDE). O zoom/modo compacto continuam se aplicando por
cima do valor escolhido (`ResultGrid#styleTable` combina os dois: `scale.
applyAsInt(rowHeightBasePx)`), os dois não se substituem.

Trocar o espaçamento reconstrói as grades já abertas na hora (mesmo
mecanismo de `refreshDynamicSizing()` já usado pelo Zoom). Persistido em
`~/.nureal-ide/ui.conf` (`UiPreferences`, campo `rowSpacingIndex`) — mesmo
arquivo do zoom/modo compacto/keep-alive, reaberto automaticamente na
próxima vez que a IDE abre.

**Status:** ✅ resolvido.

---

## 14. Estados

| Estado | Mecanismo |
|---|---|
| Normal | cor padrão de cada nível de `Typography`/`GridTheme` |
| Hover | `TreeHoverTracker` (lista/árvore) / `SelectionManager` (grade) — mesma cor (`GridTheme.HOVER_BACKGROUND`) |
| Selecionado | `GridTheme.SELECTION_BACKGROUND/FOREGROUND`, mesmo tom em `FlatLaf.properties` (List/Tree/Table/Menu) e na grade |
| Focado | anel de foco global do FlatLaf (`Component.focusWidth/focusColor`) |
| Desabilitado | `IconTheme.DISABLED` / opacidade padrão do FlatLaf |
| Erro | `JComponent.outline = "error"` (mecanismo nativo do FlatLaf) — usado em validação de formulário (ex.: nome de conexão duplicado) e no editor de célula de data inválida da grade |

**Status:** ✅ resolvido — os 6 estados têm exatamente um mecanismo cada,
reaproveitado em qualquer componente que precise dele.

---

## 15. Ícones

`Icons.java`/`IconType` — grid de 20×20, stroke 2px, raio de canto 3px
(`IconTheme`), ~46 SVGs, todos mapeados 1:1 (auditado). Regra estrita de cor
de glifo: verde só em ações positivas, amarelo só em avisos, vermelho só em
erro/exclusão, todo o resto usa "INK" (tema claro) ou `GridTheme.MUTED_TEXT`
(contextos de baixo peso).

**Resolvido nesta rodada:** as setinhas de ordenação do cabeçalho da grade
(`ColumnHeaderRenderer`) usavam glifos de texto Unicode (▲/▼) soltos num
JLabel — único lugar do app que desenhava um indicador sem passar pelo
catálogo de ícones. Agora usam `IconType.SORT_ASCENDING`/`SORT_DESCENDING`
(triângulos vetoriais de verdade via `Icons.get(type, size, color)`,
registrados em `Icons.java` a partir da primitiva `triangle()` já existente —
sem precisar de nenhum SVG novo). A cor continua vindo de
`GridTheme.SORT_INDICATOR_ACTIVE/INACTIVE` (reativa ao tema); o número de
prioridade da ordenação múltipla (ex.: "▲2") virou o texto do próprio JLabel,
colado ao lado do ícone (`setIconTextGap(1)` + `setHorizontalTextPosition
(RIGHT)`), preservando a leitura visual de antes dentro da mesma zona fixa de
18px.

**Status:** ✅ resolvido — nenhuma exceção conhecida restante no sistema de ícones.

---

## Rodada 2 — aplicação mais ampla

Uma segunda auditoria (fora dos 6 arquivos já revisados na primeira rodada)
cobriu `CellContentViewer`, `ColumnMetadataPopup`, `ConnectionDialog`,
`ConnectionEditDialog`, `DdlAssistantDialog`, `ResultStatusBar`, `ResultView`
e outros — e aplicou as correções encontradas:

- `CellContentViewer` e `DdlAssistantDialog` reimplementavam
  `Buttons.styleSecondary`/`stylePrimary` na mão (inclusive uma margem que já
  tinha divergido da canônica) — agora chamam os métodos compartilhados.
- `ColumnMetadataPopup`, `ResultStatusBar`, `FkInspectorWindow`,
  `ResultGrid`, `SqlEditorPane` (barra de localizar) e mais alguns títulos em
  `MainWindow` (estado vazio, overlay de execução, propriedades de objeto)
  tinham peso OU cor definidos manualmente em vez de via `Typography` — todos
  migrados para `Typography.primary/secondary/tertiary`.
- `SqlEditorPane`'s sublinhado de hover sobre objetos do banco
  (`UnderlineHighlightPainter`) usava uma cor `static final` congelada (tom
  do tema CLARO, sempre) — agora lê `GridTheme.HEADER_FOREGROUND` ao vivo a
  cada pintura, igual a todo outro renderer do app.

## Rodada 3 — cor de marca + diálogos da fase 3/4 (Gerenciamento DBA)

Auditoria pedida explicitamente pelo usuário após receber a logo oficial da
Nureal ("padronização visual... aplicar a identidade visual oficial"), cobrindo
os 8 diálogos novos construídos na rodada de gap-analysis DBA/dev
(`UserManagementDialog`, `ProcessListDialog`, `ServerStatusDialog`,
`CsvImportDialog`, `EventsReplicationDialog`, `ExplainDialog`,
`ErDiagramWindow`/`ErDiagramCanvas`, `BackupRestoreDialog`) — construídos
DEPOIS deste documento existir, nunca auditados contra ele.

- **Cor de marca:** ver seção 2 (verde `#1E9147`, amarelo restrito a
  ícone/logo).
- **Literais de cor:** nenhum `new Color(0x...)` fora de `GridTheme` nos 8
  arquivos — todos já reutilizavam `GridTheme`/`MainWindow.ACCENT` desde a
  construção.
- **Botões:** os ~34 `JButton` dos 8 arquivos passam por
  `Buttons.stylePrimary`/`styleSecondary` (individualmente ou em loop sobre um
  array) — nenhum botão "nu" encontrado.
- **Tabelas:** todas usam `MetadataTableStyle.createStyledTable` (mesmo
  zebra/cabeçalho/seleção da grade) — nenhuma tabela Swing padrão sem estilo.
- **Correção aplicada:** `ExplainDialog` destacava tabelas com table scan
  completo (`access_type=ALL`) em VERMELHO (`GridTheme.COLOR_LOGIC_FALSE`) —
  violava a regra da seção 15 ("vermelho só em erro/exclusão"): um table scan
  é um ALERTA de performance, não um erro. Trocado para
  `GridTheme.HEADER_HIGHLIGHT_BORDER` (amarelo, mesmo token de alerta usado
  em toda a UI), texto da legenda e do javadoc atualizados de acordo.
- **Não alterado (avaliado e considerado correto):** o aviso de risco em
  `BackupRestoreDialog` ("pode sobrescrever dados existentes") continua em
  vermelho — diferente do caso acima, aqui o vermelho sinaliza uma ação
  potencialmente DESTRUTIVA/irreversível (perda de dados), não um mero alerta
  de performance; mesma convenção de confirmação destrutiva usada em qualquer
  app (perigo real, não só atenção).

**Status:** ✅ resolvido — 1 correção de semântica de cor aplicada
(`ExplainDialog`), restante já conforme.

## 16. Sistema Semântico de Cores por Tipo de Dado

Pedido explícito do usuário: "um mesmo tipo de dado seja representado sempre
pela mesma cor, independentemente de onde ele esteja sendo exibido... a cor
representa o tipo do dado, e não o seu valor textual". Substitui qualquer
coloração antiga baseada em CONTEÚDO (badges por valor, true/false
verde/vermelho) por coloração baseada no TIPO REAL declarado no banco.

**Fonte única de verdade (duas camadas):**

- `core/sql/SqlTypeKind.java` — decide a CATEGORIA de um tipo SQL (ex.:
  `"DECIMAL(10,2)"` → `DECIMAL`). Pacote `core`, sem Swing/AWT, para poder ser
  reaproveitado tanto pela UI quanto por um eventual consumidor sem interface
  gráfica.
- `ui/GridTheme.java` — decide a COR de cada categoria (`colorFor(SqlTypeKind)`),
  com uma variante clara e uma escura, trocadas por `applyPalette(dark)` como
  todo o resto da paleta.

`ui/RendererFactory.java` combina as duas camadas para a grade de resultados
e é quem também aplica a ÚNICA exceção por NOME de coluna (identificador —
ver abaixo).

**Paleta (estimada a partir da descrição do usuário, mesmo critério já usado
para o verde da marca — ajustável se surgir um valor oficial):**

| Categoria | Representa | Cor |
|---|---|---|
| Palavra-chave SQL | `SELECT`/`FROM`/`WHERE`/... | Azul institucional |
| Objeto | tabela/view/procedure/function/trigger | Branco/neutro (não compete com palavra-chave) |
| Coluna | nome de coluna | Branco/neutro |
| Inteiro | `TINYINT`...`BIGINT` | Azul claro |
| Decimal | `DECIMAL`/`FLOAT`/`DOUBLE`/... | Azul vibrante |
| Texto | `CHAR`/`VARCHAR`/`TEXT`/... | Verde |
| Enum | `ENUM`/`SET` | Verde claro |
| Data | `DATE` | Laranja |
| Hora | `TIME` | Amarelo |
| Data/Hora | `DATETIME`/`TIMESTAMP` | Laranja intenso |
| Boolean | `BOOLEAN`/`BOOL`/`BIT` | Roxo |
| UUID | `UUID` (tipo real do banco) | Azul arroxeado |
| JSON | `JSON`/`JSONB` | Ciano |
| XML | `XML` | Turquesa |
| Binário | `BLOB`/`BINARY`/`VARBINARY`/... | Cinza azulado |
| `NULL` | ausência de valor | Cinza + itálico + contraste reduzido |

**Onde já está aplicado:** grade de resultados (`RendererFactory` +
`NumberCellRenderer`/`TemporalCellRenderer`/`PlainTypedCellRenderer`/
`BooleanCellRenderer`/`BinaryCellRenderer`/`IdentifierCellRenderer`), editor
SQL (`SqlEditorPane#applySemanticSyntaxColors` — palavra-chave, literal de
texto, literal numérico inteiro/decimal, literal booleano — em AMBOS os
temas, claro e escuro), visualizador de célula (`CellContentViewer`), DDL
(aba "DDL" do dialogo de propriedades do objeto e "DDL (pré-visualização)"
do assistente de criar/alterar tabela — os dois agora são
`RSyntaxTextArea`, não mais `JTextArea` plano, via
`SqlEditorPane#styleAsReadOnlySql`), popup/quick-info de coluna
(`ColumnMetadataPopup`, campo "Tipo SQL") e a coluna "Tipo" da aba "Colunas"
do dialogo de propriedades do objeto.

**Três exceções deliberadas, decididas explicitamente pelo usuário (não são
bugs nem inconsistência):**

1. **Identificador (id/`*_id`/uuid pelo NOME da coluna) continua laranja +
   destaque dourado de chave primária**, mesmo que a regra estrita do
   sistema (cor só pelo tipo real) diga que um "id" `INT` deveria parecer um
   `INT` comum. Ver `GridTheme#COLOR_IDENTIFIER`/`COLOR_PRIMARY_KEY`.
2. **Boolean é SEMPRE roxo**, mesmo TRUE e FALSE — substitui a regra antiga
   (verde/vermelho por VALOR, pedida e implementada numa rodada anterior).
   Os campos antigos (`GridTheme.COLOR_LOGIC_TRUE`/`COLOR_LOGIC_FALSE`)
   continuam existindo, mas agora só para indicadores de STATUS do app sem
   relação com tipo de dado (dot conectado/desconectado, sucesso/falha no
   histórico, aviso de ação destrutiva do Backup/Restore).
3. **"Pills" coloridos por valor foram removidos** (`BadgeCellRenderer` e
   `EnumColumnDetector` deletados) — uma coluna `VARCHAR` com conteúdo
   repetitivo (ex.: RECEITA/DESPESA) agora é sempre verde uniforme, uma cor
   só; só o tipo real `ENUM`/`SET` do banco ganha uma cor própria (verde
   claro), fixa, não por valor.

**Limitações conhecidas (não implementado, documentado em vez de fingir
cobertura total):**

- **Autocomplete:** as sugestões já mostram o tipo da coluna como texto
  (`"VARCHAR(255) (tabela)"`), mas o popup do `AutoCompletion` (biblioteca
  RSyntaxTextArea) ainda não tem um `ListCellRenderer` próprio para colorir
  esse texto — ficou fora desta rodada por exigir mexer no mecanismo de
  renderização de uma biblioteca de terceiros sem conseguir validar em tempo
  de execução (sandbox sem Maven/JDK completo, ver Pendências).
- **Editor SQL, literais de string:** `'2026-06-01'` e `'João'` são a MESMA
  produção léxica (string entre aspas simples) — o syntax highlighter não
  sabe contra qual COLUNA (e o tipo dela) aquele literal está sendo
  comparado. Colorir por tipo exigiria análise semântica (ligar o literal à
  coluna no `WHERE`/`ON`), bem além de um destaque de sintaxe; todo literal
  de string usa a cor de TEXTO (verde) hoje.
- **"Object Preview"/"Export Preview"** citados no pedido original não
  correspondem a nenhum componente concreto já existente no app (não há uma
  tela de pré-visualização de objeto nem de exportação, distintas do que já
  foi coberto acima) — nada para aplicar a paleta ainda; revisar se/quando
  esses componentes forem construídos.

**Status:** ⚠️ aplicado em toda a grade, editor, DDL, visualizador de célula
e popup de metadados; autocomplete e as duas limitações de análise semântica
acima ficam para uma rodada futura.

### Rodada 2 — grade quase monocromática (editor inalterado)

Revisão pedida explicitamente pelo usuário depois de ver a paleta acima em
uso real: "a cor deve indicar informação estrutural, nunca decorar o
conteúdo". A tabela de 15 categorias coloridas da Rodada 1 (seção acima) foi
**substituída** para a GRADE DE RESULTADOS e demais exibições de DADOS
(autocomplete, tooltips, visualizador de célula, popup de metadados) — o
EDITOR SQL foi deliberadamente **mantido como estava** (destaque rico,
palavra-chave/string/número/data/booleano cada um com sua cor).

**Por que dá para mudar só a grade sem tocar no editor:** `GridTheme.colorFor
(SqlTypeKind)` é chamado SOMENTE pelos consumidores de exibição de dados
(`RendererFactory`, `CellContentViewer`, `ColumnMetadataPopup`, grid de
metadados da janela principal). O editor SQL nunca chama esse método —
`SqlEditorPane#applySemanticSyntaxColors` lê os campos `COLOR_INTEGER`/
`COLOR_DECIMAL`/`COLOR_TEXTUAL`/`COLOR_BOOLEAN` diretamente. Os dois caminhos
já eram desacoplados antes desta rodada; a mudança só precisou trocar o que
`colorFor()` retorna, sem tocar em nenhum campo editor-only nem no
`SqlEditorPane`.

**Nova paleta da grade (Rodada 2):**

| Elemento | Cor | Observação |
|---|---|---|
| Texto comum, números (inteiro/decimal), enum, UUID, binário, hora | Cor padrão do tema (branco no escuro / preto no claro) — `COLOR_DEFAULT_TEXT` | Deixaram de ter cor própria — pedido explícito: "todo o restante utiliza a cor padrão do tema" |
| Chave primária (PK) | Dourado — `COLOR_PRIMARY_KEY` | Inalterado, já funcionava bem |
| Chave estrangeira (FK) | Amarelo — `COLOR_FOREIGN_KEY` (campo novo) | Antes reaproveitava o laranja de `COLOR_IDENTIFIER`; agora é cor própria, distinta da PK mas na mesma família visual ("mantém relação visual com PK sem confundir") |
| `DATE`/`DATETIME`/`TIMESTAMP` | Roxo suave — `COLOR_DATE`/`COLOR_DATETIME` | Antes era laranja/laranja intenso |
| `JSON` | Azul petróleo/ciano discreto — `COLOR_JSON` | Tom mais discreto que a Rodada 1 |
| `XML` | Verde-água discreto — `COLOR_XML` | Idem |
| Boolean `TRUE` | Azul — `COLOR_BOOLEAN_TRUE` (campo novo) | Reverte para cor POR VALOR (a Rodada 1 tinha unificado em roxo por TIPO; usuário pediu de volta, com cores novas) |
| Boolean `FALSE` | Laranja — `COLOR_BOOLEAN_FALSE` (campo novo) | Nunca vermelho puro — pedido explícito do usuário ("eu evitaria vermelho puro"); vermelho fica reservado a erro/valor inválido |
| `NULL` | Cinza itálico — `COLOR_NULL` | Inalterado |
| Valor editado (grid, pendente de salvar) | Verde Nureal — `EDIT_DIRTY_CELL`/`EDIT_NEW_ROW` | Inalterado, já usa a cor da marca |
| Valor inválido | Vermelho | Reservado exclusivamente para erro — ainda não há um componente concreto de "valor inválido" na grade para aplicar (mesmo gap já documentado antes) |

**Detecção de valor booleano** (`BooleanCellRenderer#truthValue`):
reconhece, case-insensitive, `true/false`, `1/0`, `y/n`, `yes/no`, `s/n`
(sim/não), `on/off`, `ativo/inativo`, `t/f`. Um valor não reconhecido cai de
volta na cor uniforme antiga (`COLOR_BOOLEAN`, roxo) em vez de arriscar um
palpite errado.

**`colorFor(SqlTypeKind)` depois da Rodada 2** (única fonte de cor da
grade/autocomplete/tooltips/metadados/visualizador de célula):

```java
static Color colorFor(SqlTypeKind kind) {
    return switch (kind) {
        case DATE, DATETIME -> COLOR_DATE;
        case JSON -> COLOR_JSON;
        case XML -> COLOR_XML;
        case INTEGER, DECIMAL, TEXTUAL, ENUM, UUID, BINARY, TIME, BOOLEAN -> COLOR_DEFAULT_TEXT;
    };
}
```

`BOOLEAN` cai em `COLOR_DEFAULT_TEXT` aqui porque a grade nunca usa este
método para colorir uma célula booleana de verdade — isso é feito por VALOR
em `BooleanCellRenderer#colorFor`, não por tipo. Este caso só é alcançado
quando algum componente mostra a palavra "BOOLEAN" como TEXTO (ex.: coluna
"Tipo" de uma grade de metadados), onde não há valor para colorir por
TRUE/FALSE.

**Fora do escopo desta rodada (decidido deliberadamente):** `Typography.java`
e `MetadataTableStyle.java` usam `GridTheme.COLOR_TEXTUAL` diretamente como
cor geral de "texto de conteúdo" do app (painéis, árvores, cabeçalhos) — uma
decisão de design PRÉ-EXISTENTE, anterior a todo o sistema de cor-por-tipo e
sem relação com ele; não foi tocada. `ErDiagramCanvas` (nomes de coluna no
diagrama ER) também usa `COLOR_TEXTUAL` para colunas comuns — mantido
igual, é um contexto estrutural de diagrama, não de exibição de dado de
célula; só a cor de FK ali foi trocada de `COLOR_IDENTIFIER` para
`COLOR_FOREIGN_KEY`, para consistência com a grade.

### Formatação de DECIMAL na grade — poda de zeros à direita

Independente de cor: `NumberCellRenderer#formatValue` agora poda zeros à
direita da parte decimal de valores `BigDecimal` (colunas `DECIMAL`/
`NUMERIC`), pedido explícito do usuário para reduzir poluição visual em
colunas com escala fixa alta (ex.: `DECIMAL(20,8)` devolvendo
`"112500.00000000"` para o que é, na prática, um valor redondo).

Regra (`NumberCellRenderer#stripTrailingZeros`): remove zeros à direita da
parte decimal; se nada sobrar, usa `.00` (mínimo de duas casas); se sobrar
só um dígito, completa para duas (`2.4` → `2.40`); se sobrarem duas ou mais
casas significativas, preserva todas sem arredondar (`150.12345678`
continua com os 8 dígitos). Colunas `INTEGER` (sem parte decimal) e o
visualizador de célula (`CellContentViewer`, que mostra o valor bruto
completo) não são afetados.

## 17. Atualização automática

Pedido do usuário: checar a cada abertura do app se existe uma versão mais
nova publicada no GitHub e, se sim, permitir baixar e instalar sem sair da
IDE. Pacote `com.nureal.ide.core.update` (sem Swing — mesma separação
core/ui do resto do projeto) + três componentes de UI em `ui`.

**Cadeia de versão (fonte única: a tag do Git):** tag `vX.Y.Z` publicada →
workflow de release roda `versions:set` para sincronizar `pom.xml` com a tag
→ `maven-shade-plugin` grava `Implementation-Version` no manifesto do fat
jar → `jpackage --app-version` usa a MESMA string para o instalador `.msi`.
Em runtime, `AppVersion.current()` lê `Implementation-Version` do próprio
manifesto (`Package#getImplementationVersion()`) — nunca o `<version>` do
`pom.xml` diretamente, que só existe em tempo de build. Rodando fora de um
jar empacotado (`mvn exec:java`, IDE) não há manifesto: `AppVersion` cai em
`"0.0.0-dev"` e a checagem AUTOMÁTICA do startup é pulada (só a manual, via
menu, continua funcionando) — evita avisos de atualização sem sentido
durante desenvolvimento.

**Checagem:** `UpdateChecker.fetchLatestRelease()` consulta
`GET /repos/alvescvinicius/nureal-database-ide/releases/latest` (API pública
do GitHub, sem autenticação) e decodifica a resposta com o parser JSON que
já existe em `core.json.JsonParser` (criado originalmente para o EXPLAIN
FORMAT=JSON) — nenhuma dependência nova (Jackson/Gson/org.json) adicionada
ao projeto, mesma filosofia já documentada naquele parser. `SemVer` compara
só MAJOR.MINOR.PATCH (ignora sufixo de pré-release/build metadata).

**UI:** `UpdateBanner` — faixa discreta e dispensável no topo da janela
(NORTH da `MainWindow`, acima de tudo), nunca um diálogo modal bloqueando o
startup (pedido explícito do usuário). Fundo `GridTheme.HOVER_BACKGROUND` +
listra verde da marca à esquerda; botões "Baixar e instalar" (primário),
"Ver notas", "Ignorar esta versão" e fechar (dispensa só na sessão atual,
sem persistir). Menu Layout (ícone de engrenagem da toolbar) ganhou
"Verificar atualizações..." para checagem manual a qualquer momento — essa
SEMPRE mostra algum feedback (banner, "já está atualizado" ou erro),
diferente da automática (silenciosa em qualquer falha).

**Baixar e instalar:** `UpdateInstallDialog` baixa o asset `.msi` do release
(`UpdateDownloader`, via `java.net.http.HttpClient`, mesmo idioma
`SwingWorker`/`JProgressBar` já usado por `BackupRestoreDialog`) e, ao
terminar, dispara `msiexec /i` (não silencioso — o usuário confirma cada
passo do instalador gráfico do Windows, igual a rodar o `.msi` manualmente)
e fecha a IDE logo em seguida (`UpdateInstallLauncher`): o instalador só
consegue substituir os arquivos em uso depois que o processo Java atual
encerra. Hoje só o Windows tem instalação automática (único artefato que o
workflow de release publica); qualquer outro SO, ou um release sem `.msi`
anexado, cai automaticamente no plano B — abre a página do release no
navegador padrão.

**Preferências:** `UpdatePreferences` (`~/.nureal-ide/update.conf`, mesmo
formato chave=valor de `UiPreferences`) guarda `autoCheckEnabled` e
`skippedVersion` — "Ignorar esta versão" grava a tag ali; a checagem
automática do startup não repete o aviso para essa MESMA tag, mas some
sozinha assim que um release mais novo sair.

**Bug relatado pelo usuário — launcher `.exe` crashando com "GetMessage()
failed" / System error 1400:** não era o `.msi` nem o `msiexec /i` acima —
era o próprio launcher nativo que o `jpackage` gera pro Windows. Todo app
empacotado com `jpackage` se REINICIA sozinho num segundo processo logo ao
abrir, só pra poder ajustar a variável `PATH` antes de carregar a JVM
(limitação do `jli.dll` — não dá pra mudar o `PATH` do processo atual), e
espera esse segundo processo rodando um loop de mensagens do Windows
(`GetMessage()`) que pode falhar (ex.: antivírus/EDR interferindo na
criação do processo suspenso usado nesse truque) com exatamente o erro
relatado. Corrigido no `.github/workflows/release.yml`: JDK do workflow
subiu de 17 para 25 (a partir do JDK 24/JDK-8340311 o jpackage passou a ler
uma propriedade `win.norestart=true` no `.cfg` da app-image pra pular esse
reinício de vez — JDK 17 simplesmente não lê essa propriedade) e a geração
do `.msi` virou 3 passos em vez de 1: `jpackage --type app-image` → injeta
`win.norestart=true` na seção `[Application]` do `.cfg` gerado (não existe
flag de linha de comando pra isto) → `jpackage --type msi --app-image
<pasta>` empacota a app-image já editada.

## Pendências conhecidas (ainda não implementadas)

1. **Azul "informação" (seção 2):** não existe hoje um campo semântico
   dedicado em `GridTheme` para essa cor — nenhum consumidor concreto foi
   identificado ainda que precise dela; adicionar só quando um caso de uso
   real aparecer, para não criar um token não utilizado.
2. ~~Setinhas de ordenação da grade (seção 15)~~ — resolvido (ver seção 15
   acima: agora usam `IconType.SORT_ASCENDING`/`SORT_DESCENDING`).
3. **Nenhuma verificação visual/compilação** foi feita ainda (sandbox sem
   Maven/JDK completo) — recomenda-se um build + inspeção visual dos quatro
   painéis laterais, da barra superior, do dialogo de propriedades de objeto
   e do assistente de DDL antes de considerar o design system "fechado".
4. **Pipeline de release (3 passos + JDK 25) ainda não rodou de verdade** —
   mudança em `.github/workflows/release.yml` (ver seção 17, "Bug relatado
   pelo usuário") só foi revisada manualmente, nunca executada numa tag real.
   Publicar uma tag de teste (`vX.Y.Z`) e conferir: (a) o workflow completa
   os 3 passos sem erro, (b) o `.cfg` dentro da app-image realmente ganhou a
   linha `win.norestart=true`, (c) o `.msi` gerado instala e abre a IDE sem
   o erro "GetMessage() failed".
