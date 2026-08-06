package com.nureal.ide.ui;
import com.nureal.ide.compartilhado.designsystem.GridTheme;
import com.nureal.ide.compartilhado.designsystem.NSearchField;
import com.nureal.ide.compartilhado.designsystem.Buttons;
import com.nureal.ide.compartilhado.designsystem.Typography;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.ListCellRenderer;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.LinkedHashSet;
import java.util.List;
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
 *
 * A lista e um {@link JList} VIRTUALIZADO (um unico {@link JCheckBox}
 * reutilizado como "carimbo" de renderizacao, ver {@link CheckboxRenderer}) —
 * nao mais um {@code JCheckBox} REAL por valor. Uma coluna de alta
 * cardinalidade (ex.: "nome" numa grade com 100 mil linhas, cada uma com um
 * valor distinto) chegava a criar 100 mil componentes Swing pesados de uma
 * vez dentro de um {@code BoxLayout}, travando a UI por varios segundos so
 * pra abrir o popup — pedido explicito do usuario pra otimizar o filtro em
 * tabelas grandes. Com {@code JList}, so as linhas VISIVEIS na area de
 * rolagem ganham um renderer.
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

        List<String> allKeys = List.copyOf(valueLabels.keySet());
        Set<String> checkedKeys = new LinkedHashSet<>(initiallyChecked);

        DefaultListModel<String> listModel = new DefaultListModel<>();
        for (String key : allKeys) {
            listModel.addElement(key);
        }

        JList<String> list = new JList<>(listModel);
        list.setFixedCellHeight(22); // fixo: evita o JList medir a altura de CADA item (essencial pra virtualizar bem com muitos valores)
        list.setVisibleRowCount(-1);
        list.setCellRenderer(new CheckboxRenderer(valueLabels, checkedKeys));

        // Sem selecao "de foco" do JList em si — o clique so alterna o
        // checkbox do item (ver abaixo); a selecao nativa do JList nao tem
        // papel aqui.
        list.setSelectionModel(new javax.swing.DefaultListSelectionModel() {
            @Override
            public void setSelectionInterval(int index0, int index1) {
                // no-op: evita o realce de selecao padrao do JList
            }
        });

        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int index = list.locationToIndex(e.getPoint());
                if (index < 0) {
                    return;
                }
                String key = listModel.getElementAt(index);
                if (checkedKeys.contains(key)) {
                    checkedKeys.remove(key);
                } else {
                    checkedKeys.add(key);
                }
                list.repaint();
            }
        });

        JCheckBox selectAll = new JCheckBox("(Selecionar tudo)");
        selectAll.setOpaque(false);
        selectAll.setSelected(allKeys.size() == checkedKeys.size());
        selectAll.addActionListener(e -> {
            boolean checked = selectAll.isSelected();
            for (int i = 0; i < listModel.size(); i++) {
                String key = listModel.getElementAt(i);
                if (checked) {
                    checkedKeys.add(key);
                } else {
                    checkedKeys.remove(key);
                }
            }
            list.repaint();
        });

        // Digitar no campo de busca so ESCONDE os itens que nao batem (nao
        // desmarca nada) — igual ao Excel: a marcacao de um valor sobrevive
        // mesmo que ele fique temporariamente fora de vista pela busca.
        // Refiltra o MODELO da lista (nao a visibilidade de componentes
        // individuais, que nao existem mais um-a-um).
        search.onTextChange(() -> {
            String needle = search.getText().trim().toLowerCase(Locale.ROOT);
            listModel.clear();
            for (String key : allKeys) {
                if (needle.isEmpty() || valueLabels.get(key).toLowerCase(Locale.ROOT).contains(needle)) {
                    listModel.addElement(key);
                }
            }
        });

        JScrollPane scroll = new JScrollPane(list);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.setPreferredSize(new Dimension(220, Math.min(260, Math.max(60, allKeys.size() * 22 + 30))));

        JButton okButton = new JButton("OK");
        JButton cancelButton = new JButton("Cancelar");
        JButton clearButton = new JButton("Limpar filtro");
        Buttons.stylePrimary(okButton);
        Buttons.styleSecondary(cancelButton);
        Buttons.styleSecondary(clearButton);

        okButton.addActionListener(e -> {
            popup.setVisible(false);
            onApply.accept(new LinkedHashSet<>(checkedKeys));
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

        if (allKeys.isEmpty()) {
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

    /**
     * Reutiliza um UNICO {@link JCheckBox} como "carimbo" de pintura pra
     * cada linha visivel do {@link JList} — o mesmo padrao de
     * {@code DefaultTableCellRenderer}/{@code DefaultListCellRenderer}, so
     * que devolvendo um checkbox real (com o L&F/tema aplicado
     * normalmente) em vez de um {@code JLabel}. O componente devolvido NUNCA
     * e adicionado a arvore de UI de verdade — so pintado na celula pelo
     * proprio {@code JList} — entao um so basta pra lista inteira, nao
     * importa quantos valores existam.
     */
    private static final class CheckboxRenderer extends JCheckBox implements ListCellRenderer<String> {
        private final Map<String, String> valueLabels;
        private final Set<String> checkedKeys;

        CheckboxRenderer(Map<String, String> valueLabels, Set<String> checkedKeys) {
            this.valueLabels = valueLabels;
            this.checkedKeys = checkedKeys;
            setOpaque(true);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends String> list, String key, int index,
                boolean isSelected, boolean cellHasFocus) {
            setText(valueLabels.get(key));
            setSelected(checkedKeys.contains(key));
            setBackground(list.getBackground());
            setForeground(list.getForeground());
            return this;
        }
    }
}
