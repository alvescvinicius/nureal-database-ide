package com.nureal.ide.ui;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
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
import java.util.function.Supplier;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.ButtonGroup;
import javax.swing.DefaultListCellRenderer;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComboBox;
import javax.swing.JComponent;
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
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;

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
import com.nureal.ide.core.metadata.model.SchemaInfo;
import com.nureal.ide.core.queries.SavedQueryStore;
import com.nureal.ide.core.history.ExecutionHistoryStore;
import com.nureal.ide.core.safety.SqlRiskAnalyzer;
import com.nureal.ide.core.session.SessionStore;
import com.nureal.ide.core.sql.SqlStatementSplitter;
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
import com.nureal.ide.ui.components.NToast;

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

	static final int PAGE_SIZE = 200;
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
	private JComponent toolbarBar;
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
	private final ObjectExplorerController objectExplorer = new ObjectExplorerController(this);
	private final ResultsAreaController resultsController = new ResultsAreaController(this);
	private ConnectionsPanel connectionsPanel;
	private SavedQueriesPanel savedQueriesPanel;
	private HistoryPanel historyPanel;
	private NIconRail leftIconRail;
	private JPanel leftContent;
	private static final String CARD_CONNECTIONS = "connections";
	private static final String CARD_OBJECTS = "objects";
	private static final String CARD_QUERIES = "queries";
	private static final String CARD_HISTORY = "history";
	/** Esquema selecionado na conexao ativa — so escrever via {@link #setCurrentSchema}. */
	private SchemaInfo currentSchema;
	/**
	 * Modelo de mensagens transitorias — nunca fica visivel em layout nenhum
	 * (SPEC-0007: barra inferior eliminada). {@link com.nureal.ide.ui.components.NToast}
	 * escuta este JLabel (evento "text", ja disparado sozinho por
	 * {@code JLabel#setText}) e mostra a mensagem numa bolha flutuante que
	 * some sozinha — nenhum dos ~80 lugares que chamam
	 * {@code statusBar.setText(" mensagem")} precisou mudar.
	 */
	private final JLabel statusBar = new JLabel(" Pronto");
	/** Card fixo "Conexao Ativa" da sidebar (SPEC-0007) — unico lugar mostrando nome/host/engine/status. */
	private ConnectionStatusCard connectionCard;
	private JButton runButton;
	private JButton saveQueryButton;
	private JButton themeButton;
	/** Nome da conexao em processo de conectar agora (ver {@link #setConnectingState}), ou null. */
	private String connectingWorkspaceName;
	SwingWorker<List<QueryResult>, Void> runWorker;
	volatile Statement runningStatement;

	// Tema ESCURO agora e o padrao de arranque do app (ver App#main) — este
	// campo so espelha o L&F ja ativo quando a janela e construida.
	private boolean dark = true;

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
	boolean resultsVertical = false;
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
		// Liga o autocomplete ao cache de FKs (ver ObjectExplorerController#lookupForeignKeysForCompletion)
		// — "auxiliar de montagem de queries": ao completar o alvo de um JOIN,
		// o provider passa a sugerir primeiro as tabelas relacionadas por FK.
		completionProvider.setForeignKeyLookup(objectExplorer::lookupForeignKeysForCompletion);
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
		resultsArea = resultsController.buildResultsArea();
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

		// SPEC-0007 "Sidebar Workspace": barra inferior eliminada — todo
		// espaco que ela ocupava volta pro editor/grid. Status (statusBar)
		// virou notificacao flutuante (ver NToast); conexao/host/engine
		// (antigo connStatusLabel do rodape) mudou pro ConnectionStatusCard
		// fixo da sidebar (ver buildLeftSide), sem duplicar em lugar nenhum.
		setDisconnectedState();
		NToast.attach(this, statusBar);

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

		int rowHeight = addRunFormatExplainButtons(mainBar, gbc);
		addSaveHistoryButtons(mainBar, gbc, rowHeight);

		// --- O ESPAÇADOR INVISÍVEL ---
		// Ele joga tudo o que vier a partir daqui totalmente para a direita
		gbc.gridx = 6;
		gbc.weightx = 1.0;
		gbc.insets = new Insets(0, 0, 0, 0);
		mainBar.add(Box.createHorizontalGlue(), gbc);

		addRightIconGroup(mainBar, gbc);

		toolbarBar = mainBar;
		// initWorkspaces() ja rodou (ver buildEditorArea) quando chegamos aqui,
		// entao editorTabs ja tem a aba inicial — reflete o estado real dela no
		// botao Salvar desde o primeiro desenho, em vez de nascer sempre habilitado.
		updateSaveButtonState();
		return mainBar;
	}

	/** Executar/Formatar/menu de opcoes de formatacao/Explicar — grupo da esquerda da barra. Devolve a altura comum das 4. */
	private int addRunFormatExplainButtons(JPanel mainBar, GridBagConstraints gbc) {
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
		return rowHeight;
	}

	/** Salvar/Historico — segundo grupo da esquerda, mesma altura calculada pelo grupo anterior. */
	private void addSaveHistoryButtons(JPanel mainBar, GridBagConstraints gbc, int rowHeight) {
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
	}

	/** Icones discretos da direita (sidebar/resultados/layout/tema/chat) — ultimo grupo da barra. */
	private void addRightIconGroup(JPanel mainBar, GridBagConstraints gbc) {
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

		// Os 5 icones da direita vao num UNICO painel FlowLayout, em vez de 5
		// celulas separadas do GridBagLayout de mainBar — GridBagLayout pode
		// comprimir celulas de peso zero abaixo do tamanho preferido quando a
		// janela fica estreita demais pra caber tudo (nenhum componente aqui
		// tem folga entre minimo/preferido pra doar, entao a compressao caia
		// nos icones-so, deixando-os "esmagados"/desalinhados ao redimensionar
		// a janela — bug relatado pelo usuario). FlowLayout nunca redimensiona
		// os filhos: ou desenha todos no tamanho preferido, ou faz overflow —
		// nunca os espreme.
		JPanel rightIcons = new JPanel(new FlowLayout(FlowLayout.LEFT, Spacing.XS, 0));
		rightIcons.setOpaque(false);
		rightIcons.add(toggleSidebar);
		rightIcons.add(toggleResults);
		rightIcons.add(layoutButton);
		rightIcons.add(themeButton);
		rightIcons.add(chatButton);

		gbc.gridx = 7;
		// CRITICO: weightx de volta pra 0 aqui — ficou em 1.0 desde o glue
		// (gridx=6) e, com fill=NONE + anchor=BASELINE (centraliza
		// horizontalmente dentro da propria celula), metade do espaco extra
		// da janela ia pra ESTA celula em vez de toda pro glue: os icones
		// ficavam centralizados no meio da barra, longe da quina direita
		// (bug relatado pelo usuario). O divisor removido antes fazia esse
		// mesmo reset — perdido junto quando ele saiu.
		gbc.weightx = 0.0;
		// Respiro maior (LG) antes do grupo — sozinho, sem linha divisoria, ja
		// marca visualmente onde ele comeca.
		gbc.insets = new Insets(0, Spacing.LG, 0, 0);
		mainBar.add(rightIcons, gbc);
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
	void toggleResultsFocusMode() {
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
		bindGlobalAction(rp, "control R", "refresh-objects", () -> objectExplorer.refreshObjectTree(true));
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
	void toggleResultsOrientation() {
		resultsVertical = !resultsVertical;
		centerSplit.setOrientation(resultsVertical ? JSplitPane.HORIZONTAL_SPLIT : JSplitPane.VERTICAL_SPLIT);
		centerSplit.setResizeWeight(0.62);
		resultsLoc = -1;
		centerSplit.setDividerLocation(0.62);
		centerSplit.revalidate();
		centerSplit.repaint();
		resultsController.refreshOrientationIcon();
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
	int scaledPx(int basePx) {
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
	int resultRowHeightBasePx() {
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
		objectExplorer.applyDensityBorder(outer);
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
		revalidate();
		repaint();
	}

	/**
	 * Reaplica os tamanhos derivados do zoom/modo compacto a componentes ja
	 * construidos (linhas da arvore, cartoes de conexao, grade de resultados).
	 */
	private void refreshDynamicSizing() {
		objectExplorer.setRowHeight(scaledPx(ConnectionsPanel.DEFAULT_ROW_HEIGHT));
		if (connectionsPanel != null) {
			connectionsPanel.setRowHeight(scaledPx(ConnectionsPanel.DEFAULT_ROW_HEIGHT));
		}
		// Reconstroi as abas de resultado (tabela, gutter e cabecalho usam
		// tamanhos fixos definidos na hora da criacao do JTable).
		resultsController.reshowIfVisible();
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

	// ---------- Estado da conexao (ConnectionStatusCard fixo da sidebar, SPEC-0007) ----------

	private void setDisconnectedState() {
		connectionCard.showDisconnected();
		connectingWorkspaceName = null;
		updateWorkspaceContextBar();
	}

	private void setConnectingState(String name) {
		connectionCard.showConnecting(name);
		connectingWorkspaceName = name;
		updateWorkspaceContextBar();
	}

	private void setConnectedState(String label) {
		connectionCard.showConnected(label, activeWorkspaceHostLabel(), activeWorkspaceEngineLabel());
		connectingWorkspaceName = null;
		updateWorkspaceContextBar();
	}

	/** {@code usuario@host} da conexao ativa (ver exemplo da SPEC-0007), ou {@code null} sem profile. */
	private String activeWorkspaceHostLabel() {
		if (activeWorkspace == null || activeWorkspace.profile == null) {
			return null;
		}
		return activeWorkspace.profile.user() + "@" + activeWorkspace.profile.host();
	}

	/** {@code "MySQL 8.0"} (produto + versao major.minor) da conexao ativa, ou {@code null} sem metadados disponiveis. */
	private String activeWorkspaceEngineLabel() {
		String product = databaseProductNameForAi();
		if (product == null) {
			return null;
		}
		String version = databaseVersionForAi();
		if (version == null) {
			return product;
		}
		String[] parts = version.split("\\.");
		String shortVersion = parts.length >= 2 ? parts[0] + "." + parts[1] : version;
		return product + " " + shortVersion;
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
	void updateWorkspaceContextBar() {
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
		leftContent.add(objectExplorer.buildObjectBrowser(), CARD_OBJECTS);
		leftContent.add(savedQueriesPanel, CARD_QUERIES);
		leftContent.add(historyPanel, CARD_HISTORY);

		// Favoritos/Configuracoes (SPEC-0007): itens PLACEHOLDER — a
		// funcionalidade ainda nao existe no app (sem "favoritar" query
		// salva, sem tela de configuracoes unificada), entao aparecem
		// desabilitados em vez de fingir que funcionam.
		leftIconRail = new NIconRail()
				.addItem(CARD_CONNECTIONS, IconType.CONNECTION, "Conexoes")
				.addItem(CARD_OBJECTS, IconType.DATABASE, "Objetos")
				.addItem(CARD_QUERIES, IconType.SAVE, "Consultas")
				.addItem(CARD_HISTORY, IconType.HISTORY, "Historico")
				.onSelect(this::showLeftCard)
				.addDisabledItem(IconType.FAVORITE, "Favoritos", "Favoritos — em breve")
				.addDisabledItem(IconType.SETTINGS, "Config.", "Configuracoes — em breve");

		// Logo compacto no topo da coluna de conteudo (SPEC-0007: Sidebar =
		// Logo + Navigation Rail + Active Connection Card + Workspace) — o
		// texto "Nureal" que antes vivia no canto direito da barra inferior
		// (removida) migrou pra ca, nao foi descartado.
		JLabel logoIcon = new JLabel(new javax.swing.ImageIcon(Icons.brandImage(18)));
		JLabel logoText = new JLabel("Nureal");
		logoText.setFont(logoText.getFont().deriveFont(Font.BOLD, 13f));
		logoText.setForeground(ACCENT);
		JPanel logoRow = new JPanel(new FlowLayout(FlowLayout.LEFT, Spacing.SM, 0));
		logoRow.setOpaque(false);
		logoRow.add(logoIcon);
		logoRow.add(logoText);

		// Active Connection Card: SEMPRE visivel, qualquer que seja a secao
		// selecionada no rail (unico lugar do app mostrando conexao/host/
		// engine/status — ver ConnectionStatusCard).
		connectionCard = new ConnectionStatusCard();
		JPanel cardRow = new JPanel(new BorderLayout());
		cardRow.setOpaque(false);
		cardRow.setBorder(BorderFactory.createEmptyBorder(Spacing.SM, 0, Spacing.SM, 0));
		cardRow.add(connectionCard, BorderLayout.CENTER);

		JPanel topStack = new JPanel(new BorderLayout());
		topStack.setOpaque(false);
		topStack.add(logoRow, BorderLayout.NORTH);
		topStack.add(cardRow, BorderLayout.SOUTH);

		JPanel contentColumn = new JPanel(new BorderLayout());
		contentColumn.setBorder(BorderFactory.createEmptyBorder(Spacing.SM, Spacing.SM, 0, 0));
		contentColumn.add(topStack, BorderLayout.NORTH);
		contentColumn.add(leftContent, BorderLayout.CENTER);

		JPanel container = new JPanel(new BorderLayout());
		container.add(leftIconRail, BorderLayout.WEST);
		container.add(contentColumn, BorderLayout.CENTER);
		container.setPreferredSize(new Dimension(280, 100));
		return container;
	}

	/** Troca qual painel da lateral esta visivel (ver {@link #leftIconRail}). */
	private void showLeftCard(String cardId) {
		((CardLayout) leftContent.getLayout()).show(leftContent, cardId);
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
				resultsController.showResultsForActiveEditor();
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

	boolean titleExists(String title) {
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
	boolean addQueryTab(String title, String sql) {
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
				formatState.editorFontFamily(), () -> currentSchema, objectExplorer::openEditorObject, objectExplorer::navigateBack,
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
			resultsController.forgetTab(sep);
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
				resultsController.forgetTab(sep);
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
						resultsController.rememberTab(sep, saved);
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
		resultsController.showResultsForActiveEditor();
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
				List<QueryResult> results = resultsController.resultsFor(sep);
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
			objectExplorer.populateTree(w.schema);
		} else if (w.schemaList != null) {
			completionProvider.refresh(null);
			objectExplorer.buildSchemaPicker(w.schemaList);
		} else {
			setCurrentSchema(null);
			completionProvider.refresh(null);
			String label = (w.profile == null) ? "Sem conexao"
					: (w.mgr.isConnected() ? "Selecione um esquema" : "Desconectado");
			objectExplorer.showDisconnected(label);
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
	ConnectionManager connectionManager() {
		return (activeWorkspace != null) ? activeWorkspace.mgr : bootstrapConnectionManager;
	}

	// ---------- Acessores de pacote para ObjectExplorerController ----------
	// Visibilidade de pacote (nao public): so o proprio colaborador desta
	// janela, no mesmo pacote com.nureal.ide.ui, deve enxergar estes campos —
	// ver extracao do explorador de objetos (ObjectExplorerController), que
	// precisa da conexao/esquema/status ativos sem duplicar nenhum deles.
	Conexao activeWorkspace() {
		return activeWorkspace;
	}

	SchemaInfo currentSchema() {
		return currentSchema;
	}

	JLabel statusBar() {
		return statusBar;
	}

	DatabaseDialect dialect() {
		return dialect;
	}

	MetadataService metadataService() {
		return metadataService;
	}

	MetadataCache metadataCache() {
		return metadataCache;
	}

	TableMetadataCache tableMetadataCache() {
		return tableMetadataCache;
	}

	SqlCompletionProvider completionProvider() {
		return completionProvider;
	}

	/**
	 * UNICO ponto de ESCRITA do "esquema atual" ({@link #currentSchema}):
	 * mantem ele e {@code activeWorkspace.schema} sempre em sincronia num so
	 * lugar, em vez de cada trecho do codigo ter que lembrar de atualizar os
	 * dois separadamente (o jeito antigo — risco real de esquecer um dos dois
	 * e eles divergirem silenciosamente). {@code schema == null} desmarca
	 * (nenhum esquema selecionado no momento, ex.: workspace sem conexao).
	 */
	void setCurrentSchema(SchemaInfo schema) {
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

	SqlEditorPane currentEditor() {
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

	/** Delega para {@link Typography#sectionHeader} — ponto UNICO desta receita, compartilhado com os paineis laterais. */
	JLabel sectionHeader(String text) {
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
		// ConnectionStatusCard nao e um componente PADRAO do FlatLaf, entao
		// FlatLaf.updateUI() nao o alcanca — sem isto, o dot/status ficavam
		// com a cor GridTheme do tema anterior ate a proxima mudanca de
		// estado de conexao.
		connectionCard.refreshTheme();
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
		resultsController.showResultsForActiveEditor();
		resultsController.styleExecutingOverlay();
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
			resultsController.closeOpenCursors();
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

	/**
	 * Abre um esquema escolhido na lista: define como banco padrao e carrega
	 * objetos.
	 */
	void openSchema(String schemaName) {
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
	void openSchema(String schemaName, Runnable onOpened) {
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
					objectExplorer.populateTree(schema);
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
		objectExplorer.runQuery(ws, explainSql, rows -> {
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
					objectExplorer.populateTree(schema);
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
		final List<String> statements = statementsToRun(editor);
		if (statements == null) {
			return;
		}
		prepareForExecution(editor, statements);

		SwingWorker<List<QueryResult>, Void> worker = new SwingWorker<>() {
			@Override
			protected List<QueryResult> doInBackground() {
				return executeStatements(statements, this::isCancelled);
			}

			@Override
			protected void done() {
				resultsController.showExecuting(false);
				runningStatement = null;
				runWorker = null;
				runButton.setEnabled(true);
				try {
					handleStatementResults(editor, statements, get());
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

	/** Split + validacoes pre-execucao (data sem aspas, confirmacao de instrucoes arriscadas). {@code null} = nao deve executar. */
	private List<String> statementsToRun(SqlEditorPane editor) {
		List<String> statements = SqlStatementSplitter.split(editor.currentSql());
		if (statements.isEmpty()) {
			return null;
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
			return null;
		}
		if (!confirmRiskyStatements(statements)) {
			statusBar.setText(" Execucao cancelada.");
			return null;
		}
		return statements;
	}

	/** Estado da UI ANTES de disparar o SwingWorker: fecha cursores antigos, reabre resultados, desabilita "Executar". */
	private void prepareForExecution(SqlEditorPane editor, List<String> statements) {
		if (activeWorkspace != null) {
			// Atividade de verdade: reseta a contagem de ociosidade do
			// keep-alive (ver pingKeepAlive) — acabou de rodar algo, nao
			// precisa de um SELECT 1 de teste tao cedo.
			activeWorkspace.setLastActivityMillis(System.currentTimeMillis());
		}
		resultsController.closeOpenCursors();
		if (resultsArea != null && !resultsArea.isVisible()) {
			toggleResults(); // reabre os resultados para mostrar o carregamento
		}
		runButton.setEnabled(false);
		resultsController.showExecuting(true);
		boolean usingSelection = editor.hasSelection();
		statusBar.setText(" Executando " + statements.size() + " instrucao(oes)"
				+ (usingSelection ? "  —  ATENCAO: rodando apenas a SELECAO" : "") + "...");
	}

	/** Roda cada instrucao em sequencia (thread do SwingWorker) — para na primeira que der erro. */
	private List<QueryResult> executeStatements(List<String> statements, Supplier<Boolean> isCancelled) {
		List<QueryResult> results = new ArrayList<>();
		Connection conn = connectionManager().getConnection();
		for (int i = 0; i < statements.size(); i++) {
			if (isCancelled.get()) {
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
				results.add(buildStatementResult(st, sql, n, hasResultSet, execMs));
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

	/** Monta o {@link QueryResult} de UMA instrucao ja executada com sucesso (grade de linhas OU contagem afetada). */
	private QueryResult buildStatementResult(Statement st, String sql, int n, boolean hasResultSet, long execMs)
			throws SQLException {
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
			return QueryResult.grid("Resultado " + n, sql, model, execMs, fetchMs, cursor);
		}
		int updated = st.getUpdateCount();
		st.close();
		return QueryResult.message("Comando " + n, sql, updated + " linha(s) afetada(s)", false, execMs);
	}

	/** Trata o resultado final (thread da UI, {@code SwingWorker#done}): guarda na aba, redesenha se ainda ativa, atualiza historico/arvore. */
	private void handleStatementResults(SqlEditorPane editor, List<String> statements, List<QueryResult> results) {
		// Resultado pertence a ABA que rodou (editor), nao a "aba
		// selecionada agora" — o usuario pode ter trocado de aba enquanto a
		// query rodava. So redesenha o painel de RESULTADOS se aquela aba
		// ainda for a selecionada; senao, so guarda (ver
		// showResultsForActiveEditor, chamado quando o usuario voltar pra ela).
		resultsController.rememberTab(editor, results);
		if (editor == currentEditor()) {
			resultsController.showResults(results);
		}
		if (ranStructuralDdl(statements, results)) {
			objectExplorer.refreshObjectTree(false);
		}
		logExecutionHistory(editor, results);
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


	/** Fecha cursores abertos e as conexoes JDBC de TODOS os workspaces (ao fechar a janela). */
	private void closeAllConnections() {
		resultsController.closeOpenCursors();
		for (Conexao w : workspaces.values()) {
			w.mgr.close();
		}
	}

	File chooseSaveFile(String defaultName) {
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

	void doExport(List<ExcelExporter.TableSheet> sheets, File file) {
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
	static String sqlTooltip(String sql) {
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

	/**
	 * Mostra o erro num JTextArea (nao editavel, mas SELECIONAVEL/copiavel
	 * com Ctrl+C — ao contrario da String simples que
	 * {@code JOptionPane.showMessageDialog} renderia como um JLabel comum),
	 * mesmo padrao ja usado em {@link #confirmRiskyStatements}: mensagem de
	 * erro e exatamente o tipo de texto que da vontade de copiar (pesquisar,
	 * colar num chamado de suporte etc.).
	 */
	void showError(String title, Exception ex) {
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
