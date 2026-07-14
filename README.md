# Nureal Database IDE

IDE para desenvolvedores de banco de dados. Desktop, multiplataforma (Windows, macOS e
Linux — ver [Compatibilidade multiplataforma](#compatibilidade-multiplataforma)),
começando por **MySQL** e evoluindo para multi-banco. Foco principal: **autocomplete
ultrarrápido** ao editar SQL.

## Status: protótipo em evolução (v0.4)

O que já funciona:

- **Gerenciador de conexões persistente**: conexões salvas em arquivo na pasta do
  usuário, listadas no painel esquerdo. Duplo-clique conecta. Nova/Editar/Excluir,
  com botão **"Testar conexão"** no formulário para validar host/porta/usuário/senha
  antes de salvar (sem precisar sair do formulário e conectar de verdade primeiro)
- Conexão MySQL com senha opcional (salva cifrada ou solicitada ao conectar)
- Leitura da estrutura do banco em **uma única consulta** ao `information_schema`
- Cache de metadados em memória (autocomplete nunca consulta o banco ao digitar)
- **Autocomplete sensível ao contexto do cursor**:
  - após `FROM`/`JOIN`/`UPDATE`/`INTO` → sugere tabelas
  - após `alias.` ou `tabela.` → sugere as colunas daquela tabela (resolve o alias)
  - demais posições → palavras-chave + tabelas + colunas
- Editor SQL com syntax highlighting; execução com F5; resultados em grade
- **Formatador de SQL** (Ctrl+Shift+F) com 3 presets de estilo (RIVER, STANDARD,
  COMMA_FIRST), caixa de palavra-chave configurável e **regra da maioria para
  caixa de alias**: conta quantos alias de tabela/coluna o usuário escreveu
  maiúsculo vs. minúsculo e normaliza todos (definição + toda referência
  `alias.coluna`) para a maioria — protege contra o MySQL enxergar `p` e `P`
  como alias diferentes na mesma instrução
- **Faixa de contexto do workspace**, colada acima das abas do editor SQL,
  com cor própria por conexão: mostra sem ambiguidade a qual conexão/banco/
  esquema qualquer instrução da aba ativa será enviada ao Executar — essencial
  com várias conexões abertas ao mesmo tempo
- **Assistente de DDL guiado** (menu de contexto: "Nova tabela..." / "Alterar
  tabela..."): cria uma tabela do zero ou adiciona colunas/chaves estrangeiras/
  índices a uma existente (sempre aditivo no modo alterar — nunca MODIFY/DROP),
  com abas para colunas, FKs, índices, **sugestões automáticas de normalização**
  (chave primária ausente, tipos de dado, grupos repetitivos, dependência
  parcial/transitiva, índices de FK ausentes) e pré-visualização do DDL já
  formatado antes de executar
- **Inspetor Flutuante de Chave Estrangeira**: botão direito num valor de FK
  no resultado (ex.: o `5` da coluna `cliente_id`) → "Visualizar Origem" abre
  uma janela **não-modal** (não bloqueia a IDE, pode ser arrastada/redimensionada/
  levada para outro monitor) já filtrada pelo registro de origem, com barra de
  filtro editável por coluna referenciada (apagar/trocar o valor ou limpar para
  ver todos os registros). A grade interna é um resultado de verdade — suporta
  o mesmo menu de contexto, inclusive abrir **outro** inspetor a partir de uma
  FK vista dentro do inspetor (navegação em cadeia pelas relações)
- **Confirmação de segurança** antes de rodar comandos de risco (DELETE/UPDATE
  sem WHERE, DROP, TRUNCATE, ALTER/CREATE/RENAME)
- **Exportação dos resultados para Excel** (.xlsx, via Apache POI/SXSSF)
- Browser de objetos (árvore de tabelas/colunas)

## Onde as conexões são salvas

```
<pasta do usuário>/.nureal-ide/connections.conf
```

> A senha, quando "Salvar senha" está marcado, é cifrada com **AES-256/GCM**
> usando uma chave própria desta instalação, gerada no primeiro uso e guardada em
> `~/.nureal-ide/.connections.key` (permissões restritas ao dono do perfil).
> Isto **não é** um cofre do sistema operacional (Windows Credential Manager/DPAPI):
> quem tiver acesso total ao perfil do usuário pode, em teoria, achar a chave.
> Próximo passo: mover a guarda da chave para o Windows Credential Manager (via DPAPI).

## Arquitetura

```
ui/                  Swing: janela, painel de conexões, diálogos
core/
  connection/        ConnectionManager + ConnectionProfile + ConnectionStore (persistência)
  dialect/           DatabaseDialect (interface) + MySqlDialect
  metadata/          MetadataService (lê) + MetadataCache (guarda) + model/
  autocomplete/      CaretContextResolver (contexto do cursor) + SqlCompletionProvider
```

O ponto de extensão multi-banco é a interface `DatabaseDialect`: cada novo banco
é uma nova implementação, sem alterar UI, metadados ou autocomplete.

## Como rodar

### Eclipse
1. *File → Import → Maven → Existing Maven Projects*
2. Selecione a pasta `nureal-database-ide`
3. Deixe o Maven baixar as dependências
4. Rode `com.nureal.ide.App` como *Java Application*

### Linha de comando
```bash
mvn compile
mvn exec:java
```

## Gerar o instalador e publicar no GitHub (Releases)

O projeto já vem com um workflow do GitHub Actions (`.github/workflows/release.yml`)
que, **ao publicar uma tag**, compila tudo e gera automaticamente os instaladores das
3 plataformas em paralelo, cada um com a Java embutida (quem baixar não precisa ter
Java instalado):

| Plataforma | Arquivo(s) | Job |
|---|---|---|
| Windows 10 ou superior | `.exe` | `windows-installer` |
| macOS — Apple Silicon | `.dmg` (sufixo `-arm64`) | `macos-installer` (runner `macos-14`) |
| macOS — Intel | `.dmg` (sufixo `-x64`) | `macos-installer` (runner `macos-13`) |
| Linux (qualquer distro) | `.AppImage` | `linux-installer` |
| Linux (Debian/Ubuntu) | `.deb` | `linux-installer` |

Um job final (`publish-release`) espera os 3 anteriores terminarem e publica todos os
artefatos juntos num único GitHub Release.

### Primeira vez: subir o projeto para o GitHub
```bash
cd nureal-database-ide
git init
git add .
git commit -m "Nureal Database IDE"
git branch -M main
git remote add origin https://github.com/<seu-usuario>/<seu-repo>.git
git push -u origin main
```

### Publicar uma versão (gera todos os instaladores sozinho)
```bash
git tag v0.1.0
git push origin v0.1.0
```
Isso dispara o workflow. Em alguns minutos, vá em **Releases** no GitHub: o `.exe`,
os dois `.dmg`, o `.deb`, o `.AppImage` e o `.jar` portátil estarão lá para download.
Para uma nova versão, repita com `v0.2.0`, etc.

### Instalar em outra máquina
- **Windows**: baixe o `.exe`, dê duplo-clique e instale (Windows 10 ou superior).
  Como o instalador ainda não é assinado digitalmente, o SmartScreen pode avisar
  "O Windows protegeu o computador" — clique em *Mais informações* → *Executar
  assim mesmo*.
- **macOS**: baixe o `.dmg` da sua arquitetura (`-arm64` para Apple Silicon M1/M2/M3/M4,
  `-x64` para Intel), abra e arraste para Aplicativos. O `.dmg` **não é assinado/
  notarizado** (não há conta Apple Developer configurada neste repositório) — se o
  Gatekeeper bloquear a abertura, clique com o botão direito no app → *Abrir*, ou rode
  `xattr -cr "Nureal Database IDE.app"` no Terminal.
- **Linux**: instale o `.deb` (`sudo dpkg -i *.deb` em Debian/Ubuntu) **ou** dê
  permissão de execução ao `.AppImage` (`chmod +x *.AppImage`) e rode direto, sem
  instalar nada — funciona em qualquer distro.

### (Opcional) Gerar o instalador localmente
Precisa de JDK 17+ com `jpackage`.

**Windows** (adicionalmente, [WiX Toolset 3.x](https://github.com/wixtoolset/wix3/releases)):
```powershell
mvn -DskipTests package
mkdir target\dist
copy target\nureal-database-ide.jar target\dist\
jpackage --type exe --name "Nureal Database IDE" --app-version 0.1.0 `
  --input target\dist --main-jar nureal-database-ide.jar `
  --main-class com.nureal.ide.App --dest target\installer `
  --win-menu --win-shortcut
```

**macOS**:
```bash
mvn -DskipTests package
mkdir -p target/dist && cp target/nureal-database-ide.jar target/dist/
jpackage --type dmg --name "Nureal Database IDE" --app-version 0.1.0 \
  --input target/dist --main-jar nureal-database-ide.jar \
  --main-class com.nureal.ide.App --dest target/installer
```

**Linux** (`.deb`; para `.AppImage` ver os passos do workflow, que usa o
`appimagetool`):
```bash
mvn -DskipTests package
mkdir -p target/dist && cp target/nureal-database-ide.jar target/dist/
jpackage --type deb --name "Nureal Database IDE" --app-version 0.1.0 \
  --input target/dist --main-jar nureal-database-ide.jar \
  --main-class com.nureal.ide.App --dest target/installer \
  --linux-shortcut
```

## Compatibilidade multiplataforma

O código já é escrito para rodar em Windows, macOS e Linux sem caminhos condicionais
por sistema operacional na maior parte da IDE (Swing + JDK puro). Pontos que merecem
atenção:

- **Persistência de conexões e chave de criptografia** (`ConnectionStore`/`LocalVault`):
  usa `System.getProperty("user.home")` (funciona nos 3 sistemas) e trata a ausência de
  permissões POSIX no Windows com um `catch` dedicado — já testado/pensado para os 3.
- **Fonte da interface**: a lista de fontes preferidas já inclui `Segoe UI` (Windows),
  `SF Pro Text` (macOS) e `Noto Sans`/`DejaVu Sans`/`Liberation Sans` (Linux), com
  fallback para a fonte padrão do sistema se nenhuma bater.
- **Backup/Restore via `mysqldump`/`mysql`** (`MySqlDumpRunner`): chama os binários
  pelo nome (resolvido via `PATH`), sem caminho fixo de nenhum SO — funciona em
  qualquer plataforma desde que o MySQL Client Tools esteja instalado.
- **Atualização automática** (`UpdateInstallLauncher`): hoje só sabe *auto-instalar*
  no Windows (baixa e executa o `.exe` diretamente). Em macOS/Linux, a checagem de nova versão
  continua funcionando, mas a IDE cai no "plano B" (abre a página do Release no
  navegador) em vez de instalar sozinha — os novos instaladores `.dmg`/`.deb`/
  `.AppImage` já ficam publicados no Release, só não são baixados/instalados
  automaticamente pela própria IDE ainda.
- **Atalhos de teclado**: todos usam `Ctrl` (ex.: `Ctrl+Enter` para executar,
  `Ctrl+Shift+F` para formatar). Funciona em macOS (o teclado tem uma tecla Ctrl),
  mas não segue a convenção nativa do Mac de usar `Cmd` — puramente cosmético, não
  impede o uso.
- **Barra de título customizada** (`JFrame.setDefaultLookAndFeelDecorated`, via
  FlatLaf): suportada nos 3 sistemas pelo FlatLaf, com aparência um pouco menos
  "nativa" no macOS do que no Windows/Linux (limitação conhecida do FlatLaf, não
  específica desta IDE).

Nada encontrado que impeça a IDE de abrir/funcionar em macOS ou Linux — o app nunca
tinha sido empacotado/testado nessas plataformas antes (só existia instalador
Windows), então vale um teste manual do `.dmg`/`.AppImage`/`.deb` gerados pelo
workflow antes de anunciar suporte oficial.

## Requisitos

- JDK 17+ (apenas para desenvolver/compilar — o instalador já embute o Java)
- MySQL acessível para testar a conexão

## Próximos passos (roadmap)

1. Índice Trie por prefixo no cache de metadados (escala para milhares de colunas)
2. Pool de conexões (HikariCP) e múltiplas abas/sessões
3. Resultados em streaming (fetch size + grid virtualizado)
4. Cofre de credenciais do SO para as senhas (Windows Credential Manager)
5. Suporte multi-banco (Postgres, SQL Server, Oracle)
```
