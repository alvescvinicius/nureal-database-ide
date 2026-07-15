package com.nureal.ide.ui;

// OBSOLETO — pode apagar este arquivo.
//
// Existia pra baixar o instalador dentro da propria IDE e rodar ele
// automaticamente (via UpdateInstallLauncher/UpdateDownloader, tambem
// obsoletos). O fluxo de atualizacao agora e sempre MANUAL: o botao
// "Baixar" da UpdateBanner so abre a pagina do Release no navegador (ver
// MainWindow#onInstallUpdate) — o usuario baixa e roda o instalador certo
// pra sua plataforma sozinho, igual ja funcionava em macOS/Linux (o
// auto-instalar so existia no Windows; agora o comportamento e o MESMO nos
// 3 sistemas).
//
// Nao foi possivel apagar o arquivo de fato nesta sessao (o mount usado
// aqui nao permite unlink/rename, so escrita de conteudo) — o corpo da
// classe foi removido pra nao quebrar a compilacao (referenciava
// GithubRelease.Asset/findExeAsset(), que tambem foram removidos). Apague
// manualmente quando puder.
