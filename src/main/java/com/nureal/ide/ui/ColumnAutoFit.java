package com.nureal.ide.ui;

import javax.swing.JTable;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableColumn;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Insets;

/**
 * Ajuste de largura de coluna da grade de resultados. Duas operacoes bem
 * distintas moram aqui, de proposito:
 *
 * <ul>
 *   <li>{@link #applyDefaultWidths}/{@link #applyDefaultWidth} — a largura
 *       PADRAO, usada APENAS na primeira exibicao de um resultado (sem
 *       layout salvo ainda): TODAS as colunas saem com EXATAMENTE o MESMO
 *       tamanho, largo o suficiente para {@value #DEFAULT_WIDTH_CHARS}
 *       caracteres — sem excecao para nomes de coluna mais longos (esses
 *       ficam com o nome cortado, "..." — visivel por inteiro no popup de
 *       hover/tooltip). Isto e SO o ponto de partida: dali em diante o
 *       usuario tem controle TOTAL para aumentar ou diminuir qualquer coluna
 *       (arrastando a divisoria do cabecalho), inclusive para BAIXO do
 *       padrao — ver {@link ResultGrid#styleTable} (o {@code minWidth} DURO
 *       do Swing e um valor minusculo, so para a coluna nunca desaparecer de
 *       vez, NAO o mesmo valor do padrao) e
 *       {@link ResultGrid#applyPersistedLayoutOrAutoFit} (uma largura ja
 *       salva e sempre respeitada exatamente como o usuario deixou, sem
 *       forcar de volta para o padrao).</li>
 *   <li>{@link #packColumns}/{@link #packColumn} — o ajuste "estilo Excel"
 *       POR CONTEUDO, disparado por acao explicita do usuario (duplo-clique
 *       na divisoria do cabecalho, "AutoFit"/"AutoFit Todas" no menu de
 *       contexto): cada coluna fica do tamanho do seu maior conteudo
 *       (cabecalho OU celula), sem cortar texto — este SIM considera o nome
 *       do cabecalho, porque e uma acao explicita pedindo para tudo caber.</li>
 * </ul>
 *
 * O calculo do autofit por conteudo considera, para o cabecalho: o texto do
 * nome da coluna, a fonte em negrito do cabecalho e a largura reservada para
 * o botao de ordenacao (sempre alinhado a direita — ver
 * {@link ColumnHeaderRenderer}); para as celulas: o texto formatado (nao um
 * mero {@code String.length()}), a fonte da propria celula e o padding do
 * renderer. Sem limites artificiais de largura maxima — se o conteudo for
 * largo, a coluna cresce; o usuario sempre pode redimensionar manualmente
 * depois.
 */
final class ColumnAutoFit {

    private static final int CELL_PADDING = 16;   // 8px cada lado (ver AbstractTypedCellRenderer)
    private static final int HEADER_PADDING = 20;  // 10px cada lado (ver ColumnHeaderRenderer)
    private static final int SAMPLE_ROWS = 200; // limite de linhas amostradas, por performance

    /**
     * Largura "em caracteres" do PADRAO inicial — mesma unidade que o Excel
     * usa para largura de coluna. So vale como PONTO DE PARTIDA (primeira
     * exibicao de um resultado); NAO e um piso permanente — o usuario pode
     * livremente redimensionar qualquer coluna para MENOS que isso depois
     * (ver {@link ResultGrid#styleTable}, que usa um minimo DURO bem menor e
     * independente deste valor).
     */
    private static final int DEFAULT_WIDTH_CHARS = 16;

    /**
     * Largura "em caracteres" do passo {@code MINIMIZED} do ciclo de
     * duplo-clique no canto (ver {@link SelectionManager#installCorner}):
     * compacta, mas ainda LEGIVEL — o suficiente para alguns caracteres de
     * conteudo real, nao so "...". Bem diferente do piso DURO do Swing
     * ({@code TableColumn.getMinWidth()}, ver {@link ResultGrid#styleTable}),
     * que existe apenas para a coluna nunca desaparecer por completo e nao
     * tem compromisso nenhum com legibilidade.
     */
    private static final int MINIMIZED_WIDTH_CHARS = 8;

    /** Rede de seguranca pura (metricas de fonte degeneradas) — nao e a regra de negocio, ver {@link #DEFAULT_WIDTH_CHARS}. */
    private static final int SAFETY_FLOOR_PX = 32;

    private ColumnAutoFit() {
    }

    /**
     * Largura PADRAO (uniforme, {@value #DEFAULT_WIDTH_CHARS} caracteres) em
     * TODAS as colunas, SEM EXCECAO — usada na primeira exibicao de um
     * resultado, antes de qualquer ajuste manual ou por conteudo. Ver a nota
     * de classe: nomes de coluna mais longos que isso ficam cortados por
     * padrao (o visual uniforme foi priorizado explicitamente); o autofit
     * por conteudo continua disponivel para quem quiser ver tudo, e o
     * redimensionamento manual (para qualquer tamanho, inclusive menor) fica
     * sempre disponivel depois.
     */
    static void applyDefaultWidths(JTable table) {
        int uniform = defaultColumnWidth(table);
        for (int col = 0; col < table.getColumnCount(); col++) {
            applyWidth(table, col, uniform);
        }
        refreshLayout(table);
    }

    /** Mesma largura padrao de {@link #applyDefaultWidths}, mas para UMA coluna (coluna nova, sem layout salvo). */
    static void applyDefaultWidth(JTable table, int viewColumn) {
        applyWidth(table, viewColumn, defaultColumnWidth(table));
    }

    /**
     * Aplica {@code width} tanto em {@code preferredWidth} quanto em
     * {@code width} (a largura EFETIVA, a que {@code getWidth()} devolve) —
     * de proposito as DUAS, e nao so a preferida: {@code setPreferredWidth}
     * sozinho so se reflete em {@code getWidth()} no PROXIMO ciclo de layout
     * do Swing (assincrono, via {@code revalidate()}), e quem le
     * {@code getWidth()} logo em seguida — como {@link ResultGrid#persistLayout}
     * apos um clique de menu — pegaria o valor ANTIGO. Definir os dois aqui
     * garante que a largura efetiva ja esteja correta no instante em que o
     * metodo retorna, sem depender de timing de layout.
     */
    static void applyWidth(JTable table, int viewColumn, int width) {
        if (viewColumn < 0 || viewColumn >= table.getColumnCount()) {
            return;
        }
        TableColumn column = table.getColumnModel().getColumn(viewColumn);
        column.setPreferredWidth(width);
        column.setWidth(width);
    }

    /**
     * Converte {@value #DEFAULT_WIDTH_CHARS} "caracteres" em pixels usando a
     * MESMA definicao do Excel: a largura do digito {@code '0'} da fonte da
     * celula — nao a largura media de uma letra qualquer, que varia demais
     * em fontes proporcionais e daria uma largura "de caracteres" pouco
     * previsivel. O {@code Math.max} final e so uma rede de seguranca contra
     * metricas de fonte degeneradas (largura de digito zero/negativa) — na
     * pratica {@value #DEFAULT_WIDTH_CHARS} caracteres sempre excede isso.
     */
    private static int defaultColumnWidth(JTable table) {
        return charsToPixels(table, DEFAULT_WIDTH_CHARS);
    }

    /** Mesma ideia de {@link #defaultColumnWidth}, mas para o tamanho compacto-porem-legivel do passo {@code MINIMIZED}. */
    private static int minimizedColumnWidth(JTable table) {
        return charsToPixels(table, MINIMIZED_WIDTH_CHARS);
    }

    private static int charsToPixels(JTable table, int chars) {
        FontMetrics cellMetrics = table.getFontMetrics(table.getFont());
        int charWidth = cellMetrics.charWidth('0');
        return Math.max(charWidth * chars + CELL_PADDING, SAFETY_FLOOR_PX);
    }

    /** Ajusta TODAS as colunas visiveis ao conteudo. Disparado por acao explicita do usuario (ver nota de classe). */
    static void packColumns(JTable table) {
        for (int col = 0; col < table.getColumnCount(); col++) {
            packColumnWidthOnly(table, col);
        }
        refreshLayout(table);
    }

    /** Ajusta UMA coluna (usado no duplo-clique na divisoria do cabecalho, estilo Excel). */
    static void packColumn(JTable table, int viewColumn) {
        packColumnWidthOnly(table, viewColumn);
        refreshLayout(table);
    }

    /**
     * Reduz TODAS as colunas a um tamanho compacto, mas ainda LEGIVEL
     * ({@value #MINIMIZED_WIDTH_CHARS} caracteres) — 2o passo do ciclo de
     * duplo-clique no canto superior-esquerdo (ver
     * {@link SelectionManager#installCorner}). Isto e DIFERENTE do piso DURO
     * do Swing ({@code TableColumn.getMinWidth()}, ver
     * {@code ResultGrid#styleTable}): aquele e minusculo de proposito, so
     * para a coluna nunca sumir por completo ao arrastar manualmente, sem
     * nenhum compromisso com conseguir ler o conteudo — usa-lo aqui deixaria
     * a grade inteira reduzida a "...", inutil. O {@code Math.max} com
     * {@code getMinWidth()} so entra em jogo se o piso duro (raro, mas
     * configuravel por zoom) for MAIOR que o tamanho legivel calculado.
     */
    static void shrinkToMinimum(JTable table) {
        int minimized = minimizedColumnWidth(table);
        for (int col = 0; col < table.getColumnCount(); col++) {
            int hardFloor = table.getColumnModel().getColumn(col).getMinWidth();
            applyWidth(table, col, Math.max(minimized, hardFloor));
        }
        refreshLayout(table);
    }

    private static void packColumnWidthOnly(JTable table, int viewColumn) {
        if (viewColumn < 0 || viewColumn >= table.getColumnCount()) {
            return;
        }
        TableColumn column = table.getColumnModel().getColumn(viewColumn);

        int width = headerWidth(table, column);
        width = Math.max(width, contentWidth(table, viewColumn));
        // Mesmo o autofit por conteudo nunca produz uma coluna mais estreita
        // que o minimo geral da grade (ver DEFAULT_WIDTH_CHARS) — conteudo
        // curtissimo (ex.: uma coluna booleana com valores "0"/"1") nao deve
        // resultar numa coluna ilegivelmente fina so porque o texto cabe.
        applyWidth(table, viewColumn, Math.max(width, defaultColumnWidth(table)));
    }

    /**
     * Forca a largura efetiva ({@code width}, nao so {@code preferredWidth})
     * e a pintura a se atualizarem IMEDIATAMENTE. Necessario porque, ao
     * contrario da primeira exibicao de um resultado (onde o proprio
     * primeiro ciclo de layout do Swing, ao tornar o componente visivel, ja
     * sincroniza {@code width} com {@code preferredWidth} sozinho),
     * disparar estes metodos DEPOIS que a grade ja esta na tela e visivel
     * (menu de contexto, duplo-clique) so muda a preferencia — sem isto, o
     * clique do usuario nao teria efeito visivel nenhum ate a proxima vez
     * que algo mais (rolar, redimensionar a janela) forcasse um relayout.
     */
    private static void refreshLayout(JTable table) {
        JTableHeader header = table.getTableHeader();
        if (header != null) {
            header.revalidate();
            header.repaint();
        }
        table.revalidate();
        table.repaint();
    }

    private static int headerWidth(JTable table, TableColumn column) {
        FontMetrics headerMetrics = table.getTableHeader()
                .getFontMetrics(table.getTableHeader().getFont().deriveFont(Font.BOLD));
        int width = headerMetrics.stringWidth(String.valueOf(column.getHeaderValue()));
        width += HEADER_PADDING;
        width += ColumnHeaderRenderer.SORT_ZONE_WIDTH;
        // Sem reserva para icone de PK/FK: o cabecalho nao mostra mais esse
        // icone (removido a pedido do usuario — poluia o header); a mesma
        // informacao continua no popup de hover/dialogo "Informacoes da coluna".
        return width;
    }

    private static int contentWidth(JTable table, int viewColumn) {
        FontMetrics cellMetrics = table.getFontMetrics(table.getFont());
        Insets extra = rendererInsets(table, viewColumn);
        int maxWidth = 0;
        int rows = Math.min(table.getRowCount(), SAMPLE_ROWS);
        for (int row = 0; row < rows; row++) {
            Object value = table.getValueAt(row, viewColumn);
            String text = (value == null) ? "null" : displayText(table, row, viewColumn, value);
            // CellText.forWidthMeasurement corta ANTES de medir: um unico
            // CLOB/JSON gigante nao pode forcar a coluna inteira a ficar
            // largura absurda (a celula em si ja trunca na exibicao — ver
            // AbstractTypedCellRenderer — mas o calculo de largura usava o
            // texto completo, entao o bug aparecia so aqui, no autofit).
            int width = cellMetrics.stringWidth(CellText.forWidthMeasurement(text));
            if (width > maxWidth) {
                maxWidth = width;
            }
        }
        return maxWidth + CELL_PADDING + extra.left + extra.right;
    }

    /** Usa o proprio renderer da coluna para formatar o valor (datas, etc.), quando disponivel. */
    private static String displayText(JTable table, int row, int column, Object value) {
        try {
            var renderer = table.getCellRenderer(row, column);
            if (renderer instanceof AbstractTypedCellRenderer typed) {
                return typed.formatValue(value);
            }
        } catch (RuntimeException ignore) {
            // cai para toString() abaixo
        }
        return value.toString();
    }

    private static Insets rendererInsets(JTable table, int viewColumn) {
        // O padding do renderer ja esta embutido na constante CELL_PADDING
        // (ver AbstractTypedCellRenderer); reservado para eventuais margens
        // extras especificas de um renderer futuro.
        return new Insets(0, 0, 0, 0);
    }
}
