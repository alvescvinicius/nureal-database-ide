# Visão Geral — Adoção do Nureal Development Standards (NDS)

## Objetivo

Este e os demais documentos em `.specs/000` a `.specs/016` especificam como recriar (ou migrar incrementalmente) o **Nureal Database IDE** para seguir o [Nureal Development Standards](../../nureal-development-standard) — o repositório de padrões oficiais da Nureal para desenvolvimento orientado por IA.

Este conjunto de specs não é um padrão genérico: é a **aplicação concreta** do NDS a este projeto específico. Onde o NDS define a regra geral (ex.: Backbone Pattern), estas specs dizem exatamente qual módulo, qual classe, qual arquivo deve mudar para segui-la.

## Por que este trabalho existe

O Nureal Database IDE é um protótipo em evolução (v0.4) que cresceu organicamente: pacote `ui/` quase plano com ~90 arquivos Swing, pacote `core/` organizado por feature (não por camada), e pelo menos um god class de quase 4000 linhas (`MainWindow.java`) que mistura montagem de UI, orquestração de execução SQL, JDBC cru, e composição de dependências ao mesmo tempo. O próprio time já havia percebido isso — o histórico do Git mostra uma tentativa anterior (`SPEC-0008 — Architecture Cleanup`, ver `git show HEAD~1:.specs/0008-architecture-cleanup.md`) de reduzir arquivos grandes, duplicação e acoplamento, sem ainda ter uma arquitetura-alvo formalizada para guiar onde cada pedaço deveria ir.

O NDS fornece essa arquitetura-alvo. Estas specs conectam o diagnóstico do código real ao padrão oficial.

## Como estes documentos foram produzidos

Duas investigações independentes e somente-leitura do código-fonte (`src/main/java/com/nureal/ide/ui/**` e `src/main/java/com/nureal/ide/core/**`) mais a leitura da documentação de produto já existente (`docs/000` a `docs/119`, `GAP_ANALYSIS_DBA_DEV.md`, `AI-CHAT-MASTER-PLAN.md`, `DESIGN_SYSTEM.md`) alimentaram o diagnóstico em [01-diagnostico-arquitetural-atual.md](01-diagnostico-arquitetural-atual.md). A partir daí, cada módulo-alvo foi especificado individualmente.

## Estrutura destes documentos

| Documento | Conteúdo |
|---|---|
| [00-visao-geral-e-como-usar.md](00-visao-geral-e-como-usar.md) | Este documento |
| [01-diagnostico-arquitetural-atual.md](01-diagnostico-arquitetural-atual.md) | Estado atual: god classes, acoplamento, o que já está bom |
| [02-arquitetura-alvo-e-modulos.md](02-arquitetura-alvo-e-modulos.md) | Lista oficial de módulos-alvo e a estrutura de camadas que cada um deve ter |
| [03-modulo-conexoes-e-seguranca.md](03-modulo-conexoes-e-seguranca.md) | Módulo `conexoes` |
| [04-modulo-dialeto-e-metadados.md](04-modulo-dialeto-e-metadados.md) | Módulos `dialeto` e `metadados` |
| [05-modulo-autocomplete-e-editor-sql.md](05-modulo-autocomplete-e-editor-sql.md) | Módulo `autocomplete` + divisão do `SqlEditorPane` |
| [06-modulo-execucao-e-edicao-de-grade.md](06-modulo-execucao-e-edicao-de-grade.md) | Extração do motor de execução SQL e edição de grade do `MainWindow`/`GridEditController` |
| [07-modulo-assistente-ddl.md](07-modulo-assistente-ddl.md) | Módulo `assistente-ddl` |
| [08-modulo-historico-consultas-sessao.md](08-modulo-historico-consultas-sessao.md) | Unificação de histórico, consultas salvas e sessão |
| [09-modulo-backup-exportacao.md](09-modulo-backup-exportacao.md) | Backup/restore e exportação |
| [10-modulo-atualizacao-app.md](10-modulo-atualizacao-app.md) | Checagem de atualização |
| [11-modulo-ia-chat.md](11-modulo-ia-chat.md) | Formalização do `core.ai` como módulo de referência |
| [12-interface-design-system-e-dialogos.md](12-interface-design-system-e-dialogos.md) | `ui/components/`, `DESIGN_SYSTEM.md` e o "dialog shell" comum |
| [13-composition-root-e-bootstrap.md](13-composition-root-e-bootstrap.md) | `App.java` como composition root real |
| [14-plano-de-migracao-fases.md](14-plano-de-migracao-fases.md) | Ordem de execução obrigatória, fase a fase |
| [15-convencoes-e-glossario-do-projeto.md](15-convencoes-e-glossario-do-projeto.md) | Decisões de nomenclatura específicas deste projeto (incluindo exceções documentadas ao NDS) |
| [16-estrategia-de-testes.md](16-estrategia-de-testes.md) | Como testar cada camada nova neste projeto especificamente |

## Como usar estes documentos

- **Se a decisão for recriar o projeto do zero**: siga a ordem de `02` a `13` como especificação de arquitetura-alvo; use `06-Exemplos/01-exemplo-modulo-completo.md` do NDS como gabarito de estrutura de pastas para cada módulo novo.
- **Se a decisão for migrar incrementalmente** (recomendado — ver motivo em [14-plano-de-migracao-fases.md](14-plano-de-migracao-fases.md)): siga `14` como plano de execução; cada fase referencia o documento de módulo correspondente.
- **Para qualquer dúvida de padrão não coberta aqui**: consulte o NDS diretamente (`01-Arquitetura/`, `02-Código/`, `04-IA/`) e, se a lacuna persistir, registre-a em `09-Evolução/02-lacunas-identificadas.md` do NDS.

## Regras gerais que se aplicam a todos os módulos

1. Nenhuma migração de módulo altera comportamento observável da IDE — mesma regra da antiga SPEC-0008 ("a aplicação deve funcionar exatamente igual após cada refatoração"), agora reforçada por [01-Arquitetura/10-evolucao-arquitetural.md](../../nureal-development-standard/01-Arquitetura/10-evolucao-arquitetural.md) do NDS (módulo piloto antes de propagar).
2. Nenhum módulo novo introduz uma dependência externa (biblioteca) sem essa decisão ser explicitamente aprovada — ver [04-IA/07-limites-e-responsabilidades-da-ia.md](../../nureal-development-standard/04-IA/07-limites-e-responsabilidades-da-ia.md).
3. `DatabaseDialect` e `core.ai.provider` (ver [11-modulo-ia-chat.md](11-modulo-ia-chat.md)) são tratados como abstrações já maduras — a migração os formaliza como contratos de domínio, não os reescreve do zero.
4. Toda extração de lógica de um god class (`MainWindow`, `ObjectExplorerController`, `GridEditController`) exige teste de caracterização do comportamento atual antes de mover o código, conforme [04-IA/04-refatoracao-assistida-por-ia.md](../../nureal-development-standard/04-IA/04-refatoracao-assistida-por-ia.md).

## Checklist

- [ ] A pessoa ou agente lendo este conjunto de specs também tem acesso ao repositório `nureal-development-standard` como referência?
- [ ] A decisão entre "recriar" e "migrar incrementalmente" foi tomada antes de iniciar (ver [14](14-plano-de-migracao-fases.md))?
- [ ] Cada módulo será tratado como uma unidade completa (interface/aplicação/domínio/infraestrutura) antes de passar ao próximo?
