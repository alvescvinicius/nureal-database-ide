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

    // ANTES era "static final Color COLOR_NULL = GridTheme.COLOR_NULL" — uma
    // copia feita UMA VEZ, no carregamento da classe. GridTheme.COLOR_NULL
    // agora muda quando o usuario alterna claro/escuro (ver GridTheme#applyPalette),
    // mas aquela copia ficava presa para sempre no valor do primeiro
    // carregamento — a cor de "null" nunca acompanhava a troca de tema. Le
    // GridTheme.COLOR_NULL diretamente (linha abaixo) em vez de cachear.

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
        applyRowBackground(table, isSelected, row, column);

        if (isNull) {
            setHorizontalAlignment(SwingConstants.LEFT);
            setForeground(GridTheme.COLOR_NULL);
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
            // Chave primaria/estrangeira DE VERDADE (metadados do banco, nao
            // heuristica de nome) sobrepoe so a COR — o alinhamento continua
            // vindo do tipo real da coluna (ver alignment() acima), pedido
            // explicito do usuario: nao mexer em como varchar/number etc. sao
            // exibidos, so trazer de volta a cor de destaque de chave.
            Color keyColor = keyHighlightColor(table, column);
            if (keyColor != null) {
                setForeground(keyColor);
            }
        }
        paintActiveCellBorder(table, row, column);
        return this;
    }

    /**
     * Cor de destaque de chave (dourado = PK, laranja = FK) para a coluna sob
     * o cursor de pintura, ou {@code null} se a coluna nao e chave de
     * nenhuma tabela (ou os metadados ainda nao carregaram). Consulta o
     * {@link ColumnMetadataResolver} guardado pela {@link ResultGrid} desta
     * tabela especifica (client property — ver {@link RendererFactory#KEY_METADATA_RESOLVER}),
     * o MESMO usado pelo indicador de FK do cabecalho e pelo popup de
     * metadados, entao o resultado e sempre consistente entre os tres.
     * Enquanto o schema ainda nao carregou, {@code resolve} dispara a carga
     * em segundo plano e agenda um {@code table.repaint()} para quando ela
     * terminar — a celula simplesmente aparece sem destaque ate la.
     */
    private static Color keyHighlightColor(JTable table, int viewColumn) {
        Object resolverObj = table.getClientProperty(RendererFactory.KEY_METADATA_RESOLVER);
        if (!(resolverObj instanceof ColumnMetadataResolver resolver)
                || !(table.getModel() instanceof ResultTableModel model)) {
            return null;
        }
        int modelColumn = table.convertColumnIndexToModel(viewColumn);
        ColumnMetadata meta = resolver.resolve(model, modelColumn, table::repaint);
        if (meta.primaryKey()) {
            return GridTheme.COLOR_PRIMARY_KEY;
        }
        if (meta.hasForeignKey()) {
            return GridTheme.COLOR_IDENTIFIER;
        }
        return null;
    }

    /**
     * Zebra discreta (linhas pares/impares) + destaque suave de hover — SO
     * quando a linha nao esta selecionada E nao tem estado de edicao
     * pendente. Selecionada (sem estado de edicao), o fundo e sempre o que
     * {@code super.getTableCellRendererComponent} ja aplicou (cinza neutro
     * configurado na tabela) e nunca e sobrescrito aqui.
     *
     * Estado de edicao pendente (ver {@link GridEditController}) — linha
     * nova (verde), linha marcada para exclusao (vermelho) ou, faltando
     * essas duas, a celula especifica editada (amarelo) — SEMPRE aparece,
     * mesmo quando a celula/linha esta selecionada (ver {@link #blendWithSelection}):
     * antes, esse destaque so pintava quando a linha NAO estava selecionada,
     * mas selecionar a linha e EXATAMENTE o que o usuario precisa fazer para
     * marca-la para exclusao (botao "Excluir linha(s)" usa a selecao) — o
     * resultado era a linha ficar cinza-selecionada, sem NENHUM sinal
     * visivel de que a exclusao foi marcada, dando a impressao de que o
     * botao "nao fazia nada" mesmo com o contador de pendencias subindo.
     */
    private void applyRowBackground(JTable table, boolean isSelected, int row, int column) {
        setOpaque(true);
        GridEditController edit = editControllerFor(table);
        if (edit != null && edit.isEditable()) {
            // row/column aqui sao indices da VIEW (ordenada/filtrada) — o
            // controller trabalha em indices de MODELO (ver seu javadoc de
            // classe), entao SEMPRE converte antes de perguntar a ele.
            int modelRow = table.convertRowIndexToModel(row);
            int modelColumn = table.convertColumnIndexToModel(column);
            if (edit.isDeletedRow(modelRow)) {
                setBackground(isSelected ? blendWithSelection(GridTheme.EDIT_DELETED_ROW) : GridTheme.EDIT_DELETED_ROW);
                return;
            }
            if (edit.isNewRow(modelRow)) {
                setBackground(isSelected ? blendWithSelection(GridTheme.EDIT_NEW_ROW) : GridTheme.EDIT_NEW_ROW);
                return;
            }
            if (edit.isDirtyCell(modelRow, modelColumn)) {
                setBackground(isSelected ? blendWithSelection(GridTheme.EDIT_DIRTY_CELL) : GridTheme.EDIT_DIRTY_CELL);
                return;
            }
        }
        if (isSelected) {
            return;
        }
        boolean hover = SelectionManager.hoverRow(table) == row;
        if (hover) {
            setBackground(GridTheme.HOVER_BACKGROUND);
        } else {
            setBackground((row % 2 == 0) ? GridTheme.ZEBRA_EVEN : GridTheme.ZEBRA_ODD);
        }
    }

    /**
     * Media simples entre a cor de estado de edicao e o cinza de selecao da
     * grade — usada quando uma linha/celula marcada (nova/excluida/suja)
     * TAMBEM esta selecionada, pra continuar transmitindo os dois sinais ao
     * mesmo tempo (a borda da celula ativa, ver {@link #paintActiveCellBorder},
     * ja distingue o foco preciso dentro da selecao).
     */
    private static Color blendWithSelection(Color editColor) {
        Color sel = GridTheme.SELECTION_BACKGROUND;
        return new Color(
                (editColor.getRed() + sel.getRed()) / 2,
                (editColor.getGreen() + sel.getGreen()) / 2,
                (editColor.getBlue() + sel.getBlue()) / 2);
    }

    private static GridEditController editControllerFor(JTable table) {
        return (table.getModel() instanceof ResultTableModel rtm) ? rtm.editController() : null;
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
