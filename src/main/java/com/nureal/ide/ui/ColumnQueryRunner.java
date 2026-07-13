package com.nureal.ide.ui;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Igual a {@link QueryRunner}, so que tambem devolve os NOMES das colunas —
 * necessario para consultas cujo conjunto de colunas VARIA (ex.:
 * {@code SHOW SLAVE STATUS}/{@code SHOW MASTER STATUS}, cujas colunas mudam
 * entre versoes do MySQL, ver {@code EventsReplicationDialog}), ao contrario
 * de {@link QueryRunner} (usado onde as colunas ja sao conhecidas de
 * antemao — o proprio SELECT foi escrito por esta IDE, ver
 * {@link ProcessListDialog}/{@link ServerStatusDialog}).
 */
@FunctionalInterface
interface ColumnQueryRunner {
    void query(String sql, BiConsumer<List<String>, List<Object[]>> onResult, Consumer<Exception> onError);
}
