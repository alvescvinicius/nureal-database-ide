package com.nureal.ide.modulos.iachat.infraestrutura.tool;
import com.nureal.ide.modulos.iachat.dominio.entidades.ToolResult;

import java.util.List;

/**
 * Dado tabular de uma execucao de {@link ExecuteSqlTool} — o
 * {@code structuredData} de {@link ToolResult} para esta tool. Formato
 * simples (colunas + linhas de {@code Object}) de proposito: este pacote
 * ({@code modulos.iachat}) nunca importa {@code javax.swing.*}, entao nao pode
 * devolver um {@code TableModel}/{@code ResultTableModel} pronto — quem
 * renderiza (ver {@code modulos.iachat.apresentacao.MessageRenderer}) monta a tabela Swing a
 * partir destes dados.
 */
public record SqlQueryResult(String sql, List<String> columns, List<List<Object>> rows, boolean truncated) {

    public SqlQueryResult {
        columns = List.copyOf(columns);
        rows = List.copyOf(rows);
    }
}
