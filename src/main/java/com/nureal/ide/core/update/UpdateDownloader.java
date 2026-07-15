package com.nureal.ide.core.update;

// OBSOLETO — pode apagar este arquivo.
//
// Baixava o instalador pra dentro do proprio processo da IDE (usado so por
// UpdateInstallDialog, tambem obsoleto). O fluxo de atualizacao agora e
// sempre MANUAL: o navegador abre a pagina do Release no GitHub e o proprio
// usuario baixa e roda o instalador (ver MainWindow#onInstallUpdate) — a
// IDE nao baixa mais nenhum arquivo de atualizacao sozinha.
//
// Nao foi possivel apagar o arquivo de fato nesta sessao (o mount usado
// aqui nao permite unlink/rename, so escrita de conteudo) — o corpo da
// classe foi removido pra nao quebrar a compilacao. Apague manualmente
// quando puder.
