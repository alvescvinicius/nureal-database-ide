package com.nureal.ide.modulos.exportacaorelacional.infraestrutura;

import com.nureal.ide.modulos.dialeto.dominio.contratos.DatabaseDialect;
import com.nureal.ide.modulos.exportacaorelacional.dominio.RowFetcher;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** {@code SELECT * FROM tabela WHERE coluna IN (...)}, em lotes se a lista de valores for grande. */
public final class JdbcRowFetcher implements RowFetcher {

    private static final int TAMANHO_LOTE_IN = 500;

    private final Connection conn;
    private final DatabaseDialect dialect;

    public JdbcRowFetcher(Connection conn, DatabaseDialect dialect) {
        this.conn = conn;
        this.dialect = dialect;
    }

    @Override
    public List<Map<String, Object>> fetch(String tabela, String coluna, List<Object> valores) throws SQLException {
        List<Map<String, Object>> resultado = new ArrayList<>();
        for (int inicio = 0; inicio < valores.size(); inicio += TAMANHO_LOTE_IN) {
            List<Object> lote = valores.subList(inicio, Math.min(inicio + TAMANHO_LOTE_IN, valores.size()));
            String placeholders = lote.stream().map(v -> "?").collect(Collectors.joining(", "));
            String sql = "SELECT * FROM " + dialect.quoteIdentifier(tabela) + " WHERE "
                    + dialect.quoteIdentifier(coluna) + " IN (" + placeholders + ")";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                for (int i = 0; i < lote.size(); i++) {
                    ps.setObject(i + 1, lote.get(i));
                }
                try (ResultSet rs = ps.executeQuery()) {
                    ResultSetMetaData md = rs.getMetaData();
                    int cols = md.getColumnCount();
                    while (rs.next()) {
                        Map<String, Object> linha = new LinkedHashMap<>();
                        for (int c = 1; c <= cols; c++) {
                            linha.put(md.getColumnName(c), rs.getObject(c));
                        }
                        resultado.add(linha);
                    }
                }
            }
        }
        return resultado;
    }
}
