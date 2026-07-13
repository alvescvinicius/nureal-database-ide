package com.nureal.ide.ui;

import com.nureal.ide.core.history.ExecutionHistoryStore;
import com.nureal.ide.core.history.ExecutionHistoryStore.Entry;
import com.nureal.ide.core.log.AppLogger;

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
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
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
    private final JTextField search = new JTextField();

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

    private JComponent buildHeader() {
        // Ver Typography#sectionHeader: MESMA receita de "OBJETOS"/"CONEXOES"/
        // "QUERIES SALVAS" — ponto unico, sem copia colada.
        JLabel title = Typography.sectionHeader("HISTORICO");

        search.putClientProperty("JTextField.placeholderText", "Buscar no historico...");
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
        list.setFixedCellHeight(52);
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
        int ok = JOptionPane.showConfirmDialog(DialogUtil.owner(this),
                "Limpar todo o historico de execucoes de \"" + label + "\"? Esta acao nao pode ser desfeita.",
                "Limpar historico", JOptionPane.YES_NO_OPTION);
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

    /** Cartao de cada execucao: bolinha de status + SQL (1 linha) + "ha X · Yms". */
    private final class EntryRenderer extends DefaultListCellRenderer {
        private static final long serialVersionUID = 1L;

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            setHorizontalAlignment(SwingConstants.LEFT);
            setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));
            if (value instanceof Entry e) {
                // Mesmo par verde/vermelho "logico" da grade (BooleanCellRenderer)
                // e do status de conexao no rodape — reativo ao tema, nao mais um
                // par de literais proprios so desta lista.
                String dot = hex(e.success() ? GridTheme.COLOR_LOGIC_TRUE : GridTheme.COLOR_LOGIC_FALSE);
                // Sub-texto: cor de selecao (a linha inteira agora usa o MESMO
                // cinza neutro da grade/arvore, nao mais o verde solido do L&F —
                // ver #buildList) OU o cinza mudo de sempre fora da selecao.
                String subColor = hex(isSelected ? GridTheme.SELECTION_FOREGROUND : GridTheme.MUTED_TEXT);
                String family = getFont().getFamily();
                String preview = escape(oneLine(e.sql(), 70));
                String meta = relativeTime(e.executedAt()) + "  ·  " + e.durationMs() + "ms"
                        + (e.schema() != null ? "  ·  " + escape(e.schema()) : "");
                setText("<html><div style='font-family:" + family + ";line-height:1.5'>"
                        + "<span style='color:" + dot + "'>&#9679;</span> "
                        + "<b>" + preview + "</b><br>"
                        + "<span style='color:" + subColor + ";font-size:10px'>" + meta + "</span></div></html>");
                String tooltip = absoluteTime(e.executedAt());
                if (e.resultSummary() != null && !e.resultSummary().isBlank()) {
                    tooltip += "\n" + e.resultSummary();
                }
                setToolTipText("<html>" + escape(tooltip).replace("\n", "<br>") + "</html>");
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

        private static String escape(String s) {
            return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        }

        /** Mesma conversao Color->hex de {@code ObjectTreeCellRenderer#columnHtml} — HTML embutido so aceita string. */
        private static String hex(Color c) {
            return String.format("#%06X", c.getRGB() & 0xFFFFFF);
        }
    }
}
