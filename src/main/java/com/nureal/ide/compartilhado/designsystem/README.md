# Design System (`compartilhado.designsystem`)

## Responsabilidade

Design system oficial do projeto (ver `DESIGN_SYSTEM.md`): fontes de tema (`Spacing`, `GridTheme`, `Typography`, `Buttons`, `Icons`, `IconTheme`, `IconType`) e os componentes reutilizáveis (`NAccent`, `NBadge`, `NButton`, `NCard`, `NCodeBlock`, `NIconRail`, `NSearchField`, `NTheme`, `NToast`, `NToolbar`).

## Estrutura interna

Pacote flat (sem `dominio/aplicacao/infraestrutura`): design system é infraestrutura de apresentação pura, sem regra de negócio — não se encaixa no Backbone Pattern.

## Regra de dependência

`NTheme` delega para `Spacing`/`GridTheme`/`Typography` (nunca o contrário) — regra "child depende do parent" já em vigor antes desta migração, só realocada de pacote. Nenhum componente aqui pode depender de classes de `ui` (a aplicação em si): isso é o inverso do que um design system deveria fazer.

## Nota de escopo

**Movidos sem mudança de comportamento**: `ui.components.*` (10 arquivos) e `ui.{Spacing,GridTheme,Typography,Buttons,Icons,IconTheme,IconType}` (7 arquivos), todos referenciados por dezenas de arquivos em `ui/` — nenhum deles tinha import antes (mesmo pacote `ui`), então a migração precisou adicionar import em ~40 arquivos consumidores, além de tornar `public` vários membros de `GridTheme`/`Icons`/`IconTheme` que eram package-private (só funcionavam por estarem no mesmo pacote de quem os usava).

**Violação de camada corrigida durante a migração**: `Buttons.stylePrimary()` chamava `MainWindow.ACCENT` diretamente — um componente de design system dependendo de uma classe da aplicação (`ui.MainWindow`), o inverso do que a regra "child depende do parent" exige. A constante do verde da marca (`0x1E9147`) foi movida para `GridTheme.BRAND_GREEN` (a fonte de verdade correta, já que `GridTheme` é quem centraliza cor); `MainWindow.ACCENT` continua existindo com o mesmo valor, agora delegando a `GridTheme.BRAND_GREEN`, para não quebrar os demais consumidores (`ConnectionsPanel`, `ObjectTreeCellRenderer`, `ResultStatusBar`, `UpdateBanner`).

**`DialogShell` (spec: `compartilhado/designsystem/dialog/DialogShell.java`) não foi criado nesta fase** — introduzir uma nova base comum para `JDialog` e migrar uma dialog piloto (`ExplainDialog` ou `ProcessListDialog`) muda comportamento real de UI (modalidade, foco, fechamento) que só pode ser validado abrindo a aplicação de verdade; sem essa verificação visual disponível neste ambiente, o risco de regressão silenciosa é alto demais para fazer às cegas. Ver [.specs/12-interface-design-system-e-dialogos.md](../../../../../../../.specs/12-interface-design-system-e-dialogos.md).
