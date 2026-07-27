# Módulo: Autocomplete (`modulos.autocomplete`)

## Responsabilidade

Gerar as sugestões de autocomplete do editor SQL a partir do cache de metadados em memória, sensíveis ao contexto do cursor (coluna, tabela, geral). Nunca consulta o banco ao digitar.

## Estrutura interna

```
dominio/         CaretContextResolver — lógica estática de posição/regex sobre texto, zero dependência
aplicacao/       GeradorDeSugestoes (a lógica de sugestão em si, sem depender de nenhum tipo do fife/RSyntaxTextArea),
                 SugestaoDeCompletion (saída simples: texto/descrição/snippet opcional),
                 FonteDeChavesEstrangeiras (ponte funcional para FKs conhecidas de uma tabela)
infraestrutura/  SqlCompletionProviderRSyntax — único adaptador do projeto autorizado a
                 `extends org.fife.ui.autocomplete.DefaultCompletionProvider`; só traduz
                 SugestaoDeCompletion para Completion do fife
```

## Expõe (portas públicas)

- `SqlCompletionProviderRSyntax` — construído uma vez em `ComposicaoRaiz`, usado por `SqlEditorPane` (via `org.fife.ui.autocomplete.AutoCompletion`) e atualizado por `MainWindow` a cada `refresh(schema)`.

## Dependências

Depende de `modulos.metadados.dominio.entidades` (`SchemaInfo`, `TableInfo`, `ColumnInfo`, `ForeignKeyInfo`) e de `core.sql.TableAliasGenerator` (ainda não migrado).

## Nota de escopo

Esta extração isola a herança de `DefaultCompletionProvider` na borda de infraestrutura e move a lógica de sugestão para `aplicacao`, mas não a reescreve como caso de uso Input/Handler/Output literal — `GeradorDeSugestoes` mantém estado (schema/índice de tabelas carregados via `refresh`) porque reflete fielmente o ciclo de vida real (schema muda ao conectar/trocar de conexão, sugestões são consultadas a cada tecla). Ver [.specs/05-modulo-autocomplete-e-editor-sql.md](../../../../../../../.specs/05-modulo-autocomplete-e-editor-sql.md).
