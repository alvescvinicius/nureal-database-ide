package com.nureal.ide.modulos.populador.infraestrutura;

import com.nureal.ide.modulos.dialeto.dominio.contratos.DatabaseDialect;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Insere as linhas geradas em lotes (mesmo padrao de
 * {@code ObjectDataTransfer#runCsvImport}: {@code autoCommit(false)}, lotes
 * de {@value #TAMANHO_LOTE}, commit no fim ou rollback em erro, autoCommit
 * restaurado no {@code finally}). Cancelavel — checa {@code cancelado} entre
 * cada linha, nao so entre lotes, pra reagir rapido a um clique em
 * "Cancelar" mesmo com lotes grandes.
 */
public final class PopuladorExecutor {

    private static final int TAMANHO_LOTE = 500;

    private PopuladorExecutor() {
    }

    public interface ProgressoListener {
        void onProgresso(int inseridas);
    }

    /** @return quantas linhas foram de fato inseridas (pode ser menor que {@code linhas.size()} se cancelado no meio). */
    public static int inserir(Connection conn, DatabaseDialect dialect, String tabela,
            List<Map<String, Object>> linhas, ProgressoListener listener, Supplier<Boolean> cancelado)
            throws SQLException {
        if (linhas.isEmpty()) {
            return 0;
        }
        List<String> colunas = new ArrayList<>(linhas.get(0).keySet());
        String sql = "INSERT INTO " + dialect.quoteIdentifier(tabela) + " ("
                + colunas.stream().map(dialect::quoteIdentifier).collect(Collectors.joining(", ")) + ") VALUES ("
                + colunas.stream().map(c -> "?").collect(Collectors.joining(", ")) + ")";

        boolean autoCommitOriginal = conn.getAutoCommit();
        conn.setAutoCommit(false);
        int inseridas = 0;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int noLote = 0;
            for (Map<String, Object> linha : linhas) {
                if (cancelado.get()) {
                    break;
                }
                for (int i = 0; i < colunas.size(); i++) {
                    ps.setObject(i + 1, linha.get(colunas.get(i)));
                }
                ps.addBatch();
                noLote++;
                if (noLote >= TAMANHO_LOTE) {
                    ps.executeBatch();
                    inseridas += noLote;
                    noLote = 0;
                    if (listener != null) {
                        listener.onProgresso(inseridas);
                    }
                }
            }
            if (noLote > 0) {
                ps.executeBatch();
                inseridas += noLote;
                if (listener != null) {
                    listener.onProgresso(inseridas);
                }
            }
            conn.commit();
            return inseridas;
        } catch (SQLException ex) {
            conn.rollback();
            throw ex;
        } finally {
            conn.setAutoCommit(autoCommitOriginal);
        }
    }
}
