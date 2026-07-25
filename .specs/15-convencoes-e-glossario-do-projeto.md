# Convenções e Glossário do Projeto

## Objetivo

Registrar decisões de nomenclatura específicas deste projeto, incluindo exceções documentadas às convenções padrão do NDS ([02-Código/01-convencoes-de-nomenclatura.md](../../nureal-development-standard/02-Código/01-convencoes-de-nomenclatura.md)), justificadas pelo tamanho e maturidade do código já existente.

## Motivação

O NDS recomenda nomes de conceitos de negócio em português quando a equipe fala português, e nomes técnicos em inglês. O Nureal Database IDE já tem ~218 arquivos majoritariamente em inglês (`ConnectionManager`, `MetadataService`, `SqlFormatter`...), com uma única inconsistência histórica notável (`ui.Conexao`, em português). Renomear massivamente 218 arquivos para português só para cumprir a letra do padrão teria custo altíssimo (todo PR em andamento, todo o histórico de `git blame`, toda documentação já escrita em `docs/`) para um ganho de consistência que não resolve nenhum problema real de confusão de nomes — ninguém no projeto hoje confunde `ConnectionManager` com outro conceito.

## Decisão

1. **Nomes de classe, pacote, método e variável em código permanecem em inglês**, conforme já estabelecido no projeto. Isto é uma **exceção documentada** à regra geral do NDS (que prioriza português para conceitos de negócio), registrada aqui conforme exige [01-Arquitetura/10-evolucao-arquitetural.md](../../nureal-development-standard/01-Arquitetura/10-evolucao-arquitetural.md) do NDS — nenhuma migração de fase (`03`–`13`) deve traduzir nomes de classe existentes para português salvo os casos novos abaixo.
2. **Nomes de Casos de Uso (Input/Handler/Output) introduzidos por esta migração são em português**, seguindo o Backbone Pattern do NDS à risca nas partes novas (ex.: `ExecutarLoteDeInstrucoesHandler`, não `ExecuteStatementBatchHandler`) — isso cria uma fronteira visível e intencional entre "código legado em inglês, sendo envolvido" e "estrutura de aplicação nova, em português", o que ajuda a rastrear o progresso da migração só de olhar os nomes.
3. **A única exceção inversa** é `ui.Conexao` (já em português) — mantida como está; não será traduzida para `Connection` só por simetria, pois isso violaria a regra de "não reescrever sem necessidade".
4. **Nomes de pacote de módulo** (`modulos/conexoes`, `modulos/execucao-consulta`, etc.) são em português, conforme definido em [02-arquitetura-alvo-e-modulos.md](02-arquitetura-alvo-e-modulos.md) — o nome do módulo é a unidade de negócio (deve ser legível por qualquer stakeholder), enquanto o nome da classe dentro dele é um detalhe técnico já estabelecido.

## Glossário de termos deste projeto

| Termo | Definição | Nota |
|---|---|---|
| **Conexão ativa** | A conexão JDBC atualmente aberta e em foco no workspace | `ConexaoAtivaPort` no domínio, `ConnectionManagerJdbc`/`Conexao` no código legado |
| **Dialeto** | Implementação de `DatabaseDialect` para um vendor específico de banco | Único ponto de extensão multi-banco |
| **Workspace** | Uma conexão + suas abas de editor abertas + estado de sessão associado | Termo já usado no README/UI atual |
| **Instrução (statement)** | Um comando SQL individual dentro de um lote separado por `;` | Mapeado por `SqlStatementSplitter` |
| **Risco (SQL de risco)** | Instrução classificada por `SqlRiskAnalyzer` como potencialmente destrutiva (DELETE/UPDATE sem WHERE, DROP, TRUNCATE, ALTER/CREATE/RENAME) | Exige confirmação de segurança antes de executar |
| **Especialista (Specialist)** | Estratégia de conhecimento específico de vendor usada pelo agente de IA para compor o prompt de sistema | Espelha o conceito de Dialeto, mas para o módulo `ia-chat` |

## Regras adicionais específicas deste projeto

1. **Logging**: o NDS pede logging estruturado (chave-valor/JSON) — `core.log.AppLogger` hoje é um wrapper simples sobre `java.util.logging` com mensagens de texto livre. Esta é uma lacuna conhecida e registrada, não resolvida por nenhuma das specs `03`–`13` (que tratam de arquitetura de módulos, não de observabilidade). Tratar como candidato a uma spec futura dedicada, seguindo o processo de [10-Contribuicao/01-processo-de-proposta-de-padrao.md](../../nureal-development-standard/10-Contribuicao/01-processo-de-proposta-de-padrao.md) do NDS.
2. **JSON caseiro** (`core.json.JsonParser`/`JsonWriter`): mantido como está — trocar por uma biblioteca externa (Jackson/Gson) não é necessário para esta migração de arquitetura e introduziria uma dependência nova sem necessidade comprovada (violaria [04-IA/07-limites-e-responsabilidades-da-ia.md](../../nureal-development-standard/04-IA/07-limites-e-responsabilidades-da-ia.md) do NDS, que exige validação humana explícita para novas dependências).
3. **`AppLogger` como utilitário estático global**: aceito como exceção documentada ao padrão geral de "toda dependência é injetada" do NDS ([01-Arquitetura/05-dependency-injection.md](../../nureal-development-standard/01-Arquitetura/05-dependency-injection.md)) — logging é tratado como preocupação verdadeiramente transversal, na linha do que o próprio NDS aceita para utilitários de infraestrutura cross-cutting.

## Checklist

- [ ] Nenhum nome de classe existente foi traduzido para português sem necessidade durante a migração.
- [ ] Todo caso de uso novo (Input/Handler/Output) segue nomenclatura em português.
- [ ] Toda exceção às convenções padrão do NDS está registrada aqui com justificativa, não decidida ad hoc durante a implementação.
