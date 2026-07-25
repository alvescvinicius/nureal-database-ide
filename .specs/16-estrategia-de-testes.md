# Estratégia de Testes

## Objetivo

Definir como testar cada camada nova introduzida pela migração para NDS, considerando as limitações reais deste projeto: aplicação desktop Swing (difícil de testar automaticamente na camada de interface) com um banco MySQL real como dependência externa.

## Estado atual

`src/test/java` cobre hoje quase exclusivamente `core.ai.*` (agente, config, contexto, histórico, prompt, providers, specialist, tool) mais `core.ddl.NormalizationAdvisor`, `core.format.SqlFormatter`, `core.json.JsonWriter`, `core.safety.SqlRiskAnalyzer`. **Nenhum teste automatizado existe para `ui/` nem para o motor de execução/edição de grade hoje embutido em `MainWindow`/`GridEditController`** — exatamente a área de maior risco desta migração.

## Estratégia por camada, aplicando [02-Código/04-testes.md](../../nureal-development-standard/02-Código/04-testes.md) do NDS

### Domínio (`dominio/`)
Teste unitário puro, sem mocks, sem banco real. Já é o padrão hoje para `SqlRiskAnalyzer`, `SqlFormatter`, `NormalizationAdvisor` — manter.

### Aplicação (`aplicacao/casos-de-uso/`)
Teste unitário com as portas (`ConexaoAtivaPort`, `MetadataRepository`, `ExecutorDeConsultaPort`, `EditorDeGradePort`) substituídas por implementações fake em memória — nunca contra um MySQL real. Isto é a mudança de maior valor desta migração: hoje é **impossível** testar "o que acontece se uma instrução no meio do lote falhar" sem banco real, porque a lógica está presa a `java.sql.*` dentro de `MainWindow`; depois da extração (ver [06](06-modulo-execucao-e-edicao-de-grade.md)), um `ExecutorDeConsultaPort` fake torna isso trivial.

### Infraestrutura (`infraestrutura/`)
Teste de integração contra MySQL real (ambiente de desenvolvimento local ou container), cobrindo especificamente: `ConnectionManagerJdbc`, `MetadataRepositoryJdbc`, `ExecutorDeConsultaJdbc`, `EditorDeGradeJdbc`, `MySqlDumpRunner`. Estes testes são mais lentos e não rodam a cada commit — rodam antes de merge de mudanças na camada de infraestrutura.

### Interface (Swing)
Testes automatizados de UI Swing têm custo/benefício ruim para este projeto (sem framework de teste de UI hoje configurado). Em vez de introduzir um framework novo sem necessidade comprovada (o que violaria [04-IA/07-limites-e-responsabilidades-da-ia.md](../../nureal-development-standard/04-IA/07-limites-e-responsabilidades-da-ia.md) do NDS), a estratégia é:
- Toda extração de lógica de um god class Swing (`MainWindow`, `SqlEditorPane`, `ObjectExplorerController`) move a lógica testável para `aplicacao`/`dominio` (testável sem Swing), deixando na `interface/` apenas composição e tradução de evento — o que reduz drasticamente a superfície não testada automaticamente.
- Checklist de regressão manual (roteiro fixo de ações a verificar na IDE rodando) antes de cada release, cobrindo os fluxos críticos: conectar, executar SQL, editar grade, formatar, autocomplete, assistente de DDL, chat de IA, exportar/backup.

## Testes de caracterização (obrigatórios antes de qualquer extração de god class)

Antes de tocar em `MainWindow.executeStatements`/`GridEditController`, escrever testes cobrindo o comportamento **atual**, mesmo que ainda contra a estrutura antiga, para servir de rede de segurança durante a extração (ver [04-IA/04-refatoracao-assistida-por-ia.md](../../nureal-development-standard/04-IA/04-refatoracao-assistida-por-ia.md) do NDS):

1. Executar um único `SELECT` — resultado paginável correto.
2. Executar múltiplas instruções separadas por `;` — todas executadas em ordem.
3. Executar um lote onde a segunda instrução falha — comportamento atual de erro parcial preservado.
4. Paginar um resultado grande (mais de uma página) — paginação sob demanda correta.
5. Inserir uma linha via grade — sucesso, chave gerada recuperada corretamente.
6. Atualizar uma linha via grade — sucesso.
7. Excluir uma linha via grade — sucesso.
8. Aplicar uma edição de grade que viola uma restrição do banco — rollback completo, mensagem de erro exibida.

## Regras

1. Toda extração de código de `ui/` para `aplicacao/dominio` só é considerada concluída quando o código extraído tem teste unitário cobrindo, no mínimo, os casos de sucesso e os erros de negócio mapeados na spec do módulo correspondente.
2. Teste de infraestrutura contra MySQL real nunca bloqueia o build padrão (`mvn test` no dia a dia) — roda como suíte separada antes de merge, evitando que a exigência de um banco disponível trave o desenvolvimento local de quem só mexe em domínio/aplicação.
3. Nenhum bug encontrado durante a migração é apenas corrigido — ganha um teste de regressão no nível de camada apropriado (unitário se for lógica de domínio/aplicação, roteiro manual se for exclusivamente de interface).

## Critério de aceite

- [ ] Todos os 8 testes de caracterização acima existem e passam antes do início da Fase 1 (ver [14-plano-de-migracao-fases.md](14-plano-de-migracao-fases.md)).
- [ ] Cada caso de uso novo introduzido pelas specs `03`–`13` tem teste unitário com fake de porta, sem depender de MySQL real.
- [ ] O roteiro de regressão manual está escrito e é executado antes de cada release durante todo o período de migração.
