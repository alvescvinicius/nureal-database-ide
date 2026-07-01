package com.nureal.ide.ui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;

import javax.swing.BorderFactory;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableCellRenderer;

/**
 * Base comum dos renderers "por tipo" da grade de resultados
 * ({@link IdentifierCellRenderer}, {@link NumberCellRenderer},
 * {@link TemporalCellRenderer}, {@link BooleanCellRenderer},
 * {@link BinaryCellRenderer}, {@link TextCellRenderer}).
 *
 * Cada COLUNA recebe UMA instancia fixa do renderer do seu grupo — a
 * classificacao (ver {@link RendererFactory}) acontece uma unica vez, quando
 * a grade e montada, nao a cada celula pintada. NULL, porem, e uma
 * possibilidade em QUALQUER coluna (o tipo da coluna nao muda, mas uma
 * celula especifica pode nao ter valor), entao o tratamento de NULL fica
 * centralizado aqui, comum a todos os grupos, em vez de um "NullCellRenderer"
 * por coluna — o valor nulo sempre aparece em italico/cinza/esquerda,
 * independente do tipo da coluna.
 *
 * IMPORTANTE: o JTable reusa a MESMA instancia de renderer para todas as
 * celulas da coluna. {@link DefaultTableCellRenderer} cacheia
 * foreground/fonte na propria instancia entre chamadas — sem resetar
 * explicitamente a cada render, o estilo de uma celula "vaza" para a
 * proxima (bug ja visto e corrigido nesta base: resetamos ANTES de tudo).
 */
abstract class AbstractTypedCellRenderer extends DefaultTableCellRenderer {

    private static final long serialVersionUID = 1L;

    static final Color COLOR_NULL = GridTheme.COLOR_NULL;

    @Override
    public final Component getTableCellRendererComponent(JTable table, Object value,
            boolean isSelected, boolean hasFocus, int row, int column) {
        // Reset defensivo: evita vazar cor/italico de uma celula anterior
        // renderizada por esta MESMA instancia compartilhada.
        setForeground(null);
        setFont(getFont().deriveFont(Font.PLAIN));

        boolean isNull = (value == null);
        String display = isNull ? "null" : formatValue(value);
        // Trunca so a APARENCIA (nunca o valor real, que continua em
        // getValueAt para tooltip/copia/visualizador) — evita que um JSON ou
        // CLOB gigante pese na pintura a cada repaint.
        super.getTableCellRendererComponent(table, CellText.forDisplay(display), isSelected, hasFocus, row, column);
        setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
        applyRowBackground(table, isSelected, row);

        if (isNull) {
            setHorizontalAlignment(SwingConstants.LEFT);
            setForeground(COLOR_NULL);
            setFont(getFont().deriveFont(Font.ITALIC));
        } else {
            setHorizontalAlignment(alignment(value));
            Color color = colorFor(value);
            // A cor por tipo de dado fica visivel SEMPRE, mesmo com a linha
            // selecionada — a selecao usa um fundo neutro (ver ResultTable)
            // feito para nao competir com o texto colorido.
            if (color != null) {
                setForeground(color);
            }
        }
        paintActiveCellBorder(table, row, column);
        return this;
    }

    /**
     * Zebra discreta (linhas pares/impares) + destaque suave de hover — SO
     * quando a linha nao esta selecionada. Selecionada, o fundo e sempre o
     * que {@code super.getTableCellRendererComponent} ja aplicou (cinza
     * neutro configurado na tabela) e nunca e sobrescrito aqui.
     */
    private void applyRowBackground(JTable table, boolean isSelected, int row) {
        if (isSelected) {
            return;
        }
        setOpaque(true);
        boolean hover = SelectionManager.hoverRow(table) == row;
        if (hover) {
            setBackground(GridTheme.HOVER_BACKGROUND);
        } else {
            setBackground((row % 2 == 0) ? GridTheme.ZEBRA_EVEN : GridTheme.ZEBRA_ODD);
        }
    }

    /**
     * Destaque adicional (borda fina) na "celula ativa" — a de foco da
     * selecao (lead do modelo de linha E de coluna), alem do destaque de
     * linha/celula selecionada que o fundo ja fornece.
     */
    private void paintActiveCellBorder(JTable table, int row, int column) {
        int leadRow = table.getSelectionModel().getLeadSelectionIndex();
        int leadCol = table.getColumnModel().getSelectionModel().getLeadSelectionIndex();
        if (row == leadRow && column == leadCol && table.isCellSelected(row, column)) {
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(1, 1, 1, 1, GridTheme.ACTIVE_CELL_BORDER),
                    BorderFactory.createEmptyBorder(0, 7, 0, 7)));
        }
    }

    /**
     * Alinhamento horizontal (SwingConstants) para valores nao-nulos deste
     * grupo. Recebe o valor porque um mesmo grupo pode precisar de
     * alinhamentos diferentes conforme o CONTEUDO (ex.: {@link IdentifierCellRenderer}
     * alinha um ID numerico a direita mas um UUID/GUID a esquerda).
     */
    abstract int alignment(Object value);

    /** Cor do texto para o valor (nao-nulo) desta celula; {@code null} = cor padrao do tema. */
    abstract Color colorFor(Object value);

    /** Formata o valor para exibicao (datas/timestamps amigaveis; demais tipos usam toString()). */
    String formatValue(Object value) {
        return value.toString();
    }
}
