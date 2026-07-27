# Módulo: Editor SQL (`modulos.editorsql`)

## Responsabilidade

Widget de edição de SQL (`ui.SqlEditorPane`, ainda não movido — ver nota de escopo) e sua infraestrutura de suporte específica de Swing/texto.

## Estrutura interna

```
infraestrutura/   EditorUndoManager — estende javax.swing.undo.UndoManager,
                  integra com Document/CaretEvent; por natureza dependente
                  de Swing, não pertence a core/domínio
```

Nome do pacote sem hífen (`editorsql`, não `editor-sql`) por restrição da linguagem Java para identificadores de pacote — mesmo padrão já adotado em `modulos.backupexportacao`.

## Expõe (portas públicas)

- `EditorUndoManager` — usado por `ui.SqlEditorPane` para agrupar digitações contínuas em uma única operação de desfazer/refazer.

## Dependências

Nenhuma dependência de outro módulo.

## Nota de escopo

Esta primeira fatia move apenas `EditorUndoManager`, que já era puramente de infraestrutura Swing e não exigia decisão de design. A divisão do restante de `ui.SqlEditorPane` (1937 linhas: aparência, atalhos, hover/breadcrumb, find/replace, menu de contexto — ver [.specs/05-modulo-autocomplete-e-editor-sql.md](../../../../../../../.specs/05-modulo-autocomplete-e-editor-sql.md)) em `apresentacao/` e no caso de uso `resolver-objeto-sob-cursor` continua pendente: é uma decomposição maior de um widget Swing sem cobertura de teste automatizado, e depende de verificação visual (regressão manual de highlight/autocomplete/atalhos/hover) indisponível neste ambiente — feita sem visualização, mas o usuário testará localmente antes de aceitar.
