package com.nureal.ide.ui;
import com.nureal.ide.compartilhado.designsystem.IconType;
import com.nureal.ide.compartilhado.designsystem.Icons;
import com.nureal.ide.compartilhado.designsystem.Buttons;
import com.nureal.ide.compartilhado.designsystem.Typography;
import com.nureal.ide.compartilhado.designsystem.GridTheme;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.FlowLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Barra inferior de uma aba de resultado: a esquerda, contagem de linhas +
 * tempos de execucao/busca + o link discreto "Carregar todas as linhas
 * restantes"; a direita, o botao "Exportar" com seu menu. A paginacao em si
 * (buscar a proxima pagina) passou a acontecer sozinha, ao rolar perto do
 * fim da grade (ver {@code ResultsAreaController}) — revisao de UX: "queria
 * eliminar esses botoes no final da tela para dar impressao de
 * continuidade". Este link so fica pra quem quer TUDO de uma vez, sem
 * precisar rolar repetidamente numa tabela grande.
 *
 * Consolida num unico componente o que a especificacao de arquitetura do
 * Grid descreve como dois componentes ({@code ResultStatistics} +
 * {@code ResultStatus}): aqui os dois representam o MESMO pequeno estado
 * (linhas carregadas, tempos, se ha mais para buscar) e aparecem SEMPRE
 * juntos, na mesma barra — separa-los criaria duas classes triviais so
 * repassando o mesmo estado uma para a outra, sem nenhuma responsabilidade
 * propria em qualquer uma delas.
 *
 * Nao conhece JDBC/cursor: decidir QUANDO ha mais linhas e COMO busca-las e
 * responsabilidade de {@link MainWindow} (dono do ciclo de vida do
 * ResultSet) — esta classe so expoe pontos de callback ({@code onXxx}) para
 * essas acoes e um metodo {@link #refresh} que recebe um snapshot ja pronto
 * para exibir. Construcao em duas fases (callbacks acopladas DEPOIS do
 * construtor) e proposital: o Runnable de "recarregar a propria barra" so
 * pode ser montado depois que a barra existe, e so entao repassado como a
 * acao dos botoes — nenhuma das duas partes precisa saber da outra antes da
 * hora.
 */
final class ResultStatusBar {

    private final JPanel panel = new JPanel(new BorderLayout());
    private final JLabel info = new JLabel();
    /**
     * Resumo da selecao atual (quantidade de celulas + soma, quando ha valor
     * numerico entre elas) — estilo barra de status do Excel. So exibe algo
     * quando ha mais de uma celula selecionada (ver {@link #updateSelectionSummary}):
     * para uma unica celula, o proprio valor ja esta visivel na grade.
     */
    private final JLabel selectionSummary = new JLabel();
    /** Ultima soma exibida — o que um clique em {@link #selectionSummary} copia (ver {@link #copySum}). */
    private java.math.BigDecimal lastSum;
    /**
     * Sem mais um botao "Carregar mais N" aqui (revisao de UX: "queria
     * eliminar esses botoes no final da tela para dar impressao de
     * continuidade") — {@code ResultsAreaController} agora carrega a
     * proxima pagina sozinho quando o usuario rola perto do fim da grade
     * (ver {@code #onNearScrollEnd}). {@code loadAllButton} continua
     * existindo, so que como um LINK discreto (nao mais um botao
     * proeminente) — util pra quem quer TUDO de uma vez numa tabela grande
     * em vez de rolar repetidamente, mas sem competir visualmente com o
     * resto da barra.
     */
    private final JButton loadAllButton = new JButton("Carregar todas as linhas restantes");
    /**
     * Roda um {@code SELECT COUNT(*)} sob demanda pra saber o total REAL da
     * consulta sem precisar carregar todas as linhas na grade (ver
     * {@code ResultsAreaController#showExactTotal}) — deliberadamente
     * OPT-IN (nunca automatico): pode ser lento em tabela grande, entao so
     * roda quando o usuario pede.
     */
    private final JButton exactTotalButton = new JButton("Ver total exato");
    private final JButton exportButton = new JButton("Exportar");
    private final JMenuItem exportThisItem = new JMenuItem("Exportar este resultado...");
    private final JMenuItem exportAllItem = new JMenuItem("Exportar todos (uma aba por resultado)...");
    /**
     * CSV ja existia como opcao (ver {@link GridExporter}), mas so alcancavel
     * pelo menu de clique-direito da grade — pouco descobrivel para quem
     * procura "Exportar" so no botao (ver {@code GAP_ANALYSIS_DBA_DEV.md},
     * fase 3). Mesma acao, so exposta tambem aqui, junto do Excel.
     */
    private final JMenuItem exportCsvItem = new JMenuItem("Exportar CSV...");
    /** Mesmo raciocinio do CSV acima — JSON ja existia so no menu de clique-direito da grade (revisao de UX: consolidar exportacao num unico lugar descobrivel). */
    private final JMenuItem exportJsonItem = new JMenuItem("Exportar JSON...");

    // ---------- Barra de edicao (so aparece quando GridEditController.isEditable()) ----------
    private final JPanel editBar = new JPanel(new BorderLayout());
    /**
     * Alterna o "Modo de edicao" (ver {@link GridEditController#setEditModeOn}) —
     * SEMPRE visivel quando o resultado e capaz de ser editado (mesmo antes
     * de o usuario ligar o modo), diferente dos demais botoes desta barra
     * (ver {@link #setEditModeUi}). Pedido explicito do usuario: enquanto o
     * resultado e so exibido, a grade deve se comportar como puramente
     * visual/navegacao — incluir/editar/excluir so ficam disponiveis depois
     * que o usuario liga o modo de proposito, evitando edicoes acidentais
     * so por estar navegando/selecionando linhas para copiar.
     */
    private final JButton editModeToggle = new JButton("Ativar edicao");
    private final JButton addRowButton = new JButton("Nova linha");
    private final JButton deleteRowsButton = new JButton("Excluir linha(s)");
    private final JLabel pendingLabel = new JLabel();
    private final JButton discardButton = new JButton("Descartar");
    private final JButton saveChangesButton = new JButton("Salvar alteracoes");

    ResultStatusBar() {
        exportButton.setIcon(Icons.get(IconType.EXPORT, 14, MainWindow.ACCENT));
        exportButton.setToolTipText("Exportar resultado para Excel");
        JPopupMenu exportMenu = new JPopupMenu();
        exportMenu.add(exportThisItem);
        exportMenu.add(exportAllItem);
        exportMenu.addSeparator();
        exportMenu.add(exportCsvItem);
        exportMenu.add(exportJsonItem);
        exportButton.addActionListener(e -> exportMenu.show(exportButton, 0, exportButton.getHeight()));

        // Mesmo padrao "contorno" de botao secundario usado em qualquer
        // dialogo do app (ver Buttons#styleSecondary) — antes estes eram
        // botoes PADRAO do FlatLaf, sem o mesmo raio/borda/espacamento das
        // acoes secundarias em outros lugares (DdlAssistantDialog,
        // FkInspectorWindow, CellContentViewer).
        Buttons.styleSecondary(exportButton);
        // "Carregar todas as linhas restantes" e "Ver total exato" viram
        // LINKS discretos (sem fundo/borda de botao) — nenhum dos dois e
        // mais a acao PRINCIPAL desta barra (paginacao agora e automatica ao
        // rolar, ver ResultsAreaController), so atalhos secundarios opt-in.
        styleAsLink(loadAllButton);
        styleAsLink(exactTotalButton);
        exactTotalButton.setToolTipText(
                "Roda um SELECT COUNT(*) — pode ser lento em tabelas muito grandes, por isso nao e automatico");

        // Nivel TERCIARIO (informacao auxiliar) — ver Typography.
        Typography.tertiary(selectionSummary);
        // Clique copia a soma — mesmo gesto do Excel (clicar no "Soma:" da
        // barra de status copia o valor pronto pra colar), pedido explicito
        // do usuario pra facilitar reaproveitar o total sem digitar de novo.
        selectionSummary.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                copySum();
            }
        });

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 3));
        left.add(info);
        left.add(selectionSummary);
        left.add(loadAllButton);
        left.add(exactTotalButton);
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 3));
        right.add(exportButton);

        panel.add(left, BorderLayout.WEST);
        panel.add(right, BorderLayout.EAST);
        panel.add(buildEditBar(), BorderLayout.NORTH);
    }

    /**
     * Linha extra ACIMA da barra normal — so visivel quando
     * {@code MainWindow} resolve que o resultado e editavel (ver
     * {@code MainWindow#tryEnableEditing}). Pedido explicito do usuario:
     * update/insert/delete direto na grade, em vez de so gerar o SQL para
     * copiar (ver {@link GridClipboard}).
     */
    private JComponent buildEditBar() {
        editModeToggle.setIcon(Icons.get(IconType.EDIT, 13, MainWindow.ACCENT));
        // GridTheme.HEADER_FOREGROUND, nao um literal proprio — o valor claro
        // ja era EXATAMENTE 0x334155 (mesmo "ink" usado no cabecalho da
        // grade), so nunca acompanhava o tema escuro (icone baixo-contraste
        // no modo escuro).
        // Buttons.bindThemedIcon (nao Icons.get(...) resolvido uma unica vez
        // no construtor): sem isto, os 2 icones abaixo ficavam congelados na
        // cor HEADER_FOREGROUND do tema em que a grade foi criada (mesmo bug
        // sistemico corrigido no resto do app, ver Buttons#bindThemedIcon).
        Buttons.bindThemedIcon(addRowButton, IconType.NEW, 13, () -> GridTheme.HEADER_FOREGROUND);
        Buttons.bindThemedIcon(deleteRowsButton, IconType.DELETE, 13, () -> GridTheme.HEADER_FOREGROUND);
        saveChangesButton.setIcon(Icons.get(IconType.SAVE, 13, Color.WHITE));
        // Nivel TERCIARIO (status auxiliar) — ver Typography.
        Typography.tertiary(pendingLabel);

        // Mesmo padrao secundario/primario de qualquer dialogo do app (ver
        // Buttons) — "Salvar alteracoes" e a acao de CONFIRMACAO desta barra
        // (preenchida na cor da marca), o resto e secundario (contorno).
        for (JButton btn : new JButton[] { editModeToggle, addRowButton, deleteRowsButton, discardButton }) {
            Buttons.styleSecondary(btn);
        }
        Buttons.stylePrimary(saveChangesButton);

        JPanel editLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 3));
        editLeft.add(editModeToggle);
        editLeft.add(addRowButton);
        editLeft.add(deleteRowsButton);
        editLeft.add(pendingLabel);
        JPanel editRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 3));
        editRight.add(discardButton);
        editRight.add(saveChangesButton);

        editBar.add(editLeft, BorderLayout.WEST);
        editBar.add(editRight, BorderLayout.EAST);
        editBar.setVisible(false);
        // Comeca com o modo desligado: so o botao de alternar aparece ate o
        // usuario ligar de proposito (ver setEditModeUi/javadoc de editModeToggle).
        setEditModeUi(false);
        return editBar;
    }

    JComponent asComponent() {
        return panel;
    }

    void onLoadAll(Runnable action) {
        loadAllButton.addActionListener(e -> action.run());
    }

    /**
     * Troca o texto de "Carregar todas as linhas restantes" enquanto a
     * leitura roda em segundo plano — mesmo padrao ja usado por
     * {@link #setExactTotalBusy}, MAS continua CLICAVEL enquanto ocupado
     * (nao mais desabilitado): o MESMO botao vira "Cancelar carregamento",
     * clicavel pra interromper a leitura em andamento (ver
     * {@code ResultsAreaController#loadAll}) — pedido explicito do usuario
     * ("preciso de um botao cancelar quando estiver carregando tudo").
     */
    void setLoadAllBusy(boolean busy) {
        loadAllButton.setText(busy ? "Cancelar carregamento" : "Carregar todas as linhas restantes");
    }

    /** "Ver total exato" clicado — ver javadoc de {@link #exactTotalButton}. */
    void onShowExactTotal(Runnable action) {
        exactTotalButton.addActionListener(e -> action.run());
    }

    /** Desabilita "Ver total exato" enquanto a contagem roda em segundo plano — evita clique duplo. */
    void setExactTotalBusy(boolean busy) {
        exactTotalButton.setEnabled(!busy);
        exactTotalButton.setText(busy ? "Contando..." : "Ver total exato");
    }

    /**
     * Mostra o total REAL apurado por {@code SELECT COUNT(*)} (ver
     * {@code ResultsAreaController#showExactTotal}) — substitui a contagem
     * aproximada de {@link #refresh} ate a proxima chamada dele (ex.:
     * quando mais uma pagina e carregada, a contagem exata continua valendo,
     * so a parte "carregadas" do texto muda).
     */
    void showExactTotal(long total) {
        lastKnownTotal = total;
        exactTotalButton.setVisible(false);
        refreshInfoText();
    }

    void onExportThis(Runnable action) {
        exportThisItem.addActionListener(e -> action.run());
    }

    void onExportAll(Runnable action) {
        exportAllItem.addActionListener(e -> action.run());
    }

    void onExportCsv(Runnable action) {
        exportCsvItem.addActionListener(e -> action.run());
    }

    void onExportJson(Runnable action) {
        exportJsonItem.addActionListener(e -> action.run());
    }

    void onToggleEditMode(Runnable action) {
        editModeToggle.addActionListener(e -> action.run());
    }

    void onAddRow(Runnable action) {
        addRowButton.addActionListener(e -> action.run());
    }

    void onDeleteRows(Runnable action) {
        deleteRowsButton.addActionListener(e -> action.run());
    }

    void onSaveChanges(Runnable action) {
        saveChangesButton.addActionListener(e -> action.run());
    }

    void onDiscardChanges(Runnable action) {
        discardButton.addActionListener(e -> action.run());
    }

    /** Mostra/esconde a barra de edicao inteira (o botao de alternar + o resto) — chamado uma vez quando o resultado e capaz de ser editado. */
    void showEditControls(boolean visible) {
        editBar.setVisible(visible);
    }

    /**
     * Atualiza o texto do botao de alternar e mostra/esconde TUDO que so faz
     * sentido com o modo ligado (Nova linha, Excluir, pendencias, Descartar,
     * Salvar) — o proprio botao de alternar permanece sempre visivel (ver
     * {@link #editModeToggle}). Chamado ao ligar/desligar o modo e uma vez ao
     * habilitar a edicao (comecando desligado).
     */
    void setEditModeOn(boolean on) {
        setEditModeUi(on);
    }

    private void setEditModeUi(boolean on) {
        editModeToggle.setText(on ? "Desativar edicao" : "Ativar edicao");
        addRowButton.setVisible(on);
        deleteRowsButton.setVisible(on);
        pendingLabel.setVisible(on);
        discardButton.setVisible(on);
        saveChangesButton.setVisible(on);
    }

    /** Desabilita os botoes de edicao enquanto {@code apply} roda em segundo plano — evita clique duplo em "Salvar". */
    void setEditBusy(boolean busy) {
        editModeToggle.setEnabled(!busy);
        addRowButton.setEnabled(!busy);
        deleteRowsButton.setEnabled(!busy);
        discardButton.setEnabled(!busy);
        saveChangesButton.setEnabled(!busy);
    }

    /**
     * Atualiza o rotulo de pendencias e o habilitar/desabilitar dos botoes de
     * edicao — chamado sempre que {@link GridEditController} muda (edicao,
     * insercao, marca/desmarca exclusao) e quando a selecao da grade muda
     * (para {@code Excluir linha(s)}).
     */
    void updatePendingState(int pendingCount, boolean hasSelection) {
        pendingLabel.setText(pendingCount > 0 ? pendingCount + " alteracao(oes) pendente(s)" : "");
        saveChangesButton.setEnabled(pendingCount > 0);
        discardButton.setEnabled(pendingCount > 0);
        deleteRowsButton.setEnabled(hasSelection);
    }

    /** Ultimos valores recebidos por {@link #refresh} — guardados pra {@link #refreshInfoText()} recompor o texto quando {@link #showExactTotal} chega depois. */
    private int lastRowCount;
    private long lastExecMs;
    private long lastFetchMs;
    private boolean lastHasMore;
    /** Total exato ja apurado por {@link #showExactTotal}, ou {@code null} se ainda nao pedido/nao sabido. */
    private Long lastKnownTotal;

    /** Atualiza o texto de contagem/tempos e mostra/esconde os links de paginacao/contagem. */
    void refresh(int rowCount, long execMs, long fetchMs, boolean hasMore) {
        lastRowCount = rowCount;
        lastExecMs = execMs;
        lastFetchMs = fetchMs;
        lastHasMore = hasMore;
        if (!hasMore) {
            // Cursor esgotado (rolou ate o fim ou clicou "Carregar tudo") —
            // rowCount JA E o total exato, nao precisa mais perguntar.
            lastKnownTotal = (long) rowCount;
        }
        refreshInfoText();
        loadAllButton.setVisible(hasMore);
        exactTotalButton.setVisible(hasMore && lastKnownTotal == null);
    }

    /**
     * Separador de milhar (ponto, padrao PT-BR) SO na tela — pedido explicito
     * do usuario apos ver "10000000 linha(s)" dificil de ler de relance.
     * Nunca usado para copiar/exportar (ver {@link GridClipboard}/{@link
     * ExcelExporter}, que continuam lendo o {@link java.math.BigDecimal}/
     * {@code long} cru): se este rotulo algum dia ganhar "clique para
     * copiar" (como {@link #selectionSummary} ja tem para a soma), o valor
     * copiado tem que ser o numero puro, sem o ponto de milhar — decimais,
     * quando houver, sempre com "." (nunca ",", que quebraria colar em SQL/
     * Excel — mesmo motivo documentado em {@link #formatSum}).
     */
    private static final java.text.NumberFormat COUNT_FORMAT = java.text.NumberFormat
            .getIntegerInstance(new java.util.Locale("pt", "BR"));

    private static String formatCount(long n) {
        return COUNT_FORMAT.format(n);
    }

    /** Recompoe o texto da contagem a partir do ultimo {@link #refresh} + {@link #lastKnownTotal}, se houver. */
    private void refreshInfoText() {
        String countPart = (lastKnownTotal != null)
                ? formatCount(lastRowCount) + " de " + formatCount(lastKnownTotal) + " linha(s)"
                : formatCount(lastRowCount) + (lastHasMore ? " linhas carregadas" : " linha(s)");
        info.setText(countPart
                + "   ·   execucao " + formatCount(lastExecMs) + " ms"
                + "   ·   busca " + formatCount(lastFetchMs) + " ms");
    }

    /**
     * Atualiza o resumo de selecao ("N selecionada(s) · Soma: X · Media: Y ·
     * Min: Z · Max: W") — chamado pela {@link ResultGrid} sempre que a
     * selecao de celulas muda (ver {@code MainWindow#buildGridPanel}, que liga
     * os dois). Pedido explicito do usuario: selecionar N celulas agrega so
     * essas N; selecionar a coluna inteira agrega a coluna inteira — igual a
     * barra de status do Excel, so que com todas as funcoes de uma vez (nao
     * so soma) em vez de exigir escolher uma por menu de contexto.
     *
     * {@code cellCount <= 1} nao mostra nada: com uma unica celula selecionada
     * o proprio valor ja esta visivel na grade, um "1 selecionada" fixo so
     * seria ruido durante a navegacao normal (mesmo comportamento do Excel).
     */
    void updateSelectionSummary(SelectionStats stats) {
        lastSum = stats.sum();
        if (stats.cellCount() <= 1) {
            selectionSummary.setText("");
            selectionSummary.setToolTipText(null);
            selectionSummary.setCursor(Cursor.getDefaultCursor());
            return;
        }
        StringBuilder text = new StringBuilder(formatCount(stats.cellCount()) + " selecionada(s)");
        if (stats.sum() != null) {
            text.append("   ·   Soma: ").append(formatSumDisplay(stats.sum()));
            text.append("   ·   Media: ").append(formatSumDisplay(stats.average()));
            text.append("   ·   Min: ").append(formatSumDisplay(stats.min()));
            text.append("   ·   Max: ").append(formatSumDisplay(stats.max()));
            selectionSummary.setToolTipText("Clique para copiar a soma");
            selectionSummary.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        } else {
            selectionSummary.setToolTipText(null);
            selectionSummary.setCursor(Cursor.getDefaultCursor());
        }
        selectionSummary.setText(text.toString());
    }

    /**
     * Copia so o NUMERO da soma (sem "N selecionada(s) ·" na frente) pra
     * area de transferencia, com um retorno visual rapido de confirmacao —
     * chamado pelo clique em {@link #selectionSummary}. Sem efeito quando a
     * selecao atual nao tem soma (texto/vazio): nada para copiar.
     */
    private void copySum() {
        if (lastSum == null) {
            return;
        }
        GridClipboard.setClipboard(formatSum(lastSum));
        String original = selectionSummary.getText();
        selectionSummary.setText("Soma copiada!");
        Timer revert = new Timer(900, e -> {
            if (lastSum != null) {
                selectionSummary.setText(original);
            }
        });
        revert.setRepeats(false);
        revert.start();
    }

    /** Sem casas decimais artificiais (1500 fica "1500", nao "1500,00") mas sem perder as que existirem de fato. */
    private static String formatSum(java.math.BigDecimal sum) {
        java.math.BigDecimal stripped = sum.stripTrailingZeros();
        // stripTrailingZeros() pode devolver notacao cientifica para
        // inteiros grandes (ex.: 1E+2) — toPlainString() sempre expande.
        return stripped.toPlainString();
    }

    /**
     * Mesma logica de {@link #formatSum} (sem casas decimais artificiais),
     * so com separador de milhar (PT-BR, ponto) pra leitura — pedido
     * explicito do usuario apos ver "5000050000" dificil de ler de relance
     * (mesmo motivo/solucao ja aplicados a {@link #formatCount}). SO pra
     * TELA: {@link #copySum} continua copiando {@link #formatSum} (sem
     * pontuacao nenhuma) pra area de transferencia.
     */
    private static String formatSumDisplay(java.math.BigDecimal sum) {
        java.math.BigDecimal stripped = sum.stripTrailingZeros();
        int scale = Math.max(0, stripped.scale());
        java.text.NumberFormat nf = java.text.NumberFormat.getNumberInstance(new java.util.Locale("pt", "BR"));
        nf.setMinimumFractionDigits(scale);
        nf.setMaximumFractionDigits(scale);
        return nf.format(stripped);
    }

    /** Texto sem fundo/borda de botao, cor de link, cursor de mao — usado por {@link #loadAllButton}/{@link #exactTotalButton}. */
    private static void styleAsLink(JButton button) {
        button.setBorderPainted(false);
        button.setContentAreaFilled(false);
        button.setOpaque(false);
        button.setForeground(GridTheme.ACCENT_INFO);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setMargin(new java.awt.Insets(0, 0, 0, 0));
        button.setFont(button.getFont().deriveFont(11f));
    }
}
