package com.nureal.ide.core.update;

// OBSOLETO — pode apagar este arquivo.
//
// Rodava o instalador ja baixado (Windows apenas — .exe/.msi diretamente ou
// via msiexec, dependendo da versao) e encerrava a IDE em seguida. O fluxo
// de atualizacao agora e sempre MANUAL em qualquer sistema operacional: o
// navegador abre a pagina do Release e o usuario baixa/roda o instalador
// sozinho (ver MainWindow#onInstallUpdate) — a IDE nunca mais dispara um
// instalador como processo filho.
//
// Nao foi possivel apagar o arquivo de fato nesta sessao (o mount usado
// aqui nao permite unlink/rename, so escrita de conteudo) — o corpo da
// classe foi removido pra nao quebrar a compilacao. Apague manualmente
// quando puder.
