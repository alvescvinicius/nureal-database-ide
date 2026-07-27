# Módulos: Autocomplete e Editor SQL

## Objetivo

Especificar a migração de `core.autocomplete.*` para `modulos/autocomplete/`, e a divisão do god class `ui.SqlEditorPane` (1937 linhas) dentro de `modulos/editor-sql/`.

## Estado atual

- `CaretContextResolver` — lógica estática de posição/regex sobre texto, zero dependência. Domínio puro.
- `SqlCompletionProvider` — **estende `org.fife.ui.autocomplete.DefaultCompletionProvider`** (tipo de biblioteca de UI) dentro do pacote `core`. Já usa uma interface funcional `ForeignKeyLookup` para não depender de `ui.TableMetadataCache` — meio caminho andado para desacoplamento.
- `SqlEditorPane.java` (1937 linhas, sem JDBC) — não é vazamento de infraestrutura, é puramente um god class de apresentação: construção do text area (~232–730), atalhos de teclado (~309–514), hover/breadcrumb de objetos de schema (~780–1330, lógica de resolução de domínio dentro do widget), find/replace (~1433–1633), utilitários de fonte/case/undo (~1633–1888), menu de contexto (~1888+).

## Estado alvo

```
modulos/autocomplete/
  README.md
  dominio/
    CaretContextResolver.java         (sem mudança)
  aplicacao/
    casos-de-uso/
      sugerir-completions/
        ...input/output/handler        (encapsula SqlCompletionProvider sem herdar de tipo de biblioteca de UI na fronteira do módulo)
  infraestrutura/
    SqlCompletionProviderRSyntax.java  (adaptador concreto que efetivamente estende DefaultCompletionProvider — a herança de biblioteca de UI fica isolada aqui, não é mais importada por nenhum outro módulo)

modulos/editor-sql/
  README.md
  apresentacao/
    SqlEditorPane.java                 (casca fina remanescente: composição dos componentes abaixo)
    EditorThemeApplier.java            (extraído: ~232-730 de aparência/paleta)
    EditorShortcutBinder.java          (extraído: ~309-514 de atalhos)
    FindReplaceBar.java                (extraído: ~1433-1633)
    EditorPopupMenuFactory.java        (extraído: menu de contexto)
    EditorFontRegistry.java            (extraído: utilitários estáticos de fonte)
  aplicacao/
    casos-de-uso/
      resolver-objeto-sob-cursor/
        ...input/output/handler         (extrai a lógica de hover/breadcrumb ~780-1330: dado texto+posição+SchemaInfo, retorna qual tabela/coluna está sob o cursor)
```

## Regras específicas

1. `SqlCompletionProviderRSyntax` é a única classe do projeto autorizada a `extends DefaultCompletionProvider` — qualquer outro ponto de autocomplete futuro reusa esta mesma classe em vez de herdar novamente da biblioteca.
2. A extração de `resolver-objeto-sob-cursor` como caso de uso não muda a UX (mesmo hover, mesmo breadcrumb) — apenas move a lógica de "dado um texto e uma posição de cursor, qual objeto de schema está ali" para fora do widget Swing, tornando-a testável sem instanciar um `JTextComponent`.
3. `EditorUndoManager` (hoje em `core.editor`, estende `javax.swing.undo.UndoManager`) muda de pacote para `modulos/editor-sql/infraestrutura/` — é Swing-dependente por natureza (integra com `Document`/`CaretEvent`), então pertence à camada de infraestrutura da interface, não a `core`/domínio. Isso corrige o vazamento identificado no diagnóstico sem mudar uma linha de lógica.
4. Nenhuma extração desta spec deve alterar o comportamento de highlight, autocomplete, atalhos ou breadcrumb — todos são cobertos por teste manual de regressão (ver [16-estrategia-de-testes.md](16-estrategia-de-testes.md)) antes/depois de cada extração, já que testar Swing automatizado tem custo alto neste projeto.

## Exemplos bons

- `resolver-objeto-sob-cursor` testado unitariamente passando um `SchemaInfo` fake e uma string de SQL, sem precisar abrir uma janela Swing.

## Exemplos ruins

- Duplicar a lógica de resolução de objeto sob o cursor entre `SqlEditorPane` e um segundo widget futuro em vez de reusar o caso de uso extraído.

## Critério de aceite

- [ ] `SqlEditorPane.java` fica abaixo de ~400 linhas após a extração (casca de composição).
- [ ] Nenhuma outra classe do projeto estende `DefaultCompletionProvider` fora de `SqlCompletionProviderRSyntax`.
- [ ] `EditorUndoManager` não está mais em um pacote nomeado `core`/domínio.
- [ ] Autocomplete, highlight, atalhos, hover e find/replace continuam idênticos ao comportamento atual.
