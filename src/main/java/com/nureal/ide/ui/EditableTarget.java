package com.nureal.ide.ui;

import java.util.List;

/**
 * Descreve COMO um resultado pode ser editado direto na grade: a tabela
 * fisica unica de onde ele veio, quais colunas (indices do MODELO, nao da
 * view — ver {@link ResultGrid}) formam a chave primaria (usadas como ANCORA
 * do WHERE em UPDATE/DELETE, sempre com o valor ORIGINAL, mesmo que o usuario
 * tenha editado a propria celula da PK) e quais colunas no total sao
 * editaveis (mapeiam para uma coluna real desta MESMA tabela — expressoes/
 * funcoes/colunas de outra tabela num JOIN ficam de fora).
 *
 * Resolvido por {@code MainWindow#tryEnableEditing}: um resultado so vira
 * "editavel" quando TODAS as colunas com tabela de origem conhecida apontam
 * para a MESMA tabela (SELECT simples, sem JOIN) e essa tabela tem ao menos
 * uma coluna de chave primaria presente no resultado (sem PK nao da pra
 * identificar univocamente qual linha fisica atualizar/excluir).
 */
final class EditableTarget {

    private final String table;
    private final List<Integer> primaryKeyColumns;
    private final List<Integer> editableColumns;

    EditableTarget(String table, List<Integer> primaryKeyColumns, List<Integer> editableColumns) {
        this.table = table;
        this.primaryKeyColumns = List.copyOf(primaryKeyColumns);
        this.editableColumns = List.copyOf(editableColumns);
    }

    String table() {
        return table;
    }

    /** Indices (do modelo) das colunas de chave primaria presentes no resultado. */
    List<Integer> primaryKeyColumns() {
        return primaryKeyColumns;
    }

    /** Indices (do modelo) de TODAS as colunas editaveis, incluindo as de PK. */
    List<Integer> editableColumns() {
        return editableColumns;
    }

    boolean isEditableColumn(int modelColumn) {
        return editableColumns.contains(modelColumn);
    }
}
