package com.nureal.ide.ui;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.FlowLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Barra inferior de uma aba de resultado: a esquerda, contagem de linhas +
 * tempos de execucao/busca + paginacao ("Carregar mais N" / "Carregar
 * tudo"); a direita, o botao "Exportar" com seu menu.
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
    private final JButton loadMoreButton;
    private final JButton loadAllButton = new JButton("Carregar tudo");
    private final JButton exportButton = new JButton("Exportar");
    private final JMenuItem exportThisItem = new JMenuItem("Exportar este resultado...");
    private final JMenuItem exportAllItem = new JMenuItem("Exportar todos (uma aba por resultado)...");

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

    ResultStatusBar(int pageSize) {
        loadMoreButton = new JButton("Carregar mais " + pageSize);

        exportButton.setIcon(Icons.get(IconType.EXPORT, 14, MainWindow.ACCENT));
        exportButton.setToolTipText("Exportar resultado para Excel");
        JPopupMenu exportMenu = new JPopupMenu();
        exportMenu.add(exportThisItem);
        exportMenu.add(exportAllItem);
        exportButton.addActionListener(e -> exportMenu.show(exportButton, 0, exportButton.getHeight()));

        selectionSummary.setForeground(GridTheme.MUTED_TEXT);
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
        left.add(loadMoreButton);
        left.add(loadAllButton);
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
        addRowButton.setIcon(Icons.get(IconType.NEW, 13, new java.awt.Color(0x334155)));
        deleteRowsButton.setIcon(Icons.get(IconType.DELETE, 13, new java.awt.Color(0x334155)));
        saveChangesButton.setIcon(Icons.get(IconType.SAVE, 13, MainWindow.ACCENT));
        pendingLabel.setForeground(GridTheme.MUTED_TEXT);

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

    void onLoadMore(Runnable action) {
        loadMoreButton.addActionListener(e -> action.run());
    }

    void onLoadAll(Runnable action) {
        loadAllButton.addActionListener(e -> action.run());
    }

    void onExportThis(Runnable action) {
        exportThisItem.addActionListener(e -> action.run());
    }

    void onExportAll(Runnable action) {
        exportAllItem.addActionListener(e -> action.run());
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

    /** Atualiza o texto de contagem/tempos e mostra/esconde os botoes de paginacao. */
    void refresh(int rowCount, long execMs, long fetchMs, boolean hasMore) {
        info.setText(rowCount + " linha(s)" + (hasMore ? "+" : "")
                + "   ·   execucao " + execMs + " ms"
                + "   ·   busca " + fetchMs + " ms");
        loadMoreButton.setVisible(hasMore);
        loadAllButton.setVisible(hasMore);
    }

    /**
     * Atualiza o resumo de selecao ("N selecionada(s) · Soma: X") — chamado
     * pela {@link ResultGrid} sempre que a selecao de celulas muda (ver
     * {@code MainWindow#buildGridPanel}, que liga os dois). Pedido explicito
     * do usuario: selecionar N celulas soma so essas N; selecionar a coluna
     * inteira soma a coluna inteira — igual a barra de status do Excel.
     *
     * {@code count <= 1} nao mostra nada: com uma unica celula selecionada o
     * proprio valor ja esta visivel na grade, um "1 selecionada" fixo so
     * seria ruido durante a navegacao normal (mesmo comportamento do Excel).
     *
     * @param sum {@code null} quando nenhum valor numerico entra na selecao
     *            (texto/nulos/datas) — nesse caso mostra so a contagem.
     */
    void updateSelectionSummary(int count, java.math.BigDecimal sum) {
        lastSum = sum;
        if (count <= 1) {
            selectionSummary.setText("");
            selectionSummary.setToolTipText(null);
            selectionSummary.setCursor(Cursor.getDefaultCursor());
            return;
        }
        String text = count + " selecionada(s)";
        if (sum != null) {
            text += "   ·   Soma: " + formatSum(sum);
            selectionSummary.setToolTipText("Clique para copiar a soma");
            selectionSummary.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        } else {
            selectionSummary.setToolTipText(null);
            selectionSummary.setCursor(Cursor.getDefaultCursor());
        }
        selectionSummary.setText(text);
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
}
