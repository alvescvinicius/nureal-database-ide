# Nureal Database IDE — o que falta para uma experiência completa (dev + DBA)

> Documento de planejamento, não de arquitetura de código. Objetivo: mapear a
> distância entre o estado atual (v0.4) e uma ferramenta que sirva tanto
> desenvolvedores (escrever/testar SQL, modelar tabelas) quanto administradores
> de banco (gerenciar usuários, privilégios, saúde do servidor). Complementa o
> roadmap técnico já existente no `README.md` (Trie de autocomplete, pool de
> conexões HikariCP, streaming de resultados, cofre de credenciais do SO,
> multi-banco) — este documento cobre a lacuna que esses itens não tocam:
> **administração do servidor/usuários**, e o que falta para o dev completar o
> ciclo de vida de um schema.

## Como este projeto está publicado hoje

Vale registrar antes de falar de features: a distribuição já está resolvida.
Existe um workflow de GitHub Actions (`release.yml`) que, ao publicar uma tag,
gera um instalador `.msi` para Windows com Java embutido — quem baixa não
precisa ter JDK instalado. Isso significa que "publicar" no sentido de
"colocar nas mãos de alguém" já funciona; o que falta é a ferramenta *ter
substância* suficiente para competir com DBeaver, MySQL Workbench, phpMyAdmin,
Navicat, HeidiSQL e DataGrip — que é para onde qualquer devedor/DBA vai
comparar assim que testar.

**Importante distinguir dois sentidos de "gerenciamento de usuário" antes de
priorizar** (não são a mesma coisa e custam muito diferente):

1. **Gerenciar os usuários DO BANCO conectado** (`CREATE USER`, `GRANT`,
   roles do MySQL 8+) — isto é uma tela/wizard DENTRO da IDE, cabe na
   arquitetura atual (desktop, sem servidor próprio). É o que a maioria dos
   pedidos de "administradores das bases" costuma significar, e é o foco
   principal deste documento.
2. **A própria aplicação ter contas/login/times/permissões de quem pode ver o
   quê** — isto só faz sentido se o Nureal virar um produto hospedado/cliente-
   servidor (equipe compartilhando conexões, auditoria centralizada). Hoje o
   app é 100% desktop single-user (cada instalação lê/grava
   `~/.nureal-ide/connections.conf` local, sem backend) — isto é um projeto
   à parte, não um item de roadmap incremental. Se for essa a intenção,
   avisa que vale uma conversa separada antes de estimar, porque muda a
   arquitetura inteira (precisaria de um servidor, autenticação própria,
   sincronização de conexões por equipe etc.).

O resto deste documento assume o sentido (1), que é o que dá para evoluir sem
reescrever a aplicação.

## Inventário do que já existe (verificado no código, não por suposição)

**Conexão:** perfil simples (host/porta/schema/usuário/senha), múltiplas
conexões simultâneas (`Conexao`/`ConnectionManager`), senha opcional cifrada
localmente com AES-256/GCM (`LocalVault`) — mas SEM SSL/TLS, sem túnel SSH,
sem parâmetros JDBC avançados (charset, timezone, opções do driver).

**Metadados:** leitura de schemas/tabelas/views/procedures/functions/triggers
via `information_schema` (`MetadataService`), cache em memória, detalhe de
colunas/índices/FKs sob demanda, visualizador de DDL, Inspetor de Chave
Estrangeira (navegação em cadeia pelas relações).

**Modelagem:** Assistente de DDL guiado (criar tabela do zero, ou alterar
tabela — sempre aditivo, nunca `MODIFY`/`DROP`) com sugestões de
normalização automáticas. Criar schema.

**Editor SQL:** RSyntaxTextArea com highlight, autocomplete sensível a
contexto (agora com sugestão de JOIN via FK), formatador com 3 presets,
localizar/substituir, histórico de execuções local, biblioteca de queries
salvas, analisador de risco (avisa antes de `DELETE`/`UPDATE` sem `WHERE`,
`DROP`, `TRUNCATE`).

**Grade de resultados:** edição em lote (update/insert/delete pendente até
"Salvar alterações"), renderizadores por tipo, badges, exportação para Excel.

**O que NÃO existe, em nenhum lugar do código** (confirmado por busca — não
há um único arquivo, classe ou até palavra-chave de UI para isto, só
`GRANT`/`REVOKE` como palavras reservadas do syntax highlight): qualquer tela
de usuários, papéis (roles) ou privilégios do servidor; `SHOW
PROCESSLIST`/matar sessão; variáveis/status do servidor; log de queries
lentas; backup/restore; eventos agendados; réplica; manutenção de tabela
(`OPTIMIZE`/`ANALYZE`/`CHECK`); import de dados (CSV/planilha → tabela);
diagrama ER; comparação de schema entre dois bancos.

## Lacunas — lado DBA (o que foi pedido explicitamente)

Esta é a área com **zero cobertura hoje**, e é naturalmente a que mais
diferencia uma "IDE de SQL" de uma "ferramenta completa de administração".
Ordenado por valor/esforço:

1. **Usuários e privilégios (o item citado no pedido).** Uma tela nova
   (aba de objeto de nível SERVIDOR, acima dos schemas na árvore, ou um item
   de menu dedicado) que traga: listar usuários (`mysql.user` /
   `information_schema.USER_PRIVILEGES`), criar/editar/excluir usuário
   (`CREATE USER` / `ALTER USER` / `DROP USER`, incluindo política de senha —
   expiração, exigir troca no primeiro login), uma matriz visual de
   privilégios por nível (global / schema / tabela / coluna — os 4 níveis que
   o MySQL realmente suporta) com toggles em vez de exigir que o usuário
   escreva `GRANT` na mão, suporte a **roles** do MySQL 8+ (criar role,
   atribuir privilégios à role, atribuir role a usuário), e um "Ver
   `SHOW GRANTS`" para qualquer usuário selecionado. Isto sozinho cobre a
   maior parte do que falta para "administrador da base".
2. **Monitoramento de sessões.** `SHOW PROCESSLIST` numa grade viva
   (auto-refresh configurável), com botão para `KILL` uma sessão/query —
   essencial para um DBA que precisa liberar um lock ou parar uma query
   travada sem abrir outro cliente.
3. **Variáveis e status do servidor.** `SHOW VARIABLES` / `SHOW GLOBAL
   STATUS` pesquisável, com indicação do que é editável em runtime (`SET
   GLOBAL`) vs. o que exige reiniciar o servidor/editar `my.cnf`.
4. **Manutenção de tabela.** `OPTIMIZE TABLE`, `ANALYZE TABLE`, `CHECK
   TABLE` no menu de contexto do objeto — ação de um clique, hoje só dá
   para fazer digitando SQL na mão (o que a maioria dos DBAs vindos de
   phpMyAdmin/Workbench estranha não ter).
5. **Backup/restore lógico.** Mesmo que só um wrapper fino sobre
   `mysqldump`/`mysqlpump` (gerar o `.sql` de um schema, ou restaurar um
   arquivo `.sql` executando-o em lote) — não precisa reinventar o
   `mysqldump`, só dar uma UI para não precisar sair da IDE e abrir o
   terminal.
6. **Log de queries lentas / `EXPLAIN` visual.** Já existe o analisador de
   risco (`SqlRiskAnalyzer`) para comandos perigosos; falta o par
   complementar de performance — rodar `EXPLAIN`/`EXPLAIN ANALYZE` e mostrar
   o plano de forma legível (não só o resultado cru em grade), e opcionalmente
   ler a slow query log se estiver ativada no servidor.
7. **Eventos agendados e réplica.** Mais nicho — só vale priorizar se o
   público-alvo administra bancos com replicação ativa; listar
   `SHOW EVENTS`/`SHOW SLAVE STATUS` é barato de implementar (mesma receita
   de `MetadataService`) uma vez que o item 1 já abriu o precedente de "tela
   de nível servidor, não de schema".

## Lacunas — lado desenvolvedor

Menos crítico que o bloco DBA (o dev já tem um editor+autocomplete+grade
bons), mas há buracos reais no ciclo de vida de trabalhar com dados:

1. **Importar dados.** Hoje só existe exportação (Excel). Sem importar CSV/
   planilha para uma tabela existente, popular uma tabela de teste ainda
   exige escrever `INSERT` na mão ou usar outra ferramenta.
2. **Exportar em mais formatos.** Só `.xlsx` hoje — CSV e "gerar como
   `INSERT` statements" (para levar um punhado de linhas de um ambiente para
   outro) são os dois formatos mais pedidos em qualquer IDE de banco.
3. **Diagrama ER.** Visualizar as relações de um schema inteiro como grafo
   (não só uma FK de cada vez, como o Inspetor já faz) é o recurso mais
   citado como "faltando" em qualquer comparação com Workbench/DBeaver.
4. **Editor de procedure/function com debug básico.** Hoje só visualiza o
   DDL; não dá para editar uma procedure existente na própria IDE nem
   inspecionar variáveis durante a execução.
5. **Templates/snippets de SQL** reutilizáveis (além da biblioteca de
   queries salvas já existente) — parametrizáveis, com placeholder tipo
   `${tabela}`, para acelerar tarefas repetitivas.
6. **Controle de versão dos scripts salvos.** As queries salvas hoje são um
   arquivo local; um histórico de mudanças (mesmo que só "versões anteriores
   desta query salva", sem chegar a integrar Git de verdade) evita perder
   uma versão anterior que funcionava.

## Lacunas transversais (afetam os dois públicos)

- **Multi-banco.** README já lista isto no roadmap; vale reforçar que
  qualquer feature nova de administração (usuários, processlist, variáveis)
  deveria já nascer atrás da interface `DatabaseDialect`, para não duplicar
  trabalho quando Postgres entrar (Postgres tem um modelo de
  roles/privilégios BEM diferente do MySQL — `pg_roles`, `GRANT` por
  schema/tabela com sintaxe própria — então a UI de privilégios precisa ser
  desenhada pensando nisso desde já, não só para MySQL).
- **SSL/TLS e túnel SSH na conexão.** Qualquer banco corporativo real exige
  isto; hoje `ConnectionProfile` não tem nenhum campo para certificado/modo
  SSL nem túnel — é provavelmente o maior bloqueador silencioso para adoção
  fora de um MySQL local de desenvolvimento.
- **Auditoria.** O histórico de execuções existe, mas é local à máquina do
  usuário — não serve como trilha de auditoria de "quem fez o quê no banco",
  que é o que um DBA/compliance pede. Isso exigiria ou ler o audit log do
  próprio MySQL (plugin `audit_log`, se instalado) ou aceitar que este tipo
  de auditoria fica fora do escopo de uma ferramenta cliente.

## Priorização sugerida

| Fase | Foco | Itens |
|---|---|---|
| 1 | Fecha o pedido explícito | Tela de Usuários e Privilégios (com roles), SSL/TLS na conexão |
| 2 | DBA do dia a dia | Process list + kill, manutenção de tabela (OPTIMIZE/ANALYZE/CHECK), variáveis/status |
| 3 | Completa o ciclo do dev | Importar CSV, exportar CSV/INSERT, diagrama ER |
| 4 | Nicho / maturidade | Backup/restore, EXPLAIN visual, eventos/réplica, editor de procedure |

Fase 1 é a que resolve diretamente o que foi pedido nesta conversa. As demais
ficam como próximos passos — nenhuma depende de reescrever o que já existe,
todas encaixam na arquitetura atual (`DatabaseDialect` + árvore de objetos +
mesmo padrão de diálogo do `DdlAssistantDialog`).
