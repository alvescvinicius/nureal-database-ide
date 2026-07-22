# SPEC-0008 — Architecture Cleanup

## Objetivo
Reduzir a complexidade do código, eliminar duplicidades e diminuir o custo de manutenção sem alterar o comportamento da aplicação.

Esta SPEC não implementa funcionalidades. Seu único objetivo é melhorar a arquitetura.

## Objetivos
- reduzir arquivos muito grandes
- reduzir duplicação
- reduzir acoplamento
- remover código morto
- aumentar reutilização
- facilitar manutenção
- diminuir contexto necessário para IA

## Fora do escopo
Não alterar: comportamento da IDE, layout, fluxo do usuário, banco de dados, SQL, funcionalidades.

A aplicação deve funcionar exatamente igual após cada refatoração.

## Ordem obrigatória
Executar exatamente nesta sequência. Nunca pular etapas.
1. Detectar
2. Planejar
3. Refatorar
4. Validar
5. Commit

## Etapa 1 — Código morto
Identificar: classes nunca utilizadas, métodos nunca chamados, interfaces órfãs, constantes nunca utilizadas, imports, variáveis, parâmetros, recursos, imagens, ícones, fontes, arquivos.

Gerar relatório. **Não remover automaticamente.**

## Etapa 2 — Duplicidades
Localizar: código igual, código semelhante, métodos semelhantes, componentes semelhantes, listeners semelhantes, dialogs semelhantes, painéis semelhantes, toolbars semelhantes, factories semelhantes, builders semelhantes, utilitários semelhantes.

Para cada duplicidade informar: Origem, Destino, Percentual semelhante, Sugestão.

## Etapa 3 — Arquivos grandes
Detectar arquivos acima de: 300 / 500 / 800 / 1000 / 1500 linhas.

Classificar: Baixo / Médio / Alto / Crítico.

Critérios:
- Mais de 800 linhas: obrigatório avaliar divisão.
- Mais de 1200 linhas: obrigatório dividir.
- Mais de 2000 linhas: não permitido.

Extração — sempre preferir extrair: Panels, Dialogs, Controllers, Factories, Models, Services, Builders, Renderers. Nunca duplicar código.

Métodos — detectar: >40 linhas, >80 linhas, >120 linhas. Extrair responsabilidade.

Classes — cada classe deve possuir apenas uma responsabilidade.

## Componentes
Nunca criar componentes semelhantes. Sempre reutilizar (ex.: NButton, NTextField, NToolbar, NPanel, NSection, NDialog, NPopup).

Não pode existir: botão igual, toolbar igual, dialog igual, popup igual, renderer igual, cell renderer igual. Se existir, extrair componente.

## Utilitários
Detectar: StringUtils, SwingUtils, ColorUtils, FileUtils, DateUtils, ValidationUtils. Eliminar métodos repetidos.

## Acoplamento
Evitar: MainWindow conhece tudo; Panel conhece MainWindow; Controller conhece View; View conhece Service.

Preferir: Interfaces, Eventos, Callbacks, Observers.

## Dependências
Eliminar: dependências circulares, imports desnecessários, injeções não utilizadas.

## Performance da IA
Toda refatoração deve reduzir: arquivos necessários para entendimento, quantidade de contexto, dependências entre classes.

Objetivo: uma funcionalidade deve exigir leitura de no máximo 5 arquivos (ideal: 3).

## Commits
Cada categoria gera um commit: refactor(dead-code), refactor(duplicates), refactor(main-window), refactor(sidebar), refactor(dialogs), refactor(utils), refactor(renderers). Nunca misturar categorias.

## Critérios de aceite
- ✔ Nenhuma funcionalidade alterada
- ✔ Projeto compila
- ✔ Testes passam
- ✔ Sem código morto conhecido
- ✔ Sem duplicidades relevantes
- ✔ Nenhuma classe acima do limite
- ✔ Nenhum método excessivamente grande
- ✔ Componentes reutilizados
- ✔ Acoplamento reduzido
- ✔ Estrutura mais simples
