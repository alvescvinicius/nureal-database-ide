package com.nureal.ide.ui;

import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.BorderLayout;

/**
 * Uma aba de resultado completa: {@link ResultGrid} (dados: tabela,
 * cabecalho, numeracao de linhas, filtro, selecao, menus) mais
 * {@link ResultStatusBar} (contagem/tempos/paginacao/exportacao) — a unidade
 * que {@link MainWindow} instancia e encaixa numa aba, sem precisar montar a
 * composicao ela mesma. Isto e o que falta para {@code MainWindow} nao
 * conter NENHUMA logica de layout do resultado, so orquestracao (criar o
 * model, decidir os callbacks de paginacao/exportacao que dependem do cursor
 * JDBC, e entregar tudo pronto para estas duas classes).
 */
final class ResultView {

    private final JPanel panel = new JPanel(new BorderLayout());
    private final ResultGrid grid;
    private final ResultStatusBar statusBar;

    ResultView(ResultGrid grid, ResultStatusBar statusBar) {
        this.grid = grid;
        this.statusBar = statusBar;
        panel.add(grid.asComponent(), BorderLayout.CENTER);
        panel.add(statusBar.asComponent(), BorderLayout.SOUTH);
    }

    JComponent asComponent() {
        return panel;
    }

    ResultGrid grid() {
        return grid;
    }

    ResultStatusBar statusBar() {
        return statusBar;
    }
}
