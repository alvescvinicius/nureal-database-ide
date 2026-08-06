package com.nureal.ide.ui;
import com.nureal.ide.compartilhado.designsystem.Buttons;
import com.nureal.ide.compartilhado.designsystem.IconType;
import com.nureal.ide.compartilhado.designsystem.Typography;
import com.nureal.ide.compartilhado.designsystem.GridTheme;
import com.nureal.ide.compartilhado.designsystem.NEmptyState;

import com.nureal.ide.core.log.AppLogger;
import com.nureal.ide.modulos.historico.infraestrutura.SavedQueryStore;
import com.nureal.ide.modulos.historico.infraestrutura.SavedQueryStore.Query;
import com.nureal.ide.compartilhado.designsystem.NSearchField;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
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
import java.awt.Component;
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
 * Painel lateral com as queries SALVAS (deliberadas, com titulo) — mesmo
 * estilo visual/estrutural do {@link ConnectionsPanel} (lista em cartoes,
 * busca no topo, menu de contexto).
 *
 * Filtra SEMPRE pela conexao ATIVA (ver {@link #setActiveConnection}): a
 * lista mostra so as queries salvas naquela conexao, nao todas de uma vez —
 * decisao tomada de proposito para nao misturar queries de bancos
 * diferentes na mesma busca. Trocar de workspace atualiza o filtro
 * automaticamente (ver {@code MainWindow#activateWorkspace}).
 */
public class SavedQueriesPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    private final SavedQueryStore store;
    private final Consumer<Query> openAction;
    private final DefaultListModel<Query> model = new DefaultListModel<>();
    private final JList<Query> list = new JList<>(model);
    private final NSearchField search = new NSearchField("Buscar por titulo ou SQL...");
    /** Alterna entre a lista e o estado vazio — mesma receita de {@link ConnectionsPanel#buildEmptyState}. */
    private final JPanel listCards = new JPanel(new CardLayout());
    private JLabel emptyTitle;
    private JLabel emptySub;

    private List<Query> all = new ArrayList<>();
    private String activeConnection; // null = workspace "sem conexao" (SCRATCH)

    public SavedQueriesPanel(SavedQueryStore store, Consumer<Query> openAction) {
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
     * corrigido em {@link ConnectionsPanel}/{@link HistoryPanel}, ver la o
     * javadoc completo). Guard contra {@code null}: o PRIMEIRO
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
     * Sem titulo "QUERIES SALVAS" aqui: este painel agora vive dentro de uma
     * aba da sidebar (ver {@code MainWindow#buildLeftSide}) cujo ROTULO da
     * propria aba ja diz "Salvas" — repetir o nome dentro do conteudo seria
     * a MESMA duplicacao de marca ja corrigida no logo do topo da coluna
     * (revisao de UX).
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
        list.setFixedCellHeight(48);
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
        list.setCellRenderer(new QueryRenderer());
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
     * Estado vazio: nenhuma query salva ainda, OU a busca nao encontrou nada
     * — mesma composicao (icone 40px reativo ao tema + titulo + subtitulo)
     * que {@link ConnectionsPanel#buildEmptyState()} usa, para manter um
     * UNICO "idioma" de estado vazio em toda a sidebar (antes esta lista
     * simplesmente ficava em branco quando vazia).
     */
    private JComponent buildEmptyState() {
        // NEmptyState (design system): ponto UNICO da receita "icone +
        // titulo + subtitulo" — antes esta era uma de 4 copias praticamente
        // identicas (achado numa auditoria pedida pelo usuario).
        NEmptyState state = new NEmptyState(IconType.SAVE, "", "");
        emptyTitle = state.titleLabel();
        emptySub = state.subtitleLabel();
        return state;
    }

    /** Mostra a lista ou o estado vazio, com o texto certo pro caso (sem query salva ainda vs. busca sem resultado). */
    private void updateEmptyState(boolean empty) {
        ((CardLayout) listCards.getLayout()).show(listCards, empty ? "empty" : "list");
        if (!empty || emptyTitle == null) {
            return;
        }
        String query = search.getText() == null ? "" : search.getText().trim();
        if (query.isEmpty()) {
            emptyTitle.setText("Nenhuma query salva");
            emptySub.setText("Salve uma consulta para reusa-la depois");
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
        Query q = list.getSelectedValue();
        if (q == null) {
            return;
        }
        JPopupMenu menu = new JPopupMenu();
        JMenuItem open = new JMenuItem("Abrir");
        open.addActionListener(a -> openSelected());
        JMenuItem favorite = new JMenuItem(q.favorite() ? "Remover dos favoritos" : "Favoritar");
        favorite.addActionListener(a -> toggleFavorite(q));
        JMenuItem rename = new JMenuItem("Renomear...");
        rename.addActionListener(a -> renameSelected(q));
        JMenuItem delete = new JMenuItem("Excluir");
        delete.addActionListener(a -> deleteSelected(q));
        menu.add(open);
        menu.addSeparator();
        menu.add(favorite);
        menu.add(rename);
        menu.addSeparator();
        menu.add(delete);
        menu.show(list, e.getX(), e.getY());
    }

    private void openSelected() {
        Query q = list.getSelectedValue();
        if (q != null) {
            openAction.accept(q);
        }
    }

    private void toggleFavorite(Query q) {
        try {
            store.setFavorite(q.id(), !q.favorite());
            reload();
        } catch (IOException ex) {
            reportError("favoritar", ex);
        }
    }

    private void renameSelected(Query q) {
        // Centraliza na JANELA (nao neste painel, que fica na lateral) — ver DialogUtil.
        String name = JOptionPane.showInputDialog(DialogUtil.owner(this), "Novo titulo:", q.title());
        if (name == null || name.trim().isEmpty()) {
            return;
        }
        try {
            store.rename(q.id(), name.trim());
            reload();
        } catch (IOException ex) {
            reportError("renomear", ex);
        }
    }

    private void deleteSelected(Query q) {
        // WARNING_MESSAGE: mesmo icone usado nas demais confirmacoes de
        // acao irreversivel do app (ver ConnectionsPanel#onDelete) —
        // inconsistencia encontrada numa auditoria pedida pelo usuario.
        int ok = JOptionPane.showConfirmDialog(DialogUtil.owner(this),
                "Excluir a query \"" + q.title() + "\"? Esta acao nao pode ser desfeita.",
                "Excluir query", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (ok != JOptionPane.YES_OPTION) {
            return;
        }
        try {
            store.delete(q.id());
            reload();
        } catch (IOException ex) {
            reportError("excluir", ex);
        }
    }

    private void reportError(String action, IOException ex) {
        AppLogger.warning("Falha ao " + action + " query salva", ex);
        JOptionPane.showMessageDialog(DialogUtil.owner(this),
                "Nao foi possivel " + action + " a query:\n" + ex.getMessage(),
                "Queries salvas", JOptionPane.ERROR_MESSAGE);
    }

    /** Recarrega do disco (chamado apos salvar/excluir/renomear, inclusive de fora deste painel). */
    public void reload() {
        try {
            all = store.loadAll();
        } catch (IOException ex) {
            all = new ArrayList<>();
            AppLogger.warning("Falha ao carregar queries salvas", ex);
            JOptionPane.showMessageDialog(DialogUtil.owner(this),
                    "Nao foi possivel ler as queries salvas:\n" + ex.getMessage(),
                    "Queries salvas", JOptionPane.WARNING_MESSAGE);
        }
        applyFilter();
    }

    /**
     * Muda a conexao usada para filtrar a lista (chamado ao trocar de
     * workspace/desconectar — ver {@code MainWindow#activateWorkspace}).
     * {@code connectionName}: {@code null} para o workspace "sem conexao".
     */
    public void setActiveConnection(String connectionName) {
        this.activeConnection = connectionName;
        applyFilter();
    }

    private void applyFilter() {
        String f = search.getText() == null ? "" : search.getText().trim().toLowerCase(Locale.ROOT);
        List<Query> filtered = new ArrayList<>();
        for (Query q : all) {
            if (!Objects.equals(q.connectionName(), activeConnection)) {
                continue;
            }
            if (!f.isEmpty()
                    && !q.title().toLowerCase(Locale.ROOT).contains(f)
                    && !q.sql().toLowerCase(Locale.ROOT).contains(f)) {
                continue;
            }
            filtered.add(q);
        }
        filtered.sort(Comparator.comparingLong(Query::updatedAt).reversed());
        model.clear();
        for (Query q : filtered) {
            model.addElement(q);
        }
        updateEmptyState(filtered.isEmpty());
    }

    /** Cartao de cada query: estrela (se favorita) + titulo em negrito + "atualizado ha X". */
    private final class QueryRenderer extends DefaultListCellRenderer {
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
            // faltava aqui apesar do painel ja instalar TreeHoverTracker —
            // inconsistencia encontrada numa auditoria pedida pelo usuario.
            // Selecao sempre tem prioridade visual.
            boolean hovered = !isSelected && index == TreeHoverTracker.hoverRow(list);
            if (hovered) {
                setOpaque(true);
                setBackground(GridTheme.HOVER_BACKGROUND);
            }
            if (value instanceof Query q) {
                // Cor de selecao ja e o cinza neutro (ver #buildList), nao mais
                // o verde solido do L&F — sub-texto acompanha a MESMA cor de
                // selecao/mudo reativa ao tema, nao dois literais proprios.
                String subColor = HtmlText.hex(isSelected ? GridTheme.SELECTION_FOREGROUND : GridTheme.MUTED_TEXT);
                String family = getFont().getFamily();
                String star = q.favorite() ? "★ " : "";
                setText("<html><div style='font-family:" + family + ";line-height:1.5'>"
                        + star + "<b>" + HtmlText.escape(q.title()) + "</b><br>"
                        + "<span style='color:" + subColor + ";font-size:10px'>"
                        + HtmlText.escape(RelativeTime.relative(q.updatedAt())) + "</span></div></html>");
                setToolTipText("Atualizado em " + RelativeTime.absolute(q.updatedAt()));
            }
            return this;
        }
    }
}
