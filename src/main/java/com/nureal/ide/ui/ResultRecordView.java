package com.nureal.ide.ui;
import com.nureal.ide.compartilhado.designsystem.GridTheme;
import com.nureal.ide.compartilhado.designsystem.Typography;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.function.IntConsumer;

/**
 * "Modo registro": mostra UMA linha do resultado por vez, cada coluna numa
 * linha propria ("nome da coluna: valor"), empilhadas verticalmente — pedido
 * explicito do usuario ("mostrar a grade na vertical tambem"), mesmo conceito
 * do alternador Grade/Texto do DBeaver: util pra ver TODOS os campos de UM
 * registro sem rolar horizontalmente, quando a consulta tem muitas colunas.
 *
 * So LEITURA (nao reaproveita {@link GridEditController}/os renderers
 * tipados por celula da grade — ver {@code AbstractTypedCellRenderer}):
 * valores sao exibidos como texto simples (nulo em itálico/cinza, mesma cor
 * {@link GridTheme#COLOR_NULL} da grade, unico ponto reaproveitado). Editar
 * um valor continua exigindo voltar pra grade — this e um inspetor, nao um
 * formulario de edicao.
 *
 * Le direto do {@link ResultTableModel} pelo INDICE DE MODELO da linha (nunca
 * indice de VIEW, que muda com filtro/ordenacao da grade) — {@link #showRow}
 * e quem recebe esse indice.
 */
final class ResultRecordView extends JPanel {

    private static final long serialVersionUID = 1L;

    private final ResultTableModel model;
    private final JTable table;
    /** Chamado quando o usuario navega (Anterior/Proximo) para o indice de MODELO da nova linha — {@link ResultGrid} usa isto pra manter a selecao da grade em dia. */
    private final IntConsumer onNavigate;

    private final JPanel fieldsPanel = new JPanel();
    private final JLabel positionLabel = new JLabel();
    private final JButton prevButton = new JButton("< Anterior");
    private final JButton nextButton = new JButton("Proximo >");

    private int currentRow = -1;

    /**
     * Mesma familia de bug ja corrigida em varios lugares deste app
     * ({@code ResultGrid}/{@code ConnectionsPanel} etc.): as cores lidas de
     * {@link GridTheme} em {@link #buildFieldRow} sao "queimadas" no
     * {@code JLabel} no momento em que a linha e montada — se o tema mudar
     * (ver {@code MainWindow#toggleTheme}) enquanto este painel ja esta
     * visivel, precisa remontar pra refletir a paleta nova.
     */
    @Override
    public void updateUI() {
        super.updateUI();
        if (fieldsPanel != null) {
            rebuild();
        }
    }

    ResultRecordView(ResultTableModel model, JTable table, IntConsumer onNavigate) {
        super(new BorderLayout());
        this.model = model;
        this.table = table;
        this.onNavigate = onNavigate;

        fieldsPanel.setLayout(new BoxLayout(fieldsPanel, BoxLayout.Y_AXIS));
        fieldsPanel.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));
        JScrollPane scroll = new JScrollPane(fieldsPanel);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(16);

        prevButton.addActionListener(e -> navigate(-1));
        nextButton.addActionListener(e -> navigate(1));
        Typography.tertiary(positionLabel);

        JPanel nav = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 6));
        nav.add(prevButton);
        nav.add(positionLabel);
        nav.add(nextButton);

        add(nav, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
    }

    private void navigate(int delta) {
        int next = currentRow + delta;
        if (next < 0 || next >= model.getRowCount()) {
            return;
        }
        showRow(next);
        onNavigate.accept(next);
    }

    /** Mostra a linha (indice de MODELO) informada — {@code -1} ou fora da faixa vira o estado "sem registro". */
    void showRow(int modelRow) {
        currentRow = (modelRow >= 0 && modelRow < model.getRowCount()) ? modelRow : -1;
        rebuild();
    }

    /** Reconstroi os campos exibidos para {@link #currentRow} — chamado tambem apos re-tema (cores) ou paginacao (mais linhas carregadas). */
    void refresh() {
        rebuild();
    }

    private void rebuild() {
        fieldsPanel.removeAll();
        int rowCount = model.getRowCount();
        boolean hasRow = currentRow >= 0 && currentRow < rowCount;
        prevButton.setEnabled(hasRow && currentRow > 0);
        nextButton.setEnabled(hasRow && currentRow < rowCount - 1);
        positionLabel.setText(hasRow ? "Registro " + (currentRow + 1) + " de " + rowCount : "Nenhum registro selecionado");

        if (hasRow) {
            int columns = model.getColumnCount();
            for (int col = 0; col < columns; col++) {
                fieldsPanel.add(buildFieldRow(col));
                if (col < columns - 1) {
                    fieldsPanel.add(Box.createVerticalStrut(6));
                }
            }
        } else {
            JLabel empty = new JLabel("Selecione uma linha na grade (ou use \"Ver como registro\" com uma linha ja selecionada).");
            Typography.tertiary(empty);
            fieldsPanel.add(empty);
        }
        fieldsPanel.revalidate();
        fieldsPanel.repaint();
    }

    private JPanel buildFieldRow(int col) {
        JLabel nameLabel = new JLabel(model.getColumnName(col));
        Typography.tertiary(nameLabel);
        nameLabel.setFont(nameLabel.getFont().deriveFont(11f));

        Object value = model.getValueAt(currentRow, col);
        JLabel valueLabel = new JLabel(value == null ? "NULL" : String.valueOf(value));
        valueLabel.setFont(valueLabel.getFont().deriveFont(value == null ? Font.ITALIC : Font.PLAIN, 13f));
        valueLabel.setForeground(value == null ? GridTheme.COLOR_NULL : GridTheme.COLOR_TEXTUAL);
        valueLabel.setHorizontalAlignment(SwingConstants.LEFT);

        JPanel row = new JPanel(new BorderLayout(0, 1));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.add(nameLabel, BorderLayout.NORTH);
        row.add(valueLabel, BorderLayout.CENTER);
        // BoxLayout Y_AXIS estica componentes cujo getMaximumSize() permite
        // (o padrao de JComponent e "sem limite") — sem travar a altura no
        // PREFERIDO, cada linha esticaria pra preencher o espaco vertical
        // sobrando, empurrando os campos pra longe uns dos outros em vez de
        // ficarem compactos e colados.
        row.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
        return row;
    }
}
