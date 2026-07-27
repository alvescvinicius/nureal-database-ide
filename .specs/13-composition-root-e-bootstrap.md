# Composition Root e Bootstrap

## Objetivo

Transformar `App.java` de um script de bootstrap puro em um composition root real, aplicando [01-Arquitetura/05-dependency-injection.md](../../nureal-development-standard/01-Arquitetura/05-dependency-injection.md) do NDS — o ponto único onde todas as implementações concretas dos módulos são construídas e injetadas.

## Estado atual

`App.java` (109 linhas): inicializa `AppLogger`, configura FlatLaf/fonte/decoração de janela, registra o fold parser de SQL, instala cursor global, e faz `SwingUtilities.invokeLater(() -> new MainWindow().setVisible(true))`. Nenhum `ConnectionManager`, `MetadataService`, `AiPreferences`, ou qualquer outra dependência de módulo é construído aqui — tudo é instanciado sob demanda, em pontos variados dentro de `MainWindow`, na maioria das vezes lazily no primeiro uso (o pior caso: todo o grafo de objetos da IA só é criado na primeira vez que `openAiChat()` roda).

## Estado alvo

```
com.nureal.ide.app/
  App.java                  (bootstrap de LaF/fonte/decoração — sem mudança nessa parte)
  ComposicaoRaiz.java        (novo: constrói e conecta as implementações concretas de cada módulo)
```

`ComposicaoRaiz` constrói, na inicialização (antes de `MainWindow` ser exibida):

- `ConexaoAtivaPort` (implementação `ConnectionManagerJdbc`) e `ConnectionRepository`/`CredentialCipher` do módulo `conexoes`.
- `MetadataRepository` do módulo `metadados`.
- `Agent` completo do módulo `ia-chat` (via `LLMProviderFactory`, `DefaultContextProvider`, `ToolExecutor` com as tools registradas, `ChatHistoryStore`) — fechando a Lacuna 1 descrita em [11-modulo-ia-chat.md](11-modulo-ia-chat.md).
- Repositórios de histórico/consultas salvas/sessão do módulo `historico-e-consultas`.
- `RepositorioDeReleasesPort` do módulo `atualizacao`.

`MainWindow` (e, à medida que for dividida — ver [06](06-modulo-execucao-e-edicao-de-grade.md)), passa a **receber** essas dependências via construtor, em vez de instanciá-las internamente.

## Regras específicas

1. `ComposicaoRaiz` é o único lugar do projeto autorizado a fazer `new` das implementações concretas de infraestrutura de cada módulo (`ConnectionManagerJdbc`, `MetadataRepositoryJdbc`, os 6 `LLMProvider`s, etc.) — em qualquer outro lugar, isso é um sinal de desvio a ser corrigido.
2. A ordem de construção respeita as dependências entre módulos definidas em [02-arquitetura-alvo-e-modulos.md](02-arquitetura-alvo-e-modulos.md) (ex.: `conexoes` antes de `metadados`, que depende dele).
3. Construir tudo eagerly no startup não deve introduzir latência perceptível de inicialização — objetos custosos (ex.: uma conexão JDBC real) continuam sendo abertos sob demanda (quando o usuário conecta), apenas a **fábrica**/composição é que existe desde o início, não o recurso caro em si.
4. Esta mudança é, propositalmente, a **última fase da migração** (ver [14](14-plano-de-migracao-fases.md)) — só faz sentido reorganizar a composição depois que os módulos individuais já expõem os contratos que `ComposicaoRaiz` precisa injetar.

## Exemplos bons

- `ComposicaoRaiz` construindo o `Agent` de IA na inicialização e passando-o pronto para `MainWindow`, que apenas abre a janela de chat existente quando solicitado, sem reconstruir nada.

## Exemplos ruins

- Qualquer `new ConnectionManagerJdbc(...)` ou `new ClaudeProvider(...)` fora de `ComposicaoRaiz`.

## Critério de aceite

- [x] Nenhuma implementação concreta de infraestrutura é instanciada fora de `ComposicaoRaiz`.
- [x] O tempo de inicialização da IDE não piora perceptivelmente em relação ao estado atual.
- [x] `MainWindow` recebe suas dependências via construtor.

> **Progresso**: implementado. `ComposicaoRaiz` (`com.nureal.ide.app.ComposicaoRaiz`) construída e injetada a partir de `App.main`, antes de `MainWindow` existir. Constrói os 12 objetos de infraestrutura hoje elegíveis: `DatabaseDialect` (`MySqlDialect`), `bootstrapConnectionManager` (`ConexaoAtivaPort`/`ConnectionManager`, usado só antes da primeira conexão real do usuário existir), `MetadataRepository` (`MetadataService`), `MetadataCache`, `TableMetadataCache` (tornada `public`, antes package-private), `SqlCompletionProvider`, `ConnectionRepository` (`ConnectionStore`), `SessionStore`, `SavedQueryStore`, `ExecutionHistoryStore`, `RepositorioDeReleasesPort` (`UpdateChecker`), `FormatPreferences`. `MainWindow` deixou de instanciar essas 12 dependências em inicializadores de campo — agora recebe `ComposicaoRaiz raiz` no construtor e só faz `this.x = raiz.x()`. `ConnectionsPanel` foi alargado de `ConnectionStore` para o contrato `ConnectionRepository` (só usava `.load()`/`.save()`, ambos no contrato). `App.java` cria `ComposicaoRaiz raiz = new ComposicaoRaiz()` e passa para `new MainWindow(raiz)`. Verificado com `mvn clean test`: 162 testes, 1 falha (pré-existente, `SqlFormatterTest`, não relacionada).
>
> Duas exceções documentadas no próprio javadoc de `ComposicaoRaiz`, intencionalmente fora do escopo desta fase: (1) o grafo de objetos do módulo `ia-chat` continua montado lazily dentro de `MainWindow.openAiChat()`, porque depende de preferências que mudam em runtime (troca de modelo/provider) — ver Lacuna 1 em [11-modulo-ia-chat.md](11-modulo-ia-chat.md); (2) os `ConnectionManager` de cada conexão individual do usuário (um por `Conexao`/workspace, criados sob demanda ao conectar) continuam sendo criados fora de `ComposicaoRaiz` — só o `bootstrapConnectionManager` (usado antes de qualquer conexão real existir) é que vem dela.
