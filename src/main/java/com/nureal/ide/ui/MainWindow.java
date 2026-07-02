package com.nureal.ide.ui;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Vector;
import java.util.concurrent.CancellationException;
import java.util.function.BiConsumer;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.ButtonGroup;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

import com.formdev.flatlaf.FlatClientProperties;
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
import com.nureal.ide.core.format.FormatPreferences;
import com.nureal.ide.core.format.SqlFormatter;
import com.nureal.ide.core.log.AppLogger;
import com.nureal.ide.core.metadata.MetadataCache;
import com.nureal.ide.core.metadata.MetadataService;
import com.nureal.ide.core.metadata.model.ColumnDetail;
import com.nureal.ide.core.metadata.model.ColumnInfo;
import com.nureal.ide.core.metadata.model.ForeignKeyInfo;
import com.nureal.ide.core.metadata.model.IndexInfo;
import com.nureal.ide.core.metadata.model.SchemaInfo;
import com.nureal.ide.core.metadata.model.TableDetails;
import com.nureal.ide.core.metadata.model.TableInfo;
import com.nureal.ide.core.safety.SqlRiskAnalyzer;
import com.nureal.ide.core.session.SessionStore;
import com.nureal.ide.core.sql.SqlStatementSplitter;
import com.nureal.ide.core.ui.UiPreferences;

/**
 * Janela principal no estilo de uma IDE moderna (FlatLaf): top bar com acao de
 * executar e tema, conexoes e objetos a esquerda, editor SQL em abas no centro
 * e resultados em abas abaixo (uma aba por statement), com exportacao para
 * Excel.
 */
public class MainWindow extends JFrame {

	private static final long serialVersionUID = 1L;
	// Pacote-visivel (nao private): reaproveitada por ResultStatusBar para o
	// icone do botao "Exportar" — evita duplicar o mesmo valor de cor em duas
	// classes do mesmo pacote.
	static final Color ACCENT = new Color(0x059669);
	private static final Color MUTED = new Color(0x6B7280);

	private static final int PAGE_SIZE = 200;
	private static final int MAX_TABS = 15;

	private static final String SCRATCH = "(sem conexao)";

	private final DatabaseDialect dialect = new MySqlDialect();
	/** Aponta SEMPRE para a conexao do workspace ativo (trocada ao alternar). */
	private ConnectionManager connectionManager = new ConnectionManager(dialect);
	private final Map<String, Workspace> workspaces = new LinkedHashMap<>();
	private Workspace activeWorkspace;
	private Map<String, SessionStore.Session> savedSessions = new LinkedHashMap<>();
	private final MetadataService metadataService = new MetadataService(dialect);
	private final MetadataCache metadataCache = new MetadataCache();
	// Cache de metadados de tabela (colunas/PK/indices/FKs) para a grade de
	// resultados — indicador de FK no cabecalho e popup/menu de metadados de
	// coluna; compartilhado por TODAS as grades da sessao (ver ResultGrid),
	// evita repetir loadTableDetails() para a mesma tabela a cada resultado.
	private final TableMetadataCache tableMetadataCache = new TableMetadataCache(metadataService);
	private final SqlCompletionProvider completionProvider = new SqlCompletionProvider(dialect.keywords());
	private final ConnectionStore connectionStore = new ConnectionStore();
	private final SessionStore sessionStore = new SessionStore();
	private Timer autosaveTimer;

	private JTabbedPane editorTabs;
	private Component plusTab;
	private boolean addingTab;
	private JSplitPane mainSplit;
	private JSplitPane centerSplit;
	private JComponent leftSide;
	private JComponent resultsArea;
	private JComponent editorAreaPanel;
	private JComponent objectBrowserPanel;
	private JComponent toolbarBar;
	private JComponent footerBar;
	private JButton resultsOrientationButton;
	private int sidebarLoc = 248;
	private int resultsLoc = -1;
	private JTabbedPane resultTabs;
	private JPanel resultsCards;
	private JTree objectTree;
	private ConnectionsPanel connectionsPanel;
	private JTextField objectSearch;
	private SchemaInfo currentSchema;
	private JLabel statusBar;
	private JLabel connStatusLabel;
	private JProgressBar connProgress;
	private JButton runButton;
	private JButton themeButton;
	private JComponent resultsOverlay;
	private SwingWorker<List<QueryResult>, Void> runWorker;
	private volatile Statement runningStatement;

	private boolean dark = false;
	private List<QueryResult> lastResults = new ArrayList<>();
	private final List<ResultCursor> openCursors = new ArrayList<>();
	/**
	 * Ultimo conjunto de resultados de CADA aba de SQL — cada aba tem os
	 * seus proprios resultados, independentes das outras (ver
	 * {@code showResultsForActiveEditor}, chamado ao trocar de aba). Uma aba
	 * sem entrada aqui ainda nao rodou nenhuma query nesta sessao (mostra o
	 * estado vazio). Entrada removida quando a aba fecha (ver
	 * {@code closeQueryTab}).
	 */
	private final Map<SqlEditorPane, List<QueryResult>> resultsByTab = new LinkedHashMap<>();

	// ---------- Layout flexivel / zoom / modo compacto ----------

	private static final double[] ZOOM_LEVELS = { 0.75, 0.90, 1.00, 1.10, 1.25, 1.50 };

	private final UiPreferences uiPrefsStore = new UiPreferences();
	private Font baseDefaultFont;
	private boolean sidebarOnRight = false;
	private boolean resultsVertical = false;
	private boolean compactMode = false;
	private int zoomIndex = UiPreferences.DEFAULT_ZOOM_INDEX;

	// ---------- Formatacao de SQL (presets) e fonte do editor ----------

	private final FormatPreferences formatPrefsStore = new FormatPreferences();
	private FormatPreferences.State formatState = FormatPreferences.State.defaults();

	public MainWindow() {
		super("Nureal Database IDE");
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setIconImages(Icons.brandImages());
		setSize(1280, 800);
		setLocationRelativeTo(null);
		loadUiPrefs();
		loadFormatPrefs();
		buildUi();
		registerWindowShortcuts();
		// Salva a sessao e fecha as conexoes JDBC ao fechar (alem do autosave
		// continuo durante a digitacao) — sem isso, as conexoes de todos os
		// workspaces ficavam abertas ate o processo encerrar de vez.
		addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				saveSession();
				closeAllConnections();
			}
		});
	}

	/**
	 * Carrega as preferencias de layout/zoom salvas e ja aplica a fonte do zoom.
	 */
	private void loadUiPrefs() {
		captureBaseFont();
		UiPreferences.State state;
		try {
			state = uiPrefsStore.load();
		} catch (Exception ex) {
			AppLogger.warning("Falha ao carregar preferencias de UI; usando padrao", ex);
			state = UiPreferences.State.defaults();
		}
		sidebarOnRight = state.sidebarOnRight();
		resultsVertical = state.resultsVertical();
		compactMode = state.compactMode();
		zoomIndex = clampZoomIndex(state.zoomIndex());
		if (zoomIndex != UiPreferences.DEFAULT_ZOOM_INDEX) {
			applyZoomFont(zoomIndex); // so a fonte; ainda nao ha janela/componentes
		}
	}

	/** Carrega o preset de formatacao de SQL e a fonte do editor salvos. */
	private void loadFormatPrefs() {
		try {
			formatState = formatPrefsStore.load();
		} catch (Exception ex) {
			AppLogger.warning("Falha ao carregar preferencias de formatacao; usando padrao", ex);
			formatState = FormatPreferences.State.defaults();
		}
	}

	private void saveFormatState() {
		try {
			formatPrefsStore.save(formatState);
		} catch (Exception ex) {
			AppLogger.warning("Falha ao salvar preferencias de formatacao", ex);
			if (statusBar != null) {
				statusBar.setText(" Aviso: nao foi possivel salvar as preferencias de formatacao: " + ex.getMessage());
			}
		}
	}

	/** Formatador atual (preset + caixa + indentacao JSON), sob demanda. */
	private SqlFormatter currentSqlFormatter() {
		return formatState.buildFormatter();
	}

	/** Aplica a fonte escolhida a todas as abas de SQL atualmente abertas. */
	private void applyEditorFontToOpenTabs() {
		if (editorTabs == null) {
			return;
		}
		for (int i = 0; i < editorTabs.getTabCount(); i++) {
			Component c = editorTabs.getComponentAt(i);
			if (c instanceof SqlEditorPane sep) {
				sep.setFontFamily(formatState.editorFontFamily());
			}
		}
	}

	private void buildUi() {
		setLayout(new BorderLayout());

		leftSide = buildLeftSide();
		resultsArea = buildResultsArea();
		editorAreaPanel = buildEditorArea();

		centerSplit = new JSplitPane(resultsVertical ? JSplitPane.HORIZONTAL_SPLIT : JSplitPane.VERTICAL_SPLIT,
				editorAreaPanel, resultsArea);
		centerSplit.setResizeWeight(0.62);
		centerSplit.setBorder(BorderFactory.createEmptyBorder());

		mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, sidebarOnRight ? centerSplit : leftSide,
				sidebarOnRight ? leftSide : centerSplit);
		mainSplit.setResizeWeight(sidebarOnRight ? 0.78 : 0.22);
		mainSplit.setBorder(BorderFactory.createEmptyBorder());
		add(mainSplit, BorderLayout.CENTER);

		footerBar = buildFooter();
		add(footerBar, BorderLayout.SOUTH);

		applyDensityToPanels();
	}

	// ---------- Barras ----------

	private JComponent buildToolbar() {
		// JPanel totalmente transparente (sem o fundo mais claro que você não gostou)
		JPanel mainBar = new JPanel(new GridBagLayout());
		mainBar.setOpaque(false);

		// 8px acima/abaixo — o MESMO valor do padding do painel CONEXOES (ver
		// ConnectionsPanel, createEmptyBorder(8,8,8,8)), para as duas linhas
		// (cabecalho do sidebar e esta barra) ficarem na mesma altura visual,
		// lado a lado. 0px na esquerda para colar na linha da aba.
		mainBar.setBorder(BorderFactory.createEmptyBorder(8, 0, 8, 12));

		GridBagConstraints gbc = new GridBagConstraints();
		// O segredo do alinhamento: força todos os elementos a compartilharem a mesma
		// linha base vertical
		gbc.anchor = GridBagConstraints.BASELINE;
		gbc.fill = GridBagConstraints.NONE;
		gbc.weighty = 1.0;
		gbc.gridy = 0;

		// --- Botões da Esquerda ---
		runButton = new JButton("Executar");
		runButton.setIcon(Icons.get(IconType.RUN, 14, Color.WHITE));
		runButton.setToolTipText("Executar (Ctrl+Enter ou F5)");
		runButton.setEnabled(false);
		runButton.addActionListener(e -> onRun());
		runButton.setIconTextGap(6);
		runButton.setMargin(new Insets(6, 14, 6, 12));
		runButton.putClientProperty(FlatClientProperties.STYLE,
				"arc: 8; focusWidth: 0; innerFocusWidth: 0; borderWidth: 0");
		styleRunButton(); // Mantém o verde da Nureal

		// Sem icone aqui de proposito: o icone de "linhas" ficava estranho colado
		// ao texto "Formatar" nesse tamanho — so texto. Estilo OUTLINE (contorno,
		// sem preenchimento) — igual ao "Nova" do painel CONEXOES (ver
		// ConnectionsPanel#buildHeader) — em vez de um segundo botao solido do
		// lado do Executar: fica claro que Executar e a acao primaria, e o
		// conjunto Formatar+seta e mais leve/discreto (mesma leitura de "acao
		// secundaria" nos dois lugares da UI).
		JButton formatButton = new JButton("Formatar");
		formatButton.setToolTipText("Formatar SQL (Ctrl+Shift+F)");
		formatButton.addActionListener(e -> {
			SqlEditorPane editor = currentEditor();
			if (editor != null) {
				editor.formatText();
			}
		});
		formatButton.setMargin(new Insets(6, 12, 6, 12));
		formatButton.putClientProperty("JButton.buttonType", "roundRect");
		formatButton.putClientProperty(FlatClientProperties.STYLE, "arc: 8; borderWidth: 1");

		JButton formatMenuButton = new JButton(new com.formdev.flatlaf.icons.FlatMenuArrowIcon());
		formatMenuButton.setToolTipText("Presets e opcoes de formatacao");
		formatMenuButton
				.addActionListener(e -> buildFormatMenu().show(formatMenuButton, 0, formatMenuButton.getHeight()));
		formatMenuButton.setMargin(new Insets(6, 8, 6, 8));
		formatMenuButton.putClientProperty("JButton.buttonType", "roundRect");
		formatMenuButton.putClientProperty(FlatClientProperties.STYLE, "arc: 8; borderWidth: 1");

		// O icone minusculo da seta rende um "preferred height" menor que o do
		// texto "Executar"/"Formatar" — sem isto os tres ficam com alturas
		// ligeiramente diferentes mesmo com a mesma margem vertical. Forca os
		// tres a MESMA altura (a maior das tres), so a largura continua livre.
		int rowHeight = Math.max(runButton.getPreferredSize().height,
				Math.max(formatButton.getPreferredSize().height, formatMenuButton.getPreferredSize().height));
		for (JButton b : new JButton[] { runButton, formatButton, formatMenuButton }) {
			Dimension d = b.getPreferredSize();
			b.setPreferredSize(new Dimension(d.width, rowHeight));
		}

		// Adiciona os botões esquerdos um a um aplicando pequenos recuos à direita
		// (insets)
		gbc.gridx = 0;
		gbc.weightx = 0.0;
		gbc.insets = new Insets(0, 0, 0, 10); // Colado na esquerda, espaço apos o Executar
		mainBar.add(runButton, gbc);

		gbc.gridx = 1;
		gbc.insets = new Insets(0, 0, 0, 0);
		mainBar.add(formatButton, gbc);

		gbc.gridx = 2;
		// So uma pequena margem cinza entre "Formatar" e a seta de opcoes — nao
		// colados de vez (como um segmented button ficaria), so proximos.
		gbc.insets = new Insets(0, 4, 0, 0);
		mainBar.add(formatMenuButton, gbc);

		// --- O ESPAÇADOR INVISÍVEL ---
		// Ele joga tudo o que vier a partir daqui totalmente para a direita
		gbc.gridx = 3;
		gbc.weightx = 1.0;
		mainBar.add(Box.createHorizontalGlue(), gbc);

		// --- Separador sutil antes do grupo de icones de layout/tema ---
		JSeparator divider = new JSeparator(SwingConstants.VERTICAL);
		divider.setPreferredSize(new Dimension(1, 18));
		divider.setForeground(new Color(0xE2E5EA));
		gbc.gridx = 4;
		gbc.weightx = 0.0;
		gbc.insets = new Insets(0, 6, 0, 10);
		mainBar.add(divider, gbc);

		// --- Botões da Direita (icones discretos, mesma linguagem visual) ---
		JButton toggleSidebar = new JButton(Icons.get(IconType.PANEL_LEFT, 16, MUTED));
		toggleSidebar.setToolTipText("Mostrar/ocultar painel lateral (Ctrl+B)");
		toggleSidebar.addActionListener(e -> toggleSidebar());

		JButton toggleResults = new JButton(Icons.get(IconType.PANEL_BOTTOM, 16, MUTED));
		toggleResults.setToolTipText("Mostrar/ocultar resultados (Ctrl+J)");
		toggleResults.addActionListener(e -> toggleResults());

		JButton layoutButton = new JButton(Icons.get(IconType.SETTINGS, 16, MUTED));
		layoutButton.setToolTipText("Layout, zoom e modo compacto");
		layoutButton.addActionListener(e -> buildLayoutMenu().show(layoutButton, 0, layoutButton.getHeight()));

		themeButton = new JButton(Icons.get(IconType.THEME_DARK, 16, MUTED));
		themeButton.setToolTipText("Alternar tema claro/escuro");
		themeButton.addActionListener(e -> toggleTheme());

		// Icones planos, quadrados e com o mesmo arco do resto da barra — o
		// realce ao passar o mouse (hover) vem de graca do buttonType do FlatLaf.
		for (JButton btn : new JButton[] { toggleSidebar, toggleResults, layoutButton, themeButton }) {
			btn.putClientProperty("JButton.buttonType", "toolBarButton");
			btn.putClientProperty(FlatClientProperties.STYLE, "arc: 8");
			btn.setMargin(new Insets(5, 5, 5, 5));
		}

		// Adiciona os botões da direita sequencialmente
		gbc.insets = new Insets(0, 3, 0, 3); // Pequeno espaço entre os ícones

		gbc.gridx = 5;
		mainBar.add(toggleSidebar, gbc);
		gbc.gridx = 6;
		mainBar.add(toggleResults, gbc);
		gbc.gridx = 7;
		mainBar.add(layoutButton, gbc);
		gbc.gridx = 8;
		mainBar.add(themeButton, gbc);

		toolbarBar = mainBar;
		return mainBar;
	}

	/**
	 * Menu de layout: mover painel lateral, alternar orientacao dos resultados,
	 * modo compacto e niveis de zoom (Opcao B da spec: menu, em vez de
	 * drag-and-drop dos paineis).
	 */

	private JPopupMenu buildLayoutMenu() {
		JPopupMenu menu = new JPopupMenu();

		JMenuItem moveSidebar = new JMenuItem(
				sidebarOnRight ? "Mover painel lateral para a esquerda" : "Mover painel lateral para a direita");
		moveSidebar.addActionListener(a -> toggleSidebarSide());
		menu.add(moveSidebar);

		JMenuItem toggleOrientation = new JMenuItem(resultsVertical ? "Resultados embaixo do editor (horizontal)"
				: "Resultados ao lado do editor (vertical)");
		toggleOrientation.addActionListener(a -> toggleResultsOrientation());
		menu.add(toggleOrientation);

		menu.addSeparator();

		JCheckBoxMenuItem compact = new JCheckBoxMenuItem("Modo compacto", compactMode);
		compact.addActionListener(a -> toggleCompactMode());
		menu.add(compact);

		menu.addSeparator();
		JMenu zoomMenu = new JMenu("Zoom");
		for (int i = 0; i < ZOOM_LEVELS.length; i++) {
			int idx = i;
			int pct = (int) Math.round(ZOOM_LEVELS[i] * 100);
			String mark = (i == zoomIndex) ? "✓ " : "      ";
			JMenuItem item = new JMenuItem(mark + pct + "%");
			item.addActionListener(a -> setZoomIndex(idx));
			zoomMenu.add(item);
		}
		zoomMenu.addSeparator();
		JMenuItem reset = new JMenuItem("Redefinir (Ctrl+0)");
		reset.addActionListener(a -> resetZoom());
		zoomMenu.add(reset);
		menu.add(zoomMenu);

		return menu;
	}

	/**
	 * Menu de formatacao: presets, opcoes (caixa alta / JSON) e fonte do editor.
	 */
	private JPopupMenu buildFormatMenu() {
		JPopupMenu menu = new JPopupMenu();

		menu.add(formatMenuHeader("Presets"));
		ButtonGroup presets = new ButtonGroup();
		menu.add(presetItem(presets, SqlFormatter.Style.RIVER, "Oracle (Alinhado a direita)"));
		menu.add(presetItem(presets, SqlFormatter.Style.STANDARD, "Standard (Indentado por tab)"));
		menu.add(presetItem(presets, SqlFormatter.Style.COMMA_FIRST, "Commas First (Virgulas no inicio)"));

		menu.addSeparator();
		menu.add(formatMenuHeader("Configuracoes"));

		JCheckBoxMenuItem upper = new JCheckBoxMenuItem("Caixa alta para palavras-chave (SELECT, FROM...)",
				formatState.upperKeywords());
		upper.addActionListener(a -> {
			formatState = new FormatPreferences.State(formatState.style(), !formatState.upperKeywords(),
					formatState.indentJson(), formatState.editorFontFamily());
			saveFormatState();
		});
		menu.add(upper);

		JCheckBoxMenuItem json = new JCheckBoxMenuItem("Indentar funcoes JSON (JSON_OBJECT/JSON_ARRAY)",
				formatState.indentJson());
		json.addActionListener(a -> {
			formatState = new FormatPreferences.State(formatState.style(), formatState.upperKeywords(),
					!formatState.indentJson(), formatState.editorFontFamily());
			saveFormatState();
		});
		menu.add(json);

		menu.addSeparator();
		JMenuItem chooseFont = new JMenuItem("Escolher fonte do editor...");
		chooseFont.addActionListener(a -> chooseEditorFont());
		menu.add(chooseFont);

		return menu;
	}

	/** Cabecalho de secao do menu de formatacao — mesmo estilo de "OBJETOS"/"CONEXOES" do resto da IDE. */
	private JComponent formatMenuHeader(String text) {
		JLabel label = sectionHeader(text);
		label.setBorder(BorderFactory.createEmptyBorder(4, 12, 4, 12));
		return label;
	}

	/**
	 * Item de preset de formatacao: {@link JRadioButtonMenuItem} de verdade
	 * (marcador nativo do FlatLaf), nao mais um caractere unicode de check
	 * grudado no texto — em algumas fontes esse caractere nao existia e
	 * aparecia como um quadrado vazio ("tofu"). Radio button tambem descreve
	 * melhor a escolha: os tres presets sao mutuamente exclusivos.
	 */
	private JRadioButtonMenuItem presetItem(ButtonGroup group, SqlFormatter.Style style, String label) {
		JRadioButtonMenuItem item = new JRadioButtonMenuItem(label, formatState.style() == style);
		group.add(item);
		item.addActionListener(a -> {
			formatState = new FormatPreferences.State(style, formatState.upperKeywords(), formatState.indentJson(),
					formatState.editorFontFamily());
			saveFormatState();
			if (statusBar != null) {
				statusBar.setText(" Preset de formatacao: " + label);
			}
		});
		return item;
	}

	/**
	 * Abre o seletor de fonte do editor e aplica a escolha a todas as abas abertas.
	 */
	private void chooseEditorFont() {
		List<String> fonts = SqlEditorPane.availableEditorFonts();
		List<String> options = new ArrayList<>();
		options.add("Automatico (recomendado pelo sistema)");
		options.addAll(fonts);

		String current = formatState.editorFontFamily();
		int currentIdx = (current == null || current.isBlank()) ? 0 : options.indexOf(current);
		if (currentIdx < 0) {
			currentIdx = 0;
		}

		JComboBox<String> combo = new JComboBox<>(options.toArray(new String[0]));
		combo.setSelectedIndex(currentIdx);
		combo.setRenderer(new DefaultListCellRenderer() {
			private static final long serialVersionUID = 1L;

			@Override
			public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected,
					boolean cellHasFocus) {
				JLabel l = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
				if (value instanceof String fam && index >= 1) {
					l.setFont(new Font(fam, Font.PLAIN, 13));
					l.setText(fam + "   —   SELECT * FROM tabela;");
				}
				return l;
			}
		});

		JPanel panel = new JPanel(new BorderLayout(0, 8));
		panel.add(new JLabel("Fonte do editor SQL:"), BorderLayout.NORTH);
		panel.add(combo, BorderLayout.CENTER);
		panel.setPreferredSize(new Dimension(420, 70));

		int opt = JOptionPane.showConfirmDialog(this, panel, "Escolher fonte do editor", JOptionPane.OK_CANCEL_OPTION,
				JOptionPane.PLAIN_MESSAGE);
		if (opt != JOptionPane.OK_OPTION) {
			return;
		}
		int sel = combo.getSelectedIndex();
		String chosen = (sel <= 0) ? "" : options.get(sel);
		formatState = new FormatPreferences.State(formatState.style(), formatState.upperKeywords(),
				formatState.indentJson(), chosen);
		saveFormatState();
		applyEditorFontToOpenTabs();
		if (statusBar != null) {
			statusBar.setText(" Fonte do editor: " + (chosen.isEmpty() ? "automatica" : chosen));
		}
	}

	private void styleRunButton() {
		runButton.setBackground(ACCENT);
		runButton.setForeground(Color.WHITE);
	}

	/**
	 * Recolhe/expande o painel lateral, focando o editor (ciente do lado atual).
	 */
	private void toggleSidebar() {
		if (leftSide.isVisible()) {
			sidebarLoc = mainSplit.getDividerLocation();
			leftSide.setVisible(false);
			mainSplit.setDividerSize(0);
			mainSplit.setDividerLocation(sidebarOnRight ? mainSplit.getWidth() : 0);
		} else {
			leftSide.setVisible(true);
			mainSplit.setDividerSize(4);
			if (sidebarOnRight) {
				int total = mainSplit.getWidth();
				int loc = (sidebarLoc > 0 && sidebarLoc < total) ? sidebarLoc : (int) (Math.max(total, 800) * 0.78);
				mainSplit.setDividerLocation(loc);
			} else {
				mainSplit.setDividerLocation(sidebarLoc > 0 ? sidebarLoc : 248);
			}
		}
		mainSplit.revalidate();
		focusEditor();
	}

	/**
	 * Recolhe/expande a area de resultados (ciente da orientacao atual), focando o
	 * editor.
	 */
	private void toggleResults() {
		boolean horizontalSplit = centerSplit.getOrientation() == JSplitPane.VERTICAL_SPLIT;
		if (resultsArea.isVisible()) {
			resultsLoc = centerSplit.getDividerLocation();
			resultsArea.setVisible(false);
			centerSplit.setDividerSize(0);
			centerSplit.setDividerLocation(horizontalSplit ? centerSplit.getHeight() : centerSplit.getWidth());
		} else {
			resultsArea.setVisible(true);
			centerSplit.setDividerSize(4);
			if (resultsLoc > 0) {
				centerSplit.setDividerLocation(resultsLoc);
			} else {
				centerSplit.setResizeWeight(0.62);
				centerSplit.setDividerLocation(0.62);
			}
		}
		centerSplit.revalidate();
		focusEditor();
	}

	private void focusEditor() {
		SqlEditorPane editor = currentEditor();
		if (editor != null) {
			editor.textArea().requestFocusInWindow();
		}
	}

	/**
	 * Atalhos globais: Ctrl+B (lateral), Ctrl+J (resultados), Ctrl +/-/0 (zoom da
	 * UI).
	 */
	private void registerWindowShortcuts() {
		JComponent rp = getRootPane();
		rp.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("control B"), "toggle-sidebar");
		rp.getActionMap().put("toggle-sidebar", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e) {
				toggleSidebar();
			}
		});
		rp.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("control J"), "toggle-results");
		rp.getActionMap().put("toggle-results", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e) {
				toggleResults();
			}
		});
		// Zoom global da interface. Quando o foco esta no editor SQL, o proprio
		// editor trata essas teclas para seu zoom de fonte (WHEN_FOCUSED tem
		// prioridade sobre WHEN_IN_FOCUSED_WINDOW); fora do editor, e o zoom geral.
		bindGlobalAction(rp, "control EQUALS", "zoom-ui-in", this::zoomIn);
		bindGlobalAction(rp, "control PLUS", "zoom-ui-in2", this::zoomIn);
		bindGlobalAction(rp, "control ADD", "zoom-ui-in3", this::zoomIn);
		bindGlobalAction(rp, "control MINUS", "zoom-ui-out", this::zoomOut);
		bindGlobalAction(rp, "control SUBTRACT", "zoom-ui-out2", this::zoomOut);
		bindGlobalAction(rp, "control 0", "zoom-ui-reset", this::resetZoom);
		bindGlobalAction(rp, "control R", "refresh-objects", () -> refreshObjectTree(true));
	}

	private static void bindGlobalAction(JComponent rp, String keyStroke, String name, Runnable action) {
		rp.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(keyStroke), name);
		rp.getActionMap().put(name, new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e) {
				action.run();
			}
		});
	}

	// ---------- Inversao do painel lateral ----------

	/**
	 * Move o bloco "Conexoes/Objetos" para o outro lado, reconstruindo o split
	 * principal.
	 */
	private void toggleSidebarSide() {
		sidebarOnRight = !sidebarOnRight;
		remove(mainSplit);
		mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, sidebarOnRight ? centerSplit : leftSide,
				sidebarOnRight ? leftSide : centerSplit);
		mainSplit.setResizeWeight(sidebarOnRight ? 0.78 : 0.22);
		mainSplit.setBorder(BorderFactory.createEmptyBorder());
		add(mainSplit, BorderLayout.CENTER);
		sidebarLoc = -1;
		revalidate();
		repaint();
		focusEditor();
		saveUiState();
		if (statusBar != null) {
			statusBar.setText(" Painel lateral movido para a " + (sidebarOnRight ? "direita" : "esquerda") + ".");
		}
	}

	// ---------- Orientacao do painel de Resultados ----------

	/**
	 * Alterna entre Resultados embaixo do editor (horizontal) e ao lado (vertical).
	 */
	private void toggleResultsOrientation() {
		resultsVertical = !resultsVertical;
		centerSplit.setOrientation(resultsVertical ? JSplitPane.HORIZONTAL_SPLIT : JSplitPane.VERTICAL_SPLIT);
		centerSplit.setResizeWeight(0.62);
		resultsLoc = -1;
		centerSplit.setDividerLocation(0.62);
		centerSplit.revalidate();
		centerSplit.repaint();
		if (resultsOrientationButton != null) {
			updateOrientationToggleIcon(resultsOrientationButton);
		}
		focusEditor();
		saveUiState();
		if (statusBar != null) {
			statusBar.setText(" Resultados: layout "
					+ (resultsVertical ? "vertical (lado a lado)" : "horizontal (embaixo)") + ".");
		}
	}

	// ---------- Zoom global da interface ----------

	private void captureBaseFont() {
		if (baseDefaultFont == null) {
			Font f = UIManager.getFont("defaultFont");
			baseDefaultFont = (f != null) ? f : new Font(Font.SANS_SERIF, Font.PLAIN, 12);
		}
	}

	private static int clampZoomIndex(int index) {
		return Math.max(0, Math.min(ZOOM_LEVELS.length - 1, index));
	}

	private double currentScale() {
		return ZOOM_LEVELS[zoomIndex];
	}

	/**
	 * Tamanho em px escalado pelo zoom atual e, se ativo, pela densidade compacta.
	 */
	private int scaledPx(int basePx) {
		double v = basePx * currentScale();
		if (compactMode) {
			v *= 0.6; // modo compacto: ~40% de reducao adicional
		}
		return Math.max(1, (int) Math.round(v));
	}

	/**
	 * Aplica somente a fonte padrao (UIManager + FlatLaf.updateUI), sem reconstruir
	 * layout.
	 */
	private void applyZoomFont(int index) {
		zoomIndex = clampZoomIndex(index);
		captureBaseFont();
		float newSize = Math.round(baseDefaultFont.getSize2D() * (float) currentScale());
		UIManager.put("defaultFont", baseDefaultFont.deriveFont(newSize));
		FlatLaf.updateUI();
	}

	/**
	 * Define o nivel de zoom (0..ZOOM_LEVELS.length-1) e atualiza tudo que depende
	 * dele.
	 */
	private void setZoomIndex(int index) {
		applyZoomFont(index);
		refreshDynamicSizing();
		saveUiState();
		if (statusBar != null) {
			statusBar.setText(" Zoom da interface: " + Math.round(currentScale() * 100) + "%.");
		}
	}

	private void zoomIn() {
		setZoomIndex(zoomIndex + 1);
	}

	private void zoomOut() {
		setZoomIndex(zoomIndex - 1);
	}

	private void resetZoom() {
		setZoomIndex(UiPreferences.DEFAULT_ZOOM_INDEX);
	}

	// ---------- Modo compacto ----------

	private void toggleCompactMode() {
		compactMode = !compactMode;
		applyDensityToPanels();
		refreshDynamicSizing();
		saveUiState();
		if (statusBar != null) {
			statusBar.setText(compactMode ? " Modo compacto ativado." : " Modo compacto desativado.");
		}
	}

	/** Reaplica os paddings dos paineis principais conforme zoom/modo compacto. */
	private void applyDensityToPanels() {
		int outer = compactMode ? 5 : 8;
		if (objectBrowserPanel != null) {
			objectBrowserPanel.setBorder(BorderFactory.createEmptyBorder(outer, outer, outer, outer));
		}
		if (editorAreaPanel != null) {
			editorAreaPanel.setBorder(BorderFactory.createEmptyBorder(0, outer, compactMode ? 2 : 4, outer));
		}
		if (resultsArea != null) {
			resultsArea.setBorder(BorderFactory.createEmptyBorder(compactMode ? 2 : 4, outer, outer, outer));
		}
		if (toolbarBar != null) {
			int v = compactMode ? 4 : 8;
			int h = compactMode ? 8 : 12;
			toolbarBar.setBorder(BorderFactory.createEmptyBorder(v, h, v, h));
		}
		if (footerBar != null) {
			int v = compactMode ? 3 : 5;
			footerBar.setBorder(BorderFactory.createEmptyBorder(v, 12, v, 12));
		}
		revalidate();
		repaint();
	}

	/**
	 * Reaplica os tamanhos derivados do zoom/modo compacto a componentes ja
	 * construidos (linhas da arvore, cartoes de conexao, grade de resultados).
	 */
	private void refreshDynamicSizing() {
		if (objectTree != null) {
			objectTree.setRowHeight(scaledPx(22));
		}
		if (connectionsPanel != null) {
			connectionsPanel.setRowHeight(scaledPx(54));
		}
		// Reconstroi as abas de resultado (tabela, gutter e cabecalho usam
		// tamanhos fixos definidos na hora da criacao do JTable).
		if (resultTabs != null && resultTabs.getTabCount() > 0) {
			showResults(lastResults);
		}
		if (mainSplit != null) {
			mainSplit.revalidate();
			mainSplit.repaint();
		}
	}

	private void saveUiState() {
		try {
			uiPrefsStore.save(new UiPreferences.State(sidebarOnRight, resultsVertical, zoomIndex, compactMode));
		} catch (Exception ex) {
			AppLogger.warning("Falha ao salvar preferencias de UI", ex);
			if (statusBar != null) {
				statusBar.setText(" Aviso: nao foi possivel salvar as preferencias de UI: " + ex.getMessage());
			}
		}
	}

	private JComponent buildFooter() {
		connStatusLabel = new JLabel();
		connStatusLabel.setIconTextGap(6);
		connStatusLabel.setFont(connStatusLabel.getFont().deriveFont(Font.BOLD));

		statusBar = new JLabel(" Pronto");
		statusBar.setForeground(MUTED);

		connProgress = new JProgressBar();
		connProgress.setIndeterminate(true);
		connProgress.setPreferredSize(new Dimension(120, 6));
		connProgress.setVisible(false);

		JLabel brand = new JLabel("Nureal");
		brand.setFont(brand.getFont().deriveFont(Font.BOLD));
		brand.setForeground(ACCENT);

		JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
		left.setOpaque(false);
		left.add(connStatusLabel);
		left.add(statusBar);

		JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
		right.setOpaque(false);
		right.add(connProgress);
		right.add(brand);

		JPanel footer = new JPanel(new BorderLayout());
		footer.setBorder(BorderFactory.createEmptyBorder(5, 12, 5, 12));
		footer.add(left, BorderLayout.WEST);
		footer.add(right, BorderLayout.EAST);

		setDisconnectedState();
		return footer;
	}

	// ---------- Estado da conexao (rodape) ----------

	private void setDisconnectedState() {
		connStatusLabel.setIcon(Icons.get(IconType.STATUS_DOT, 10, new Color(0xDC2626)));
		connStatusLabel.setText("Desconectado");
		connStatusLabel.setForeground(new Color(0xB91C1C));
		connProgress.setVisible(false);
	}

	private void setConnectingState(String name) {
		connStatusLabel.setIcon(Icons.get(IconType.STATUS_DOT, 10, new Color(0xF59E0B)));
		connStatusLabel.setText("Conectando a " + name + "...");
		connStatusLabel.setForeground(new Color(0xB45309));
		connProgress.setVisible(true);
	}

	private void setConnectedState(String label) {
		connStatusLabel.setIcon(Icons.get(IconType.STATUS_DOT, 10, ACCENT));
		connStatusLabel.setText("Conectado: " + label);
		connStatusLabel.setForeground(new Color(0x047857));
		connProgress.setVisible(false);
	}

	// ---------- Lado esquerdo ----------

	private JComponent buildLeftSide() {
		connectionsPanel = new ConnectionsPanel(connectionStore, this::connectTo, this::disconnectFrom);
		connectionsPanel.setRowHeight(scaledPx(54));
		JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, connectionsPanel, buildObjectBrowser());
		split.setResizeWeight(0.5);
		split.setBorder(BorderFactory.createEmptyBorder());
		split.setPreferredSize(new Dimension(248, 100));
		return split;
	}

	private JComponent buildObjectBrowser() {
		objectTree = new JTree(new DefaultTreeModel(new DefaultMutableTreeNode("Sem conexao")));
		objectTree.setRootVisible(true);
		// false: some SO o "punho" (triangulo) de expandir/recolher da RAIZ —
		// os filhos (categorias, tabelas, colunas) continuam com o triangulo
		// normal. Na raiz, o lugar do triangulo passa a ser a bolinha de
		// status da conexao (ver ObjectTreeCellRenderer). Expandir/recolher a
		// raiz continua funcionando por duplo-clique (comportamento nativo do
		// JTree, independente do triangulo estar visivel).
		objectTree.setShowsRootHandles(false);
		// FlatLaf pinta por conta propria uma selecao "wide" (linha inteira,
		// verde generico do L&F, sem nocao de categoria) por cima de
		// qualquer coisa que o renderer desenhe. Desligando aqui: quem manda
		// no fundo de cada linha (categoria OU selecao) e 100% o
		// ObjectTreeCellRenderer (ver seu javadoc — ele proprio se estica
		// para cobrir a linha inteira, sem depender de nada fora dele).
		objectTree.putClientProperty("JTree.paintSelection", false);
		objectTree.setRowHeight(scaledPx(22));
		objectTree.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 4));
		objectTree.setCellRenderer(new ObjectTreeCellRenderer());
		objectTree.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				// Setinha de "trocar esquema" na ponta direita da linha do
				// schema (raiz) — ver ObjectTreeCellRenderer#paintComponent.
				// So no clique simples, antes de qualquer outra coisa: um
				// duplo-clique ali nao deve contar como dois acionamentos.
				if (e.getClickCount() == 1 && isSchemaSwitchArrowClick(e)) {
					switchSchema();
					return;
				}
				// So SCHEMA_PICK reage a duplo clique aqui — objetos
				// abriveis (tabela/view/...) deixam o duplo clique 100%
				// livre para o expand/recolher nativo do JTree. "Abrir
				// propriedades" desses objetos e via clique direito (ver
				// maybeShowObjectContextMenu abaixo).
				if (e.getClickCount() == 2) {
					openSelectedObjectProperties();
				}
			}

			@Override
			public void mousePressed(MouseEvent e) {
				maybeShowObjectContextMenu(e);
			}

			@Override
			public void mouseReleased(MouseEvent e) {
				maybeShowObjectContextMenu(e);
			}
		});
		// Ctrl+C copia o(s) nome(s) da(s) linha(s) selecionada(s) — a arvore
		// nao e um campo de texto, entao nao tem selecao de CARACTERES, mas
		// selecionar uma ou mais linhas e copiar o nome delas e um pedido
		// razoavel (e explicito) do usuario.
		objectTree.getInputMap(JComponent.WHEN_FOCUSED).put(
				KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.CTRL_DOWN_MASK), "copy-object-name");
		objectTree.getActionMap().put("copy-object-name", new AbstractAction() {
			private static final long serialVersionUID = 1L;

			@Override
			public void actionPerformed(ActionEvent e) {
				copySelectedObjectNames();
			}
		});

		JScrollPane sp = new JScrollPane(objectTree);
		sp.setBorder(BorderFactory.createEmptyBorder());

		objectSearch = new JTextField();
		objectSearch.putClientProperty("JTextField.placeholderText", "Buscar objeto...");
		objectSearch.putClientProperty("JTextField.showClearButton", true);
		objectSearch.setEnabled(false);
		objectSearch.getDocument().addDocumentListener(new DocumentListener() {
			@Override
			public void insertUpdate(DocumentEvent e) {
				applyObjectFilter();
			}

			@Override
			public void removeUpdate(DocumentEvent e) {
				applyObjectFilter();
			}

			@Override
			public void changedUpdate(DocumentEvent e) {
				applyObjectFilter();
			}
		});

		JButton switchSchemaButton = new JButton(Icons.get(IconType.DATABASE, 13, MUTED));
		switchSchemaButton.setBorderPainted(false);
		switchSchemaButton.setContentAreaFilled(false);
		switchSchemaButton.setToolTipText("Trocar esquema / ver todos os esquemas");
		switchSchemaButton.addActionListener(e -> switchSchema());

		JButton refreshObjectsButton = new JButton(Icons.get(IconType.REFRESH, 13, MUTED));
		refreshObjectsButton.setBorderPainted(false);
		refreshObjectsButton.setContentAreaFilled(false);
		refreshObjectsButton.setToolTipText("Atualizar objetos (Ctrl+R)");
		refreshObjectsButton.addActionListener(e -> refreshObjectTree(true));

		JPanel headerButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
		headerButtons.setOpaque(false);
		headerButtons.add(switchSchemaButton);
		headerButtons.add(refreshObjectsButton);

		JPanel headerRow = new JPanel(new BorderLayout());
		headerRow.setOpaque(false);
		headerRow.add(sectionHeader("OBJETOS"), BorderLayout.WEST);
		headerRow.add(headerButtons, BorderLayout.EAST);

		JPanel top = new JPanel(new BorderLayout(0, 8));
		top.setOpaque(false);
		top.add(headerRow, BorderLayout.NORTH);
		top.add(objectSearch, BorderLayout.SOUTH);

		JPanel panel = new JPanel(new BorderLayout(0, 8));
		panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
		panel.add(top, BorderLayout.NORTH);
		panel.add(sp, BorderLayout.CENTER);
		objectBrowserPanel = panel;
		return panel;
	}

	// ---------- Editor (abas) ----------

	private JComponent buildEditorArea() {
		editorTabs = new JTabbedPane();
		// Sem isto, o FlatLaf reserva um respiro antes da primeira aba (a area
		// de abas tem um inset esquerdo proprio, independente do painel que a
		// contem) — a primeira aba ficava alguns pixels mais a direita que o
		// botao "Executar" da barra logo acima, mesmo os dois partindo de
		// x=0 no layout. Zerar o inset alinha a aba com a barra de ferramentas.
		editorTabs.putClientProperty("JTabbedPane.tabAreaInsets", new Insets(0, 0, 0, 0));
		editorTabs.putClientProperty("JTabbedPane.tabClosable", true);
		editorTabs.putClientProperty("JTabbedPane.tabCloseCallback",
				(BiConsumer<JTabbedPane, Integer>) (k, index) -> closeQueryTab(index));
		// Selecionar a aba "+" abre uma nova query; qualquer outra troca salva a
		// sessao E redesenha RESULTADOS com o que essa aba tinha da ultima
		// vez (cada aba de SQL tem seus proprios resultados — ver
		// resultsByTab/showResultsForActiveEditor).
		editorTabs.addChangeListener(e -> {
			if (addingTab) {
				return; // evita reentrancia: insertTab desloca a selecao da aba "+"
			}
			if (plusTab != null && editorTabs.getSelectedComponent() == plusTab) {
				if (!addQueryTab()) {
					selectLastRealTab();
				}
			} else {
				scheduleSave();
				showResultsForActiveEditor();
			}
		});
		// Botao direito no titulo da aba: fechar / fechar as outras.
		editorTabs.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				maybeTabMenu(e);
			}

			@Override
			public void mouseReleased(MouseEvent e) {
				maybeTabMenu(e);
			}
		});

		// Inicializa o workspace "sem conexao" com as abas salvas (+ aba "+").
		initWorkspaces();

		JPanel panel = new JPanel(new BorderLayout());
		// CORREÇÃO: Removemos o 8 da esquerda e da direita para alinhar perfeitamente
		// com a quina das conexões
		panel.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));

		panel.add(buildToolbar(), BorderLayout.NORTH);
		panel.add(editorTabs, BorderLayout.CENTER);
		return panel;
	}

	private boolean addQueryTab() {
		return addQueryTab(nextQueryTitle(), "");
	}

	/**
	 * Menor "SQL Query N" ainda nao usado pelas abas abertas (reaproveita gaps).
	 */
	private String nextQueryTitle() {
		int n = 1;
		while (titleExists("SQL Query " + n)) {
			n++;
		}
		return "SQL Query " + n;
	}

	private boolean titleExists(String title) {
		for (int i = 0; i < editorTabs.getTabCount(); i++) {
			if (editorTabs.getComponentAt(i) != plusTab && title.equals(editorTabs.getTitleAt(i))) {
				return true;
			}
		}
		return false;
	}

	private boolean addQueryTab(String title, String sql) {
		if (realTabCount() >= MAX_TABS) {
			if (statusBar != null) {
				statusBar.setText(" Limite de " + MAX_TABS + " abas atingido.");
			}
			return false;
		}
		SqlEditorPane pane = new SqlEditorPane(completionProvider, this::onRun, this::currentSqlFormatter,
				formatState.editorFontFamily());
		pane.textArea().setText(sql);
		pane.textArea().setCaretPosition(0);
		pane.textArea().getDocument().addDocumentListener(new DocumentListener() {
			@Override
			public void insertUpdate(DocumentEvent e) {
				scheduleSave();
			}

			@Override
			public void removeUpdate(DocumentEvent e) {
				scheduleSave();
			}

			@Override
			public void changedUpdate(DocumentEvent e) {
				scheduleSave();
			}
		});
		addingTab = true;
		try {
			// insere ANTES da aba "+", para que ela continue sendo a ultima
			int at = (plusTab != null) ? editorTabs.indexOfComponent(plusTab) : editorTabs.getTabCount();
			editorTabs.insertTab(title, null, pane, null, at);
			editorTabs.setSelectedComponent(pane);
		} finally {
			addingTab = false;
		}
		scheduleSave();
		return true;
	}

	/** Numero de abas reais (exclui a aba "+"). */
	private int realTabCount() {
		return editorTabs.getTabCount() - (plusTab != null ? 1 : 0);
	}

	private void selectLastRealTab() {
		for (int i = editorTabs.getTabCount() - 1; i >= 0; i--) {
			if (editorTabs.getComponentAt(i) != plusTab) {
				editorTabs.setSelectedIndex(i);
				return;
			}
		}
	}

	/** Menu de contexto do titulo da aba (botao direito). */
	private void maybeTabMenu(MouseEvent e) {
		if (!e.isPopupTrigger()) {
			return;
		}
		int idx = editorTabs.indexAtLocation(e.getX(), e.getY());
		if (idx < 0) {
			return;
		}
		final Component target = editorTabs.getComponentAt(idx);
		if (target == plusTab) {
			return;
		}
		JPopupMenu menu = new JPopupMenu();
		JMenuItem rename = new JMenuItem("Renomear...");
		rename.addActionListener(a -> renameTab(target));
		JMenuItem close = new JMenuItem("Fechar");
		close.addActionListener(a -> closeTabComponent(target));
		JMenuItem closeOthers = new JMenuItem("Fechar as outras");
		closeOthers.addActionListener(a -> closeOtherTabs(target));
		menu.add(rename);
		menu.addSeparator();
		menu.add(close);
		menu.add(closeOthers);
		menu.show(editorTabs, e.getX(), e.getY());
	}

	private void renameTab(Component target) {
		int i = editorTabs.indexOfComponent(target);
		if (i < 0) {
			return;
		}
		String current = editorTabs.getTitleAt(i);
		String name = JOptionPane.showInputDialog(this, "Novo nome da aba:", current);
		if (name != null && !name.trim().isEmpty()) {
			editorTabs.setTitleAt(i, name.trim());
			scheduleSave();
		}
	}

	private void closeTabComponent(Component c) {
		int i = editorTabs.indexOfComponent(c);
		if (i >= 0) {
			closeQueryTab(i);
		}
	}

	private void closeOtherTabs(Component keep) {
		// Seleciona a aba a manter antes de remover as demais (evita cair na "+").
		int keepIdx = editorTabs.indexOfComponent(keep);
		if (keepIdx >= 0) {
			editorTabs.setSelectedIndex(keepIdx);
		}
		for (int i = editorTabs.getTabCount() - 1; i >= 0; i--) {
			Component c = editorTabs.getComponentAt(i);
			if (c == plusTab || c == keep) {
				continue;
			}
			editorTabs.removeTabAt(i);
		}
		scheduleSave();
	}

	/** Adiciona a aba "+" (conteudo vazio, pequena e nao fechavel) ao final. */
	private void addPlusTab() {
		JPanel dummy = new JPanel();
		dummy.putClientProperty("JTabbedPane.tabClosable", false);
		plusTab = dummy;
		editorTabs.addTab("+", dummy);
		editorTabs.setToolTipTextAt(editorTabs.indexOfComponent(dummy), "Nova query");
	}

	private void closeQueryTab(int index) {
		Component target = editorTabs.getComponentAt(index);
		if (target == plusTab) {
			return;
		}
		if (realTabCount() <= 1) {
			return;
		}
		// Se a aba a fechar e a selecionada, selecione antes uma aba real vizinha,
		// para que a remocao nao caia na aba "+" (o que abriria uma nova query).
		if (editorTabs.getSelectedIndex() == index) {
			int neighbor = findAdjacentRealTab(index);
			if (neighbor >= 0) {
				editorTabs.setSelectedIndex(neighbor);
			}
		}
		editorTabs.removeTabAt(index);
		// Os resultados dessa aba morrem com ela — nao fazem mais sentido
		// sem a aba de SQL que os gerou (ver resultsByTab).
		if (target instanceof SqlEditorPane sep) {
			resultsByTab.remove(sep);
		}
		scheduleSave();
	}

	private int findAdjacentRealTab(int index) {
		for (int i = index - 1; i >= 0; i--) {
			if (editorTabs.getComponentAt(i) != plusTab) {
				return i;
			}
		}
		for (int i = index + 1; i < editorTabs.getTabCount(); i++) {
			if (editorTabs.getComponentAt(i) != plusTab) {
				return i;
			}
		}
		return -1;
	}

	// ---------- Persistencia da sessao (nunca perder trabalho) ----------

	/** Inicializa o workspace "sem conexao" com as abas salvas e monta o editor. */
	private void initWorkspaces() {
		try {
			savedSessions = sessionStore.load();
		} catch (Exception ex) {
			AppLogger.warning("Falha ao carregar sessoes salvas; iniciando vazio", ex);
			savedSessions = new LinkedHashMap<>();
		}
		Workspace scratch = new Workspace(SCRATCH, null, connectionManager);
		SessionStore.Session sc = savedSessions.get(SCRATCH);
		if (sc != null) {
			scratch.tabs = new ArrayList<>(sc.tabs());
			scratch.selectedTab = sc.selectedIndex();
		}
		workspaces.put(SCRATCH, scratch);
		activeWorkspace = scratch;
		rebuildEditorTabs(scratch.tabs, scratch.selectedTab, scratch.tabResults);
	}

	/**
	 * Reconstroi as abas do editor a partir do conteudo salvo (titulo + SQL)
	 * e restaura os resultados de cada uma, se houver (ver
	 * {@code Workspace#tabResults}) — usado tanto na inicializacao quanto ao
	 * trocar de workspace/conexao (ver {@code activateWorkspace}): trocar de
	 * conexao e voltar tem que devolver os resultados que cada aba tinha
	 * antes da troca, nao uma tela vazia.
	 */
	private void rebuildEditorTabs(List<SessionStore.Tab> tabs, int selected, Map<Integer, List<QueryResult>> savedResults) {
		// As abas antigas (de outro workspace/conexao, ou recarregadas do
		// disco) vao ser descartadas e substituidas por instancias NOVAS de
		// SqlEditorPane (mesmo titulo/SQL, objeto diferente) — os resultados
		// guardados para as antigas (ver resultsByTab) nunca mais seriam
		// encontrados por uma instancia nova, entao ficariam so ocupando
		// memoria a toa. Ja foram (ou nao) salvos no workspace de origem por
		// saveActiveTabs ANTES desta chamada — aqui e so limpeza da chave
		// antiga, mesma regra de "resultado morre com a aba" de closeQueryTab.
		for (int i = 0; i < editorTabs.getTabCount(); i++) {
			Component c = editorTabs.getComponentAt(i);
			if (c instanceof SqlEditorPane sep) {
				resultsByTab.remove(sep);
			}
		}
		editorTabs.removeAll();
		plusTab = null;
		if (tabs == null || tabs.isEmpty()) {
			addQueryTab();
		} else {
			for (SessionStore.Tab t : tabs) {
				String title = (t.title() == null || t.title().isBlank()) ? nextQueryTitle() : t.title();
				addQueryTab(title, t.sql());
			}
		}
		addPlusTab();
		// Restaura os resultados salvos (indexados por POSICAO da aba, ver
		// Workspace#tabResults) nas instancias NOVAS de SqlEditorPane recem
		// criadas acima — tem que ser DEPOIS de cria-las (para termos as
		// instancias certas como chave) e ANTES do showResultsForActiveEditor
		// no final (para ele ja encontrar o resultado, se houver).
		if (savedResults != null && !savedResults.isEmpty()) {
			int tabIndex = 0;
			for (int i = 0; i < editorTabs.getTabCount(); i++) {
				Component c = editorTabs.getComponentAt(i);
				if (c == plusTab) {
					continue;
				}
				if (c instanceof SqlEditorPane sep) {
					List<QueryResult> saved = savedResults.get(tabIndex);
					if (saved != null) {
						resultsByTab.put(sep, saved);
					}
				}
				tabIndex++;
			}
		}
		if (selected >= 0 && selected < editorTabs.getTabCount() && editorTabs.getComponentAt(selected) != plusTab) {
			editorTabs.setSelectedIndex(selected);
		}
		// Chamada explicita (nao so via ChangeListener): se o indice
		// selecionado no fim da reconstrucao for igual ao que ja estava
		// selecionado, o JTabbedPane nao dispara ChangeEvent nenhum, e o
		// painel de RESULTADOS ficaria mostrando o estado da aba antiga (de
		// outro workspace/sessao) por engano.
		showResultsForActiveEditor();
	}

	/** Captura o conteudo atual das abas do editor (titulo + SQL). */
	private List<SessionStore.Tab> collectTabs() {
		List<SessionStore.Tab> list = new ArrayList<>();
		for (int i = 0; i < editorTabs.getTabCount(); i++) {
			Component c = editorTabs.getComponentAt(i);
			if (c instanceof SqlEditorPane sep) {
				list.add(new SessionStore.Tab(editorTabs.getTitleAt(i), sep.textArea().getText()));
			}
		}
		return list;
	}

	/** Salva as abas atuais do editor (SQL + resultados) no workspace ativo. */
	private void saveActiveTabs() {
		if (activeWorkspace != null && editorTabs != null) {
			activeWorkspace.tabs = collectTabs();
			activeWorkspace.selectedTab = Math.max(editorTabs.getSelectedIndex(), 0);
			activeWorkspace.tabResults = snapshotTabResults();
		}
	}

	/**
	 * Guarda os resultados ATUAIS de cada aba de SQL (ver resultsByTab),
	 * indexados por POSICAO (nao pela instancia de SqlEditorPane — ver
	 * javadoc de {@code Workspace#tabResults}). Chamado ao SAIR de um
	 * workspace (ver {@code saveActiveTabs}), para que
	 * {@code rebuildEditorTabs} consiga devolve-los quando o usuario voltar.
	 */
	private Map<Integer, List<QueryResult>> snapshotTabResults() {
		Map<Integer, List<QueryResult>> snapshot = new HashMap<>();
		int tabIndex = 0;
		for (int i = 0; i < editorTabs.getTabCount(); i++) {
			Component c = editorTabs.getComponentAt(i);
			if (c == plusTab) {
				continue;
			}
			if (c instanceof SqlEditorPane sep) {
				List<QueryResult> results = resultsByTab.get(sep);
				if (results != null) {
					snapshot.put(tabIndex, results);
				}
			}
			tabIndex++;
		}
		return snapshot;
	}

	/**
	 * Ativa um workspace: guarda as abas do ativo (SQL + resultados),
	 * troca a conexao corrente, reconstroi as abas do alvo (restaurando os
	 * resultados que ele tinha da ultima vez que esteve ativo) e atualiza
	 * navegador/autocomplete/indicadores.
	 */
	private void activateWorkspace(Workspace w) {
		saveActiveTabs();
		activeWorkspace = w;
		connectionManager = w.mgr;
		rebuildEditorTabs(w.tabs, w.selectedTab, w.tabResults);
		if (w.schema != null) {
			metadataCache.set(w.schema);
			completionProvider.refresh(w.schema);
			populateTree(w.schema);
		} else if (w.schemaList != null) {
			completionProvider.refresh(null);
			buildSchemaPicker(w.schemaList);
		} else {
			currentSchema = null;
			completionProvider.refresh(null);
			objectSearch.setEnabled(false);
			String label = (w.profile == null) ? "Sem conexao"
					: (w.mgr.isConnected() ? "Selecione um esquema" : "Desconectado");
			objectTree.setModel(new DefaultTreeModel(new DefaultMutableTreeNode(label)));
		}
		refreshConnectionIndicators();
		runButton.setEnabled(w.mgr.isConnected());
		focusEditor();
	}

	/** Atualiza as bolinhas (conectados) e o indicador de status do rodape. */
	private void refreshConnectionIndicators() {
		Set<String> connected = new HashSet<>();
		for (Workspace w : workspaces.values()) {
			if (w.profile != null && w.mgr.isConnected()) {
				connected.add(w.name);
			}
		}
		connectionsPanel.setConnectedNames(connected);
		if (activeWorkspace != null && activeWorkspace.profile != null && activeWorkspace.mgr.isConnected()) {
			setConnectedState(activeWorkspace.profile.label());
		} else {
			setDisconnectedState();
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

	/** Grava agora as abas de TODAS as conexoes (workspaces) no disco. */
	private void saveSession() {
		if (editorTabs == null) {
			return;
		}
		saveActiveTabs();
		Map<String, SessionStore.Session> sessions = new LinkedHashMap<>();
		for (Workspace w : workspaces.values()) {
			sessions.put(w.name, new SessionStore.Session(new ArrayList<>(w.tabs), w.selectedTab));
		}
		try {
			sessionStore.save(sessions);
		} catch (Exception ex) {
			AppLogger.warning("Falha ao salvar a sessao", ex);
			if (statusBar != null) {
				statusBar.setText(" Aviso: nao foi possivel salvar a sessao: " + ex.getMessage());
			}
		}
	}

	private SqlEditorPane currentEditor() {
		Component c = editorTabs.getSelectedComponent();
		return (c instanceof SqlEditorPane sep) ? sep : null;
	}

	/**
	 * Redesenha o painel de RESULTADOS com o que a aba de SQL atualmente
	 * selecionada tinha da ULTIMA vez que rodou algo (ver
	 * {@code resultsByTab}, preenchido em {@code onRun}) — nunca o que outra
	 * aba rodou. Aba que ainda nao rodou nada nesta sessao: estado vazio.
	 * Chamado sempre que a selecao de {@code editorTabs} muda para uma aba
	 * real (ver {@code buildEditorArea}).
	 */
	private void showResultsForActiveEditor() {
		SqlEditorPane editor = currentEditor();
		List<QueryResult> results = (editor == null) ? null : resultsByTab.get(editor);
		if (results == null) {
			lastResults = new ArrayList<>();
			resultTabs.removeAll();
			showEmptyState();
			return;
		}
		showResults(results);
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

		JButton orientationToggle = new JButton();
		orientationToggle.setBorderPainted(false);
		orientationToggle.setContentAreaFilled(false);
		orientationToggle.addActionListener(e -> toggleResultsOrientation());
		updateOrientationToggleIcon(orientationToggle);
		this.resultsOrientationButton = orientationToggle;

		JPanel header = new JPanel(new BorderLayout());
		header.setOpaque(false);
		header.add(sectionHeader("RESULTADOS"), BorderLayout.WEST);
		header.add(orientationToggle, BorderLayout.EAST);

		JPanel panel = new JPanel(new BorderLayout(0, 8));
		panel.setBorder(BorderFactory.createEmptyBorder(4, 8, 8, 8));
		panel.add(header, BorderLayout.NORTH);
		panel.add(overlayStack(resultsCards), BorderLayout.CENTER);
		return panel;
	}

	/**
	 * Atualiza icone/tooltip do botao de orientacao dos resultados conforme o
	 * estado atual.
	 */
	private void updateOrientationToggleIcon(JButton button) {
		button.setIcon(resultsVertical ? Icons.get(IconType.PANEL_LEFT, 14, MUTED)
				: Icons.get(IconType.PANEL_BOTTOM, 14, MUTED));
		button.setToolTipText(resultsVertical ? "Mudar para resultados embaixo do editor (horizontal)"
				: "Mudar para resultados ao lado do editor (vertical)");
	}

	/** Empilha o conteudo dos resultados e um overlay de "carregando" por cima. */
	private JComponent overlayStack(JComponent content) {
		resultsOverlay = buildResultsOverlay();
		JPanel stack = new JPanel(null) {
			private static final long serialVersionUID = 1L;

			@Override
			public void doLayout() {
				for (Component c : getComponents()) {
					c.setBounds(0, 0, getWidth(), getHeight());
				}
			}
		};
		stack.add(resultsOverlay);
		stack.add(content);
		stack.setComponentZOrder(resultsOverlay, 0); // overlay no topo
		return stack;
	}

	/** Camada translucida com spinner e botao Cancelar, escondida por padrao. */
	private JComponent buildResultsOverlay() {
		JLabel label = new JLabel("Executando consulta...");
		label.setAlignmentX(Component.CENTER_ALIGNMENT);
		label.setFont(label.getFont().deriveFont(Font.BOLD, 13f));

		JProgressBar spinner = new JProgressBar();
		spinner.setIndeterminate(true);
		spinner.setPreferredSize(new Dimension(200, 6));
		spinner.setMaximumSize(new Dimension(200, 6));
		spinner.setAlignmentX(Component.CENTER_ALIGNMENT);

		JButton cancel = new JButton("Cancelar");
		cancel.setAlignmentX(Component.CENTER_ALIGNMENT);
		cancel.addActionListener(e -> cancelExecution());

		JPanel card = new JPanel();
		card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
		card.setBackground(new Color(0xFFFFFF));
		card.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(0xE0E3E7)),
				BorderFactory.createEmptyBorder(18, 28, 18, 28)));
		card.add(label);
		card.add(Box.createVerticalStrut(12));
		card.add(spinner);
		card.add(Box.createVerticalStrut(14));
		card.add(cancel);

		JPanel overlay = new JPanel(new GridBagLayout()) {
			private static final long serialVersionUID = 1L;

			@Override
			protected void paintComponent(Graphics g) {
				g.setColor(new Color(244, 245, 247, 205)); // dim translucido
				g.fillRect(0, 0, getWidth(), getHeight());
				super.paintComponent(g);
			}
		};
		overlay.setOpaque(false);
		overlay.add(card);
		// bloqueia interacao com os resultados por tras
		overlay.addMouseListener(new MouseAdapter() {
		});
		overlay.setVisible(false);
		return overlay;
	}

	private void showExecuting(boolean executing) {
		if (resultsOverlay != null) {
			resultsOverlay.setVisible(executing);
			resultsOverlay.repaint();
		}
	}

	/** Cancela de fato a instrucao em execucao (Statement.cancel) e o worker. */
	private void cancelExecution() {
		statusBar.setText(" Cancelando execucao...");
		Statement st = runningStatement;
		if (st != null) {
			// roda em outra thread: nao pode bloquear a EDT esperando o KILL QUERY
			new Thread(() -> {
				try {
					st.cancel();
				} catch (SQLException ignore) {
					// ignora
				}
			}, "cancel-query").start();
		}
		if (runWorker != null) {
			runWorker.cancel(true);
		}
	}

	private JComponent buildEmptyState() {
		JLabel icon = new JLabel(Icons.get(IconType.TABLE, 46, new Color(0xCBD5E1)));
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
		themeButton
				.setIcon(dark ? Icons.get(IconType.THEME_LIGHT, 16, MUTED) : Icons.get(IconType.THEME_DARK, 16, MUTED));
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

		// Ja conectado a essa conexao? Apenas ativa o workspace dela.
		Workspace existing = workspaces.get(target.name());
		if (existing != null && existing.mgr.isConnected()) {
			activateWorkspace(existing);
			statusBar.setText(" Workspace: " + target.name());
			return;
		}

		setConnectingState(target.name());
		connectionsPanel.setConnecting(target);
		runButton.setEnabled(false);
		statusBar.setText(" Conectando a " + target.host() + "...");

		final boolean pickSchema = target.schema() == null || target.schema().isBlank();
		final Workspace ws = (existing != null) ? existing
				: new Workspace(target.name(), target, new ConnectionManager(dialect));

		new SwingWorker<Object, Void>() {
			@Override
			protected Object doInBackground() throws Exception {
				ws.mgr.open(target);
				Connection conn = ws.mgr.getConnection();
				if (pickSchema) {
					return metadataService.listSchemas(conn); // List<String>
				}
				return metadataService.loadSchema(conn, target.schema()); // SchemaInfo
			}

			@Override
			protected void done() {
				try {
					Object result = get();
					if (existing == null) {
						SessionStore.Session saved = savedSessions.get(target.name());
						if (saved != null) {
							ws.tabs = new ArrayList<>(saved.tabs());
							ws.selectedTab = saved.selectedIndex();
						}
						workspaces.put(target.name(), ws);
					}
					if (pickSchema) {
						@SuppressWarnings("unchecked")
						List<String> schemas = (List<String>) result;
						ws.schemaList = schemas;
						ws.schema = null;
					} else {
						ws.schema = (SchemaInfo) result;
						ws.schemaList = null;
					}
					activateWorkspace(ws);
					setTitle("Nureal Database IDE - " + target.name());
					if (pickSchema) {
						statusBar.setText(
								" Conectado  (" + ((List<?>) result).size() + " esquema(s) - duplo-clique para abrir)");
					} else {
						statusBar.setText(" Conectado  (" + ws.schema.tables().size() + " tabelas)");
					}
				} catch (Exception ex) {
					connectionsPanel.setConnecting(null);
					refreshConnectionIndicators();
					showError("Falha ao conectar", ex);
					statusBar.setText(" Falha ao conectar");
				}
			}
		}.execute();
	}

	/**
	 * Desconecta explicitamente (menu de contexto do ConnectionsPanel). Fecha
	 * a conexao JDBC mas MANTEM o workspace — as abas de SQL abertas continuam
	 * la, so sem conexao ativa (mesma logica do workspace "sem conexao" ja
	 * usada pelo SCRATCH). Para reconectar, e so clicar/dar duplo-clique na
	 * conexao de novo — se ela permitir varios esquemas, volta a perguntar
	 * qual abrir.
	 */
	private void disconnectFrom(ConnectionProfile profile) {
		Workspace w = workspaces.get(profile.name());
		if (w == null || !w.mgr.isConnected()) {
			return;
		}
		int ok = JOptionPane.showConfirmDialog(this,
				"Desconectar de \"" + profile.name() + "\"?",
				"Desconectar", JOptionPane.YES_NO_OPTION);
		if (ok != JOptionPane.YES_OPTION) {
			return;
		}
		if (activeWorkspace == w) {
			closeOpenCursors();
		}
		w.mgr.close();
		w.schema = null;
		w.schemaList = null;
		if (activeWorkspace == w) {
			currentSchema = null;
			activateWorkspace(w);
		}
		refreshConnectionIndicators();
		statusBar.setText(" Desconectado de " + profile.name() + ".");
	}

	/** Monta a arvore com a lista de esquemas (duplo-clique abre o esquema). */
	private void buildSchemaPicker(List<String> schemas) {
		currentSchema = null;
		objectSearch.setEnabled(false);
		objectSearch.setText("");
		DefaultMutableTreeNode root = new DefaultMutableTreeNode(
				new ObjNode(NodeType.SCHEMA, "Esquemas", "Esquemas", null, null, null));
		for (String s : schemas) {
			root.add(new DefaultMutableTreeNode(new ObjNode(NodeType.SCHEMA_PICK, s, s, null, null, null)));
		}
		objectTree.setModel(new DefaultTreeModel(root));
		objectTree.expandPath(new TreePath(root.getPath()));
	}

	/**
	 * Abre um esquema escolhido na lista: define como banco padrao e carrega
	 * objetos.
	 */
	private void openSchema(String schemaName) {
		statusBar.setText(" Abrindo esquema " + schemaName + "...");
		new SwingWorker<SchemaInfo, Void>() {
			@Override
			protected SchemaInfo doInBackground() throws Exception {
				Connection conn = connectionManager.getConnection();
				conn.setCatalog(schemaName); // define o banco padrao (USE schema)
				return metadataService.loadSchema(conn, schemaName);
			}

			@Override
			protected void done() {
				try {
					SchemaInfo schema = get();
					if (activeWorkspace != null) {
						activeWorkspace.schema = schema;
						// mantem schemaList: e o que permite "Trocar esquema..." depois,
						// sem precisar desconectar e reconectar (ver maybeShowObjectContextMenu).
					}
					metadataCache.set(schema);
					completionProvider.refresh(schema);
					populateTree(schema);
					setConnectedState(schemaName);
					statusBar.setText(" Esquema " + schemaName + "  (" + schema.tables().size() + " tabelas)");
				} catch (Exception ex) {
					showError("Falha ao abrir o esquema", ex);
					statusBar.setText(" Erro ao abrir esquema");
				}
			}
		}.execute();
	}

	/**
	 * Se houver instrucoes de risco (DELETE/UPDATE sem WHERE, DDL), pede
	 * confirmacao listando-as. Retorna true para prosseguir; false para cancelar. O
	 * botao padrao e "Cancelar" (mais seguro).
	 */
	private boolean confirmRiskyStatements(List<String> statements) {
		StringBuilder sb = new StringBuilder();
		int count = 0;
		for (String sql : statements) {
			String reason = SqlRiskAnalyzer.riskReason(sql);
			if (reason != null) {
				count++;
				sb.append("• ").append(reason).append('\n').append("      ").append(snippet(sql)).append("\n\n");
			}
		}
		if (count == 0) {
			return true;
		}

		JTextArea area = new JTextArea("Atencao: " + count + " instrucao(oes) de risco detectada(s):\n\n" + sb
				+ "Tem certeza de que deseja executar?");
		area.setEditable(false);
		area.setOpaque(false);
		area.setFont(UIManager.getFont("Label.font"));
		JScrollPane scroll = new JScrollPane(area);
		scroll.setPreferredSize(new Dimension(560, 240));
		scroll.setBorder(BorderFactory.createEmptyBorder());

		Object[] options = { "Executar mesmo assim", "Cancelar" };
		int opt = JOptionPane.showOptionDialog(this, scroll, "Confirmar execucao de risco", JOptionPane.YES_NO_OPTION,
				JOptionPane.WARNING_MESSAGE, null, options, options[1]);
		return opt == 0;
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
		if (!confirmRiskyStatements(statements)) {
			statusBar.setText(" Execucao cancelada.");
			return;
		}
		closeOpenCursors();
		if (resultsArea != null && !resultsArea.isVisible()) {
			toggleResults(); // reabre os resultados para mostrar o carregamento
		}
		runButton.setEnabled(false);
		showExecuting(true);
		boolean usingSelection = editor.hasSelection();
		statusBar.setText(" Executando " + statements.size() + " instrucao(oes)"
				+ (usingSelection ? "  —  ATENCAO: rodando apenas a SELECAO" : "") + "...");

		SwingWorker<List<QueryResult>, Void> worker = new SwingWorker<>() {
			@Override
			protected List<QueryResult> doInBackground() {
				List<QueryResult> results = new ArrayList<>();
				Connection conn = connectionManager.getConnection();
				for (int i = 0; i < statements.size(); i++) {
					if (isCancelled()) {
						break;
					}
					String sql = statements.get(i);
					int n = i + 1;
					long t0 = System.nanoTime();
					Statement st = null;
					try {
						st = conn.createStatement();
						// cursor do servidor: busca em lotes do tamanho da pagina
						st.setFetchSize(PAGE_SIZE);
						runningStatement = st;
						boolean hasResultSet = st.execute(sql);
						long execMs = (System.nanoTime() - t0) / 1_000_000L;
						if (hasResultSet) {
							ResultSet rs = st.getResultSet();
							ResultTableModel model = createModel(rs);
							long t1 = System.nanoTime();
							int read = appendPage(model, rs, PAGE_SIZE);
							long fetchMs = (System.nanoTime() - t1) / 1_000_000L;
							boolean hasMore = read == PAGE_SIZE;
							ResultCursor cursor = null;
							if (hasMore) {
								cursor = new ResultCursor(st, rs);
							} else {
								rs.close();
								st.close();
							}
							results.add(QueryResult.grid("Resultado " + n, sql, model, execMs, fetchMs, cursor));
						} else {
							int updated = st.getUpdateCount();
							st.close();
							results.add(QueryResult.message("Comando " + n, sql, updated + " linha(s) afetada(s)",
									false, execMs));
						}
					} catch (SQLException ex) {
						if (st != null) {
							try {
								st.close();
							} catch (SQLException ignore) {
								// ignora
							}
						}
						long execMs = (System.nanoTime() - t0) / 1_000_000L;
						results.add(QueryResult.message("Erro " + n, sql, "Erro: " + ex.getMessage(), true, execMs));
						break;
					} finally {
						runningStatement = null;
					}
				}
				return results;
			}

			@Override
			protected void done() {
				showExecuting(false);
				runningStatement = null;
				runWorker = null;
				runButton.setEnabled(true);
				try {
					List<QueryResult> results = get();
					// Resultado pertence a ABA que rodou (editor), nao a
					// "aba selecionada agora" — o usuario pode ter trocado
					// de aba enquanto a query rodava. So redesenha o painel
					// de RESULTADOS se aquela aba ainda for a selecionada;
					// senao, so guarda (ver showResultsForActiveEditor,
					// chamado quando o usuario voltar pra ela).
					resultsByTab.put(editor, results);
					if (editor == currentEditor()) {
						showResults(results);
					}
					if (ranStructuralDdl(statements, results)) {
						refreshObjectTree(false);
					}
				} catch (CancellationException ce) {
					statusBar.setText(" Execucao cancelada.");
				} catch (Exception ex) {
					showError("Erro ao executar SQL", ex);
					statusBar.setText(" Erro na execucao");
				}
			}
		};
		runWorker = worker;
		worker.execute();
	}

	/**
	 * Verdadeiro se alguma das instrucoes EXECUTADAS COM SUCESSO (sem erro) era DDL
	 * estrutural — caso em que o navegador de objetos precisa ser recarregado. A
	 * lista de resultados pode ser menor que a de instrucoes quando a execucao
	 * parou num erro ou foi cancelada no meio.
	 */
	private static boolean ranStructuralDdl(List<String> statements, List<QueryResult> results) {
		int n = Math.min(statements.size(), results.size());
		for (int i = 0; i < n; i++) {
			QueryResult r = results.get(i);
			if (!r.error() && SqlRiskAnalyzer.isStructuralChange(statements.get(i))) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Redesenha as abas de RESULTADOS a partir de {@code results} — chamada
	 * tanto logo apos uma execucao quanto para REDESENHAR (zoom/modo
	 * compacto, ver refreshDynamicSizing, ou troca de aba de SQL, ver
	 * showResultsForActiveEditor) um conjunto ja existente. Por isso o
	 * {@code openCursors.contains(...)} abaixo: sem ele, reexibir o MESMO
	 * resultado (o mesmo objeto {@code ResultCursor}) duas vezes duplicaria
	 * a entrada na lista de cursores abertos.
	 */
	private void showResults(List<QueryResult> results) {
		this.lastResults = results;
		resultTabs.removeAll();
		boolean error = false;
		int grids = 0;
		for (QueryResult r : results) {
			JComponent content;
			if (r.model() != null) {
				if (r.cursor() != null && !r.cursor().exhausted && !openCursors.contains(r.cursor())) {
					openCursors.add(r.cursor());
				}
				content = buildGridPanel(r);
				grids++;
			} else {
				JTextArea area = new JTextArea(r.message() + "\n\n(executado em " + r.execMs() + " ms)");
				area.setEditable(false);
				content = new JScrollPane(area);
			}
			resultTabs.addTab(r.title(), content);
			resultTabs.setToolTipTextAt(resultTabs.getTabCount() - 1, sqlTooltip(r.sql()));
			error = error || r.error();
		}
		if (resultTabs.getTabCount() > 0) {
			resultTabs.setSelectedIndex(0);
			showResultsCard();
		} else {
			showEmptyState();
		}
		statusBar.setText(" " + results.size() + " instrucao(oes) executada(s), " + grids + " com resultado"
				+ (error ? " - parou em erro" : ""));
	}

	/**
	 * Painel de grade de uma aba de resultado: monta {@link ResultGrid} +
	 * {@link ResultStatusBar} atraves de {@link ResultView}. MainWindow so decide
	 * OS CALLBACKS que dependem do ciclo de vida do cursor JDBC (paginacao/leitura,
	 * que e responsabilidade sua, nao da grade nem da barra) — nenhuma logica de
	 * layout do resultado mora aqui.
	 */
	private JComponent buildGridPanel(QueryResult r) {
		ResultTableModel model = (ResultTableModel) r.model();
		String schemaName = (currentSchema != null) ? currentSchema.name() : null;
		ResultGrid grid = new ResultGrid(model, connectionManager, schemaName, tableMetadataCache,
				() -> exportResult(r), this::scaledPx);

		// Nome distinto de propósito do campo MainWindow.statusBar (JLabel do
		// rodape da JANELA inteira) — esta e a barra de UMA aba de resultado.
		ResultStatusBar resultStatusBar = new ResultStatusBar(PAGE_SIZE);
		Runnable refresh = () -> resultStatusBar.refresh(r.model().getRowCount(), r.execMs(), r.fetchMs(),
				r.cursor() != null && !r.cursor().exhausted);
		resultStatusBar.onLoadMore(() -> loadPage(r, PAGE_SIZE, refresh));
		resultStatusBar.onLoadAll(() -> loadAll(r, refresh));
		resultStatusBar.onExportThis(() -> exportResult(r));
		resultStatusBar.onExportAll(this::exportAll);
		refresh.run();

		return new ResultView(grid, resultStatusBar).asComponent();
	}

	/** Exporta um resultado especifico (este) para um arquivo Excel. */
	private void exportResult(QueryResult r) {
		if (r.model() == null) {
			JOptionPane.showMessageDialog(this, "Este resultado nao possui dados tabulares para exportar.",
					"Exportar para Excel", JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		File file = chooseSaveFile(r.title());
		if (file != null) {
			List<ExcelExporter.TableSheet> sheets = new ArrayList<>();
			sheets.add(new ExcelExporter.TableSheet(r.title(), r.model()));
			sheets.add(instructionsSheet(List.of(r)));
			doExport(sheets, file);
		}
	}

	/**
	 * Le ate {@code max} linhas do cursor em segundo plano (a leitura do
	 * ResultSet e a mutacao do TableModel NUNCA podem rodar fora da EDT — mesmo
	 * padrao seguro de {@link #loadAll}, so que limitado a uma pagina em vez de
	 * ate o fim do cursor) e entao chama {@code refresh}.
	 */
	private void loadPage(QueryResult r, int max, Runnable refresh) {
		ResultCursor c = r.cursor();
		if (c == null || c.exhausted) {
			return;
		}
		new SwingWorker<List<Vector<Object>>, Void>() {
			@Override
			protected List<Vector<Object>> doInBackground() throws SQLException {
				int cols = r.model().getColumnCount();
				List<Vector<Object>> rows = new ArrayList<>();
				while (rows.size() < max && c.rs.next()) {
					Vector<Object> row = new Vector<>(cols);
					for (int i = 1; i <= cols; i++) {
						row.add(c.rs.getObject(i));
					}
					rows.add(row);
				}
				return rows;
			}

			@Override
			protected void done() {
				try {
					List<Vector<Object>> rows = get();
					for (Vector<Object> row : rows) {
						r.model().addRow(row);
					}
					if (rows.size() < max) {
						c.exhausted = true;
						c.close();
						openCursors.remove(c);
					}
				} catch (Exception ex) {
					Throwable cause = (ex.getCause() != null) ? ex.getCause() : ex;
					AppLogger.warning("Falha ao carregar mais linhas", ex);
					c.exhausted = true;
					c.close();
					openCursors.remove(c);
					statusBar.setText(" Erro ao carregar mais linhas: " + cause.getMessage());
				}
				refresh.run();
			}
		}.execute();
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
					statusBar.setText(" Todas as linhas carregadas (" + r.model().getRowCount() + ").");
				} catch (Exception ex) {
					AppLogger.warning("Falha ao carregar linhas", ex);
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

	/** Fecha cursores abertos e as conexoes JDBC de TODOS os workspaces (ao fechar a janela). */
	private void closeAllConnections() {
		closeOpenCursors();
		for (Workspace w : workspaces.values()) {
			w.mgr.close();
		}
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
			JOptionPane.showMessageDialog(this, "Esta aba nao possui dados tabulares para exportar.",
					"Exportar para Excel", JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		File file = chooseSaveFile(r.title());
		if (file != null) {
			List<ExcelExporter.TableSheet> sheets = new ArrayList<>();
			sheets.add(new ExcelExporter.TableSheet(r.title(), r.model()));
			sheets.add(instructionsSheet(List.of(r)));
			doExport(sheets, file);
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
			JOptionPane.showMessageDialog(this, "Nenhum resultado tabular para exportar.", "Exportar para Excel",
					JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		sheets.add(instructionsSheet(lastResults));
		File file = chooseSaveFile("resultados");
		if (file != null) {
			doExport(sheets, file);
		}
	}

	/**
	 * Monta a aba "Instrucoes SQL" (Resultado x SQL executado), estilo PL/SQL
	 * Developer.
	 */
	private ExcelExporter.TableSheet instructionsSheet(List<QueryResult> results) {
		DefaultTableModel m = new DefaultTableModel(new Object[] { "Resultado", "Instrucao SQL" }, 0) {
			@Override
			public boolean isCellEditable(int r, int c) {
				return false;
			}
		};
		for (QueryResult r : results) {
			m.addRow(new Object[] { r.title(), r.sql() });
		}
		return new ExcelExporter.TableSheet("Instrucoes SQL", m);
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
					askToOpen(file);
				} catch (Exception ex) {
					showError("Falha ao exportar", ex);
					statusBar.setText(" Erro ao exportar");
				}
			}
		}.execute();
	}

	/** Apos exportar, pergunta se deseja abrir o arquivo no aplicativo padrao. */
	private void askToOpen(File file) {
		int opt = JOptionPane.showConfirmDialog(this,
				"Exportacao concluida:\n" + file.getName() + "\n\nDeseja abrir o arquivo agora?", "Exportar para Excel",
				JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
		if (opt != JOptionPane.YES_OPTION) {
			return;
		}
		if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
			statusBar.setText(" Abertura automatica nao suportada neste sistema.");
			return;
		}
		try {
			Desktop.getDesktop().open(file);
		} catch (Exception ex) {
			showError("Nao foi possivel abrir o arquivo", ex);
		}
	}

	// ---------- Auxiliares ----------

	private static String snippet(String sql) {
		String oneLine = sql.replaceAll("\\s+", " ").trim();
		return oneLine.length() > 80 ? oneLine.substring(0, 80) + "..." : oneLine;
	}

	/**
	 * Tooltip com o SQL exato executado (para conferir se o WHERE foi incluido).
	 */
	private static String sqlTooltip(String sql) {
		String body = sql.length() > 2000 ? sql.substring(0, 2000) + "..." : sql;
		String esc = body.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br>");
		return "<html><b>SQL executado:</b><br>" + esc + "</html>";
	}

	/**
	 * Cria o modelo (cabecalhos + tipos + origem real + tipo SQL de cada coluna)
	 * para uma consulta.
	 */
	private static ResultTableModel createModel(ResultSet rs) throws SQLException {
		ResultSetMetaData md = rs.getMetaData();
		int cols = md.getColumnCount();
		Vector<String> names = new Vector<>();
		Class<?>[] types = new Class<?>[cols];
		String[] sourceTables = new String[cols];
		String[] realColumnNames = new String[cols];
		String[] sqlTypeNames = new String[cols];
		ResultTableModel.ColumnJdbcMeta[] jdbcMeta = new ResultTableModel.ColumnJdbcMeta[cols];
		for (int i = 1; i <= cols; i++) {
			names.add(md.getColumnLabel(i));
			types[i - 1] = resolveColumnClass(md, i);
			// Tabela/coluna "reais" de origem (quando o driver informa) — usadas
			// so para casar a coluna do resultado com FKs do schema (indicador
			// no cabecalho). Podem vir vazias para expressoes/funcoes/JOINs
			// complexos; nesse caso simplesmente nao mostramos o indicador.
			try {
				sourceTables[i - 1] = md.getTableName(i);
				realColumnNames[i - 1] = md.getColumnName(i);
			} catch (SQLException ignore) {
				sourceTables[i - 1] = null;
				realColumnNames[i - 1] = null;
			}
			// Nome do tipo SQL real (ex.: "VARCHAR", "BIGINT", "JSON", "TIMESTAMP")
			// — usado pelo RendererFactory para colorir/alinhar por tipo.
			try {
				sqlTypeNames[i - 1] = md.getColumnTypeName(i);
			} catch (SQLException ignore) {
				sqlTypeNames[i - 1] = null;
			}
			jdbcMeta[i - 1] = readJdbcMeta(md, i);
		}
		return new ResultTableModel(names, types, sourceTables, realColumnNames, sqlTypeNames, jdbcMeta);
	}

	/**
	 * Le os metadados de coluna que o driver ja entrega junto com o
	 * ResultSetMetaData (sem nenhuma consulta extra ao banco): nulabilidade,
	 * precisao, escala, tamanho de exibicao e auto-increment. Cada chamada e
	 * protegida individualmente porque alguns drivers/tipos lancam SQLException
	 * para campos que nao fazem sentido (ex.: escala de uma coluna texto) em vez de
	 * simplesmente devolver 0.
	 */
	private static ResultTableModel.ColumnJdbcMeta readJdbcMeta(ResultSetMetaData md, int col) {
		boolean nullable = true;
		int precision = 0;
		int scale = 0;
		int displaySize = 0;
		boolean autoIncrement = false;
		try {
			nullable = md.isNullable(col) != ResultSetMetaData.columnNoNulls;
		} catch (SQLException ignore) {
			// mantem o padrao (nullable)
		}
		try {
			precision = md.getPrecision(col);
		} catch (SQLException ignore) {
			// mantem 0
		}
		try {
			scale = md.getScale(col);
		} catch (SQLException ignore) {
			// mantem 0
		}
		try {
			displaySize = md.getColumnDisplaySize(col);
		} catch (SQLException ignore) {
			// mantem 0
		}
		try {
			autoIncrement = md.isAutoIncrement(col);
		} catch (SQLException ignore) {
			// mantem false
		}
		return new ResultTableModel.ColumnJdbcMeta(nullable, precision, scale, displaySize, autoIncrement);
	}

	private static Class<?> resolveColumnClass(ResultSetMetaData md, int col) {
		try {
			return Class.forName(md.getColumnClassName(col));
		} catch (Exception ex) {
			AppLogger.fine("Nao foi possivel resolver a classe da coluna via metadata; usando Object", ex);
			return Object.class;
		}
	}

	/** Anexa ate {@code max} linhas do ResultSet ao modelo; retorna quantas leu. */
	private static int appendPage(DefaultTableModel model, ResultSet rs, int max) throws SQLException {
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
		// preserva o texto da busca (relevante quando isto e chamado por um
		// refresh apos DDL, em vez de uma conexao/abertura de esquema nova)
		rebuildTree(objectSearch.getText());
	}

	private void applyObjectFilter() {
		if (currentSchema != null) {
			rebuildTree(objectSearch.getText());
		}
	}

	/**
	 * Recarrega os metadados do esquema atual (tabelas, views, procedures,
	 * functions, triggers) sem mudar a conexao nem a aba selecionada. Chamado
	 * automaticamente apos DDL bem-sucedido e tambem pelo botao de atualizar
	 * (icone) e pelo atalho Ctrl+R.
	 */
	private void refreshObjectTree(boolean manual) {
		if (!connectionManager.isConnected() || currentSchema == null) {
			if (manual && statusBar != null) {
				statusBar.setText(" Conecte-se e abra um esquema antes de atualizar os objetos.");
			}
			return;
		}
		String schemaName = currentSchema.name();
		if (manual && statusBar != null) {
			statusBar.setText(" Atualizando objetos de " + schemaName + "...");
		}
		new SwingWorker<SchemaInfo, Void>() {
			@Override
			protected SchemaInfo doInBackground() throws Exception {
				Connection conn = connectionManager.getConnection();
				return metadataService.loadSchema(conn, schemaName);
			}

			@Override
			protected void done() {
				try {
					SchemaInfo schema = get();
					if (activeWorkspace != null) {
						activeWorkspace.schema = schema;
						// mantem schemaList (ver openSchema) para "Trocar esquema..." continuar disponivel.
					}
					metadataCache.set(schema);
					completionProvider.refresh(schema);
					// Descarta detalhes de tabela (colunas/PK/indices/FKs) em cache: apos
					// um DDL estrutural, a tela de propriedades nao pode continuar
					// mostrando o estado ANTERIOR ao ALTER/DROP so porque a tabela ja
					// tinha sido aberta antes nesta sessao.
					tableMetadataCache.clear();
					populateTree(schema);
					if (statusBar != null) {
						statusBar.setText(" Objetos atualizados (" + schema.tables().size() + " tabelas).");
					}
				} catch (Exception ex) {
					// Erro visivel de verdade (dialogo), nao so na status bar: um
					// refresh que falha silenciosamente (manual ou automatico apos
					// DDL) e indistinguivel de "nao fez nada" para quem esta usando.
					showError("Falha ao atualizar objetos", ex);
					if (statusBar != null) {
						statusBar.setText(" Erro ao atualizar objetos.");
					}
				}
			}
		}.execute();
	}

	/**
	 * Monta a arvore de objetos agrupada por tipo (estilo PL/SQL Developer),
	 * filtrando pelos nomes que contem {@code filter}. Tabelas e views tambem casam
	 * quando uma de suas colunas bate com o filtro.
	 */
	private void rebuildTree(String filter) {
		String f = filter == null ? "" : filter.trim().toLowerCase(Locale.ROOT);
		boolean filtering = !f.isEmpty();
		SchemaInfo schema = currentSchema;

		DefaultMutableTreeNode root = new DefaultMutableTreeNode(
				new ObjNode(NodeType.SCHEMA, schema.name(), schema.name(), null, null, null));

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

	private void addTableCategory(DefaultMutableTreeNode root, String label, List<TableInfo> items, NodeType type,
			String kind, String f, boolean filtering) {
		DefaultMutableTreeNode cat = new DefaultMutableTreeNode();
		int shown = 0;
		for (TableInfo t : items) {
			if (filtering && !contains(t.name(), f) && !anyColumnMatches(t, f)) {
				continue;
			}
			DefaultMutableTreeNode tn = new DefaultMutableTreeNode(new ObjNode(type, t.name(), t.name(), kind, t, null));
			// Ao buscar, a arvore mostra so o objeto; as colunas ficam na tela
			// de propriedades (duplo-clique). No modo normal, expande as colunas.
			if (!filtering) {
				for (ColumnInfo c : t.columns()) {
					// "kind" (TABLE/VIEW) propagado para a coluna: e o que o
					// ObjectTreeCellRenderer usa pra saber de qual categoria
					// colorida a coluna faz parte (a cor "desce" ate ela).
					tn.add(new DefaultMutableTreeNode(
							new ObjNode(NodeType.COLUMN, c.name() + " : " + c.type(), c.name(), kind, null, c.type())));
				}
			}
			cat.add(tn);
			shown++;
		}
		if (!filtering || shown > 0) {
			// "kind" tambem no cabecalho da categoria — a cor cobre a linha
			// "Tabelas (4)" inteira, nao so os itens dentro dela.
			cat.setUserObject(new ObjNode(NodeType.CATEGORY, label + " (" + items.size() + ")", label, kind, null, null));
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

	private void addNameCategory(DefaultMutableTreeNode root, String label, List<String> items, NodeType type,
			String kind, String f, boolean filtering) {
		DefaultMutableTreeNode cat = new DefaultMutableTreeNode();
		int shown = 0;
		for (String name : items) {
			if (filtering && !contains(name, f)) {
				continue;
			}
			cat.add(new DefaultMutableTreeNode(new ObjNode(type, name, name, kind, null, null)));
			shown++;
		}
		if (!filtering || shown > 0) {
			cat.setUserObject(new ObjNode(NodeType.CATEGORY, label + " (" + items.size() + ")", label, kind, null, null));
			root.add(cat);
		}
	}

	private static boolean contains(String value, String lowerFilter) {
		return lowerFilter.isEmpty() || value.toLowerCase(Locale.ROOT).contains(lowerFilter);
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

	/**
	 * Duplo-clique numa linha da arvore de objetos. So trata SCHEMA_PICK
	 * (item de uma lista de escolha de schema, folha sem filhos — nao ha
	 * conflito com expandir/recolher). Objetos abriveis (tabela/view/
	 * procedure/function/trigger) NAO abrem mais propriedades por duplo
	 * clique — esse gesto ficou reservado 100% para o expand/recolher
	 * nativo do JTree (ver o MouseListener em buildObjectBrowser). Abrir
	 * propriedades desses objetos agora e so pelo clique direito (ver
	 * {@link #maybeShowObjectContextMenu}).
	 */
	private void openSelectedObjectProperties() {
		TreePath path = objectTree.getSelectionPath();
		if (path == null) {
			return;
		}
		Object node = ((DefaultMutableTreeNode) path.getLastPathComponent()).getUserObject();
		if (node instanceof ObjNode obj && obj.type() == NodeType.SCHEMA_PICK) {
			openSchema(obj.name());
		}
	}

	/**
	 * Menu de contexto (clique direito) da arvore de objetos — so aparece
	 * para um objeto de fato "de banco" (tabela/view/procedure/function/
	 * trigger, ver {@link #isOpenableObject}), nunca para schema, categoria
	 * ou coluna. Substitui a antiga setinha fixa no fim da linha (poluia o
	 * visual) como forma de abrir "Propriedades".
	 */
	private void maybeShowObjectContextMenu(MouseEvent e) {
		if (!e.isPopupTrigger()) {
			return;
		}
		int row = objectTree.getRowForLocation(e.getX(), e.getY());
		if (row < 0) {
			return;
		}
		objectTree.setSelectionRow(row);
		TreePath path = objectTree.getPathForRow(row);
		Object node = ((DefaultMutableTreeNode) path.getLastPathComponent()).getUserObject();
		if (!(node instanceof ObjNode obj)) {
			return;
		}
		if (obj.type() == NodeType.SCHEMA) {
			buildSchemaRootContextMenu().show(objectTree, e.getX(), e.getY());
			return;
		}
		if (!isOpenableObject(obj.type())) {
			return;
		}
		buildObjectContextMenu(obj).show(objectTree, e.getX(), e.getY());
	}

	/**
	 * Menu de contexto da RAIZ da arvore (o schema aberto): hoje so tem
	 * "Trocar esquema...", habilitado quando a conexao deu acesso a mais de
	 * um esquema (login sem esquema fixo no cadastro — ver {@code pickSchema}
	 * em {@code connectTo}). Sem isto, quem entra com um usuario multi-schema
	 * e abre um esquema fica "preso" nele ate desconectar e reconectar.
	 */
	private JPopupMenu buildSchemaRootContextMenu() {
		JPopupMenu menu = new JPopupMenu();
		JMenuItem switchSchema = new JMenuItem("Trocar esquema...");
		boolean canSwitch = activeWorkspace != null
				&& activeWorkspace.schemaList != null
				&& !activeWorkspace.schemaList.isEmpty();
		switchSchema.setEnabled(canSwitch);
		if (!canSwitch) {
			switchSchema.setToolTipText("Esta conexao usa um esquema fixo definido no cadastro.");
		}
		switchSchema.addActionListener(a -> switchSchema());
		menu.add(switchSchema);
		return menu;
	}

	/**
	 * Verdadeiro se o clique caiu em cima da setinha de "trocar esquema"
	 * desenhada na ponta direita da linha do schema (raiz) — ver
	 * {@link ObjectTreeCellRenderer#paintComponent}. So a linha 0 (raiz)
	 * conta, e so quando ela de fato representa um schema aberto (a raiz
	 * tambem e usada como texto simples "Sem conexao"/"Selecione um
	 * esquema", sem essa seta).
	 */
	private boolean isSchemaSwitchArrowClick(MouseEvent e) {
		if (objectTree.getRowForLocation(e.getX(), e.getY()) != 0) {
			return false;
		}
		Object root = objectTree.getModel().getRoot();
		Object userObj = (root instanceof DefaultMutableTreeNode n) ? n.getUserObject() : null;
		if (!(userObj instanceof ObjNode obj) || obj.type() != NodeType.SCHEMA) {
			return false;
		}
		int zoneWidth = ObjectTreeCellRenderer.SCHEMA_SWITCH_ICON_SIZE + ObjectTreeCellRenderer.SCHEMA_SWITCH_ICON_MARGIN + 8;
		return e.getX() >= objectTree.getWidth() - zoneWidth;
	}

	/** Volta para a lista de esquemas da conexao ativa (ver {@link #buildSchemaPicker}). */
	private void switchSchema() {
		if (activeWorkspace == null || !activeWorkspace.mgr.isConnected()) {
			statusBar.setText(" Conecte-se a uma base antes de trocar de esquema.");
			return;
		}
		if (activeWorkspace.schemaList == null || activeWorkspace.schemaList.isEmpty()) {
			statusBar.setText(" Esta conexao usa um esquema fixo definido no cadastro.");
			return;
		}
		activeWorkspace.schema = null;
		currentSchema = null;
		buildSchemaPicker(activeWorkspace.schemaList);
		statusBar.setText(" Selecione um esquema (" + activeWorkspace.schemaList.size() + " disponiveis).");
	}

	private JPopupMenu buildObjectContextMenu(ObjNode obj) {
		JPopupMenu menu = new JPopupMenu();
		JMenuItem properties = new JMenuItem("Propriedades...");
		properties.addActionListener(a -> showObjectProperties(obj));
		menu.add(properties);
		JMenuItem rename = new JMenuItem("Renomear...");
		rename.setEnabled(false);
		rename.setToolTipText("Ainda nao implementado — reservado para uma proxima versao");
		menu.add(rename);
		menu.addSeparator();
		JMenuItem copyName = new JMenuItem("Copiar nome (Ctrl+C)");
		copyName.addActionListener(a -> copySelectedObjectNames());
		menu.add(copyName);
		return menu;
	}

	/**
	 * Copia o(s) nome(s) da(s) linha(s) selecionada(s) na arvore de objetos
	 * para a area de transferencia — atalho Ctrl+C (ver
	 * {@code buildObjectBrowser}) e item "Copiar nome" do menu de contexto.
	 * A arvore nao tem selecao de texto (nao e um campo editavel), entao
	 * "copiar o que esta selecionado" aqui significa o(s) nome(s) da(s)
	 * linha(s) — uma por linha, se mais de uma estiver selecionada.
	 */
	private void copySelectedObjectNames() {
		TreePath[] paths = objectTree.getSelectionPaths();
		if (paths == null || paths.length == 0) {
			return;
		}
		StringBuilder sb = new StringBuilder();
		for (TreePath path : paths) {
			Object node = ((DefaultMutableTreeNode) path.getLastPathComponent()).getUserObject();
			String text = (node instanceof ObjNode obj) ? obj.name() : String.valueOf(node);
			if (sb.length() > 0) {
				sb.append('\n');
			}
			sb.append(text);
		}
		GridClipboard.setClipboard(sb.toString());
	}

	/**
	 * Verdadeiro para os tipos de no que representam um objeto de banco de
	 * verdade (abrivel via "Informacoes"/DDL): tabela, view, procedure/
	 * function (ROUTINE) e trigger. NAO inclui CATEGORY nem COLUMN — desde
	 * que a cor de categoria passou a "descer" ate eles (ver
	 * {@link ObjectTreeCellRenderer}), os dois tambem carregam um
	 * {@code kind} nao nulo (reaproveitado so para escolher a cor), o que
	 * sozinho nao bastaria mais pra decidir se o duplo-clique deve abrir a
	 * tela de propriedades.
	 */
	private static boolean isOpenableObject(NodeType type) {
		return type == NodeType.TABLE || type == NodeType.VIEW
				|| type == NodeType.ROUTINE || type == NodeType.TRIGGER;
	}

	/**
	 * Janela de propriedades: para tabelas/views mostra a grade de colunas; para
	 * todos os objetos carrega a definicao (DDL) sob demanda.
	 */
	private void showObjectProperties(ObjNode obj) {
		JDialog dialog = new JDialog(this, prettyKind(obj.kind()) + " - " + obj.name(), false);
		dialog.setSize(560, 460);
		dialog.setLocationRelativeTo(this);
		dialog.setLayout(new BorderLayout());

		// SelectableLabel (nao JLabel comum): o nome do objeto e o "kind ·
		// schema" ficam selecionaveis/copiaveis com Ctrl+C — pedido
		// explicito do usuario ("qualquer texto aqui dentro pode ser
		// selecionado... ate nas propriedades").
		JComponent title = SelectableLabel.of(obj.name());
		title.setFont(title.getFont().deriveFont(Font.BOLD, 14f));
		JComponent sub = SelectableLabel.of(prettyKind(obj.kind()) + "  ·  " + currentSchema.name());
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
			colModel = readOnlyModel("#", "Coluna", "Tipo", "Nulo", "Chave", "Default", "Extra", "Comentario");
			tabs.addTab("Colunas", tableInScroll(colModel));
			// Indices e FKs sao especificos de tabelas (views nao tem).
			if ("TABLE".equals(obj.kind())) {
				idxModel = readOnlyModel("Indice", "Unico", "Tipo", "Colunas");
				tabs.addTab("Indices", tableInScroll(idxModel));
				fkModel = readOnlyModel("Constraint", "Coluna(s)", "Referencia", "Coluna(s) ref.", "On Update",
						"On Delete");
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
	private void loadTableDetailsInto(ObjNode obj, DefaultTableModel colModel, DefaultTableModel idxModel,
			DefaultTableModel fkModel) {
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
						colModel.addRow(new Object[] { c.position(), c.name(), c.type(), c.nullable() ? "Sim" : "Nao",
								prettyKey(c.key()), c.defaultValue() == null ? "" : c.defaultValue(),
								c.extra() == null ? "" : c.extra(), c.comment() == null ? "" : c.comment() });
					}
					if (idxModel != null) {
						for (IndexInfo ix : d.indexes()) {
							idxModel.addRow(new Object[] { ix.name(), ix.unique() ? "Sim" : "Nao", ix.type(),
									String.join(", ", ix.columns()) });
						}
					}
					if (fkModel != null) {
						for (ForeignKeyInfo fk : d.foreignKeys()) {
							fkModel.addRow(
									new Object[] { fk.name(), String.join(", ", fk.columns()), fk.referencedTable(),
											String.join(", ", fk.referencedColumns()), fk.onUpdate(), fk.onDelete() });
						}
					}
				} catch (Exception ex) {
					Throwable c = (ex.getCause() != null) ? ex.getCause() : ex;
					AppLogger.warning("Falha ao carregar detalhes do objeto", ex);
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
				try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
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
					AppLogger.warning("Falha ao carregar a definicao do objeto", ex);
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

	/**
	 * Tipos de no na arvore de objetos. Visibilidade de pacote: usado por outras
	 * classes de UI da arvore, no mesmo pacote (ver {@code ui}).
	 */
	enum NodeType {
		SCHEMA, SCHEMA_PICK, CATEGORY, TABLE, VIEW, ROUTINE, TRIGGER, COLUMN
	}

	/**
	 * No da arvore: tipo, texto exibido, nome cru do objeto, o tipo para o DDL
	 * (kind, null para schema/categoria/coluna), a tabela associada quando
	 * houver e o tipo SQL da coluna (columnType, so preenchido para
	 * NodeType.COLUMN — usado pelo {@link ObjectTreeCellRenderer} para
	 * destacar o nome em negrito e mostrar o tipo em cinza a parte).
	 */
	record ObjNode(NodeType type, String display, String name, String kind, TableInfo table, String columnType) {
		@Override
		public String toString() {
			return display;
		}
	}

	/**
	 * Mostra o erro num JTextArea (nao editavel, mas SELECIONAVEL/copiavel
	 * com Ctrl+C — ao contrario da String simples que
	 * {@code JOptionPane.showMessageDialog} renderia como um JLabel comum),
	 * mesmo padrao ja usado em {@link #confirmRiskyStatements}: mensagem de
	 * erro e exatamente o tipo de texto que da vontade de copiar (pesquisar,
	 * colar num chamado de suporte etc.).
	 */
	private void showError(String title, Exception ex) {
		AppLogger.warning(title, ex);
		Throwable cause = (ex.getCause() != null) ? ex.getCause() : ex;
		String message = (cause.getMessage() != null) ? cause.getMessage() : cause.toString();
		JTextArea area = new JTextArea(message);
		area.setEditable(false);
		area.setOpaque(false);
		area.setLineWrap(true);
		area.setWrapStyleWord(true);
		area.setFont(UIManager.getFont("Label.font"));
		JScrollPane scroll = new JScrollPane(area);
		scroll.setPreferredSize(new Dimension(480, 160));
		scroll.setBorder(BorderFactory.createEmptyBorder());
		JOptionPane.showMessageDialog(this, scroll, title, JOptionPane.ERROR_MESSAGE);
	}

	/**
	 * Workspace de uma conexao: sua sessao JDBC, esquema e abas de SQL proprias.
	 */
	private static final class Workspace {
		final String name; // nome da conexao (ou SCRATCH)
		final ConnectionProfile profile; // null para o workspace sem conexao
		final ConnectionManager mgr; // gerenciador JDBC proprio
		SchemaInfo schema; // esquema carregado (ou null)
		List<String> schemaList; // lista de esquemas (schema em branco)
		List<SessionStore.Tab> tabs = new ArrayList<>();
		int selectedTab = 0;
		/**
		 * Ultimos resultados de cada aba de SQL deste workspace, indexados
		 * pela POSICAO da aba (0-based, mesma ordem de {@code tabs} —
		 * indice, nao a instancia de SqlEditorPane: ao trocar de workspace e
		 * voltar, {@code rebuildEditorTabs} cria instancias NOVAS de
		 * SqlEditorPane a partir do texto salvo em {@code tabs}, entao a
		 * instancia antiga (chave usada em {@code MainWindow#resultsByTab}
		 * enquanto este workspace estava ativo) nao serve mais de chave).
		 * Preenchido em {@code saveActiveTabs} (ao SAIR deste workspace) e
		 * consumido em {@code rebuildEditorTabs} (ao VOLTAR pra ele).
		 */
		Map<Integer, List<QueryResult>> tabResults = new HashMap<>();

		Workspace(String name, ConnectionProfile profile, ConnectionManager mgr) {
			this.name = name;
			this.profile = profile;
			this.mgr = mgr;
		}
	}

	/**
	 * Resultado de um statement: grade (model != null) ou mensagem (update/erro).
	 */
	private record QueryResult(String title, String sql, DefaultTableModel model, String message, boolean error,
			long execMs, long fetchMs, ResultCursor cursor) {
		static QueryResult grid(String title, String sql, DefaultTableModel model, long execMs, long fetchMs,
				ResultCursor cursor) {
			return new QueryResult(title, sql, model, null, false, execMs, fetchMs, cursor);
		}

		static QueryResult message(String title, String sql, String message, boolean error, long execMs) {
			return new QueryResult(title, sql, null, message, error, execMs, 0L, null);
		}
	}
}
