package com.nureal.ide.modulos.exportacaorelacional.dominio;

import com.nureal.ide.modulos.metadados.dominio.entidades.SchemaForeignKey;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Mesma ideia de {@link FecharDependenciasHandler}, mas fechando o grafo por
 * NOME DE TABELA (sem linhas de dado nenhuma) — usado pelo "Gerar DDL com
 * hierarquia" ({@code TableDdlHierarchyDialog}), que so precisa saber QUAIS
 * tabelas incluir no script, nao dados delas. PAIS sempre inclusos (tabelas
 * que {@code tabelaInicial} referencia); FILHOS opcionais (tabelas que
 * referenciam {@code tabelaInicial}), mesmo criterio "usuario escolhe na
 * hora" ja usado no exportador de linhas.
 */
public final class FecharTabelasHandler {

    public Set<String> fechar(List<SchemaForeignKey> grafo, String tabelaInicial, boolean incluirFilhos) {
        Set<String> visitado = new LinkedHashSet<>();
        Deque<String> fila = new ArrayDeque<>();
        visitado.add(tabelaInicial);
        fila.add(tabelaInicial);

        while (!fila.isEmpty()) {
            String atual = fila.poll();
            for (SchemaForeignKey fk : grafo) {
                if (fk.fromTable().equalsIgnoreCase(atual) && visitado.add(fk.toTable())) {
                    fila.add(fk.toTable());
                }
            }
            if (incluirFilhos) {
                for (SchemaForeignKey fk : grafo) {
                    if (fk.toTable().equalsIgnoreCase(atual) && visitado.add(fk.fromTable())) {
                        fila.add(fk.fromTable());
                    }
                }
            }
        }
        return visitado;
    }
}
