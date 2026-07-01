package com.nureal.ide.ui;

import javax.swing.JTable;
import javax.swing.RowSorter.SortKey;
import javax.swing.SortOrder;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;
import java.util.ArrayList;
import java.util.List;

/**
 * Ordenacao "estilo Excel" da grade de resultados: DUAS setinhas
 * independentes no cabecalho ({@link ColumnHeaderRenderer}, zona fixa a
 * direita — ver {@link ResultTableHeader#arrowAtPoint}), uma para CRESCENTE e
 * outra para DECRESCENTE. Clicar na setinha desejada ordena diretamente
 * naquela direcao ({@link #setDirection}); clicar de novo na MESMA setinha
 * ja ativa desfaz a ordenacao (volta a ordem original da consulta) — sem
 * nenhuma das duas ativas, a coluna simplesmente nao participa da ordenacao.
 * Ctrl+clique em qualquer setinha adiciona/atualiza a coluna numa ordenacao
 * multipla (mantendo as demais colunas ja ordenadas).
 *
 * Cria e possui o {@link TableRowSorter} da tabela. IMPORTANTE:
 * {@code setAutoCreateRowSorter(false)} (em {@link ResultGrid}) so evita que
 * o {@code JTable} CRIE seu proprio {@code RowSorter} sozinho — NAO evita
 * que ele registre, por conta propria, um {@code MouseListener} nativo no
 * cabecalho que ordena a coluna INTEIRA a qualquer clique, chamando
 * {@code RowSorter.toggleSortOrder(column)}; isso acontece automaticamente
 * assim que QUALQUER {@code RowSorter} e atribuido via
 * {@code table.setRowSorter(...)}, seja ele automatico ou nao. Esse
 * mecanismo nativo ignorava por completo a zona das setinhas e ordenava com
 * um clique em QUALQUER parte do cabecalho — por isso {@code toggleSortOrder}
 * e sobrescrito abaixo como no-op: o JTable so enxerga esse metodo, entao
 * fica neutralizado, enquanto toda ordenacao real desta classe
 * ({@link #setDirection}/{@link #clear}/{@link #setSingleSort}) usa
 * {@code setSortKeys} diretamente e continua funcionando normalmente — agora
 * SO atraves das setinhas, como pretendido.
 */
final class ColumnSorter {

    private final TableRowSorter<TableModel> sorter;

    ColumnSorter(JTable table) {
        this.sorter = new TableRowSorter<>(table.getModel()) {
            @Override
            public void toggleSortOrder(int column) {
                // Ver o javadoc da classe: o JTable chama isto sozinho a
                // qualquer clique no cabecalho, fora da zona do botao de
                // ordenacao. De proposito, nao faz nada.
            }
        };
        sorter.setMaxSortKeys(Integer.MAX_VALUE);
        table.setRowSorter(sorter);
    }

    TableRowSorter<TableModel> rowSorter() {
        return sorter;
    }

    /**
     * Define a ordenacao da coluna (indice de MODELO) para a direcao
     * EXPLICITA que o usuario clicou ({@code order} e sempre ASCENDING ou
     * DESCENDING — chamado pela setinha de cima ou de baixo do cabecalho, ver
     * {@link ColumnHeaderRenderer}), com uma excecao: clicar na setinha que
     * JA esta ativa para esta coluna desfaz a ordenacao (volta para
     * UNSORTED — a ordem original da consulta), servindo de "toggle off"
     * unico jeito de remover a ordenacao pelo cabecalho sem abrir o menu de
     * contexto. Sem {@code additive} (clique simples): a coluna vira a UNICA
     * ordenacao, substituindo qualquer outra. Com {@code additive}
     * (Ctrl+clique): a coluna e adicionada/atualizada/removida dentro da
     * ordenacao multipla existente, preservando as demais colunas ja
     * ordenadas.
     */
    void setDirection(int modelColumn, SortOrder order, boolean additive) {
        List<SortKey> keys = new ArrayList<>(sorter.getSortKeys());
        int idx = indexOf(keys, modelColumn);
        SortOrder current = (idx >= 0) ? keys.get(idx).getSortOrder() : SortOrder.UNSORTED;
        SortOrder next = (current == order) ? SortOrder.UNSORTED : order;

        if (!additive) {
            sorter.setSortKeys(next == SortOrder.UNSORTED
                    ? List.of() : List.of(new SortKey(modelColumn, next)));
            return;
        }

        if (idx >= 0) {
            if (next == SortOrder.UNSORTED) {
                keys.remove(idx);
            } else {
                keys.set(idx, new SortKey(modelColumn, next));
            }
        } else if (next != SortOrder.UNSORTED) {
            keys.add(new SortKey(modelColumn, next));
        }
        sorter.setSortKeys(keys);
    }

    /** Remove toda ordenacao. */
    void clear() {
        sorter.setSortKeys(List.of());
    }

    /** Define {@code modelColumn} como UNICA ordenacao, na direcao dada (usado pelo menu de contexto). */
    void setSingleSort(int modelColumn, SortOrder order) {
        sorter.setSortKeys(order == SortOrder.UNSORTED
                ? List.of() : List.of(new SortKey(modelColumn, order)));
    }

    /** Direcao atual da coluna (indice de MODELO), ou {@code SortOrder.UNSORTED}. */
    SortOrder orderOf(int modelColumn) {
        for (SortKey key : sorter.getSortKeys()) {
            if (key.getColumn() == modelColumn) {
                return key.getSortOrder();
            }
        }
        return SortOrder.UNSORTED;
    }

    /** Posicao (1-based) da coluna na ordenacao multipla; 0 se nao ordenada. */
    int priorityOf(int modelColumn) {
        List<? extends SortKey> keys = sorter.getSortKeys();
        for (int i = 0; i < keys.size(); i++) {
            if (keys.get(i).getColumn() == modelColumn) {
                return i + 1;
            }
        }
        return 0;
    }

    /** Verdadeiro se ha mais de uma coluna participando da ordenacao atual. */
    boolean isMultiSort() {
        return sorter.getSortKeys().size() > 1;
    }

    private static int indexOf(List<SortKey> keys, int modelColumn) {
        for (int i = 0; i < keys.size(); i++) {
            if (keys.get(i).getColumn() == modelColumn) {
                return i;
            }
        }
        return -1;
    }

}
