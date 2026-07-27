# DialogShell (`compartilhado.designsystem.dialog`)

## Responsabilidade

Base comum para os 12+ dialogs do app: elimina a duplicação mecânica de `new JDialog(owner, title, modality)` + `setDefaultCloseOperation` + `setLayout(BorderLayout)` + `setLocationRelativeTo(owner)` + `setVisible(true)`.

## Estado desta fase

`DialogShell` criado e migrado como piloto em `ui.ProcessListDialog` (o mais simples dos 12+: não-modal, sem rodapé OK/Cancelar, sem validação de formulário) — exatamente a recomendação da spec ("começando pela mais simples"). `ExplainDialog` foi avaliado como segundo candidato mas também não tem rodapé OK/Cancelar nem validação; ambos os "pilotos sugeridos" pela spec exercitam só a parte de construção/ciclo de vida da janela, não o rodapé de botões.

## O que NÃO foi incluído (e por quê)

- **Resolução do "owner"**: continua sendo `ui.DialogUtil#owner`, chamada pelo dialog ANTES de construir o `DialogShell` — não duplicado aqui, não movido para `compartilhado` nesta fase (evita forçar os outros ~24 arquivos que ainda usam `DialogUtil` a ganhar um import só por isso).
- **Rodapé padrão OK/Cancelar**: já existe, e já é usado (`DdlAssistantDialog`), como `Buttons.dialogFooter(JDialog, JButton)` — não duplicado em `DialogShell`.
- **Ponto único de exibição de erro de validação**: os assistentes guiados (DDL/view/trigger/routine) ainda usam mecanismos diferentes entre si (`ConstruirDdlDeTabelaOutput` tipado vs. `SqlBuilderValidationException`) — introduzir um "ponto único" agora seria ceremônia prematura (regra dos três usos) até que pelo menos dois desses convirjam para o mesmo mecanismo.

## Próximos passos (fora desta fatia)

Migrar as demais 11+ dialogs para `DialogShell` uma de cada vez (ver [.specs/14-plano-de-migracao-fases.md](../../../../../../../.specs/14-plano-de-migracao-fases.md)) — cada uma precisa ser testada manualmente após a migração (abrir, redimensionar, fechar pelo X, verificar que o listener de fechamento ainda dispara), verificação indisponível neste ambiente.
