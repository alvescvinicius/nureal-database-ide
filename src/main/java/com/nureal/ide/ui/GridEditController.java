package com.nureal.ide.ui;

import com.nureal.ide.core.dialect.DatabaseDialect;
import com.nureal.ide.core.log.AppLogger;

import javax.swing.SwingUtilities;
import javax.swing.event.TableModelEvent;
import java.lang.reflect.InvocationTargetException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Controla a edicao "em lote" da grade de resultados: nada toca o banco
 * enquanto o usuario edita celulas, adiciona ou marca linhas para excluir —
 * tudo fica PENDENTE (marcado visualmente, ver {@link AbstractTypedCellRenderer})
 * ate {@link #apply} ser chamado (botao "Salvar alteracoes"), que aplica tudo
 * numa UNICA transacao (tudo ou nada) via {@code PreparedStatement}. {@link #discardAll}
 * reverte tudo sem tocar no banco.
 *
 * So existe (ver {@link #enable}) quando {@code MainWindow} resolveu um
 * {@link EditableTarget} para o resultado: uma UNICA tabela fisica de origem,
 * com chave primaria conhecida presente no result set — sem isso a grade
 * continua somente-leitura, como sempre foi.
 *
 * Indices de linha SEMPRE se referem ao MODELO (nao a view ordenada/filtrada
 * do JTable) — quem chama a partir de eventos de mouse/selecao precisa
 * converter com {@code table.convertRowIndexToModel(viewRow)} antes.
 */
final class GridEditController {

    private final ResultTableModel model;

    private EditableTarget target;
    private Runnable onChange;

    /**
     * {@code true} somente quando o usuario LIGOU explicitamente o "Modo de
     * edicao" (botao dedicado na barra de resultado, ver {@code ResultStatusBar}).
     * Ter um {@link #target} resolvido (ver {@link #enable}) so significa que
     * este resultado E CAPAZ de ser editado — pedido explicito do usuario:
     * enquanto o modo estiver desligado, o resultado deve se comportar como
     * puramente visual/navegacao (mesmo que capaz), e so incluir/editar/excluir
     * depois que o usuario ligar o modo de proposito. Comeca sempre desligado,
     * mesmo quando {@link #enable} acaba de resolver um alvo editavel.
     */
    private boolean editModeOn;

    /** Linhas adicionadas nesta sessao de edicao (ainda nao inseridas no banco). */
    private final Set<Integer> newRows = new HashSet<>();
    /** Linhas marcadas para exclusao (linhas normais OU novas — nesse caso, so somem localmente). */
    private final Set<Integer> deletedRows = new HashSet<>();
    /** row -> conjunto de colunas com valor editado (para linhas JA existentes). */
    private final Map<Integer, Set<Integer>> dirtyCells = new HashMap<>();
    /**
     * row -> valores ORIGINAIS de TODAS as colunas, capturados quando a linha
     * foi vista pela primeira vez (existente, carregada do banco — nunca
     * para linhas novas). Serve dois propositos: (1) ancora do WHERE em
     * UPDATE/DELETE (sempre os valores de PK originais, mesmo que a propria
     * PK tenha sido editada) e (2) permite {@link #discardAll} devolver o
     * valor exibido em cada celula dirty ao que era antes, nao so "esquecer"
     * que ela foi editada deixando o valor errado visivel na grade.
     */
    private final Map<Integer, Object[]> originalRow = new HashMap<>();

    /**
     * {@code true} enquanto o PROPRIO controller esta mudando o modelo
     * (reverter no discardAll, preencher chave gerada, remover linha apos
     * commit) — suprime o listener abaixo, que senao interpretaria essas
     * escritas internas como "o usuario editou de novo" e re-marcaria a
     * celula como suja logo depois de temos acabado de limpar.
     */
    private boolean suppressEvents;

    GridEditController(ResultTableModel model) {
        this.model = model;
        model.addTableModelListener(this::onModelEvent);
    }

    /**
     * Reage a QUALQUER mudanca de celula do modelo (inclusive as que o
     * proprio JTable aplica ao terminar a edicao de uma celula) marcando-a
     * como suja — exceto as que o controller mesmo provocou (ver
     * {@link #suppressEvents}) e as de linhas novas (nada a comparar com
     * "original"; o INSERT sempre le o valor atual direto do modelo).
     */
    private void onModelEvent(TableModelEvent e) {
        if (suppressEvents || target == null) {
            return;
        }
        if (e.getType() != TableModelEvent.UPDATE) {
            return; // insercoes/remocoes de linha sao tratadas explicitamente pelos metodos publicos
        }
        int col = e.getColumn();
        if (col == TableModelEvent.ALL_COLUMNS || !target.isEditableColumn(col)) {
            return;
        }
        boolean changed = false;
        for (int row = Math.max(0, e.getFirstRow()); row <= e.getLastRow() && row < model.getRowCount(); row++) {
            if (newRows.contains(row)) {
                continue;
            }
            dirtyCells.computeIfAbsent(row, k -> new HashSet<>()).add(col);
            changed = true;
        }
        if (changed) {
            fireChange();
        }
    }

    /**
     * {@link #apply} roda numa {@code SwingWorker} (thread de fundo, correto
     * para o JDBC) mas precisa mutar o {@code TableModel}/disparar
     * {@link #onChange} (Swing, so pode na EDT) para preencher chave gerada e
     * reconciliar o modelo apos o commit — este helper garante isso, sem
     * exigir que TODO {@link #apply} rode na EDT (o que bloquearia a UI
     * durante o round-trip do banco).
     */
    private void runOnEdt(Runnable action) {
        if (SwingUtilities.isEventDispatchThread()) {
            action.run();
            return;
        }
        try {
            SwingUtilities.invokeAndWait(action);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        } catch (InvocationTargetException ite) {
            AppLogger.warning("Falha ao atualizar a grade na EDT", ite);
        }
    }

    private void suppressed(Runnable action) {
        runOnEdt(() -> {
            suppressEvents = true;
            try {
                action.run();
            } finally {
                suppressEvents = false;
            }
        });
    }

    void setOnChange(Runnable onChange) {
        this.onChange = onChange;
    }

    /** Liga a edicao para este resultado e tira a "foto" inicial de cada linha ja carregada. */
    void enable(EditableTarget target) {
        this.target = target;
        for (int row = 0; row < model.getRowCount(); row++) {
            snapshotRow(row);
        }
        fireChange();
    }

    boolean isEditable() {
        return target != null;
    }

    EditableTarget target() {
        return target;
    }

    /** {@code true} quando o usuario ligou o "Modo de edicao" — ver {@link #editModeOn}. */
    boolean isEditModeOn() {
        return editModeOn;
    }

    /**
     * Liga/desliga o "Modo de edicao". Desligar com alteracoes pendentes NAO
     * e impedido AQUI (fica a cargo de {@code MainWindow}, que pergunta ao
     * usuario antes de chamar isto — ver {@code ResultStatusBar#onToggleEditMode});
     * este metodo so aplica a mudanca de estado e notifica quem estiver
     * ouvindo, pra atualizar botoes/editabilidade das celulas.
     */
    void setEditModeOn(boolean on) {
        this.editModeOn = on;
        fireChange();
    }

    /** Chamado depois que paginas adicionais sao carregadas (ver MainWindow#loadPage/#loadAll). */
    void onRowsAppended(int firstNewRow) {
        if (target == null) {
            return;
        }
        for (int row = firstNewRow; row < model.getRowCount(); row++) {
            snapshotRow(row);
        }
    }

    private void snapshotRow(int row) {
        if (originalRow.containsKey(row)) {
            return;
        }
        int cols = model.getColumnCount();
        Object[] values = new Object[cols];
        for (int c = 0; c < cols; c++) {
            values[c] = model.getValueAt(row, c);
        }
        originalRow.put(row, values);
    }

    boolean isCellEditable(int row, int column) {
        return target != null && editModeOn && !deletedRows.contains(row) && target.isEditableColumn(column);
    }

    boolean isNewRow(int row) {
        return newRows.contains(row);
    }

    boolean isDeletedRow(int row) {
        return deletedRows.contains(row);
    }

    boolean isDirtyCell(int row, int column) {
        Set<Integer> cols = dirtyCells.get(row);
        return cols != null && cols.contains(column);
    }

    boolean rowHasDirtyCells(int row) {
        Set<Integer> cols = dirtyCells.get(row);
        return cols != null && !cols.isEmpty();
    }

    /** Adiciona uma linha em branco ao FIM do modelo, pronta para o usuario preencher. */
    int addNewRow() {
        suppressed(() -> model.addRow(new Object[model.getColumnCount()]));
        int row = model.getRowCount() - 1;
        newRows.add(row);
        fireChange();
        return row;
    }

    /** Marca linhas (indices de MODELO) para exclusao ao salvar. */
    void markForDelete(int[] modelRows) {
        for (int row : modelRows) {
            deletedRows.add(row);
        }
        fireChange();
    }

    void unmarkForDelete(int[] modelRows) {
        for (int row : modelRows) {
            deletedRows.remove(row);
        }
        fireChange();
    }

    boolean hasPendingChanges() {
        return !newRows.isEmpty() || !deletedRows.isEmpty() || !dirtyCells.isEmpty();
    }

    /** Quantidade de operacoes pendentes (para o rotulo "N alteracao(oes) pendente(s)"). */
    int pendingCount() {
        int updates = 0;
        for (Integer row : dirtyCells.keySet()) {
            if (!newRows.contains(row) && !deletedRows.contains(row)) {
                updates++;
            }
        }
        int inserts = 0;
        for (Integer row : newRows) {
            if (!deletedRows.contains(row)) {
                inserts++;
            }
        }
        return updates + inserts + deletedRows.size();
    }

    /**
     * Desfaz TUDO sem tocar no banco: remove linhas novas, desmarca exclusoes
     * e devolve CADA celula editada ao valor original (guardado em
     * {@link #originalRow} desde a primeira vez que a linha foi vista) — nao
     * so "esquece" a edicao deixando o valor errado visivel na grade.
     */
    void discardAll() {
        suppressed(() -> {
            for (Map.Entry<Integer, Set<Integer>> e : dirtyCells.entrySet()) {
                int row = e.getKey();
                Object[] original = originalRow.get(row);
                if (original == null) {
                    continue;
                }
                for (int col : e.getValue()) {
                    model.setValueAt(original[col], row, col);
                }
            }
        });
        dirtyCells.clear();
        deletedRows.clear();

        // Remove as linhas novas (do fim para o inicio, para os indices nao desandarem).
        List<Integer> toRemove = new ArrayList<>(newRows);
        toRemove.sort((a, b) -> b - a);
        for (int row : toRemove) {
            model.removeRow(row);
            reindexAfterRemoval(row);
        }
        fireChange();
    }

    /**
     * Aplica tudo que esta pendente numa UNICA transacao: exclusoes, depois
     * atualizacoes, depois insercoes. Qualquer erro desfaz TUDO (rollback) e
     * relanca a excecao — nada no modelo local muda nesse caso, o usuario
     * pode corrigir e tentar salvar de novo. So em caso de sucesso o modelo
     * local e reconciliado (linhas excluidas somem, marcas "dirty"/"nova"
     * somem, a "foto" da PK e refeita com os valores agora persistidos).
     */
    ApplyResult apply(Connection conn, DatabaseDialect dialect) throws SQLException {
        if (target == null) {
            return new ApplyResult(0, 0, 0);
        }
        SqlGridPersistenceEngine.Resultado r = SqlGridPersistenceEngine.apply(conn, dialect, target, model,
                deletedRows, newRows, dirtyCells, originalRow);
        if (!r.generatedKeys().isEmpty()) {
            int pkCol = target.primaryKeyColumns().get(0);
            suppressed(() -> r.generatedKeys().forEach((row, value) -> model.setValueAt(value, row, pkCol)));
        }
        reconcileAfterCommit();
        return new ApplyResult(r.inserted(), r.updated(), r.deleted());
    }

    /** Chamado ao final de {@link #apply}, possivelmente de uma thread de fundo — ver {@link #runOnEdt}. */
    private void reconcileAfterCommit() {
        runOnEdt(() -> {
            List<Integer> toRemove = new ArrayList<>(deletedRows);
            toRemove.sort((a, b) -> b - a);
            for (int row : toRemove) {
                model.removeRow(row);
                reindexAfterRemoval(row);
            }
            newRows.clear();
            dirtyCells.clear();
            // Refaz a foto de tudo que restou (cobre PK editada e chaves geradas por auto-increment).
            originalRow.clear();
            for (int row = 0; row < model.getRowCount(); row++) {
                snapshotRow(row);
            }
            fireChange();
        });
    }

    /** Reindexação das estruturas apos remover fisicamente a linha {@code removedRow} do modelo. */
    private void reindexAfterRemoval(int removedRow) {
        shiftSet(newRows, removedRow);
        shiftSet(deletedRows, removedRow);
        shiftMapKeys(dirtyCells, removedRow);
        shiftMapKeys(originalRow, removedRow);
    }

    private static void shiftSet(Set<Integer> set, int removedRow) {
        Set<Integer> shifted = new HashSet<>();
        for (int row : set) {
            if (row == removedRow) {
                continue;
            }
            shifted.add(row > removedRow ? row - 1 : row);
        }
        set.clear();
        set.addAll(shifted);
    }

    private static <V> void shiftMapKeys(Map<Integer, V> map, int removedRow) {
        Map<Integer, V> shifted = new HashMap<>();
        for (Map.Entry<Integer, V> e : map.entrySet()) {
            int row = e.getKey();
            if (row == removedRow) {
                continue;
            }
            shifted.put(row > removedRow ? row - 1 : row, e.getValue());
        }
        map.clear();
        map.putAll(shifted);
    }

    private void fireChange() {
        if (onChange != null) {
            onChange.run();
        }
    }

    /** Linhas (indices de MODELO) atualmente marcadas para exclusao, em ordem crescente — para a UI listar/confirmar. */
    Set<Integer> deletedRowsView() {
        return new TreeSet<>(deletedRows);
    }

    /** Resultado de {@link #apply}: quantas linhas foram inseridas/atualizadas/excluidas de fato. */
    record ApplyResult(int inserted, int updated, int deleted) {
        int total() {
            return inserted + updated + deleted;
        }
    }
}
