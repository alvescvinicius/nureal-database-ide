package com.nureal.ide.ui;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.nureal.ide.core.autocomplete.SqlCompletionProvider;
import com.nureal.ide.core.connection.ConnectionManager;
import com.nureal.ide.core.connection.ConnectionProfile;
import com.nureal.ide.core.connection.ConnectionStore;
import com.nureal.ide.core.dialect.DatabaseDialect;
import com.nureal.ide.core.dialect.MySqlDialect;
import com.nureal.ide.core.export.ExcelExporter;
import com.nureal.ide.core.metadata.MetadataCache;
import com.nureal.ide.core.metadata.MetadataService;
import com.nureal.ide.core.metadata.model.ColumnDetail;
import com.nureal.ide.core.metadata.model.ColumnInfo;
import com.nureal.ide.core.metadata.model.ForeignKeyInfo;
import com.nureal.ide.core.metadata.model.IndexInfo;
import com.nureal.ide.core.metadata.model.SchemaInfo;
import com.nureal.ide.core.metadata.model.TableDetails;
import com.nureal.ide.core.metadata.model.TableInfo;
import com.nureal.ide.core.session.SessionStore;
import com.nureal.ide.core.sql.SqlStatementSplitter;

import javax.swing.AbstractListModel;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JList;
import javax.swing.ListCellRenderer;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Vector;
import java.util.function.BiConsumer;

/**
 * Janela principal no estilo de uma IDE moderna (FlatLaf): top bar com acao de
 * executar e tema, conexoes e objetos a esquerda, editor SQL em abas no centro e
 * resultados em abas abaixo (uma aba por statement), com exportacao para Excel.
 */
public class MainWindow extends JFrame {

    private static final long serialVersionUID = 1L;
	private static final Color ACCENT = new Color(0x059669);
    private static final Color MUTED = new Color(0x6B7280);

    private static final int PAGE_SIZE = 200;

    private final DatabaseDialect dialect = new MySqlDialect();
    private final ConnectionManager connectionManager = new ConnectionManager(dialect);
    private final MetadataService metadataService = new MetadataService(dialect);
    private final MetadataCache metadataCache = new MetadataCache();
    private final SqlCompletionProvider completionProvider =
            new SqlCompletionProvider(dialect.keywords());
    private final ConnectionStore connectionStore = new ConnectionStore();
    private final SessionStore sessionStore = new SessionStore();
    private Timer autosaveTimer;

    private JTabbedPane editorTabs;
    private JTabbedPane resultTabs;
    private JPanel resultsCards;
    private JTree objectTree;
    private ConnectionsPanel connectionsPanel;
    private JTextField objectSearch;
    private SchemaInfo currentSchema;
    private JLabel statusBar;
    private JButton runButton;
    private JButton themeButton;

    private int queryCounter = 0;
    private boolean dark = false;
    private List<QueryResult> lastResults = new ArrayList<>();
    private final List<ResultCursor> openCursors = new ArrayList<>();

    public MainWindow() {
        super("Nureal Database IDE");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1280, 800);
        setLocationRelativeTo(null);
        buildUi();
        // Salva a sessao ao fechar (alem do autosave continuo durante a digitacao).
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                saveSession();
            }
        });
    }

    private void buildUi() {
        setLayout(new BorderLayout());

        JSplitPane center = new JSplitPane(
                JSplitPane.VERTICAL_SPLIT, buildEditorArea(), buildResultsArea());
        center.setResizeWeight(0.62);
        center.setBorder(BorderFactory.createEmptyBorder());

        JSplitPane main = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT, buildLeftSide(), center);
        main.setResizeWeight(0.22);
        main.setBorder(BorderFactory.createEmptyBorder());
        add(main, BorderLayout.CENTER);

        add(buildFooter(), BorderLayout.SOUTH);
    }

    // ---------- Barras ----------

    private JComponent buildToolbar() {
        runButton = new JButton("Executar");
        runButton.setIcon(Icons.play(13, Color.WHITE));
        runButton.setToolTipText("Executar (Ctrl+Enter ou F5)");
        runButton.setEnabled(false);
        runButton.addActionListener(e -> onRun());
        styleRunButton();

        JButton formatButton = new JButton("Formatar");
        formatButton.setToolTipText("Formatar SQL (Ctrl+Shift+F)");
        formatButton.addActionListener(e -> {
            SqlEditorPane editor = currentEditor();
            if (editor != null) {
                editor.formatText();
            }
        });

        themeButton = new JButton(Icons.moon(16, MUTED));
        themeButton.setToolTipText("Alternar tema claro/escuro");
        themeButton.addActionListener(e -> toggleTheme());

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        left.setOpaque(false);
        left.add(runButton);
        left.add(formatButton);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setOpaque(false);
        right.add(themeButton);

        JPanel bar = new JPanel(new BorderLayout());
        bar.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
        bar.add(left, BorderLayout.WEST);
        bar.add(right, BorderLayout.EAST);
        return bar;
    }

    private void styleRunButton() {
        runButton.setBackground(ACCENT);
        runButton.setForeground(Color.WHITE);
    }

    private JComponent buildFooter() {
        statusBar = new JLabel(" Pronto - conexoes salvas em: " + connectionStore.location());
        statusBar.setForeground(MUTED);

        JLabel brand = new JLabel("Nureal");
        brand.setFont(brand.getFont().deriveFont(Font.BOLD));
        brand.setForeground(ACCENT);

        JPanel footer = new JPanel(new BorderLayout());
        footer.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));
        footer.add(statusBar, BorderLayout.WEST);
        footer.add(brand, BorderLayout.EAST);
        return footer;
    }

    // ---------- Lado esquerdo ----------

    private JComponent buildLeftSide() {
        connectionsPanel = new ConnectionsPanel(connectionStore, this::connectTo);
        JSplitPane split = new JSplitPane(
                JSplitPane.VERTICAL_SPLIT, connectionsPanel, buildObjectBrowser());
        split.setResizeWeight(0.5);
        split.setBorder(BorderFactory.createEmptyBorder());
        split.setPreferredSize(new Dimension(280, 100));
        return split;
    }

    private JComponent buildObjectBrowser() {
        objectTree = new JTree(new DefaultTreeModel(
                new DefaultMutableTreeNode("Sem conexao")));
        objectTree.setRootVisible(true);
        objectTree.setShowsRootHandles(true);
        objectTree.setRowHeight(22);
        objectTree.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 4));
        objectTree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    openSelectedObjectProperties();
                }
            }
        });

        JScrollPane sp = new JScrollPane(objectTree);
        sp.setBorder(BorderFactory.createEmptyBorder());

        objectSearch = new JTextField();
        objectSearch.putClientProperty("JTextField.placeholderText", "Buscar objeto...");
        objectSearch.putClientProperty("JTextField.showClearButton", true);
        objectSearch.setEnabled(false);
        objectSearch.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { applyObjectFilter(); }
            @Override public void removeUpdate(DocumentEvent e) { applyObjectFilter(); }
            @Override public void changedUpdate(DocumentEvent e) { applyObjectFilter(); }
        });

        JPanel top = new JPanel(new BorderLayout(0, 8));
        top.setOpaque(false);
        top.add(sectionHeader("OBJETOS"), BorderLayout.NORTH);
        top.add(objectSearch, BorderLayout.SOUTH);

        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        panel.add(top, BorderLayout.NORTH);
        panel.add(sp, BorderLayout.CENTER);
        return panel;
    }

    // ---------- Editor (abas) ----------

    private JComponent buildEditorArea() {
        editorTabs = new JTabbedPane();
        editorTabs.putClientProperty("JTabbedPane.tabClosable", true);
        editorTabs.putClientProperty("JTabbedPane.tabCloseCallback",
                (BiConsumer<JTabbedPane, Integer>) (pane, index) -> closeQueryTab(index));
        editorTabs.addChangeListener(e -> scheduleSave());

        JButton plus = new JButton("+");
        plus.setToolTipText("Nova query");
        plus.addActionListener(e -> addQueryTab());
        editorTabs.putClientProperty("JTabbedPane.trailingComponent", plus);

        // Restaura a sessao salva (abas com SQL); se nao houver, abre uma aba vazia.
        restoreSession();

        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(0, 8, 4, 8));
        panel.add(buildToolbar(), BorderLayout.NORTH);
        panel.add(editorTabs, BorderLayout.CENTER);
        return panel;
    }

    private void addQueryTab() {
        addQueryTab("SQL Query " + (++queryCounter), "");
    }

    private void addQueryTab(String title, String sql) {
        SqlEditorPane pane = new SqlEditorPane(completionProvider, this::onRun);
        pane.textArea().setText(sql);
        pane.textArea().setCaretPosition(0);
        pane.textArea().getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { scheduleSave(); }
            @Override public void removeUpdate(DocumentEvent e) { scheduleSave(); }
            @Override public void changedUpdate(DocumentEvent e) { scheduleSave(); }
        });
        editorTabs.addTab(title, pane);
        editorTabs.setSelectedComponent(pane);
        scheduleSave();
    }

    private void closeQueryTab(int index) {
        if (editorTabs.getTabCount() > 1) {
            editorTabs.removeTabAt(index);
            scheduleSave();
        }
    }

    // ---------- Persistencia da sessao (nunca perder trabalho) ----------

    /** Restaura as abas/SQLs salvos; se nao houver, abre uma aba vazia. */
    private void restoreSession() {
        SessionStore.Session session = null;
        try {
            session = sessionStore.load();
        } catch (Exception ex) {
            session = null;
        }
        if (session == null || session.tabs().isEmpty()) {
            addQueryTab();
            return;
        }
        for (SessionStore.Tab t : session.tabs()) {
            String title = (t.title() == null || t.title().isBlank())
                    ? "SQL Query " + (editorTabs.getTabCount() + 1) : t.title();
            addQueryTab(title, t.sql());
        }
        queryCounter = editorTabs.getTabCount();
        int sel = session.selectedIndex();
        if (sel >= 0 && sel < editorTabs.getTabCount()) {
            editorTabs.setSelectedIndex(sel);
        }
    }

    /** Agenda um salvamento (debounce) ~1s apos a ultima alteracao. */
    private void scheduleSave() {
        if (autosaveTimer == null) {
            autosaveTimer = new Timer(1000, e -> saveSession());
            autosaveTimer.setRepeats(false);
        }
        autosaveTimer.restart();
    }

    /** Grava agora as abas e seus SQLs no disco. */
    private void saveSession() {
        if (editorTabs == null) {
            return;
        }
        List<SessionStore.Tab> tabs = new ArrayList<>();
        for (int i = 0; i < editorTabs.getTabCount(); i++) {
            Component c = editorTabs.getComponentAt(i);
            if (c instanceof SqlEditorPane sep) {
                tabs.add(new SessionStore.Tab(
                        editorTabs.getTitleAt(i), sep.textArea().getText()));
            }
        }
        int sel = Math.max(editorTabs.getSelectedIndex(), 0);
        try {
            sessionStore.save(new SessionStore.Session(tabs, sel));
        } catch (Exception ex) {
            if (statusBar != null) {
                statusBar.setText(" Aviso: nao foi possivel salvar a sessao: "
                        + ex.getMessage());
            }
        }
    }

    private SqlEditorPane currentEditor() {
        Component c = editorTabs.getSelectedComponent();
        return (c instanceof SqlEditorPane sep) ? sep : null;
    }

    // ---------- Resultados ----------

    private JComponent buildResultsArea() {
        resultTabs = new JTabbedPane();
        resultTabs.putClientProperty("JTabbedPane.tabType", "card");
        resultTabs.putClientProperty("JTabbedPane.minimumTabWidth", 96);
        resultTabs.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                maybeShowTabMenu(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                maybeShowTabMenu(e);
            }
        });

        JPanel tabsPanel = new JPanel(new BorderLayout());
        tabsPanel.add(resultTabs, BorderLayout.CENTER);

        resultsCards = new JPanel(new CardLayout());
        resultsCards.add(buildEmptyState(), "empty");
        resultsCards.add(tabsPanel, "tabs");

        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(4, 8, 8, 8));
        panel.add(sectionHeader("RESULTADOS"), BorderLayout.NORTH);
        panel.add(resultsCards, BorderLayout.CENTER);
        return panel;
    }

    private JComponent buildEmptyState() {
        JLabel icon = new JLabel(Icons.grid(46, new Color(0xCBD5E1)));
        icon.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel title = new JLabel("Execute uma consulta para ver os resultados");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 14f));
        title.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel sub = new JLabel("Os resultados da consulta aparecerao aqui");
        sub.setForeground(MUTED);
        sub.setAlignmentX(Component.CENTER_ALIGNMENT);

        JPanel box = new JPanel();
        box.setOpaque(false);
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        box.add(icon);
        box.add(Box.createVerticalStrut(12));
        box.add(title);
        box.add(Box.createVerticalStrut(4));
        box.add(sub);

        JPanel center = new JPanel(new GridBagLayout());
        center.add(box);
        return center;
    }

    private void showEmptyState() {
        ((CardLayout) resultsCards.getLayout()).show(resultsCards, "empty");
    }

    private void showResultsCard() {
        ((CardLayout) resultsCards.getLayout()).show(resultsCards, "tabs");
    }

    private JLabel sectionHeader(String text) {
        JLabel label = new JLabel(text);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 11f));
        label.setForeground(MUTED);
        return label;
    }

    // ---------- Tema ----------

    private void toggleTheme() {
        dark = !dark;
        if (dark) {
            FlatDarkLaf.setup();
        } else {
            FlatLightLaf.setup();
        }
        FlatLaf.updateUI();
        themeButton.setIcon(dark ? Icons.sun(16, MUTED) : Icons.moon(16, MUTED));
        styleRunButton();
    }

    // ---------- Acoes ----------

    private void connectTo(ConnectionProfile profile) {
        ConnectionProfile effective = profile;
        if (profile.needsPasswordPrompt()) {
            String pw = ConnectionDialog.promptPassword(this, profile);
            if (pw == null) {
                return;
            }
            effective = profile.withPassword(pw);
        }
        final ConnectionProfile target = effective;
        statusBar.setText(" Conectando a " + target.host() + "...");

        new SwingWorker<SchemaInfo, Void>() {
            @Override
            protected SchemaInfo doInBackground() throws Exception {
                connectionManager.open(target);
                Connection conn = connectionManager.getConnection();
                return metadataService.loadSchema(conn, target.schema());
            }

            @Override
            protected void done() {
                try {
                    SchemaInfo schema = get();
                    metadataCache.set(schema);
                    completionProvider.refresh(schema);
                    populateTree(schema);
                    runButton.setEnabled(true);
                    connectionsPanel.setConnected(target);
                    setTitle("Nureal Database IDE - " + target.name());
                    statusBar.setText(" Conectado: " + target.label()
                            + "  (" + schema.tables().size() + " tabelas)");
                } catch (Exception ex) {
                    showError("Falha ao conectar", ex);
                    statusBar.setText(" Desconectado");
                }
            }
        }.execute();
    }

    private void onRun() {
        if (!connectionManager.isConnected()) {
            statusBar.setText(" Conecte-se a uma base antes de executar.");
            return;
        }
        SqlEditorPane editor = currentEditor();
        if (editor == null) {
            return;
        }
        final List<String> statements = SqlStatementSplitter.split(editor.currentSql());
        if (statements.isEmpty()) {
            return;
        }
        closeOpenCursors();
        runButton.setEnabled(false);
        statusBar.setText(" Executando " + statements.size() + " instrucao(oes)...");

        new SwingWorker<List<QueryResult>, Void>() {
            @Override
            protected List<QueryResult> doInBackground() {
                List<QueryResult> results = new ArrayList<>();
                Connection conn = connectionManager.getConnection();
                for (int i = 0; i < statements.size(); i++) {
                    String sql = statements.get(i);
                    int n = i + 1;
                    long t0 = System.nanoTime();
                    Statement st = null;
                    try {
                        st = conn.createStatement();
                        boolean hasResultSet = st.execute(sql);
                        long elapsed = (System.nanoTime() - t0) / 1_000_000L;
                        if (hasResultSet) {
                            ResultSet rs = st.getResultSet();
                            ResultTableModel model = createModel(rs);
                            int read = appendPage(model, rs, PAGE_SIZE);
                            boolean hasMore = read == PAGE_SIZE;
                            ResultCursor cursor = null;
                            if (hasMore) {
                                cursor = new ResultCursor(st, rs);
                            } else {
                                rs.close();
                                st.close();
                            }
                            results.add(QueryResult.grid(
                                    "Resultado " + n, sql, model, elapsed, cursor));
                        } else {
                            int updated = st.getUpdateCount();
                            st.close();
                            results.add(QueryResult.message("Comando " + n, sql,
                                    updated + " linha(s) afetada(s)", false, elapsed));
                        }
                    } catch (SQLException ex) {
                        if (st != null) {
                            try {
                                st.close();
                            } catch (SQLException ignore) {
                                // ignora
                            }
                        }
                        long elapsed = (System.nanoTime() - t0) / 1_000_000L;
                        results.add(QueryResult.message(
                                "Erro " + n, sql, "Erro: " + ex.getMessage(), true, elapsed));
                        break;
                    }
                }
                return results;
            }

            @Override
            protected void done() {
                try {
                    showResults(get());
                } catch (Exception ex) {
                    showError("Erro ao executar SQL", ex);
                    statusBar.setText(" Erro na execucao");
                } finally {
                    runButton.setEnabled(true);
                }
            }
        }.execute();
    }

    private void showResults(List<QueryResult> results) {
        this.lastResults = results;
        resultTabs.removeAll();
        boolean error = false;
        int grids = 0;
        for (QueryResult r : results) {
            JComponent content;
            if (r.model() != null) {
                if (r.cursor() != null && !r.cursor().exhausted) {
                    openCursors.add(r.cursor());
                }
                content = buildGridPanel(r);
                grids++;
            } else {
                JTextArea area = new JTextArea(
                        r.message() + "\n\n(executado em " + r.elapsedMs() + " ms)");
                area.setEditable(false);
                content = new JScrollPane(area);
            }
            resultTabs.addTab(r.title(), content);
            resultTabs.setToolTipTextAt(resultTabs.getTabCount() - 1, snippet(r.sql()));
            error = error || r.error();
        }
        if (resultTabs.getTabCount() > 0) {
            resultTabs.setSelectedIndex(0);
            showResultsCard();
        } else {
            showEmptyState();
        }
        statusBar.setText(" " + results.size() + " instrucao(oes) executada(s), "
                + grids + " com resultado" + (error ? " - parou em erro" : ""));
    }

    /** Largura inicial uniforme, ordenacao por coluna e visual mais limpo. */
    private void styleResultTable(JTable table) {
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.setRowHeight(24);
        table.setShowGrid(true);
        table.setGridColor(new Color(0xEDEFF2));
        table.setIntercellSpacing(new Dimension(0, 1));
        table.setFillsViewportHeight(true);
        table.setAutoCreateRowSorter(true);
        table.setCellSelectionEnabled(true);
        table.getTableHeader().setReorderingAllowed(false);
        styleResultHeader(table);
        installCopyMenu(table);
        ResultCellRenderer cellRenderer = new ResultCellRenderer();
        final int uniformWidth = 150;
        for (int c = 0; c < table.getColumnModel().getColumnCount(); c++) {
            table.getColumnModel().getColumn(c).setPreferredWidth(uniformWidth);
            table.getColumnModel().getColumn(c).setMinWidth(60);
            table.getColumnModel().getColumn(c).setCellRenderer(cellRenderer);
        }
    }

    /** Renderer das celulas: padding lateral e alinhamento por tipo (numeros a direita). */
    private static final class ResultCellRenderer extends DefaultTableCellRenderer {
        private static final long serialVersionUID = 1L;

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            Class<?> cls = table.getColumnClass(column);
            if (Number.class.isAssignableFrom(cls)) {
                setHorizontalAlignment(SwingConstants.RIGHT);
            } else if (cls == Boolean.class) {
                setHorizontalAlignment(SwingConstants.CENTER);
            } else {
                setHorizontalAlignment(SwingConstants.LEFT);
            }
            setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
            return this;
        }
    }

    /** Cabecalho com destaque: negrito, fundo verde claro e borda de acento. */
    private void styleResultHeader(JTable table) {
        JTableHeader header = table.getTableHeader();
        final TableCellRenderer base = header.getDefaultRenderer();
        final Font bold = header.getFont().deriveFont(Font.BOLD);
        final Color bg = new Color(0xF1F3F5);
        final Color fg = new Color(0x334155);
        final Color line = new Color(0xCBD5E1);
        header.setDefaultRenderer((tbl, value, isSelected, hasFocus, row, column) -> {
            Component comp = base.getTableCellRendererComponent(
                    tbl, value, isSelected, hasFocus, row, column);
            if (comp instanceof JLabel label) {
                label.setFont(bold);
                label.setBackground(bg);
                label.setForeground(fg);
                label.setOpaque(true);
                label.setHorizontalAlignment(SwingConstants.LEFT);
                label.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0, 0, 2, 1, line),
                        BorderFactory.createEmptyBorder(5, 10, 5, 10)));
            }
            return comp;
        });
        Dimension d = header.getPreferredSize();
        d.height = Math.max(d.height, 30);
        header.setPreferredSize(d);
    }

    /** Painel de grade: tabela + numeracao de linhas + barra de paginacao. */
    private JComponent buildGridPanel(QueryResult r) {
        JTable table = new JTable(r.model());
        styleResultTable(table);
        JScrollPane scroll = new JScrollPane(table);
        scroll.setRowHeaderView(buildRowNumbers(table, r.model()));
        scroll.setCorner(JScrollPane.UPPER_LEFT_CORNER, buildRowNumberCorner());

        JLabel info = new JLabel();
        JButton more = new JButton("Carregar mais " + PAGE_SIZE);
        JButton all = new JButton("Carregar tudo");
        Runnable refresh = () -> {
            boolean hasMore = r.cursor() != null && !r.cursor().exhausted;
            info.setText(r.model().getRowCount() + " linha(s)"
                    + (hasMore ? "+" : "") + "   ·   " + r.elapsedMs() + " ms");
            more.setVisible(hasMore);
            all.setVisible(hasMore);
        };
        more.addActionListener(e -> {
            loadPage(r, PAGE_SIZE);
            refresh.run();
        });
        all.addActionListener(e -> loadAll(r, refresh));
        refresh.run();

        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 3));
        bar.add(info);
        bar.add(more);
        bar.add(all);

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(scroll, BorderLayout.CENTER);
        panel.add(bar, BorderLayout.SOUTH);
        return panel;
    }

    /** Le ate {@code max} linhas do cursor para o modelo (na EDT). */
    private void loadPage(QueryResult r, int max) {
        ResultCursor c = r.cursor();
        if (c == null || c.exhausted) {
            return;
        }
        try {
            int read = appendPage(r.model(), c.rs, max);
            if (read < max) {
                c.exhausted = true;
                c.close();
                openCursors.remove(c);
            }
        } catch (SQLException ex) {
            c.exhausted = true;
            c.close();
            openCursors.remove(c);
            statusBar.setText(" Erro ao carregar mais linhas: " + ex.getMessage());
        }
    }

    /** Le todas as linhas restantes do cursor em segundo plano. */
    private void loadAll(QueryResult r, Runnable refresh) {
        ResultCursor c = r.cursor();
        if (c == null || c.exhausted) {
            return;
        }
        statusBar.setText(" Carregando todas as linhas...");
        new SwingWorker<Void, Vector<Object>>() {
            @Override
            protected Void doInBackground() throws Exception {
                int cols = r.model().getColumnCount();
                while (c.rs.next()) {
                    Vector<Object> row = new Vector<>(cols);
                    for (int i = 1; i <= cols; i++) {
                        row.add(c.rs.getObject(i));
                    }
                    publish(row);
                }
                return null;
            }

            @Override
            protected void process(List<Vector<Object>> chunks) {
                for (Vector<Object> row : chunks) {
                    r.model().addRow(row);
                }
                refresh.run();
            }

            @Override
            protected void done() {
                c.exhausted = true;
                c.close();
                openCursors.remove(c);
                try {
                    get();
                    statusBar.setText(" Todas as linhas carregadas ("
                            + r.model().getRowCount() + ").");
                } catch (Exception ex) {
                    statusBar.setText(" Erro ao carregar linhas: " + ex.getMessage());
                }
                refresh.run();
            }
        }.execute();
    }

    private void closeOpenCursors() {
        for (ResultCursor c : openCursors) {
            c.close();
        }
        openCursors.clear();
    }

    // ---------- Numeracao de linhas ----------

    /** Coluna fixa (gutter) com o numero de cada linha, a esquerda da grade. */
    private JComponent buildRowNumbers(JTable table, DefaultTableModel model) {
        AbstractListModel<String> listModel = new AbstractListModel<>() {
            private static final long serialVersionUID = 1L;

            @Override
            public int getSize() {
                return table.getRowCount();
            }

            @Override
            public String getElementAt(int index) {
                return Integer.toString(index + 1);
            }
        };
        JList<String> list = new JList<>(listModel);
        list.setFixedCellHeight(table.getRowHeight());
        list.setFixedCellWidth(54);
        list.setFocusable(false);
        final Color bg = new Color(0xF3F4F6);
        final Color fg = new Color(0x9AA3AF);
        final Font font = list.getFont().deriveFont(Font.PLAIN);
        ListCellRenderer<Object> renderer = (lst, value, index, selected, focused) -> {
            JLabel l = new JLabel(value == null ? "" : value.toString());
            l.setHorizontalAlignment(SwingConstants.RIGHT);
            l.setOpaque(true);
            l.setBackground(bg);
            l.setForeground(fg);
            l.setFont(font);
            l.setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 8));
            return l;
        };
        list.setCellRenderer(renderer);
        model.addTableModelListener(e -> {
            list.revalidate();
            list.repaint();
        });
        return list;
    }

    private JComponent buildRowNumberCorner() {
        JPanel corner = new JPanel();
        corner.setOpaque(true);
        corner.setBackground(new Color(0xF1F3F5));
        corner.setBorder(BorderFactory.createMatteBorder(0, 0, 2, 1, new Color(0xCBD5E1)));
        return corner;
    }

    // ---------- Copia (celula / linha / coluna) ----------

    private void installCopyMenu(JTable table) {
        JPopupMenu menu = new JPopupMenu();
        JMenuItem copyCell = new JMenuItem("Copiar celula");
        JMenuItem copyRow = new JMenuItem("Copiar linha");
        JMenuItem copyCol = new JMenuItem("Copiar coluna");
        JMenuItem copySel = new JMenuItem("Copiar selecao com cabecalho");
        copyCell.addActionListener(e -> copyCell(table));
        copyRow.addActionListener(e -> copyRows(table));
        copyCol.addActionListener(e -> copyColumn(table));
        copySel.addActionListener(e -> copySelectionWithHeader(table));
        menu.add(copyCell);
        menu.add(copyRow);
        menu.add(copyCol);
        menu.addSeparator();
        menu.add(copySel);
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                maybe(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                maybe(e);
            }

            private void maybe(MouseEvent e) {
                if (!e.isPopupTrigger()) {
                    return;
                }
                int row = table.rowAtPoint(e.getPoint());
                int col = table.columnAtPoint(e.getPoint());
                if (row >= 0 && col >= 0 && !table.isCellSelected(row, col)) {
                    table.changeSelection(row, col, false, false);
                }
                menu.show(table, e.getX(), e.getY());
            }
        });
    }

    private void copyCell(JTable t) {
        int r = t.getSelectedRow();
        int c = t.getSelectedColumn();
        if (r >= 0 && c >= 0) {
            setClipboard(cellText(t, r, c));
        }
    }

    private void copyRows(JTable t) {
        int[] rows = t.getSelectedRows();
        if (rows.length == 0) {
            return;
        }
        int cols = t.getColumnCount();
        StringBuilder sb = new StringBuilder();
        for (int ri = 0; ri < rows.length; ri++) {
            for (int c = 0; c < cols; c++) {
                if (c > 0) {
                    sb.append('\t');
                }
                sb.append(cellText(t, rows[ri], c));
            }
            if (ri < rows.length - 1) {
                sb.append('\n');
            }
        }
        setClipboard(sb.toString());
    }

    private void copyColumn(JTable t) {
        int c = t.getSelectedColumn();
        if (c < 0) {
            return;
        }
        int rows = t.getRowCount();
        StringBuilder sb = new StringBuilder();
        for (int r = 0; r < rows; r++) {
            if (r > 0) {
                sb.append('\n');
            }
            sb.append(cellText(t, r, c));
        }
        setClipboard(sb.toString());
    }

    private void copySelectionWithHeader(JTable t) {
        int[] rows = t.getSelectedRows();
        int[] cols = t.getSelectedColumns();
        if (rows.length == 0 || cols.length == 0) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (int ci = 0; ci < cols.length; ci++) {
            if (ci > 0) {
                sb.append('\t');
            }
            sb.append(t.getColumnName(cols[ci]));
        }
        sb.append('\n');
        for (int ri = 0; ri < rows.length; ri++) {
            for (int ci = 0; ci < cols.length; ci++) {
                if (ci > 0) {
                    sb.append('\t');
                }
                sb.append(cellText(t, rows[ri], cols[ci]));
            }
            if (ri < rows.length - 1) {
                sb.append('\n');
            }
        }
        setClipboard(sb.toString());
    }

    private static String cellText(JTable t, int row, int col) {
        Object v = t.getValueAt(row, col);
        return v == null ? "" : v.toString();
    }

    private void setClipboard(String text) {
        Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection(text), null);
        statusBar.setText(" Copiado para a area de transferencia.");
    }

    // ---------- Exportacao ----------

    private void maybeShowTabMenu(MouseEvent e) {
        if (!e.isPopupTrigger()) {
            return;
        }
        int idx = resultTabs.indexAtLocation(e.getX(), e.getY());
        if (idx < 0) {
            return;
        }
        resultTabs.setSelectedIndex(idx);

        JPopupMenu menu = new JPopupMenu();
        JMenuItem one = new JMenuItem("Exportar este resultado para Excel...");
        one.addActionListener(a -> exportSingle(idx));
        JMenuItem all = new JMenuItem("Exportar todos (uma aba por resultado)...");
        all.addActionListener(a -> exportAll());
        menu.add(one);
        menu.add(all);
        menu.show(resultTabs, e.getX(), e.getY());
    }

    private void exportSingle(int idx) {
        if (idx < 0 || idx >= lastResults.size()) {
            return;
        }
        QueryResult r = lastResults.get(idx);
        if (r.model() == null) {
            JOptionPane.showMessageDialog(this,
                    "Esta aba nao possui dados tabulares para exportar.",
                    "Exportar para Excel", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        File file = chooseSaveFile(r.title());
        if (file != null) {
            doExport(List.of(new ExcelExporter.TableSheet(r.title(), r.model())), file);
        }
    }

    private void exportAll() {
        List<ExcelExporter.TableSheet> sheets = new ArrayList<>();
        for (QueryResult r : lastResults) {
            if (r.model() != null) {
                sheets.add(new ExcelExporter.TableSheet(r.title(), r.model()));
            }
        }
        if (sheets.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Nenhum resultado tabular para exportar.",
                    "Exportar para Excel", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        File file = chooseSaveFile("resultados");
        if (file != null) {
            doExport(sheets, file);
        }
    }

    private File chooseSaveFile(String defaultName) {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Salvar como Excel");
        fc.setSelectedFile(new File(defaultName + ".xlsx"));
        fc.setFileFilter(new FileNameExtensionFilter("Planilha Excel (*.xlsx)", "xlsx"));
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return null;
        }
        File file = fc.getSelectedFile();
        if (!file.getName().toLowerCase().endsWith(".xlsx")) {
            file = new File(file.getParentFile(), file.getName() + ".xlsx");
        }
        return file;
    }

    private void doExport(List<ExcelExporter.TableSheet> sheets, File file) {
        statusBar.setText(" Exportando para " + file.getName() + "...");
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                ExcelExporter.export(sheets, file);
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    statusBar.setText(" Exportado: " + file.getAbsolutePath());
                } catch (Exception ex) {
                    showError("Falha ao exportar", ex);
                    statusBar.setText(" Erro ao exportar");
                }
            }
        }.execute();
    }

    // ---------- Auxiliares ----------

    private static String snippet(String sql) {
        String oneLine = sql.replaceAll("\\s+", " ").trim();
        return oneLine.length() > 80 ? oneLine.substring(0, 80) + "..." : oneLine;
    }

    /** Cria o modelo (apenas cabecalhos + tipos de coluna) para uma consulta. */
    private static ResultTableModel createModel(ResultSet rs) throws SQLException {
        ResultSetMetaData md = rs.getMetaData();
        int cols = md.getColumnCount();
        Vector<String> names = new Vector<>();
        Class<?>[] types = new Class<?>[cols];
        for (int i = 1; i <= cols; i++) {
            names.add(md.getColumnLabel(i));
            types[i - 1] = resolveColumnClass(md, i);
        }
        return new ResultTableModel(names, types);
    }

    private static Class<?> resolveColumnClass(ResultSetMetaData md, int col) {
        try {
            return Class.forName(md.getColumnClassName(col));
        } catch (Exception ex) {
            return Object.class;
        }
    }

    /** Anexa ate {@code max} linhas do ResultSet ao modelo; retorna quantas leu. */
    private static int appendPage(DefaultTableModel model, ResultSet rs, int max)
            throws SQLException {
        int cols = model.getColumnCount();
        int read = 0;
        while (read < max && rs.next()) {
            Vector<Object> row = new Vector<>(cols);
            for (int i = 1; i <= cols; i++) {
                row.add(rs.getObject(i));
            }
            model.addRow(row);
            read++;
        }
        return read;
    }

    /** Modelo de tabela somente-leitura que expoe o tipo real de cada coluna
     *  (para a ordenacao numerica/data funcionar corretamente). */
    private static final class ResultTableModel extends DefaultTableModel {
        private static final long serialVersionUID = 1L;
        private final transient Class<?>[] types;

        ResultTableModel(Vector<String> names, Class<?>[] types) {
            super(names, 0);
            this.types = types;
        }

        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }

        @Override
        public Class<?> getColumnClass(int column) {
            if (column >= 0 && column < types.length && types[column] != null) {
                return types[column];
            }
            return Object.class;
        }
    }

    /** Cursor aberto (Statement + ResultSet) para paginacao sob demanda. */
    private static final class ResultCursor {
        final Statement st;
        final ResultSet rs;
        boolean exhausted;

        ResultCursor(Statement st, ResultSet rs) {
            this.st = st;
            this.rs = rs;
        }

        void close() {
            try {
                rs.close();
            } catch (SQLException ignore) {
                // ignora
            }
            try {
                st.close();
            } catch (SQLException ignore) {
                // ignora
            }
        }
    }

    private void populateTree(SchemaInfo schema) {
        this.currentSchema = schema;
        objectSearch.setEnabled(true);
        rebuildTree("");
    }

    private void applyObjectFilter() {
        if (currentSchema != null) {
            rebuildTree(objectSearch.getText());
        }
    }

    /**
     * Monta a arvore de objetos agrupada por tipo (estilo PL/SQL Developer),
     * filtrando pelos nomes que contem {@code filter}. Tabelas e views tambem
     * casam quando uma de suas colunas bate com o filtro.
     */
    private void rebuildTree(String filter) {
        String f = filter == null ? "" : filter.trim().toLowerCase(Locale.ROOT);
        boolean filtering = !f.isEmpty();
        SchemaInfo schema = currentSchema;

        DefaultMutableTreeNode root = new DefaultMutableTreeNode(
                new ObjNode(NodeType.SCHEMA, schema.name(), schema.name(), null, null));

        addTableCategory(root, "Tabelas", schema.tables(), NodeType.TABLE, "TABLE", f, filtering);
        addTableCategory(root, "Visualizacoes", schema.views(), NodeType.VIEW, "VIEW", f, filtering);
        addNameCategory(root, "Procedures", schema.procedures(), NodeType.ROUTINE, "PROCEDURE", f, filtering);
        addNameCategory(root, "Functions", schema.functions(), NodeType.ROUTINE, "FUNCTION", f, filtering);
        addNameCategory(root, "Triggers", schema.triggers(), NodeType.TRIGGER, "TRIGGER", f, filtering);

        objectTree.setModel(new DefaultTreeModel(root));
        if (filtering) {
            expandAll();
        } else {
            expandCategories(root);
        }
    }

    private void addTableCategory(DefaultMutableTreeNode root, String label,
            List<TableInfo> items, NodeType type, String kind, String f, boolean filtering) {
        DefaultMutableTreeNode cat = new DefaultMutableTreeNode();
        int shown = 0;
        for (TableInfo t : items) {
            if (filtering && !contains(t.name(), f) && !anyColumnMatches(t, f)) {
                continue;
            }
            DefaultMutableTreeNode tn = new DefaultMutableTreeNode(
                    new ObjNode(type, t.name(), t.name(), kind, t));
            // Ao buscar, a arvore mostra so o objeto; as colunas ficam na tela
            // de propriedades (duplo-clique). No modo normal, expande as colunas.
            if (!filtering) {
                for (ColumnInfo c : t.columns()) {
                    tn.add(new DefaultMutableTreeNode(new ObjNode(
                            NodeType.COLUMN, c.name() + " : " + c.type(), c.name(), null, null)));
                }
            }
            cat.add(tn);
            shown++;
        }
        if (!filtering || shown > 0) {
            cat.setUserObject(new ObjNode(NodeType.CATEGORY,
                    label + " (" + items.size() + ")", label, null, null));
            root.add(cat);
        }
    }

    private static boolean anyColumnMatches(TableInfo t, String f) {
        for (ColumnInfo c : t.columns()) {
            if (contains(c.name(), f)) {
                return true;
            }
        }
        return false;
    }

    private void addNameCategory(DefaultMutableTreeNode root, String label,
            List<String> items, NodeType type, String kind, String f, boolean filtering) {
        DefaultMutableTreeNode cat = new DefaultMutableTreeNode();
        int shown = 0;
        for (String name : items) {
            if (filtering && !contains(name, f)) {
                continue;
            }
            cat.add(new DefaultMutableTreeNode(new ObjNode(type, name, name, kind, null)));
            shown++;
        }
        if (!filtering || shown > 0) {
            cat.setUserObject(new ObjNode(NodeType.CATEGORY,
                    label + " (" + items.size() + ")", label, null, null));
            root.add(cat);
        }
    }

    private static boolean contains(String value, String lowerFilter) {
        return lowerFilter.isEmpty()
                || value.toLowerCase(Locale.ROOT).contains(lowerFilter);
    }

    private void expandCategories(DefaultMutableTreeNode root) {
        objectTree.expandPath(new TreePath(root.getPath()));
        for (int i = 0; i < root.getChildCount(); i++) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) root.getChildAt(i);
            objectTree.expandPath(new TreePath(child.getPath()));
        }
    }

    private void expandAll() {
        for (int i = 0; i < objectTree.getRowCount(); i++) {
            objectTree.expandRow(i);
        }
    }

    /** Abre a tela de propriedades do objeto selecionado (duplo-clique). */
    private void openSelectedObjectProperties() {
        TreePath path = objectTree.getSelectionPath();
        if (path == null) {
            return;
        }
        Object node = ((DefaultMutableTreeNode) path.getLastPathComponent()).getUserObject();
        if (node instanceof ObjNode obj && obj.kind() != null) {
            showObjectProperties(obj);
        }
    }

    /**
     * Janela de propriedades: para tabelas/views mostra a grade de colunas;
     * para todos os objetos carrega a definicao (DDL) sob demanda.
     */
    private void showObjectProperties(ObjNode obj) {
        JDialog dialog = new JDialog(this, prettyKind(obj.kind()) + " - " + obj.name(), false);
        dialog.setSize(560, 460);
        dialog.setLocationRelativeTo(this);
        dialog.setLayout(new BorderLayout());

        JLabel title = new JLabel(obj.name());
        title.setFont(title.getFont().deriveFont(Font.BOLD, 14f));
        JLabel sub = new JLabel(prettyKind(obj.kind()) + "  ·  " + currentSchema.name());
        sub.setForeground(MUTED);
        JPanel head = new JPanel(new BorderLayout());
        head.setBorder(BorderFactory.createEmptyBorder(10, 12, 8, 12));
        head.add(title, BorderLayout.NORTH);
        head.add(sub, BorderLayout.SOUTH);

        JTabbedPane tabs = new JTabbedPane();
        boolean isTableLike = obj.table() != null;
        DefaultTableModel colModel = null;
        DefaultTableModel idxModel = null;
        DefaultTableModel fkModel = null;
        if (isTableLike) {
            colModel = readOnlyModel(
                    "#", "Coluna", "Tipo", "Nulo", "Chave", "Default", "Extra", "Comentario");
            tabs.addTab("Colunas", tableInScroll(colModel));
            // Indices e FKs sao especificos de tabelas (views nao tem).
            if ("TABLE".equals(obj.kind())) {
                idxModel = readOnlyModel("Indice", "Unico", "Tipo", "Colunas");
                tabs.addTab("Indices", tableInScroll(idxModel));
                fkModel = readOnlyModel(
                        "Constraint", "Coluna(s)", "Referencia", "Coluna(s) ref.",
                        "On Update", "On Delete");
                tabs.addTab("Chaves estrangeiras", tableInScroll(fkModel));
            }
        }

        JTextArea ddlArea = new JTextArea("Carregando definicao...");
        ddlArea.setEditable(false);
        ddlArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        tabs.addTab("DDL", new JScrollPane(ddlArea));

        dialog.add(head, BorderLayout.NORTH);
        dialog.add(tabs, BorderLayout.CENTER);
        dialog.setVisible(true);

        if (isTableLike) {
            loadTableDetailsInto(obj, colModel, idxModel, fkModel);
        }
        loadDefinition(obj, ddlArea);
    }

    private static DefaultTableModel readOnlyModel(Object... columns) {
        return new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int r, int c) {
                return false;
            }
        };
    }

    private static JComponent tableInScroll(DefaultTableModel model) {
        JTable t = new JTable(model);
        t.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        t.setFillsViewportHeight(true);
        if ("#".equals(model.getColumnName(0))) {
            t.getColumnModel().getColumn(0).setMaxWidth(40);
        }
        t.getTableHeader().setReorderingAllowed(false);
        return new JScrollPane(t);
    }

    /** Preenche as grades de colunas, indices e FKs em segundo plano. */
    private void loadTableDetailsInto(ObjNode obj, DefaultTableModel colModel,
            DefaultTableModel idxModel, DefaultTableModel fkModel) {
        new SwingWorker<TableDetails, Void>() {
            @Override
            protected TableDetails doInBackground() throws Exception {
                Connection conn = connectionManager.getConnection();
                return metadataService.loadTableDetails(conn, currentSchema.name(), obj.name());
            }

            @Override
            protected void done() {
                try {
                    TableDetails d = get();
                    for (ColumnDetail c : d.columns()) {
                        colModel.addRow(new Object[] {
                                c.position(), c.name(), c.type(),
                                c.nullable() ? "Sim" : "Nao",
                                prettyKey(c.key()),
                                c.defaultValue() == null ? "" : c.defaultValue(),
                                c.extra() == null ? "" : c.extra(),
                                c.comment() == null ? "" : c.comment()});
                    }
                    if (idxModel != null) {
                        for (IndexInfo ix : d.indexes()) {
                            idxModel.addRow(new Object[] {
                                    ix.name(), ix.unique() ? "Sim" : "Nao",
                                    ix.type(), String.join(", ", ix.columns())});
                        }
                    }
                    if (fkModel != null) {
                        for (ForeignKeyInfo fk : d.foreignKeys()) {
                            fkModel.addRow(new Object[] {
                                    fk.name(), String.join(", ", fk.columns()),
                                    fk.referencedTable(),
                                    String.join(", ", fk.referencedColumns()),
                                    fk.onUpdate(), fk.onDelete()});
                        }
                    }
                } catch (Exception ex) {
                    Throwable c = (ex.getCause() != null) ? ex.getCause() : ex;
                    statusBar.setText(" Erro ao carregar detalhes: " + c.getMessage());
                }
            }
        }.execute();
    }

    private static String prettyKey(String key) {
        if (key == null) {
            return "";
        }
        return switch (key) {
            case "PRI" -> "PK";
            case "UNI" -> "Unica";
            case "MUL" -> "Indice";
            default -> key;
        };
    }

    private void loadDefinition(ObjNode obj, JTextArea target) {
        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                if (!connectionManager.isConnected()) {
                    return "Sem conexao ativa.";
                }
                Connection conn = connectionManager.getConnection();
                String sql = dialect.definitionQuery(obj.kind(), obj.name());
                try (Statement st = conn.createStatement();
                     ResultSet rs = st.executeQuery(sql)) {
                    if (rs.next()) {
                        int idx = pickDefinitionColumn(rs.getMetaData());
                        String def = rs.getString(idx);
                        return (def != null) ? def : "(sem definicao)";
                    }
                    return "(sem definicao)";
                }
            }

            @Override
            protected void done() {
                try {
                    target.setText(get());
                } catch (Exception ex) {
                    Throwable c = (ex.getCause() != null) ? ex.getCause() : ex;
                    target.setText("Erro ao carregar a definicao: " + c.getMessage());
                }
                target.setCaretPosition(0);
            }
        }.execute();
    }

    /** Escolhe a coluna do SHOW CREATE que contem o DDL (ex.: "Create Table"). */
    private static int pickDefinitionColumn(ResultSetMetaData md) throws SQLException {
        int cols = md.getColumnCount();
        for (int i = 1; i <= cols; i++) {
            String label = md.getColumnLabel(i).toLowerCase(Locale.ROOT);
            if (label.contains("create") || label.contains("statement")) {
                return i;
            }
        }
        return cols;
    }

    private static String prettyKind(String kind) {
        return switch (kind) {
            case "TABLE" -> "Tabela";
            case "VIEW" -> "Visualizacao";
            case "PROCEDURE" -> "Procedure";
            case "FUNCTION" -> "Function";
            case "TRIGGER" -> "Trigger";
            default -> kind;
        };
    }

    /** Tipos de no na arvore de objetos. */
    private enum NodeType { SCHEMA, CATEGORY, TABLE, VIEW, ROUTINE, TRIGGER, COLUMN }

    /**
     * No da arvore: tipo, texto exibido, nome cru do objeto, o tipo para o DDL
     * (kind, null para schema/categoria/coluna) e a tabela associada quando houver.
     */
    private record ObjNode(NodeType type, String display, String name,
                           String kind, TableInfo table) {
        @Override
        public String toString() {
            return display;
        }
    }

    private void showError(String title, Exception ex) {
        Throwable cause = (ex.getCause() != null) ? ex.getCause() : ex;
        JOptionPane.showMessageDialog(
                this, cause.getMessage(), title, JOptionPane.ERROR_MESSAGE);
    }

    /** Resultado de um statement: grade (model != null) ou mensagem (update/erro). */
    private record QueryResult(String title, String sql, DefaultTableModel model,
                               String message, boolean error, long elapsedMs,
                               ResultCursor cursor) {
        static QueryResult grid(String title, String sql, DefaultTableModel model,
                long elapsedMs, ResultCursor cursor) {
            return new QueryResult(title, sql, model, null, false, elapsedMs, cursor);
        }

        static QueryResult message(String title, String sql, String message,
                boolean error, long elapsedMs) {
            return new QueryResult(title, sql, null, message, error, elapsedMs, null);
        }
    }
}
