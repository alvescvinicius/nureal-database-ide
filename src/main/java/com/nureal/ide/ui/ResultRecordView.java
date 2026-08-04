package com.nureal.ide.ui;
import com.nureal.ide.compartilhado.designsystem.GridTheme;
import com.nureal.ide.compartilhado.designsystem.Typography;
import com.nureal.ide.core.log.AppLogger;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.IntConsumer;

/**
 * "Modo registro": mostra UMA linha do resultado por vez, cada coluna numa
 * linha propria no formato "coluna: valor", empilhadas verticalmente —
 * pedido explicito do usuario ("mostrar a grade na vertical tambem"), mesmo
 * conceito do alternador Grade/Texto do DBeaver: util pra ver TODOS os
 * campos de UM registro sem rolar horizontalmente, quando a consulta tem
 * muitas colunas.
 *
 * Paridade com a grade horizontal (pedido explicito do usuario: "mesmo
 * visual e todas as mesmas funcionalidades do grid, so que vertical" —
 * incluindo copiar e navegar pela origem de chaves estrangeiras):
 * <ul>
 * <li>Visual: cada campo vira uma "linha de tabela" (zebra + borda inferior
 * com {@link GridTheme#GRID_LINE}), rotulo e valor na MESMA linha (rotulos
 * alinhados numa coluna fixa, como um mini-grid de 2 colunas), com o valor
 * formatado (cor/fonte) pelo MESMO renderer tipado que a grade usa — ver
 * {@link #renderValueLabel}.</li>
 * <li>Edicao: quando o {@link GridEditController} da grade esta com o modo
 * de edicao ligado E a celula e editavel ({@link ResultTableModel#isCellEditable}
 * — MESMA verificacao que a grade usa, nenhuma logica de permissao
 * duplicada), o campo mostra o MESMO {@link TableCellEditor} que a coluna
 * usaria na grade (inclusive {@code TemporalCellEditor} pra data/hora) — ver
 * {@link #buildEditableValue}. O valor digitado e aplicado direto no
 * {@link ResultTableModel} ({@code model.setValueAt}), que e o MESMO modelo
 * que a grade le/escreve — o {@link GridEditController} (que so ouve o
 * MODELO, nao a grade em si) marca a celula como suja normalmente, entao
 * "Salvar alteracoes"/"Descartar" funcionam igual, venha a edicao daqui ou
 * da grade.</li>
 * <li>Menu de contexto (clique direito num campo): "Copiar valor" sempre, e
 * "Visualizar Origem" quando o campo e uma chave estrangeira — reaproveita o
 * MESMO {@link ResultContextMenu.FkOriginHandler} que {@link ResultGrid} ja
 * constroi pro menu/icone de FK da grade (nenhuma logica de abrir o
 * inspetor de FK duplicada aqui).</li>
 * </ul>
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
    private final ColumnHeaderRenderer.MetadataSource metadataSource;
    private final ResultContextMenu.FkOriginHandler fkOrigin;

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

    ResultRecordView(ResultTableModel model, JTable table, IntConsumer onNavigate,
            ColumnHeaderRenderer.MetadataSource metadataSource, ResultContextMenu.FkOriginHandler fkOrigin) {
        super(new BorderLayout());
        this.model = model;
        this.table = table;
        this.onNavigate = onNavigate;
        this.metadataSource = metadataSource;
        this.fkOrigin = fkOrigin;

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

    /**
     * Reconstroi os campos exibidos para {@link #currentRow} — chamado tambem
     * apos re-tema (cores), paginacao (mais linhas carregadas) e sempre que o
     * {@link GridEditController} muda de estado (modo de edicao ligado/
     * desligado, linha marcada para exclusao etc. — ver
     * {@code ResultGrid#refreshRecordView}), pra alternar entre campo
     * so-leitura e campo editavel no mesmo instante.
     */
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
            int labelWidth = computeLabelColumnWidth(columns);
            for (int col = 0; col < columns; col++) {
                fieldsPanel.add(buildFieldRow(col, labelWidth));
            }
        } else {
            JLabel empty = new JLabel("Selecione uma linha na grade (ou use \"Ver como registro\" com uma linha ja selecionada).");
            Typography.tertiary(empty);
            empty.setBorder(BorderFactory.createEmptyBorder(6, 4, 6, 4));
            fieldsPanel.add(empty);
        }
        fieldsPanel.revalidate();
        fieldsPanel.repaint();
    }

    /**
     * Largura fixa da coluna de rotulos ("coluna:") — todas as linhas usam a
     * MESMA largura (a do nome mais comprido, com um teto) pra formar duas
     * colunas alinhadas, como um mini-grid de 2 colunas, em vez de cada
     * rotulo empurrar o valor pra uma posicao diferente.
     */
    private int computeLabelColumnWidth(int columns) {
        JLabel probe = new JLabel();
        Typography.tertiary(probe);
        FontMetrics fm = probe.getFontMetrics(probe.getFont().deriveFont(11f));
        int max = 60;
        for (int col = 0; col < columns; col++) {
            max = Math.max(max, fm.stringWidth(model.getColumnName(col) + ":"));
        }
        return Math.min(max, 220);
    }

    /**
     * Uma "linha de tabela" por campo (zebra + borda inferior, mesmas cores
     * {@link GridTheme#ZEBRA_EVEN}/{@link GridTheme#ZEBRA_ODD}/{@link GridTheme#GRID_LINE}
     * da grade horizontal), rotulo e valor lado a lado no formato "coluna:
     * valor" — pedido explicito do usuario. O indice do CAMPO (nao da linha
     * de dados) decide a zebra, ja que aqui so existe UM registro por vez.
     */
    private JPanel buildFieldRow(int col, int labelWidth) {
        JLabel nameLabel = new JLabel(model.getColumnName(col) + ":");
        Typography.tertiary(nameLabel);
        nameLabel.setFont(nameLabel.getFont().deriveFont(11f));
        nameLabel.setVerticalAlignment(SwingConstants.TOP);
        nameLabel.setPreferredSize(new Dimension(labelWidth, nameLabel.getPreferredSize().height));

        boolean editable = model.isCellEditable(currentRow, col);
        JComponent valueComponent = editable ? buildEditableValue(col)
                : renderValueLabel(col, model.getValueAt(currentRow, col));

        JPanel row = new JPanel(new BorderLayout(10, 0));
        row.setOpaque(true);
        row.setBackground((col % 2 == 0) ? GridTheme.ZEBRA_EVEN : GridTheme.ZEBRA_ODD);
        row.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, GridTheme.GRID_LINE),
                BorderFactory.createEmptyBorder(6, 10, 6, 10)));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.add(nameLabel, BorderLayout.WEST);
        row.add(valueComponent, BorderLayout.CENTER);
        // BoxLayout Y_AXIS estica componentes cujo getMaximumSize() permite
        // (o padrao de JComponent e "sem limite") — sem travar a altura no
        // PREFERIDO, cada linha esticaria pra preencher o espaco vertical
        // sobrando, empurrando os campos pra longe uns dos outros em vez de
        // ficarem compactos e colados, formando as "linhas de tabela".
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));

        installContextMenu(row, col);
        return row;
    }

    /**
     * Clique direito num campo: "Copiar valor" sempre; "Visualizar Origem"
     * so quando a coluna e uma chave estrangeira (mesmo metadado que decide
     * o icone de FK na grade — ver {@link AbstractTypedCellRenderer}).
     * Instalado no PAINEL da linha inteira (nao so no valor) pra funcionar
     * mesmo clicando no rotulo ou na area vazia da linha.
     */
    private void installContextMenu(JPanel row, int col) {
        row.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                maybeShow(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                maybeShow(e);
            }

            private void maybeShow(MouseEvent e) {
                if (!e.isPopupTrigger()) {
                    return;
                }
                JPopupMenu menu = new JPopupMenu();
                JMenuItem copy = new JMenuItem("Copiar valor");
                copy.addActionListener(a -> copyValueToClipboard(col));
                menu.add(copy);

                ColumnMetadata meta = metadataSource != null ? metadataSource.metadataFor(col) : null;
                if (meta != null && meta.hasForeignKey() && fkOrigin != null) {
                    JMenuItem viewOrigin = new JMenuItem("Visualizar Origem");
                    viewOrigin.addActionListener(a -> {
                        int viewRow = Math.max(table.convertRowIndexToView(currentRow), 0);
                        fkOrigin.openOrigin(meta, viewRow);
                    });
                    menu.addSeparator();
                    menu.add(viewOrigin);
                }
                menu.show(row, e.getX(), e.getY());
            }
        });
    }

    /** Copia o valor CRU (nao formatado) da celula pra area de transferencia — mesmo texto que {@code GridClipboard#copyCell} copiaria da grade. */
    private void copyValueToClipboard(int col) {
        if (currentRow < 0) {
            return;
        }
        Object value = model.getValueAt(currentRow, col);
        String text = (value == null) ? "" : String.valueOf(value);
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
    }

    /**
     * Formata o valor exatamente como a grade horizontal formataria (mesma
     * cor/fonte por tipo de dado — data, numero, booleano, binario,
     * identificador/chave primaria etc.). Reaproveita o MESMO renderer
     * tipado que {@link RendererFactory} instala na coluna da grade
     * ({@link AbstractTypedCellRenderer} e subclasses), so LENDO o
     * texto/cor/fonte que ele produziria (nunca reaproveitando a INSTANCIA
     * do componente em si: e compartilhada entre todas as celulas do mesmo
     * grupo/tipo em toda a grade — embuti-la direto neste painel a
     * reparentaria pra ca, sumindo da grade no proximo repaint dela).
     */
    private JLabel renderValueLabel(int col, Object value) {
        JLabel label = new JLabel();
        if (value == null) {
            label.setText("NULL");
            label.setFont(label.getFont().deriveFont(Font.ITALIC, 13f));
            label.setForeground(GridTheme.COLOR_NULL);
            label.setHorizontalAlignment(SwingConstants.LEFT);
            return label;
        }
        RendererFactory.Group group = RendererFactory.classify(
                model.sqlType(col), model.getColumnClass(col), model.getColumnName(col));
        TableCellRenderer renderer = RendererFactory.rendererFor(group);
        int viewRow = Math.max(table.convertRowIndexToView(currentRow), 0);
        int viewCol = table.convertColumnIndexToView(col);
        Component rendered = renderer.getTableCellRendererComponent(
                table, value, false, false, viewRow, viewCol >= 0 ? viewCol : col);
        label.setText(rendered instanceof JLabel jl ? jl.getText() : String.valueOf(value));
        label.setFont(rendered.getFont().deriveFont(13f));
        label.setForeground(rendered.getForeground());
        // SEMPRE esquerda, nunca o alinhamento do renderer da grade: la faz
        // sentido alinhar numero/identificador a DIREITA porque a celula e
        // ESTREITA (largura da coluna); aqui rotulo e valor dividem a
        // largura do painel so em DUAS colunas fixas, entao "direita"
        // jogava o texto colado no rotulo ou pra fora da area visivel do
        // valor — bug visual relatado pelo usuario.
        label.setHorizontalAlignment(SwingConstants.LEFT);
        return label;
    }

    /**
     * Campo EDITAVEL: mesmo {@link TableCellEditor} que a coluna usa na
     * grade (custom, ex. {@code TemporalCellEditor} pra data/hora, ou o
     * editor padrao do Swing pro tipo da coluna quando nao ha um custom
     * instalado) — reaproveita a MESMA logica de parse/validacao por tipo em
     * vez de reinventar um campo de texto generico. O editor NAO e
     * compartilhado entre colunas (cada coluna tem sua PROPRIA instancia,
     * ver {@code RendererFactory#installOn}), entao embutir o componente
     * aqui nao tem o mesmo risco de "reparentar e sumir" dos renderers (ver
     * {@link #renderValueLabel}) — a grade so voltaria a usar esta MESMA
     * instancia se o usuario clicasse pra editar a celula na grade
     * enquanto ela estiver visivel, o que nao acontece com "Ver como
     * registro" aberto (a grade fica escondida pelo CardLayout).
     */
    private JComponent buildEditableValue(int col) {
        Object value = model.getValueAt(currentRow, col);
        TableCellEditor editor = resolveEditor(col);
        int viewRow = Math.max(table.convertRowIndexToView(currentRow), 0);
        int viewCol = table.convertColumnIndexToView(col);
        Component editComp = editor.getTableCellEditorComponent(
                table, value, false, viewRow, viewCol >= 0 ? viewCol : col);
        editComp.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                commitEdit(editor, col);
            }
        });
        if (editComp instanceof JTextField textField) {
            // Enter tambem aplica, sem depender so de perder o foco (ex.:
            // usuario edita e clica direto em "Proximo").
            textField.addActionListener(e -> commitEdit(editor, col));
        }
        return (editComp instanceof JComponent jc) ? jc : wrapNonJComponent(editComp);
    }

    /** Editor da coluna: o mesmo instalado na grade (ex. data/hora), com fallback pro editor padrao do Swing pelo tipo da coluna — mesma resolucao que {@link RendererFactory#installOn} fez na grade. */
    private TableCellEditor resolveEditor(int col) {
        int viewCol = table.convertColumnIndexToView(col);
        if (viewCol >= 0) {
            TableCellEditor columnEditor = table.getColumnModel().getColumn(viewCol).getCellEditor();
            if (columnEditor != null) {
                return columnEditor;
            }
        }
        return table.getDefaultEditor(model.getColumnClass(col));
    }

    /** Aplica o valor do editor no modelo (mesmo caminho que {@code JTable#editingStopped} usa) — o {@link GridEditController} (que so ouve o MODELO) marca a celula como suja normalmente. */
    private void commitEdit(TableCellEditor editor, int col) {
        if (currentRow < 0) {
            return;
        }
        try {
            Object newValue = editor.getCellEditorValue();
            model.setValueAt(newValue, currentRow, col);
        } catch (RuntimeException ex) {
            AppLogger.warning("Falha ao aplicar edicao no modo registro", ex);
        }
    }

    private static JComponent wrapNonJComponent(Component c) {
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setOpaque(false);
        wrap.add(c, BorderLayout.CENTER);
        return wrap;
    }
}
