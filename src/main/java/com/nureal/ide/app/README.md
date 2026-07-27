# Composition Root (`com.nureal.ide.app`)

## Responsabilidade

Único ponto de entrada autorizado a instanciar as implementações concretas de infraestrutura dos módulos (`dialeto`, `conexoes`, `metadados`, `historico`, `atualizacao`) e conectá-las via injeção de construtor, aplicando o princípio de composition root descrito em [01-Arquitetura/05-dependency-injection.md](../../../../../../nureal-development-standard/01-Arquitetura/05-dependency-injection.md) do NDS. Ver detalhamento completo em [.specs/13-composition-root-e-bootstrap.md](../../../../../../.specs/13-composition-root-e-bootstrap.md).

## Estrutura interna

```
ComposicaoRaiz.java   constrói os 12 objetos de infraestrutura elegíveis e expõe getters
```

Não há `dominio`/`aplicacao`/`infraestrutura` aqui: `ComposicaoRaiz` não é um módulo de negócio, é o ponto de fiação (wiring) entre os módulos existentes, então segue uma estrutura mais simples e própria.

## Expõe (portas públicas)

- `ComposicaoRaiz` — construída uma única vez em `com.nureal.ide.App#main` e passada para `MainWindow` via construtor.

## Dependências

Depende de contratos e implementações de infraestrutura de `modulos.conexoes`, `modulos.dialeto`, `modulos.metadados`, `modulos.historico`, `modulos.atualizacao`, além de `core.autocomplete`/`core.format` (ainda não migrados) e `ui.TableMetadataCache`.

## Exceções deliberadas (fora do escopo desta composição)

- O grafo de objetos do módulo `ia-chat` continua montado lazily dentro de `MainWindow.openAiChat()`, por depender de preferências que mudam em runtime (troca de modelo/provider) — ver Lacuna 1 em [.specs/11-modulo-ia-chat.md](../../../../../../.specs/11-modulo-ia-chat.md).
- Os `ConnectionManager` de cada conexão individual do usuário (um por `Conexao`/workspace, criados sob demanda ao conectar) não vêm de `ComposicaoRaiz` — apenas o `bootstrapConnectionManager`, usado antes de qualquer conexão real existir.
