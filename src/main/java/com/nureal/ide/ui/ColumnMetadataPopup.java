package com.nureal.ide.ui;

import com.nureal.ide.core.metadata.model.ForeignKeyInfo;
import com.nureal.ide.core.metadata.model.IndexInfo;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JRootPane;
import javax.swing.JTable;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.table.JTableHeader;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Popup (nao um simples tooltip) com os metadados completos de uma coluna,
 * exibido ao passar o mouse sobre o nome dela no cabecalho da grade — nome,
 * tipo SQL, nullable, PK, FK (tabela/coluna relacionada), indices, precisao,
 * escala, tamanho, auto increment e comentario. Some automaticamente assim
 * que o mouse sai do cabecalho ou muda de coluna.
 *
 * E deliberadamente NAO interativo (sem botao de copiar, sem foco) — ao
 * contrario do antigo popup de FK (clique + copiavel), que atendia um caso
 * de uso diferente (ler com calma/copiar os detalhes de uma FK). Esse caso
 * agora e coberto pelo menu de contexto "Informacoes da coluna" (ver
 * {@link ResultHeaderContextMenu}), que abre um dialogo persistente com os
 * mesmos dados. Manter os dois popups por clique competiria com o gesto de
 * hover exigido aqui.
 */
final class ColumnMetadataPopup {

    private static final int SHOW_DELAY_MS = 400;

    private final JTable table;
    private final JTableHeader header;
    private final ColumnHeaderRenderer.MetadataSource metadataSource;
    private final Timer showTimer;

    private JPopupMenu current;
    private int shownForColumn = -1;

    private ColumnMetadataPopup(JTable table, JTableHeader header,
            ColumnHeaderRenderer.MetadataSource metadataSource) {
        this.table = table;
        this.header = header;
        this.metadataSource = metadataSource;
        this.showTimer = new Timer(SHOW_DELAY_MS, e -> showForPoint(lastPoint));
        this.showTimer.setRepeats(false);
    }

    private java.awt.Point lastPoint;

    static void install(JTable table, JTableHeader header, ColumnHeaderRenderer.MetadataSource metadataSource) {
        ColumnMetadataPopup popup = new ColumnMetadataPopup(table, header, metadataSource);
        header.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                popup.onMouseMoved(e);
            }
        });
        header.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseExited(MouseEvent e) {
                popup.hide();
            }

            @Override
            public void mousePressed(MouseEvent e) {
                popup.hide();
            }
        });
    }

    private void onMouseMoved(MouseEvent e) {
        int col = header.columnAtPoint(e.getPoint());
        if (col < 0) {
            hide();
            return;
        }
        if (col == shownForColumn && current != null && current.isVisible()) {
            return; // ja mostrando esta coluna
        }
        hide();
        lastPoint = e.getPoint();
        shownForColumn = col;
        showTimer.restart();
    }

    private void showForPoint(java.awt.Point point) {
        if (point == null || !header.isShowing()) {
            return;
        }
        int viewColumn = header.columnAtPoint(point);
        if (viewColumn < 0) {
            return;
        }
        int modelColumn = table.getColumnModel().getColumn(viewColumn).getModelIndex();
        ColumnMetadata meta = metadataSource.metadataFor(modelColumn);
        if (meta == null) {
            return;
        }
        JPopupMenu popup = new JPopupMenu();
        popup.setFocusable(false); // hover-only: nunca rouba foco da grade
        popup.add(buildContent(meta));
        popup.show(header, point.x, header.getHeight());
        current = popup;
    }

    private void hide() {
        showTimer.stop();
        if (current != null) {
            current.setVisible(false);
            current = null;
        }
        shownForColumn = -1;
    }

    /**
     * Dialogo PERSISTENTE (ao contrario do popup de hover, que some sozinho)
     * com os mesmos metadados — usado pelo item de menu "Informacoes da
     * coluna" (menu de contexto da celula e do cabecalho), para quem quiser
     * ler com calma ou selecionar/copiar o texto.
     *
     * NAO-MODAL de proposito: e um JDialog comum (nao um
     * {@code JOptionPane.showMessageDialog}, que bloqueia e so fecha no OK)
     * para poder fechar sozinho quando o usuario clica fora dele — igual um
     * popup, so que sem perder a selecao/copia de texto. O fechamento usa
     * {@code windowLostFocus}: como o dialogo nao e modal, clicar em
     * QUALQUER outra janela (a principal, outro dialogo, etc.) tira o foco
     * da janela do dialogo e dispara o fechamento; clicar DENTRO dele (para
     * selecionar um valor, por exemplo) so move o foco entre campos da MESMA
     * janela, o que nao conta como perda de foco da janela. Esc tambem
     * fecha, como reforco.
     */
    static void showDialog(java.awt.Component parent, ColumnMetadata meta) {
        Window owner = SwingUtilities.getWindowAncestor(parent);
        JDialog dialog = new JDialog(owner, "Informacoes da coluna: " + meta.label(),
                JDialog.ModalityType.MODELESS);
        dialog.setResizable(false);
        dialog.getContentPane().add(buildContent(meta, true));
        dialog.pack();
        // Centralizado na JANELA (owner), nao no componente que disparou o
        // menu (uma celula/cabecalho especifico dentro da grade) — senao o
        // dialogo aparece em qualquer canto da tela, dependendo de onde a
        // celula estava. Ver DialogUtil para o mesmo padrao em todo o app.
        dialog.setLocationRelativeTo(owner);

        dialog.addWindowFocusListener(new WindowAdapter() {
            @Override
            public void windowLostFocus(WindowEvent e) {
                dialog.dispose();
            }
        });

        JRootPane root = dialog.getRootPane();
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "close-dialog");
        root.getActionMap().put("close-dialog", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                dialog.dispose();
            }
        });

        dialog.setVisible(true);
    }

    /** Monta o painel com os metadados formatados (popup de hover — texto NAO selecionavel, por design). */
    static JPanel buildContent(ColumnMetadata meta) {
        return buildContent(meta, false);
    }

    /**
     * @param selectable quando {@code true} (dialogo persistente "Informacoes
     *                   da coluna"), cada valor vira um campo de texto
     *                   selecionavel — da pra marcar e Ctrl+C. Quando
     *                   {@code false} (popup de hover), continua puro texto
     *                   (JLabel), reforcando que ali e so para bater o olho,
     *                   nao para copiar (ver javadoc da classe).
     */
    private static JPanel buildContent(ColumnMetadata meta, boolean selectable) {
        JPanel content = new JPanel(new GridBagLayout());
        content.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));

        GridBagConstraints gc = new GridBagConstraints();
        gc.gridx = 0;
        gc.gridy = 0;
        gc.anchor = GridBagConstraints.NORTHWEST;
        gc.insets = new Insets(1, 0, 1, 12);

        JComponent title = textPart(meta.label(), selectable);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 13f));
        gc.gridwidth = 2;
        content.add(title, gc);
        gc.gridwidth = 1;
        gc.gridy++;

        addRow(content, gc, "Tipo SQL", displaySqlType(meta), selectable);
        addRow(content, gc, "Tipo Java", meta.javaType() == null ? "—" : meta.javaType().getSimpleName(), selectable);
        addRow(content, gc, "Nullable", meta.jdbcMeta().nullable() ? "Sim" : "Nao", selectable);
        addRow(content, gc, "Precisao", String.valueOf(meta.jdbcMeta().precision()), selectable);
        addRow(content, gc, "Escala", String.valueOf(meta.jdbcMeta().scale()), selectable);
        addRow(content, gc, "Tamanho", String.valueOf(meta.jdbcMeta().displaySize()), selectable);
        addRow(content, gc, "Auto increment", meta.jdbcMeta().autoIncrement() ? "Sim" : "Nao", selectable);
        addRow(content, gc, "Schema",
                (meta.schema() == null || meta.schema().isBlank()) ? "—" : meta.schema(), selectable);
        addRow(content, gc, "Tabela de origem",
                (meta.sourceTable() == null || meta.sourceTable().isBlank()) ? "—" : meta.sourceTable(), selectable);

        if (!meta.schemaLoaded()) {
            addRow(content, gc, "Chave primaria", "carregando...", selectable);
        } else {
            addRow(content, gc, "Chave primaria", meta.primaryKey() ? "Sim" : "Nao", selectable);
            if (meta.hasForeignKey()) {
                ForeignKeyInfo fk = meta.foreignKey();
                addRow(content, gc, "Chave estrangeira", fk.name(), selectable);
                addRow(content, gc, "Tabela relacionada", fk.referencedTable(), selectable);
                addRow(content, gc, "Coluna relacionada", String.join(", ", fk.referencedColumns()), selectable);
            }
            if (meta.hasIndexes()) {
                addRow(content, gc, "Indices", indexSummary(meta.indexes()), selectable);
            }
            if (meta.hasComment()) {
                addRow(content, gc, "Comentario", meta.comment(), selectable);
            }
        }
        return content;
    }

    private static String displaySqlType(ColumnMetadata meta) {
        String type = meta.sqlType();
        return (type == null || type.isBlank()) ? "(desconhecido)" : type;
    }

    private static String indexSummary(List<IndexInfo> indexes) {
        return indexes.stream()
                .map(i -> i.name() + (i.unique() ? " (unico)" : ""))
                .collect(Collectors.joining(", "));
    }

    private static void addRow(JPanel panel, GridBagConstraints gc, String label, String value, boolean selectable) {
        JLabel labelComp = new JLabel(label + ":");
        labelComp.setForeground(GridTheme.MUTED_TEXT);
        labelComp.setFont(labelComp.getFont().deriveFont(11f));
        gc.gridx = 0;
        panel.add(labelComp, gc);

        JComponent valueComp = textPart(value, selectable);
        valueComp.setFont(valueComp.getFont().deriveFont(11f));
        gc.gridx = 1;
        panel.add(valueComp, gc);

        gc.gridy++;
    }

    /**
     * {@code selectable=false}: JLabel comum (popup de hover, ver
     * {@link #buildContent(ColumnMetadata)}). {@code selectable=true}: um
     * "rotulo" selecionavel/copiavel com Ctrl+C (ver {@link SelectableLabel}).
     * Usado pelo dialogo persistente "Informacoes da coluna" (ver
     * {@link #showDialog}), que existe justamente para isso.
     */
    private static JComponent textPart(String value, boolean selectable) {
        return selectable ? SelectableLabel.of(value) : new JLabel(value);
    }
}
