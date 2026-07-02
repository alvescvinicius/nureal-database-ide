# Nureal Database IDE

IDE para desenvolvedores de banco de dados. Desktop (Windows), começando por **MySQL**
e evoluindo para multi-banco. Foco principal: **autocomplete ultrarrápido** ao editar SQL.

## Status: protótipo em evolução (v0.4)

O que já funciona:

- **Gerenciador de conexões persistente**: conexões salvas em arquivo na pasta do
  usuário, listadas no painel esquerdo. Duplo-clique conecta. Nova/Editar/Excluir.
- Conexão MySQL com senha opcional (salva cifrada ou solicitada ao conectar)
- Leitura da estrutura do banco em **uma única consulta** ao `information_schema`
- Cache de metadados em memória (autocomplete nunca consulta o banco ao digitar)
- **Autocomplete sensível ao contexto do cursor**:
  - após `FROM`/`JOIN`/`UPDATE`/`INTO` → sugere tabelas
  - após `alias.` ou `tabela.` → sugere as colunas daquela tabela (resolve o alias)
  - demais posições → palavras-chave + tabelas + colunas
- Editor SQL com syntax highlighting; execução com F5; resultados em grade
- **Formatador de SQL** (Ctrl+Shift+F) com 3 presets de estilo (RIVER, STANDARD,
  COMMA_FIRST) e caixa de palavra-chave configurável
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

O projeto já vem com um workflow do GitHub Actions que, **ao publicar uma tag**,
compila tudo e gera automaticamente um **instalador Windows (.msi)** que embute o
Java — quem baixar não precisa ter Java instalado.

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

### Publicar uma versão (gera o instalador sozinho)
```bash
git tag v0.1.0
git push origin v0.1.0
```
Isso dispara o workflow `.github/workflows/release.yml`. Em alguns minutos, vá em
**Releases** no GitHub: o `Nureal Database IDE-0.1.0.msi` (e o `.jar` portátil)
estarão lá para download. Para uma nova versão, repita com `v0.2.0`, etc.

### Instalar em outra máquina
Baixe o `.msi` do Releases, dê duplo-clique e instale. Vai aparecer no Menu
Iniciar como **Nureal Database IDE** — não precisa de Java na máquina.

### (Opcional) Gerar o instalador localmente, no seu PC Windows
Precisa de JDK 17 e do [WiX Toolset 3.x](https://github.com/wixtoolset/wix3/releases):
```powershell
mvn -DskipTests package
mkdir target\dist
copy target\nureal-database-ide.jar target\dist\
jpackage --type msi --name "Nureal Database IDE" --app-version 0.1.0 `
  --input target\dist --main-jar nureal-database-ide.jar `
  --main-class com.nureal.ide.App --dest target\installer `
  --win-menu --win-shortcut
```

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
