# Módulo: Atualização do Aplicativo

> **Progresso**: concluído por completo — `RepositorioDeReleasesPort`
> extraído e implementado por `UpdateChecker`; todas as classes movidas
> fisicamente para `modulos.atualizacao.{dominio,infraestrutura}`. Verificado
> por `mvn clean test` — 162 testes, mesma única falha pré-existente.

## Objetivo

Especificar a migração de `core.update.*` para `modulos/atualizacao/`, o módulo mais simples e de menor risco desta lista — bom candidato para validar o processo de migração antes de atacar módulos maiores.

## Estado atual

`AppVersion` (lê versão do manifesto do JAR), `GithubRelease` (record), `SemVer`, `UpdateChecker` (HTTP síncrono via `java.net.http.HttpClient` + `core.json.JsonParser`), `UpdatePreferences`. Sem interfaces, mas também sem vazamento de framework relevante — é infraestrutura de rede consultando uma API externa (GitHub Releases), o que é apropriado.

## Estado alvo

```
modulos/atualizacao/
  README.md
  apresentacao/
    UpdateBanner.java                  (movido de ui/, sem mudança)
  aplicacao/
    casos-de-uso/
      verificar-atualizacao-disponivel/
        verificar-atualizacao-disponivel.input.java   (versao atual)
        verificar-atualizacao-disponivel.output.java  (semNovaVersao | novaVersaoDisponivel(GithubRelease) | erro: FALHA_DE_REDE)
        verificar-atualizacao-disponivel.handler.java (era UpdateChecker.check)
  dominio/
    entidades/
      SemVer.java, GithubRelease.java   (sem mudança)
    contratos/
      RepositorioDeReleasesPort.java    (novo: interface — buscar release mais recente)
  infraestrutura/
    RepositorioDeReleasesGithub.java     (implementa a porta usando HttpClient + JsonParser)
    AppVersion.java, UpdatePreferences.java   (sem mudança, apenas pacote)
```

## Regras específicas

1. Falha de rede ao checar atualização é um `Output` tipado (`FALHA_DE_REDE`), nunca deve travar ou atrasar visivelmente a inicialização da IDE — comportamento que já deve existir hoje (checagem em background) e deve ser preservado.
2. `RepositorioDeReleasesPort` existe para permitir, no futuro, trocar a fonte de releases (ex.: um espelho interno) sem tocar no caso de uso.

## Critério de aceite

- [ ] Checagem de atualização na inicialização continua assíncrona e não bloqueia a UI.
- [ ] Banner de atualização, notas de release e "pular esta versão" continuam funcionando de forma idêntica.
