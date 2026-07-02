package com.nureal.ide.ui;

import com.nureal.ide.core.connection.ConnectionManager;
import com.nureal.ide.core.log.AppLogger;
import com.nureal.ide.core.metadata.MetadataService;
import com.nureal.ide.core.metadata.model.TableDetails;

import javax.swing.SwingWorker;
import java.sql.Connection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cache (por sessao) dos metadados completos de uma tabela — {@link TableDetails}:
 * colunas (com PK/comentario/nullable), indices e chaves estrangeiras —
 * carregados sob demanda em segundo plano.
 *
 * Generaliza o antigo "fkCache" que existia so para o indicador de FK do
 * cabecalho: agora a MESMA chamada a {@link MetadataService#loadTableDetails}
 * tambem alimenta o {@link ColumnMetadataPopup}, sem nenhum round-trip extra
 * ao banco. Cada tabela e carregada no maximo uma vez por sessao (por
 * schema+tabela); chamadas repetidas so leem o cache.
 */
final class TableMetadataCache {

    private final Map<String, TableDetails> cache = new ConcurrentHashMap<>();
    private final Set<String> loading = ConcurrentHashMap.newKeySet();
    private final MetadataService metadataService;

    TableMetadataCache(MetadataService metadataService) {
        this.metadataService = metadataService;
    }

    private static String key(String schema, String table) {
        return ((schema == null ? "" : schema) + "." + table).toLowerCase(Locale.ROOT);
    }

    /**
     * Descarta tudo o que ja foi carregado. Chamado apos DDL estrutural (ou
     * refresh manual do navegador de objetos): sem isto, a tela de
     * propriedades de uma tabela alterada (colunas/PK/indices/FKs) continua
     * mostrando os dados de ANTES do ALTER/DROP ate o app ser reiniciado,
     * mesmo que a arvore de objetos em si ja tenha sido atualizada.
     */
    void clear() {
        cache.clear();
    }

    /**
     * Detalhes ja conhecidos da tabela, ou {@code null} se ainda nao
     * carregados — nesse caso a carga e disparada em segundo plano e
     * {@code onLoaded} e chamado quando ela terminar (para o chamador
     * reconsultar o cache e atualizar a UI).
     */
    TableDetails get(ConnectionManager connectionManager, String schema, String table, Runnable onLoaded) {
        String cacheKey = key(schema, table);
        TableDetails cached = cache.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        loadAsync(connectionManager, schema, table, onLoaded);
        return null;
    }

    private void loadAsync(ConnectionManager connectionManager, String schema, String table, Runnable onLoaded) {
        String cacheKey = key(schema, table);
        if (!loading.add(cacheKey)) {
            return; // ja esta carregando esta tabela
        }
        if (schema == null || !connectionManager.isConnected()) {
            loading.remove(cacheKey);
            return;
        }
        new SwingWorker<TableDetails, Void>() {
            @Override
            protected TableDetails doInBackground() throws Exception {
                Connection conn = connectionManager.getConnection();
                return metadataService.loadTableDetails(conn, schema, table);
            }

            @Override
            protected void done() {
                loading.remove(cacheKey);
                try {
                    cache.put(cacheKey, get());
                } catch (Exception ex) {
                    AppLogger.warning("Falha ao carregar metadados da tabela " + cacheKey, ex);
                    cache.put(cacheKey, new TableDetails(List.of(), List.of(), List.of()));
                }
                if (onLoaded != null) {
                    onLoaded.run();
                }
            }
        }.execute();
    }
}
