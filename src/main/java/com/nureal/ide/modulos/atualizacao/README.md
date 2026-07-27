# Módulo: Atualização (`modulos.atualizacao`)

## Responsabilidade

Checagem de nova versão publicada no GitHub Releases do próprio projeto, comparação com a versão em execução e persistência de preferências (versão ignorada, checagem automática ligada/desligada).

## Estrutura interna

```
dominio/
  contratos/    RepositorioDeReleasesPort
  entidades/    GithubRelease
infraestrutura/ UpdateChecker (implementa RepositorioDeReleasesPort), SemVer, AppVersion, UpdatePreferences
```

`SemVer` é package-private e só usado internamente por `UpdateChecker` — não é uma porta pública do módulo, por isso fica em `infraestrutura` junto com quem o usa, sem entrada em `dominio/contratos`.

## Expõe (portas públicas)

- `RepositorioDeReleasesPort` — usado por `MainWindow` (campo `releasesRepository`) para checar atualizações, sem depender da implementação concreta (`UpdateChecker`).

## Dependências

Nenhuma dependência de outro módulo.
