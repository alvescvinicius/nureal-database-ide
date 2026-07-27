package com.nureal.ide.ui;
import com.nureal.ide.compartilhado.designsystem.Buttons;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.regex.Pattern;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.RowFilter;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;

import com.nureal.ide.compartilhado.designsystem.NSearchField;

/**
 * Aba "campo de busca + tabela filtravel + Atualizar" — layout repetido
 * (antes copiado/colado) em {@link ServerStatusDialog}/
 * {@link EventsReplicationDialog}: busca filtra por regex (case-insensitive)
 * na coluna 0 da tabela, "Atualizar" chama {@code refreshAction}. NAO cobre
 * {@code EventsReplicationDialog#buildReplicationTab} — aquela aba nao tem
 * busca (Campo | Valor, uma unica linha de origem) e usa outro layout.
 */
final class SearchableTableTab {

    private SearchableTableTab() {
    }

    static JComponent build(JTable table, TableRowSorter<DefaultTableModel> sorter, Runnable refreshAction,
            Dimension scrollSize, JComponent footer) {
        NSearchField search = new NSearchField("Filtrar por nome...");
        search.onTextChange(() -> {
            String text = search.getText().trim();
            sorter.setRowFilter(text.isEmpty() ? null : RowFilter.regexFilter("(?i)" + Pattern.quote(text), 0));
        });

        JButton refresh = new JButton("Atualizar");
        Buttons.styleSecondary(refresh);
        refresh.addActionListener(a -> refreshAction.run());

        JPanel top = new JPanel(new BorderLayout(6, 0));
        top.setBorder(BorderFactory.createEmptyBorder(8, 8, 6, 8));
        top.add(search, BorderLayout.CENTER);
        JPanel refreshWrap = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        refreshWrap.add(refresh);
        top.add(refreshWrap, BorderLayout.EAST);

        JScrollPane scroll = new JScrollPane(table);
        scroll.setPreferredSize(scrollSize);

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(top, BorderLayout.NORTH);
        panel.add(scroll, BorderLayout.CENTER);
        if (footer != null) {
            panel.add(footer, BorderLayout.SOUTH);
        }
        return panel;
    }
}
