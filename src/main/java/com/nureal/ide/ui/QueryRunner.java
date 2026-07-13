package com.nureal.ide.ui;

import java.util.List;
import java.util.function.Consumer;

/**
 * Executa uma consulta de LEITURA (SELECT/SHOW) em segundo plano e entrega as
 * linhas (ja na EDT) como {@code Object[]} — uma entrada por coluna, na ordem
 * do SELECT — ou o erro. Promovido de {@code UserManagementDialog} (onde
 * nasceu) para topo do pacote porque {@link ProcessListDialog},
 * {@link ServerStatusDialog} e outros dialogos de administracao do servidor
 * tambem precisam ler resultado (nao so executar comandos — ver
 * {@code DdlAssistantDialog.DdlRunner}, que so faz {@code executeUpdate}).
 * Implementado em {@code MainWindow#runQuery}.
 */
@FunctionalInterface
interface QueryRunner {
    void query(String sql, Consumer<List<Object[]>> onRows, Consumer<Exception> onError);
}
