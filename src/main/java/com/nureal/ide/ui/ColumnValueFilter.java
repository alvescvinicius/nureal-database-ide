package com.nureal.ide.ui;

import javax.swing.RowFilter;
import javax.swing.table.TableModel;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Filtro "por valor" estilo Excel (autofiltro): cada coluna (indice de
 * MODELO) pode ter um conjunto de valores PERMITIDOS — sem entrada no mapa,
 * a coluna nao restringe nada. Combina com o filtro de texto "inteligente"
 * ja existente ({@link SmartCellFilter}) via {@code RowFilter.andFilter}, ver
 * {@link ResultGrid#applyFilters()}.
 *
 * A lista de valores oferecida no popup de cada coluna e "em cascata" (igual
 * ao Excel de verdade): ao montar as opcoes da coluna X, os filtros JA
 * ativos nas OUTRAS colunas (e o filtro de texto da barra) restringem quais
 * linhas contam — ver {@link #rowMatchesExcluding} e
 * {@link ResultGrid#distinctValuesFor}.
 */
final class ColumnValueFilter {

    /** {@code modelColumn -> valores permitidos} (formato {@link #stringValue}); ausente = sem restricao nesta coluna. */
    private final Map<Integer, Set<String>> allowed = new LinkedHashMap<>();

    boolean isActive(int modelColumn) {
        return allowed.containsKey(modelColumn);
    }

    boolean hasAny() {
        return !allowed.isEmpty();
    }

    Set<String> allowedValues(int modelColumn) {
        return allowed.get(modelColumn);
    }

    /**
     * Define os valores permitidos da coluna. Se {@code selected} cobre TODO
     * {@code allPossible}, a coluna volta a "sem filtro" (equivalente a
     * marcar tudo no Excel = autofiltro desativado para aquela coluna).
     */
    void setAllowedValues(int modelColumn, Set<String> selected, Set<String> allPossible) {
        if (selected.containsAll(allPossible) && allPossible.containsAll(selected)) {
            allowed.remove(modelColumn);
        } else {
            allowed.put(modelColumn, new LinkedHashSet<>(selected));
        }
    }

    void clear(int modelColumn) {
        allowed.remove(modelColumn);
    }

    void clearAll() {
        allowed.clear();
    }

    /** {@code true} se a linha (indice de MODELO) bate com os filtros de TODAS as colunas ativas, exceto {@code excludeColumn} (-1 = nenhuma excecao). */
    boolean rowMatchesExcluding(TableModel model, int row, int excludeColumn) {
        for (Map.Entry<Integer, Set<String>> e : allowed.entrySet()) {
            if (e.getKey() == excludeColumn) {
                continue;
            }
            if (!e.getValue().contains(stringValue(model.getValueAt(row, e.getKey())))) {
                return false;
            }
        }
        return true;
    }

    /** {@code RowFilter} combinando TODAS as colunas ativas — {@code null} se nenhuma estiver ativa. */
    RowFilter<Object, Object> buildRowFilter() {
        if (allowed.isEmpty()) {
            return null;
        }
        List<RowFilter<Object, Object>> filters = new ArrayList<>();
        for (Map.Entry<Integer, Set<String>> e : allowed.entrySet()) {
            filters.add(forColumn(e.getKey(), e.getValue()));
        }
        return filters.size() == 1 ? filters.get(0) : RowFilter.andFilter(filters);
    }

    private static RowFilter<Object, Object> forColumn(int modelColumn, Set<String> allowedValues) {
        return new RowFilter<>() {
            @Override
            public boolean include(Entry<?, ?> entry) {
                return allowedValues.contains(entry.getStringValue(modelColumn));
            }
        };
    }

    /** Mesma conversao usada por {@code RowFilter.Entry#getStringValue} (null vira ""), para as duas pontas (filtro real e calculo de valores distintos) baterem sempre. */
    static String stringValue(Object value) {
        return value == null ? "" : value.toString();
    }

    /**
     * Ordena valores tratando texto numerico como numero (1, 2, 10 em vez de
     * 1, 10, 2) — reaproveita {@link SmartCellFilter#parseNumber} (aceita
     * tanto "1234.56" quanto o formato BR "1.234,56"), a MESMA logica de
     * reconhecimento numerico que a barra de filtro usa. Antes chamava
     * {@code Double.parseDouble} direto, que rejeita o formato BR — um
     * valor como "1.234,56" caia no fallback de comparacao TEXTUAL aqui
     * (ordem alfabetica) mesmo sendo reconhecido como numero na barra de
     * filtro, uma divergencia encontrada numa auditoria pedida pelo
     * usuario.
     */
    static Set<String> newSortedSet() {
        return new TreeSet<>((a, b) -> {
            if (a.isEmpty() != b.isEmpty()) {
                return a.isEmpty() ? -1 : 1;
            }
            Double na = SmartCellFilter.parseNumber(a);
            Double nb = SmartCellFilter.parseNumber(b);
            if (na != null && nb != null) {
                return Double.compare(na, nb);
            }
            return a.compareToIgnoreCase(b);
        });
    }
}
