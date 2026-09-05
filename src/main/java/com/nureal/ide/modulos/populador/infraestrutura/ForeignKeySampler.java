package com.nureal.ide.modulos.populador.infraestrutura;

import com.nureal.ide.modulos.dialeto.dominio.contratos.DatabaseDialect;
import com.nureal.ide.modulos.metadados.dominio.entidades.ForeignKeyInfo;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Amostra valores de PK JA EXISTENTES nas tabelas pai de cada FK — o
 * Populador NUNCA cria linha na tabela pai (pedido explicito do usuario),
 * so sorteia entre o que ja existe. Uma entrada por COLUNA LOCAL de FK (nao
 * por constraint inteira): para uma FK composta (2+ colunas), cada coluna e
 * amostrada INDEPENDENTEMENTE — combinacoes entre colunas nao sao
 * garantidas coerentes entre si, aceitavel para dado de teste (nao e
 * garantia de integridade referencial perfeita em FK composta, so em FK
 * simples, a grande maioria dos casos reais).
 */
public final class ForeignKeySampler {

    private static final int LIMITE_AMOSTRA = 200;

    private ForeignKeySampler() {
    }

    public static Map<String, List<Object>> amostrar(Connection conn, DatabaseDialect dialect,
            List<ForeignKeyInfo> fks) throws SQLException {
        Map<String, List<Object>> resultado = new LinkedHashMap<>();
        for (ForeignKeyInfo fk : fks) {
            for (int i = 0; i < fk.columns().size(); i++) {
                String colunaLocal = fk.columns().get(i);
                String colunaRef = fk.referencedColumns().get(i);
                resultado.put(colunaLocal, amostrarColuna(conn, dialect, fk.referencedTable(), colunaRef));
            }
        }
        return resultado;
    }

    private static List<Object> amostrarColuna(Connection conn, DatabaseDialect dialect, String tabela,
            String coluna) throws SQLException {
        List<Object> valores = new ArrayList<>();
        String sql = dialect.randomSampleQuery(tabela, coluna, LIMITE_AMOSTRA);
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                valores.add(rs.getObject(1));
            }
        }
        return valores;
    }
}
