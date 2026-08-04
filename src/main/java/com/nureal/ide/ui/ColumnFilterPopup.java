package com.nureal.ide.ui;
import com.nureal.ide.compartilhado.designsystem.GridTheme;
import com.nureal.ide.compartilhado.designsystem.NSearchField;
import com.nureal.ide.compartilhado.designsystem.Buttons;
import com.nureal.ide.compartilhado.designsystem.Typography;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Point;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Popup de autofiltro "estilo Excel" de UMA coluna: campo de busca,
 * "Selecionar tudo" e a lista de valores distintos (com checkbox), cada um
 * marcado/desmarcado conforme o filtro ATUAL daquela coluna (ou todos
 * marcados, se ainda nao ha filtro nela). "OK" aplica so os valores marcados
 * (ver {@link ColumnValueFilter#setAllowedValues}); "Limpar filtro" remove
 * qualquer restricao desta coluna; "Cancelar"/clicar fora nao muda nada.
 *
 * Os VALORES (chave do mapa passado ao construtor) sao os mesmos usados pelo
 * filtro de verdade ({@link ColumnValueFilter#stringValue}) — o TEXTO exibido
 * ao lado de cada checkbox pode ser diferente (ex.: "(vazios)" para "").
 */
final class ColumnFilterPopup {

    private ColumnFilterPopup() {
    }

    /**
     * @param valueLabels  valor bruto (chave, ver {@link ColumnValueFilter#stringValue}) -> texto exibido, NA ORDEM que deve aparecer na lista
     * @param initiallyChecked valores (mesma chave) que devem comecar marcados
     * @param onApply      chamado com o conjunto de valores marcados ao clicar "OK"
     * @param onClearFilter chamado ao clicar "Limpar filtro" (remove a restricao desta coluna, sem abrir de novo)
     */
    static void show(JComponent invoker, Point anchor, Map<String, String> valueLabels, Set<String> initiallyChecked,
            Consumer<Set<String>> onApply, Runnable onClearFilter) {
        JPopupMenu popup = new JPopupMenu();
        popup.setLayout(new BorderLayout());

        NSearchField search = new NSearchField("Buscar valor...");

        Map<String, JCheckBox> checkboxes = new LinkedHashMap<>();
        JPanel listPanel = new JPanel();
        listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
        listPanel.setOpaque(false);
        for (Map.Entry<String, String> e : valueLabels.entrySet()) {
            JCheckBox cb = new JCheckBox(e.getValue(), initiallyChecked.contains(e.getKey()));
            cb.setOpaque(false);
            checkboxes.put(e.getKey(), cb);
            listPanel.add(cb);
        }

        JCheckBox selectAll = new JCheckBox("(Selecionar tudo)");
        selectAll.setOpaque(false);
        selectAll.setSelected(checkboxes.size() == initiallyChecked.size());
        selectAll.addActionListener(e -> {
            boolean checked = selectAll.isSelected();
            for (JCheckBox cb : checkboxes.values()) {
                if (cb.isVisible()) {
                    cb.setSelected(checked);
                }
            }
        });

        // Digitar no campo de busca so ESCONDE as linhas que nao batem (nao
        // desmarca nada) — igual ao Excel: a marcacao de um valor sobrevive
        // mesmo que ele fique temporariamente fora de vista pela busca.
        search.onTextChange(() -> {
            String needle = search.getText().trim().toLowerCase(Locale.ROOT);
            for (Map.Entry<String, JCheckBox> e : checkboxes.entrySet()) {
                boolean visible = needle.isEmpty()
                        || valueLabels.get(e.getKey()).toLowerCase(Locale.ROOT).contains(needle);
                e.getValue().setVisible(visible);
            }
            listPanel.revalidate();
            listPanel.repaint();
        });

        JScrollPane scroll = new JScrollPane(listPanel);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.setPreferredSize(new Dimension(220, Math.min(260, Math.max(60, checkboxes.size() * 22 + 30))));

        JButton okButton = new JButton("OK");
        JButton cancelButton = new JButton("Cancelar");
        JButton clearButton = new JButton("Limpar filtro");
        Buttons.stylePrimary(okButton);
        Buttons.styleSecondary(cancelButton);
        Buttons.styleSecondary(clearButton);

        okButton.addActionListener(e -> {
            popup.setVisible(false);
            Set<String> selected = new LinkedHashSet<>();
            for (Map.Entry<String, JCheckBox> entry : checkboxes.entrySet()) {
                if (entry.getValue().isSelected()) {
                    selected.add(entry.getKey());
                }
            }
            onApply.accept(selected);
        });
        cancelButton.addActionListener(e -> popup.setVisible(false));
        clearButton.addActionListener(e -> {
            popup.setVisible(false);
            onClearFilter.run();
        });

        JPanel top = new JPanel(new BorderLayout(0, 4));
        top.setOpaque(false);
        top.setBorder(BorderFactory.createEmptyBorder(6, 8, 4, 8));
        top.add(search, BorderLayout.NORTH);
        JPanel selectAllWrap = new JPanel(new BorderLayout());
        selectAllWrap.setOpaque(false);
        selectAllWrap.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, GridTheme.HEADER_BORDER),
                BorderFactory.createEmptyBorder(2, 0, 4, 0)));
        selectAllWrap.add(selectAll, BorderLayout.WEST);
        top.add(selectAllWrap, BorderLayout.SOUTH);

        JPanel bottom = new JPanel();
        bottom.setLayout(new BoxLayout(bottom, BoxLayout.X_AXIS));
        bottom.setBorder(BorderFactory.createEmptyBorder(6, 8, 8, 8));
        bottom.add(clearButton);
        bottom.add(Box.createHorizontalGlue());
        bottom.add(cancelButton);
        bottom.add(Box.createHorizontalStrut(6));
        bottom.add(okButton);

        if (checkboxes.isEmpty()) {
            JLabel empty = new JLabel("Sem valores para filtrar");
            Typography.tertiary(empty);
            empty.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
            popup.add(empty, BorderLayout.CENTER);
        } else {
            popup.add(scroll, BorderLayout.CENTER);
        }
        popup.add(top, BorderLayout.NORTH);
        popup.add(bottom, BorderLayout.SOUTH);

        popup.show(invoker, anchor.x, anchor.y);
        SwingUtilities.invokeLater(search::requestFocusInWindow);
    }
}
