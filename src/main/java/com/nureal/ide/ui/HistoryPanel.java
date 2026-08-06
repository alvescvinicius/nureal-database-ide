package com.nureal.ide.ui;
import com.nureal.ide.compartilhado.designsystem.IconType;
import com.nureal.ide.compartilhado.designsystem.Icons;
import com.nureal.ide.compartilhado.designsystem.GridTheme;
import com.nureal.ide.compartilhado.designsystem.NEmptyState;

import com.nureal.ide.modulos.historico.infraestrutura.ExecutionHistoryStore;
import com.nureal.ide.modulos.historico.infraestrutura.ExecutionHistoryStore.Entry;
import com.nureal.ide.core.log.AppLogger;
import com.nureal.ide.compartilhado.designsystem.NSearchField;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Painel lateral com o HISTORICO de execucoes (log automatico, sem titulo —
 * ver {@link ExecutionHistoryStore}), mesmo estilo visual/estrutural do
 * {@link SavedQueriesPanel} para manter o padrao das abas "Conexoes" /
 * "Queries salvas" / "Historico".
 *
 * Filtra SEMPRE pela conexao ATIVA (igual SavedQueriesPanel): mostra so as
 * execucoes daquela conexao. Cada item mostra uma bolinha verde/vermelha
 * (sucesso/erro), o SQL (uma linha, truncado) e "ha X min · Yms".
 */
public class HistoryPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    private final ExecutionHistoryStore store;
    private final Consumer<Entry> openAction;
    private final DefaultListModel<Entry> model = new DefaultListModel<>();
    private final JList<Entry> list = new JList<>(model);
    private final NSearchField search = new NSearchField("Buscar no historico...");
    /** Alterna entre a lista e o estado vazio — mesma receita de {@link ConnectionsPanel#buildEmptyState}. */
    private final JPanel listCards = new JPanel(new CardLayout());
    private JLabel emptyTitle;
    private JLabel emptySub;

    private List<Entry> all = new ArrayList<>();
    private String activeConnection; // null = workspace "sem conexao" (SCRATCH)

    public HistoryPanel(ExecutionHistoryStore store, Consumer<Entry> openAction) {
        super(new BorderLayout(0, 8));
        this.store = store;
        this.openAction = openAction;
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        add(buildHeader(), BorderLayout.NORTH);
        add(buildList(), BorderLayout.CENTER);

        reload();
    }

    /**
     * Reaplica a cor de SELECAO a cada troca de tema — sem isto ficava
     * CONGELADA na paleta de quando o painel foi construido (mesmo bug
     * corrigido em {@link ConnectionsPanel}/{@link SavedQueriesPanel}, ver
     * la o javadoc completo). Guard contra {@code null}: o PRIMEIRO
     * {@code updateUI()} vem do proprio {@code super(...)} do construtor,
     * antes de {@link #list} existir.
     */
    @Override
    public void updateUI() {
        super.updateUI();
        if (list != null) {
            list.setSelectionBackground(GridTheme.SELECTION_BACKGROUND);
            list.setSelectionForeground(GridTheme.SELECTION_FOREGROUND);
        }
    }

    /**
     * Sem titulo "HISTORICO" aqui: este painel agora vive dentro de uma aba
     * da sidebar (ver {@code MainWindow#buildLeftSide}) cujo ROTULO da
     * propria aba ja diz "Historico" — repetir o nome dentro do conteudo
     * seria a MESMA duplicacao de marca ja corrigida no logo do topo da
     * coluna (revisao de UX).
     */
    private JComponent buildHeader() {
        search.onTextChange(this::applyFilter);

        JPanel header = new JPanel(new BorderLayout(0, 6));
        header.setOpaque(false);
        header.add(search, BorderLayout.CENTER);
        return header;
    }

    private JComponent buildList() {
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setFixedCellHeight(52);
        // Sem isto, JList usa o default de 8 linhas "fantasma" pra calcular a
        // altura preferida mesmo com a lista vazia/curta — na sidebar
        // unificada (Fase 3 do AI-CHAT-MASTER-PLAN.md) isso sobrava uma caixa
        // cinza vazia enorme (ver mesmo ajuste em ConnectionsPanel).
        list.setVisibleRowCount(3);
        // Mesmo cinza neutro de selecao da grade/arvore de Objetos, nao o
        // verde solido default do FlatLaf.properties (List.selectionBackground)
        // — ver o mesmo ajuste em ConnectionsPanel#buildList.
        list.setSelectionBackground(GridTheme.SELECTION_BACKGROUND);
        list.setSelectionForeground(GridTheme.SELECTION_FOREGROUND);
        list.setCellRenderer(new EntryRenderer());
        TreeHoverTracker.installOnList(list);
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    openSelected();
                }
            }

            @Override
            public void mousePressed(MouseEvent e) {
                maybeMenu(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                maybeMenu(e);
            }
        });

        JScrollPane sp = new JScrollPane(list);
        sp.setBorder(BorderFactory.createEmptyBorder());

        listCards.add(sp, "list");
        listCards.add(buildEmptyState(), "empty");
        return listCards;
    }

    /**
     * Estado vazio: nenhuma execucao registrada ainda, OU a busca nao
     * encontrou nada — mesma composicao (icone 40px reativo ao tema + titulo
     * + subtitulo) que {@link ConnectionsPanel#buildEmptyState()} usa, para
     * manter um UNICO "idioma" de estado vazio em toda a sidebar (antes esta
     * lista simplesmente ficava em branco quando vazia).
     */
    private JComponent buildEmptyState() {
        // NEmptyState (design system): ponto UNICO da receita "icone +
        // titulo + subtitulo" — antes esta era uma de 4 copias praticamente
        // identicas (achado numa auditoria pedida pelo usuario).
        NEmptyState state = new NEmptyState(IconType.HISTORY, "", "");
        emptyTitle = state.titleLabel();
        emptySub = state.subtitleLabel();
        return state;
    }

    /** Mostra a lista ou o estado vazio, com o texto certo pro caso (sem historico ainda vs. busca sem resultado). */
    private void updateEmptyState(boolean empty) {
        ((CardLayout) listCards.getLayout()).show(listCards, empty ? "empty" : "list");
        if (!empty || emptyTitle == null) {
            return;
        }
        String query = search.getText() == null ? "" : search.getText().trim();
        if (query.isEmpty()) {
            emptyTitle.setText("Nenhuma execucao ainda");
            emptySub.setText("O historico aparece aqui apos rodar uma consulta");
        } else {
            emptyTitle.setText("Nenhum resultado");
            emptySub.setText("Nada bate com \"" + query + "\"");
        }
    }

    private void maybeMenu(MouseEvent e) {
        if (!e.isPopupTrigger()) {
            return;
        }
        int idx = list.locationToIndex(e.getPoint());
        if (idx >= 0) {
            list.setSelectedIndex(idx);
        }
        Entry entry = list.getSelectedValue();
        JPopupMenu menu = new JPopupMenu();
        if (entry != null) {
            JMenuItem open = new JMenuItem("Abrir em nova aba");
            open.addActionListener(a -> openSelected());
            JMenuItem copy = new JMenuItem("Copiar SQL");
            copy.addActionListener(a -> copySql(entry));
            JMenuItem delete = new JMenuItem("Remover do historico");
            delete.addActionListener(a -> deleteSelected(entry));
            menu.add(open);
            menu.add(copy);
            menu.addSeparator();
            menu.add(delete);
            menu.addSeparator();
        }
        JMenuItem clearAll = new JMenuItem("Limpar historico desta conexao");
        clearAll.addActionListener(a -> clearForActiveConnection());
        menu.add(clearAll);
        menu.show(list, e.getX(), e.getY());
    }

    private void openSelected() {
        Entry entry = list.getSelectedValue();
        if (entry != null) {
            openAction.accept(entry);
        }
    }

    private void copySql(Entry entry) {
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(entry.sql()), null);
    }

    private void deleteSelected(Entry entry) {
        try {
            store.delete(entry.id());
            reload();
        } catch (IOException ex) {
            reportError("remover", ex);
        }
    }

    private void clearForActiveConnection() {
        String label = activeConnection == null ? "sem conexao" : activeConnection;
        // WARNING_MESSAGE: mesmo icone usado nas demais confirmacoes de
        // acao irreversivel do app (ver ConnectionsPanel#onDelete) —
        // inconsistencia encontrada numa auditoria pedida pelo usuario.
        int ok = JOptionPane.showConfirmDialog(DialogUtil.owner(this),
                "Limpar todo o historico de execucoes de \"" + label + "\"? Esta acao nao pode ser desfeita.",
                "Limpar historico", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (ok != JOptionPane.YES_OPTION) {
            return;
        }
        try {
            store.clearForConnection(activeConnection);
            reload();
        } catch (IOException ex) {
            reportError("limpar", ex);
        }
    }

    private void reportError(String action, IOException ex) {
        AppLogger.warning("Falha ao " + action + " historico", ex);
        JOptionPane.showMessageDialog(DialogUtil.owner(this),
                "Nao foi possivel " + action + " o historico:\n" + ex.getMessage(),
                "Historico", JOptionPane.ERROR_MESSAGE);
    }

    /** Recarrega do disco (chamado apos cada execucao registrada). */
    public void reload() {
        try {
            all = store.loadAll();
        } catch (IOException ex) {
            all = new ArrayList<>();
            AppLogger.warning("Falha ao carregar historico de execucoes", ex);
        }
        applyFilter();
    }

    /** Muda a conexao usada para filtrar a lista (ver SavedQueriesPanel#setActiveConnection). */
    public void setActiveConnection(String connectionName) {
        this.activeConnection = connectionName;
        applyFilter();
    }

    private void applyFilter() {
        String f = search.getText() == null ? "" : search.getText().trim().toLowerCase(Locale.ROOT);
        List<Entry> filtered = new ArrayList<>();
        for (Entry e : all) {
            if (!Objects.equals(e.connectionName(), activeConnection)) {
                continue;
            }
            if (!f.isEmpty() && !e.sql().toLowerCase(Locale.ROOT).contains(f)) {
                continue;
            }
            filtered.add(e);
        }
        filtered.sort(Comparator.comparingLong(Entry::executedAt).reversed());
        model.clear();
        for (Entry e : filtered) {
            model.addElement(e);
        }
        updateEmptyState(filtered.isEmpty());
    }

    /** Cartao de cada execucao: bolinha de status + SQL (1 linha) + "ha X · Yms". */
    private final class EntryRenderer extends DefaultListCellRenderer {
        private static final long serialVersionUID = 1L;

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            setHorizontalAlignment(SwingConstants.LEFT);
            setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));
            // Hover (linha sob o mouse, sem selecionar) — mesmo destaque
            // suave de ConnectionsPanel#ConnectionRenderer/arvore de
            // Objetos/grade de resultados (GridTheme.HOVER_BACKGROUND);
            // faltava aqui apesar do painel ja instalar TreeHoverTracker
            // (so usava a posicao do mouse pro CURSOR, nunca pra pintar a
            // linha) — inconsistencia encontrada numa auditoria pedida pelo
            // usuario. Selecao sempre tem prioridade visual.
            boolean hovered = !isSelected && index == TreeHoverTracker.hoverRow(list);
            if (hovered) {
                setOpaque(true);
                setBackground(GridTheme.HOVER_BACKGROUND);
            }
            if (value instanceof Entry e) {
                // Bolinha de status: icone de verdade (mesma receita do
                // ConnectionStatusCard), nao mais o caractere HTML "&#9679;"
                // — o glifo nao existe em algumas fontes e aparecia como um
                // quadrado vazio ("tofu") no lugar da bolinha, relatado pelo
                // usuario com captura de tela. Mesma familia de bug ja
                // corrigida em outros menus (ver MainWindow#presetItem).
                Color dotColor = e.success() ? GridTheme.COLOR_LOGIC_TRUE : GridTheme.COLOR_LOGIC_FALSE;
                setIcon(Icons.get(IconType.STATUS_DOT, 8, dotColor));
                setIconTextGap(6);
                setVerticalTextPosition(SwingConstants.TOP);
                setHorizontalTextPosition(SwingConstants.RIGHT);
                // Sub-texto: cor de selecao (a linha inteira agora usa o MESMO
                // cinza neutro da grade/arvore, nao mais o verde solido do L&F —
                // ver #buildList) OU o cinza mudo de sempre fora da selecao.
                String subColor = HtmlText.hex(isSelected ? GridTheme.SELECTION_FOREGROUND : GridTheme.MUTED_TEXT);
                String family = getFont().getFamily();
                String preview = HtmlText.escape(oneLine(e.sql(), 70));
                String meta = RelativeTime.relative(e.executedAt()) + "  ·  " + e.durationMs() + "ms"
                        + (e.schema() != null ? "  ·  " + HtmlText.escape(e.schema()) : "");
                setText("<html><div style='font-family:" + family + ";line-height:1.5'>"
                        + "<b>" + preview + "</b><br>"
                        + "<span style='color:" + subColor + ";font-size:10px'>" + meta + "</span></div></html>");
                String tooltip = RelativeTime.absolute(e.executedAt());
                if (e.resultSummary() != null && !e.resultSummary().isBlank()) {
                    tooltip += "\n" + e.resultSummary();
                }
                setToolTipText("<html>" + HtmlText.escape(tooltip).replace("\n", "<br>") + "</html>");
            }
            return this;
        }

        private static String oneLine(String sql, int maxLen) {
            String flat = sql.replaceAll("\\s+", " ").trim();
            if (flat.length() > maxLen) {
                flat = flat.substring(0, maxLen - 1) + "…";
            }
            return flat;
        }
    }
}
