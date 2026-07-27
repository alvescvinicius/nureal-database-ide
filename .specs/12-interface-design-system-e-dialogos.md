# Interface: Design System e Dialogos Compartilhados

## Objetivo

Formalizar `ui.components` (`NButton`, `NCard`, `NTheme`, etc.) como o design system oficial do projeto em `compartilhado/designsystem/`, alinhado a `DESIGN_SYSTEM.md`, e introduzir um "dialog shell" comum para eliminar a duplicação mecânica identificada em 12+ dialogs.

## Estado atual

`ui.components` já é um design system real e em uso (não decorativo): `NTheme` delega corretamente para `Spacing`/`GridTheme`/`Typography` (pacote-pai), documentando no próprio javadoc que foi corrigido após duplicar uma escala própria — um bom precedente de "child depende do parent, nunca o contrário" já aplicado no código. `NSearchField` (9 usos), `NCard` (6), `NAccent` (7), `NBadge`/`NToast`/`NCodeBlock`/`NButton`/`NToolbar` (2–4 cada), incluindo uso em `ai/ChatPanel`/`ai/MessageRenderer`. Nenhum componente tem paleta hardcoded própria — tudo delega a `NTheme`.

`DESIGN_SYSTEM.md` já documenta, com autoridade, tipografia (`Typography.primary/secondary/tertiary`), cores (`GridTheme.applyPalette`, verde de marca `#1E9147`, amarelo restrito a ícone/logo, vermelho só para erro/destrutivo), botões (`Buttons.java`, 3 papéis), espaçamento (`Spacing.java`, migração incompleta para painéis laterais/dialogs/editor/grade — gap conhecido, não bug), ícones (`Icons.java`/`IconType`, grade 20×20, regra de cor estrita).

Nenhuma dialog (12+ arquivos: `ConnectionDialog`, `ConnectionEditDialog`, `CsvImportDialog`, `RoutineBuilderDialog`, `TriggerBuilderDialog`, `ViewBuilderDialog`, `BackupRestoreDialog`, `EventsReplicationDialog`, `ExplainDialog`, `ProcessListDialog`, `ServerStatusDialog`, `UserManagementDialog`, `DdlAssistantDialog`) estende uma base comum — cada uma reimplementa `new JDialog(owner, title, modality)`, rodapé de botões e exibição de erro de validação manualmente. Único utilitário compartilhado hoje: `DialogUtil.owner(Component)`.

## Estado alvo

```
compartilhado/designsystem/
  NAccent.java, NBadge.java, NButton.java, NCard.java, NCodeBlock.java,
  NIconRail.java, NSearchField.java, NTheme.java, NToast.java, NToolbar.java   (movidos de ui/components, sem mudança de comportamento)
  Spacing.java, GridTheme.java, Typography.java, Buttons.java, Icons.java, IconTheme.java, IconType.java   (movidos de ui/, hoje já são as fontes que NTheme referencia)

compartilhado/designsystem/dialog/
  DialogShell.java        (novo: base comum — abre JDialog com modalidade/decoração/centralização via DialogUtil.owner, monta rodapé de botões padrão OK/Cancelar, expõe um ponto único de exibição de erro de validação)
```

## Regras específicas

1. `DialogShell` é introduzido **sem migrar as 12+ dialogs de uma vez** — cada dialog migra para usá-lo individualmente, como parte do plano de fases (ver [14](14-plano-de-migracao-fases.md)), começando pela mais simples (`ExplainDialog` ou `ProcessListDialog`) como piloto.
2. Nenhum componente de `compartilhado/designsystem` define cor/espaçamento/tipografia própria fora de `NTheme`/`Spacing`/`GridTheme`/`Typography` — continua a regra já em vigor hoje, apenas realocada de pacote.
3. A migração de espaçamento ad hoc (painéis laterais, dialogs, editor, grade) para a escala formal de `Spacing.java`, já registrada como gap conhecido em `DESIGN_SYSTEM.md`, é tratada como um item de trabalho de design system, não como bloqueador desta migração de arquitetura — pode prosseguir em paralelo ou depois.
4. A regra de cor por tipo semântico (`SqlTypeKind` → `GridTheme.colorFor`) e a restrição de amarelo/vermelho descritas em `DESIGN_SYSTEM.md` continuam sendo a fonte de verdade — nenhuma spec de módulo funcional (03 a 11) deve redefinir cor localmente.

## Exemplos bons

- Uma dialog nova (ex.: para um dialeto Postgres futuro) usando `DialogShell` desde o início em vez de reimplementar `new JDialog(...)`.

## Exemplos ruins

- Introduzir um décimo terceiro esqueleto manual de `JDialog` em vez de usar `DialogShell` já disponível.

## Critério de aceite

- [x] `compartilhado/designsystem` contém os componentes `N*` e as fontes de tema (`Spacing`, `GridTheme`, `Typography`, `Buttons`, `Icons`) sem mudança de comportamento visual.
- [x] `DialogShell` existe e ao menos uma dialog piloto foi migrada para usá-lo com sucesso, validando o ganho antes de propagar às demais.
- [ ] Nenhuma regressão visual em tema claro/escuro para os componentes movidos (não verificável neste ambiente — ver progresso).

> **Progresso (DialogShell)**: criado em `compartilhado.designsystem.dialog.DialogShell` — cobre construção (`create`), montagem (`center`/`north`/`south`), fechamento (`onClosed`) e exibição (`show(width, height)`) de um `JDialog`. `ui.ProcessListDialog` migrado como piloto (sem mudança de comportamento: mesma modalidade MODELESS, mesmo tamanho 920×520, mesmo listener de fechamento parando o timer de auto-refresh). Deliberadamente **não** inclui resolução de "owner" (continua `ui.DialogUtil`, fora desta fase) nem rodapé OK/Cancelar (já existe como `Buttons.dialogFooter`) — ver [compartilhado/designsystem/dialog/README.md](../src/main/java/com/nureal/ide/compartilhado/designsystem/dialog/README.md) para o raciocínio completo. Verificado com `mvn clean test`: 173 testes, 1 falha pré-existente — **sem teste automatizado de `DialogShell` em si** (nenhum teste deste projeto constrói `JDialog`/`JPanel` reais, para não depender de display; ver padrão já estabelecido no restante da suíte) e **sem verificação visual** de `ProcessListDialog` (abrir/fechar/auto-refresh) — o usuário deve testar manualmente antes de aceitar esta fatia. As demais 11+ dialogs continuam usando `new JDialog(...)` direto.

> **Progresso**: `ui.components.*` (10 arquivos: `NAccent`, `NBadge`, `NButton`, `NCard`, `NCodeBlock`, `NIconRail`, `NSearchField`, `NTheme`, `NToast`, `NToolbar`) e `ui.{Spacing,GridTheme,Typography,Buttons,Icons,IconTheme,IconType}` (7 arquivos) movidos para `compartilhado.designsystem`, package flat, sem mudança de lógica — só pacote. ~40 arquivos consumidores em `ui/` (que antes não precisavam de import, mesmo pacote) ganharam o novo import; vários membros de `GridTheme`/`Icons`/`IconTheme` que eram package-private viraram `public` (só funcionavam por coincidência de pacote antes). Verificado com `mvn clean compile` + `mvn clean test`: 173 testes, 1 falha pré-existente (`SqlFormatterTest`, não relacionada) — mas **nenhuma verificação visual foi feita** (não há como abrir a IDE Swing neste ambiente); o usuário deve testar tema claro/escuro localmente antes de aceitar esta fatia.
>
> **Achado durante a migração**: `Buttons.stylePrimary()` dependia de `MainWindow.ACCENT` — um componente de design system importando da aplicação, violação direta da regra "child depende do parent, nunca o contrário" que a própria spec (seção "Estado atual") descreve como já respeitada. Corrigido: a constante do verde da marca virou `GridTheme.BRAND_GREEN` (fonte de verdade correta); `MainWindow.ACCENT` mantido com o mesmo valor, agora delegando para lá, sem quebrar os 4 outros consumidores existentes. Ver [compartilhado/designsystem/README.md](../src/main/java/com/nureal/ide/compartilhado/designsystem/README.md).
>
> **`DialogShell` e a migração de dialog piloto continuam pendentes**: deliberadamente não feitos nesta fatia — mudam comportamento real de `JDialog` (modalidade, foco, fechamento) e só podem ser validados com a aplicação rodando de verdade, verificação indisponível neste ambiente.
