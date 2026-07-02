package com.nureal.ide.ui;

import com.nureal.ide.core.log.AppLogger;
import com.nureal.ide.core.queries.SavedQueryStore;
import com.nureal.ide.core.queries.SavedQueryStore.Query;

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
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
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
    private final JTextField search = new JTextField();

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

    private JComponent buildHeader() {
        JLabel title = new JLabel("QUERIES SALVAS");
        title.putClientProperty("FlatLaf.styleClass", "small");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 11f));
        title.setForeground(new Color(0x6B7280));

        search.putClientProperty("JTextField.placeholderText", "Buscar por titulo ou SQL...");
        search.putClientProperty("JTextField.showClearButton", true);
        search.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                applyFilter();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                applyFilter();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                applyFilter();
            }
        });

        JPanel header = new JPanel(new BorderLayout(0, 6));
        header.setOpaque(false);
        header.add(title, BorderLayout.NORTH);
        header.add(search, BorderLayout.SOUTH);
        return header;
    }

    private JComponent buildList() {
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setFixedCellHeight(48);
        list.setCellRenderer(new QueryRenderer());
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
        return sp;
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
        int ok = JOptionPane.showConfirmDialog(DialogUtil.owner(this),
                "Excluir a query \"" + q.title() + "\"? Esta acao nao pode ser desfeita.",
                "Excluir query", JOptionPane.YES_NO_OPTION);
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
    }

    private static String relativeTime(long epochMillis) {
        if (epochMillis <= 0) {
            return "";
        }
        long diffSec = Math.max(0, (System.currentTimeMillis() - epochMillis) / 1000);
        if (diffSec < 60) {
            return "agora";
        }
        long min = diffSec / 60;
        if (min < 60) {
            return "ha " + min + " min";
        }
        long hours = min / 60;
        if (hours < 24) {
            return "ha " + hours + "h";
        }
        long days = hours / 24;
        if (days < 7) {
            return "ha " + days + " dia(s)";
        }
        return absoluteTime(epochMillis);
    }

    private static String absoluteTime(long epochMillis) {
        return DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")
                .format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()));
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
            if (value instanceof Query q) {
                String subColor = isSelected ? "#E5F5EC" : "#6B7280";
                String family = getFont().getFamily();
                String star = q.favorite() ? "★ " : "";
                setText("<html><div style='font-family:" + family + ";line-height:1.5'>"
                        + star + "<b>" + escape(q.title()) + "</b><br>"
                        + "<span style='color:" + subColor + ";font-size:10px'>"
                        + escape(relativeTime(q.updatedAt())) + "</span></div></html>");
                setToolTipText("Atualizado em " + absoluteTime(q.updatedAt()));
            }
            return this;
        }

        private static String escape(String s) {
            return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        }
    }
}
