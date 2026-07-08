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
 *   <li>{@link #packColumns}/{@link #packColumn} — o ajuste "estilo Excel"
 *       POR CONTEUDO: cada coluna fica do tamanho do seu maior conteudo
 *       (cabecalho OU celula, o que for maior), sem cortar texto. E o que
 *       {@link ResultGrid#applyPersistedLayoutOrAutoFit} usa na PRIMEIRA
 *       exibicao de um resultado (sem layout salvo ainda) — pedido explicito
 *       do usuario para o resultado ja "nascer expandido", em vez de largura
 *       uniforme cortando nomes de coluna por padrao. Tambem disparado por
 *       acao explicita do usuario depois disso (duplo-clique na divisoria do
 *       cabecalho, "AutoFit"/"AutoFit Todas" no menu de contexto, passo
 *       {@code EXPANDED} do ciclo do canto — ver {@link SelectionManager}).</li>
 *   <li>{@link #applyDefaultWidths}/{@link #applyDefaultWidth} — a largura
 *       UNIFORME antiga ({@value #DEFAULT_WIDTH_CHARS} caracteres em TODAS as
 *       colunas, sem excecao para nomes mais longos, que ficam cortados —
 *       visivel por inteiro no popup de hover/tooltip): nao e mais o padrao
 *       automatico, mas continua disponivel como acao explicita ("Largura
 *       padrao" no menu de contexto, passo {@code DEFAULT} do ciclo do
 *       canto), pra quem preferir o visual uniforme de volta.</li>
 * </ul>
 *
 * Em ambos os casos, isto e so o PONTO DE PARTIDA: dali em diante o usuario
 * tem controle TOTAL para aumentar ou diminuir qualquer coluna (arrastando a
 * divisoria do cabecalho), inclusive para BAIXO do que o autofit calculou —
 * ver {@link ResultGrid#styleTable} (o {@code minWidth} DURO do Swing e um
 * valor minusculo, so para a coluna nunca desaparecer de vez) e
 * {@link ResultGrid#applyPersistedLayoutOrAutoFit} (uma largura ja salva e
 * sempre respeitada exatamente como o usuario deixou, sem forcar de volta
 * pro autofit ou pro padrao).
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

    /**
     * Mesma largura padrao de {@link #applyDefaultWidths}, mas para UMA
     * coluna — usada tanto para uma coluna nova sem layout salvo (grade
     * ainda nem visivel; o {@code refreshLayout} abaixo e um no-op inofensivo
     * nesse caso) quanto pelo duplo-clique na divisoria "encolhendo de
     * volta" uma coluna ja expandida (grade JA visivel — ai o
     * {@code refreshLayout} e obrigatorio, mesmo motivo de {@link #packColumn}).
     */
    static void applyDefaultWidth(JTable table, int viewColumn) {
        applyWidth(table, viewColumn, defaultColumnWidth(table));
        refreshLayout(table);
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

    /**
     * Mesma ideia de {@link #shrinkToMinimum}, mas para UMA coluna — usada
     * pelo lado "encolher" do duplo-clique na divisoria (ver
     * {@link ResultTableHeader}) quando a coluna JA esta expandida.
     *
     * IMPORTANTE usar o tamanho MINIMIZADO aqui, e nao a largura padrao
     * ({@link #applyDefaultWidth}): {@link #computePackedWidth} nunca produz
     * nada MENOR que a largura padrao (e o piso dela, de proposito — ver seu
     * javadoc). Para uma coluna de conteudo curto (ex.: "id"), isso faz o
     * autofit "expandido" e a largura "padrao" darem o MESMO numero de
     * pixels — usar a largura padrao como alvo do encolher fazia o
     * duplo-clique parecer NAO FAZER NADA nessas colunas (sempre a mesma
     * largura nos dois lados do alternar), exatamente o bug relatado pelo
     * usuario ("a primeira coluna nao acompanha", tipicamente um id curto).
     * O tamanho minimizado e sempre estritamente menor que o piso do
     * autofit, entao o alternar sempre produz uma mudanca visivel.
     */
    static void applyMinimizedWidth(JTable table, int viewColumn) {
        if (viewColumn < 0 || viewColumn >= table.getColumnCount()) {
            return;
        }
        int minimized = minimizedColumnWidth(table);
        int hardFloor = table.getColumnModel().getColumn(viewColumn).getMinWidth();
        applyWidth(table, viewColumn, Math.max(minimized, hardFloor));
        refreshLayout(table);
    }

    private static void packColumnWidthOnly(JTable table, int viewColumn) {
        if (viewColumn < 0 || viewColumn >= table.getColumnCount()) {
            return;
        }
        applyWidth(table, viewColumn, computePackedWidth(table, viewColumn));
    }

    /** So o CALCULO (sem aplicar nada) — usado por {@link #packColumn}/{@link #packColumns} e por {@link #isPacked}. */
    private static int computePackedWidth(JTable table, int viewColumn) {
        TableColumn column = table.getColumnModel().getColumn(viewColumn);
        int width = headerWidth(table, column);
        width = Math.max(width, contentWidth(table, viewColumn));
        // Mesmo o autofit por conteudo nunca produz uma coluna mais estreita
        // que o minimo geral da grade (ver DEFAULT_WIDTH_CHARS) — conteudo
        // curtissimo (ex.: uma coluna booleana com valores "0"/"1") nao deve
        // resultar numa coluna ilegivelmente fina so porque o texto cabe.
        return Math.max(width, defaultColumnWidth(table));
    }

    /**
     * Verdadeiro quando {@code viewColumn} JA esta na largura que o autofit
     * por conteudo calcularia agora — usado pelo duplo-clique na divisoria
     * do cabecalho (ver {@link ResultTableHeader}) para decidir se desta vez
     * deve EXPANDIR (ajustar ao conteudo) ou ENCOLHER (voltar a largura
     * padrao). Sem isto, duplo-clicar numa coluna ja expandida reaplicava a
     * MESMA largura calculada e nao tinha efeito visivel nenhum — pedido
     * explicito do usuario, comparando com o Excel: o duplo-clique deve
     * alternar entre os dois estados, nao travar num deles.
     *
     * Decide pelo estado ATUAL de verdade (recalculando e comparando),
     * nao por uma flag lembrada — funciona igual independente de COMO a
     * coluna chegou nessa largura (autofit anterior, layout salvo do
     * usuario, redimensionamento manual que por acaso bateu no mesmo
     * tamanho...). A tolerancia de poucos pixels absorve arredondamento de
     * fonte/DPI que deixaria a largura atual 1-2px diferente do calculo
     * mesmo quando e visualmente "a mesma".
     */
    static boolean isPacked(JTable table, int viewColumn) {
        if (viewColumn < 0 || viewColumn >= table.getColumnCount()) {
            return false;
        }
        int current = table.getColumnModel().getColumn(viewColumn).getWidth();
        int packed = computePackedWidth(table, viewColumn);
        return Math.abs(current - packed) <= 2;
    }

    /** {@link #isPacked} para TODAS as colunas — usado quando todas estao selecionadas (ver {@link ResultTableHeader}). */
    static boolean allPacked(JTable table) {
        for (int col = 0; col < table.getColumnCount(); col++) {
            if (!isPacked(table, col)) {
                return false;
            }
        }
        return true;
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

    /**
     * Espaco entre o nome (zona CENTER) e a zona de ordenacao (zona EAST) do
     * cabecalho — o {@code hgap} do {@code BorderLayout(4, 0)} de
     * {@link ColumnHeaderRenderer#panel}. Faltava aqui antes: o AutoFit
     * calculava {@code HEADER_PADDING + SORT_ZONE_WIDTH} mas esquecia esses
     * 4px do meio, entao a coluna saia alguns pixels ESTREITA demais e o
     * nome ainda cortava (".."), mesmo logo depois de "expandir" — pedido
     * explicito do usuario para nunca mais acontecer.
     */
    private static final int HEADER_LABEL_GAP = 4;

    /**
     * Folga extra de seguranca (alem do calculo exato de fonte/padding/gap
     * acima): o JLabel do nome pode ter 1-2px de insets/antialiasing
     * proprios que as metricas de fonte cruas nao capturam por completo —
     * melhor sobrar um pixel do que faltar e voltar a cortar o nome.
     */
    private static final int HEADER_SAFETY_MARGIN = 4;

    private static int headerWidth(JTable table, TableColumn column) {
        FontMetrics headerMetrics = table.getTableHeader()
                .getFontMetrics(table.getTableHeader().getFont().deriveFont(Font.BOLD));
        int width = headerMetrics.stringWidth(String.valueOf(column.getHeaderValue()));
        width += HEADER_PADDING;
        width += HEADER_LABEL_GAP;
        width += ColumnHeaderRenderer.SORT_ZONE_WIDTH;
        width += HEADER_SAFETY_MARGIN;
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
