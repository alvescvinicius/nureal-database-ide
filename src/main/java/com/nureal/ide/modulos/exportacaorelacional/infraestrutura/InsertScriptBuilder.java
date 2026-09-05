package com.nureal.ide.modulos.exportacaorelacional.infraestrutura;

import com.nureal.ide.modulos.dialeto.dominio.contratos.DatabaseDialect;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Monta o texto final: um {@code INSERT INTO} por linha, na ordem de
 * tabelas ja calculada (ver {@code OrdenarTabelasHandler}), embrulhado em
 * {@code SET FOREIGN_KEY_CHECKS=0/1} — rede de seguranca contra qualquer
 * ciclo de FK que a ordenacao nao resolva 100% (ver seu javadoc).
 */
public final class InsertScriptBuilder {

    private InsertScriptBuilder() {
    }

    public static String build(DatabaseDialect dialect, List<String> ordemTabelas,
            Map<String, List<Map<String, Object>>> linhasPorTabela) {
        StringBuilder sb = new StringBuilder();
        sb.append("-- Gerado pelo Exportador Relacional da Nureal Database IDE\n");
        sb.append("SET FOREIGN_KEY_CHECKS=0;\n\n");
        for (String tabela : ordemTabelas) {
            List<Map<String, Object>> linhas = linhasPorTabela.get(tabela);
            if (linhas == null || linhas.isEmpty()) {
                continue;
            }
            sb.append("-- ").append(tabela).append(" (").append(linhas.size()).append(" linha(s))\n");
            for (Map<String, Object> linha : linhas) {
                List<String> colunas = new ArrayList<>(linha.keySet());
                sb.append("INSERT INTO ").append(dialect.quoteIdentifier(tabela)).append(" (")
                        .append(colunas.stream().map(dialect::quoteIdentifier).collect(Collectors.joining(", ")))
                        .append(") VALUES (")
                        .append(colunas.stream().map(c -> SqlValueFormatter.format(linha.get(c)))
                                .collect(Collectors.joining(", ")))
                        .append(");\n");
            }
            sb.append('\n');
        }
        sb.append("SET FOREIGN_KEY_CHECKS=1;\n");
        return sb.toString();
    }
}
