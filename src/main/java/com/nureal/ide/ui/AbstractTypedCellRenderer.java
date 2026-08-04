package com.nureal.ide.ui;
import com.nureal.ide.compartilhado.designsystem.GridTheme;
import com.nureal.ide.compartilhado.designsystem.IconType;
import com.nureal.ide.compartilhado.designsystem.Icons;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Graphics;

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableCellRenderer;

/**
 * Base comum dos renderers "por tipo" da grade de resultados
 * ({@link IdentifierCellRenderer}, {@link NumberCellRenderer},
 * {@link TemporalCellRenderer}, {@link BooleanCellRenderer},
 * {@link BinaryCellRenderer}, {@link PlainTypedCellRenderer}).
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

    /**
     * Largura fixa (px) reservada no canto direito de uma celula de chave
     * estrangeira para o icone de "ir para a origem" (ver {@link #fkIconVisible}
     * e {@link SelectionManager}, que usa esta MESMA constante para o
     * hit-test do clique) — pedido explicito do usuario: acessar "Visualizar
     * Origem" sem precisar abrir o menu de contexto.
     */
    static final int FK_ICON_ZONE_WIDTH = 18;
    private static final int FK_ICON_SIZE = 12;

    /** {@code true} so durante a pintura de UMA celula de FK (ver {@link #getTableCellRendererComponent}) — consultado por {@link #paintComponent}. */
    private boolean fkIconVisible;

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
        resetTypedState();
        fkIconVisible = false;

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
            ColumnMetadata meta = resolveMetadata(table, column);
            if (meta != null && meta.primaryKey()) {
                setForeground(GridTheme.COLOR_PRIMARY_KEY);
            } else if (meta != null && meta.hasForeignKey()) {
                setForeground(GridTheme.COLOR_IDENTIFIER);
            }
            // O icone de "ir para a origem" (ver paintComponent/SelectionManager)
            // depende SO de ser FK — DESACOPLADO da cor acima de proposito:
            // uma coluna que e PK desta tabela E FK de outra ao mesmo tempo
            // (comum em tabela de detalhe 1:1, ex.: "pessoa_id" PK de
            // "pessoa_juridica" e FK para "pessoa") caia sempre no ramo de PK
            // pra cor (prioridade preservada de proposito, ja era assim antes
            // desta mudanca), mas ainda tem uma origem valida pra visualizar —
            // bug relatado pelo usuario: o menu de contexto mostrava
            // "Visualizar Origem", mas o icone nunca aparecia nessas colunas
            // porque o "else if" antigo nunca era alcancado quando a coluna
            // tambem era PK.
            if (meta != null && meta.hasForeignKey()) {
                fkIconVisible = true;
                setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8 + FK_ICON_ZONE_WIDTH));
            }
        }
        paintActiveCellBorder(table, row, column);
        return this;
    }

    /**
     * Metadados (PK/FK) da coluna sob o cursor de pintura, ou {@code null} se
     * a coluna nao e chave de nenhuma tabela (ou os metadados ainda nao
     * carregaram). Consulta o {@link ColumnMetadataResolver} guardado pela
     * {@link ResultGrid} desta tabela especifica (client property — ver
     * {@link RendererFactory#KEY_METADATA_RESOLVER}), o MESMO usado pelo
     * indicador de FK do cabecalho, pelo popup de metadados e pelo clique no
     * icone de FK (ver {@link SelectionManager}), entao o resultado e sempre
     * consistente entre os quatro. Enquanto o schema ainda nao carregou,
     * {@code resolve} dispara a carga em segundo plano e agenda um
     * {@code table.repaint()} para quando ela terminar — a celula
     * simplesmente aparece sem destaque/icone ate la.
     */
    static ColumnMetadata resolveMetadata(JTable table, int viewColumn) {
        Object resolverObj = table.getClientProperty(RendererFactory.KEY_METADATA_RESOLVER);
        if (!(resolverObj instanceof ColumnMetadataResolver resolver)
                || !(table.getModel() instanceof ResultTableModel model)) {
            return null;
        }
        int modelColumn = table.convertColumnIndexToModel(viewColumn);
        return resolver.resolve(model, modelColumn, table::repaint);
    }

    /**
     * Pinta o icone de "ir para a origem" por CIMA do texto ja pintado pelo
     * {@link DefaultTableCellRenderer}, no canto direito da celula (zona
     * reservada via {@link #FK_ICON_ZONE_WIDTH}, ver {@link #getTableCellRendererComponent}) —
     * so quando esta celula especifica e de uma coluna de chave estrangeira
     * ({@link #fkIconVisible}). O clique em cima dele e tratado por
     * {@link SelectionManager} (mesma zona de pixels), nao aqui — um renderer
     * nunca recebe eventos de mouse por si so.
     */
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (!fkIconVisible) {
            return;
        }
        Icon icon = Icons.get(IconType.FOREIGN_KEY, FK_ICON_SIZE, GridTheme.COLOR_IDENTIFIER);
        int x = getWidth() - FK_ICON_ZONE_WIDTH + (FK_ICON_ZONE_WIDTH - FK_ICON_SIZE) / 2 - 4;
        int y = (getHeight() - FK_ICON_SIZE) / 2;
        icon.paintIcon(this, g, x, y);
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
            // Preserva a zona reservada do icone de FK (ver fkIconVisible) —
            // sem o "+ FK_ICON_ZONE_WIDTH" aqui, a celula ATIVA de uma coluna
            // de FK perdia o espaco reservado e o texto passava a ficar
            // embaixo do icone bem na celula que o usuario esta olhando.
            int right = 7 + (fkIconVisible ? FK_ICON_ZONE_WIDTH : 0);
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(1, 1, 1, 1, GridTheme.ACTIVE_CELL_BORDER),
                    BorderFactory.createEmptyBorder(0, 7, 0, right)));
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

    /**
     * Gancho para subclasses limparem estado de "scratch" PROPRIO (campos de
     * instancia usados so entre {@link #colorFor} e uma pintura customizada)
     * antes de CADA celula ser processada — chamado incondicionalmente aqui
     * em cima, mesmo para valores nulos (que nunca chamam {@link #colorFor}).
     * Sem isto, um estado setado por uma celula NAO-NULA anterior pintada por
     * esta MESMA instancia compartilhada vazaria para a proxima celula, mesmo
     * se ela for nula — mesma familia de bug ja vista e corrigida com
     * COLOR_NULL (ver topo desta classe). Default: nada a limpar. Nenhuma
     * subclasse atual usa este gancho (o antigo {@code BadgeCellRenderer} foi
     * removido — ver "Sistema Semantico de Cores por Tipo de Dado" em
     * DESIGN_SYSTEM.md); mantido como ponto de extensao.
     */
    void resetTypedState() {
    }
}
