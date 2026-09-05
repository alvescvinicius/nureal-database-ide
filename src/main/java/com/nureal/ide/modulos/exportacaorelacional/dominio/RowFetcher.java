package com.nureal.ide.modulos.exportacaorelacional.dominio;

import java.util.List;
import java.util.Map;

/**
 * Busca linhas de {@code tabela} onde {@code coluna} esteja em {@code valores}
 * — abstrai o JDBC de verdade (ver {@code infraestrutura.JdbcRowFetcher}) do
 * caminhamento do grafo de FK ({@link FecharDependenciasHandler}), que assim
 * fica puro/testavel com uma implementacao fake em memoria.
 */
public interface RowFetcher {
    List<Map<String, Object>> fetch(String tabela, String coluna, List<Object> valores) throws Exception;
}
