package com.nureal.ide.ui;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import java.awt.BorderLayout;
import java.awt.FlowLayout;

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
    private final JButton loadMoreButton;
    private final JButton loadAllButton = new JButton("Carregar tudo");
    private final JButton exportButton = new JButton("Exportar");
    private final JMenuItem exportThisItem = new JMenuItem("Exportar este resultado...");
    private final JMenuItem exportAllItem = new JMenuItem("Exportar todos (uma aba por resultado)...");

    ResultStatusBar(int pageSize) {
        loadMoreButton = new JButton("Carregar mais " + pageSize);

        exportButton.setIcon(Icons.get(IconType.EXPORT, 14, MainWindow.ACCENT));
        exportButton.setToolTipText("Exportar resultado para Excel");
        JPopupMenu exportMenu = new JPopupMenu();
        exportMenu.add(exportThisItem);
        exportMenu.add(exportAllItem);
        exportButton.addActionListener(e -> exportMenu.show(exportButton, 0, exportButton.getHeight()));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 3));
        left.add(info);
        left.add(loadMoreButton);
        left.add(loadAllButton);
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 3));
        right.add(exportButton);

        panel.add(left, BorderLayout.WEST);
        panel.add(right, BorderLayout.EAST);
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

    /** Atualiza o texto de contagem/tempos e mostra/esconde os botoes de paginacao. */
    void refresh(int rowCount, long execMs, long fetchMs, boolean hasMore) {
        info.setText(rowCount + " linha(s)" + (hasMore ? "+" : "")
                + "   ·   execucao " + execMs + " ms"
                + "   ·   busca " + fetchMs + " ms");
        loadMoreButton.setVisible(hasMore);
        loadAllButton.setVisible(hasMore);
    }
}
