# Plano de Migração — Fases

## Objetivo

Definir a ordem obrigatória de execução da migração para NDS, escolhendo explicitamente **migração incremental** em vez de reescrita do zero, e detalhando o processo fase a fase.

## Motivação

Reescrever o Nureal Database IDE do zero descartaria partes já maduras e corretas (`DatabaseDialect`, `core.ai`, os renderers de célula, o design system) e arriscaria uma regressão longa sem release funcional no meio do caminho. O NDS já recomenda este caminho: [01-Arquitetura/10-evolucao-arquitetural.md](../../nureal-development-standard/01-Arquitetura/10-evolucao-arquitetural.md) pede módulo piloto antes de propagar, nunca big-bang. A antiga `SPEC-0008 — Architecture Cleanup` do próprio projeto já estabelecia o mesmo princípio ("a aplicação deve funcionar exatamente igual após cada refatoração").

## Decisão

**Migrar incrementalmente, módulo por módulo, dentro do repositório existente.** Não recriar o projeto do zero. Cada fase abaixo corresponde a um ou mais dos documentos `03`–`13` e produz commits pequenos, por categoria, sem misturar mudança de comportamento com refatoração (ver [04-IA/04-refatoracao-assistida-por-ia.md](../../nureal-development-standard/04-IA/04-refatoracao-assistida-por-ia.md) do NDS).

## Ordem obrigatória

### Fase 0 — Preparação
- Configurar/confirmar lint e build (`mvn compiler`, `surefire`) já cobrem o baseline atual.
- Escrever testes de caracterização para os comportamentos de maior risco antes de qualquer extração (execução de lote de SQL, paginação de cursor, CRUD de grade) — ver [16-estrategia-de-testes.md](16-estrategia-de-testes.md).
- Congelar (documentar como "não tocar nesta fase") os pontos já maduros: `DatabaseDialect`, `core.ai.provider` (6 implementações), `metadata.model.*`, renderers de célula.

### Fase 1 — Módulo piloto: execução e edição de grade
Especificado em [06-modulo-execucao-e-edicao-de-grade.md](06-modulo-execucao-e-edicao-de-grade.md). É o módulo de maior risco e maior ganho — deve ser o primeiro, validando o processo completo (teste de caracterização → extração de interface → mover implementação → reduzir a casca de UI) antes de repeti-lo em módulos menores.

### Fase 2 — Fundações: conexões, dialeto, metadados
Especificado em [03](03-modulo-conexoes-e-seguranca.md) e [04](04-modulo-dialeto-e-metadados.md). Executar depois da Fase 1 porque `execucao-consulta`/`edicao-grade` já terão definido o formato de `ConexaoAtivaPort` que estes módulos fornecem.

### Fase 3 — Módulos de features independentes (podem ser paralelizados entre si)
- [05-modulo-autocomplete-e-editor-sql.md](05-modulo-autocomplete-e-editor-sql.md)
- [07-modulo-assistente-ddl.md](07-modulo-assistente-ddl.md)
- [08-modulo-historico-consultas-sessao.md](08-modulo-historico-consultas-sessao.md)
- [09-modulo-backup-exportacao.md](09-modulo-backup-exportacao.md)
- [10-modulo-atualizacao-app.md](10-modulo-atualizacao-app.md)

Estes módulos não dependem uns dos outros de forma significativa — podem ser feitos em qualquer ordem relativa entre si, cada um como seu próprio ciclo de teste-de-caracterização → extração → validação.

### Fase 4 — Formalização do módulo já maduro
[11-modulo-ia-chat.md](11-modulo-ia-chat.md) — mudança estrutural pequena (mover pacote, extrair validação de entrada), pois a maior parte já está correta. Fazer depois da Fase 2 porque depende de `ConexaoAtivaPort` e `MetadataRepository` já existirem.

### Fase 5 — Interface compartilhada
[12-interface-design-system-e-dialogos.md](12-interface-design-system-e-dialogos.md) — mover o design system e introduzir `DialogShell`, migrando uma dialog piloto. Pode começar em paralelo à Fase 3, mas a migração das 12+ dialogs para `DialogShell` deve esperar até que cada módulo (Fase 3) já tenha movido sua própria dialog para o novo pacote.

### Fase 6 — Composition root
[13-composition-root-e-bootstrap.md](13-composition-root-e-bootstrap.md) — sempre por último, porque depende de todos os módulos já exporem seus contratos de domínio (`ConexaoAtivaPort`, `MetadataRepository`, o `Agent` de IA, etc.) para ter o que injetar.

## Regras de execução válidas para todas as fases

1. Uma categoria de mudança por commit (seguindo o espírito da antiga SPEC-0008): extração de interface, movimentação de arquivo, redução de casca de UI são commits separados, nunca misturados.
2. Nenhuma fase introduz funcionalidade nova — apenas reorganiza código existente. Funcionalidades novas (ver `GAP_ANALYSIS_DBA_DEV.md`: usuários/privilégios, SSL/SSH tunnel, etc.) continuam em specs de produto separadas, não nestas specs de arquitetura.
3. Cada módulo, ao ser concluído, ganha seu próprio `README.md` (ver [05-Projetos/02-estrutura-de-modulos.md](../../nureal-development-standard/05-Projetos/02-estrutura-de-modulos.md) do NDS) antes de ser considerado "migrado".
4. Se, durante qualquer fase, surgir uma decisão não coberta por nenhuma destas specs, ela é sinalizada como lacuna (ver [09-Evolução/02-lacunas-identificadas.md](../../nureal-development-standard/09-Evolução/02-lacunas-identificadas.md) do NDS) antes de prosseguir por suposição.

## Critério de aceite geral da migração

- [ ] Todas as fases 0–6 concluídas, cada módulo com `README.md` próprio.
- [ ] `MainWindow.java` reduzido a uma casca de composição de UI (sem `java.sql.*`, sem motor de execução embutido).
- [ ] `App.java`/`ComposicaoRaiz` é o único ponto de construção de implementações concretas de infraestrutura.
- [ ] Nenhuma regressão de comportamento em nenhum momento — a IDE funciona de forma idêntica à v0.4 atual em cada checkpoint entre fases.
- [ ] O antigo objetivo informal da SPEC-0008 ("uma funcionalidade deve exigir leitura de no máximo 5 arquivos") é atingido para a maioria dos módulos.
