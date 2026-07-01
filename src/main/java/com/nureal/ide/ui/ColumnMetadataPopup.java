package com.nureal.ide.ui;

import com.nureal.ide.core.metadata.model.ForeignKeyInfo;
import com.nureal.ide.core.metadata.model.IndexInfo;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTable;
import javax.swing.Timer;
import javax.swing.table.JTableHeader;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
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
        this.showTimer = new Timer(SHOW_DELAY_MS, k -> showForPoint(lastPoint));
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
     */
    static void showDialog(java.awt.Component parent, ColumnMetadata meta) {
        javax.swing.JOptionPane.showMessageDialog(parent, buildContent(meta),
                "Informacoes da coluna: " + meta.label(), javax.swing.JOptionPane.PLAIN_MESSAGE);
    }

    /** Monta o painel com os metadados formatados — reutilizado pelo popup de hover e pelo dialogo "Informacoes da coluna". */
    static JPanel buildContent(ColumnMetadata meta) {
        JPanel content = new JPanel(new GridBagLayout());
        content.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));

        GridBagConstraints gc = new GridBagConstraints();
        gc.gridx = 0;
        gc.gridy = 0;
        gc.anchor = GridBagConstraints.NORTHWEST;
        gc.insets = new Insets(1, 0, 1, 12);

        JLabel title = new JLabel(meta.label());
        title.setFont(title.getFont().deriveFont(Font.BOLD, 13f));
        gc.gridwidth = 2;
        content.add(title, gc);
        gc.gridwidth = 1;
        gc.gridy++;

        addRow(content, gc, "Tipo SQL", displaySqlType(meta));
        addRow(content, gc, "Tipo Java", meta.javaType() == null ? "—" : meta.javaType().getSimpleName());
        addRow(content, gc, "Nullable", meta.jdbcMeta().nullable() ? "Sim" : "Nao");
        addRow(content, gc, "Precisao", String.valueOf(meta.jdbcMeta().precision()));
        addRow(content, gc, "Escala", String.valueOf(meta.jdbcMeta().scale()));
        addRow(content, gc, "Tamanho", String.valueOf(meta.jdbcMeta().displaySize()));
        addRow(content, gc, "Auto increment", meta.jdbcMeta().autoIncrement() ? "Sim" : "Nao");
        addRow(content, gc, "Schema",
                (meta.schema() == null || meta.schema().isBlank()) ? "—" : meta.schema());
        addRow(content, gc, "Tabela de origem",
                (meta.sourceTable() == null || meta.sourceTable().isBlank()) ? "—" : meta.sourceTable());

        if (!meta.schemaLoaded()) {
            addRow(content, gc, "Chave primaria", "carregando...");
        } else {
            addRow(content, gc, "Chave primaria", meta.primaryKey() ? "Sim" : "Nao");
            if (meta.hasForeignKey()) {
                ForeignKeyInfo fk = meta.foreignKey();
                addRow(content, gc, "Chave estrangeira", fk.name());
                addRow(content, gc, "Tabela relacionada", fk.referencedTable());
                addRow(content, gc, "Coluna relacionada", String.join(", ", fk.referencedColumns()));
            }
            if (meta.hasIndexes()) {
                addRow(content, gc, "Indices", indexSummary(meta.indexes()));
            }
            if (meta.hasComment()) {
                addRow(content, gc, "Comentario", meta.comment());
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

    private static void addRow(JPanel panel, GridBagConstraints gc, String label, String value) {
        JLabel labelComp = new JLabel(label + ":");
        labelComp.setForeground(GridTheme.MUTED_TEXT);
        labelComp.setFont(labelComp.getFont().deriveFont(11f));
        gc.gridx = 0;
        panel.add(labelComp, gc);

        JLabel valueComp = new JLabel(value);
        valueComp.setFont(valueComp.getFont().deriveFont(11f));
        gc.gridx = 1;
        panel.add(valueComp, gc);

        gc.gridy++;
    }
}
