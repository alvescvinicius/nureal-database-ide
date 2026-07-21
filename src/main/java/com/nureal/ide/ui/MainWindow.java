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
import java.nio.file.Path;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.Vector;
import java.util.concurrent.CancellationException;
import java.util.function.BiConsumer;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.ButtonGroup;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.Icon;
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
import javax.swing.JRootPane;
import javax.swing.JPopupMenu;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.KeyStroke;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumn;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.nureal.ide.core.autocomplete.SqlCompletionProvider;
import com.nureal.ide.core.connection.ConnectionManager;
import com.nureal.ide.core.connection.ConnectionProfile;
import com.nureal.ide.core.connection.ConnectionStore;
import com.nureal.ide.core.csv.CsvUtil;
import com.nureal.ide.core.backup.MySqlDumpRunner;
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
import com.nureal.ide.core.metadata.model.DbUserInfo;
import com.nureal.ide.core.metadata.model.ForeignKeyInfo;
import com.nureal.ide.core.metadata.model.IndexInfo;
import com.nureal.ide.core.metadata.model.SchemaForeignKey;
import com.nureal.ide.core.metadata.model.SchemaInfo;
import com.nureal.ide.core.metadata.model.TableDetails;
import com.nureal.ide.core.metadata.model.TableInfo;
import com.nureal.ide.core.queries.SavedQueryStore;
import com.nureal.ide.core.history.ExecutionHistoryStore;
import com.nureal.ide.core.safety.SqlRiskAnalyzer;
import com.nureal.ide.core.session.SessionStore;
import com.nureal.ide.core.sql.SqlStatementSplitter;
import com.nureal.ide.core.sql.SqlTypeKind;
import com.nureal.ide.core.sql.TableAliasGenerator;
import com.nureal.ide.core.sql.UnquotedDateGuard;
import com.nureal.ide.core.ui.UiPreferences;
import com.nureal.ide.core.update.AppVersion;
import com.nureal.ide.core.update.GithubRelease;
import com.nureal.ide.core.update.UpdateChecker;
import com.nureal.ide.core.update.UpdatePreferences;
import com.nureal.ide.core.ai.agent.Agent;
import com.nureal.ide.core.ai.agent.DefaultAgent;
import com.nureal.ide.core.ai.config.AiCredentialsStore;
import com.nureal.ide.core.ai.config.AiPreferences;
import com.nureal.ide.core.ai.config.LLMProviderFactory;
import com.nureal.ide.core.ai.context.ContextProvider;
import com.nureal.ide.core.ai.context.DefaultContextProvider;
import com.nureal.ide.core.ai.context.IdeStateAccessor;
import com.nureal.ide.core.ai.history.ChatHistoryStore;
import com.nureal.ide.core.ai.provider.LLMProvider;
import com.nureal.ide.core.ai.tool.DescribeTableTool;
import com.nureal.ide.core.ai.tool.ListTablesTool;
import com.nureal.ide.core.ai.tool.ToolExecutor;
import com.nureal.ide.ui.ai.AiSettingsDialog;
import com.nureal.ide.ui.ai.ChatActions;
import com.nureal.ide.ui.ai.ChatWindow;
import com.nureal.ide.ui.ai.IdeContextAccessor;
import com.nureal.ide.ui.components.NButton;
import com.nureal.ide.ui.components.NIconRail;
import com.nureal.ide.ui.components.NStatusBar;

/**
 * Janela principal no estilo de uma IDE moderna (FlatLaf): top bar com acao de
 * executar e tema, conexoes e objetos a esquerda, editor SQL em abas no centro
 * e resultados em abas abaixo (uma aba por statement), com exportacao para
 * Excel.
 */
public class MainWindow extends JFrame {

	private static final long serialVersionUID = 1L;
	// Publica (nao so pacote-visivel): reaproveitada por ResultStatusBar para o
	// icone do botao "Exportar" e por com.nureal.ide.ui.components.NButton
	// (Nureal Design System) — evita duplicar o mesmo valor de cor em outra
	// classe/pacote.
	// Verde institucional da marca Nureal (ver logo) — era 0x059669 (um verde-
	// esmeralda generico, sem relacao com a marca); atualizado apos revisao de
	// identidade visual (ver DESIGN_SYSTEM.md, secao 2). Unico ponto de
	// verdade: qualquer lugar que precisar do verde da marca reusa ACCENT, nunca
	// um literal proprio (ja auditado — ver Buttons/ConnectionsPanel/
	// ObjectTreeCellRenderer/ResultStatusBar).
	public static final Color ACCENT = new Color(0x1E9147);

	private static final int PAGE_SIZE = 200;
	private static final int MAX_TABS = 15;

	private static final String SCRATCH = "(sem conexao)";

	private final DatabaseDialect dialect = new MySqlDialect();
	/**
	 * Usado SO para criar a conexao SCRATCH em {@code initWorkspaces()}, antes
	 * de {@code activeWorkspace} existir — depois disso, {@code activeWorkspace}
	 * nunca mais fica null pelo resto da vida da janela, entao {@link #connectionManager()}
	 * sempre devolve {@code activeWorkspace.mgr} a partir dai. Nao usar
	 * diretamente fora de {@code initWorkspaces()}.
	 */
	private final ConnectionManager bootstrapConnectionManager = new ConnectionManager(dialect);
	private final Map<String, Conexao> workspaces = new LinkedHashMap<>();
	private Conexao activeWorkspace;
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
	private final SavedQueryStore savedQueryStore = new SavedQueryStore();
	private final ExecutionHistoryStore historyStore = new ExecutionHistoryStore();
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

	// ---------- Modo foco (botoes "Expandir" do editor/resultados) ----------
	// Ver toggleEditorFocusMode/toggleResultsFocusMode: guardam SO o que ELES
	// proprios esconderam/moveram, pra restaurar exatamente aquilo (nunca o
	// estado de visibilidade que o usuario ja tinha escolhido por conta
	// propria antes de entrar no modo foco).
	private boolean editorFocusMode;
	private boolean editorFocusHadSidebar;
	private boolean editorFocusHadResults;
	private boolean resultsFocusMode;
	private boolean resultsFocusHadSidebar;
	private int resultsFocusPrevDividerLoc = -1;
	private JTabbedPane resultTabs;
	private JPanel resultsCards;
	private JTree objectTree;
	private ConnectionsPanel connectionsPanel;
	private SavedQueriesPanel savedQueriesPanel;
	private HistoryPanel historyPanel;
	private NIconRail leftIconRail;
	private JPanel leftContent;
	private static final String CARD_CONNECTIONS = "connections";
	private static final String CARD_OBJECTS = "objects";
	private static final String CARD_QUERIES = "queries";
	private static final String CARD_HISTORY = "history";
	private JTextField objectSearch;
	/** Esquema selecionado na conexao ativa — so escrever via {@link #setCurrentSchema}. */
	private SchemaInfo currentSchema;
	private JLabel statusBar;
	private JLabel connStatusLabel;
	private JProgressBar connProgress;
	private JButton runButton;
	private JButton saveQueryButton;
	private JButton themeButton;
	private JComponent resultsOverlay;
	private JPanel executingCard;
	/** Nome da conexao em processo de conectar agora (ver {@link #setConnectingState}), ou null. */
	private String connectingWorkspaceName;
	/**
	 * Cor do dot/texto de {@link #connStatusLabel} no estado atual (des/
	 * conectado/conectando) — guardada para poder ser REAVALIADA em
	 * {@link #toggleTheme()} (ver {@link #applyConnStatusColor}). Sem isto, o
	 * mesmo bug sistemico dos botoes-so-de-icone (ver
	 * {@code Buttons#bindThemedIcon}) tambem valia aqui: alternar o tema SEM
	 * mudar de conexao deixava o dot com a cor GridTheme do tema anterior ate
	 * o proximo evento de conexao.
	 */
	private java.util.function.Supplier<Color> connStatusColorSupplier = () -> GridTheme.COLOR_LOGIC_FALSE;
	private SwingWorker<List<QueryResult>, Void> runWorker;
	private volatile Statement runningStatement;

	// Tema ESCURO agora e o padrao de arranque do app (ver App#main) — este
	// campo so espelha o L&F ja ativo quando a janela e construida.
	private boolean dark = true;
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

	/**
	 * Altura de linha (em px, ANTES do zoom/modo compacto — ver
	 * {@link #resultRowHeightBasePx()}) das grades de resultado — pedido
	 * explicito do usuario: "as vezes fica muito apertado... uma opcao
	 * permitindo alguns tamanhos". Independente do zoom da interface (que
	 * escala tudo) e do modo compacto (que reduz tudo): este controle mexe
	 * SO no espaco entre as linhas da grade de resultados, pensado para quem
	 * quer letras no tamanho normal mas linhas mais respiradas (ou o
	 * contrario, mais linhas visiveis de uma vez). {@code 22} (indice 1,
	 * "Padrao") e o valor que a grade sempre usou antes deste controle
	 * existir.
	 */
	private static final int[] ROW_SPACING_LEVELS = { 18, 22, 28, 34 };
	private static final String[] ROW_SPACING_LABELS = { "Compacta", "Padrao", "Confortavel", "Espacosa" };

	private final UiPreferences uiPrefsStore = new UiPreferences();
	private Font baseDefaultFont;
	private boolean sidebarOnRight = false;
	private boolean resultsVertical = false;
	private boolean compactMode = false;
	private int zoomIndex = UiPreferences.DEFAULT_ZOOM_INDEX;
	private int rowSpacingIndex = UiPreferences.DEFAULT_ROW_SPACING_INDEX;

	// ---------- Keep-alive de conexao ----------

	/**
	 * A cada {@link #keepAliveIntervalMs}, se ligado, roda um "SELECT 1"
	 * (via {@link DatabaseDialect#keepAliveQuery()}) em TODAS as conexoes
	 * abertas E ociosas ha pelo menos esse mesmo intervalo — so pra manter o
	 * socket/sessao vivos enquanto a IDE esta aberta (evita o banco ou um
	 * firewall/load balancer no meio derrubar a conexao por inatividade).
	 * Intervalo configuravel (menu Layout -> "Intervalo do keep-alive...")
	 * porque o padrao antigo fixo de 4 minutos podia ser MAIOR que o timeout
	 * real de rede/firewall/proxy de alguns ambientes, fazendo a conexao cair
	 * antes do primeiro ping — ver {@link #startKeepAliveTimer()} e
	 * {@link #pingKeepAlive()}.
	 */
	private boolean keepAliveEnabled = false;
	private int keepAliveIntervalMs = UiPreferences.DEFAULT_KEEP_ALIVE_SECONDS * 1000;
	private javax.swing.Timer keepAliveTimer;

	// ---------- Atualizacao automatica (ver com.nureal.ide.core.update) ----------

	/**
	 * Faixa discreta no topo da janela (ver {@link UpdateBanner}) — construida
	 * sempre (mesmo sem nenhuma atualizacao pendente), mas comeca invisivel;
	 * so aparece quando {@link #checkForUpdates} encontra uma versao mais nova.
	 */
	private UpdateBanner updateBanner;
	private final UpdatePreferences updatePreferencesStore = new UpdatePreferences();
	/** Ultimo release encontrado por {@link #checkForUpdates} — usado pelos botoes da faixa (baixar/ver notas/ignorar). */
	private GithubRelease latestReleaseFound;

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
		// Liga o autocomplete ao cache de FKs (ver #lookupForeignKeysForCompletion)
		// — "auxiliar de montagem de queries": ao completar o alvo de um JOIN,
		// o provider passa a sugerir primeiro as tabelas relacionadas por FK.
		completionProvider.setForeignKeyLookup(this::lookupForeignKeysForCompletion);
		buildUi();
		registerWindowShortcuts();
		startKeepAliveTimer();
		scheduleStartupUpdateCheck();
		// Salva a sessao e fecha as conexoes JDBC ao fechar (alem do autosave
		// continuo durante a digitacao) — sem isso, as conexoes de todos os
		// workspaces ficavam abertas ate o processo encerrar de vez.
		addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				if (keepAliveTimer != null) {
					keepAliveTimer.stop();
				}
				saveSession();
				closeAllConnections();
			}
		});
	}

	/**
	 * Granularidade FIXA do relogio interno do keep-alive — o {@code Timer}
	 * em si sempre roda a cada 5s, independente do intervalo configurado pelo
	 * usuario ({@link #keepAliveIntervalMs}); {@link #pingKeepAlive} e quem
	 * decide, a cada tick, se cada conexao ja esta ociosa o suficiente pra
	 * merecer um ping. Isso permite trocar o intervalo em tempo real (menu
	 * "Intervalo do keep-alive...") sem precisar recriar o Timer, e deixa
	 * intervalos curtos (ex.: 15s) responsivos de verdade.
	 */
	private static final int KEEP_ALIVE_TICK_MS = 5_000;

	/** Menor intervalo aceito na configuracao (evita o usuario zerar sem querer e martelar o banco). */
	private static final int MIN_KEEP_ALIVE_SECONDS = 5;

	/**
	 * Timer unico (fica sempre rodando enquanto a janela existe, granularidade
	 * fixa — ver {@link #KEEP_ALIVE_TICK_MS}) que so FAZ alguma coisa quando
	 * {@link #keepAliveEnabled} esta ligado ({@link #toggleKeepAlive}) — mais
	 * simples que criar/parar um Timer novo toda vez que o usuario liga ou
	 * desliga a opcao no menu (ou muda o intervalo).
	 */
	private void startKeepAliveTimer() {
		keepAliveTimer = new javax.swing.Timer(KEEP_ALIVE_TICK_MS, e -> {
			if (keepAliveEnabled) {
				pingKeepAlive();
			}
		});
		keepAliveTimer.start();
	}

	/**
	 * Roda o {@link DatabaseDialect#keepAliveQuery()} (ex.: "SELECT 1") em
	 * TODA conexao aberta (de qualquer workspace, nao so o ativo) que esteja
	 * ociosa ha pelo menos {@link #keepAliveIntervalMs} — ver
	 * {@code Conexao#lastActivityMillis}, atualizado sempre que
	 * {@link #onRun} executa alguma instrucao de verdade. Cada ping roda em
	 * background (nunca na EDT, evita travar a interface se a rede estiver
	 * lenta) e falhas sao so logadas: um keep-alive que falha nao deve
	 * incomodar o usuario com um dialogo de erro, a proxima execucao real vai
	 * revelar o problema de conexao normalmente se ele persistir.
	 */
	private void pingKeepAlive() {
		long now = System.currentTimeMillis();
		for (Conexao w : workspaces.values()) {
			if (w.profile() == null || !w.mgr().isConnected()) {
				continue;
			}
			if (now - w.lastActivityMillis() < keepAliveIntervalMs) {
				continue; // teve atividade de verdade recente, nao precisa de ping
			}
			w.setLastActivityMillis(now); // evita reenviar no proximo tick se a rede estiver lenta
			String query = dialect.keepAliveQuery();
			new Thread(() -> {
				try {
					ConnectionManager mgr = w.mgr();
					if (mgr.isConnected()) {
						try (Statement st = mgr.getConnection().createStatement()) {
							st.execute(query);
						}
					}
				} catch (Exception ex) {
					AppLogger.fine("Keep-alive falhou para " + w.name() + ": " + ex.getMessage(), ex);
				}
			}, "keep-alive-" + w.name()).start();
		}
	}

	/** Texto amigavel do intervalo atual: segundos se &lt; 1 min, senao minutos (com casas decimais so se necessario). */
	private String keepAliveIntervalLabel() {
		int seconds = keepAliveIntervalMs / 1000;
		if (seconds < 60) {
			return seconds + "s";
		}
		if (seconds % 60 == 0) {
			return (seconds / 60) + " min";
		}
		return String.format(java.util.Locale.ROOT, "%.1f min", seconds / 60.0);
	}

	/** Liga/desliga o keep-alive de conexao — ver checkbox no menu de layout. */
	private void toggleKeepAlive() {
		keepAliveEnabled = !keepAliveEnabled;
		saveUiState();
		if (statusBar != null) {
			statusBar.setText(keepAliveEnabled
					? " Keep-alive de conexao ativado (SELECT de teste a cada "
							+ keepAliveIntervalLabel() + " de ociosidade)."
					: " Keep-alive de conexao desativado.");
		}
	}

	// ---------- Atualizacao automatica ----------

	/**
	 * Dispara a checagem AUTOMATICA de atualizacao pouco depois do startup —
	 * atraso de proposito (a janela ja aparece e pinta primeiro; checar
	 * atualizacao nunca deve atrasar a abertura do app) via um {@link Timer}
	 * de disparo unico. Pulada inteiramente quando:
	 * <ul>
	 *   <li>{@link AppVersion#isDevBuild()} — rodando fora de um jar
	 *       empacotado (ex.: {@code mvn exec:java}), nao ha versao real para
	 *       comparar (ver javadoc de {@code AppVersion}).</li>
	 *   <li>{@code autoCheckEnabled=false} nas preferencias — usuario
	 *       desligou a checagem automatica.</li>
	 * </ul>
	 * A checagem MANUAL (menu "Verificar atualizacoes...") ignora as duas
	 * condicoes acima, sempre roda.
	 */
	private void scheduleStartupUpdateCheck() {
		if (AppVersion.isDevBuild()) {
			return;
		}
		Timer delay = new Timer(2000, e -> {
			try {
				UpdatePreferences.State prefs = updatePreferencesStore.load();
				if (prefs.autoCheckEnabled()) {
					checkForUpdates(false);
				}
			} catch (IOException ex) {
				AppLogger.fine("Nao foi possivel ler preferencias de atualizacao: " + ex.getMessage(), ex);
			}
		});
		delay.setRepeats(false);
		delay.start();
	}

	/**
	 * Consulta o GitHub em segundo plano (ver {@link UpdateChecker}, chamada
	 * de rede sincrona — por isso SEMPRE numa {@link SwingWorker}, nunca
	 * direto na EDT) e mostra a {@link UpdateBanner} se houver versao mais
	 * nova.
	 *
	 * @param manual {@code true} quando disparada pelo menu "Verificar
	 *               atualizacoes..." — nesse caso SEMPRE mostra algum
	 *               feedback ao usuario (banner OU um dialogo "voce ja esta
	 *               atualizado"/erro), e ignora tanto {@code autoCheckEnabled}
	 *               quanto uma versao previamente "ignorada"
	 *               ({@code skippedVersion}). A checagem automatica do
	 *               startup ({@code manual=false}) e silenciosa em qualquer
	 *               falha (rede indisponivel e o caso mais comum — nao deve
	 *               incomodar ninguem com um erro no startup) e respeita
	 *               {@code skippedVersion}.
	 */
	private void checkForUpdates(boolean manual) {
		new SwingWorker<GithubRelease, Void>() {
			@Override
			protected GithubRelease doInBackground() throws Exception {
				return UpdateChecker.fetchLatestRelease();
			}

			@Override
			protected void done() {
				GithubRelease release;
				try {
					release = get();
				} catch (Exception ex) {
					AppLogger.fine("Checagem de atualizacao falhou: " + ex.getMessage(), ex);
					if (manual) {
						JOptionPane.showMessageDialog(MainWindow.this,
								"Nao foi possivel checar atualizacoes: " + rootMessage(ex),
								"Verificar atualizacoes", JOptionPane.WARNING_MESSAGE);
					}
					return;
				}
				if (!UpdateChecker.isUpdateAvailable(release)) {
					if (manual) {
						JOptionPane.showMessageDialog(MainWindow.this,
								"Voce ja esta na versao mais recente (" + AppVersion.current() + ").",
								"Verificar atualizacoes", JOptionPane.INFORMATION_MESSAGE);
					}
					return;
				}
				if (!manual && isSkipped(release)) {
					return;
				}
				latestReleaseFound = release;
				updateBanner.showUpdate(release);
			}
		}.execute();
	}

	/** {@code true} quando o usuario ja mandou "ignorar esta versao" para exatamente este release. */
	private boolean isSkipped(GithubRelease release) {
		try {
			String skipped = updatePreferencesStore.load().skippedVersion();
			return !skipped.isBlank() && skipped.equals(release.tagName());
		} catch (IOException ex) {
			return false;
		}
	}

	/** Mensagem da causa raiz de {@code ex} (a de mais alto nivel costuma ser so "java.io.IOException" generico). */
	private static String rootMessage(Throwable ex) {
		Throwable t = ex;
		while (t.getCause() != null) {
			t = t.getCause();
		}
		String msg = t.getMessage();
		return (msg == null || msg.isBlank()) ? t.getClass().getSimpleName() : msg;
	}

	/**
	 * Botao "Baixar" da faixa — abre a pagina do Release no navegador padrao
	 * (a mesma URL do botao "Abrir no GitHub" de {@link ReleaseNotesDialog}).
	 * A atualizacao e sempre MANUAL: o usuario baixa e roda o instalador da
	 * sua plataforma sozinho, a partir da pagina — a IDE nao baixa nem
	 * executa mais nenhum instalador por conta propria (era so no Windows
	 * antes; agora o comportamento e o MESMO nos 3 sistemas operacionais).
	 */
	private void onInstallUpdate() {
		if (latestReleaseFound == null) {
			return;
		}
		String url = latestReleaseFound.htmlUrl();
		try {
			if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
				Desktop.getDesktop().browse(java.net.URI.create(url));
				return;
			}
		} catch (Exception ex) {
			AppLogger.fine("Nao foi possivel abrir o navegador para a pagina do release: " + ex.getMessage(), ex);
		}
		JOptionPane.showMessageDialog(this,
				"Nao foi possivel abrir o navegador automaticamente. Link: " + url,
				"Baixar atualizacao", JOptionPane.INFORMATION_MESSAGE);
	}

	/** Botao "Ver notas" da faixa — ver {@link ReleaseNotesDialog}. */
	private void onViewUpdateNotes() {
		if (latestReleaseFound != null) {
			ReleaseNotesDialog.open(this, latestReleaseFound);
		}
	}

	/** Botao "Ignorar esta versao" — grava a tag em {@code skippedVersion} e some com a faixa (nao volta ate um release NOVO sair). */
	private void onSkipUpdate() {
		if (latestReleaseFound != null) {
			try {
				updatePreferencesStore.save(updatePreferencesStore.load().withSkippedVersion(latestReleaseFound.tagName()));
			} catch (IOException ex) {
				AppLogger.fine("Nao foi possivel salvar a versao ignorada: " + ex.getMessage(), ex);
			}
		}
		updateBanner.hideBanner();
	}

	/** Botao "X" da faixa — so fecha PARA ESTA SESSAO, sem persistir nada (ver javadoc de {@link UpdateBanner}). */
	private void onDismissUpdateBanner() {
		updateBanner.hideBanner();
	}

	/**
	 * Pede ao usuario o novo intervalo do keep-alive, EM SEGUNDOS (menu Layout
	 * -> "Intervalo do keep-alive...") — o padrao fixo de 4 minutos podia ser
	 * maior que o timeout real de alguns ambientes (firewall/proxy/load
	 * balancer cortando por inatividade bem antes disso), entao a conexao
	 * caia mesmo com o keep-alive ligado. Aceita qualquer valor inteiro >=
	 * {@link #MIN_KEEP_ALIVE_SECONDS}; o Timer em si roda numa granularidade
	 * fixa (ver {@link #KEEP_ALIVE_TICK_MS}), entao o novo valor passa a valer
	 * ja no proximo tick, sem precisar reiniciar nada.
	 */
	private void configureKeepAliveInterval() {
		String input = JOptionPane.showInputDialog(this,
				"Intervalo do keep-alive, em segundos (ex.: 60 = 1 minuto):",
				keepAliveIntervalMs / 1000);
		if (input == null) {
			return; // cancelado
		}
		int seconds;
		try {
			seconds = Integer.parseInt(input.trim());
		} catch (NumberFormatException ex) {
			JOptionPane.showMessageDialog(this, "Digite um numero inteiro de segundos.",
					"Intervalo invalido", JOptionPane.ERROR_MESSAGE);
			return;
		}
		if (seconds < MIN_KEEP_ALIVE_SECONDS) {
			JOptionPane.showMessageDialog(this,
					"O intervalo minimo e " + MIN_KEEP_ALIVE_SECONDS + " segundos.",
					"Intervalo invalido", JOptionPane.ERROR_MESSAGE);
			return;
		}
		keepAliveIntervalMs = seconds * 1000;
		saveUiState();
		if (statusBar != null) {
			statusBar.setText(" Intervalo do keep-alive definido para " + keepAliveIntervalLabel() + ".");
		}
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
		keepAliveEnabled = state.keepAliveEnabled();
		keepAliveIntervalMs = Math.max(MIN_KEEP_ALIVE_SECONDS, state.keepAliveIntervalSeconds()) * 1000;
		zoomIndex = clampZoomIndex(state.zoomIndex());
		if (zoomIndex != UiPreferences.DEFAULT_ZOOM_INDEX) {
			applyZoomFont(zoomIndex); // so a fonte; ainda nao ha janela/componentes
		}
		rowSpacingIndex = clampRowSpacingIndex(state.rowSpacingIndex());
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

		// Faixa de atualizacao disponivel (ver "Atualizacao automatica" acima) —
		// no NORTH da janela inteira (nao so da area do editor), pra ficar
		// visivel independente de qual painel/aba esta em foco. Comeca
		// invisivel (ver construtor de UpdateBanner) — nao ocupa espaco ate
		// checkForUpdates() encontrar uma versao mais nova.
		updateBanner = new UpdateBanner(
				this::onInstallUpdate, this::onViewUpdateNotes, this::onSkipUpdate, this::onDismissUpdateBanner);
		add(updateBanner, BorderLayout.NORTH);

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

		// 8px (Spacing.SM) acima/abaixo — o MESMO valor do padding do painel
		// CONEXOES (ver ConnectionsPanel, createEmptyBorder(8,8,8,8)), para as
		// duas linhas (cabecalho do sidebar e esta barra) ficarem na mesma
		// altura visual, lado a lado. 0px na esquerda para colar na linha da
		// aba; 12px (Spacing.MD) na direita.
		mainBar.setBorder(BorderFactory.createEmptyBorder(Spacing.SM, 0, Spacing.SM, Spacing.MD));

		GridBagConstraints gbc = new GridBagConstraints();
		// O segredo do alinhamento: força todos os elementos a compartilharem a mesma
		// linha base vertical
		gbc.anchor = GridBagConstraints.BASELINE;
		gbc.fill = GridBagConstraints.NONE;
		gbc.weighty = 1.0;
		gbc.gridy = 0;

		// --- Botões da Esquerda ---
		runButton = new NButton("Executar", NButton.Kind.PRIMARY);
		runButton.setIcon(Icons.get(IconType.RUN, 14, Color.WHITE));
		runButton.setToolTipText("Executar (Ctrl+Enter ou F5)");
		runButton.setEnabled(false);
		runButton.addActionListener(e -> onRun());
		runButton.setIconTextGap(6);
		// Vertical XS (4, era 6 — barra mais baixa, pedido explicito da
		// revisao premium) e um pouco mais de respiro horizontal a esquerda
		// (LG=16) que a direita (MD=12): unico botao PREENCHIDO/em destaque
		// da barra, a leve assimetria reforca presenca sem parecer maior por
		// acidente. Sobrescreve a margem padrao de NButton.Kind.PRIMARY de proposito.
		runButton.setMargin(new Insets(Spacing.XS, Spacing.LG, Spacing.XS, Spacing.MD));

		// Sem icone aqui de proposito: o icone de "linhas" ficava estranho colado
		// ao texto "Formatar" nesse tamanho — so texto. Estilo OUTLINE (contorno,
		// sem preenchimento) — igual ao "Nova" do painel CONEXOES (ver
		// ConnectionsPanel#buildHeader) — em vez de um segundo botao solido do
		// lado do Executar: fica claro que Executar e a acao primaria, e o
		// conjunto Formatar+seta e mais leve/discreto (mesma leitura de "acao
		// secundaria" nos dois lugares da UI).
		JButton formatButton = new NButton("Formatar", NButton.Kind.SECONDARY);
		formatButton.setToolTipText("Formatar SQL (Ctrl+Shift+F)");
		formatButton.addActionListener(e -> {
			SqlEditorPane editor = currentEditor();
			if (editor != null) {
				editor.formatText();
			}
		});
		formatButton.setMargin(new Insets(Spacing.XS, Spacing.MD, Spacing.XS, Spacing.MD));

		JButton formatMenuButton = new JButton(new com.formdev.flatlaf.icons.FlatMenuArrowIcon());
		formatMenuButton.setToolTipText("Presets e opcoes de formatacao");
		formatMenuButton
				.addActionListener(e -> buildFormatMenu().show(formatMenuButton, 0, formatMenuButton.getHeight()));
		Buttons.styleSecondary(formatMenuButton);
		formatMenuButton.setMargin(new Insets(Spacing.XS, Spacing.SM, Spacing.XS, Spacing.SM));

		// "Explicar" (fase 4 do GAP_ANALYSIS_DBA_DEV.md: "EXPLAIN visual") —
		// mesmo estilo OUTLINE de "Formatar" (acao secundaria, ao lado da
		// primaria "Executar"), rodando EXPLAIN FORMAT=JSON na instrucao atual
		// sem executa-la de verdade.
		JButton explainButton = new NButton("Explicar", NButton.Kind.SECONDARY);
		explainButton.setToolTipText("Ver o plano de execucao (EXPLAIN) da consulta atual");
		explainButton.addActionListener(e -> onExplain());
		explainButton.setMargin(new Insets(Spacing.XS, Spacing.MD, Spacing.XS, Spacing.MD));

		// O icone minusculo da seta rende um "preferred height" menor que o do
		// texto "Executar"/"Formatar" — sem isto os tres ficam com alturas
		// ligeiramente diferentes mesmo com a mesma margem vertical. Forca os
		// tres a MESMA altura (a maior das tres), so a largura continua livre.
		int rowHeight = Math.max(runButton.getPreferredSize().height,
				Math.max(formatButton.getPreferredSize().height,
						Math.max(formatMenuButton.getPreferredSize().height, explainButton.getPreferredSize().height)));
		for (JButton b : new JButton[] { runButton, formatButton, formatMenuButton, explainButton }) {
			Dimension d = b.getPreferredSize();
			b.setPreferredSize(new Dimension(d.width, rowHeight));
		}

		// Adiciona os botões esquerdos um a um aplicando pequenos recuos à direita
		// (insets)
		gbc.gridx = 0;
		gbc.weightx = 0.0;
		gbc.insets = new Insets(0, 0, 0, Spacing.MD); // Colado na esquerda, espaço apos o Executar
		mainBar.add(runButton, gbc);

		gbc.gridx = 1;
		gbc.insets = new Insets(0, 0, 0, 0);
		mainBar.add(formatButton, gbc);

		gbc.gridx = 2;
		// So uma pequena margem entre "Formatar" e a seta de opcoes — nao
		// colados de vez (como um segmented button ficaria), so proximos.
		gbc.insets = new Insets(0, Spacing.XS, 0, 0);
		mainBar.add(formatMenuButton, gbc);

		gbc.gridx = 3;
		gbc.insets = new Insets(0, Spacing.MD, 0, 0);
		mainBar.add(explainButton, gbc);

		// Salvar a aba atual como query (biblioteca gerenciada pelo app — ver
		// SavedQueryStore): mesmo estilo outline do Formatar, acao secundaria.
		// Desabilitado quando a aba atual esta vazia (ver updateSaveButtonState) —
		// antes o clique era aceito mas nao fazia nada alem de um aviso na barra
		// de status, o que parecia um bug de "salvar nao funciona".
		saveQueryButton = new NButton("Salvar", NButton.Kind.SECONDARY);
		// Buttons.bindThemedIcon (nao setIcon(Icons.get(...)) solto): sem
		// isto o icone ficava congelado na paleta do tema em que a janela
		// abriu, ilegivel apos o primeiro toggleTheme() (ver javadoc do
		// metodo — mesmo bug sistemico corrigido nos botoes so-de-icone).
		Buttons.bindThemedIcon(saveQueryButton, IconType.SAVE, 13, () -> GridTheme.MUTED_TEXT);
		saveQueryButton.setToolTipText("Salvar como query (Ctrl+S)");
		saveQueryButton.addActionListener(e -> onSaveQuery());
		saveQueryButton.setIconTextGap(6);
		saveQueryButton.setMargin(new Insets(Spacing.XS, Spacing.MD, Spacing.XS, Spacing.MD));
		Dimension saveDim = saveQueryButton.getPreferredSize();
		saveQueryButton.setPreferredSize(new Dimension(saveDim.width, rowHeight));

		gbc.gridx = 4;
		gbc.insets = new Insets(0, Spacing.MD, 0, 0);
		mainBar.add(saveQueryButton, gbc);

		// Historico de execucoes (ver ExecutionHistoryStore/HistoryPanel): abre a
		// aba "Historico" do painel lateral, ja filtrada pela conexao ativa —
		// mesmo grupo visual/posicao do Salvar, por ser tambem uma acao sobre a
		// query da aba atual (rever/re-rodar o que ja foi executado).
		JButton historyButton = new NButton("Historico", NButton.Kind.SECONDARY);
		Buttons.bindThemedIcon(historyButton, IconType.HISTORY, 13, () -> GridTheme.MUTED_TEXT);
		historyButton.setToolTipText("Ver historico de execucoes desta conexao");
		historyButton.addActionListener(e -> showHistoryPanel());
		historyButton.setIconTextGap(6);
		historyButton.setMargin(new Insets(Spacing.XS, Spacing.MD, Spacing.XS, Spacing.MD));
		Dimension historyDim = historyButton.getPreferredSize();
		historyButton.setPreferredSize(new Dimension(historyDim.width, rowHeight));

		gbc.gridx = 5;
		gbc.insets = new Insets(0, Spacing.SM, 0, 0);
		mainBar.add(historyButton, gbc);

		// --- O ESPAÇADOR INVISÍVEL ---
		// Ele joga tudo o que vier a partir daqui totalmente para a direita
		gbc.gridx = 6;
		gbc.weightx = 1.0;
		gbc.insets = new Insets(0, 0, 0, 0);
		mainBar.add(Box.createHorizontalGlue(), gbc);

		// --- Botões da Direita (icones discretos, mesma linguagem visual) ---
		// Sem separador visivel antes deste grupo (principio do NDS:
		// "divisorias deixam de ser linhas, passam a ser espaco") — o
		// respiro extra no inset do primeiro icone (ver gridx=8 abaixo) ja
		// comunica o agrupamento, sem precisar de um traco desenhado.
		// Buttons.iconButton ja aplica styleIconButton E prende o icone a
		// GridTheme.MUTED_TEXT (ver seu javadoc) — antes estes 4 botoes eram
		// "new JButton(Icons.get(..., GridTheme.MUTED_TEXT))" com a cor
		// CONGELADA no tema em que a janela abriu, so corrigindo sozinha se o
		// usuario fechasse e reabrisse a janela.
		JButton toggleSidebar = Buttons.iconButton(IconType.PANEL_LEFT, 16, () -> GridTheme.MUTED_TEXT);
		toggleSidebar.setToolTipText("Mostrar/ocultar painel lateral (Ctrl+B)");
		toggleSidebar.addActionListener(e -> toggleSidebar());

		JButton toggleResults = Buttons.iconButton(IconType.PANEL_BOTTOM, 16, () -> GridTheme.MUTED_TEXT);
		toggleResults.setToolTipText("Mostrar/ocultar resultados (Ctrl+J)");
		toggleResults.addActionListener(e -> toggleResults());

		JButton layoutButton = Buttons.iconButton(IconType.SETTINGS, 16, () -> GridTheme.MUTED_TEXT);
		layoutButton.setToolTipText("Layout, zoom e modo compacto");
		layoutButton.addActionListener(e -> buildLayoutMenu().show(layoutButton, 0, layoutButton.getHeight()));

		// Icone inicial mostra a ACAO do botao (pra ONDE ele muda o tema), nao
		// o tema atual — app arranca no tema escuro (ver App#main), entao o
		// botao comeca oferecendo "mudar para claro" (icone de sol). O TIPO do
		// icone muda conforme o tema (sol/lua), entao continua sendo
		// resetado explicitamente em toggleTheme() — so a COR (MUTED_TEXT)
		// precisava do fix generico, ja coberta por iconButton aqui tambem.
		themeButton = Buttons.iconButton(IconType.THEME_LIGHT, 16, () -> GridTheme.MUTED_TEXT);
		themeButton.setToolTipText("Alternar tema claro/escuro");
		themeButton.addActionListener(e -> toggleTheme());

		// Chat com IA (Ollama local) — janela nao-modal propria (ver
		// com.nureal.ide.ui.ai.ChatWindow), mesma linguagem visual discreta
		// dos outros icones do grupo da direita.
		JButton chatButton = Buttons.iconButton(IconType.CHAT, 16, () -> GridTheme.MUTED_TEXT);
		chatButton.setToolTipText("Chat com IA (Ollama local)");
		chatButton.addActionListener(e -> openAiChat());

		// Adiciona os botões da direita sequencialmente
		gbc.gridx = 7;
		// Respiro maior (LG) antes do primeiro icone do grupo — sozinho, sem
		// linha divisoria, ja marca visualmente onde o grupo comeca.
		gbc.insets = new Insets(0, Spacing.LG, 0, Spacing.XS);
		mainBar.add(toggleSidebar, gbc);

		gbc.insets = new Insets(0, Spacing.XS, 0, Spacing.XS); // Pequeno espaço entre os ícones
		gbc.gridx = 8;
		mainBar.add(toggleResults, gbc);
		gbc.gridx = 9;
		mainBar.add(layoutButton, gbc);
		gbc.gridx = 10;
		mainBar.add(themeButton, gbc);
		gbc.gridx = 11;
		mainBar.add(chatButton, gbc);

		toolbarBar = mainBar;
		// initWorkspaces() ja rodou (ver buildEditorArea) quando chegamos aqui,
		// entao editorTabs ja tem a aba inicial — reflete o estado real dela no
		// botao Salvar desde o primeiro desenho, em vez de nascer sempre habilitado.
		updateSaveButtonState();
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

		JCheckBoxMenuItem keepAlive = new JCheckBoxMenuItem("Manter conexao viva (keep-alive)", keepAliveEnabled);
		keepAlive.setToolTipText("Roda um SELECT de teste a cada " + keepAliveIntervalLabel() + " de ociosidade, "
				+ "so nas conexoes que ja estao abertas, pra evitar que caiam por inatividade.");
		keepAlive.addActionListener(a -> toggleKeepAlive());
		menu.add(keepAlive);

		JMenuItem keepAliveInterval = new JMenuItem("Intervalo do keep-alive... (" + keepAliveIntervalLabel() + ")");
		keepAliveInterval.addActionListener(a -> configureKeepAliveInterval());
		menu.add(keepAliveInterval);

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

		// Espacamento de linhas da grade de resultados — pedido explicito do
		// usuario, independente do Zoom acima (que escala a interface
		// inteira): so a altura da linha das grades de resultado.
		JMenu rowSpacingMenu = new JMenu("Espacamento de linhas (grade)");
		for (int i = 0; i < ROW_SPACING_LEVELS.length; i++) {
			int idx = i;
			String mark = (i == rowSpacingIndex) ? "✓ " : "      ";
			JMenuItem item = new JMenuItem(mark + ROW_SPACING_LABELS[i] + " (" + ROW_SPACING_LEVELS[i] + "px)");
			item.addActionListener(a -> setRowSpacingIndex(idx));
			rowSpacingMenu.add(item);
		}
		menu.add(rowSpacingMenu);

		menu.addSeparator();
		JMenuItem checkUpdates = new JMenuItem("Verificar atualizacoes...");
		checkUpdates.addActionListener(a -> checkForUpdates(true));
		menu.add(checkUpdates);

		return menu;
	}

	/**
	 * Menu de formatacao: presets, opcoes (caixa alta / JSON) e fonte do editor.
	 */
	private JPopupMenu buildFormatMenu() {
		JPopupMenu menu = new JPopupMenu();

		menu.add(formatMenuHeader("Presets"));
		ButtonGroup presets = new ButtonGroup();
		menu.add(presetItem(presets, SqlFormatter.Style.STANDARD, "Padrao (recomendado)"));
		menu.add(presetItem(presets, SqlFormatter.Style.COMMA_FIRST, "Virgula no inicio da linha"));
		menu.add(presetItem(presets, SqlFormatter.Style.RIVER, "Alinhado a direita (estilo classico)"));

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

	/**
	 * Alterna "modo foco" do EDITOR: esconde o painel lateral E os resultados
	 * (reaproveitando {@link #toggleSidebar()}/{@link #toggleResults()}, as
	 * mesmas acoes ja usadas por Ctrl+B/Ctrl+J) para a aba de SQL ocupar a
	 * janela inteira — chamado pelo botao "Expandir" da barra de acoes
	 * rapidas do editor (ver {@code SqlEditorPane#buildBreadcrumbBar}).
	 * Alternar de novo (em QUALQUER aba, o estado e da janela, nao da aba)
	 * desfaz, restaurando so o que este modo escondeu — se o usuario ja
	 * tinha os resultados fechados por conta propria ANTES de expandir, eles
	 * continuam fechados depois de recolher.
	 */
	private void toggleEditorFocusMode() {
		if (!editorFocusMode) {
			editorFocusHadSidebar = leftSide.isVisible();
			editorFocusHadResults = resultsArea != null && resultsArea.isVisible();
			if (editorFocusHadSidebar) {
				toggleSidebar();
			}
			if (editorFocusHadResults) {
				toggleResults();
			}
			editorFocusMode = true;
		} else {
			if (editorFocusHadSidebar && !leftSide.isVisible()) {
				toggleSidebar();
			}
			if (editorFocusHadResults && resultsArea != null && !resultsArea.isVisible()) {
				toggleResults();
			}
			editorFocusMode = false;
		}
	}

	/**
	 * Alterna "modo foco" dos RESULTADOS: esconde o painel lateral e empurra
	 * o divisor central quase todo para o lado dos resultados (sem esconder
	 * o editor de vez — so sobra uma faixa minima dele) — chamado pelo
	 * botao "Expandir" do cabecalho de RESULTADOS (ver
	 * {@code #buildResultsArea}). Mesma logica de restaurar SO o que este
	 * modo mexeu, ver {@link #toggleEditorFocusMode}.
	 */
	private void toggleResultsFocusMode() {
		if (!resultsFocusMode) {
			resultsFocusHadSidebar = leftSide.isVisible();
			resultsFocusPrevDividerLoc = centerSplit.getDividerLocation();
			if (resultsFocusHadSidebar) {
				toggleSidebar();
			}
			if (resultsArea != null && !resultsArea.isVisible()) {
				toggleResults();
			}
			centerSplit.setDividerLocation(40);
			resultsFocusMode = true;
		} else {
			if (resultsFocusHadSidebar && !leftSide.isVisible()) {
				toggleSidebar();
			}
			if (resultsFocusPrevDividerLoc > 0) {
				centerSplit.setDividerLocation(resultsFocusPrevDividerLoc);
			} else {
				centerSplit.setResizeWeight(0.62);
				centerSplit.setDividerLocation(0.62);
			}
			resultsFocusMode = false;
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
		bindGlobalAction(rp, "control S", "save-query", this::onSaveQuery);
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

	// ---------- Espacamento de linhas da grade de resultados ----------

	private static int clampRowSpacingIndex(int index) {
		return Math.max(0, Math.min(ROW_SPACING_LEVELS.length - 1, index));
	}

	/**
	 * Altura de linha BASE (antes de {@link #scaledPx}, que continua
	 * aplicando zoom/modo compacto por cima) que {@link ResultGrid} usa para
	 * TODAS as grades de resultado da sessao — trocar o indice reconstroi as
	 * grades ja abertas (ver {@link #setRowSpacingIndex}) do mesmo jeito que
	 * mudar o zoom ja fazia.
	 */
	private int resultRowHeightBasePx() {
		return ROW_SPACING_LEVELS[rowSpacingIndex];
	}

	/** Define o espacamento de linha da grade (0..ROW_SPACING_LEVELS.length-1) e reconstroi as grades abertas. */
	private void setRowSpacingIndex(int index) {
		rowSpacingIndex = clampRowSpacingIndex(index);
		refreshDynamicSizing();
		saveUiState();
		if (statusBar != null) {
			statusBar.setText(" Espacamento de linhas da grade: " + ROW_SPACING_LABELS[rowSpacingIndex] + ".");
		}
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
			objectTree.setRowHeight(scaledPx(ConnectionsPanel.DEFAULT_ROW_HEIGHT));
		}
		if (connectionsPanel != null) {
			connectionsPanel.setRowHeight(scaledPx(ConnectionsPanel.DEFAULT_ROW_HEIGHT));
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
			uiPrefsStore.save(new UiPreferences.State(sidebarOnRight, resultsVertical, zoomIndex, compactMode,
					keepAliveEnabled, keepAliveIntervalMs / 1000, rowSpacingIndex));
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
		Typography.tertiary(statusBar);

		connProgress = new JProgressBar();
		connProgress.setIndeterminate(true);
		connProgress.setPreferredSize(new Dimension(120, 6));
		connProgress.setVisible(false);

		JLabel brand = new JLabel("Nureal");
		brand.setFont(brand.getFont().deriveFont(Font.BOLD));
		brand.setForeground(ACCENT);

		NStatusBar footer = new NStatusBar()
				.addLeft(connStatusLabel)
				.addLeft(statusBar)
				.addRight(connProgress)
				.addRight(brand);

		setDisconnectedState();
		return footer;
	}

	// ---------- Estado da conexao (rodape) ----------

	/**
	 * Aplica a MESMA cor ao dot e ao texto de {@link #connStatusLabel}, e
	 * guarda o supplier em {@link #connStatusColorSupplier} para que
	 * {@link #toggleTheme()} possa reavaliar (o valor concreto do supplier
	 * pode mudar de tema pra tema, ex.: {@code GridTheme.COLOR_LOGIC_FALSE}).
	 */
	private void applyConnStatusColor(java.util.function.Supplier<Color> colorSupplier) {
		connStatusColorSupplier = colorSupplier;
		Color color = colorSupplier.get();
		connStatusLabel.setIcon(Icons.get(IconType.STATUS_DOT, 10, color));
		connStatusLabel.setForeground(color);
	}

	private void setDisconnectedState() {
		// Mesmo vermelho do texto logo abaixo (GridTheme.COLOR_LOGIC_FALSE) —
		// antes o dot usava um literal PROPRIO (0xDC2626), um vermelho
		// LIGEIRAMENTE diferente do texto ao lado dele, mesma familia de
		// inconsistencia que a revisao visual pede pra eliminar ("reutilizar
		// sempre as mesmas cores"). GridTheme.COLOR_LOGIC_FALSE tambem e
		// reativo ao tema, nao um literal proprio (0xB91C1C era vermelho
		// ESCURO demais pra ler sobre fundo escuro, baixo contraste no modo
		// escuro do rodape) — ver applyConnStatusColor.
		applyConnStatusColor(() -> GridTheme.COLOR_LOGIC_FALSE);
		connStatusLabel.setText("Desconectado");
		connProgress.setVisible(false);
		connectingWorkspaceName = null;
		updateWorkspaceContextBar();
	}

	private void setConnectingState(String name) {
		// Mesmo ambar do texto logo abaixo (GridTheme.HEADER_HIGHLIGHT_BORDER)
		// — antes o dot usava um literal PROPRIO (0xF59E0B), diferente do tom
		// usado pelo texto ao lado. GridTheme.HEADER_HIGHLIGHT_BORDER: mesmo
		// ambar usado no destaque de coluna da grade, ja calibrado pra
		// funcionar em claro E escuro (valor unico nos dois temas) — em vez
		// do literal 0xB45309, marrom-escuro que sumia sobre fundo escuro.
		applyConnStatusColor(() -> GridTheme.HEADER_HIGHLIGHT_BORDER);
		connStatusLabel.setText("Conectando a " + name + "...");
		connProgress.setVisible(true);
		connectingWorkspaceName = name;
		updateWorkspaceContextBar();
	}

	private void setConnectedState(String label) {
		// Inconsistencia encontrada na revisao: o dot usava ACCENT (verde da
		// MARCA, fixo) enquanto o texto ao lado usava GridTheme.COLOR_LOGIC_TRUE
		// (verde reativo ao tema) — os dois estados irmaos (desconectado/
		// conectando, acima) sempre usaram a MESMA cor pro dot e pro texto;
		// unificado aqui tambem, no verde reativo (mesmo booleano "verdadeiro"
		// da grade — 0x047857 no claro tambem sumia sobre fundo escuro).
		applyConnStatusColor(() -> GridTheme.COLOR_LOGIC_TRUE);
		connStatusLabel.setText("Conectado: " + label);
		connProgress.setVisible(false);
		connectingWorkspaceName = null;
		updateWorkspaceContextBar();
	}

	// ---------- Barra de contexto do workspace (conexao/banco/esquema ativos) ----------

	/**
	 * Delega para {@link ConnectionsPanel#colorForWorkspace} — a paleta e a
	 * logica de atribuicao de cor por conexao moraram para la porque so ali
	 * existe a ORDEM ESTAVEL de cadastro das conexoes, necessaria pra garantir
	 * uma cor DIFERENTE para cada conexao (indice na lista, nao mais hash do
	 * nome — hash podia repetir cor entre duas conexoes abertas ao mesmo
	 * tempo).
	 */
	private Color colorForWorkspace(String name) {
		return connectionsPanel.colorForWorkspace(name);
	}

	/**
	 * Atualiza o pequeno DOT colorido no icone de cada aba do editor (todas
	 * pertencem sempre ao mesmo workspace ATIVO — trocar de conexao
	 * reconstroi o conjunto inteiro de abas, ver {@code rebuildEditorTabs}) —
	 * chamado sempre que o estado de conexao muda (conectar, trocar de
	 * workspace, trocar de esquema, desconectar; ver {@code setConnectedState}/
	 * {@code setConnectingState}/{@code setDisconnectedState}) e tambem ao
	 * criar/restaurar abas (ver {@code addQueryTab}/{@code rebuildEditorTabs}).
	 * <p>
	 * Antes havia uma faixa de texto fixa acima das abas ("Executando em:
	 * conexao · schema: X · host:porta") — removida a pedido do usuario, por
	 * nao combinar com o resto da aplicacao. A mesma informacao continua
	 * disponivel (tooltip do dot/aba), so nao ocupa mais uma linha inteira
	 * permanentemente; a cor de identidade da conexao (ver
	 * {@link #colorForWorkspace}) fica so no dot, no mesmo lugar onde antes
	 * havia so um marcador neutro.
	 */
	private void updateWorkspaceContextBar() {
		if (editorTabs == null) {
			return; // ainda nao construida (chamada durante a montagem inicial)
		}
		Icon dot;
		String tooltip;
		// Numa conexao com varios esquemas, cada aba pode estar "ligada" a um
		// esquema diferente do que a conexao tem aberto agora (ver
		// SqlEditorPane#getSchema e onRun) — nesse caso o tooltip generico
		// abaixo (mesmo pra todas as abas) e substituido por um por-aba mais
		// abaixo, no loop.
		boolean perTabSchema = false;
		if (connectingWorkspaceName != null) {
			// Mesmo ambar de GridTheme.HEADER_HIGHLIGHT_BORDER usado em
			// setConnectingState() — era outro literal (0xF59E0B) igual mas
			// duplicado, cada um podendo divergir se algum dia um dos dois
			// mudasse sozinho.
			dot = ConnectionsPanel.statusDot(GridTheme.HEADER_HIGHLIGHT_BORDER);
			tooltip = "Conectando a " + connectingWorkspaceName + "...";
		} else {
			Conexao w = activeWorkspace;
			if (w == null || w.profile() == null) {
				dot = ConnectionsPanel.statusDot(new Color(0xC4C9D1));
				tooltip = "Rascunho — sem conexao (as instrucoes aqui nao serao executadas em nenhum banco)";
			} else {
				boolean connected = w.mgr().isConnected();
				Color tag = colorForWorkspace(w.name());
				dot = ConnectionsPanel.statusDot(connected ? tag : new Color(0xC4C9D1));
				String schemaPart;
				if (currentSchema != null && connected) {
					schemaPart = currentSchema.name();
				} else if (w.schemaList() != null && connected) {
					schemaPart = "selecione um esquema";
				} else {
					schemaPart = "-";
				}
				if (connected) {
					tooltip = "Executando em: " + w.name() + "   ·   schema: " + schemaPart
							+ "   ·   " + w.profile().host() + ":" + w.profile().port();
					perTabSchema = w.schemaList() != null;
				} else {
					tooltip = "Desconectado: " + w.name() + "   ·   ultimo schema: "
							+ (w.schema() != null ? w.schema().name() : "-");
				}
			}
		}
		for (int i = 0; i < editorTabs.getTabCount(); i++) {
			Component c = editorTabs.getComponentAt(i);
			if (c == plusTab) {
				continue;
			}
			editorTabs.setIconAt(i, dot);
			String tabTooltip = tooltip;
			if (perTabSchema && c instanceof SqlEditorPane sep) {
				String tabSchema = (sep.getSchema() != null) ? sep.getSchema() : "selecione um esquema";
				tabTooltip = "Executando em: " + activeWorkspace.name() + "   ·   schema desta aba: " + tabSchema
						+ "   ·   " + activeWorkspace.profile().host() + ":" + activeWorkspace.profile().port();
			}
			editorTabs.setToolTipTextAt(i, tabTooltip);
		}
	}

	// ---------- Lado esquerdo ----------

	private JComponent buildLeftSide() {
		connectionsPanel = new ConnectionsPanel(connectionStore, this::connectTo, this::disconnectFrom);
		connectionsPanel.setRowHeight(scaledPx(ConnectionsPanel.DEFAULT_ROW_HEIGHT));
		savedQueriesPanel = new SavedQueriesPanel(savedQueryStore, this::openSavedQuery);
		historyPanel = new HistoryPanel(historyStore, this::openHistoryEntry);

		// Rail de icones (NDS) no lugar do antigo par abas+split: "Conexoes",
		// "Objetos", "Consultas" e "Historico" agora ocupam o MESMO espaco,
		// um de cada vez (estilo activity bar), em vez de Conexoes/Queries/
		// Historico em abas com o navegador de objetos sempre visivel
		// embaixo. Escolha explicita do usuario ao ver o mockup, ciente do
		// tradeoff de perder a visibilidade simultanea de Conexoes+Objetos.
		leftContent = new JPanel(new CardLayout());
		leftContent.add(connectionsPanel, CARD_CONNECTIONS);
		leftContent.add(buildObjectBrowser(), CARD_OBJECTS);
		leftContent.add(savedQueriesPanel, CARD_QUERIES);
		leftContent.add(historyPanel, CARD_HISTORY);

		leftIconRail = new NIconRail()
				.addItem(CARD_CONNECTIONS, IconType.CONNECTION, "Conexoes")
				.addItem(CARD_OBJECTS, IconType.DATABASE, "Objetos")
				.addItem(CARD_QUERIES, IconType.SAVE, "Consultas")
				.addItem(CARD_HISTORY, IconType.HISTORY, "Historico")
				.onSelect(this::showLeftCard);

		JPanel container = new JPanel(new BorderLayout());
		container.add(leftIconRail, BorderLayout.WEST);
		container.add(leftContent, BorderLayout.CENTER);
		container.setPreferredSize(new Dimension(280, 100));
		return container;
	}

	/** Troca qual painel da lateral esta visivel (ver {@link #leftIconRail}). */
	private void showLeftCard(String cardId) {
		((CardLayout) leftContent.getLayout()).show(leftContent, cardId);
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
		// Mesma base de altura do cartao de conexao (ver
		// ConnectionsPanel#DEFAULT_ROW_HEIGHT) — unica fonte de verdade para
		// as duas listas/arvores da lateral, agora que ambas tem a mesma
		// composicao visual (icone pequeno + texto) em toda linha.
		objectTree.setRowHeight(scaledPx(ConnectionsPanel.DEFAULT_ROW_HEIGHT));
		objectTree.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 4));
		objectTree.setCellRenderer(new ObjectTreeCellRenderer());
		// Estado de hover (destaque suave sob o mouse, sem mexer na selecao) —
		// faltava aqui, unico dos 5 estados pedidos na revisao visual que a
		// arvore ainda nao tinha (ver TreeHoverTracker).
		TreeHoverTracker.installOnTree(objectTree);
		// Duplo-clique num objeto abrivel (tabela/view/procedure/.../trigger)
		// agora cola o nome no editor SQL em vez de expandir/recolher —
		// pedido explicito do usuario, igual ao comportamento de outras IDEs
		// de banco. Expandir/recolher passa a ser 100% via a setinha nativa
		// do JTree (clique simples nela), entao desligamos o toggle nativo
		// por duplo-clique da arvore inteira (setToggleClickCount(0)) e
		// tratamos categoria/schema manualmente abaixo, pra nao perder esse
		// atalho nelas.
		objectTree.setToggleClickCount(0);
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
				if (e.getClickCount() == 2) {
					handleObjectTreeDoubleClick(e);
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

		// Buttons.iconButton (nao mais "new JButton(Icons.get(...))" +
		// styleIconButton solto): o icone agora se refaz sozinho a cada troca
		// de tema — antes, estes 3 icones ficavam com a cor MUTED_TEXT
		// CONGELADA no tema em que a janela abriu (bug sistemico encontrado
		// na revisao de codigo, ver javadoc de Buttons#iconButton).
		JButton switchSchemaButton = Buttons.iconButton(IconType.DATABASE, 13, () -> GridTheme.MUTED_TEXT);
		switchSchemaButton.setToolTipText("Trocar esquema / ver todos os esquemas");
		switchSchemaButton.addActionListener(e -> switchSchema());

		JButton refreshObjectsButton = Buttons.iconButton(IconType.REFRESH, 13, () -> GridTheme.MUTED_TEXT);
		refreshObjectsButton.setToolTipText("Atualizar objetos (Ctrl+R)");
		refreshObjectsButton.addActionListener(e -> refreshObjectTree(true));

		JButton createSchemaButton = Buttons.iconButton(IconType.NEW, 13, () -> GridTheme.MUTED_TEXT);
		createSchemaButton.setToolTipText("Criar esquema...");
		createSchemaButton.addActionListener(e -> createSchema());

		JPanel headerButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
		headerButtons.setOpaque(false);
		headerButtons.add(switchSchemaButton);
		headerButtons.add(refreshObjectsButton);
		headerButtons.add(createSchemaButton);

		JPanel headerRow = new JPanel(new BorderLayout());
		headerRow.setOpaque(false);
		headerRow.add(sectionHeader("OBJETOS"), BorderLayout.WEST);
		headerRow.add(headerButtons, BorderLayout.EAST);

		// Gap de 6px (nao 8) entre o titulo e a busca — mesmo valor que
		// ConnectionsPanel/HistoryPanel/SavedQueriesPanel ja usam no cabecalho
		// interno deles (ver ConnectionsPanel#buildHeader), pra este painel
		// nao ser o unico com um espacamento levemente diferente dos outros
		// tres (spec de padronizacao visual: "todo painel deve respeitar o
		// mesmo espacamento").
		JPanel top = new JPanel(new BorderLayout(0, 6));
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
		// Mesma largura minima das abas de Resultados (ver resultTabs abaixo)
		// — sem isto, abrir varias queries deixava as abas do editor
		// afinarem muito mais que as de Resultados, uma inconsistencia de
		// tamanho entre as duas areas de abas mais usadas do app.
		editorTabs.putClientProperty("JTabbedPane.minimumTabWidth", 96);
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
				updateSaveButtonState();
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
		// Dot inicial nas abas ja criadas por initWorkspaces() acima — ver
		// updateWorkspaceContextBar() (chamada de novo a cada troca de estado
		// de conexao, alem de ao criar/restaurar abas).
		updateWorkspaceContextBar();

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

	/**
	 * Abre uma aba NOVA (id gerado agora) — usado por "+", "abrir query salva"
	 * etc. Herda o esquema ABERTO NO MOMENTO na conexao ativa (se houver),
	 * para que uma aba criada com um esquema ja selecionado "lembre" dele
	 * desde o inicio (ver {@link #onRun}).
	 */
	private boolean addQueryTab(String title, String sql) {
		return addQueryTab(title, sql, UUID.randomUUID().toString(), currentActiveSchemaName());
	}

	/**
	 * Abre uma aba com o id ESPECIFICADO mas SEM esquema explicito (herda o
	 * esquema aberto no momento, mesma regra do "+") — hoje sem chamadores
	 * diretos (a restauracao de sessao usa a variante de 4 argumentos, com o
	 * esquema persistido daquela aba), mantido para quem precisar de um id
	 * fixo sem amarrar a um esquema especifico.
	 */
	private boolean addQueryTab(String title, String sql, String tabId) {
		return addQueryTab(title, sql, tabId, currentActiveSchemaName());
	}

	/**
	 * Nome do esquema aberto no momento na conexao ativa, ou {@code null} se
	 * nao houver conexao ativa ou nenhum esquema selecionado ainda. Usado
	 * para que abas NOVAS ja nascam "ligadas" ao esquema atual (ver
	 * {@code addQueryTab}).
	 */
	private String currentActiveSchemaName() {
		return (activeWorkspace != null && activeWorkspace.schema() != null) ? activeWorkspace.schema().name() : null;
	}

	/**
	 * Igual a {@link #addQueryTab(String, String, String)}, mas com o
	 * ESQUEMA explicito da aba — usado ao restaurar uma sessao salva (o
	 * esquema vem do que foi persistido para aquela aba especifica, ver
	 * {@code SessionStore.Tab#schema}), em vez de herdar o esquema atual.
	 */
	private boolean addQueryTab(String title, String sql, String tabId, String schema) {
		if (realTabCount() >= MAX_TABS) {
			if (statusBar != null) {
				statusBar.setText(" Limite de " + MAX_TABS + " abas atingido.");
			}
			return false;
		}
		// Referencia circular inevitavel: o callback "Mais opcoes" precisa
		// saber QUAL SqlEditorPane o chamou (pra abrir o menu de contexto da
		// aba certa), mas so existe DEPOIS que o construtor terminar. Um
		// array de 1 posicao guarda a instancia assim que ela fica pronta —
		// o lambda so LE holder[0] quando o usuario de fato clicar no botao
		// (bem depois), nunca durante a propria construcao.
		final SqlEditorPane[] holder = new SqlEditorPane[1];
		SqlEditorPane pane = new SqlEditorPane(tabId, completionProvider, this::onRun, this::currentSqlFormatter,
				formatState.editorFontFamily(), () -> currentSchema, this::openEditorObject, this::navigateBack,
				this::showHistoryPanel, this::toggleEditorFocusMode,
				anchor -> showTabOptionsMenu(holder[0], anchor), this::currentConnectionLabel);
		holder[0] = pane;
		pane.setSchema(schema);
		pane.textArea().setText(sql);
		pane.textArea().setCaretPosition(0);
		// Carregar o SQL salvo/restaurado NAO pode entrar no historico de
		// desfazer desta aba nova — sem isto, um Ctrl+Z logo ao abrir a aba
		// apagaria a query inteira (ver SqlEditorPane#discardUndoHistory).
		pane.discardUndoHistory();
		pane.textArea().getDocument().addDocumentListener(new DocumentListener() {
			@Override
			public void insertUpdate(DocumentEvent e) {
				scheduleSave();
				updateSaveButtonState();
			}

			@Override
			public void removeUpdate(DocumentEvent e) {
				scheduleSave();
				updateSaveButtonState();
			}

			@Override
			public void changedUpdate(DocumentEvent e) {
				scheduleSave();
				updateSaveButtonState();
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
		// addingTab suprime o listener de troca de aba acima (evita reentrancia
		// com a aba "+"), entao o estado do botao Salvar precisa ser atualizado
		// aqui na mao — sem isto, uma aba nova (sempre vazia) mostrava "Salvar"
		// habilitado ate o usuario clicar em outra aba e voltar.
		updateSaveButtonState();
		// Aba nova criada fora de uma troca de estado de conexao (ex.: clique
		// no "+") nao ganharia o dot de identidade ate o proximo evento de
		// conexao — aplica de imediato (ver updateWorkspaceContextBar).
		updateWorkspaceContextBar();
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
		buildTabContextMenu(target).show(editorTabs, e.getX(), e.getY());
	}

	/**
	 * Monta o menu de contexto de uma aba (Renomear/Salvar como query/Fechar/
	 * Fechar as outras) — extraido de {@link #maybeTabMenu} para tambem ser
	 * usado pelo botao "Mais opcoes" da barra de acoes rapidas do editor (ver
	 * {@link #showTabOptionsMenu}), sem duplicar a lista de itens em dois
	 * lugares.
	 */
	private JPopupMenu buildTabContextMenu(Component target) {
		JPopupMenu menu = new JPopupMenu();
		JMenuItem rename = new JMenuItem("Renomear...");
		rename.addActionListener(a -> renameTab(target));
		JMenuItem saveQuery = new JMenuItem("Salvar como query...");
		saveQuery.addActionListener(a -> {
			editorTabs.setSelectedComponent(target);
			onSaveQuery();
		});
		JMenuItem close = new JMenuItem("Fechar");
		close.addActionListener(a -> closeTabComponent(target));
		JMenuItem closeOthers = new JMenuItem("Fechar as outras");
		closeOthers.addActionListener(a -> closeOtherTabs(target));
		menu.add(rename);
		menu.add(saveQuery);
		menu.addSeparator();
		menu.add(close);
		menu.add(closeOthers);
		return menu;
	}

	/**
	 * Abre o MESMO menu de contexto da aba (ver {@link #buildTabContextMenu}),
	 * ancorado no botao "Mais opcoes" (icone {@code ...}) da barra de acoes
	 * rapidas do PROPRIO editor — chamado pelo {@code onMoreOptions} passado a
	 * cada {@link SqlEditorPane} (ver {@code addQueryTab}), com {@code pane}
	 * sendo a instancia que disparou o clique e {@code anchor} o botao em si
	 * (usado so para posicionar o popup logo abaixo dele).
	 */
	private void showTabOptionsMenu(SqlEditorPane pane, JComponent anchor) {
		if (pane == null) {
			return;
		}
		buildTabContextMenu(pane).show(anchor, 0, anchor.getHeight());
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
		Conexao scratch = new Conexao(SCRATCH, null, bootstrapConnectionManager);
		SessionStore.Session sc = savedSessions.get(SCRATCH);
		if (sc != null) {
			scratch.tabs = new ArrayList<>(sc.tabs());
			scratch.selectedTab = sc.selectedIndex();
		}
		workspaces.put(SCRATCH, scratch);
		activeWorkspace = scratch;
		if (savedQueriesPanel != null) {
			savedQueriesPanel.setActiveConnection(null);
		}
		if (historyPanel != null) {
			historyPanel.setActiveConnection(null);
		}
		rebuildEditorTabs(scratch.tabs, scratch.selectedTab, scratch.tabResults);
	}

	/**
	 * Reconstroi as abas do editor a partir do conteudo salvo (titulo + SQL)
	 * e restaura os resultados de cada uma, se houver (ver
	 * {@code Conexao#tabResults}) — usado tanto na inicializacao quanto ao
	 * trocar de workspace/conexao (ver {@code activateWorkspace}): trocar de
	 * conexao e voltar tem que devolver os resultados que cada aba tinha
	 * antes da troca, nao uma tela vazia.
	 */
	private void rebuildEditorTabs(List<SessionStore.Tab> tabs, int selected, Map<String, List<QueryResult>> savedResults) {
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
				addQueryTab(title, t.sql(), t.id(), t.schema());
			}
		}
		addPlusTab();
		// Restaura os resultados salvos (indexados pelo ID ESTAVEL da aba, ver
		// Conexao#tabResults) nas instancias NOVAS de SqlEditorPane recem
		// criadas acima — tem que ser DEPOIS de cria-las (para termos as
		// instancias certas como chave) e ANTES do showResultsForActiveEditor
		// no final (para ele ja encontrar o resultado, se houver). Como cada
		// pane foi criado acima com addQueryTab(title, sql, t.id()), seu
		// tabId() e igual ao id salvo — a busca e direta, sem depender de
		// posicao/ordem das abas.
		if (savedResults != null && !savedResults.isEmpty()) {
			for (int i = 0; i < editorTabs.getTabCount(); i++) {
				Component c = editorTabs.getComponentAt(i);
				if (c instanceof SqlEditorPane sep) {
					List<QueryResult> saved = savedResults.get(sep.tabId());
					if (saved != null) {
						resultsByTab.put(sep, saved);
					}
				}
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

	/**
	 * Primeira aba ABERTA (no {@code editorTabs} ao vivo, nao no {@code
	 * SessionStore} salvo em disco) marcada com {@code schemaName} (ver
	 * {@link SqlEditorPane#getSchema()}) — usada por {@link #openSchema} para
	 * trocar de aba ao trocar de esquema, em vez de so deixar a aba de OUTRO
	 * esquema selecionada por acaso. {@code null} se nenhuma aba aberta tiver
	 * sido usada ainda com esse esquema nesta sessao.
	 */
	private SqlEditorPane findTabForSchema(String schemaName) {
		if (schemaName == null) {
			return null;
		}
		for (int i = 0; i < editorTabs.getTabCount(); i++) {
			Component c = editorTabs.getComponentAt(i);
			if (c instanceof SqlEditorPane sep && schemaName.equals(sep.getSchema())) {
				return sep;
			}
		}
		return null;
	}

	/** Captura o conteudo atual das abas do editor (titulo + SQL). */
	private List<SessionStore.Tab> collectTabs() {
		List<SessionStore.Tab> list = new ArrayList<>();
		for (int i = 0; i < editorTabs.getTabCount(); i++) {
			Component c = editorTabs.getComponentAt(i);
			if (c instanceof SqlEditorPane sep) {
				list.add(new SessionStore.Tab(editorTabs.getTitleAt(i), sep.textArea().getText(), sep.tabId(),
						sep.getSchema()));
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
	 * indexados pelo ID ESTAVEL da aba (nao pela instancia de SqlEditorPane
	 * nem pela POSICAO — ver javadoc de {@code Conexao#tabResults}). Chamado
	 * ao SAIR de um workspace (ver {@code saveActiveTabs}), para que
	 * {@code rebuildEditorTabs} consiga devolve-los quando o usuario voltar.
	 */
	private Map<String, List<QueryResult>> snapshotTabResults() {
		Map<String, List<QueryResult>> snapshot = new HashMap<>();
		for (int i = 0; i < editorTabs.getTabCount(); i++) {
			Component c = editorTabs.getComponentAt(i);
			if (c == plusTab) {
				continue;
			}
			if (c instanceof SqlEditorPane sep) {
				List<QueryResult> results = resultsByTab.get(sep);
				if (results != null) {
					snapshot.put(sep.tabId(), results);
				}
			}
		}
		return snapshot;
	}

	/**
	 * Ativa um workspace: guarda as abas do ativo (SQL + resultados),
	 * troca a conexao corrente, reconstroi as abas do alvo (restaurando os
	 * resultados que ele tinha da ultima vez que esteve ativo) e atualiza
	 * navegador/autocomplete/indicadores.
	 */
	private void activateWorkspace(Conexao w) {
		saveActiveTabs();
		activeWorkspace = w;
		// Nao precisa mais copiar w.mgr pra um campo separado: connectionManager()
		// ja devolve activeWorkspace.mgr ao vivo (ver o metodo, logo abaixo).
		if (savedQueriesPanel != null) {
			savedQueriesPanel.setActiveConnection(w.profile == null ? null : w.profile.name());
		}
		if (historyPanel != null) {
			historyPanel.setActiveConnection(w.profile == null ? null : w.profile.name());
		}
		rebuildEditorTabs(w.tabs, w.selectedTab, w.tabResults);
		if (w.schema != null) {
			metadataCache.set(w.schema);
			completionProvider.refresh(w.schema);
			populateTree(w.schema);
		} else if (w.schemaList != null) {
			completionProvider.refresh(null);
			buildSchemaPicker(w.schemaList);
		} else {
			setCurrentSchema(null);
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

	/**
	 * Conexao JDBC da CONEXAO ATIVA (nao mais um campo espelhado e reatribuido
	 * manualmente a cada troca de workspace — ver {@link #activateWorkspace}):
	 * le direto de {@code activeWorkspace}, entao nunca corre o risco de ficar
	 * "desatualizado" em relacao a ele. So cai no {@code bootstrapConnectionManager}
	 * na janela minuscula ANTES de {@code initWorkspaces()} definir
	 * {@code activeWorkspace} pela 1a vez (nao deveria acontecer na pratica,
	 * ja que nada mais roda antes disso, mas evita NPE se algo mudar).
	 */
	private ConnectionManager connectionManager() {
		return (activeWorkspace != null) ? activeWorkspace.mgr : bootstrapConnectionManager;
	}

	/**
	 * UNICO ponto de ESCRITA do "esquema atual" ({@link #currentSchema}):
	 * mantem ele e {@code activeWorkspace.schema} sempre em sincronia num so
	 * lugar, em vez de cada trecho do codigo ter que lembrar de atualizar os
	 * dois separadamente (o jeito antigo — risco real de esquecer um dos dois
	 * e eles divergirem silenciosamente). {@code schema == null} desmarca
	 * (nenhum esquema selecionado no momento, ex.: workspace sem conexao).
	 */
	private void setCurrentSchema(SchemaInfo schema) {
		currentSchema = schema;
		if (activeWorkspace != null) {
			activeWorkspace.schema = schema;
		}
	}

	/** Atualiza as bolinhas (conectados) e o indicador de status do rodape. */
	private void refreshConnectionIndicators() {
		Set<String> connected = new HashSet<>();
		for (Conexao w : workspaces.values()) {
			if (w.profile != null && w.mgr.isConnected()) {
				connected.add(w.name);
			}
		}
		connectionsPanel.setConnectedNames(connected);
		connectionsPanel.setActiveName(
				(activeWorkspace != null && activeWorkspace.profile != null) ? activeWorkspace.name : null);
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
		for (Conexao w : workspaces.values()) {
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

	// ---------- Chat de IA (Ollama local) ----------
	// Ver com.nureal.ide.ui.ai.ChatWindow/ChatController/DefaultAgent. Toda a
	// logica de IA fica em core.ai (Swing-free); aqui so montamos o grafo de
	// objetos (Provider/ToolExecutor/ContextProvider/Agent) a partir dos
	// servicos que o MainWindow ja tem, via IdeContextAccessor.

	/** Uma unica conversa persistente para o MVP — sem seletor de conversas ainda. */
	private static final String AI_CONVERSATION_ID = "default";

	private void openAiChat() {
		AiPreferences aiPreferences = new AiPreferences();
		AiCredentialsStore aiCredentials = new AiCredentialsStore();
		ChatHistoryStore chatHistoryStore = new ChatHistoryStore();
		Agent agent = buildAiAgent(aiPreferences, aiCredentials, chatHistoryStore);
		ChatActions actions = new ChatActions(this::runSqlFromChat, this::currentSqlFormatter, sql -> { });
		ChatWindow.open(this, agent, chatHistoryStore, AI_CONVERSATION_ID,
				() -> openAiSettings(aiPreferences, aiCredentials), actions);
	}

	/**
	 * "Executar" de um card SQL do chat de IA — abre uma aba nova com a
	 * consulta (preserva as abas existentes do usuario) e roda pelo MESMO
	 * caminho do botao "Executar" da toolbar ({@link #onRun}), reusando toda
	 * a logica de resolucao de schema/conexao em vez de duplicar.
	 */
	private void runSqlFromChat(String sql) {
		if (addQueryTab("Chat: consulta", sql)) {
			onRun();
		}
	}

	private void openAiSettings(AiPreferences aiPreferences, AiCredentialsStore aiCredentials) {
		AiSettingsDialog.open(this, aiPreferences, aiCredentials,
				() -> ChatWindow.updateAgent(buildAiAgent(aiPreferences, aiCredentials, new ChatHistoryStore())));
	}

	private Agent buildAiAgent(AiPreferences aiPreferences, AiCredentialsStore aiCredentials,
			ChatHistoryStore chatHistoryStore) {
		AiPreferences.State prefs;
		try {
			prefs = aiPreferences.load();
		} catch (IOException e) {
			AppLogger.warning("Falha ao carregar configuracao de IA", e);
			prefs = AiPreferences.State.defaults();
		}
		LLMProvider provider = LLMProviderFactory.create(prefs, aiCredentials);
		IdeStateAccessor accessor = new IdeContextAccessor(
				this::connectionManager,
				() -> metadataService,
				() -> currentSchema,
				() -> currentSchema != null ? currentSchema.name() : null,
				this::activeConnectionLabelForAi,
				this::databaseProductNameForAi,
				this::databaseVersionForAi,
				this::currentEditorSqlForAi,
				this::hasEditorSelectionForAi,
				this::lastExecutionForAi);
		ToolExecutor toolExecutor = new ToolExecutor(
				List.of(new ListTablesTool(accessor), new DescribeTableTool(accessor)));
		ContextProvider contextProvider = new DefaultContextProvider(accessor);
		return new DefaultAgent(provider, contextProvider, toolExecutor, chatHistoryStore, () -> {
			try {
				return aiPreferences.load();
			} catch (IOException e) {
				AppLogger.warning("Falha ao carregar configuracao de IA", e);
				return AiPreferences.State.defaults();
			}
		});
	}

	/** Rotulo da conexao ativa SEM senha (ver ConnectionProfile — nunca usar toString() nele). */
	private String activeConnectionLabelForAi() {
		ConnectionManager mgr = connectionManager();
		if (mgr == null || !mgr.isConnected() || mgr.profile() == null) {
			return null;
		}
		ConnectionProfile p = mgr.profile();
		return p.host() + ":" + p.port() + "/" + p.schema() + " (" + p.user() + ")";
	}

	private String currentEditorSqlForAi() {
		SqlEditorPane editor = currentEditor();
		if (editor == null) {
			return null;
		}
		String selected = editor.textArea().getSelectedText();
		if (selected != null && !selected.isBlank()) {
			return selected;
		}
		return editor.textArea().getText();
	}

	private boolean hasEditorSelectionForAi() {
		SqlEditorPane editor = currentEditor();
		if (editor == null) {
			return false;
		}
		String selected = editor.textArea().getSelectedText();
		return selected != null && !selected.isBlank();
	}

	/** Nome/versao do banco conectado (ex.: "MySQL"/"8.4.0") — usado pra resolver o Database Specialist. */
	private String databaseProductNameForAi() {
		ConnectionManager mgr = connectionManager();
		if (mgr == null || !mgr.isConnected()) {
			return null;
		}
		try {
			return mgr.getConnection().getMetaData().getDatabaseProductName();
		} catch (SQLException e) {
			return null;
		}
	}

	private String databaseVersionForAi() {
		ConnectionManager mgr = connectionManager();
		if (mgr == null || !mgr.isConnected()) {
			return null;
		}
		try {
			return mgr.getConnection().getMetaData().getDatabaseProductVersion();
		} catch (SQLException e) {
			return null;
		}
	}

	/** Ultima execucao registrada (ExecutionHistoryStore) da conexao ativa, pro ExecutionContext de IA. */
	private Optional<ExecutionHistoryStore.Entry> lastExecutionForAi() {
		String connectionName = currentConnectionLabel();
		try {
			return historyStore.loadAll().stream()
					.filter(e -> java.util.Objects.equals(e.connectionName(), connectionName))
					.max(Comparator.comparingLong(ExecutionHistoryStore.Entry::executedAt));
		} catch (IOException e) {
			AppLogger.warning("Falha ao carregar historico de execucao para o contexto de IA", e);
			return Optional.empty();
		}
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
		orientationToggle.addActionListener(e -> toggleResultsOrientation());
		updateOrientationToggleIcon(orientationToggle);
		// Sem isto, o icone (tipo E cor) so era recalculado quando o PROPRIO
		// botao era clicado — trocar o tema sem mexer na orientacao deixava
		// este icone especifico com a cor MUTED_TEXT do tema antigo (mesmo
		// bug sistemico corrigido nos demais botoes-so-de-icone, ver
		// Buttons#bindThemedIcon; aqui e manual porque o TIPO do icone
		// tambem depende de estado, nao so a cor).
		orientationToggle.addPropertyChangeListener("UI", e -> updateOrientationToggleIcon(orientationToggle));
		this.resultsOrientationButton = orientationToggle;

		// Atalho para exportar TODOS os resultados abertos (mesma acao do
		// "Exportar > Exportar todos" no rodape de cada aba de resultado, ver
		// ResultStatusBar) — so uma segunda porta de entrada pro mesmo
		// recurso ja existente, pensado pra ficar ao alcance sem precisar
		// abrir o menu do botao la embaixo. Estilo identico ao restante dos
		// icones da barra de ferramentas (ver #buildToolbar), pra esta linha
		// nao parecer "de outro app" dentro da mesma janela.
		JButton exportAllButton = new JButton();
		Buttons.bindThemedIcon(exportAllButton, IconType.EXPORT, 14, () -> GridTheme.MUTED_TEXT);
		exportAllButton.setToolTipText("Exportar todos os resultados abertos (uma aba por resultado)");
		exportAllButton.addActionListener(e -> exportAll());

		// "Expandir": empurra o divisor central quase todo pro lado dos
		// resultados e esconde o painel lateral (ver #toggleResultsFocusMode)
		// — o editor nao some de vez (so fica com uma faixa minima), pra nao
		// perder o contexto de qual instrucao gerou o resultado. Clicar de
		// novo (aqui ou no botao espelho do editor) desfaz.
		JButton expandResultsButton = new JButton();
		Buttons.bindThemedIcon(expandResultsButton, IconType.EXPAND, 14, () -> GridTheme.MUTED_TEXT);
		expandResultsButton.setToolTipText("Expandir/recolher resultados (oculta paineis laterais)");
		expandResultsButton.addActionListener(e -> toggleResultsFocusMode());

		for (JButton btn : new JButton[] { exportAllButton, orientationToggle, expandResultsButton }) {
			Buttons.styleIconButton(btn);
		}

		JPanel headerIcons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 3, 0));
		headerIcons.setOpaque(false);
		headerIcons.add(exportAllButton);
		headerIcons.add(orientationToggle);
		headerIcons.add(expandResultsButton);

		JPanel header = new JPanel(new BorderLayout());
		header.setOpaque(false);
		header.add(sectionHeader("RESULTADOS"), BorderLayout.WEST);
		header.add(headerIcons, BorderLayout.EAST);

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
		button.setIcon(resultsVertical ? Icons.get(IconType.PANEL_LEFT, 14, GridTheme.MUTED_TEXT)
				: Icons.get(IconType.PANEL_BOTTOM, 14, GridTheme.MUTED_TEXT));
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
		label.setFont(label.getFont().deriveFont(13f));
		// Nivel PRIMARIO (mensagem de destaque) — mesma correcao do
		// buildEmptyState logo abaixo: so definia o peso, sem cor explicita.
		Typography.primary(label);

		JProgressBar spinner = new JProgressBar();
		spinner.setIndeterminate(true);
		spinner.setPreferredSize(new Dimension(200, 6));
		spinner.setMaximumSize(new Dimension(200, 6));
		spinner.setAlignmentX(Component.CENTER_ALIGNMENT);

		JButton cancel = new JButton("Cancelar");
		cancel.setAlignmentX(Component.CENTER_ALIGNMENT);
		cancel.addActionListener(e -> cancelExecution());
		// Mesmo padrao secundario (contorno) de qualquer outro botao do app —
		// antes era um JButton cru, unico botao "fora do padrao" da janela
		// principal (ver Buttons).
		Buttons.styleSecondary(cancel);

		JPanel card = new JPanel();
		card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
		card.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(0xE0E3E7)),
				BorderFactory.createEmptyBorder(18, 28, 18, 28)));
		card.add(label);
		card.add(Box.createVerticalStrut(12));
		card.add(spinner);
		card.add(Box.createVerticalStrut(14));
		card.add(cancel);
		executingCard = card;

		// O fundo do card e o "esfumacado" por tras dele eram fixos em cores
		// claras (branco/cinza quase branco) — no tema escuro isso aparecia
		// como uma caixa branca chocante flutuando no meio de uma janela
		// escura toda vez que uma consulta rodava (bug relatado: "essas
		// funcionalidades nao ficavam boas" no tema escuro). O scrim (dim)
		// le FlatLaf.isLafDark() dentro do proprio paintComponent — repinta
		// a cada vez que o overlay aparece (ver showExecuting), entao esta
		// sempre correto; o card em si e estilizado por #styleExecutingOverlay,
		// chamado aqui E de novo em showExecuting(true), ja que o Look and
		// Feel pode ter sido alternado enquanto o overlay estava escondido.
		JPanel overlay = new JPanel(new GridBagLayout()) {
			private static final long serialVersionUID = 1L;

			@Override
			protected void paintComponent(Graphics g) {
				g.setColor(FlatLaf.isLafDark() ? new Color(10, 11, 13, 190) : new Color(244, 245, 247, 205));
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
		styleExecutingOverlay();
		return overlay;
	}

	/**
	 * Cores do card "Executando consulta..." — separado de {@link #buildResultsOverlay}
	 * (chamado uma unica vez) para poder ser chamado de novo sempre que o
	 * overlay for exibido (ver {@link #showExecuting}), pegando o tema ATUAL
	 * mesmo que o usuario tenha alternado claro/escuro enquanto nenhuma
	 * consulta estava rodando.
	 */
	private void styleExecutingOverlay() {
		if (executingCard == null) {
			return;
		}
		boolean dark = FlatLaf.isLafDark();
		executingCard.setBackground(dark ? new Color(0x2B, 0x2D, 0x30) : new Color(0xFF, 0xFF, 0xFF));
		executingCard.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(dark ? new Color(0x44, 0x48, 0x4D) : new Color(0xE0, 0xE3, 0xE7)),
				BorderFactory.createEmptyBorder(18, 28, 18, 28)));
	}

	private void showExecuting(boolean executing) {
		if (resultsOverlay != null) {
			if (executing) {
				styleExecutingOverlay();
			}
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
		// GridTheme.MUTED_TEXT (reativo ao tema) em vez de um literal PROPRIO
		// (0xCBD5E1) que so funcionava bem no tema escuro — no tema claro
		// esse mesmo tom (um cinza-azulado claro) tinha baixo contraste
		// contra o fundo claro do painel.
		// buildEmptyState() e chamado UMA vez so, no arranque (ver
		// resultsCards.add(..., "empty")) — sem Buttons.bindThemedIcon, este
		// icone ficava congelado no tema de arranque se o usuario trocasse de
		// tema ANTES de rodar a primeira consulta (mesmo bug sistemico
		// corrigido no resto do app, ver Buttons#bindThemedIcon).
		JLabel icon = new JLabel();
		Buttons.bindThemedIcon(icon, IconType.TABLE, 46, () -> GridTheme.MUTED_TEXT);
		icon.setAlignmentX(Component.CENTER_ALIGNMENT);

		JLabel title = new JLabel("Execute uma consulta para ver os resultados");
		title.setFont(title.getFont().deriveFont(14f));
		// Nivel PRIMARIO (mensagem de destaque) — antes so definia o peso
		// (Bold) sem nenhuma cor explicita, caindo no padrao do L&F por
		// acaso em vez do alto contraste que todo outro titulo do app usa.
		Typography.primary(title);
		title.setAlignmentX(Component.CENTER_ALIGNMENT);

		JLabel sub = new JLabel("Os resultados da consulta aparecerao aqui");
		Typography.tertiary(sub);
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

	/** Delega para {@link Typography#sectionHeader} — ponto UNICO desta receita, compartilhado com os paineis laterais. */
	private JLabel sectionHeader(String text) {
		return Typography.sectionHeader(text);
	}

	// ---------- Tema ----------

	private void toggleTheme() {
		dark = !dark;
		if (dark) {
			FlatDarkLaf.setup();
		} else {
			FlatLightLaf.setup();
		}
		// CRITICAL: GridTheme.applyPalette PRECISA rodar ANTES de
		// FlatLaf.updateUI() (nao depois, como era antes). FlatLaf.updateUI()
		// e quem percorre TODAS as janelas abertas (Window.getWindows() +
		// updateComponentTreeUI) e dispara, em cascata, o updateUI() de cada
		// componente — inclusive o override em ResultGrid$JTable (reaplica
		// styleTable) e em qualquer outra janela flutuante com chrome proprio
		// (ex.: FkInspectorWindow). Se a paleta so for trocada DEPOIS desse
		// passo, toda janela SECUNDARIA (inspetor de FK, propriedades de
		// objeto etc. — qualquer uma alem desta MainWindow) redesenha a
		// tempo, mas ainda com as cores ANTIGAS de GridTheme, porque elas so
		// mudam no passo seguinte — o resultado so aparece certo depois de
		// fechar e abrir a janela de novo (bug relatado pelo usuario: "eu
		// preciso fechar e abrir de novo"). A MainWindow escapava do bug
		// porque reconstroi sua PROPRIA grade explicitamente mais abaixo
		// (showResultsForActiveEditor), depois da paleta trocada — mas
		// nenhuma outra janela tinha esse retoque manual.
		GridTheme.applyPalette(dark);
		FlatLaf.updateUI();
		themeButton
				.setIcon(dark ? Icons.get(IconType.THEME_LIGHT, 16, GridTheme.MUTED_TEXT) : Icons.get(IconType.THEME_DARK, 16, GridTheme.MUTED_TEXT));
		// connStatusLabel (dot + texto do rodape) nao e um componente PADRAO
		// do FlatLaf, entao FlatLaf.updateUI() nao o alcanca — sem isto, o dot
		// ficava com a cor GridTheme do tema anterior ate a proxima mudanca
		// de estado de conexao (ver applyConnStatusColor).
		applyConnStatusColor(connStatusColorSupplier);
		styleRunButton();
		// Mesmo motivo: UpdateBanner tem setBackground/setBorder proprios
		// (ver seu javadoc), fora do alcance do FlatLaf.updateUI().
		if (updateBanner != null) {
			updateBanner.refreshTheme();
		}
		// FlatLaf.updateUI() so atualiza componentes Swing PADRAO (botoes,
		// paineis, arvore etc.) — a grade de resultados e o editor SQL pintam
		// sozinhos, lendo paletas proprias (ver GridTheme e
		// SqlEditorPane#applyEditorPalette) que NAO faziam parte do L&F.
		// Atualiza os pontos que ainda precisam de retoque manual NESTA janela:
		if (editorTabs != null) {
			for (int i = 0; i < editorTabs.getTabCount(); i++) {
				Component c = editorTabs.getComponentAt(i);
				if (c instanceof SqlEditorPane sep) {
					sep.refreshTheme();
				}
			}
		}
		// Resultados ja exibidos foram construidos com a paleta antiga da
		// grade — reconstroi a grade da aba ativa (showResultsForActiveEditor
		// cria um ResultGrid NOVO a partir do modelo salvo) pra refletir a
		// paleta nova imediatamente, sem esperar a proxima consulta.
		showResultsForActiveEditor();
		styleExecutingOverlay();
		// Mesmo motivo: os cards do chat de IA (MessageRenderer) tem cores de
		// RSyntaxTextArea/fundo proprias, presas no tema de quando cada
		// mensagem foi renderizada — sem isto, o chat (janela singleton que
		// pode ficar aberta atravessando um toggle) mantinha cards do tema
		// antigo ate a proxima mensagem.
		ChatWindow.refreshTheme();
	}

	// ---------- Acoes ----------

	private void connectTo(ConnectionProfile profile) {
		connectTo(profile, null);
	}

	/**
	 * Igual a {@link #connectTo(ConnectionProfile)}, mas com um callback
	 * opcional disparado (na EDT) so quando a conexao TERMINA COM SUCESSO —
	 * usado pelo modal de "nao conectado" ao executar (ver {@link #onRun}),
	 * pra rodar a instrucao logo apos conectar, sem o usuario precisar clicar
	 * em "Executar" de novo.
	 */
	private void connectTo(ConnectionProfile profile, Runnable onConnected) {
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
		Conexao existing = workspaces.get(target.name());
		if (existing != null && existing.mgr.isConnected()) {
			activateWorkspace(existing);
			statusBar.setText(" Workspace: " + target.name());
			if (onConnected != null) {
				onConnected.run();
			}
			return;
		}

		setConnectingState(target.name());
		connectionsPanel.setConnecting(target);
		runButton.setEnabled(false);
		statusBar.setText(" Conectando a " + target.host() + "...");

		final boolean pickSchema = target.schema() == null || target.schema().isBlank();
		final Conexao ws = (existing != null) ? existing
				: new Conexao(target.name(), target, new ConnectionManager(dialect));

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
					if (pickSchema) {
						statusBar.setText(
								" Conectado  (" + ((List<?>) result).size() + " esquema(s) - duplo-clique para abrir)");
						// Sem schema resolvido ainda, nao da pra rodar a instrucao
						// sozinho (falta contexto) — o usuario escolhe o schema
						// primeiro (duplo-clique) e clica Executar de novo.
					} else {
						statusBar.setText(" Conectado  (" + ws.schema.tables().size() + " tabelas)");
						if (onConnected != null) {
							onConnected.run();
						}
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
		Conexao w = workspaces.get(profile.name());
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
			setCurrentSchema(null);
			activateWorkspace(w);
		}
		refreshConnectionIndicators();
		statusBar.setText(" Desconectado de " + profile.name() + ".");
	}

	/** Monta a arvore com a lista de esquemas (duplo-clique abre o esquema). */
	private void buildSchemaPicker(List<String> schemas) {
		setCurrentSchema(null);
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
		openSchema(schemaName, null);
	}

	/**
	 * Igual a {@link #openSchema(String)}, mas roda {@code onOpened} logo
	 * apos o esquema terminar de abrir com sucesso (ex.: encadear a abertura
	 * do Diagrama ER pro esquema que acabou de carregar, quando o clique
	 * direito veio de um item da LISTA de esquemas — ver
	 * {@link #buildSchemaPickContextMenu} — em vez de um esquema ja aberto,
	 * onde {@link #openErDiagram()} sozinho ja basta). {@code null} = nao
	 * encadeia nada (comportamento igual ao de antes).
	 */
	private void openSchema(String schemaName, Runnable onOpened) {
		statusBar.setText(" Abrindo esquema " + schemaName + "...");
		new SwingWorker<SchemaInfo, Void>() {
			@Override
			protected SchemaInfo doInBackground() throws Exception {
				Connection conn = connectionManager().getConnection();
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
					// Troca de esquema (ver "Trocar esquema..." em switchSchema): se ja
					// existe uma aba marcada com ESTE esquema (de uma troca anterior
					// nesta mesma sessao — ver SqlEditorPane#getSchema/setSchema),
					// troca para ela em vez de deixar selecionada uma aba de OUTRO
					// esquema. Pedido explicito do usuario: "quando mudo de esquema
					// ele nao esta trocando as abas para abas que pertencem aquela
					// esquema, se existirem". Sem aba correspondente (caso comum: 1a
					// vez que este esquema e aberto nesta sessao), cai no
					// comportamento de sempre — a aba selecionada no momento "adota"
					// este esquema: da proxima vez que o usuario clicar em Executar
					// nela (mesmo apos abrir outra aba de outro esquema no meio do
					// caminho), onRun sabe para qual esquema conectar de volta
					// automaticamente, sem precisar vir aqui escolher de novo na
					// arvore.
					SqlEditorPane activeEditor = currentEditor();
					SqlEditorPane matchingTab = findTabForSchema(schemaName);
					if (matchingTab != null && matchingTab != activeEditor) {
						for (int i = 0; i < editorTabs.getTabCount(); i++) {
							if (editorTabs.getComponentAt(i) == matchingTab) {
								editorTabs.setSelectedIndex(i);
								break;
							}
						}
						activeEditor = matchingTab;
					}
					if (activeEditor != null) {
						activeEditor.setSchema(schemaName);
						scheduleSave();
					}
					metadataCache.set(schema);
					completionProvider.refresh(schema);
					populateTree(schema);
					setConnectedState(schemaName);
					updateWorkspaceContextBar();
					statusBar.setText(" Esquema " + schemaName + "  (" + schema.tables().size() + " tabelas)");
					if (onOpened != null) {
						onOpened.run();
					}
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

	/**
	 * Chamado por {@link #onRun} quando NAO ha conexao ativa: em vez de so
	 * avisar na barra de status (jeito antigo), pergunta se o usuario quer
	 * conectar e ja executar a instrucao — mostrando a BASE e o SCHEMA que
	 * serao usados (os da aba/workspace atual), pra deixar claro onde a
	 * instrucao vai rodar antes de disparar a conexao. Pedido explicito do
	 * usuario. So se aplica quando a aba atual pertence a um workspace com
	 * perfil de conexao conhecido (nao a aba SCRATCH, que nunca teve uma base
	 * "certa" pra oferecer) — nesse caso cai no aviso simples de sempre.
	 */
	private void offerConnectThenRun() {
		Conexao ws = activeWorkspace;
		ConnectionProfile profile = (ws != null) ? ws.profile() : null;
		if (profile == null) {
			statusBar.setText(" Conecte-se a uma base antes de executar.");
			return;
		}
		String schemaLabel = (profile.schema() != null && !profile.schema().isBlank())
				? profile.schema()
				: (ws.schema() != null ? ws.schema().name() : "(nenhum selecionado ainda)");
		String message = "Voce nao esta conectado no momento.\n\n"
				+ "Base:    " + profile.name() + "  (" + profile.host() + ")\n"
				+ "Schema:  " + schemaLabel
				+ "\n\nConectar agora e executar a instrucao desta aba?";
		int opt = JOptionPane.showConfirmDialog(this, message, "Nao conectado",
				JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
		if (opt == JOptionPane.YES_OPTION) {
			statusBar.setText(" Conectando a " + profile.name() + " para executar...");
			connectTo(profile, this::onRun);
		} else {
			statusBar.setText(" Execucao cancelada: nao conectado.");
		}
	}

	private void onRun() {
		if (!connectionManager().isConnected()) {
			offerConnectThenRun();
			return;
		}
		SqlEditorPane editor = currentEditor();
		if (editor == null) {
			return;
		}
		// Conexao com varios esquemas (nao um schema fixo no cadastro): cada
		// aba pode pertencer a um esquema diferente (ver SqlEditorPane#getSchema/
		// #setSchema). Antes de rodar, garante que a conexao esta "apontando"
		// (USE) para o esquema DESTA aba especifica — nao o que outra aba
		// deixou selecionado por ultimo — conectando nele primeiro se
		// necessario. Isto elimina o erro "No database selected" e a
		// necessidade de reabrir o esquema na arvore antes de executar.
		if (activeWorkspace != null && activeWorkspace.schemaList() != null) {
			String needed = editor.getSchema();
			if (needed == null || needed.isBlank()) {
				// Aba sem esquema proprio ainda (ex.: criada antes de qualquer
				// esquema ter sido aberto nesta conexao, ou sessao salva antes
				// desta funcionalidade existir) — cai no esquema aberto no
				// momento, se houver, e passa a "adotar" ele dai em diante.
				needed = currentActiveSchemaName();
				if (needed == null) {
					// Nunca foi aberto NENHUM esquema nesta conexao ainda — nao ha
					// como adivinhar qual a aba deveria usar (a conexao pode ter
					// dezenas deles). Em vez de so avisar na barra de status e
					// travar a execucao (jeito antigo, que o usuario relatou como
					// "nao executou a instrucao" — o aviso passava despercebido),
					// pergunta o esquema agora mesmo e ja executa em seguida.
					promptSchemaThenRun(editor);
					return;
				}
				editor.setSchema(needed);
				scheduleSave();
			}
			String activeName = currentActiveSchemaName();
			if (!needed.equals(activeName)) {
				switchToSchemaThenRun(needed, editor);
				return;
			}
		}
		runStatements(editor);
	}

	/**
	 * "Explicar" (fase 4 do GAP_ANALYSIS_DBA_DEV.md: "EXPLAIN visual") — roda
	 * {@code EXPLAIN FORMAT=JSON} na PRIMEIRA instrucao da selecao/aba atual
	 * (nunca a executa de verdade) e mostra o plano navegavel em
	 * {@link ExplainDialog}. Deliberadamente NAO reproduz toda a logica de
	 * troca de esquema por aba de {@link #onRun} (ver
	 * {@link #switchToSchemaThenRun}) — exige que a conexao ja esteja
	 * "apontando" pro esquema certo (normal, ja que se explica uma consulta
	 * que presumivelmente ja foi rodada nesta aba antes); escopo menor
	 * assumido conscientemente para nao duplicar ~60 linhas de reconciliacao
	 * de esquema so para este atalho.
	 */
	private void onExplain() {
		if (!connectionManager().isConnected()) {
			statusBar.setText(" Conecte-se a um servidor antes de explicar uma consulta.");
			return;
		}
		SqlEditorPane editor = currentEditor();
		if (editor == null) {
			return;
		}
		List<String> statements = SqlStatementSplitter.split(editor.currentSql());
		if (statements.isEmpty() || statements.get(0).isBlank()) {
			statusBar.setText(" Escreva (ou selecione) uma consulta antes de explicar.");
			return;
		}
		if (statements.size() > 1) {
			statusBar.setText(" Varias instrucoes selecionadas — explicando so a primeira.");
		}
		String sql = statements.get(0).trim();
		if (sql.endsWith(";")) {
			sql = sql.substring(0, sql.length() - 1).trim();
		}
		Conexao ws = activeWorkspace != null ? activeWorkspace : null;
		if (ws == null) {
			return;
		}
		String explainSql = "EXPLAIN FORMAT=JSON " + sql;
		String finalSql = sql;
		statusBar.setText(" Gerando plano de execucao...");
		runQuery(ws, explainSql, rows -> {
			statusBar.setText(" Pronto.");
			if (rows.isEmpty() || rows.get(0).length == 0 || rows.get(0)[0] == null) {
				showError("EXPLAIN nao retornou plano", new Exception("Resposta vazia do servidor"));
				return;
			}
			ExplainDialog.open(this, finalSql, String.valueOf(rows.get(0)[0]));
		}, ex -> {
			statusBar.setText(" Falha ao gerar o plano de execucao.");
			showError("Falha ao executar EXPLAIN", ex);
		});
	}

	/**
	 * Troca o "banco padrao" (USE) da conexao ativa para {@code schemaName} e
	 * recarrega os metadados ANTES de rodar a instrucao de {@code editor} —
	 * chamado por {@link #onRun} quando a aba a executar pertence a um
	 * esquema diferente do que a conexao tem aberto no momento (conexao com
	 * varios esquemas e abas de esquemas diferentes abertas ao mesmo tempo).
	 * Espelha {@link #openSchema}, mas encadeia a execucao no final em vez de
	 * so atualizar a arvore de objetos.
	 */
	private void switchToSchemaThenRun(String schemaName, SqlEditorPane editor) {
		statusBar.setText(" Conectando no esquema \"" + schemaName + "\" desta aba...");
		new SwingWorker<SchemaInfo, Void>() {
			@Override
			protected SchemaInfo doInBackground() throws Exception {
				Connection conn = connectionManager().getConnection();
				conn.setCatalog(schemaName); // define o banco padrao (USE schema)
				return metadataService.loadSchema(conn, schemaName);
			}

			@Override
			protected void done() {
				try {
					SchemaInfo schema = get();
					if (activeWorkspace != null) {
						activeWorkspace.schema = schema;
					}
					metadataCache.set(schema);
					completionProvider.refresh(schema);
					populateTree(schema);
					setConnectedState(schemaName);
					updateWorkspaceContextBar();
					runStatements(editor);
				} catch (Exception ex) {
					showError("Falha ao conectar no esquema \"" + schemaName + "\"", ex);
					statusBar.setText(" Erro ao trocar de esquema");
				}
			}
		}.execute();
	}

	/**
	 * Pergunta em qual esquema (dentre os disponiveis na conexao) esta aba
	 * deveria rodar — chamado por {@link #onRun} quando a aba nunca teve um
	 * esquema definido E a conexao tambem nunca abriu nenhum ainda (por isso
	 * nao ha esquema "atual" nenhum para herdar). Ao escolher, a aba "adota"
	 * esse esquema permanentemente (fica salvo com ela) e a instrucao roda
	 * OK EM SEGUIDA, sem precisar clicar em Executar de novo.
	 */
	private void promptSchemaThenRun(SqlEditorPane editor) {
		List<String> schemas = activeWorkspace.schemaList();
		if (schemas == null || schemas.isEmpty()) {
			statusBar.setText(" Esta conexao nao tem nenhum esquema disponivel.");
			return;
		}
		JComboBox<String> combo = new JComboBox<>(schemas.toArray(new String[0]));
		JPanel panel = new JPanel(new BorderLayout(0, 8));
		panel.add(new JLabel("Esta aba ainda nao tem um esquema definido. Em qual esquema executar?"),
				BorderLayout.NORTH);
		panel.add(combo, BorderLayout.CENTER);
		int opt = JOptionPane.showConfirmDialog(this, panel, "Escolher esquema para esta aba",
				JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
		if (opt != JOptionPane.OK_OPTION) {
			statusBar.setText(" Execucao cancelada: nenhum esquema escolhido para esta aba.");
			return;
		}
		String chosen = (String) combo.getSelectedItem();
		if (chosen == null) {
			return;
		}
		editor.setSchema(chosen);
		scheduleSave();
		switchToSchemaThenRun(chosen, editor);
	}

	/** Roda de fato as instrucoes SQL da aba {@code editor} — ver {@link #onRun}. */
	private void runStatements(SqlEditorPane editor) {
		final List<String> statements = SqlStatementSplitter.split(editor.currentSql());
		if (statements.isEmpty()) {
			return;
		}
		String badDate = null;
		for (String stmt : statements) {
			badDate = UnquotedDateGuard.findUnquotedDate(stmt);
			if (badDate != null) {
				break;
			}
		}
		if (badDate != null) {
			JOptionPane.showMessageDialog(this,
					"Encontrei uma data sem aspas: \"" + badDate + "\".\n\n"
							+ "Sem aspas isso NAO e uma data para o banco — e uma subtracao numerica "
							+ "(ex.: 2026 - 07 - 08 = 2011), que muda o resultado da consulta sem "
							+ "nenhum erro, e se repete do mesmo jeito se a instrucao for rodada fora "
							+ "da IDE. Coloque a data entre aspas (ex.: '2026-07-08') e execute de novo.",
					"Data sem aspas", JOptionPane.ERROR_MESSAGE);
			statusBar.setText(" Execucao bloqueada: data sem aspas (\"" + badDate + "\").");
			return;
		}
		if (!confirmRiskyStatements(statements)) {
			statusBar.setText(" Execucao cancelada.");
			return;
		}
		if (activeWorkspace != null) {
			// Atividade de verdade: reseta a contagem de ociosidade do
			// keep-alive (ver pingKeepAlive) — acabou de rodar algo, nao
			// precisa de um SELECT 1 de teste tao cedo.
			activeWorkspace.setLastActivityMillis(System.currentTimeMillis());
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
				Connection conn = connectionManager().getConnection();
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
					logExecutionHistory(editor, results);
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

	// ---------- Queries salvas (biblioteca gerenciada pelo app — ver SavedQueryStore) ----------

	/**
	 * Mantem o botao "Salvar" (e por extensao o Ctrl+S, que chama {@link #onSaveQuery()}
	 * de qualquer forma) desabilitado quando a aba atual nao tem SQL — sem
	 * conteudo nao ha o que salvar. Chamado sempre que a aba selecionada
	 * muda, o texto do editor muda, ou uma aba nova e criada (sempre vazia).
	 * Antes disto o botao ficava sempre clicavel e so avisava na barra de
	 * status ao clicar numa aba vazia, o que parecia (com razao) um "Salvar
	 * que nao funciona".
	 */
	private void updateSaveButtonState() {
		if (saveQueryButton == null) {
			return;
		}
		SqlEditorPane editor = currentEditor();
		boolean hasContent = editor != null && editor.fullText() != null && !editor.fullText().isBlank();
		saveQueryButton.setEnabled(hasContent);
	}

	/**
	 * Salva a aba atual como query: se ela ja esta ligada a uma query salva
	 * (reaberta do painel, ou ja salva antes nesta sessao), SOBRESCREVE sem
	 * perguntar nada — se nao, pede so o titulo. Acionado pelo botao
	 * "Salvar" da barra, por Ctrl+S ou pelo menu de contexto da aba.
	 */
	private void onSaveQuery() {
		SqlEditorPane editor = currentEditor();
		if (editor == null) {
			return;
		}
		String sql = editor.fullText();
		if (sql == null || sql.isBlank()) {
			statusBar.setText(" Nada para salvar: a aba esta vazia.");
			updateSaveButtonState();
			return;
		}
		try {
			String existingId = editor.getSavedQueryId();
			if (existingId != null) {
				savedQueryStore.updateSql(existingId, sql);
				statusBar.setText(" Query atualizada.");
			} else {
				int idx = editorTabs.indexOfComponent(editor);
				String suggested = (idx >= 0) ? editorTabs.getTitleAt(idx) : "";
				String title = JOptionPane.showInputDialog(this, "Nome da query:", suggested);
				if (title == null || title.trim().isEmpty()) {
					return;
				}
				SavedQueryStore.Query created = savedQueryStore.create(title.trim(), sql, currentConnectionLabel());
				editor.setSavedQueryId(created.id());
				statusBar.setText(" Query salva: " + title.trim());
			}
			if (savedQueriesPanel != null) {
				savedQueriesPanel.reload();
			}
		} catch (IOException ex) {
			showError("Falha ao salvar a query", ex);
		}
	}

	/**
	 * Abre uma query salva (duplo-clique no painel "Queries salvas") numa
	 * aba NOVA, ja ligada ao id dela — salvar de novo sobrescreve direto.
	 */
	private void openSavedQuery(SavedQueryStore.Query query) {
		if (!addQueryTab(query.title(), query.sql())) {
			return;
		}
		SqlEditorPane editor = currentEditor();
		if (editor != null) {
			editor.setSavedQueryId(query.id());
		}
		statusBar.setText(" Query aberta: " + query.title());
	}

	/** Nome da conexao ativa, ou {@code null} no workspace "sem conexao" (SCRATCH). */
	private String currentConnectionLabel() {
		return (activeWorkspace != null && activeWorkspace.profile != null) ? activeWorkspace.profile.name() : null;
	}

	// ---------- Historico de execucoes (log automatico — ver ExecutionHistoryStore) ----------

	/**
	 * Abre a aba "Historico" do painel lateral (botao "Historico" da barra de
	 * ferramentas) — garante que o painel lateral esteja visivel (reabre se
	 * o usuario tinha escondido com Ctrl+B) e seleciona a aba certa dentro
	 * dele.
	 */
	private void showHistoryPanel() {
		if (leftSide != null && !leftSide.isVisible()) {
			toggleSidebar();
		}
		if (leftIconRail != null && historyPanel != null) {
			leftIconRail.select(CARD_HISTORY);
		}
	}

	/**
	 * Reabre uma execucao do historico (duplo-clique/"Abrir em nova aba" no
	 * painel Historico) numa aba NOVA, ja marcada com o esquema em que rodou
	 * (se algum) — assim, ao clicar Executar de novo, cai direto no mesmo
	 * esquema sem precisar escolher de novo.
	 */
	private void openHistoryEntry(ExecutionHistoryStore.Entry entry) {
		if (!addQueryTab("Historico", entry.sql())) {
			return;
		}
		SqlEditorPane editor = currentEditor();
		if (editor != null && entry.schema() != null) {
			editor.setSchema(entry.schema());
			scheduleSave();
		}
		statusBar.setText(" Execucao reaberta do historico.");
	}

	/**
	 * Registra no historico cada instrucao ja executada (sucesso ou erro) —
	 * chamado no fim de {@link #runStatements}, uma entrada por resultado
	 * (a lista de resultados pode ser menor que a de instrucoes se a
	 * execucao parou num erro ou foi cancelada no meio). Falha silenciosa
	 * (so loga um aviso) — nunca deve interromper a execucao de verdade por
	 * causa do log de historico.
	 */
	private void logExecutionHistory(SqlEditorPane editor, List<QueryResult> results) {
		if (results == null || results.isEmpty()) {
			return;
		}
		String connectionName = currentConnectionLabel();
		String schema = editor != null ? editor.getSchema() : null;
		boolean changed = false;
		for (QueryResult r : results) {
			try {
				historyStore.append(r.sql(), connectionName, schema, r.execMs(), !r.error(), r.message());
				changed = true;
			} catch (IOException ex) {
				AppLogger.warning("Falha ao gravar historico de execucao", ex);
				break;
			}
		}
		if (changed && historyPanel != null) {
			historyPanel.reload();
		}
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
		ResultGrid grid = new ResultGrid(model, connectionManager(), schemaName, tableMetadataCache,
				() -> exportResult(r), this::scaledPx, resultRowHeightBasePx());

		// Nome distinto de propósito do campo MainWindow.statusBar (JLabel do
		// rodape da JANELA inteira) — esta e a barra de UMA aba de resultado.
		ResultStatusBar resultStatusBar = new ResultStatusBar(PAGE_SIZE);
		// Bug relatado pelo usuario: "Excluir linha nao esta funcionando" —
		// clicar no botao "Excluir linha(s)" movia o foco da tabela para o
		// PROPRIO botao antes do seu actionPerformed rodar, e o
		// SelectionManager (ver #installFocusClearing) limpava a selecao
		// nesse focusLost — o botao entao lia uma selecao ja vazia e nao
		// fazia nada, sem nenhum erro visivel. Eximir a barra de acoes
		// INTEIRA (Nova linha/Excluir/Descartar/Salvar) deste "limpar ao
		// perder foco" resolve, sem perder o comportamento original para
		// cliques em qualquer OUTRO lugar do app.
		grid.keepSelectionOnFocusTo(resultStatusBar.asComponent());
		Runnable refresh = () -> resultStatusBar.refresh(r.model().getRowCount(), r.execMs(), r.fetchMs(),
				r.cursor() != null && !r.cursor().exhausted);
		resultStatusBar.onLoadMore(() -> loadPage(r, PAGE_SIZE, refresh));
		resultStatusBar.onLoadAll(() -> loadAll(r, refresh));
		resultStatusBar.onExportThis(() -> exportResult(r));
		resultStatusBar.onExportAll(this::exportAll);
		resultStatusBar.onExportCsv(() -> exportResultCsv(grid.table(), r.title()));
		grid.onSelectionSummary(resultStatusBar::updateSelectionSummary);
		refresh.run();

		wireGridEditing(grid, resultStatusBar, model, schemaName);

		return new ResultView(grid, resultStatusBar).asComponent();
	}

	// ---------- Edicao direta na grade (update/insert/delete) ----------

	/**
	 * Liga os botoes de edicao da barra de resultado a {@link GridEditController}
	 * da grade, e tenta habilitar a edicao em si (ver {@link #tryEnableEditing}).
	 * Pedido explicito do usuario: poder atualizar/inserir/excluir linhas
	 * direto na grade (em lote, com um botao "Salvar alteracoes"), em vez de
	 * so gerar o SQL para copiar (ver {@link GridClipboard}).
	 */
	private void wireGridEditing(ResultGrid grid, ResultStatusBar resultStatusBar, ResultTableModel model,
			String schemaName) {
		GridEditController editController = grid.editController();

		Runnable refreshEditUi = () -> resultStatusBar.updatePendingState(
				editController.pendingCount(), grid.selectedModelRows().length > 0);
		editController.setOnChange(refreshEditUi);
		grid.table().getSelectionModel().addListSelectionListener(e -> {
			if (!e.getValueIsAdjusting()) {
				refreshEditUi.run();
			}
		});

		// "Modo de edicao": comeca sempre DESLIGADO (ver tryEnableEditing
		// abaixo) — pedido explicito do usuario para o resultado se
		// comportar como puramente visual/navegacao ate ele ligar de
		// proposito. Desligar de novo so e permitido sem alteracoes
		// pendentes (senao o usuario perderia edicoes sem perceber, ja que
		// desligado a grade some visualmente com Nova linha/Excluir/Salvar);
		// com pendencias, avisa e mantem ligado.
		resultStatusBar.onToggleEditMode(() -> {
			if (editController.isEditModeOn()) {
				if (editController.hasPendingChanges()) {
					JOptionPane.showMessageDialog(this,
							"Salve ou descarte as alteracoes pendentes antes de desativar o modo de edicao.",
							"Modo de edicao", JOptionPane.WARNING_MESSAGE);
					return;
				}
				editController.setEditModeOn(false);
			} else {
				editController.setEditModeOn(true);
			}
			resultStatusBar.setEditModeOn(editController.isEditModeOn());
			refreshEditUi.run();
		});

		resultStatusBar.onAddRow(grid::addNewRowAndReveal);
		resultStatusBar.onDeleteRows(() -> {
			int[] rows = grid.selectedModelRows();
			if (rows.length > 0) {
				editController.markForDelete(rows);
				// markForDelete so muda um Set interno do controller — NENHUM
				// TableModelEvent e disparado (ao contrario de addNewRow/
				// discardAll, que mexem no TableModel de verdade e por isso o
				// JTable se repinta sozinho). Sem este repaint(), a linha
				// continuava com a cor de sempre ate algum repaint incidental
				// acontecer (rolar, redimensionar...) — clicar em "Excluir
				// linha(s)" parecia nao fazer nada, mesmo a exclusao ja
				// estando registrada (valendo no proximo "Salvar alteracoes").
				grid.table().repaint();
			}
		});
		resultStatusBar.onDiscardChanges(() -> {
			editController.discardAll();
			grid.table().repaint();
		});
		resultStatusBar.onSaveChanges(() -> applyPendingChanges(editController, grid, resultStatusBar, refreshEditUi));

		tryEnableEditing(schemaName, model, () -> {
			resultStatusBar.showEditControls(true);
			resultStatusBar.setEditModeOn(false);
			refreshEditUi.run();
		});
	}

	/**
	 * So habilita a edicao quando TODAS as colunas com tabela de origem
	 * conhecida apontam para a MESMA tabela (SELECT simples, sem JOIN — ver
	 * {@link #uniqueSourceTable}) e essa tabela tem ao menos uma coluna de PK
	 * PRESENTE no resultado (sem PK nao da pra identificar univocamente qual
	 * linha fisica atualizar/excluir). Os metadados da tabela (PK) podem
	 * ainda nao estar em cache — dispara a carga e tenta de novo quando ela
	 * terminar, mesmo padrao ja usado por {@link ColumnMetadataResolver}.
	 */
	private void tryEnableEditing(String schemaName, ResultTableModel model, Runnable onEnabled) {
		if (schemaName == null) {
			return; // workspace "sem conexao" (SCRATCH) nunca tem metadados de tabela
		}
		String table = uniqueSourceTable(model);
		if (table == null) {
			return;
		}
		TableDetails details = tableMetadataCache.get(connectionManager(), schemaName, table,
				() -> tryEnableEditing(schemaName, model, onEnabled));
		if (details == null) {
			return; // ainda carregando; o callback acima tenta de novo quando terminar
		}
		EditableTarget target = buildEditableTarget(model, table, details);
		if (target == null) {
			return; // sem PK conhecida presente no resultado
		}
		model.editController().enable(target);
		onEnabled.run();
	}

	/** A UNICA tabela fisica de origem entre as colunas do resultado, ou {@code null} se houver mais de uma (JOIN) ou nenhuma. */
	private static String uniqueSourceTable(ResultTableModel model) {
		String table = null;
		for (int c = 0; c < model.getColumnCount(); c++) {
			String t = model.sourceTable(c);
			if (t == null || t.isBlank()) {
				continue;
			}
			if (table == null) {
				table = t;
			} else if (!table.equalsIgnoreCase(t)) {
				return null;
			}
		}
		return table;
	}

	private static EditableTarget buildEditableTarget(ResultTableModel model, String table, TableDetails details) {
		Set<String> pkNames = new HashSet<>();
		for (ColumnDetail col : details.columns()) {
			if ("PRI".equalsIgnoreCase(col.key())) {
				pkNames.add(col.name().toLowerCase(Locale.ROOT));
			}
		}
		if (pkNames.isEmpty()) {
			return null;
		}
		List<Integer> pkModelColumns = new ArrayList<>();
		List<Integer> editableColumns = new ArrayList<>();
		for (int c = 0; c < model.getColumnCount(); c++) {
			String realCol = model.realColumnName(c);
			String sourceTable = model.sourceTable(c);
			if (realCol == null || sourceTable == null || !sourceTable.equalsIgnoreCase(table)) {
				continue;
			}
			editableColumns.add(c);
			if (pkNames.contains(realCol.toLowerCase(Locale.ROOT))) {
				pkModelColumns.add(c);
			}
		}
		if (pkModelColumns.isEmpty()) {
			return null; // a PK da tabela nao esta presente no resultado (ex.: SELECT sem a coluna de id)
		}
		return new EditableTarget(table, pkModelColumns, editableColumns);
	}

	/** Pede confirmacao e aplica tudo que esta pendente numa unica transacao (ver {@link GridEditController#apply}). */
	private void applyPendingChanges(GridEditController editController, ResultGrid grid,
			ResultStatusBar resultStatusBar, Runnable refreshEditUi) {
		if (!editController.hasPendingChanges()) {
			return;
		}
		if (!connectionManager().isConnected()) {
			statusBar.setText(" Conecte-se a uma base antes de salvar alteracoes.");
			return;
		}
		int pending = editController.pendingCount();
		int ok = JOptionPane.showConfirmDialog(this,
				"Salvar " + pending + " alteracao(oes) pendente(s) na tabela \"" + editController.target().table()
						+ "\"?\nIsto grava direto no banco (uma unica transacao; tudo ou nada).",
				"Salvar alteracoes", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
		if (ok != JOptionPane.YES_OPTION) {
			return;
		}
		Connection conn = connectionManager().getConnection();
		DatabaseDialect dialect = connectionManager().dialect();
		resultStatusBar.setEditBusy(true);
		SwingWorker<GridEditController.ApplyResult, Void> worker = new SwingWorker<>() {
			@Override
			protected GridEditController.ApplyResult doInBackground() throws SQLException {
				return editController.apply(conn, dialect);
			}

			@Override
			protected void done() {
				// Ordem importa: primeiro devolve os 4 botoes ao habilitado
				// "de linha de base", DEPOIS refreshEditUi corrige de novo com
				// base no estado real (pendingCount pode ter zerado com o
				// sucesso, ou continuar positivo se a excecao interrompeu no
				// meio) — nunca o contrario, ou a correcao seria sobrescrita.
				resultStatusBar.setEditBusy(false);
				try {
					GridEditController.ApplyResult result = get();
					statusBar.setText(" Alteracoes salvas: " + result.inserted() + " inserida(s), "
							+ result.updated() + " atualizada(s), " + result.deleted() + " excluida(s).");
					grid.table().repaint();
				} catch (Exception ex) {
					showError("Falha ao salvar as alteracoes da grade", ex);
				} finally {
					refreshEditUi.run();
				}
			}
		};
		worker.execute();
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
	 * Exporta este resultado para CSV (todas as linhas/colunas VISIVEIS na
	 * grade — respeita filtro/ordenacao atuais, ver {@link GridExporter}).
	 * Mesma acao ja alcancavel pelo clique-direito na grade
	 * ("Exportar > CSV..." em {@link ResultContextMenu}); exposta tambem aqui,
	 * no botao "Exportar" principal, para paridade de descoberta com o Excel
	 * (ver {@code GAP_ANALYSIS_DBA_DEV.md}, fase 3).
	 */
	private void exportResultCsv(JTable table, String title) {
		JFileChooser fc = new JFileChooser();
		fc.setDialogTitle("Exportar CSV");
		fc.setSelectedFile(new File(title + ".csv"));
		fc.setFileFilter(new FileNameExtensionFilter("CSV (*.csv)", "csv"));
		if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
			return;
		}
		File file = fc.getSelectedFile();
		if (!file.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".csv")) {
			file = new File(file.getParentFile(), file.getName() + ".csv");
		}
		try {
			GridExporter.exportCsv(table, file.toPath());
		} catch (IOException ex) {
			showError("Falha ao exportar CSV", ex);
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
					int before = r.model().getRowCount();
					for (Vector<Object> row : rows) {
						r.model().addRow(row);
					}
					// As linhas recem-carregadas tambem precisam de uma "foto"
					// original se a edicao ja estiver ligada (ver GridEditController) —
					// senao um UPDATE/DELETE nelas nao teria WHERE para ancorar.
					((ResultTableModel) r.model()).editController().onRowsAppended(before);
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
				int before = r.model().getRowCount();
				for (Vector<Object> row : chunks) {
					r.model().addRow(row);
				}
				((ResultTableModel) r.model()).editController().onRowsAppended(before);
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
		for (Conexao w : workspaces.values()) {
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
	 * para uma consulta. Visibilidade de pacote (nao mais private): reaproveitado
	 * por {@link FkInspectorWindow} para montar a grade do Inspetor Flutuante de FK
	 * com o MESMO caminho usado pelas abas de resultado normais.
	 */
	static ResultTableModel createModel(ResultSet rs) throws SQLException {
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

	/**
	 * Anexa ate {@code max} linhas do ResultSet ao modelo; retorna quantas leu.
	 * Visibilidade de pacote: tambem usado por {@link FkInspectorWindow}.
	 */
	static int appendPage(DefaultTableModel model, ResultSet rs, int max) throws SQLException {
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
	static final class ResultCursor {
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
		setCurrentSchema(schema);
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
		if (!connectionManager().isConnected() || currentSchema == null) {
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
				Connection conn = connectionManager().getConnection();
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

		// Busca sem nenhum resultado: antes a raiz ficava sozinha, sem
		// categoria nenhuma embaixo e sem nenhuma pista de que a busca "deu
		// zero" (parecia igual a um schema vazio de verdade). Uma linha
		// sintetica (sem icone, sem acao — ver NodeType.EMPTY_MESSAGE) deixa
		// isso explicito, com o proprio termo buscado no texto.
		if (filtering && root.getChildCount() == 0) {
			root.add(new DefaultMutableTreeNode(new ObjNode(NodeType.EMPTY_MESSAGE,
					"Nenhum objeto encontrado para \"" + filter.trim() + "\"", "", null, null, null)));
		}

		objectTree.setModel(new DefaultTreeModel(root));
		// So ate o nivel de CATEGORIA em ambos os modos (nunca as tabelas em
		// si) — as colunas de cada tabela agora sempre existem como filhas
		// (ver addTableCategory), entao a setinha de expandir aparece mesmo
		// filtrando, mas so abre quando o usuario clica nela, sem poluir a
		// lista com todas as colunas de todo objeto encontrado na busca.
		expandCategories(root);
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
			// Colunas sempre viram filhas (buscando ou nao) — sem isto a
			// tabela some como folha SEM setinha de expandir quando o usuario
			// esta filtrando ("a seta para expandir nao existe", pedido
			// explicito do usuario). Ficam colapsadas por padrao mesmo assim
			// (ver rebuildTree/expandCategories): so aparecem de fato se o
			// usuario clicar na setinha.
			for (ColumnInfo c : t.columns()) {
				// "kind" (TABLE/VIEW) propagado para a coluna: e o que o
				// ObjectTreeCellRenderer usa pra saber de qual categoria
				// colorida a coluna faz parte (a cor "desce" ate ela).
				tn.add(new DefaultMutableTreeNode(
						new ObjNode(NodeType.COLUMN, c.name() + " : " + c.type(), c.name(), kind, null, c.type())));
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

	/**
	 * Duplo-clique numa linha da arvore de objetos, pelo PONTO clicado (nao
	 * pela selecao — mais confiavel, funciona mesmo se o clique nao mudou a
	 * selecao). Comportamento por tipo de no:
	 *
	 *  - SCHEMA_PICK (item de lista de escolha de schema): abre o schema.
	 *  - Objeto abrivel de verdade (TABLE/VIEW/ROUTINE/TRIGGER): cola o NOME
	 *    imediatamente no editor SQL ativo, na posicao do cursor — pedido
	 *    explicito do usuario, igual outras IDEs de banco. Expandir para ver
	 *    a estrutura passou a ser SO pela setinha (ver setToggleClickCount(0)
	 *    em buildObjectBrowser); "Informacoes"/DDL completos continuam so
	 *    pelo clique direito (ver {@link #maybeShowObjectContextMenu}).
	 *  - Categoria (ex.: "Tabelas (256)") ou raiz do schema: expande/recolhe
	 *    manualmente — do contrario perderiam esse atalho com o toggle
	 *    nativo desligado.
	 *  - Coluna: nao faz nada (nao e um gesto util ali).
	 */
	private void handleObjectTreeDoubleClick(MouseEvent e) {
		TreePath path = objectTree.getPathForLocation(e.getX(), e.getY());
		if (path == null) {
			return;
		}
		Object node = ((DefaultMutableTreeNode) path.getLastPathComponent()).getUserObject();
		if (node instanceof ObjNode obj) {
			if (obj.type() == NodeType.SCHEMA_PICK) {
				openSchema(obj.name());
				return;
			}
			if (isOpenableObject(obj.type())) {
				pasteObjectNameIntoEditor(obj.name());
				return;
			}
			if (obj.type() == NodeType.COLUMN) {
				return;
			}
		}
		// Categoria ou raiz do schema: toggle manual (native desligado acima).
		if (objectTree.isExpanded(path)) {
			objectTree.collapsePath(path);
		} else {
			objectTree.expandPath(path);
		}
	}

	/**
	 * Cola {@code name} no editor SQL ativo, na posicao do cursor (substitui
	 * a selecao, se houver — comportamento padrao de "inserir texto"), e
	 * devolve o foco pro editor pra continuar digitando na hora.
	 */
	private void pasteObjectNameIntoEditor(String name) {
		SqlEditorPane editor = currentEditor();
		if (editor == null) {
			return;
		}
		editor.textArea().replaceSelection(name);
		editor.textArea().requestFocusInWindow();
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
		if (obj.type() == NodeType.SCHEMA_PICK) {
			buildSchemaPickContextMenu(obj.name()).show(objectTree, e.getX(), e.getY());
			return;
		}
		if (obj.type() == NodeType.CATEGORY && "TABLE".equals(obj.kind())) {
			buildTablesCategoryContextMenu().show(objectTree, e.getX(), e.getY());
			return;
		}
		if (obj.type() == NodeType.CATEGORY && "VIEW".equals(obj.kind())) {
			buildViewsCategoryContextMenu().show(objectTree, e.getX(), e.getY());
			return;
		}
		if (obj.type() == NodeType.CATEGORY && "TRIGGER".equals(obj.kind())) {
			buildTriggersCategoryContextMenu().show(objectTree, e.getX(), e.getY());
			return;
		}
		if (obj.type() == NodeType.CATEGORY && ("PROCEDURE".equals(obj.kind()) || "FUNCTION".equals(obj.kind()))) {
			buildRoutinesCategoryContextMenu(obj.kind()).show(objectTree, e.getX(), e.getY());
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
		menu.addSeparator();
		JMenuItem createSchema = new JMenuItem("Criar esquema...");
		createSchema.addActionListener(a -> createSchema());
		menu.add(createSchema);
		JMenuItem createTable = new JMenuItem("Nova tabela...");
		createTable.addActionListener(a -> createTable());
		menu.add(createTable);
		JMenuItem createView = new JMenuItem("Nova view...");
		createView.addActionListener(a -> createView());
		menu.add(createView);
		JMenuItem createTrigger = new JMenuItem("Novo trigger...");
		createTrigger.addActionListener(a -> createTrigger());
		menu.add(createTrigger);
		JMenuItem createRoutine = new JMenuItem("Nova procedure/function...");
		createRoutine.addActionListener(a -> createRoutine(null));
		menu.add(createRoutine);
		menu.addSeparator();
		// Administracao do SERVIDOR (nao do schema aberto) — pedido explicito
		// do usuario ("gerenciamento de usuario, permissoes... para
		// administradores das bases", ver GAP_ANALYSIS_DBA_DEV.md). Fica na
		// raiz do schema por ser hoje o unico no de nivel "conexao" da arvore
		// (nao existe um no separado de servidor) — mesmo lugar de
		// "Criar esquema...", que ja e conceitualmente de instancia, nao de
		// schema.
		JMenuItem manageUsers = new JMenuItem("Gerenciar usuarios e privilegios...");
		manageUsers.addActionListener(a -> openUserManagement());
		menu.add(manageUsers);
		// Fase 2 do GAP_ANALYSIS_DBA_DEV.md: monitoramento/manutencao do
		// servidor — mesmos motivos de ficar aqui (unico no "de conexao" da
		// arvore hoje) que "Gerenciar usuarios e privilegios..." acima.
		JMenuItem processList = new JMenuItem("Sessoes ativas (PROCESSLIST)...");
		processList.addActionListener(a -> openProcessList());
		menu.add(processList);
		JMenuItem serverStatus = new JMenuItem("Variaveis e status do servidor...");
		serverStatus.addActionListener(a -> openServerStatus());
		menu.add(serverStatus);
		menu.addSeparator();
		// Diferente dos 3 itens acima (administracao do SERVIDOR), o
		// Diagrama ER e sobre o ESQUEMA aberto — mas fica no mesmo menu por
		// ser, hoje, o unico no de nivel "esquema" da arvore com clique
		// direito (mesmo raciocinio de "Nova tabela..." acima).
		JMenuItem erDiagram = new JMenuItem("Diagrama ER...");
		erDiagram.addActionListener(a -> openErDiagram());
		menu.add(erDiagram);
		JMenuItem eventsReplication = new JMenuItem("Eventos e replicacao...");
		eventsReplication.addActionListener(a -> openEventsReplication());
		menu.add(eventsReplication);
		JMenuItem backupRestore = new JMenuItem("Backup e restauracao...");
		backupRestore.addActionListener(a -> openBackupRestore());
		menu.add(backupRestore);
		return menu;
	}

	/**
	 * Menu de contexto de um ITEM da LISTA de esquemas (no do tipo
	 * {@link NodeType#SCHEMA_PICK}, ver {@link #buildSchemaPicker}) —
	 * diferente de {@link #buildSchemaRootContextMenu}, que e do esquema JA
	 * ABERTO (raiz da arvore quando ha so um schema/ja navegou pra dentro
	 * dele). Pedido explicito do usuario: clique direito num esquema da lista
	 * nao fazia NADA (nenhum dos "if" de {@link #maybeShowObjectContextMenu}
	 * batia com {@code SCHEMA_PICK}) — precisava pelo menos de abrir, ver o
	 * diagrama ER e excluir, sem precisar abrir o esquema primeiro so pra
	 * chegar nessas acoes pelo menu da raiz.
	 * <p>
	 * SEM "Editar"/renomear de proposito: o MySQL nao tem um jeito nativo e
	 * seguro de renomear um banco (o antigo {@code RENAME DATABASE} foi
	 * removido ha muitas versoes por risco de perda de dados) — oferecer essa
	 * opcao aqui exigiria simular via CREATE+RENAME TABLE+DROP, arriscado
	 * demais para entrar sem um pedido explicito nesse sentido.
	 */
	private JPopupMenu buildSchemaPickContextMenu(String schemaName) {
		JPopupMenu menu = new JPopupMenu();
		JMenuItem open = new JMenuItem("Abrir");
		open.addActionListener(a -> openSchema(schemaName));
		menu.add(open);
		JMenuItem erDiagram = new JMenuItem("Diagrama ER...");
		// O diagrama le currentSchema.tables() (ver openErDiagram) — que so
		// existe depois que o esquema foi carregado. Encadeia via o parametro
		// onOpened de openSchema(String, Runnable) em vez de duplicar a
		// logica de carregamento aqui.
		erDiagram.addActionListener(a -> openSchema(schemaName, this::openErDiagram));
		menu.add(erDiagram);
		menu.addSeparator();
		JMenuItem delete = new JMenuItem("Excluir esquema...");
		delete.addActionListener(a -> deleteSchema(schemaName));
		menu.add(delete);
		return menu;
	}

	/**
	 * Menu de contexto do NO "Tabelas" (categoria) na arvore de objetos — hoje
	 * so oferece "Nova tabela...", mesmo atalho disponivel na raiz do schema e
	 * no menu de contexto de uma tabela ja existente (ver
	 * {@link #buildObjectContextMenu}), para quem prefere clicar direto em
	 * cima da categoria.
	 */
	private JPopupMenu buildTablesCategoryContextMenu() {
		JPopupMenu menu = new JPopupMenu();
		JMenuItem createTable = new JMenuItem("Nova tabela...");
		createTable.addActionListener(a -> createTable());
		menu.add(createTable);
		return menu;
	}

	/** Igual a {@link #buildTablesCategoryContextMenu}, so que para o no "Visualizacoes" (categoria de views). */
	private JPopupMenu buildViewsCategoryContextMenu() {
		JPopupMenu menu = new JPopupMenu();
		JMenuItem createViewItem = new JMenuItem("Nova view...");
		createViewItem.addActionListener(a -> createView());
		menu.add(createViewItem);
		return menu;
	}

	/** Igual a {@link #buildTablesCategoryContextMenu}, so que para o no "Triggers" (categoria). */
	private JPopupMenu buildTriggersCategoryContextMenu() {
		JPopupMenu menu = new JPopupMenu();
		JMenuItem createTriggerItem = new JMenuItem("Novo trigger...");
		createTriggerItem.addActionListener(a -> createTrigger());
		menu.add(createTriggerItem);
		return menu;
	}

	/** Igual a {@link #buildTablesCategoryContextMenu}, so que para os nos "Procedures"/"Functions" (categoria). */
	private JPopupMenu buildRoutinesCategoryContextMenu(String kind) {
		JPopupMenu menu = new JPopupMenu();
		JMenuItem createRoutineItem =
				new JMenuItem("PROCEDURE".equals(kind) ? "Nova procedure..." : "Nova function...");
		createRoutineItem.addActionListener(a -> createRoutine(kind));
		menu.add(createRoutineItem);
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
		setCurrentSchema(null);
		buildSchemaPicker(activeWorkspace.schemaList);
		statusBar.setText(" Selecione um esquema (" + activeWorkspace.schemaList.size() + " disponiveis).");
	}

	/**
	 * Cria um novo esquema (banco) no servidor da conexao ativa — pedido
	 * explicito do usuario, acessivel pelo botao de cabecalho do navegador de
	 * objetos e pelo menu de contexto da raiz do esquema. Funciona tanto na
	 * tela de lista de esquemas quanto com um esquema ja aberto (nesse caso
	 * so atualiza {@code schemaList} em segundo plano, sem navegar para fora
	 * do que o usuario esta vendo).
	 */
	private void createSchema() {
		if (activeWorkspace == null || !activeWorkspace.mgr.isConnected()) {
			statusBar.setText(" Conecte-se a um servidor antes de criar um esquema.");
			return;
		}
		String input = JOptionPane.showInputDialog(this, "Nome do novo esquema:", "");
		if (input == null || input.trim().isEmpty()) {
			return;
		}
		String schemaName = input.trim();
		Conexao ws = activeWorkspace;
		statusBar.setText(" Criando esquema \"" + schemaName + "\"...");
		new SwingWorker<List<String>, Void>() {
			@Override
			protected List<String> doInBackground() throws Exception {
				Connection conn = ws.mgr.getConnection();
				try (Statement st = conn.createStatement()) {
					st.executeUpdate(dialect.createSchemaStatement(schemaName));
				}
				return metadataService.listSchemas(conn);
			}

			@Override
			protected void done() {
				try {
					List<String> schemas = get();
					ws.schemaList = schemas;
					statusBar.setText(" Esquema \"" + schemaName + "\" criado.");
					if (ws == activeWorkspace && ws.schema == null) {
						// Na tela de lista de esquemas: atualiza para mostrar o recem-criado.
						buildSchemaPicker(schemas);
					}
					int open = JOptionPane.showConfirmDialog(MainWindow.this,
							"Esquema \"" + schemaName + "\" criado.\n\nDeseja abri-lo agora?",
							"Criar esquema", JOptionPane.YES_NO_OPTION);
					if (open == JOptionPane.YES_OPTION && ws == activeWorkspace) {
						openSchema(schemaName);
					}
				} catch (Exception ex) {
					showError("Falha ao criar esquema", ex);
					statusBar.setText(" Falha ao criar esquema.");
				}
			}
		}.execute();
	}

	/**
	 * Apaga um esquema (banco) inteiro do servidor da conexao ativa — {@code
	 * DROP DATABASE}, via {@link com.nureal.ide.core.dialect.DatabaseDialect#dropSchemaStatement}.
	 * Acessivel pelo clique direito num item da LISTA de esquemas (ver
	 * {@link #buildSchemaPickContextMenu}). DESTRUTIVO e IRREVERSIVEL: apaga
	 * todas as tabelas/dados/views/triggers/procedures do esquema, sem
	 * confirmacao de risco padrao ({@link #confirmRiskyStatements}) bastar —
	 * exige DIGITAR o nome exato do esquema antes de habilitar a exclusao
	 * (mesmo padrao de confirmacao usado por GitHub/GitLab para apagar um
	 * repositorio), dificil de disparar sem querer.
	 */
	private void deleteSchema(String schemaName) {
		if (activeWorkspace == null || !activeWorkspace.mgr.isConnected()) {
			statusBar.setText(" Conecte-se a um servidor antes de excluir um esquema.");
			return;
		}
		String typed = JOptionPane.showInputDialog(this,
				"Isto apaga TODAS as tabelas, dados, views, triggers e procedures\n"
						+ "do esquema \"" + schemaName + "\" — SEM VOLTA.\n\n"
						+ "Para confirmar, digite o nome exato do esquema:",
				"Excluir esquema \"" + schemaName + "\"", JOptionPane.WARNING_MESSAGE);
		if (typed == null) {
			return; // cancelado
		}
		if (!typed.equals(schemaName)) {
			JOptionPane.showMessageDialog(this,
					"O nome digitado nao confere com \"" + schemaName + "\". Nada foi excluido.",
					"Excluir esquema", JOptionPane.WARNING_MESSAGE);
			return;
		}
		Conexao ws = activeWorkspace;
		// Era o esquema ABERTO agora (nao so um item da lista) quando o
		// clique direito veio da raiz enquanto ainda mostra os itens da
		// lista logo abaixo (caso raro, mas possivel) — decide ANTES de
		// disparar o DROP, nao depois, pra nao depender do estado de
		// currentSchema ja ter mudado por alguma outra acao concorrente.
		boolean wasOpenSchema = currentSchema != null && schemaName.equals(currentSchema.name());
		statusBar.setText(" Excluindo esquema \"" + schemaName + "\"...");
		new SwingWorker<List<String>, Void>() {
			@Override
			protected List<String> doInBackground() throws Exception {
				Connection conn = ws.mgr.getConnection();
				try (Statement st = conn.createStatement()) {
					st.executeUpdate(dialect.dropSchemaStatement(schemaName));
				}
				return metadataService.listSchemas(conn);
			}

			@Override
			protected void done() {
				try {
					List<String> schemas = get();
					ws.schemaList = schemas;
					statusBar.setText(" Esquema \"" + schemaName + "\" excluido.");
					if (wasOpenSchema && ws == activeWorkspace) {
						setCurrentSchema(null);
					}
					if (ws == activeWorkspace) {
						buildSchemaPicker(schemas);
						updateWorkspaceContextBar();
					}
				} catch (Exception ex) {
					showError("Falha ao excluir o esquema", ex);
					statusBar.setText(" Falha ao excluir esquema.");
				}
			}
		}.execute();
	}

	/**
	 * Abre o assistente de DDL ({@link DdlAssistantDialog}) no modo "criar
	 * tabela nova" — acessivel pelo menu de contexto (clique direito) da
	 * raiz do esquema, do no "Tabelas" e de qualquer tabela ja existente (ver
	 * {@link #buildSchemaRootContextMenu}, {@link #buildTablesCategoryContextMenu}
	 * e {@link #buildObjectContextMenu}). O assistente coleta colunas, chaves
	 * estrangeiras e indices de forma guiada, mostra sugestoes de
	 * normalizacao e uma pre-visualizacao do DDL antes de executar; apos
	 * executar, atualiza a arvore de objetos (mesmo caminho de
	 * {@link #refreshObjectTree}) para a tabela nova aparecer sem precisar de
	 * um refresh manual.
	 */
	private void createTable() {
		if (activeWorkspace == null || !activeWorkspace.mgr.isConnected() || currentSchema == null) {
			statusBar.setText(" Abra um esquema antes de criar uma tabela.");
			return;
		}
		Set<String> existingNames = new HashSet<>();
		for (TableInfo t : currentSchema.tables()) {
			existingNames.add(t.name().toLowerCase(Locale.ROOT));
		}
		for (TableInfo v : currentSchema.views()) {
			existingNames.add(v.name().toLowerCase(Locale.ROOT));
		}
		Conexao ws = activeWorkspace;
		String schemaName = currentSchema.name();
		DdlAssistantDialog.openCreate(this, currentSchema, dialect,
				name -> existingNames.contains(name.toLowerCase(Locale.ROOT)),
				(statements, onOk, onErr) -> runDdlStatements(ws, statements, onOk, onErr),
				this::sendDdlToEditor,
				() -> {
					if (ws == activeWorkspace && schemaName.equals(currentSchema.name())) {
						refreshObjectTree(false);
					}
				});
	}

	/**
	 * Abre o assistente de DDL no modo "alterar tabela existente" — acessivel
	 * pelo menu de contexto de uma tabela ja existente (ver
	 * {@link #buildObjectContextMenu}). So permite ADICIONAR colunas, chaves
	 * estrangeiras e indices (nunca modificar/remover o que ja existe — ver
	 * javadoc de {@link DdlAssistantDialog}); carrega a estrutura atual da
	 * tabela (colunas/indices/FKs) antes de abrir o assistente para dar
	 * contexto e alimentar as sugestoes de normalizacao.
	 */
	private void alterTable(ObjNode obj) {
		if (activeWorkspace == null || !activeWorkspace.mgr.isConnected() || currentSchema == null) {
			statusBar.setText(" Abra um esquema antes de alterar uma tabela.");
			return;
		}
		Conexao ws = activeWorkspace;
		String schemaName = currentSchema.name();
		String tableName = obj.name();
		statusBar.setText(" Carregando estrutura de \"" + tableName + "\"...");
		new SwingWorker<TableDetails, Void>() {
			@Override
			protected TableDetails doInBackground() throws Exception {
				Connection conn = ws.mgr.getConnection();
				return metadataService.loadTableDetails(conn, schemaName, tableName);
			}

			@Override
			protected void done() {
				try {
					TableDetails details = get();
					statusBar.setText(" Pronto.");
					DdlAssistantDialog.openAlter(MainWindow.this, currentSchema, tableName, details, dialect,
							(statements, onOk, onErr) -> runDdlStatements(ws, statements, onOk, onErr),
							MainWindow.this::sendDdlToEditor,
							() -> {
								if (ws == activeWorkspace && schemaName.equals(currentSchema.name())) {
									refreshObjectTree(false);
								}
							});
				} catch (Exception ex) {
					showError("Falha ao carregar estrutura da tabela", ex);
					statusBar.setText(" Falha ao carregar estrutura da tabela.");
				}
			}
		}.execute();
	}

	/**
	 * Abre o {@link ViewBuilderDialog} no modo "criar view nova" — acessivel
	 * pela raiz do schema, pelo no "Visualizacoes" (categoria) e pelo menu de
	 * contexto de uma view ja existente (ver {@link #buildObjectContextMenu}).
	 * Mesmo criterio de nomes ja usados que {@link #createTable()} (tabela E
	 * view compartilham o mesmo namespace no MySQL).
	 */
	private void createView() {
		if (activeWorkspace == null || !activeWorkspace.mgr.isConnected() || currentSchema == null) {
			statusBar.setText(" Abra um esquema antes de criar uma view.");
			return;
		}
		Set<String> existingNames = new HashSet<>();
		for (TableInfo t : currentSchema.tables()) {
			existingNames.add(t.name().toLowerCase(Locale.ROOT));
		}
		for (TableInfo v : currentSchema.views()) {
			existingNames.add(v.name().toLowerCase(Locale.ROOT));
		}
		Conexao ws = activeWorkspace;
		String schemaName = currentSchema.name();
		ViewBuilderDialog.openCreate(this, dialect,
				name -> existingNames.contains(name.toLowerCase(Locale.ROOT)),
				(statements, onOk, onErr) -> runDdlStatements(ws, statements, onOk, onErr),
				this::sendDdlToEditor,
				() -> {
					if (ws == activeWorkspace && schemaName.equals(currentSchema.name())) {
						refreshObjectTree(false);
					}
				});
	}

	/**
	 * Abre o {@link ViewBuilderDialog} no modo "editar view existente" —
	 * carrega o {@code SHOW CREATE VIEW} atual ANTES de abrir o dialogo (mesmo
	 * padrao "carrega antes de mostrar" de {@link #alterTable}), pra o
	 * assistente ja aparecer com o SELECT atual preenchido em vez de vazio.
	 */
	private void editView(ObjNode obj) {
		if (activeWorkspace == null || !activeWorkspace.mgr.isConnected() || currentSchema == null) {
			statusBar.setText(" Abra um esquema antes de editar uma view.");
			return;
		}
		Conexao ws = activeWorkspace;
		String schemaName = currentSchema.name();
		String viewName = obj.name();
		statusBar.setText(" Carregando definicao de \"" + viewName + "\"...");
		new SwingWorker<String, Void>() {
			@Override
			protected String doInBackground() throws Exception {
				Connection conn = ws.mgr.getConnection();
				String sql = dialect.definitionQuery("VIEW", viewName);
				try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
					if (rs.next()) {
						int idx = pickDefinitionColumn(rs.getMetaData());
						String def = rs.getString(idx);
						return def != null ? def : "";
					}
					return "";
				}
			}

			@Override
			protected void done() {
				try {
					String rawDefinition = get();
					statusBar.setText(" Pronto.");
					ViewBuilderDialog.openEdit(MainWindow.this, dialect, viewName, rawDefinition,
							(statements, onOk, onErr) -> runDdlStatements(ws, statements, onOk, onErr),
							MainWindow.this::sendDdlToEditor,
							() -> {
								if (ws == activeWorkspace && schemaName.equals(currentSchema.name())) {
									refreshObjectTree(false);
								}
							});
				} catch (Exception ex) {
					showError("Falha ao carregar a definicao da view", ex);
					statusBar.setText(" Falha ao carregar a definicao da view.");
				}
			}
		}.execute();
	}

	/**
	 * Remove uma view existente ({@code DROP VIEW}), apos confirmacao —
	 * irreversivel, mesmo criterio de outras acoes destrutivas do app (ver
	 * confirmacao de remocao em {@code DdlAssistantDialog}).
	 */
	private void dropView(ObjNode obj) {
		if (activeWorkspace == null || !activeWorkspace.mgr.isConnected() || currentSchema == null) {
			statusBar.setText(" Abra um esquema antes de excluir uma view.");
			return;
		}
		String viewName = obj.name();
		int choice = JOptionPane.showConfirmDialog(this,
				"Excluir a view \"" + viewName + "\" permanentemente? Esta operacao nao pode ser desfeita.",
				"Excluir view", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
		if (choice != JOptionPane.YES_OPTION) {
			return;
		}
		Conexao ws = activeWorkspace;
		String schemaName = currentSchema.name();
		runDdlStatements(ws, List.of(dialect.dropViewStatement(viewName)), () -> {
			statusBar.setText(" View \"" + viewName + "\" excluida.");
			if (ws == activeWorkspace && schemaName.equals(currentSchema.name())) {
				refreshObjectTree(false);
			}
		}, ex -> {
			statusBar.setText(" Falha ao excluir a view.");
			showError("Falha ao excluir a view", ex);
		});
	}

	/**
	 * Abre o {@link TriggerBuilderDialog} no modo "criar trigger novo" —
	 * acessivel pela raiz do schema, pelo no "Triggers" (categoria) e pelo
	 * menu de contexto de um trigger ja existente (ver
	 * {@link #buildObjectContextMenu}).
	 */
	private void createTrigger() {
		if (activeWorkspace == null || !activeWorkspace.mgr.isConnected() || currentSchema == null) {
			statusBar.setText(" Abra um esquema antes de criar um trigger.");
			return;
		}
		List<String> tableNames = currentSchema.tables().stream().map(TableInfo::name).toList();
		Set<String> existingNames = new HashSet<>();
		for (String t : currentSchema.triggers()) {
			existingNames.add(t.toLowerCase(Locale.ROOT));
		}
		Conexao ws = activeWorkspace;
		String schemaName = currentSchema.name();
		TriggerBuilderDialog.openCreate(this, dialect, tableNames,
				name -> existingNames.contains(name.toLowerCase(Locale.ROOT)),
				(statements, onOk, onErr) -> runDdlStatements(ws, statements, onOk, onErr),
				this::sendDdlToEditor,
				() -> {
					if (ws == activeWorkspace && schemaName.equals(currentSchema.name())) {
						refreshObjectTree(false);
					}
				});
	}

	/**
	 * Abre o {@link TriggerBuilderDialog} no modo "editar trigger existente" —
	 * carrega o {@code SHOW CREATE TRIGGER} atual ANTES de abrir o dialogo
	 * (mesmo padrao "carrega antes de mostrar" de {@link #alterTable}/
	 * {@link #editView}).
	 */
	private void editTrigger(ObjNode obj) {
		if (activeWorkspace == null || !activeWorkspace.mgr.isConnected() || currentSchema == null) {
			statusBar.setText(" Abra um esquema antes de editar um trigger.");
			return;
		}
		Conexao ws = activeWorkspace;
		String schemaName = currentSchema.name();
		String triggerName = obj.name();
		List<String> tableNames = currentSchema.tables().stream().map(TableInfo::name).toList();
		statusBar.setText(" Carregando definicao de \"" + triggerName + "\"...");
		new SwingWorker<String, Void>() {
			@Override
			protected String doInBackground() throws Exception {
				Connection conn = ws.mgr.getConnection();
				String sql = dialect.definitionQuery("TRIGGER", triggerName);
				try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
					if (rs.next()) {
						int idx = pickDefinitionColumn(rs.getMetaData());
						String def = rs.getString(idx);
						return def != null ? def : "";
					}
					return "";
				}
			}

			@Override
			protected void done() {
				try {
					String rawDefinition = get();
					statusBar.setText(" Pronto.");
					TriggerBuilderDialog.openEdit(MainWindow.this, dialect, tableNames, triggerName, rawDefinition,
							(statements, onOk, onErr) -> runDdlStatements(ws, statements, onOk, onErr),
							MainWindow.this::sendDdlToEditor,
							() -> {
								if (ws == activeWorkspace && schemaName.equals(currentSchema.name())) {
									refreshObjectTree(false);
								}
							});
				} catch (Exception ex) {
					showError("Falha ao carregar a definicao do trigger", ex);
					statusBar.setText(" Falha ao carregar a definicao do trigger.");
				}
			}
		}.execute();
	}

	/**
	 * Remove um trigger existente ({@code DROP TRIGGER}), apos confirmacao —
	 * irreversivel, mesmo criterio de {@link #dropView}.
	 */
	private void dropTrigger(ObjNode obj) {
		if (activeWorkspace == null || !activeWorkspace.mgr.isConnected() || currentSchema == null) {
			statusBar.setText(" Abra um esquema antes de excluir um trigger.");
			return;
		}
		String triggerName = obj.name();
		int choice = JOptionPane.showConfirmDialog(this,
				"Excluir o trigger \"" + triggerName + "\" permanentemente? Esta operacao nao pode ser desfeita.",
				"Excluir trigger", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
		if (choice != JOptionPane.YES_OPTION) {
			return;
		}
		Conexao ws = activeWorkspace;
		String schemaName = currentSchema.name();
		runDdlStatements(ws, List.of(dialect.dropTriggerStatement(triggerName)), () -> {
			statusBar.setText(" Trigger \"" + triggerName + "\" excluido.");
			if (ws == activeWorkspace && schemaName.equals(currentSchema.name())) {
				refreshObjectTree(false);
			}
		}, ex -> {
			statusBar.setText(" Falha ao excluir o trigger.");
			showError("Falha ao excluir o trigger", ex);
		});
	}

	/**
	 * Abre o {@link RoutineBuilderDialog} no modo "criar" — {@code initialKind}
	 * ("PROCEDURE"/"FUNCTION"/{@code null}) preseleciona o tipo, vindo de qual
	 * atalho o usuario clicou (raiz do schema = sem preferencia; no "Procedures"/
	 * "Functions" ou menu de contexto de uma rotina existente = ja acerta o
	 * tipo certo). Nomes de procedure e function moram em namespaces
	 * SEPARADOS no MySQL — o {@code nameTaken} verifica so a lista certa.
	 */
	private void createRoutine(String initialKind) {
		if (activeWorkspace == null || !activeWorkspace.mgr.isConnected() || currentSchema == null) {
			statusBar.setText(" Abra um esquema antes de criar uma procedure/function.");
			return;
		}
		Set<String> existingProcedures = new HashSet<>();
		for (String p : currentSchema.procedures()) {
			existingProcedures.add(p.toLowerCase(Locale.ROOT));
		}
		Set<String> existingFunctions = new HashSet<>();
		for (String f : currentSchema.functions()) {
			existingFunctions.add(f.toLowerCase(Locale.ROOT));
		}
		Conexao ws = activeWorkspace;
		String schemaName = currentSchema.name();
		RoutineBuilderDialog.openCreate(this, dialect, initialKind,
				(kind, name) -> ("PROCEDURE".equals(kind) ? existingProcedures : existingFunctions)
						.contains(name.toLowerCase(Locale.ROOT)),
				(statements, onOk, onErr) -> runDdlStatements(ws, statements, onOk, onErr),
				this::sendDdlToEditor,
				() -> {
					if (ws == activeWorkspace && schemaName.equals(currentSchema.name())) {
						refreshObjectTree(false);
					}
				});
	}

	/**
	 * Remove uma procedure ou function existente (conforme {@code obj.kind()}),
	 * apos confirmacao — irreversivel, mesmo criterio de {@link #dropView}/
	 * {@link #dropTrigger}.
	 */
	private void dropRoutine(ObjNode obj) {
		if (activeWorkspace == null || !activeWorkspace.mgr.isConnected() || currentSchema == null) {
			statusBar.setText(" Abra um esquema antes de excluir uma procedure/function.");
			return;
		}
		boolean isProcedure = "PROCEDURE".equals(obj.kind());
		String routineName = obj.name();
		String label = isProcedure ? "procedure" : "function";
		int choice = JOptionPane.showConfirmDialog(this,
				"Excluir a " + label + " \"" + routineName + "\" permanentemente? Esta operacao nao pode ser desfeita.",
				"Excluir " + label, JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
		if (choice != JOptionPane.YES_OPTION) {
			return;
		}
		Conexao ws = activeWorkspace;
		String schemaName = currentSchema.name();
		String dropSql = isProcedure ? dialect.dropProcedureStatement(routineName)
				: dialect.dropFunctionStatement(routineName);
		runDdlStatements(ws, List.of(dropSql), () -> {
			statusBar.setText(" " + (isProcedure ? "Procedure" : "Function") + " \"" + routineName + "\" excluida.");
			if (ws == activeWorkspace && schemaName.equals(currentSchema.name())) {
				refreshObjectTree(false);
			}
		}, ex -> {
			statusBar.setText(" Falha ao excluir a " + label + ".");
			showError("Falha ao excluir a " + label, ex);
		});
	}

	/**
	 * Executa uma lista de comandos DDL (ja prontos, montados pelo
	 * {@link DdlAssistantDialog}) em background na conexao do workspace
	 * informado, chamando {@code onOk}/{@code onErr} de volta na EDT —
	 * mesmo padrao ja usado por {@link #createTable()} e {@link #createSchema()}.
	 */
	private void runDdlStatements(Conexao ws, List<String> statements, Runnable onOk,
			java.util.function.Consumer<Exception> onErr) {
		new SwingWorker<Void, Void>() {
			@Override
			protected Void doInBackground() throws Exception {
				Connection conn = ws.mgr.getConnection();
				try (Statement st = conn.createStatement()) {
					for (String sql : statements) {
						st.executeUpdate(sql);
					}
				}
				return null;
			}

			@Override
			protected void done() {
				try {
					get();
					onOk.run();
				} catch (java.util.concurrent.ExecutionException ex) {
					Throwable cause = ex.getCause();
					onErr.accept(cause instanceof Exception e2 ? e2 : ex);
				} catch (Exception ex) {
					onErr.accept(ex);
				}
			}
		}.execute();
	}

	/**
	 * Executa uma consulta de LEITURA (SELECT/SHOW) em background na conexao
	 * do workspace informado e devolve as linhas como {@code Object[]} (uma
	 * entrada por coluna, na ordem do SELECT) — usado pelo
	 * {@link UserManagementDialog} (listar usuarios, {@code SHOW GRANTS},
	 * listar roles), que precisa ler resultado em vez de so executar
	 * comandos (ver {@link #runDdlStatements}, que so faz
	 * {@code executeUpdate}, sem trazer linhas de volta).
	 */
	private void runQuery(Conexao ws, String sql, java.util.function.Consumer<List<Object[]>> onRows,
			java.util.function.Consumer<Exception> onErr) {
		new SwingWorker<List<Object[]>, Void>() {
			@Override
			protected List<Object[]> doInBackground() throws Exception {
				Connection conn = ws.mgr.getConnection();
				List<Object[]> rows = new ArrayList<>();
				try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
					ResultSetMetaData meta = rs.getMetaData();
					int cols = meta.getColumnCount();
					while (rs.next()) {
						Object[] row = new Object[cols];
						for (int i = 0; i < cols; i++) {
							row[i] = rs.getObject(i + 1);
						}
						rows.add(row);
					}
				}
				return rows;
			}

			@Override
			protected void done() {
				try {
					onRows.accept(get());
				} catch (java.util.concurrent.ExecutionException ex) {
					Throwable cause = ex.getCause();
					onErr.accept(cause instanceof Exception e2 ? e2 : ex);
				} catch (Exception ex) {
					onErr.accept(ex);
				}
			}
		}.execute();
	}

	/**
	 * Igual a {@link #runQuery}, so que tambem devolve os NOMES das colunas —
	 * usado por {@link ColumnQueryRunner} (ver {@code EventsReplicationDialog}),
	 * para consultas cujas colunas variam por versao do servidor
	 * ({@code SHOW SLAVE STATUS}/{@code SHOW MASTER STATUS}), ao contrario das
	 * demais (SELECT escrito por esta IDE, colunas ja conhecidas — ver
	 * {@link #runQuery}).
	 */
	private void runQueryWithColumns(Conexao ws, String sql,
			java.util.function.BiConsumer<List<String>, List<Object[]>> onResult,
			java.util.function.Consumer<Exception> onErr) {
		new SwingWorker<Object[], Void>() {
			@Override
			protected Object[] doInBackground() throws Exception {
				Connection conn = ws.mgr.getConnection();
				List<String> columns = new ArrayList<>();
				List<Object[]> rows = new ArrayList<>();
				try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
					ResultSetMetaData meta = rs.getMetaData();
					int cols = meta.getColumnCount();
					for (int i = 1; i <= cols; i++) {
						columns.add(meta.getColumnLabel(i));
					}
					while (rs.next()) {
						Object[] row = new Object[cols];
						for (int i = 0; i < cols; i++) {
							row[i] = rs.getObject(i + 1);
						}
						rows.add(row);
					}
				}
				return new Object[] { columns, rows };
			}

			@SuppressWarnings("unchecked")
			@Override
			protected void done() {
				try {
					Object[] result = get();
					onResult.accept((List<String>) result[0], (List<Object[]>) result[1]);
				} catch (java.util.concurrent.ExecutionException ex) {
					Throwable cause = ex.getCause();
					onErr.accept(cause instanceof Exception e2 ? e2 : ex);
				} catch (Exception ex) {
					onErr.accept(ex);
				}
			}
		}.execute();
	}

	/**
	 * Abre o dialogo de administracao de usuarios e privilegios (ver
	 * {@link UserManagementDialog}) — acessivel pelo menu de contexto da raiz
	 * do esquema ({@link #buildSchemaRootContextMenu}). Carrega a lista de
	 * usuarios do servidor ({@code mysql.user}) e a lista de schemas ANTES de
	 * abrir o dialogo (mesmo padrao de {@link #alterTable}: consulta em
	 * background, dialogo so aparece com os dados prontos) — se a conexao
	 * ativa nao tiver privilegio para ler {@code mysql.user}, mostra o erro
	 * em vez de abrir um dialogo vazio/quebrado.
	 */
	private void openUserManagement() {
		if (activeWorkspace == null || !activeWorkspace.mgr.isConnected()) {
			statusBar.setText(" Conecte-se a um servidor antes de gerenciar usuarios.");
			return;
		}
		Conexao ws = activeWorkspace;
		String schemaNameNow = currentSchema != null ? currentSchema.name() : null;
		List<String> currentSchemaTables = currentSchema != null
				? currentSchema.tables().stream().map(TableInfo::name).toList()
				: List.of();
		statusBar.setText(" Carregando usuarios do servidor...");
		runQuery(ws, dialect.listUsersQuery(), userRows -> {
			List<DbUserInfo> users = new ArrayList<>();
			for (Object[] row : userRows) {
				String user = String.valueOf(row[0]);
				String host = String.valueOf(row[1]);
				boolean locked = row.length > 2 && "Y".equalsIgnoreCase(String.valueOf(row[2]));
				boolean expired = row.length > 3 && "Y".equalsIgnoreCase(String.valueOf(row[3]));
				users.add(new DbUserInfo(user, host, locked, expired));
			}
			statusBar.setText(" Carregando lista de esquemas...");
			runQuery(ws, dialect.schemasQuery(), schemaRows -> {
				List<String> schemaNames = new ArrayList<>();
				for (Object[] row : schemaRows) {
					schemaNames.add(String.valueOf(row[0]));
				}
				statusBar.setText(" Pronto.");
				UserManagementDialog.open(this, dialect, users, schemaNames, schemaNameNow, currentSchemaTables,
						(statements, onOk, onErr) -> runDdlStatements(ws, statements, onOk, onErr),
						(sql, onRows, onErr) -> runQuery(ws, sql, onRows, onErr));
			}, ex -> {
				statusBar.setText(" Falha ao listar esquemas.");
				JOptionPane.showMessageDialog(this, "Falha ao listar esquemas:\n" + ex.getMessage(),
						"Usuarios e privilegios", JOptionPane.ERROR_MESSAGE);
			});
		}, ex -> {
			statusBar.setText(" Falha ao listar usuarios.");
			JOptionPane.showMessageDialog(this,
					"Falha ao listar usuarios do servidor (a conexao pode nao ter privilegio para ler mysql.user):\n"
							+ ex.getMessage(),
					"Usuarios e privilegios", JOptionPane.ERROR_MESSAGE);
		});
	}

	/**
	 * Abre o monitor de sessoes ativas ({@link ProcessListDialog}) — o
	 * dialogo e NAO-MODAL e faz suas proprias consultas (inclusive
	 * auto-refresh), entao basta abrir com a conexao ja pronta, sem
	 * pre-carregar nada (diferente de {@link #openUserManagement()}, que
	 * precisa da lista de usuarios ANTES de mostrar o dialogo).
	 */
	private void openProcessList() {
		if (activeWorkspace == null || !activeWorkspace.mgr.isConnected()) {
			statusBar.setText(" Conecte-se a um servidor antes de ver as sessoes ativas.");
			return;
		}
		Conexao ws = activeWorkspace;
		ProcessListDialog.open(this, dialect, (sql, onRows, onErr) -> runQuery(ws, sql, onRows, onErr),
				(statements, onOk, onErr) -> runDdlStatements(ws, statements, onOk, onErr));
	}

	/** Abre o visor de variaveis/status do servidor ({@link ServerStatusDialog}) — mesmo motivo de nao pre-carregar nada. */
	private void openServerStatus() {
		if (activeWorkspace == null || !activeWorkspace.mgr.isConnected()) {
			statusBar.setText(" Conecte-se a um servidor antes de ver variaveis/status.");
			return;
		}
		Conexao ws = activeWorkspace;
		ServerStatusDialog.open(this, dialect, (sql, onRows, onErr) -> runQuery(ws, sql, onRows, onErr));
	}

	/**
	 * Abre o Diagrama ER ({@link ErDiagramWindow}) do esquema aberto. As
	 * TABELAS ja estao em memoria ({@code currentSchema.tables()}, carregadas
	 * pela arvore de objetos), mas as chaves estrangeiras/primarias do
	 * schema INTEIRO ainda nao (ver {@link com.nureal.ide.core.metadata.MetadataService#loadSchemaForeignKeys}/
	 * {@code loadSchemaPrimaryKeys}, novas consultas em lote) — mesmo padrao
	 * "carrega antes de mostrar" de {@link #alterTable} e
	 * {@link #openUserManagement()}.
	 */
	private void openErDiagram() {
		if (activeWorkspace == null || !activeWorkspace.mgr.isConnected() || currentSchema == null) {
			statusBar.setText(" Abra um esquema antes de ver o diagrama ER.");
			return;
		}
		Conexao ws = activeWorkspace;
		String schemaName = currentSchema.name();
		List<TableInfo> tables = currentSchema.tables();
		statusBar.setText(" Carregando relacionamentos de \"" + schemaName + "\"...");
		new SwingWorker<Object[], Void>() {
			@Override
			protected Object[] doInBackground() throws Exception {
				Connection conn = ws.mgr.getConnection();
				List<SchemaForeignKey> fks = metadataService.loadSchemaForeignKeys(conn, schemaName);
				Map<String, Set<String>> pks = metadataService.loadSchemaPrimaryKeys(conn, schemaName);
				return new Object[] { fks, pks };
			}

			@SuppressWarnings("unchecked")
			@Override
			protected void done() {
				try {
					Object[] result = get();
					statusBar.setText(" Pronto.");
					ErDiagramWindow.open(MainWindow.this, schemaName, tables,
							(List<SchemaForeignKey>) result[0], (Map<String, Set<String>>) result[1]);
				} catch (Exception ex) {
					showError("Falha ao carregar relacionamentos do esquema", ex);
					statusBar.setText(" Falha ao carregar o diagrama ER.");
				}
			}
		}.execute();
	}

	/**
	 * Abre o visor de eventos agendados/replicacao ({@link EventsReplicationDialog})
	 * — mesmo motivo de nao pre-carregar nada de {@link #openProcessList()}/
	 * {@link #openServerStatus()} (o dialogo faz suas proprias consultas).
	 */
	private void openEventsReplication() {
		if (activeWorkspace == null || !activeWorkspace.mgr.isConnected() || currentSchema == null) {
			statusBar.setText(" Abra um esquema antes de ver eventos/replicacao.");
			return;
		}
		Conexao ws = activeWorkspace;
		String schemaName = currentSchema.name();
		EventsReplicationDialog.open(this, schemaName, dialect,
				(sql, onRows, onErr) -> runQuery(ws, sql, onRows, onErr),
				(sql, onResult, onErr) -> runQueryWithColumns(ws, sql, onResult, onErr));
	}

	/**
	 * Abre o Backup/Restauracao ({@link BackupRestoreDialog}) — fase 4 do
	 * GAP_ANALYSIS_DBA_DEV.md. Monta o {@code ConnectionTarget} a partir do
	 * {@code ConnectionProfile} do workspace ativo (host/porta/usuario/senha
	 * ja em maos, sem consulta nenhuma) — so a lista de tabelas vem de
	 * {@code currentSchema}, tambem ja em memoria.
	 */
	private void openBackupRestore() {
		if (activeWorkspace == null || !activeWorkspace.mgr.isConnected() || currentSchema == null
				|| activeWorkspace.profile == null) {
			statusBar.setText(" Abra um esquema antes de fazer backup/restauracao.");
			return;
		}
		String schemaName = currentSchema.name();
		var profile = activeWorkspace.profile;
		MySqlDumpRunner.ConnectionTarget target = new MySqlDumpRunner.ConnectionTarget(
				profile.host(), profile.port(), profile.user(), profile.password());
		List<String> tableNames = new ArrayList<>();
		for (TableInfo t : currentSchema.tables()) {
			tableNames.add(t.name());
		}
		BackupRestoreDialog.open(this, schemaName, target, tableNames,
				(options, outputFile, onLogLine, onDone, onError) ->
						runBackup(options, outputFile, onLogLine, onDone, onError),
				(options, inputFile, onLogLine, onDone, onError) ->
						runRestore(options, inputFile, onLogLine, onDone, onError));
	}

	/**
	 * Roda {@code mysqldump} em segundo plano (ver {@link MySqlDumpRunner#backup}
	 * — chamada BLOQUEANTE de proposito, por isso so dentro de
	 * {@code doInBackground}) e repassa cada linha de log (SwingWorker
	 * publish/process — mesmo idioma de {@link #runCsvImport}) e o resultado
	 * final de volta na EDT.
	 */
	private void runBackup(MySqlDumpRunner.BackupOptions options, Path outputFile,
			java.util.function.Consumer<String> onLogLine, java.util.function.Consumer<MySqlDumpRunner.RunResult> onDone,
			java.util.function.Consumer<Exception> onError) {
		new SwingWorker<MySqlDumpRunner.RunResult, String>() {
			@Override
			protected MySqlDumpRunner.RunResult doInBackground() throws Exception {
				return MySqlDumpRunner.backup(options, outputFile, this::publish);
			}

			@Override
			protected void process(List<String> chunks) {
				for (String line : chunks) {
					onLogLine.accept(line);
				}
			}

			@Override
			protected void done() {
				try {
					onDone.accept(get());
				} catch (java.util.concurrent.ExecutionException ex) {
					Throwable cause = ex.getCause();
					onError.accept(cause instanceof Exception e2 ? e2 : ex);
				} catch (Exception ex) {
					onError.accept(ex);
				}
			}
		}.execute();
	}

	/** Igual a {@link #runBackup}, so que para {@code mysql < arquivo.sql} (ver {@link MySqlDumpRunner#restore}). */
	private void runRestore(MySqlDumpRunner.RestoreOptions options, Path inputFile,
			java.util.function.Consumer<String> onLogLine, java.util.function.Consumer<MySqlDumpRunner.RunResult> onDone,
			java.util.function.Consumer<Exception> onError) {
		new SwingWorker<MySqlDumpRunner.RunResult, String>() {
			@Override
			protected MySqlDumpRunner.RunResult doInBackground() throws Exception {
				return MySqlDumpRunner.restore(options, inputFile, this::publish);
			}

			@Override
			protected void process(List<String> chunks) {
				for (String line : chunks) {
					onLogLine.accept(line);
				}
			}

			@Override
			protected void done() {
				try {
					onDone.accept(get());
				} catch (java.util.concurrent.ExecutionException ex) {
					Throwable cause = ex.getCause();
					onError.accept(cause instanceof Exception e2 ? e2 : ex);
				} catch (Exception ex) {
					onError.accept(ex);
				}
			}
		}.execute();
	}

	/** Abre uma aba de editor nova com o DDL gerado pelo assistente — botao "Enviar para o editor". */
	private void sendDdlToEditor(String ddl) {
		String title = "DDL";
		int n = 1;
		while (titleExists(title)) {
			title = "DDL " + (++n);
		}
		if (addQueryTab(title, ddl)) {
			statusBar.setText(" DDL enviado para uma nova aba do editor.");
		} else {
			statusBar.setText(" Nao foi possivel abrir uma aba nova (limite de abas atingido).");
		}
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
		// "Gerar SELECT"/"Gerar JOIN" (pedido explicito do usuario): tabela E
		// view podem virar um SELECT (ambas tem colunas conhecidas), mas so
		// tabela pode virar JOIN (views nao tem FK propria no schema).
		if (obj.type() == NodeType.TABLE || obj.type() == NodeType.VIEW) {
			menu.addSeparator();
			JMenuItem generateSelect = new JMenuItem("Gerar SELECT");
			generateSelect.addActionListener(a -> generateSelect(obj));
			menu.add(generateSelect);
			if (obj.type() == NodeType.TABLE) {
				menu.add(buildGenerateJoinItem(obj));
			}
		}
		if (obj.type() == NodeType.TABLE) {
			menu.addSeparator();
			JMenuItem alterTable = new JMenuItem("Alterar tabela... (assistente de DDL)");
			alterTable.addActionListener(a -> alterTable(obj));
			menu.add(alterTable);
			JMenuItem createTable = new JMenuItem("Nova tabela...");
			createTable.addActionListener(a -> createTable());
			menu.add(createTable);
			JMenuItem importCsvItem = new JMenuItem("Importar CSV...");
			importCsvItem.addActionListener(a -> importCsv(obj));
			menu.add(importCsvItem);
			menu.addSeparator();
			menu.add(buildTableMaintenanceMenu(obj));
		}
		if (obj.type() == NodeType.VIEW) {
			menu.addSeparator();
			JMenuItem editView = new JMenuItem("Editar view... (assistente)");
			editView.addActionListener(a -> editView(obj));
			menu.add(editView);
			JMenuItem createViewItem = new JMenuItem("Nova view...");
			createViewItem.addActionListener(a -> createView());
			menu.add(createViewItem);
			JMenuItem dropView = new JMenuItem("Excluir view...");
			dropView.addActionListener(a -> dropView(obj));
			menu.add(dropView);
		}
		if (obj.type() == NodeType.TRIGGER) {
			menu.addSeparator();
			JMenuItem editTrigger = new JMenuItem("Editar trigger... (assistente)");
			editTrigger.addActionListener(a -> editTrigger(obj));
			menu.add(editTrigger);
			JMenuItem createTriggerItem = new JMenuItem("Novo trigger...");
			createTriggerItem.addActionListener(a -> createTrigger());
			menu.add(createTriggerItem);
			JMenuItem dropTrigger = new JMenuItem("Excluir trigger...");
			dropTrigger.addActionListener(a -> dropTrigger(obj));
			menu.add(dropTrigger);
		}
		if (obj.type() == NodeType.ROUTINE) {
			menu.addSeparator();
			boolean isProcedure = "PROCEDURE".equals(obj.kind());
			JMenuItem createRoutineItem = new JMenuItem(isProcedure ? "Nova procedure..." : "Nova function...");
			createRoutineItem.addActionListener(a -> createRoutine(obj.kind()));
			menu.add(createRoutineItem);
			JMenuItem dropRoutine = new JMenuItem(isProcedure ? "Excluir procedure..." : "Excluir function...");
			dropRoutine.addActionListener(a -> dropRoutine(obj));
			menu.add(dropRoutine);
		}
		return menu;
	}

	/**
	 * "Importar CSV..." (fase 3 do GAP_ANALYSIS_DBA_DEV.md) — le e parseia o
	 * arquivo em background ({@link CsvUtil#parseLine}, linha a linha),
	 * assume a PRIMEIRA linha como cabecalho (unico modo suportado hoje),
	 * depois abre o {@link CsvImportDialog} ja com os dados prontos (mesmo
	 * padrao "carrega antes de mostrar" de {@link #openUserManagement()}).
	 */
	private void importCsv(ObjNode obj) {
		if (activeWorkspace == null || !activeWorkspace.mgr.isConnected() || currentSchema == null) {
			statusBar.setText(" Abra um esquema antes de importar CSV.");
			return;
		}
		JFileChooser fc = new JFileChooser();
		fc.setDialogTitle("Importar CSV para \"" + obj.name() + "\"");
		fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Arquivo CSV (*.csv)", "csv"));
		if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
			return;
		}
		File file = fc.getSelectedFile();
		Conexao ws = activeWorkspace;
		String schemaName = currentSchema.name();
		List<ColumnInfo> tableColumns = obj.table() != null ? obj.table().columns() : List.of();
		statusBar.setText(" Lendo " + file.getName() + "...");
		new SwingWorker<Object[], Void>() {
			@Override
			protected Object[] doInBackground() throws Exception {
				List<String> headers = null;
				List<String[]> rows = new ArrayList<>();
				try (java.io.BufferedReader reader = java.nio.file.Files.newBufferedReader(file.toPath(),
						java.nio.charset.StandardCharsets.UTF_8)) {
					String line;
					while ((line = reader.readLine()) != null) {
						List<String> fields = CsvUtil.parseLine(line, ',');
						if (headers == null) {
							headers = fields;
						} else {
							rows.add(fields.toArray(new String[0]));
						}
					}
				}
				return new Object[] { headers == null ? List.of() : headers, rows };
			}

			@Override
			@SuppressWarnings("unchecked")
			protected void done() {
				try {
					Object[] result = get();
					List<String> headers = (List<String>) result[0];
					List<String[]> rows = (List<String[]>) result[1];
					statusBar.setText(" Pronto.");
					if (rows.isEmpty()) {
						JOptionPane.showMessageDialog(MainWindow.this,
								"O arquivo nao tem linhas de dados (so cabecalho, ou esta vazio).",
								"Importar CSV", JOptionPane.WARNING_MESSAGE);
						return;
					}
					CsvImportDialog.open(MainWindow.this, schemaName, obj.name(), tableColumns, headers, rows,
							(schema, tableName, targetColumns, csvRows, onProgress, onOk, onErr) -> runCsvImport(ws,
									schema, tableName, targetColumns, csvRows, onProgress, onOk, onErr));
				} catch (Exception ex) {
					statusBar.setText(" Falha ao ler o arquivo CSV.");
					JOptionPane.showMessageDialog(MainWindow.this, "Falha ao ler o arquivo:\n" + ex.getMessage(),
							"Importar CSV", JOptionPane.ERROR_MESSAGE);
				}
			}
		}.execute();
	}

	/**
	 * Insere as linhas em lote (PreparedStatement + executeBatch, a cada 500
	 * linhas) numa UNICA transacao (tudo ou nada, mesmo espirito do
	 * {@link GridEditController#apply}). Todo valor vai como STRING via
	 * {@code setString} (celula vazia vira NULL) — o MySQL faz a conversao
	 * implicita pro tipo real da coluna; suficiente para o caso de uso
	 * (importar CSV de teste/planilha), sem precisar resolver o tipo exato
	 * de cada coluna de destino aqui.
	 */
	private void runCsvImport(Conexao ws, String schema, String table, List<String> columns, List<String[]> rows,
			java.util.function.IntConsumer onProgress, Runnable onSuccess,
			java.util.function.Consumer<Exception> onErr) {
		new SwingWorker<Void, Integer>() {
			private static final int BATCH_SIZE = 500;

			@Override
			protected Void doInBackground() throws Exception {
				Connection conn = ws.mgr.getConnection();
				boolean prevAutoCommit = conn.getAutoCommit();
				conn.setAutoCommit(false);
				StringBuilder sql = new StringBuilder("INSERT INTO ")
						.append(dialect.quoteIdentifier(schema)).append('.').append(dialect.quoteIdentifier(table))
						.append(" (");
				for (int i = 0; i < columns.size(); i++) {
					sql.append(i > 0 ? ", " : "").append(dialect.quoteIdentifier(columns.get(i)));
				}
				sql.append(") VALUES (");
				for (int i = 0; i < columns.size(); i++) {
					sql.append(i > 0 ? ", ?" : "?");
				}
				sql.append(')');
				try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
					int inBatch = 0;
					for (int r = 0; r < rows.size(); r++) {
						String[] row = rows.get(r);
						for (int c = 0; c < columns.size(); c++) {
							String value = c < row.length ? row[c] : null;
							if (value == null || value.isEmpty()) {
								ps.setNull(c + 1, java.sql.Types.VARCHAR);
							} else {
								ps.setString(c + 1, value);
							}
						}
						ps.addBatch();
						inBatch++;
						if (inBatch >= BATCH_SIZE) {
							ps.executeBatch();
							inBatch = 0;
						}
						publish(r + 1);
					}
					if (inBatch > 0) {
						ps.executeBatch();
					}
					conn.commit();
				} catch (Exception ex) {
					conn.rollback();
					throw ex;
				} finally {
					conn.setAutoCommit(prevAutoCommit);
				}
				return null;
			}

			@Override
			protected void process(List<Integer> chunks) {
				if (!chunks.isEmpty()) {
					onProgress.accept(chunks.get(chunks.size() - 1));
				}
			}

			@Override
			protected void done() {
				try {
					get();
					onSuccess.run();
				} catch (java.util.concurrent.ExecutionException ex) {
					Throwable cause = ex.getCause();
					onErr.accept(cause instanceof Exception e2 ? e2 : ex);
				} catch (Exception ex) {
					onErr.accept(ex);
				}
			}
		}.execute();
	}

	/**
	 * Submenu "Manutencao" (fase 2 do GAP_ANALYSIS_DBA_DEV.md) — OPTIMIZE/
	 * ANALYZE/CHECK TABLE com um clique, em vez de precisar digitar o SQL na
	 * mao (o que a maioria dos DBAs vindos de phpMyAdmin/Workbench estranha
	 * nao ter, ver o documento). As 3 instrucoes devolvem um RESULT SET (nao
	 * so uma contagem de linhas — colunas Table/Op/Msg_type/Msg_text), entao
	 * usam {@link #runQuery} (nao {@link #runDdlStatements}, que descarta
	 * qualquer resultado) e mostram a mensagem do servidor.
	 */
	private JMenu buildTableMaintenanceMenu(ObjNode obj) {
		JMenu menu = new JMenu("Manutencao");
		menu.add(maintenanceItem("Otimizar (OPTIMIZE TABLE)", obj, dialect::optimizeTableStatement));
		menu.add(maintenanceItem("Recalcular estatisticas (ANALYZE TABLE)", obj, dialect::analyzeTableStatement));
		menu.add(maintenanceItem("Verificar integridade (CHECK TABLE)", obj, dialect::checkTableStatement));
		return menu;
	}

	private JMenuItem maintenanceItem(String label, ObjNode obj,
			java.util.function.BiFunction<String, String, String> statementBuilder) {
		JMenuItem item = new JMenuItem(label);
		item.addActionListener(a -> runTableMaintenance(obj, statementBuilder.apply(currentSchema.name(), obj.name())));
		return item;
	}

	private void runTableMaintenance(ObjNode obj, String sql) {
		if (activeWorkspace == null || !activeWorkspace.mgr.isConnected() || currentSchema == null) {
			statusBar.setText(" Abra um esquema antes de rodar manutencao de tabela.");
			return;
		}
		Conexao ws = activeWorkspace;
		statusBar.setText(" Executando manutencao em \"" + obj.name() + "\"...");
		runQuery(ws, sql, rows -> {
			statusBar.setText(" Pronto.");
			StringBuilder sb = new StringBuilder();
			for (Object[] row : rows) {
				// Table, Op, Msg_type, Msg_text (ordem padrao do MySQL para
				// OPTIMIZE/ANALYZE/CHECK TABLE) — junta so o essencial
				// (tipo + mensagem) numa linha por resultado.
				String msgType = row.length > 2 ? String.valueOf(row[2]) : "";
				String msgText = row.length > 3 ? String.valueOf(row[3]) : "";
				sb.append(msgType).append(": ").append(msgText).append('\n');
			}
			JOptionPane.showMessageDialog(this,
					sb.length() > 0 ? sb.toString() : "Concluido (sem mensagem do servidor).",
					"Manutencao de tabela — " + obj.name(), JOptionPane.INFORMATION_MESSAGE);
		}, ex -> {
			statusBar.setText(" Falha na manutencao de \"" + obj.name() + "\".");
			JOptionPane.showMessageDialog(this, "Falha ao executar manutencao:\n" + ex.getMessage(),
					"Manutencao de tabela", JOptionPane.ERROR_MESSAGE);
		});
	}

	/**
	 * "Gerar SELECT" (menu de contexto da arvore de objetos): monta um SELECT
	 * completo — uma coluna por linha, na mesma ordem do banco — e abre numa
	 * aba de editor NOVA (mesmo padrao de {@link #sendDdlToEditor}, so que
	 * para uma consulta em vez de um DDL). Usa as colunas que a arvore JA tem
	 * carregadas ({@code obj.table()}, preenchidas em {@link #addTableCategory}),
	 * sem nenhum round-trip extra ao banco — ao contrario de "Gerar JOIN"
	 * (FKs), que precisa do {@link TableMetadataCache}.
	 */
	private void generateSelect(ObjNode obj) {
		List<ColumnInfo> cols = (obj.table() != null) ? obj.table().columns() : List.of();
		StringBuilder sql = new StringBuilder("SELECT");
		if (cols.isEmpty()) {
			sql.append(" *\n");
		} else {
			sql.append('\n');
			for (int i = 0; i < cols.size(); i++) {
				// SEM crase/aspas de proposito: isto vai para o editor como um
				// SQL "escrito pelo usuario", nao uma instrucao interna — pedido
				// explicito do usuario ("nenhuma das instrucoes geradas devem
				// ter a crase... nao usamos a crase quando escrevemos"). Ver
				// mesma decisao em insertJoinStatement.
				sql.append("    ").append(cols.get(i).name());
				sql.append(i < cols.size() - 1 ? ",\n" : "\n");
			}
		}
		sql.append("FROM ").append(obj.name()).append(";\n");

		String baseTitle = "SELECT " + obj.name();
		String title = baseTitle;
		int n = 1;
		while (titleExists(title)) {
			title = baseTitle + " " + (++n);
		}
		if (addQueryTab(title, sql.toString())) {
			statusBar.setText(" SELECT gerado numa nova aba do editor.");
		} else {
			statusBar.setText(" Nao foi possivel abrir uma aba nova (limite de abas atingido).");
		}
	}

	/**
	 * "Gerar JOIN" (menu de contexto da arvore de objetos): um submenu por
	 * chave estrangeira que ESTA tabela declara (colunas locais apontando
	 * para outra tabela), e dentro dele uma opcao por TIPO de juncao (INNER/
	 * LEFT/RIGHT — ver {@link #buildJoinTypeMenu}). Ao escolher uma, monta um
	 * {@code SELECT ... FROM ... JOIN ... ON ...} COMPLETO e ja FUNCIONAL
	 * (as duas tabelas com alias curto, ver {@link #deriveAlias}) e cola
	 * numa linha NOVA no editor SQL ATIVO, sempre ABAIXO do cursor (nunca
	 * grudado no texto ja existente — bug relatado pelo usuario: uma versao
	 * anterior colava so a clausula JOIN direto na posicao do cursor, o que
	 * gerou "select * from operationJOIN ..." quando o cursor estava logo
	 * apos "operation", sem separador nenhum; ver {@link #insertOnNewLineBelowCursor}).
	 * Gera um bloco AUTOSSUFICIENTE de proposito (nao tenta reaproveitar/
	 * reescrever o FROM que ja estiver no editor) — assim funciona sozinho
	 * do jeito que sai, sem depender de o usuario ja ter dado um alias
	 * compativel a tabela que estava digitando.
	 * <p>
	 * So cobre o sentido "para fora" (FK desta tabela apontando para outra);
	 * o sentido inverso (outras tabelas que referenciam esta) exigiria varrer
	 * o schema inteiro em vez de uma unica tabela — fora do escopo desta
	 * primeira versao.
	 * <p>
	 * Os metadados (FKs) vem do {@link TableMetadataCache}, carregados sob
	 * demanda — na PRIMEIRA vez que o usuario abre este menu para uma tabela
	 * ainda nao consultada nesta sessao, a carga e disparada em segundo
	 * plano e o item aparece desabilitado ("carregando..."); reabrir o menu
	 * de novo (ja deve estar rapido, quase instantaneo) mostra as opcoes de
	 * verdade.
	 */
	private JMenuItem buildGenerateJoinItem(ObjNode obj) {
		String schemaName = (currentSchema != null) ? currentSchema.name() : null;
		TableDetails details = tableMetadataCache.get(connectionManager(), schemaName, obj.name(), () -> { });
		if (details == null) {
			JMenuItem loading = new JMenuItem("Gerar JOIN (carregando estrutura...)");
			loading.setEnabled(false);
			return loading;
		}
		List<ForeignKeyInfo> fks = details.foreignKeys();
		if (fks.isEmpty()) {
			JMenuItem none = new JMenuItem("Gerar JOIN");
			none.setEnabled(false);
			none.setToolTipText("Esta tabela nao tem chaves estrangeiras conhecidas.");
			return none;
		}
		if (fks.size() == 1) {
			ForeignKeyInfo fk = fks.get(0);
			return buildJoinTypeMenu(obj.name(), fk, "Gerar JOIN (" + fk.referencedTable() + ")");
		}
		// Mais de uma FK (ex.: varias colunas apontando para tabelas
		// diferentes, ou ate para a MESMA tabela mais de uma vez): submenu
		// com uma opcao por relacionamento, identificado pela tabela
		// referenciada + a(s) coluna(s) locais usadas nela — cada uma, por
		// sua vez, com os 3 tipos de juncao dentro.
		JMenu submenu = new JMenu("Gerar JOIN");
		for (ForeignKeyInfo fk : fks) {
			String label = fk.referencedTable() + " (" + String.join(", ", fk.columns()) + ")";
			submenu.add(buildJoinTypeMenu(obj.name(), fk, label));
		}
		return submenu;
	}

	/**
	 * Ponte entre {@link TableMetadataCache} (cache de FK/PK/indices desta
	 * janela) e {@link SqlCompletionProvider.ForeignKeyLookup} — liga os dois
	 * uma unica vez no construtor (ver {@code completionProvider.setForeignKeyLookup}).
	 * {@code SqlCompletionProvider} vive em {@code core.autocomplete} (sem
	 * dependencia de {@code ui}), entao nao pode chamar {@link TableMetadataCache}
	 * diretamente — so por essa ponte, sem acoplar os dois pacotes. Mesma
	 * regra de "so o sentido para fora" de {@link #buildGenerateJoinItem}:
	 * devolve so as FKs que a PROPRIA tabela declara.
	 */
	private List<ForeignKeyInfo> lookupForeignKeysForCompletion(String tableName) {
		if (currentSchema == null) {
			return List.of();
		}
		TableDetails details = tableMetadataCache.get(connectionManager(), currentSchema.name(), tableName, () -> { });
		return (details != null) ? details.foreignKeys() : List.of();
	}

	/** Submenu com os 3 tipos de juncao suportados para uma FK especifica (ver {@link #buildGenerateJoinItem}). */
	private JMenu buildJoinTypeMenu(String tableName, ForeignKeyInfo fk, String label) {
		JMenu menu = new JMenu(label);
		menu.add(joinTypeItem("INNER JOIN", tableName, fk));
		menu.add(joinTypeItem("LEFT JOIN", tableName, fk));
		menu.add(joinTypeItem("RIGHT JOIN", tableName, fk));
		return menu;
	}

	private JMenuItem joinTypeItem(String joinKeyword, String tableName, ForeignKeyInfo fk) {
		JMenuItem item = new JMenuItem(joinKeyword);
		item.addActionListener(a -> insertJoinStatement(tableName, fk, joinKeyword));
		return item;
	}

	/**
	 * Monta {@code SELECT * FROM <tabela> <alias> <tipo de juncao> <tabela
	 * referenciada> <alias> ON <alias>.<col> = <alias>.<col referenciada>}
	 * (com {@code AND} entre pares de coluna, para FK composta) — SEMPRE um
	 * bloco completo e ja funcional, nunca so a clausula JOIN solta — e cola
	 * no editor SQL ATIVO numa linha NOVA, sempre abaixo do cursor (ver
	 * {@link #insertOnNewLineBelowCursor}).
	 */
	private void insertJoinStatement(String tableName, ForeignKeyInfo fk, String joinKeyword) {
		SqlEditorPane editor = currentEditor();
		if (editor == null) {
			statusBar.setText(" Abra uma aba de editor antes de gerar o JOIN.");
			return;
		}
		String refTable = fk.referencedTable();
		String alias = TableAliasGenerator.deriveAlias(tableName);
		String refAlias = TableAliasGenerator.deriveDistinctAlias(refTable, tableName, alias);
		List<String> cols = fk.columns();
		List<String> refCols = fk.referencedColumns();
		// SEM crase/aspas de proposito (mesmo criterio de generateSelect): o
		// resultado vai para o editor como SQL "escrito pelo usuario".
		StringBuilder sql = new StringBuilder("SELECT * FROM ")
				.append(tableName).append(' ').append(alias).append('\n')
				.append(joinKeyword).append(' ').append(refTable).append(' ').append(refAlias)
				.append(" ON ");
		for (int i = 0; i < cols.size(); i++) {
			if (i > 0) {
				sql.append(" AND ");
			}
			sql.append(alias).append('.').append(cols.get(i))
					.append(" = ")
					.append(refAlias).append('.').append(refCols.get(i));
		}
		sql.append(';');
		insertOnNewLineBelowCursor(editor.textArea(), sql.toString());
		editor.textArea().requestFocusInWindow();
	}

	/**
	 * Insere {@code text} SEMPRE numa linha NOVA, logo abaixo da linha onde o
	 * cursor esta agora — nunca grudado no fim do texto ja digitado naquela
	 * linha. Antes desta correcao, "Gerar JOIN" usava
	 * {@code textArea.replaceSelection(...)} (cola exatamente na posicao do
	 * cursor, sem nenhum separador): se o cursor estivesse logo apos
	 * "operation" (sem espaco/quebra de linha depois), o resultado colado
	 * virava "operationJOIN ..." — bug relatado pelo usuario com uma
	 * captura de tela. Devolve o cursor para o fim do texto inserido.
	 */
	private static void insertOnNewLineBelowCursor(org.fife.ui.rsyntaxtextarea.RSyntaxTextArea textArea, String text) {
		try {
			int caret = textArea.getCaretPosition();
			int line = textArea.getLineOfOffset(caret);
			int lineEnd = textArea.getLineEndOffset(line);
			// getLineEndOffset(line) ja inclui a quebra de linha (aponta pro
			// INICIO da linha seguinte) para toda linha que NAO seja a
			// ultima do documento — so a ultima (sem "\n" no fim) precisa
			// que a gente insira essa quebra na mao antes do texto.
			boolean lastLine = (line == textArea.getLineCount() - 1);
			String insertText = lastLine ? ("\n" + text + "\n") : (text + "\n");
			textArea.insert(insertText, lineEnd);
			textArea.setCaretPosition(Math.min(lineEnd + insertText.length(), textArea.getDocument().getLength()));
		} catch (javax.swing.text.BadLocationException ex) {
			AppLogger.warning("Falha ao inserir SQL gerado no editor", ex);
		}
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
	 * Historico de objetos abertos a partir do editor SQL (CTRL+Clique ou
	 * F12 — secoes 8.3/8.6 do pedido "Navegacao Inteligente e Interativa"),
	 * do mais antigo (fundo da pilha) ao mais recente (topo). ALT+Seta-
	 * esquerda ({@link #navigateBack}) volta um passo nesta pilha, reabrindo
	 * a tela de propriedades do objeto anterior — nao rastreia nada aberto
	 * via duplo-clique na arvore, so o que veio do editor mesmo.
	 */
	private final java.util.Deque<ObjNode> objectNavHistory = new java.util.ArrayDeque<>();

	/**
	 * Chamado pelo editor SQL quando o usuario da CTRL+Clique OU aperta F12
	 * sobre um objeto de banco reconhecido (secoes 8.3/8.6 do pedido
	 * "Navegacao Inteligente e Interativa" — ver
	 * {@link SqlEditorPane.ObjectOpenHandler}). Monta um {@link ObjNode}
	 * equivalente ao que a arvore de objetos usaria pro mesmo objeto e
	 * reaproveita a MESMA tela de propriedades — CTRL+Clique/F12 no editor e
	 * duplo-clique na setinha da arvore levam ao mesmo lugar, sem duplicar UI
	 * nenhuma. Empilha o objeto em {@link #objectNavHistory} para o ALT+Seta-
	 * esquerda ({@link #navigateBack}) poder voltar a ele depois.
	 */
	private void openEditorObject(String kind, String name, TableInfo table) {
		NodeType type = switch (kind) {
			case "TABLE" -> NodeType.TABLE;
			case "VIEW" -> NodeType.VIEW;
			case "TRIGGER" -> NodeType.TRIGGER;
			default -> NodeType.ROUTINE; // PROCEDURE ou FUNCTION
		};
		ObjNode node = new ObjNode(type, name, name, kind, table, null);
		objectNavHistory.push(node);
		showObjectProperties(node);
	}

	/**
	 * ALT+Seta-esquerda no editor SQL (secao 8.6): volta ao objeto anterior
	 * na pilha {@link #objectNavHistory} — remove o topo (o objeto atual) e
	 * reabre a tela de propriedades do que ficou no topo em seguida. Pilha
	 * vazia ou com um so item (nada "anterior" a voltar): so avisa na barra
	 * de status, sem erro.
	 */
	private void navigateBack() {
		if (!objectNavHistory.isEmpty()) {
			objectNavHistory.pop();
		}
		ObjNode previous = objectNavHistory.peek();
		if (previous != null) {
			showObjectProperties(previous);
		} else if (statusBar != null) {
			statusBar.setText(" Sem objeto anterior no historico de navegacao.");
		}
	}


	/**
	 * Janela de propriedades: para tabelas/views mostra a grade de colunas; para
	 * todos os objetos carrega a definicao (DDL) sob demanda.
	 */
	private void showObjectProperties(ObjNode obj) {
		// SelectableLabel (nao JLabel comum): o nome do objeto e o "kind ·
		// schema" ficam selecionaveis/copiaveis com Ctrl+C — pedido
		// explicito do usuario ("qualquer texto aqui dentro pode ser
		// selecionado... ate nas propriedades").
		JComponent title = SelectableLabel.of(obj.name());
		title.setFont(title.getFont().deriveFont(14f));
		// Nivel PRIMARIO (nome do objeto) — antes so definia o peso (Bold),
		// sem cor explicita.
		Typography.primary(title);
		JComponent sub = SelectableLabel.of(prettyKind(obj.kind()) + "  ·  " + currentSchema.name());
		// setForeground com um Color explicito CONGELA o valor (nao acompanha
		// GridTheme.applyPalette num toggle de tema ao vivo, com a janela ja
		// aberta — mesma familia de bug corrigida em FkInspectorWindow: "eu
		// preciso fechar e abrir de novo"). O updateUI() do dialogo abaixo
		// reaplica isto sempre que o L&F mudar, sem precisar fechar/reabrir.
		Runnable applySubColor = () -> Typography.tertiary(sub);
		applySubColor.run();

		// JDialog nao e um JComponent (nao tem updateUI() proprio) — so o
		// JRootPane dele tem. createRootPane() e o ponto de extensao padrao
		// do Swing pra receber o updateUI() em cascata do
		// FlatLaf.updateUI() chamado em toggleTheme().
		JDialog dialog = new JDialog(this, prettyKind(obj.kind()) + " - " + obj.name(), false) {
			private static final long serialVersionUID = 1L;

			@Override
			protected JRootPane createRootPane() {
				return new JRootPane() {
					private static final long serialVersionUID = 1L;

					@Override
					public void updateUI() {
						super.updateUI();
						applySubColor.run();
					}
				};
			}
		};
		dialog.setSize(560, 460);
		dialog.setLocationRelativeTo(this);
		dialog.setLayout(new BorderLayout());

		JPanel head = new JPanel(new BorderLayout());
		head.setBorder(BorderFactory.createEmptyBorder(10, 12, 8, 12));
		head.add(title, BorderLayout.NORTH);
		head.add(sub, BorderLayout.SOUTH);

		JTabbedPane tabs = new JTabbedPane();
		boolean isTableLike = obj.table() != null;
		ResultTableModel colModel = null;
		ResultTableModel idxModel = null;
		ResultTableModel fkModel = null;
		if (isTableLike) {
			// SEM coluna "#" de proposito: o ResultGrid ja tem sua propria
			// numeracao de linha (RowNumberGutter, a coluna cinza sem
			// cabecalho a esquerda) — repetir a posicao como coluna de DADOS
			// (colorida em azul/numerico, ver GridTheme.COLOR_INTEGER) criava
			// duas numeracoes lado a lado, relatado como bug pelo usuario.
			colModel = metadataModel("Colunas", "Coluna", "Tipo", "Nulo", "Chave", "Default", "Extra",
					"Comentario");
			tabs.addTab("Colunas", metadataGrid("Colunas", colModel, 1));
			// Indices e FKs sao especificos de tabelas (views nao tem).
			if ("TABLE".equals(obj.kind())) {
				idxModel = metadataModel("Indices", "Indice", "Unico", "Tipo", "Colunas");
				tabs.addTab("Indices", metadataGrid("Indices", idxModel));
				fkModel = metadataModel("Chaves estrangeiras", "Constraint", "Coluna(s)", "Referencia",
						"Coluna(s) ref.", "On Update", "On Delete");
				tabs.addTab("Chaves estrangeiras", metadataGrid("Chaves estrangeiras", fkModel));
			}
		}

		// RSyntaxTextArea com o MESMO destaque de sintaxe/paleta semantica do
		// editor de consultas (ver SqlEditorPane#styleAsReadOnlySql) — antes
		// era um JTextArea puro, sem NENHUMA cor (pedido do "Sistema Semantico
		// de Cores por Tipo de Dado": o DDL deve usar a mesma paleta em
		// qualquer lugar do app onde SQL apareca).
		org.fife.ui.rsyntaxtextarea.RSyntaxTextArea ddlArea =
				new org.fife.ui.rsyntaxtextarea.RSyntaxTextArea("Carregando definicao...");
		SqlEditorPane.styleAsReadOnlySql(ddlArea);
		tabs.addTab("DDL", new JScrollPane(ddlArea));

		dialog.add(head, BorderLayout.NORTH);
		dialog.add(tabs, BorderLayout.CENTER);
		dialog.setVisible(true);

		if (isTableLike) {
			loadTableDetailsInto(obj, colModel, idxModel, fkModel);
		}
		loadDefinition(obj, ddlArea);
	}

	/**
	 * Cria um {@link ResultTableModel} "manual" (sem ResultSet por tras) para
	 * as tabelas de metadados somente-leitura do dialogo de propriedades de
	 * objeto (Colunas/Indices/Chaves estrangeiras — ver
	 * {@code showObjectProperties}). Pedido explicito do usuario: essas
	 * tabelas devem reusar o MESMO componente de grade das consultas
	 * ({@link ResultGrid}), nao apenas imitar seu visual — assim qualquer
	 * mudanca futura na grade (cores, filtro, exportacao, atalhos) se propaga
	 * automaticamente para todo lugar que apresenta uma tabela, sem
	 * duplicacao de estilo. {@code sourceTables}/{@code realColumnNames}/
	 * {@code sqlTypeNames} ficam todos nulos de proposito: sem tabela de
	 * origem, o {@code ColumnMetadataResolver} da grade nunca tenta resolver
	 * PK/FK/indice via banco para estas colunas puramente descritivas — zero
	 * round-trip extra ao JDBC.
	 */
	private ResultTableModel metadataModel(String title, String... columns) {
		Vector<String> names = new Vector<>(Arrays.asList(columns));
		Class<?>[] types = new Class<?>[columns.length];
		Arrays.fill(types, String.class);
		String[] nulls = new String[columns.length];
		return new ResultTableModel(names, types, nulls, nulls, nulls);
	}

	/** Envolve um {@link #metadataModel} num {@link ResultGrid} de verdade, com exportacao Excel. */
	private JComponent metadataGrid(String title, ResultTableModel model) {
		String schemaName = (currentSchema != null) ? currentSchema.name() : null;
		ResultGrid grid = new ResultGrid(model, connectionManager(), schemaName, tableMetadataCache,
				() -> exportMetadataTable(title, model), this::scaledPx, resultRowHeightBasePx());
		return grid;
	}

	/**
	 * Igual a {@link #metadataGrid(String, ResultTableModel)}, mas colore o
	 * valor de {@code typeColumnIndex} pela categoria semantica do TIPO que
	 * ele representa (ex.: a celula com o texto "VARCHAR(255)" fica na MESMA
	 * cor que uma coluna VARCHAR ganharia na grade de resultados — ver
	 * {@link GridTheme#colorFor}). Usado pela aba "Colunas" do dialogo de
	 * propriedades do objeto — pedido do "Sistema Semantico de Cores por Tipo
	 * de Dado": um tipo de dado deve ter a MESMA cor em qualquer lugar do app
	 * onde apareca, inclusive nesta lista de metadados (nao so na grade de
	 * resultados).
	 */
	private JComponent metadataGrid(String title, ResultTableModel model, int typeColumnIndex) {
		ResultGrid grid = (ResultGrid) metadataGrid(title, model);
		TableColumn column = grid.table().getColumnModel().getColumn(typeColumnIndex);
		column.setCellRenderer(new DefaultTableCellRenderer() {
			private static final long serialVersionUID = 1L;

			@Override
			public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
					boolean hasFocus, int row, int col) {
				Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, col);
				if (!isSelected && value != null) {
					c.setForeground(GridTheme.colorFor(SqlTypeKind.classify(value.toString())));
				}
				return c;
			}
		});
		return grid;
	}

	/** Exporta uma unica tabela de metadados (Colunas/Indices/FKs) para Excel — mesmo fluxo de {@link #exportResult}. */
	private void exportMetadataTable(String title, ResultTableModel model) {
		File file = chooseSaveFile(title);
		if (file != null) {
			doExport(List.of(new ExcelExporter.TableSheet(title, model)), file);
		}
	}

	/** Preenche as grades de colunas, indices e FKs em segundo plano. */
	private void loadTableDetailsInto(ObjNode obj, ResultTableModel colModel, ResultTableModel idxModel,
			ResultTableModel fkModel) {
		new SwingWorker<TableDetails, Void>() {
			@Override
			protected TableDetails doInBackground() throws Exception {
				Connection conn = connectionManager().getConnection();
				return metadataService.loadTableDetails(conn, currentSchema.name(), obj.name());
			}

			@Override
			protected void done() {
				try {
					TableDetails d = get();
					for (ColumnDetail c : d.columns()) {
						colModel.addRow(new Object[] { c.name(), c.type(), c.nullable() ? "Sim" : "Nao",
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
				if (!connectionManager().isConnected()) {
					return "Sem conexao ativa.";
				}
				Connection conn = connectionManager().getConnection();
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
		SCHEMA, SCHEMA_PICK, CATEGORY, TABLE, VIEW, ROUTINE, TRIGGER, COLUMN,
		// Linha sintetica, sem acao nenhuma associada (nenhum listener de
		// clique/menu de contexto trata este tipo — ver ObjectTreeCellRenderer
		// e MainWindow#handleObjectTreeDoubleClick/#maybeShowObjectContextMenu,
		// nenhum dos dois "case" bate com ela, entao um clique nao faz nada):
		// mostrada SO quando uma busca nao encontra nenhum objeto (ver
		// MainWindow#rebuildTree), pra nao deixar a arvore com a raiz sozinha
		// e nenhuma pista do porque nao ha nada embaixo dela.
		EMPTY_MESSAGE
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

}
