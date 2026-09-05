package com.nureal.ide.ui;
import com.nureal.ide.compartilhado.designsystem.IconType;
import com.nureal.ide.compartilhado.designsystem.Icons;
import com.nureal.ide.compartilhado.designsystem.Buttons;
import com.nureal.ide.compartilhado.designsystem.Typography;
import com.nureal.ide.compartilhado.designsystem.GridTheme;
import com.nureal.ide.compartilhado.designsystem.Spacing;

import com.nureal.ide.app.ComposicaoRaiz;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.sql.Connection;
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
import java.util.concurrent.CancellationException;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
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
import javax.swing.JPopupMenu;
import javax.swing.JRootPane;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.nureal.ide.modulos.autocomplete.infraestrutura.SqlCompletionProviderRSyntax;
import com.nureal.ide.modulos.conexoes.dominio.contratos.ConexaoAtivaPort;
import com.nureal.ide.modulos.conexoes.dominio.contratos.ConnectionRepository;
import com.nureal.ide.modulos.conexoes.infraestrutura.ConnectionManager;
import com.nureal.ide.modulos.conexoes.dominio.entidades.ConnectionProfile;
import com.nureal.ide.modulos.dialeto.dominio.contratos.DatabaseDialect;
import com.nureal.ide.modulos.backupexportacao.infraestrutura.ExcelExporter;
import com.nureal.ide.core.format.FormatPreferences;
import com.nureal.ide.core.format.SqlFormatter;
import com.nureal.ide.core.log.AppLogger;
import com.nureal.ide.modulos.metadados.infraestrutura.MetadataCache;
import com.nureal.ide.modulos.metadados.dominio.contratos.MetadataRepository;
import com.nureal.ide.modulos.metadados.dominio.entidades.SchemaInfo;
import com.nureal.ide.modulos.historico.infraestrutura.SavedQueryStore;
import com.nureal.ide.modulos.historico.infraestrutura.ExecutionHistoryStore;
import com.nureal.ide.core.safety.SqlRiskAnalyzer;
import com.nureal.ide.modulos.historico.infraestrutura.SessionStore;
import com.nureal.ide.core.sql.SqlStatementSplitter;
import com.nureal.ide.core.sql.UnquotedDateGuard;
import com.nureal.ide.core.ui.UiPreferences;
import com.nureal.ide.modulos.atualizacao.infraestrutura.AppVersion;
import com.nureal.ide.modulos.atualizacao.dominio.entidades.GithubRelease;
import com.nureal.ide.modulos.atualizacao.dominio.contratos.RepositorioDeReleasesPort;
import com.nureal.ide.modulos.atualizacao.infraestrutura.UpdateChecker;
import com.nureal.ide.modulos.atualizacao.infraestrutura.UpdatePreferences;
import com.nureal.ide.modulos.iachat.dominio.contratos.Agent;
import com.nureal.ide.modulos.iachat.aplicacao.DefaultAgent;
import com.nureal.ide.modulos.iachat.infraestrutura.AiCredentialsStore;
import com.nureal.ide.modulos.iachat.infraestrutura.AiPreferences;
import com.nureal.ide.modulos.iachat.infraestrutura.LLMProviderFactory;
import com.nureal.ide.modulos.iachat.dominio.contratos.ContextProvider;
import com.nureal.ide.modulos.iachat.aplicacao.DefaultContextProvider;
import com.nureal.ide.modulos.iachat.dominio.contratos.IdeStateAccessor;
import com.nureal.ide.modulos.iachat.infraestrutura.ChatHistoryStore;
import com.nureal.ide.modulos.iachat.dominio.contratos.LLMProvider;
import com.nureal.ide.modulos.iachat.infraestrutura.tool.DescribeTableTool;
import com.nureal.ide.modulos.iachat.infraestrutura.tool.ExecuteSqlTool;
import com.nureal.ide.modulos.iachat.infraestrutura.tool.ListTablesTool;
import com.nureal.ide.modulos.iachat.aplicacao.ToolExecutor;
import com.nureal.ide.modulos.iachat.apresentacao.AiSettingsDialog;
import com.nureal.ide.modulos.iachat.apresentacao.ChatActions;
import com.nureal.ide.modulos.iachat.apresentacao.ChatWindow;
import com.nureal.ide.modulos.iachat.apresentacao.IdeContextAccessor;
import com.nureal.ide.compartilhado.designsystem.NButton;
import com.nureal.ide.compartilhado.designsystem.NIconRail;
import com.nureal.ide.compartilhado.designsystem.NToast;

/**
 * Janela principal no estilo de uma IDE moderna (FlatLaf): top bar com acao de
 * executar e tema, conexoes e objetos a esquerda, editor SQL em abas no centro
 * e resultados em abas abaixo (uma aba por statement), com exportacao para
 * Excel.
 */
public class MainWindow extends JFrame {

	private static final long serialVersionUID = 1L;
	// Publica (nao so pacote-visivel): reaproveitada por ResultStatusBar para o
	// icone do botao "Exportar" e por com.nureal.ide.compartilhado.designsystem.NButton
	// (Nureal Design System) — evita duplicar o mesmo valor de cor em outra
	// classe/pacote.
	// Verde institucional da marca Nureal (ver logo) — era 0x059669 (um verde-
	// esmeralda generico, sem relacao com a marca); atualizado apos revisao de
	// identidade visual (ver DESIGN_SYSTEM.md, secao 2). Unico ponto de
	// verdade: qualquer lugar que precisar do verde da marca reusa ACCENT, nunca
	// um literal proprio (ja auditado — ver Buttons/ConnectionsPanel/
	// ObjectTreeCellRenderer/ResultStatusBar). Delega a GridTheme.BRAND_GREEN
	// (movida para compartilhado.designsystem na migracao NDS): um componente
	// de design system nunca pode depender de MainWindow, entao a constante em
	// si teve que migrar pra la; ACCENT continua aqui, com o MESMO valor, para
	// nao quebrar os consumidores existentes.
	public static final Color ACCENT = GridTheme.BRAND_GREEN;

	private static final int MAX_TABS = 15;
	/** Largura padrao (pixels) do dock do Chat de IA na 1a abertura — ver {@link #setChatDockVisible}. */
	private static final int CHAT_DOCK_DEFAULT_WIDTH = 380;

	private static final String SCRATCH = "(sem conexao)";

	private final DatabaseDialect dialect;
	/**
	 * Usado SO para criar a conexao SCRATCH em {@code initWorkspaces()}, antes
	 * de {@code activeWorkspace} existir — depois disso, {@code activeWorkspace}
	 * nunca mais fica null pelo resto da vida da janela, entao {@link #connectionManager()}
	 * sempre devolve {@code activeWorkspace.mgr} a partir dai. Nao usar
	 * diretamente fora de {@code initWorkspaces()}.
	 */
	private final ConexaoAtivaPort bootstrapConnectionManager;
	private final Map<String, Conexao> workspaces = new LinkedHashMap<>();
	private Conexao activeWorkspace;
	private Map<String, SessionStore.Session> savedSessions = new LinkedHashMap<>();
	private final MetadataRepository metadataService;
	private final MetadataCache metadataCache;
	// Cache de metadados de tabela (colunas/PK/indices/FKs) para a grade de
	// resultados — indicador de FK no cabecalho e popup/menu de metadados de
	// coluna; compartilhado por TODAS as grades da sessao (ver ResultGrid),
	// evita repetir loadTableDetails() para a mesma tabela a cada resultado.
	private final TableMetadataCache tableMetadataCache;
	private final SqlCompletionProviderRSyntax completionProvider;
	private final ConnectionRepository connectionStore;
	private final SessionStore sessionStore;
	private final SavedQueryStore savedQueryStore;
	private final ExecutionHistoryStore historyStore;
	private final RepositorioDeReleasesPort releasesRepository;
	private Timer autosaveTimer;

	/**
	 * "Ponteiro" para os terminais SQL da conexao ATIVA no momento — nao mais
	 * uma unica instancia fixa pra IDE inteira: cada {@code Conexao} tem seu
	 * proprio {@code JTabbedPane} vivo ({@code Conexao#ownEditorTabs}), e
	 * este campo (e {@link #plusTab}) e repontado pra ele sempre que o
	 * workspace ativo muda (ver {@link #activateWorkspace}) — assim o resto
	 * do arquivo (~100 usos) continua falando com "a aba de terminais atual"
	 * sem saber que existe mais de uma conexao por tras.
	 */
	private JTabbedPane editorTabs;
	private Component plusTab;
	/** Tira de abas de CONEXAO (uma por conexao aberta, mais "Sem conexao") — ver {@link #buildEditorArea}. */
	private JTabbedPane connectionTabs;
	/** Aba "+" (pequena, nao fechavel) ao final de {@link #connectionTabs} — abre {@link #promptConnectionSelection}, mesmo padrao da aba "+" do editor (ver {@link #plusTab}). */
	private Component connectionPlusTab;
	/** Evita reentrancia entre o ChangeListener de {@link #connectionTabs} e {@link #ensureConnectionTab} selecionando a aba programaticamente. */
	private boolean switchingConnectionTab;
	private ChatWindow chatWindow;
	/** Mesma instancia usada pelo {@link #chatWindow} — reaproveitada ao reconstruir o Agent (troca de modelo/configuracao), em vez de uma nova a cada vez (ver {@link #onChatModelChanged}/{@link #openAiSettings}). */
	private ChatHistoryStore chatHistoryStore;
	/** Ultima {@link SqlEditorPane} com foco de verdade (ver o ChangeListener de {@link #editorTabs} em {@link #buildEditorArea}) — usada pelos presets do Chat quando nenhuma aba de SQL esta com foco no momento (ex.: o Chat acabou de ser aberto). */
	private SqlEditorPane lastActiveEditor;
	private boolean addingTab;
	/**
	 * Rail (esquerda/direita, ver {@link #sidebarOnRight}) + editor/resultados
	 * — {@link BorderLayout} simples, NAO MAIS um {@link JSplitPane}: o rail
	 * (ver {@link #buildLeftSide}) e so 64px de icones, largura FIXA — pedido
	 * explicito do usuario ("deveria ter um tamanho apenas para comportar os
	 * icones e nao esse espaco todo podendo redimensionar"). Regiao
	 * WEST/EAST do BorderLayout usa SEMPRE a largura preferida do filho
	 * (nunca encolhe/estica, nem oferece divisoria pra arrastar) — mesma
	 * garantia estrutural ja usada nesta classe pro botao "Executar" nunca
	 * sumir (ver {@code buildToolbar}), agora resolvendo de quebra o bug do
	 * vao cinza vazio que a versao anterior (JSplitPane com
	 * setDividerLocation manual, sujeito a timing do Swing) tinha.
	 */
	private JPanel mainLayout;
	private JSplitPane centerSplit;
	/**
	 * Split MAIS externo da janela: {@link #mainLayout} (sidebar+editor+
	 * resultados) a esquerda, {@link #chatDock} a direita — pedido explicito
	 * do usuario na revisao de UX ("chat abrindo em aba do editor ao inves
	 * de abrir uma janela na direita"). Antes o Chat era uma aba a mais
	 * dentro de {@link #editorTabs} (Fase 2 do AI-CHAT-MASTER-PLAN.md);
	 * agora e um painel proprio, sempre no mesmo lugar, que nao compete por
	 * espaco com as abas de SQL nem exige fechar o editor pra ver o chat e
	 * vice-versa.
	 */
	private JSplitPane chatSplit;
	/** Painel fixo do lado direito que hospeda {@link #chatWindow} quando aberto — vazio e colapsado (largura 0) quando nao ha chat aberto. */
	private JComponent chatDock;
	/** Ultima largura (pixels, a partir da borda esquerda) do dock do chat — restaurada ao reabrir, mesmo principio de {@link #resultsLoc}. */
	private int chatDockLoc = -1;
	private JComponent leftSide;
	private JComponent resultsArea;
	private JComponent editorAreaPanel;
	private JComponent toolbarBar;
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
	/**
	 * Modelo "navegacao em popup ancorado" (pedido do usuario com
	 * referencia visual: barra superior dividida em zonas, sem sidebar
	 * permanente pra cada secao, cada uma um botao que abre um painel
	 * flutuante logo abaixo dele, sem empurrar editor/resultados). Conexoes
	 * foi o primeiro (bug de edicao de conexao forcou a solucao — ver
	 * {@link AnchoredPopup}), "Historico" foi o piloto do padrao pra
	 * NAVEGACAO em si, validado e seguido por "SQLs" e "Salvas". "Objetos"
	 * continua fixo na sidebar de proposito (consulta CONSTANTE enquanto
	 * se escreve SQL — nome de coluna/tabela —, ao contrario das outras 3,
	 * que sao navegacao ocasional; fechar a arvore toda vez que o foco
	 * volta pro editor atrapalharia esse uso — decisao explicita do
	 * usuario ao aprovar o piloto). Os 4 popups sao {@link AnchoredPopup},
	 * um so lugar decidindo undecorated/foco/debounce/Esc pros 4 — antes
	 * de existir esta classe, cada popup tinha sua PROPRIA copia de ~40
	 * linhas quase identicas (so o conteudo/tamanho mudavam).
	 */
	private final AnchoredPopup connectionsPopup = new AnchoredPopup();
	private final AnchoredPopup historyPopup = new AnchoredPopup();
	private final AnchoredPopup sqlEditorsPopup = new AnchoredPopup();
	private final AnchoredPopup savedQueriesPopup = new AnchoredPopup();
	private final AnchoredPopup objectsPopup = new AnchoredPopup();
	/** Arvore de Objetos — construida UMA VEZ (ver {@link #buildLeftSide}), reaberta como popup a cada clique no rail (ver {@link #showObjectsPopup}). */
	private JComponent objectsBrowserPanel;
	/** Rail vertical de icones (Objetos/SQLs/Favoritos/Historico/IA/Backup/Usuarios/Mais) — ver {@link #buildLeftSide}. */
	private NIconRail iconRail;
	private SavedQueriesPanel savedQueriesPanel;
	private HistoryPanel historyPanel;
	/** Rotulo "SQL Editors (N)" — mantido em dia por {@link #updateWorkspaceContextBar} (mesmo hook que ja roda a cada aba aberta/fechada). */
	private JLabel sqlEditorsCountLabel;
	/**
	 * Lista clicavel das abas de SQL abertas (aba "SQL" da sidebar) — guarda o
	 * COMPONENTE de cada aba (nao o indice, que desloca ao fechar uma aba no
	 * meio); titulo/dot de status sao resolvidos ao vivo em {@link #editorTabs}
	 * (ver {@link SqlEditorsListRenderer}), nunca duplicados aqui. Mantida em
	 * dia por {@link #refreshSqlEditorsCount} (mesmo hook de toda troca de aba).
	 */
	private final DefaultListModel<Component> sqlEditorsListModel = new DefaultListModel<>();
	private JList<Component> sqlEditorsList;
	/** Conteudo do popup "SQLs" (ver {@link #showSqlEditorsPopup}) — atalho "+ nova aba" + lista das abas de SQL abertas. */
	private JPanel sqlEditorsPanel;
	/** Esquema selecionado na conexao ativa — so escrever via {@link #setCurrentSchema}. */
	private SchemaInfo currentSchema;
	/**
	 * Modelo de mensagens transitorias — nunca fica visivel em layout nenhum
	 * (SPEC-0007: barra inferior eliminada). {@link com.nureal.ide.compartilhado.designsystem.NToast}
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
	/**
	 * Execucao em andamento de UM terminal: o {@link SwingWorker} e o
	 * {@link Statement} JDBC atualmente rodando (para {@code Statement#cancel}
	 * — ver {@link #cancelExecution}). Nao sao mais campos UNICOS
	 * ({@code runWorker}/{@code runningStatement} da IDE inteira): cada
	 * terminal executa/cancela de forma independente (ver
	 * {@link #terminalExecutions}), senao executar numa aba desabilitaria
	 * "Executar" e a barra de progresso de TODAS as outras, mesmo de
	 * conexoes diferentes — o oposto do que foi pedido ("um terminal nao
	 * impede o outro").
	 */
	private static final class TerminalExecution {
		final SwingWorker<List<QueryResult>, Void> worker;
		volatile Statement statement;
		/** Marcado por {@link #executeWithReconnect} quando esta execucao precisou reconectar no meio — ver seu javadoc de done() em {@link #runStatements}. */
		volatile boolean reconnected;

		TerminalExecution(SwingWorker<List<QueryResult>, Void> worker) {
			this.worker = worker;
		}
	}

	/** Execucoes em andamento, uma entrada por terminal que esta rodando algo agora — ver {@link TerminalExecution}. */
	private final Map<SqlEditorPane, TerminalExecution> terminalExecutions = new HashMap<>();

	/**
	 * Conexao JDBC DEDICADA de cada terminal (emprestada do pool da conexao
	 * ativa, ver {@link ConnectionManager#borrowConnection()}), reaproveitada
	 * entre execucoes do MESMO terminal enquanto ele existir. E o que permite
	 * dois terminais executarem SQL ao mesmo tempo sem um esperar o outro —
	 * antes, todo mundo compartilhava a UNICA conexao de
	 * {@code connectionManager().getConnection()}, que so aguenta uma
	 * instrucao por vez. Entrada removida (e a conexao fechada, devolvida ao
	 * pool) quando o terminal fecha ou e descartado (ver
	 * {@link #closeTerminalConnection}).
	 */
	private final Map<SqlEditorPane, Connection> terminalConnections = new HashMap<>();

	// Tema ESCURO agora e o padrao de arranque do app (ver App#main) — este
	// campo so espelha o L&F ja ativo quando a janela e construida.
	private boolean dark = true;

	// ---------- Layout flexivel / zoom / modo compacto ----------

	private static final double[] ZOOM_LEVELS = { 0.75, 0.90, 1.00, 1.10, 1.25, 1.50 };

	/**
	 * Altura de linha (em px, ANTES do zoom/modo compacto — ver
	 * {@link #resultRowHeightBasePx()}) de TODOS os componentes de linha
	 * unica (icone + texto) da sessao: grade de resultados, arvore de
	 * Objetos e lista de Conexoes — pedido explicito do usuario: "as vezes
	 * fica muito apertado... uma opcao permitindo alguns tamanhos", estendido
	 * depois para os 3 componentes (antes so a grade de resultados usava
	 * este controle; arvore/conexoes ficavam presas a uma altura fixa,
	 * inconsistente com a mesma preferencia). Independente do zoom da
	 * interface (que escala tudo) e do modo compacto (que reduz tudo): este
	 * controle mexe SO no espaco entre linhas. {@code 22} (indice 1,
	 * "Padrao") e o valor que a grade sempre usou antes deste controle
	 * existir. Historico/Consultas Salvas NAO usam esta escala — sao cards de
	 * duas linhas (48/52px), estrutura diferente o bastante pra quebrar o
	 * layout se forem espremidos nos mesmos 18-34px.
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

	// ---------- Atualizacao automatica (ver com.nureal.ide.modulos.atualizacao) ----------

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

	private final FormatPreferences formatPrefsStore;
	private FormatPreferences.State formatState = FormatPreferences.State.defaults();

	public MainWindow(ComposicaoRaiz raiz) {
		super("Nureal Database IDE");
		this.dialect = raiz.dialect();
		this.bootstrapConnectionManager = raiz.bootstrapConnectionManager();
		this.metadataService = raiz.metadataService();
		this.metadataCache = raiz.metadataCache();
		this.tableMetadataCache = raiz.tableMetadataCache();
		this.completionProvider = raiz.completionProvider();
		this.connectionStore = raiz.connectionStore();
		this.sessionStore = raiz.sessionStore();
		this.savedQueryStore = raiz.savedQueryStore();
		this.historyStore = raiz.historyStore();
		this.releasesRepository = raiz.releasesRepository();
		this.formatPrefsStore = raiz.formatPrefsStore();
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setIconImages(Icons.brandImages());
		setSize(1280, 800);
		setLocationRelativeTo(null);
		// Maximizada por padrao (pedido explicito do usuario) — setSize acima
		// continua servindo de "tamanho de restauracao" pra quando o usuario
		// desmaximizar na mao, so nao e mais o tamanho INICIAL visivel.
		setExtendedState(JFrame.MAXIMIZED_BOTH);
		loadUiPrefs();
		loadFormatPrefs();
		// Liga o autocomplete ao cache de FKs (ver ObjectExplorerController#lookupForeignKeysForCompletion)
		// — "auxiliar de montagem de queries": ao completar o alvo de um JOIN,
		// o provider passa a sugerir primeiro as tabelas relacionadas por FK.
		completionProvider.setForeignKeyLookup(objectExplorer::lookupForeignKeysForCompletion);
		buildUi();
		// Nenhuma conexao aberta ainda logo apos o arranque (so a estrutura
		// interna "Sem conexao" existe, sem aba visivel — ver #initWorkspaces)
		// — pedido explicito do usuario: mostra o seletor de conexoes ja de
		// cara, em vez de deixar o usuario descobrir sozinho que precisa
		// clicar em algum lugar pra conectar. invokeLater: espera a janela
		// terminar de montar/aparecer antes de abrir um dialogo modal por
		// cima dela.
		SwingUtilities.invokeLater(this::promptConnectionSelection);
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
					ConexaoAtivaPort mgr = w.mgr();
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
				return releasesRepository.fetchLatestRelease();
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

		// buildLeftSide() PRIMEIRO (constroi connectionsPanel) — a barra de
		// conexao (buildConnectionBar, chamada de dentro de buildToolbar()
		// mais abaixo) depende dele existir (botao "+ Nova conexao").
		leftSide = buildLeftSide();

		resultsArea = resultsController.buildResultsArea();
		// editorAreaPanel ANTES de buildToolbar(): buildToolbar() precisa
		// de editorTabs ja existente (updateSaveButtonState no final dele
		// consulta a aba atual) — editorTabs e construido dentro de
		// buildEditorArea() (initWorkspaces), nao mais dentro de
		// buildToolbar() em si.
		editorAreaPanel = buildEditorArea();

		// updateBanner + barra de acoes EMPILHADOS no NORTH da JANELA
		// INTEIRA (nao mais so da area do editor) — pedido explicito do
		// usuario ("o toolbar ocupar toda a linha?"): em janelas estreitas,
		// a barra de acoes espremida so na largura da coluna do editor
		// (sidebar de fora) ficava sem espaco pra tantos itens e varios
		// simplesmente SUMIAM (GridBagLayout zerando colunas quando nao
		// cabe tudo). Com a largura da JANELA INTEIRA (sidebar + editor),
		// sobra bem mais espaco pros mesmos itens.
		JPanel topArea = new JPanel();
		topArea.setLayout(new BoxLayout(topArea, BoxLayout.Y_AXIS));
		topArea.add(updateBanner);
		topArea.add(buildToolbar());
		add(topArea, BorderLayout.NORTH);

		centerSplit = new JSplitPane(resultsVertical ? JSplitPane.HORIZONTAL_SPLIT : JSplitPane.VERTICAL_SPLIT,
				editorAreaPanel, resultsArea);
		centerSplit.setResizeWeight(0.62);
		// Faltava aqui (so existia nos 3 outros lugares que reajustam esta
		// divisoria depois — #toggleResultsOrientation, saida do modo foco,
		// #setRowSpacingIndex): sem um setDividerLocation inicial,
		// setResizeWeight sozinho so decide como a divisoria se move numa
		// REDIMENSIONADA futura da janela, nunca a posicao INICIAL — o
		// JSplitPane cai no proprio calculo padrao (perto de 50/50) na
		// primeira exibicao, ignorando os 62% pretendidos. Bug relatado pelo
		// usuario: "o grid aparece a primeira vez dividindo metade da tela
		// com o editor SQL".
		centerSplit.setDividerLocation(0.62);
		centerSplit.setBorder(BorderFactory.createEmptyBorder());

		mainLayout = new JPanel(new BorderLayout());
		mainLayout.setBorder(BorderFactory.createEmptyBorder());
		mainLayout.add(centerSplit, BorderLayout.CENTER);
		mainLayout.add(leftSide, sidebarOnRight ? BorderLayout.EAST : BorderLayout.WEST);

		// Dock do Chat de IA: painel proprio na borda direita da janela,
		// SEMPRE no mesmo lugar (ver javadoc de #chatSplit) — comeca vazio e
		// colapsado (dividerSize 0), so ganha conteudo e largura de verdade
		// quando o usuario abre o chat pela 1a vez (ver #openAiChat).
		chatDock = new JPanel(new BorderLayout());
		chatDock.setOpaque(false);
		chatDock.setVisible(false);

		chatSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, mainLayout, chatDock);
		chatSplit.setResizeWeight(1.0);
		chatSplit.setBorder(BorderFactory.createEmptyBorder());
		chatSplit.setDividerSize(0);
		add(chatSplit, BorderLayout.CENTER);

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

	/**
	 * Largura MAXIMA (px, ja escalada por zoom/compacto — ver {@link #scaledPx})
	 * que a pilula de conexao pode atingir no meio da barra, ver
	 * {@link #resizeConnectionPill}. Sem um teto, um monitor desktop bem
	 * largo esticaria a pilula quase pela LARGURA TODA da barra — a maior
	 * parte dela vazia (so o nome/usuario ficam a esquerda dentro dela, ver
	 * {@link ConnectionStatusCard}), o que foge do "premium"/proporcionado
	 * que o resto do Design System pede (ver DESIGN_SYSTEM.md secao 4). O
	 * piso equivalente ja existe do lado oposto: {@link ConnectionStatusCard#MIN_WIDTH_PX}.
	 */
	private static final int CONNECTION_PILL_MAX_WIDTH = 560;

	/**
	 * {@code | Executar..Salvar | Conexao (expansivel, com teto) | Busca+
	 * navegacao+icones | } — grupo de acoes fixo na PONTA ESQUERDA, icones
	 * utilitarios fixos na PONTA DIREITA, e a pilula de conexao no meio
	 * ocupando o espaco sobrando entre os dois — layout pedido explicito do
	 * usuario com referencia visual (wireframe: icones nas pontas, seletor
	 * de conexao largo no centro).
	 * <p>
	 * Antes disto o bloco inteiro (3 grupos com FlowLayout) ficava
	 * CENTRALIZADO na barra com um espacador elastico IGUAL de cada lado
	 * (pedido antigo, agora substituido por este). "Executar" continua
	 * protegido de sumir em janela estreita pelo mesmo motivo estrutural
	 * de antes: e o primeiro grupo, peso ZERO, nunca encolhido pelo
	 * GridBagLayout antes da coluna do meio (que tem peso e pode encolher
	 * ate seu minimo primeiro).
	 * <p>
	 * A coluna do meio NAO usa {@code fill=HORIZONTAL} (testado manualmente
	 * fora do app: {@code GridBagLayout} ignora {@code getMaximumSize()}
	 * quando o fill esta ativo, esticando o componente pra largura inteira
	 * da coluna mesmo com um teto definido) — em vez disso,
	 * {@link #resizeConnectionPill} recalcula a largura desejada da pilula a
	 * CADA resize da barra (fill=NONE, largura vem de
	 * {@code setPreferredSize} manual), dentro de
	 * [{@link ConnectionStatusCard#MIN_WIDTH_PX}, {@link #CONNECTION_PILL_MAX_WIDTH}].
	 * Isso cobre tanto desktop largo (pilula para de crescer no teto, sobra
	 * vira respiro visual) quanto notebook estreito (pilula encolhe ate o
	 * piso antes de qualquer grupo de icones perder espaco).
	 */
	private JComponent buildToolbar() {
		JPanel mainBar = new JPanel(new GridBagLayout());
		mainBar.setOpaque(false);
		mainBar.setBorder(BorderFactory.createEmptyBorder(Spacing.SM, Spacing.MD, Spacing.SM, Spacing.MD));

		JComponent leftGroup = buildCoreActionsGroup();
		JComponent connectionBar = buildConnectionBar();
		JComponent rightGroup = buildTrailingGroup();

		GridBagConstraints gbc = new GridBagConstraints();
		gbc.gridy = 0;
		gbc.weighty = 1.0;
		gbc.fill = GridBagConstraints.NONE;
		gbc.anchor = GridBagConstraints.WEST;

		gbc.gridx = 0;
		gbc.weightx = 0.0;
		mainBar.add(leftGroup, gbc);

		gbc.gridx = 1;
		gbc.weightx = 1.0;
		gbc.anchor = GridBagConstraints.CENTER;
		gbc.insets = new Insets(0, Spacing.MD, 0, Spacing.MD);
		mainBar.add(connectionBar, gbc);

		gbc.gridx = 2;
		gbc.weightx = 0.0;
		gbc.insets = new Insets(0, 0, 0, 0);
		gbc.anchor = GridBagConstraints.EAST;
		mainBar.add(rightGroup, gbc);

		mainBar.addComponentListener(new ComponentAdapter() {
			@Override
			public void componentResized(ComponentEvent e) {
				resizeConnectionPill(mainBar, leftGroup, connectionBar, rightGroup);
			}
		});

		toolbarBar = mainBar;
		// initWorkspaces() ja rodou (ver buildEditorArea) quando chegamos aqui,
		// entao editorTabs ja tem a aba inicial — reflete o estado real dela no
		// botao Salvar desde o primeiro desenho, em vez de nascer sempre habilitado.
		updateSaveButtonState();
		return mainBar;
	}

	/**
	 * Recalcula a largura PREFERIDA da pilula de conexao (nao a de
	 * {@code mainBar} inteira) — chamado a cada {@code componentResized} da
	 * barra (ver {@link #buildToolbar}), cobrindo tanto o resize manual da
	 * janela quanto a troca inicial de "nao mostrada" pra "mostrada"
	 * (dispara resize tambem). So aplica {@code setPreferredSize}+
	 * {@code revalidate} quando o alvo MUDA — evita revalidar a barra
	 * inteira sem necessidade a cada pixel de resize.
	 */
	private void resizeConnectionPill(JPanel mainBar, JComponent leftGroup, JComponent connectionBar,
			JComponent rightGroup) {
		Insets barInsets = mainBar.getInsets();
		int available = mainBar.getWidth() - barInsets.left - barInsets.right
				- leftGroup.getPreferredSize().width - rightGroup.getPreferredSize().width
				- (2 * Spacing.MD);
		int target = Math.max(ConnectionStatusCard.MIN_WIDTH_PX,
				Math.min(scaledPx(CONNECTION_PILL_MAX_WIDTH), available));
		Dimension current = connectionBar.getPreferredSize();
		if (current.width != target) {
			connectionBar.setPreferredSize(new Dimension(target, current.height));
			mainBar.revalidate();
		}
	}

	/** Executar/Formatar/Explicar/Salvar — mesmo gap uniforme (Spacing.SM) entre CADA botao, ver javadoc de {@link #buildToolbar}. */
	private JComponent buildCoreActionsGroup() {
		JPanel group = new JPanel(new GridBagLayout());
		group.setOpaque(false);

		GridBagConstraints gbc = new GridBagConstraints();
		// CENTER (nao mais BASELINE): o grupo mistura botoes de TEXTO
		// (Executar) com botoes SO DE ICONE (Formatar/Explicar/Salvar/
		// setinhas) — BASELINE so faz sentido pra componentes com uma
		// linha de texto de verdade, entao os dois tipos "resolviam" a
		// propria posicao vertical de um jeito diferente, ficando um por
		// cima/baixo do outro (relatado pelo usuario com captura de tela).
		// CENTER e robusto pros 2 tipos ao mesmo tempo — e como todos ja
		// tem a MESMA altura fixada (rowHeight, ver
		// #addRunFormatExplainButtons/#addSaveButton), centralizar da
		// exatamente o mesmo resultado visual de "todos alinhados".
		gbc.anchor = GridBagConstraints.CENTER;
		gbc.fill = GridBagConstraints.NONE;
		gbc.weighty = 1.0;
		gbc.gridy = 0;

		int rowHeight = addRunFormatExplainButtons(group, gbc);
		addSaveButton(group, gbc, rowHeight);
		return group;
	}

	/**
	 * Busca do editor + navegacao (SQLs/Salvas/Historico) + icones
	 * utilitarios — TODOS num UNICO {@code FlowLayout} com o MESMO gap
	 * (Spacing.SM) entre cada icone, sem excecao (antes a busca tinha um
	 * gap MAIOR — Spacing.MD — antes do resto do grupo, e o grupo em si
	 * usava Spacing.XS por dentro: duas inconsistencias diferentes na
	 * MESMA fileira de icones, corrigidas aqui numa fileira so — pedido
	 * explicito do usuario: "não permita desalinhamento").
	 * <p>
	 * Ordem dos 3 icones utilitarios (tema/resultados/layout) alinhada ao
	 * wireframe de referencia (sol · paineis · engrenagem, da esquerda pra
	 * direita) — busca continua primeiro, por nao ter equivalente no
	 * wireframe (so um rascunho de proporcao, nao uma lista exaustiva de
	 * funcionalidades).
	 */
	private JPanel buildTrailingGroup() {
		JPanel trailing = new JPanel(new FlowLayout(FlowLayout.LEFT, Spacing.SM, 0));
		trailing.setOpaque(false);
		trailing.add(buildSearchIconButton());

		// Icone inicial mostra a ACAO do botao (pra ONDE ele muda o tema), nao
		// o tema atual — app arranca no tema escuro (ver App#main), entao o
		// botao comeca oferecendo "mudar para claro" (icone de sol). O TIPO do
		// icone muda conforme o tema (sol/lua), entao continua sendo
		// resetado explicitamente em toggleTheme() — so a COR (MUTED_TEXT)
		// precisava do fix generico, ja coberta por iconButton aqui tambem.
		themeButton = Buttons.iconButton(IconType.THEME_LIGHT, 16, () -> GridTheme.MUTED_TEXT);
		themeButton.setToolTipText("Alternar tema claro/escuro");
		themeButton.addActionListener(e -> toggleTheme());
		trailing.add(themeButton);

		// Buttons.iconButton ja aplica styleIconButton E prende o icone a
		// GridTheme.MUTED_TEXT (ver seu javadoc) — antes estes botoes eram
		// "new JButton(Icons.get(..., GridTheme.MUTED_TEXT))" com a cor
		// CONGELADA no tema em que a janela abriu, so corrigindo sozinha se
		// o usuario fechasse e reabrisse a janela.
		//
		// "Mostrar/ocultar painel lateral", "SQLs", "Salvas"/"Favoritos",
		// "Historico" e "Chat com IA" SAIRAM daqui — pedido explicito do
		// usuario ("quero que objetos, e os itens de ferramentas virem
		// icones de uma barra lateral... isso elimina o icone de esconder e
		// abrir objetos que esta na toolbar superior direita"): agora sao
		// itens do rail vertical (ver {@link #buildLeftSide}/{@link
		// #onRailItemSelected}), que fica sempre visivel — nao precisa mais
		// de um botao separado pra mostrar/esconder nada.
		JButton toggleResults = Buttons.iconButton(IconType.PANEL_BOTTOM, 16, () -> GridTheme.MUTED_TEXT);
		toggleResults.setToolTipText("Mostrar/ocultar resultados (Ctrl+J)");
		toggleResults.addActionListener(e -> toggleResults());
		trailing.add(toggleResults);

		JButton layoutButton = Buttons.iconButton(IconType.SETTINGS, 16, () -> GridTheme.MUTED_TEXT);
		layoutButton.setToolTipText("Layout, zoom e modo compacto");
		layoutButton.addActionListener(e -> buildLayoutMenu().show(layoutButton, 0, layoutButton.getHeight()));
		trailing.add(layoutButton);

		return trailing;
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

		// "Executar ▾" — seta separada, mesmo estilo SO-DE-ICONE flat do
		// resto da barra agora (ver comentario em #formatButton logo
		// abaixo) — abre "Executar esta instrucao"
		// (SqlEditorPane#runStatementUnderCaret, ja existia so no menu de
		// contexto do editor — ver SqlEditorPane#buildEditorPopupMenu), sem
		// precisar clicar com o botao direito no editor primeiro.
		JButton runMenuButton = new JButton(new com.formdev.flatlaf.icons.FlatMenuArrowIcon());
		runMenuButton.setToolTipText("Opcoes de execucao");
		runMenuButton.addActionListener(e -> buildRunMenu().show(runMenuButton, 0, runMenuButton.getHeight()));
		Buttons.styleIconButton(runMenuButton);

		// SO-DE-ICONE (nao mais texto+icone) — pedido explicito do usuario
		// apos revisao visual ("podemos colocar as demais funcionalidades
		// apenas como icones ao inves de botoes com labels... fica melhor e
		// alinhar tudo"): Executar continua com texto (e a UNICA acao
		// primaria/preenchida da barra, precisa se destacar) — Formatar/
		// Explicar/Salvar viram icones flat, mesma linguagem visual do
		// grupo de navegacao da direita, e MUITO mais compactos (ajuda a
		// barra inteira caber em janelas estreitas sem precisar rolar).
		JButton formatButton = Buttons.iconButton(IconType.FORMAT, 16, () -> GridTheme.MUTED_TEXT);
		formatButton.setToolTipText("Formatar SQL (Ctrl+Shift+F)");
		formatButton.addActionListener(e -> {
			SqlEditorPane editor = currentEditor();
			if (editor != null) {
				editor.formatText();
			}
		});

		JButton formatMenuButton = new JButton(new com.formdev.flatlaf.icons.FlatMenuArrowIcon());
		formatMenuButton.setToolTipText("Presets e opcoes de formatacao");
		formatMenuButton
				.addActionListener(e -> buildFormatMenu().show(formatMenuButton, 0, formatMenuButton.getHeight()));
		Buttons.styleIconButton(formatMenuButton);

		// "Explicar" (fase 4 do GAP_ANALYSIS_DBA_DEV.md: "EXPLAIN visual") —
		// roda EXPLAIN FORMAT=JSON na instrucao atual sem executa-la de
		// verdade. IconType.INFO (nao ha um icone dedicado de "plano de
		// execucao" no catalogo do NDS): EXPLAIN mostra INFORMACAO sobre
		// como a consulta roda, encaixe semantico razoavel sem inventar um
		// icone novo so pra isto.
		JButton explainButton = Buttons.iconButton(IconType.INFO, 16, () -> GridTheme.MUTED_TEXT);
		explainButton.setToolTipText("Ver o plano de execucao (EXPLAIN) da consulta atual");
		explainButton.addActionListener(e -> onExplain());

		// O icone minusculo da seta rende um "preferred height" menor que o do
		// texto "Executar"/"Formatar" — sem isto os componentes ficam com
		// alturas ligeiramente diferentes mesmo com a mesma margem vertical.
		// Forca todos a MESMA altura (a maior de todos), so a largura
		// continua livre.
		int rowHeight = Math.max(runButton.getPreferredSize().height,
				Math.max(runMenuButton.getPreferredSize().height,
						Math.max(formatButton.getPreferredSize().height, Math.max(formatMenuButton.getPreferredSize().height,
								explainButton.getPreferredSize().height))));
		for (JButton b : new JButton[] { runButton, runMenuButton, formatButton, formatMenuButton, explainButton }) {
			Dimension d = b.getPreferredSize();
			b.setPreferredSize(new Dimension(d.width, rowHeight));
		}

		// Espacamento UNIFORME (Spacing.SM) entre CADA elemento da barra, sem
		// excecao — pedido explicito do usuario ("remova todas as margins
		// entre eles e so coloque espaco entre, deixe uniforme"). Antes,
		// alguns pares (Executar+seta, Formatar+seta) tinham inset ZERO
		// ("colados") tentando imitar um split-button unico — mas o FlatLaf
		// nao suporta raio assimetrico por botao (arc: 8 e sempre nos 4
		// cantos, verificado no fonte da FlatButtonBorder), entao dois
		// botoes com cantos arredondados encostados um no outro sem gap
		// nenhum so pareciam DESALINHADOS/quebrados, nao um controle so —
		// exatamente o que o usuario reportou. Gap identico em todo lugar
		// remove essa inconsistencia visual de uma vez.
		gbc.gridx = 0;
		gbc.weightx = 0.0;
		gbc.insets = new Insets(0, 0, 0, 0);
		mainBar.add(runButton, gbc);

		gbc.gridx = 1;
		gbc.insets = new Insets(0, Spacing.SM, 0, 0);
		mainBar.add(runMenuButton, gbc);

		gbc.gridx = 2;
		gbc.insets = new Insets(0, Spacing.SM, 0, 0);
		mainBar.add(formatButton, gbc);

		gbc.gridx = 3;
		gbc.insets = new Insets(0, Spacing.SM, 0, 0);
		mainBar.add(formatMenuButton, gbc);

		gbc.gridx = 4;
		gbc.insets = new Insets(0, Spacing.SM, 0, 0);
		mainBar.add(explainButton, gbc);
		return rowHeight;
	}

	/**
	 * Menu do "Executar ▾" (seta ao lado do botao "Executar", mesmo padrao de
	 * "Formatar ▾") — hoje so tem "Executar esta instrucao" (roda so a
	 * instrucao sob o cursor, ver {@link SqlEditorPane#runStatementUnderCaret()}),
	 * ja existia so no menu de contexto do editor. O clique DIRETO em
	 * "Executar" continua com o comportamento de sempre (roda a selecao, se
	 * houver, senao a aba inteira) — este menu so oferece a VARIANTE extra,
	 * nao repete a acao padrao.
	 */
	private JPopupMenu buildRunMenu() {
		JPopupMenu menu = new JPopupMenu();
		JMenuItem runStatement = new JMenuItem("Executar esta instrucao");
		runStatement.setToolTipText("Roda so a instrucao SQL sob o cursor, ate o \";\" anterior/seguinte");
		runStatement.addActionListener(a -> {
			SqlEditorPane editor = currentEditor();
			if (editor != null) {
				editor.runStatementUnderCaret();
			}
		});
		menu.add(runStatement);
		return menu;
	}

	/**
	 * Salvar — segundo grupo da esquerda, mesma altura calculada pelo grupo
	 * anterior. Um botao "Historico" chegou a existir aqui tambem (revisao
	 * de UX antiga: era um duplicado exato do icone de mesmo nome em toda
	 * aba de SQL, ver {@code SqlEditorPane#buildQuickActionRow}) e foi
	 * removido quando "Historico" virou aba fixa da sidebar (unico lugar
	 * dedicado, na epoca). Hoje "Historico" voltou a ter um botao proprio
	 * — no grupo de icones da DIREITA, nao aqui — mas abre um painel
	 * flutuante (ver {@link #showHistoryPopup}), nao mais uma aba fixa;
	 * ver o javadoc de {@link #buildLeftSide} pro motivo dessa mudanca.
	 */
	private void addSaveButton(JPanel mainBar, GridBagConstraints gbc, int rowHeight) {
		// Salvar a aba atual como query (biblioteca gerenciada pelo app — ver
		// SavedQueryStore): SO-DE-ICONE agora, mesmo motivo de Formatar/
		// Explicar (ver #addRunFormatExplainButtons). Desabilitado quando a
		// aba atual esta vazia (ver updateSaveButtonState) — antes o clique
		// era aceito mas nao fazia nada alem de um aviso na barra de
		// status, o que parecia um bug de "salvar nao funciona".
		saveQueryButton = Buttons.iconButton(IconType.SAVE, 16, () -> GridTheme.MUTED_TEXT);
		saveQueryButton.setToolTipText("Salvar como query (Ctrl+S)");
		saveQueryButton.addActionListener(e -> onSaveQuery());
		Dimension saveDim = saveQueryButton.getPreferredSize();
		saveQueryButton.setPreferredSize(new Dimension(saveDim.width, rowHeight));

		// "Salvar ▾" — mesmo padrao de "Executar ▾"/"Formatar ▾": a seta
		// oferece "Salvar como nova query...", que FORCA uma copia nova mesmo
		// quando a aba ja esta ligada a uma query salva (ver
		// MainWindow#onSaveQuery(boolean)) — o clique DIRETO em "Salvar"
		// continua sobrescrevendo sem perguntar, como sempre.
		JButton saveMenuButton = new JButton(new com.formdev.flatlaf.icons.FlatMenuArrowIcon());
		saveMenuButton.setToolTipText("Opcoes de salvamento");
		saveMenuButton.addActionListener(e -> buildSaveMenu().show(saveMenuButton, 0, saveMenuButton.getHeight()));
		Buttons.styleIconButton(saveMenuButton);
		saveMenuButton.setPreferredSize(new Dimension(saveMenuButton.getPreferredSize().width, rowHeight));

		// Mesmo gap uniforme (Spacing.SM) do resto da barra — ver comentario em
		// #addRunFormatExplainButtons.
		gbc.gridx = 5;
		gbc.insets = new Insets(0, Spacing.SM, 0, 0);
		mainBar.add(saveQueryButton, gbc);

		gbc.gridx = 6;
		gbc.insets = new Insets(0, Spacing.SM, 0, 0);
		mainBar.add(saveMenuButton, gbc);
	}

	/**
	 * Menu do "Salvar ▾" — hoje so tem "Salvar como nova query...", que
	 * chama {@link #onSaveQuery(boolean)} com {@code forceNew=true} (sempre
	 * cria uma copia nova, mesmo se a aba ja estiver ligada a uma query
	 * salva). Desabilitado quando a aba atual esta vazia, mesmo criterio de
	 * {@link #updateSaveButtonState}.
	 */
	private JPopupMenu buildSaveMenu() {
		JPopupMenu menu = new JPopupMenu();
		JMenuItem saveAsNew = new JMenuItem("Salvar como nova query...");
		SqlEditorPane editor = currentEditor();
		boolean hasContent = editor != null && editor.fullText() != null && !editor.fullText().isBlank();
		saveAsNew.setEnabled(hasContent);
		saveAsNew.addActionListener(a -> onSaveQuery(true));
		menu.add(saveAsNew);
		return menu;
	}

	/**
	 * Busca/substituicao no editor ativo — ANTES um campo de texto fixo
	 * (~200px sempre reservados na barra, so buscar, sem substituir, so
	 * encaminhava pra {@code SqlEditorPane#searchFromToolbar}); agora um
	 * icone que abre a barra de localizar/substituir INTEIRA (ver
	 * {@link SqlEditorPane#openFindReplace()}) — pedido explicito do
	 * usuario ("a barra de busca do sql poderia ser icone que abre popup
	 * ou algo assim com todas as opcoes de busca ou substituicao"). Ganha
	 * dois problemas de uma vez: menos largura reservada sempre-visivel
	 * (ajuda o "Executar nunca pode sumir", ver {@link #buildToolbar}) e
	 * acesso a MAIS opcoes (substituir, nao so buscar) que o campo antigo
	 * nao tinha.
	 */
	private JButton buildSearchIconButton() {
		JButton button = Buttons.iconButton(IconType.SEARCH, 16, () -> GridTheme.MUTED_TEXT);
		button.setToolTipText("Buscar/substituir no editor (Ctrl+F)");
		button.addActionListener(e -> {
			SqlEditorPane editor = currentEditor();
			if (editor != null) {
				editor.openFindReplace();
			}
		});
		return button;
	}

	/**
	 * Menu de layout: mover painel lateral, alternar orientacao dos resultados,
	 * modo compacto e niveis de zoom (Opcao B da spec: menu, em vez de
	 * drag-and-drop dos paineis).
	 */

	/**
	 * Agrupado por CONTEXTO real de cada opcao (pedido explicito do usuario,
	 * apos revisao visual: "o que for de configuracao de grid deve estar nas
	 * configuracoes de grid, o que for de sql editor nas configuracoes de sql
	 * editor..."), nao mais uma lista unica sem hierarquia — mesma receita de
	 * cabecalho de secao ({@link #formatMenuHeader}) que {@link #buildFormatMenu}
	 * ja usa para "Presets"/"Configuracoes". "Layout" fica com o que afeta a
	 * JANELA inteira; "Conexao" com o que e comportamento de conexao (nao tem
	 * nada de layout); "Grade e arvore" com o que e especifico dos
	 * componentes em linha (grade/arvore/conexoes, ver {@link #resultRowHeightBasePx});
	 * "Aplicativo" com o que nao e nem um nem outro.
	 */
	private JPopupMenu buildLayoutMenu() {
		JPopupMenu menu = new JPopupMenu();

		menu.add(formatMenuHeader("Layout"));
		JMenuItem moveSidebar = new JMenuItem(
				sidebarOnRight ? "Mover painel lateral para a esquerda" : "Mover painel lateral para a direita");
		moveSidebar.addActionListener(a -> toggleSidebarSide());
		menu.add(moveSidebar);

		JMenuItem toggleOrientation = new JMenuItem(resultsVertical ? "Resultados embaixo do editor (horizontal)"
				: "Resultados ao lado do editor (vertical)");
		toggleOrientation.addActionListener(a -> toggleResultsOrientation());
		menu.add(toggleOrientation);

		JCheckBoxMenuItem compact = new JCheckBoxMenuItem("Modo compacto", compactMode);
		compact.addActionListener(a -> toggleCompactMode());
		menu.add(compact);

		JMenu zoomMenu = new JMenu("Zoom");
		for (int i = 0; i < ZOOM_LEVELS.length; i++) {
			int idx = i;
			int pct = (int) Math.round(ZOOM_LEVELS[i] * 100);
			// JCheckBoxMenuItem nativo (marcador do FlatLaf), nao mais um
			// caractere Unicode "✓ " colado no texto — ver o mesmo ajuste (e o
			// motivo) em #presetItem: o caractere nao existe em algumas fontes
			// e aparecia como um quadrado vazio ("tofu") no lugar do check,
			// relatado pelo usuario neste menu e no de Espacamento de linhas.
			JCheckBoxMenuItem item = new JCheckBoxMenuItem(pct + "%", i == zoomIndex);
			item.addActionListener(a -> setZoomIndex(idx));
			zoomMenu.add(item);
		}
		zoomMenu.addSeparator();
		JMenuItem reset = new JMenuItem("Redefinir (Ctrl+0)");
		reset.addActionListener(a -> resetZoom());
		zoomMenu.add(reset);
		menu.add(zoomMenu);

		menu.addSeparator();
		menu.add(formatMenuHeader("Grade e arvore"));
		// Espacamento de linhas — pedido explicito do usuario, independente do
		// Zoom acima (que escala a interface inteira): so a altura de linha
		// dos componentes em formato de linha unica (icone + texto) — grade
		// de resultados, arvore de Objetos e lista de abas de SQL (ver
		// #sqlEditorsList). Historico/Consultas Salvas/Conexoes ficam de
		// fora: sao cards de DUAS linhas, estrutura diferente o bastante pra
		// essa mesma escala (18-34px) quebrar o layout deles — a lista de
		// Conexoes passou a ser card de 2 linhas numa revisao visual
		// (ConnectionRenderer), entao connectionsPanel.setRowHeight(...)
		// abaixo virou no-op de proposito (ver seu javadoc).
		JMenu rowSpacingMenu = new JMenu("Espacamento de linhas");
		for (int i = 0; i < ROW_SPACING_LEVELS.length; i++) {
			int idx = i;
			JCheckBoxMenuItem item = new JCheckBoxMenuItem(
					ROW_SPACING_LABELS[i] + " (" + ROW_SPACING_LEVELS[i] + "px)", i == rowSpacingIndex);
			item.addActionListener(a -> setRowSpacingIndex(idx));
			rowSpacingMenu.add(item);
		}
		menu.add(rowSpacingMenu);

		menu.addSeparator();
		menu.add(formatMenuHeader("Conexao"));
		JCheckBoxMenuItem keepAlive = new JCheckBoxMenuItem("Manter conexao viva (keep-alive)", keepAliveEnabled);
		keepAlive.setToolTipText("Roda um SELECT de teste a cada " + keepAliveIntervalLabel() + " de ociosidade, "
				+ "so nas conexoes que ja estao abertas, pra evitar que caiam por inatividade.");
		keepAlive.addActionListener(a -> toggleKeepAlive());
		menu.add(keepAlive);

		JMenuItem keepAliveInterval = new JMenuItem("Intervalo do keep-alive... (" + keepAliveIntervalLabel() + ")");
		keepAliveInterval.addActionListener(a -> configureKeepAliveInterval());
		menu.add(keepAliveInterval);

		menu.addSeparator();
		menu.add(formatMenuHeader("Aplicativo"));
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
		// Sem divisoria pra mexer (mainLayout e BorderLayout, nao mais
		// JSplitPane, ver o javadoc do campo): esconder/mostrar o rail e so
		// alternar visibilidade — a regiao WEST/EAST do BorderLayout
		// automaticamente colapsa pra zero quando o filho fica invisivel, e
		// volta pra largura preferida (64px) quando fica visivel de novo.
		leftSide.setVisible(!leftSide.isVisible());
		mainLayout.revalidate();
		mainLayout.repaint();
		focusEditor();
	}

	/**
	 * Recolhe/expande a area de resultados (ciente da orientacao atual), focando o
	 * editor.
	 */
	private void toggleResults() {
		setResultsVisible(!resultsArea.isVisible());
		focusEditor();
	}

	/** Mecanica pura de mostrar/esconder Resultados (ver {@link #toggleResults}), SEM mover o foco. */
	private void setResultsVisible(boolean visible) {
		if (resultsArea.isVisible() == visible) {
			return;
		}
		boolean horizontalSplit = centerSplit.getOrientation() == JSplitPane.VERTICAL_SPLIT;
		if (!visible) {
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
	 * UI), Ctrl+K (busca da sidebar unificada, ver {@link #buildLeftSide}).
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
		bindGlobalAction(rp, "control K", "focus-object-search", this::focusObjectSearch);
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
	 * Move o rail de icones (ver {@link #buildLeftSide}) pro outro lado —
	 * so troca a regiao do {@link #mainLayout} (WEST/EAST); sem divisoria
	 * pra recalcular, ao contrario da versao antiga com {@code JSplitPane}.
	 */
	private void toggleSidebarSide() {
		sidebarOnRight = !sidebarOnRight;
		mainLayout.remove(leftSide);
		mainLayout.add(leftSide, sidebarOnRight ? BorderLayout.EAST : BorderLayout.WEST);
		mainLayout.revalidate();
		mainLayout.repaint();
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
	 * aplicando zoom/modo compacto por cima) usada por {@link ResultGrid}, a
	 * arvore de Objetos, a lista de Conexoes e a lista de abas de SQL
	 * ({@link #sqlEditorsList}) — trocar o indice reconstroi/reaplica nos 4
	 * (ver {@link #setRowSpacingIndex}/{@link #refreshDynamicSizing}) do mesmo
	 * jeito que mudar o zoom ja fazia.
	 */
	int resultRowHeightBasePx() {
		return ROW_SPACING_LEVELS[rowSpacingIndex];
	}

	/** Define o espacamento de linha (0..ROW_SPACING_LEVELS.length-1) e reaplica em grade/arvore/conexoes. */
	private void setRowSpacingIndex(int index) {
		rowSpacingIndex = clampRowSpacingIndex(index);
		refreshDynamicSizing();
		saveUiState();
		if (statusBar != null) {
			statusBar.setText(" Espacamento de linhas: " + ROW_SPACING_LABELS[rowSpacingIndex] + ".");
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
		objectExplorer.setRowHeight(scaledPx(resultRowHeightBasePx()));
		if (connectionsPanel != null) {
			connectionsPanel.setRowHeight(scaledPx(resultRowHeightBasePx()));
		}
		if (sqlEditorsList != null) {
			sqlEditorsList.setFixedCellHeight(scaledPx(resultRowHeightBasePx()));
		}
		if (toolbarBar != null) {
			toolbarBar.revalidate();
			toolbarBar.repaint();
		}
		// leftSide (rail de icones, ver #buildLeftSide) nao precisa mais de
		// minimumSize nenhum aqui: sua largura vem do proprio NIconRail
		// (fixa, ITEM_WIDTH) e o BorderLayout de #mainLayout ja respeita
		// isso sempre — nao ha mais divisoria de JSplitPane pra travar.
		// Reconstroi as abas de resultado (tabela, gutter e cabecalho usam
		// tamanhos fixos definidos na hora da criacao do JTable).
		resultsController.reshowIfVisible();
		if (mainLayout != null) {
			mainLayout.revalidate();
			mainLayout.repaint();
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
		String user = (activeWorkspace != null && activeWorkspace.profile != null) ? activeWorkspace.profile.user()
				: null;
		connectionCard.showConnected(label, user);
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
		if (chatWindow != null) {
			chatWindow.setSchemaLabel(chatSchemaLabel());
		}
		refreshSqlEditorsCount();
	}

	/** "Esquema: X" pro badge do topo do Chat (ver ChatPanel), ou rotulo neutro sem conexao/esquema. */
	private String chatSchemaLabel() {
		if (activeWorkspace == null || activeWorkspace.profile() == null) {
			return "Sem conexao";
		}
		if (!activeWorkspace.mgr().isConnected()) {
			return "Desconectado";
		}
		return currentSchema != null ? "Esquema: " + currentSchema.name() : activeWorkspace.name();
	}

	/**
	 * Indicador "Conexao Ativa" — ja foi uma barra inteira acima de Executar/
	 * Formatar/... (span da largura toda da janela), depois embutida DENTRO
	 * da barra de acoes ao lado do "Salvar", depois topo fixo da coluna da
	 * sidebar (acima de "Objetos", alinhada com "Executar" acima das abas
	 * do editor — "dois cabecalhos independentes") — e agora de volta pra
	 * DENTRO da barra de acoes, no CENTRO dela (pedido explicito do
	 * usuario: "conexoes precisa ir para o centro da toolbar"), e desde a
	 * revisao de layout com wireframe ("icones nas pontas, seletor largo no
	 * centro") a pilula EXPANDE pra ocupar todo o espaco sobrando entre os
	 * dois grupos de icones fixos — ver {@link #buildToolbar} pra como a
	 * coluna do meio recebe {@code weightx=1.0}/{@code fill=HORIZONTAL}.
	 * Chamada de dentro de {@link #buildToolbar()}, DEPOIS de
	 * {@link #buildLeftSide()} ja ter
	 * rodado (precisa de {@link #connectionsPanel} existente pro botao "+
	 * Nova conexao" e pra seta abrir "Conexoes salvas" — ver
	 * {@link #showConnectionsPopup}).
	 */
	private JComponent buildConnectionBar() {
		connectionCard = new ConnectionStatusCard();
		connectionCard.setOnSwitchRequested(name -> {
			Conexao w = workspaces.get(name);
			if (w != null) {
				activateWorkspace(w);
				statusBar.setText(" Workspace: " + name);
			}
		});
		connectionCard.setOnManageConnections(() -> showConnectionsPopup(connectionCard.popupAnchor()));
		// MESMA acao do botao "+" dentro do dropdown de conexoes
		// (ConnectionsPanel#createNewConnection ja e so um encaminhamento
		// pro onNew() de la, nenhuma logica duplicada aqui) — atalho pra
		// nao precisar abrir o dropdown primeiro so pra cadastrar uma
		// conexao nova.
		connectionCard.setOnNewConnection(connectionsPanel::createNewConnection);
		return connectionCard;
	}

	// ---------- Lado esquerdo ----------

	/**
	 * Sidebar: hoje SO a arvore de Objetos (ver javadoc da classe/dos campos
	 * {@code AnchoredPopup} pro historico completo) — Conexoes/SQL/Salvas/
	 * Historico sairam daqui ao longo de varias revisoes de UX e viraram
	 * botao+popup na barra de acoes do editor (ver {@link #buildToolbar}),
	 * "Objetos" continua fixo de proposito (consulta CONSTANTE enquanto se
	 * escreve SQL, ao contrario dos outros, que sao navegacao ocasional).
	 * {@link ConnectionStatusCard} em particular passou por varias posicoes
	 * (barra inteira no topo da janela, embutida na barra de acoes, topo
	 * fixo desta coluna) antes de virar o CENTRO da barra de acoes (ver
	 * {@link #buildConnectionBar}), pedido explicito do usuario.
	 * <p>
	 * FERRAMENTAS (Backup/Usuarios/Monitor/Chat) continua um grupo fixo no
	 * rodape desta coluna — 3 linhas simples que delegam pros dialogos ja
	 * existentes (hoje so acessiveis pelo menu de contexto da raiz do
	 * esquema).
	 */
	private JComponent buildLeftSide() {
		// ConnectionsPanel PRIMEIRO (buildConnectionBar, chamada de dentro
		// de #buildToolbar, precisa dele ja existente pra achar seu dono de
		// dialogos, ver ConnectionsPanel#setOwnerWindow, e pro botao "+
		// Nova conexao").
		connectionsPanel = new ConnectionsPanel(connectionStore, this::connectTo, this::disconnectFrom);
		connectionsPanel.setOwnerWindow(this);
		connectionsPanel.setRowHeight(scaledPx(resultRowHeightBasePx()));
		// Tamanho do popup recalculado a cada abertura (ver #showConnectionsPopup),
		// nao fixado aqui: precisa acompanhar zoom/modo compacto como o resto
		// da UI, e o zoom pode mudar entre uma abertura e outra.
		savedQueriesPanel = new SavedQueriesPanel(savedQueryStore, this::openSavedQuery);
		historyPanel = new HistoryPanel(historyStore, this::openHistoryEntry);

		// selfStyling: a sidebar so e construida uma vez (nunca reconstruida
		// no toggle de tema) — sem isto o rotulo ficava com a cor do tema
		// em que o app abriu gravada pra sempre.
		sqlEditorsCountLabel = Typography.selfStyling("SQL Editors", Typography::secondary);
		JComponent sqlEditorsRow = sidebarRow(IconType.EDIT, sqlEditorsCountLabel, true, null, () -> {
			if (!addQueryTab()) {
				selectLastRealTab();
			}
			// Fecha o popup "SQLs" (ver #showSqlEditorsPopup) — mesmo padrao
			// dos outros popups de navegacao: a acao ja foi feita.
			sqlEditorsPopup.close();
		});
		// Lista de verdade das abas de SQL abertas, clicaveis pra navegar entre
		// elas — pedido explicito do usuario: a aba "SQL" so tinha o atalho
		// "SQL Editors (N)" pra ABRIR uma aba nova, sem nenhuma forma de ver
		// OU alternar entre as N abas ja abertas (parecia quebrada: um numero
		// que nao levava a lugar nenhum). Guardamos o COMPONENTE de cada aba
		// (nao o indice — indices deslocam ao fechar uma aba no meio; o
		// componente e estavel) e resolvemos titulo/dot ao vivo via
		// editorTabs.indexOfComponent/getTitleAt/getIconAt em vez de duplicar
		// esse estado aqui.
		sqlEditorsList = new JList<>(sqlEditorsListModel) {
			private static final long serialVersionUID = 1L;

			// Mesmo bug ja corrigido em ConnectionsPanel/HistoryPanel/
			// SavedQueriesPanel: setSelectionBackground/Foreground "queima" a
			// cor na hora da chamada — sem reaplicar aqui, a troca de tema
			// (FlatLaf.updateUI(), ver #toggleTheme) reseta pro cinza padrao
			// do L&F em vez de acompanhar GridTheme.
			@Override
			public void updateUI() {
				super.updateUI();
				setSelectionBackground(GridTheme.SELECTION_BACKGROUND);
				setSelectionForeground(GridTheme.SELECTION_FOREGROUND);
			}
		};
		sqlEditorsList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		sqlEditorsList.setVisibleRowCount(6);
		sqlEditorsList.setFixedCellHeight(scaledPx(resultRowHeightBasePx()));
		sqlEditorsList.setSelectionBackground(GridTheme.SELECTION_BACKGROUND);
		sqlEditorsList.setSelectionForeground(GridTheme.SELECTION_FOREGROUND);
		sqlEditorsList.setCellRenderer(new SqlEditorsListRenderer());
		TreeHoverTracker.installOnList(sqlEditorsList);
		sqlEditorsList.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				int idx = sqlEditorsList.locationToIndex(e.getPoint());
				if (idx < 0) {
					return;
				}
				sqlEditorsList.setSelectedIndex(idx);
				Component tabComponent = sqlEditorsListModel.get(idx);
				editorTabs.setSelectedComponent(tabComponent);
				focusEditor();
				// Fecha o popup "SQLs" — mesma acao de #sqlEditorsRow acima.
				sqlEditorsPopup.close();
			}
		});
		refreshSqlEditorsCount();
		JScrollPane sqlEditorsListScroll = new JScrollPane(sqlEditorsList);
		sqlEditorsListScroll.setBorder(BorderFactory.createEmptyBorder());
		sqlEditorsPanel = new JPanel(new BorderLayout(0, 4));
		sqlEditorsPanel.setOpaque(false);
		sqlEditorsPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
		sqlEditorsPanel.add(sqlEditorsRow, BorderLayout.NORTH);
		sqlEditorsPanel.add(sqlEditorsListScroll, BorderLayout.CENTER);

		// "Objetos" guardado como CAMPO (nao mais embutido direto na coluna):
		// agora e conteudo de popup, igual SQL/Salvas/Historico ja eram (ver
		// #showObjectsPopup) — construido uma unica vez aqui, reaberto a
		// cada clique no rail.
		objectsBrowserPanel = objectExplorer.buildObjectBrowser();

		// Rail vertical de icones substitui a coluna inteira (arvore de
		// Objetos + rodape FERRAMENTAS) que ficava sempre visivel e larga —
		// pedido explicito do usuario: "quero que objetos, e os itens de
		// ferramentas virem icones de uma barra lateral, assim consigo
		// mesmo minimizando ter acesso facil pra abrir novamente". Cada
		// item abre seu painel como popup ancorado a DIREITA do proprio
		// icone (ver AnchoredPopup#toggle com Placement.RIGHT) em vez de
		// ocupar espaco fixo na tela — e como o rail em si NUNCA precisa
		// ser escondido (e so 64px, sempre visivel), o botao de
		// mostrar/ocultar painel lateral que ficava na barra de acoes
		// (topo direito) deixou de fazer sentido e foi removido (ver
		// #buildTrailingGroup) — isso elimina a necessidade de "abrir a
		// sidebar de novo" que existia antes.
		iconRail = new NIconRail();
		iconRail.addItem("objetos", IconType.DATABASE, "Objetos")
				.addItem("sqls", IconType.EDIT, "SQLs")
				.addItem("favoritos", IconType.FAVORITE, "Favoritos")
				.addItem("historico", IconType.HISTORY, "Historico")
				.addItem("ia", IconType.CHAT, "IA")
				.addItem("backup", IconType.BACKUP, "Backup")
				.addItem("usuarios", IconType.USERS, "Usuarios")
				.addItem("mais", IconType.MORE, "Mais");
		iconRail.onSelect(this::onRailItemSelected);
		return iconRail;
	}

	/**
	 * Roteia o clique num item do {@link #iconRail} pro painel/dialogo
	 * correspondente — a maioria abre um {@link AnchoredPopup} ancorado ao
	 * proprio icone (ver {@link #showObjectsPopup} e equivalentes);
	 * "IA"/"Backup"/"Usuarios" abrem o que ja abriam antes (dock proprio ou
	 * dialogo modal, ver {@link #openAiChat}/{@code ObjectExplorerController});
	 * "Mais" e um menu simples pros itens que sobraram (hoje so "Monitor de
	 * Conexao" — sobrariam mais aqui se o rail crescer).
	 */
	private void onRailItemSelected(String id) {
		JComponent anchor = iconRail.anchorFor(id);
		switch (id) {
			case "objetos" -> showObjectsPopup(anchor);
			case "sqls" -> showSqlEditorsPopup(anchor);
			case "favoritos" -> showSavedQueriesPopup(anchor);
			case "historico" -> showHistoryPopup(anchor);
			case "ia" -> openAiChat();
			case "backup" -> objectExplorer.openBackupRestore();
			case "usuarios" -> objectExplorer.openUserManagement();
			case "mais" -> {
				JPopupMenu menu = new JPopupMenu();
				JMenuItem monitor = new JMenuItem("Monitor de Conexao");
				monitor.addActionListener(e -> objectExplorer.openProcessList());
				menu.add(monitor);
				menu.show(anchor, anchor.getWidth(), 0);
			}
			default -> { }
		}
	}

	/**
	 * Painel flutuante ancorado logo abaixo de um botao (Conexoes/Historico/
	 * SQLs/Salvas — ver os 4 campos {@code AnchoredPopup} no topo da
	 * classe): {@link JDialog} sem borda (undecorated), NAO-MODAL, que se
	 * fecha sozinho ao perder foco — mesmo efeito visual de "dropdown
	 * acoplado ao botao", mas suportando dialogos/menus filhos de verdade
	 * (eles sao JANELAS PROPRIAS, donas=o popup, e {@link #isChildWindowShowing}
	 * impede o fechamento automatico enquanto uma delas estiver aberta).
	 * <p>
	 * Ponto UNICO pros 4 popups (antes cada um tinha sua PROPRIA copia de
	 * ~40 linhas quase identicas): originado do popup de Conexoes, que
	 * precisou virar JDialog (nao mais {@link JPopupMenu}, ate a v1.1.3)
	 * porque {@link ConnectionsPanel} abre os PROPRIOS dialogos
	 * ({@code JOptionPane} de Nova/Editar conexao) e o PROPRIO menu de
	 * contexto — um {@code JPopupMenu} do Swing nao foi feito pra hospedar
	 * outro popup/dialogo dentro dele (o {@code MenuSelectionManager} so
	 * acompanha UMA cadeia de popup ativa por vez, entao o popup externo se
	 * fechava sozinho assim que o dialogo interno tentava abrir). Bug
	 * relatado pelo usuario na epoca: "no momento nao consigo editar uma
	 * conexao e criar uma nova".
	 */
	/** Lado do {@code anchor} onde o {@link AnchoredPopup} abre — ver {@link AnchoredPopup#toggle(JComponent, JComponent, Placement)}. */
	private enum Placement {
		BELOW, RIGHT
	}

	private final class AnchoredPopup {
		private JDialog window;
		private long closedAt;
		/**
		 * Ultimo tamanho que o USUARIO escolheu arrastando o grip (ver
		 * {@link #buildResizeGrip}), lembrado so nesta sessao (mesmo
		 * principio de {@link #chatDockLoc}, nunca gravado em disco) —
		 * pedido explicito do usuario apos ver nomes de tabela truncados
		 * sem conseguir alargar o popup ("precisa melhorar a
		 * visualizacao, redimensionamento, responsividade"). {@code null}
		 * ate a primeira vez que o usuario arrasta o grip; ate la o popup
		 * usa so o {@code setPreferredSize} que o chamador ja aplicava
		 * (ver {@code #showObjectsPopup} e equivalentes).
		 */
		private Dimension userResizedSize;

		boolean isOpen() {
			return window != null && window.isShowing();
		}

		/**
		 * Alterna: fecha se ja aberto (mesmo comportamento de "toggle" que
		 * um {@link JPopupMenu} ja dava de graca), senao mostra
		 * {@code content} ancorado logo ABAIXO de {@code anchor} — variante
		 * usada pelos botoes da barra de acoes (topo). {@code content} deve
		 * vir com {@code setPreferredSize}/{@code setBorder} JA aplicados
		 * pelo chamador (o tamanho e especifico de cada painel).
		 */
		void toggle(JComponent anchor, JComponent content) {
			toggle(anchor, content, Placement.BELOW);
		}

		/**
		 * Mesmo mecanismo, com o popup ancorado a DIREITA de {@code anchor}
		 * em vez de abaixo — usado pelo rail vertical de icones (ver
		 * {@link #buildLeftSide}), onde os itens ficam empilhados numa
		 * coluna estreita e o popup precisa abrir pro lado, nao por cima do
		 * proximo icone.
		 */
		void toggle(JComponent anchor, JComponent content, Placement placement) {
			if (isOpen()) {
				window.dispose();
				return;
			}
			// Race relatada pelo usuario ("clico de novo deveria fechar, mas
			// abre uma nova"): clicar no PROPRIO anchor pra fechar move o
			// foco da janela ANTES do clique ser entregue ao MouseListener
			// do anchor — o windowLostFocus abaixo ja dispensou o popup (e
			// ja zerou "window", ver windowClosed) quando #toggle e chamado
			// de novo pelo MESMO clique, entao o "if" acima nunca ve o
			// popup como aberto e abriria um novo por cima. Um cooldown bem
			// curto trata "acabou de fechar por perda de foco" como o MESMO
			// gesto de fechar, em vez de reabrir.
			if (System.currentTimeMillis() - closedAt < 250) {
				return;
			}
			JDialog popup = new JDialog(MainWindow.this, false);
			popup.setUndecorated(true);
			// CRITICO: App#main chama JDialog.setDefaultLookAndFeelDecorated(true)
			// (pra janelas DE VERDADE ganharem a titlebar unificada do FlatLaf) —
			// isso faz TODO JDialog, ja na construcao, herdar um windowDecorationStyle
			// que pinta uma titlebar PROPRIA do FlatLaf (icone "N" + botao fechar
			// "X") dentro do rootPane, por CIMA do conteudo. setUndecorated(true)
			// so tira a moldura NATIVA do sistema operacional — nao desliga essa
			// titlebar pintada pelo Swing/FlatLaf, que continuava aparecendo
			// mesmo num popup "undecorated" (bug relatado pelo usuario: "essa
			// lista que abre nao precisa de logo nem dessa barra superior").
			// PLAIN_DIALOG some com ela, deixando so o conteudo de verdade —
			// like uma lista que expande, nao uma janela.
			popup.getRootPane().setWindowDecorationStyle(JRootPane.NONE);
			popup.setLayout(new BorderLayout());
			popup.add(content, BorderLayout.CENTER);
			popup.add(buildResizeGrip(popup), BorderLayout.SOUTH);
			popup.pack();
			// So aplica um tamanho diferente do empacotado (que reflete o
			// setPreferredSize de cada chamador — ver #showObjectsPopup e
			// equivalentes) se o usuario ja tiver arrastado o grip antes
			// nesta sessao — a primeira abertura sempre usa o padrao de
			// cada painel.
			if (userResizedSize != null) {
				popup.setSize(userResizedSize);
			}

			Point anchorLoc = anchor.getLocationOnScreen();
			if (placement == Placement.RIGHT) {
				popup.setLocation(anchorLoc.x + anchor.getWidth(), anchorLoc.y);
			} else {
				popup.setLocation(anchorLoc.x, anchorLoc.y + anchor.getHeight());
			}

			popup.getRootPane().registerKeyboardAction(e -> popup.dispose(),
					KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
			popup.addWindowFocusListener(new WindowAdapter() {
				@Override
				public void windowLostFocus(WindowEvent e) {
					// So fecha se NENHUMA janela filha (ex.: o dialogo de
					// Nova/Editar conexao, ou o menu de contexto quando
					// "pesado" o bastante pra virar janela propria) estiver
					// aberta — checar isso em vez de e.getOppositeWindow()
					// (que costuma vir null em varios ambientes/gerenciadores
					// de janela, fechando o popup na hora errada mesmo com um
					// dialogo filho ativo).
					if (!isChildWindowShowing(popup)) {
						closedAt = System.currentTimeMillis();
						popup.dispose();
					}
				}
			});
			popup.addWindowListener(new WindowAdapter() {
				@Override
				public void windowClosed(WindowEvent e) {
					if (window == popup) {
						window = null;
					}
					// Lembra o tamanho ATUAL (arrastado ou nao) pra proxima
					// abertura desta sessao — ver javadoc de userResizedSize.
					userResizedSize = popup.getSize();
					// O painel volta a nao ter pai nenhum ate a proxima
					// abertura — remove-lo explicitamente evita mante-lo
					// preso a uma janela ja descartada.
					popup.getContentPane().removeAll();
				}
			});

			window = popup;
			popup.setVisible(true);
		}

		void close() {
			if (window != null) {
				window.dispose();
			}
		}

		/**
		 * Faixa fina no rodape do popup com um "grip" diagonal no canto
		 * inferior direito (cursor de redimensionar, arrastavel) — o
		 * {@code JDialog} e {@code setUndecorated(true)} (ver {@link #toggle}),
		 * entao nao tem nenhuma borda nativa pra o usuario arrastar; sem
		 * este grip o popup ficava PRESO no {@code setPreferredSize} que
		 * cada chamador aplicava, mesmo quando o conteudo (ex.: nome de
		 * tabela comprido na arvore de Objetos) precisava de mais espaco.
		 */
		private JComponent buildResizeGrip(JDialog popup) {
			JComponent grip = new JComponent() {
				private static final long serialVersionUID = 1L;

				@Override
				protected void paintComponent(Graphics g) {
					Graphics2D g2 = (Graphics2D) g.create();
					g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
					g2.setColor(GridTheme.MUTED_TEXT);
					for (int i = 1; i <= 3; i++) {
						int off = i * 4;
						g2.drawLine(getWidth() - off, getHeight() - 2, getWidth() - 2, getHeight() - off);
					}
					g2.dispose();
				}
			};
			grip.setPreferredSize(new Dimension(scaledPx(16), scaledPx(16)));
			grip.setOpaque(false);
			grip.setCursor(Cursor.getPredefinedCursor(Cursor.SE_RESIZE_CURSOR));
			grip.setToolTipText("Arraste para redimensionar");

			Point[] pressAnchor = new Point[1];
			Dimension[] pressSize = new Dimension[1];
			MouseAdapter dragHandler = new MouseAdapter() {
				@Override
				public void mousePressed(MouseEvent e) {
					pressAnchor[0] = e.getLocationOnScreen();
					pressSize[0] = popup.getSize();
				}

				@Override
				public void mouseDragged(MouseEvent e) {
					Point now = e.getLocationOnScreen();
					int largura = Math.max(scaledPx(220), pressSize[0].width + (now.x - pressAnchor[0].x));
					int altura = Math.max(scaledPx(200), pressSize[0].height + (now.y - pressAnchor[0].y));
					popup.setSize(largura, altura);
				}
			};
			grip.addMouseListener(dragHandler);
			grip.addMouseMotionListener(dragHandler);

			JPanel stripe = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
			stripe.setOpaque(false);
			stripe.add(grip);
			return stripe;
		}
	}

	/** {@code true} se {@code window} tiver alguma janela PROPRIA (filha) atualmente visivel — ver {@link AnchoredPopup}. */
	private static boolean isChildWindowShowing(Window window) {
		for (Window owned : window.getOwnedWindows()) {
			if (owned.isShowing()) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Janela flutuante com a lista completa de conexoes salvas
	 * ({@link #connectionsPanel}, conectar/desconectar/criar/editar) —
	 * aberta a partir da seta "Conexoes salvas" do {@link ConnectionStatusCard}
	 * (ver {@link #buildLeftSide}). Ver {@link AnchoredPopup} pro mecanismo
	 * (compartilhado com Historico/SQLs/Salvas).
	 */
	private void showConnectionsPopup(JComponent anchor) {
		// Mesma LARGURA do card de conexao (nao mais um valor fixo de
		// 300px): pedido explicito do usuario ("poderia ter um visual
		// melhor... acoplado") — com a mesma largura do card logo acima e
		// zero espaco entre os dois, o popup le como uma EXTENSAO do card
		// (um dropdown), nao como uma janela generica solta na tela.
		// Altura um pouco maior que antes (360->420): a lista de conexoes
		// agora usa cartoes de 2 linhas (ver ConnectionRenderer), mais altos
		// que a linha unica de antes — sem isto, menos linhas cabiam visiveis
		// de cara na mesma altura.
		connectionsPanel.setPreferredSize(new Dimension(Math.max(anchor.getWidth(), scaledPx(260)), scaledPx(420)));
		connectionsPanel.setBorder(BorderFactory.createLineBorder(GridTheme.HEADER_BORDER, 1, true));
		connectionsPopup.toggle(anchor, connectionsPanel);
	}

	/**
	/**
	 * Linha clicavel simples da sidebar unificada (icone + texto) — usada
	 * pelos itens de WORKSPACE/FERRAMENTAS que nao tem um painel proprio pra
	 * embutir (ex.: "Chat com IA", "Backup e Restauracao..."). {@code onClick}
	 * ignorado se {@code enabled} for falso (item placeholder, ver
	 * "Favoritos" em {@link #buildLeftSide}).
	 */
	private JComponent sidebarRow(IconType icon, String text, boolean enabled, String tooltip, Runnable onClick) {
		// Typography.selfStyling (nao "new JLabel(text)" + Typography.secondary/
		// tertiary direto): sem isto, o rotulo ficava com a cor do tema em
		// que a sidebar foi CONSTRUIDA (sempre o escuro, ver App#main)
		// gravada pra sempre — invisivel depois de trocar pro tema claro
		// (bug relatado pelo usuario, com captura mostrando os rotulos do
		// grupo FERRAMENTAS em branco sobre fundo branco).
		JLabel textLabel = Typography.selfStyling(text, label -> {
			if (enabled) {
				Typography.secondary(label);
			} else {
				Typography.tertiary(label);
			}
		});
		return sidebarRow(icon, textLabel, enabled, tooltip, onClick);
	}

	/**
	 * Igual a {@link #sidebarRow(IconType, String, boolean, String, Runnable)},
	 * mas recebendo o {@link JLabel} pronto — usado pela linha "SQL Editors"
	 * (ver {@link #buildLeftSide}), cujo texto precisa ser atualizado depois
	 * (contador de abas, ver {@link #refreshSqlEditorsCount}).
	 */
	private JComponent sidebarRow(IconType icon, JLabel textLabel, boolean enabled, String tooltip, Runnable onClick) {
		JLabel iconLabel = new JLabel();
		Buttons.bindThemedIcon(iconLabel, icon, 15, () -> enabled ? GridTheme.HEADER_FOREGROUND : GridTheme.MUTED_TEXT);
		JPanel row = new JPanel(new BorderLayout(Spacing.SM, 0));
		row.setOpaque(false);
		row.setBorder(BorderFactory.createEmptyBorder(6, Spacing.SM, 6, Spacing.SM));
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.add(iconLabel, BorderLayout.WEST);
		row.add(textLabel, BorderLayout.CENTER);
		row.setCursor(Cursor.getPredefinedCursor(enabled ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
		if (tooltip != null) {
			row.setToolTipText(tooltip);
		}
		if (enabled && onClick != null) {
			row.addMouseListener(new MouseAdapter() {
				@Override
				public void mouseClicked(MouseEvent e) {
					onClick.run();
				}
			});
		}
		return row;
	}

	/**
	 * Reflete {@link #realTabCount()} no rotulo "SQL Editors (N)" — chamado
	 * por {@link #updateWorkspaceContextBar} (mesmo hook de toda troca de
	 * aba) e uma vez direto em {@link #buildLeftSide}, ANTES de
	 * {@link #editorTabs} existir (a lateral e montada antes da area de
	 * abas) — guard contra {@code null} cobre esse caso; o rotulo comeca com
	 * "0" ate a primeira {@link #updateWorkspaceContextBar} de verdade.
	 */
	private void refreshSqlEditorsCount() {
		if (sqlEditorsCountLabel != null && editorTabs != null) {
			sqlEditorsCountLabel.setText("SQL Editors (" + realTabCount() + ")");
		}
		refreshSqlEditorsList();
	}

	/** Repopula {@link #sqlEditorsListModel} a partir de {@link #editorTabs} (exclui a aba "+") e preserva a selecao da aba ativa. */
	private void refreshSqlEditorsList() {
		if (sqlEditorsList == null || editorTabs == null) {
			return;
		}
		Component selected = editorTabs.getSelectedComponent();
		sqlEditorsListModel.clear();
		for (int i = 0; i < editorTabs.getTabCount(); i++) {
			Component c = editorTabs.getComponentAt(i);
			if (c != plusTab) {
				sqlEditorsListModel.addElement(c);
			}
		}
		if (selected != null && selected != plusTab) {
			sqlEditorsList.setSelectedValue(selected, false);
		}
	}

	/**
	 * Renderiza cada linha da lista de abas de SQL: titulo + dot de status
	 * (mesmo icone ja calculado por {@link #editorTabs}.setIconAt em
	 * {@link #updateWorkspaceContextBar}, reaproveitado em vez de recalculado)
	 * — negrito na aba ATUALMENTE selecionada no editor, pro usuario saber
	 * qual delas esta na tela sem precisar clicar em cada uma.
	 */
	private final class SqlEditorsListRenderer extends DefaultListCellRenderer {
		private static final long serialVersionUID = 1L;

		@Override
		public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected,
				boolean cellHasFocus) {
			super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
			setBorder(BorderFactory.createEmptyBorder(4, 12, 4, 12));
			setIconTextGap(10);
			boolean hovered = !isSelected && index == TreeHoverTracker.hoverRow(list);
			if (hovered) {
				setOpaque(true);
				setBackground(GridTheme.HOVER_BACKGROUND);
			}
			if (value instanceof Component tabComponent) {
				int idx = editorTabs.indexOfComponent(tabComponent);
				if (idx >= 0) {
					setText(editorTabs.getTitleAt(idx));
					setIcon(editorTabs.getIconAt(idx));
				}
				boolean active = tabComponent == editorTabs.getSelectedComponent();
				setFont(getFont().deriveFont(active ? Font.BOLD : Font.PLAIN));
			}
			return this;
		}
	}

	/**
	 * Foca a busca "Buscar objeto..." da arvore de Objetos (Ctrl+K) —
	 * reabre a lateral primeiro se estiver escondida (Ctrl+B). Ctrl+K
	 * apontava pra uma busca UNIFICADA propria da sidebar antes (encaminhava
	 * o texto pro campo de busca de cada painel embutido) — removida
	 * (pedido explicito do usuario: "esse pode remover"), era pura
	 * duplicacao ja que Objetos/Conexoes/Salvas/Historico sempre tiveram
	 * cada um o SEU PROPRIO campo de busca, sempre visivel assim que o
	 * painel/popup em questao abre. Ctrl+K agora vai direto pro campo que
	 * sobrou fixo na tela (Objetos); os outros 3 tem busca visivel assim
	 * que abertos, sem precisar de atalho.
	 */
	private void focusObjectSearch() {
		if (leftSide != null && !leftSide.isVisible()) {
			toggleSidebar();
		}
		// "Objetos" agora e conteudo de popup (ver #showObjectsPopup), nao
		// mais um painel sempre visivel — abre (ou mantem aberto, #toggle
		// so fecha se ja estava aberto) antes de focar a busca.
		if (!objectsPopup.isOpen()) {
			showObjectsPopup(iconRail.anchorFor("objetos"));
		}
		objectExplorer.focusSearch();
	}

	// ---------- Editor (abas) ----------

	/**
	 * Cria um {@link JTabbedPane} de terminais SQL NOVO, com os mesmos
	 * listeners/estilo de sempre — usado uma vez por CONEXAO (ver
	 * {@code Conexao#ownEditorTabs}), nao mais uma unica vez pra IDE
	 * inteira: cada conexao ganha o seu proprio, construido na primeira
	 * ativacao (ver {@link #activateWorkspace}) e nunca destruido depois,
	 * pra trocar de aba de conexao nao perder o estado dos terminais.
	 */
	private JTabbedPane newTerminalTabsPane() {
		JTabbedPane pane = new JTabbedPane();
		// Padrao do Swing (WRAP_TAB_LAYOUT) empilha as abas em VARIAS linhas
		// quando nao cabem mais numa so — pedido explicito do usuario para
		// manter SEMPRE uma unica linha, com setinhas de rolagem (estilo
		// navegador/VS Code) quando ha abas demais para caber.
		pane.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
		// Sem isto, o FlatLaf reserva um respiro antes da primeira aba (a area
		// de abas tem um inset esquerdo proprio, independente do painel que a
		// contem) — a primeira aba ficava alguns pixels mais a direita que o
		// botao "Executar" da barra logo acima, mesmo os dois partindo de
		// x=0 no layout. Zerar o inset alinha a aba com a barra de ferramentas.
		pane.putClientProperty("JTabbedPane.tabAreaInsets", new Insets(0, 0, 0, 0));
		// Mesma largura minima das abas de Resultados (ver resultTabs abaixo)
		// — sem isto, abrir varias queries deixava as abas do editor
		// afinarem muito mais que as de Resultados, uma inconsistencia de
		// tamanho entre as duas areas de abas mais usadas do app.
		pane.putClientProperty("JTabbedPane.minimumTabWidth", 96);
		pane.putClientProperty("JTabbedPane.tabClosable", true);
		pane.putClientProperty("JTabbedPane.tabCloseCallback",
				(BiConsumer<JTabbedPane, Integer>) (k, index) -> closeQueryTab(index));
		// Selecionar a aba "+" abre uma nova query; qualquer outra troca salva a
		// sessao E redesenha RESULTADOS com o que essa aba tinha da ultima
		// vez (cada aba de SQL tem seus proprios resultados — ver
		// resultsByTab/showResultsForActiveEditor). Le sempre os CAMPOS
		// editorTabs/plusTab (nao esta variavel local "pane") de proposito:
		// so dispara de verdade quando este pane e o ATIVO no momento (ver
		// invariante em #activateWorkspace — os campos so apontam pra um
		// pane que acabou de ser mutado/selecionado), entao os dois sempre
		// concordam quando isto roda.
		pane.addChangeListener(e -> {
			if (addingTab) {
				return; // evita reentrancia: insertTab desloca a selecao da aba "+"
			}
			if (plusTab != null && editorTabs.getSelectedComponent() == plusTab) {
				if (!addQueryTab()) {
					selectLastRealTab();
				}
			} else {
				if (editorTabs.getSelectedComponent() instanceof SqlEditorPane sep) {
					// Ultima aba de SQL com foco de verdade — usada pelos presets
					// do Chat (ver #activeOrLastSqlForPresets) pra achar o SQL
					// "ativo" quando o dock do Chat esta aberto mas nenhuma aba
					// de SQL tem foco no momento (nesse caso currentEditor()
					// sozinho devolveria null).
					lastActiveEditor = sep;
				}
				scheduleSave();
				resultsController.showResultsForActiveEditor();
				updateSaveButtonState();
				refreshRunButtonState();
				refreshExecutingOverlay();
			}
		});
		// Botao direito no titulo da aba: fechar / fechar as outras.
		pane.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				maybeTabMenu(e);
			}

			@Override
			public void mouseReleased(MouseEvent e) {
				maybeTabMenu(e);
			}
		});
		return pane;
	}

	private JComponent buildEditorArea() {
		connectionTabs = new JTabbedPane();
		// Mesmo motivo do editorTabs (ver #newTerminalTabsPane): uma unica
		// linha de abas, com rolagem, nunca empilhada em varias linhas.
		connectionTabs.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
		connectionTabs.putClientProperty("JTabbedPane.tabAreaInsets", new Insets(0, 0, 0, 0));
		// Sem override de tabType aqui: herda "underlined" do FlatLaf.properties
		// (redesenho "novo e leve", Fase 6) — mesma linguagem visual do
		// restante das abas do app.
		// Fechavel (pedido explicito do usuario) — exceto a aba "Sem conexao"
		// em si, que nunca fecha (ver #ensureConnectionTab, que marca o painel
		// dela com tabClosable=false individualmente, mesmo truque ja usado
		// pela aba "+" do editor).
		connectionTabs.putClientProperty("JTabbedPane.tabClosable", true);
		connectionTabs.putClientProperty("JTabbedPane.tabCloseCallback",
				(BiConsumer<JTabbedPane, Integer>) (k, index) -> closeConnectionTab(index));
		// Trocar de aba de CONEXAO reativa o workspace correspondente (mesmo
		// caminho de ativacao usado pela lista lateral de conexoes, ver
		// #activateWorkspace) — reentrancia evitada por switchingConnectionTab,
		// ja que #activateWorkspace TAMBEM seleciona a aba de conexao certa
		// (ver #ensureConnectionTab), o que disparia este mesmo listener de
		// volta sem o guard.
		connectionTabs.addChangeListener(e -> {
			if (switchingConnectionTab) {
				return;
			}
			if (connectionTabs.getSelectedComponent() == connectionPlusTab) {
				// Aba "+": nao e um workspace de verdade — abre o seletor de
				// conexoes (pedido explicito do usuario, mesmo botao "+" que
				// ja existe nas abas de terminal) e devolve a selecao pra aba
				// que estava ativa antes, caso o usuario cancele sem conectar
				// nem criar nada (senao a propria aba "+" ficaria "selecionada"
				// sozinha, sem nenhum workspace de verdade por tras dela).
				Conexao previousActive = activeWorkspace;
				promptConnectionSelection();
				if (connectionTabs.getSelectedComponent() == connectionPlusTab) {
					reselectConnectionTab(previousActive);
				}
				return;
			}
			Conexao w = workspaceForPanel(connectionTabs.getSelectedComponent());
			if (w != null && w != activeWorkspace) {
				activateWorkspace(w);
			}
		});
		// Aba "+" (nao fechavel, sempre por ultimo) — abre o seletor de
		// conexoes salvas, mesmo padrao ja usado pela aba "+" do editor (ver
		// #addPlusTab). Precisa existir ANTES de #initWorkspaces/qualquer
		// #ensureConnectionTab, que passam a inserir as abas de conexao de
		// verdade sempre ANTES dela (ver #ensureConnectionTab).
		JPanel plusDummy = new JPanel();
		plusDummy.putClientProperty("JTabbedPane.tabClosable", false);
		connectionPlusTab = plusDummy;
		// Guardado com switchingConnectionTab: esta e a PRIMEIRA aba (0->1),
		// e o JTabbedPane seleciona (e dispara ChangeEvent) automaticamente
		// ao adicionar a primeira aba — sem o guard, o ChangeListener via
		// "aba + selecionada" e chamava promptConnectionSelection() no MEIO
		// da construcao da janela, bem antes de buildToolbar() existir
		// (connectionCard ainda null) — NullPointerException relatada pelo
		// usuario logo no arranque.
		switchingConnectionTab = true;
		try {
			connectionTabs.addTab("+", plusDummy);
		} finally {
			switchingConnectionTab = false;
		}
		connectionTabs.setToolTipTextAt(connectionTabs.indexOfComponent(plusDummy), "Conectar/nova conexao");

		// Inicializa o workspace "sem conexao" com as abas salvas (+ aba "+"),
		// como a primeira aba de conexao.
		initWorkspaces();
		// Dot inicial nas abas ja criadas por initWorkspaces() acima — ver
		// updateWorkspaceContextBar() (chamada de novo a cada troca de estado
		// de conexao, alem de ao criar/restaurar abas).
		updateWorkspaceContextBar();

		// A barra de acoes (Executar/Formatar/.../conexao/SQL/icones) deixou
		// de ficar aqui (NORTH deste painel, so acima da area do EDITOR) —
		// pedido explicito do usuario: em janelas estreitas, varios
		// componentes dela sumiam (GridBagLayout ficando sem espaco pra
		// tantos itens espremidos so na largura da coluna do editor,
		// sidebar de fora). Virou o NORTH da JANELA INTEIRA (ver
		// #buildUi), ganhando a largura da sidebar tambem — ver
		// #buildToolbar.
		JPanel panel = new JPanel(new BorderLayout());
		panel.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));
		panel.add(connectionTabs, BorderLayout.CENTER);
		return panel;
	}

	/** A {@link Conexao} dona do painel de nivel superior informado (ver {@code Conexao#ownPanel}), ou {@code null}. */
	private Conexao workspaceForPanel(Component panel) {
		for (Conexao w : workspaces.values()) {
			if (w.ownPanel == panel) {
				return w;
			}
		}
		return null;
	}

	/**
	 * Garante que {@code w} tem uma aba na tira de conexoes (cria na
	 * primeira vez, ver {@code Conexao#ownPanel}) e a deixa selecionada —
	 * chamado sempre no final de {@link #activateWorkspace}, entao a UI de
	 * abas de conexao sempre reflete o workspace ativo, tanto quando o
	 * usuario clica numa aba de conexao diretamente quanto quando a troca
	 * vem de outro lugar (lista lateral, conectar, desconectar).
	 */
	private void ensureConnectionTab(Conexao w) {
		if (w.ownPanel == null) {
			JPanel p = new JPanel(new BorderLayout());
			p.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));
			p.add(w.ownEditorTabs, BorderLayout.CENTER);
			if (SCRATCH.equals(w.name())) {
				// Mesmo truque da aba "+" do editor (ver #addPlusTab): marca o
				// PAINEL desta aba especifica como nao-fechavel, mesmo com
				// "JTabbedPane.tabClosable"=true no nivel da tira inteira.
				p.putClientProperty("JTabbedPane.tabClosable", false);
			}
			w.ownPanel = p;
			// insere ANTES da aba "+" (mesma regra de #addQueryTab pra
			// editorTabs): ela precisa continuar sendo sempre a ULTIMA.
			int at = (connectionPlusTab != null) ? connectionTabs.indexOfComponent(connectionPlusTab)
					: connectionTabs.getTabCount();
			// Guardado com switchingConnectionTab: inserir ANTES da aba "+"
			// (que estava selecionada, ver acima) faz o proprio JTabbedPane
			// reajustar o INDICE selecionado internamente pra continuar
			// apontando pra ela (0 -> 1, por exemplo) — isso dispara um
			// ChangeEvent SINCRONO aqui dentro do insertTab, antes do
			// #ensureConnectionTab reselecionar a aba certa mais abaixo. Sem
			// o guard, o ChangeListener via "+" ainda selecionada e reabria
			// o seletor de conexao LOGO APOS a primeira conexao bem-sucedida
			// (bug relatado pelo usuario).
			switchingConnectionTab = true;
			try {
				connectionTabs.insertTab(connectionTabLabel(w), null, p, null, at);
			} finally {
				switchingConnectionTab = false;
			}
		} else {
			int idx = connectionTabs.indexOfComponent(w.ownPanel);
			if (idx >= 0) {
				connectionTabs.setTitleAt(idx, connectionTabLabel(w));
			}
		}
		int idx = connectionTabs.indexOfComponent(w.ownPanel);
		if (idx >= 0 && connectionTabs.getSelectedIndex() != idx) {
			switchingConnectionTab = true;
			try {
				connectionTabs.setSelectedIndex(idx);
			} finally {
				switchingConnectionTab = false;
			}
		}
	}

	private static String connectionTabLabel(Conexao w) {
		return SCRATCH.equals(w.name()) ? "Sem conexao" : w.name();
	}

	/** Reseleciona a aba de {@code w} (se ainda tiver uma) — ver uso na aba "+" de {@link #buildEditorArea}. */
	private void reselectConnectionTab(Conexao w) {
		if (w == null || w.ownPanel == null) {
			return;
		}
		int idx = connectionTabs.indexOfComponent(w.ownPanel);
		if (idx >= 0) {
			switchingConnectionTab = true;
			try {
				connectionTabs.setSelectedIndex(idx);
			} finally {
				switchingConnectionTab = false;
			}
		}
	}

	/**
	 * Fecha uma aba de CONEXAO (botao "x" na tira de abas, pedido explicito
	 * do usuario) — desconecta (se conectada), fecha a conexao JDBC dedicada
	 * de cada terminal dela, remove o workspace da lista viva e a propria
	 * aba. O SQL de cada terminal continua salvo em disco (ver
	 * {@code SessionStore}) e volta a aparecer se o usuario conectar de novo
	 * a esta mesma conexao — "fechar a aba" nao apaga nada, so tira da tela.
	 * A aba "Sem conexao" nunca chega aqui (ver {@link #ensureConnectionTab},
	 * que a marca como nao-fechavel).
	 */
	private void closeConnectionTab(int index) {
		Component target = connectionTabs.getComponentAt(index);
		Conexao w = workspaceForPanel(target);
		if (w == null || SCRATCH.equals(w.name())) {
			return;
		}
		int ok = JOptionPane.showConfirmDialog(this,
				"Fechar a conexao \"" + w.name() + "\"?\n\nOs terminais desta conexao serao fechados — o SQL de cada "
						+ "aba continua salvo e volta a aparecer da proxima vez que voce conectar.",
				"Fechar conexao", JOptionPane.YES_NO_OPTION);
		if (ok != JOptionPane.YES_OPTION) {
			return;
		}
		if (w.ownEditorTabs != null) {
			for (int i = 0; i < w.ownEditorTabs.getTabCount(); i++) {
				if (w.ownEditorTabs.getComponentAt(i) instanceof SqlEditorPane sep) {
					closeTerminalConnection(sep);
					resultsController.forgetTab(sep);
				}
			}
		}
		w.mgr.close();
		workspaces.remove(w.name);
		connectionTabs.remove(target);
		if (activeWorkspace == w) {
			activateWorkspace(workspaces.get(SCRATCH));
		}
		refreshConnectionIndicators();
		statusBar.setText(" Conexao \"" + w.name() + "\" fechada.");
		// Fechou a ULTIMA conexao aberta (so sobrou a aba "+"): mesma regra
		// do arranque — pedido explicito do usuario ("nao existe essa aba
		// sem conexao, quando nao houver nenhuma conexao aberta abra uma
		// caixa de selecao").
		if (connectionTabs.getTabCount() <= 1) {
			promptConnectionSelection();
		}
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
				this::toggleEditorFocusMode,
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

	/** Numero de abas de SQL (exclui "+"; o Chat nao e mais uma aba, ver {@link #chatDock}). */
	private int realTabCount() {
		int count = editorTabs.getTabCount();
		if (plusTab != null) {
			count--;
		}
		return count;
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
			if (c instanceof SqlEditorPane sep) {
				// Mesma limpeza de closeQueryTab: os resultados de uma aba
				// fechada nao fazem mais sentido sem ela (ver resultsByTab).
				resultsController.forgetTab(sep);
				closeTerminalConnection(sep);
			}
			editorTabs.removeTabAt(i);
		}
		// A aba mantida (keep) JA estava selecionada ANTES do loop acima —
		// remover as outras nunca muda o indice selecionado, entao NENHUM
		// ChangeEvent dispara (e e o ChangeListener de editorTabs, ver
		// buildEditorArea, quem normalmente aciona refreshSqlEditorsCount).
		// Sem esta chamada explicita, a lista lateral "SQL" (ver
		// sqlEditorsListModel) ficava com referencias fantasma aos
		// componentes ja removidos: indexOfComponent devolvia -1 pra eles e o
		// renderer caia no toString() padrao do Swing em vez do titulo da
		// aba — bug relatado pelo usuario (linhas tipo
		// "com.nureal.ide.ui.SqlEditorPane[,0,33,...").
		refreshSqlEditorsCount();
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
			closeTerminalConnection(sep);
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
		scratch.ownEditorTabs = newTerminalTabsPane();
		editorTabs = scratch.ownEditorTabs;
		plusTab = null;
		rebuildEditorTabs(scratch.tabs, scratch.selectedTab, scratch.tabResults);
		scratch.ownPlusTab = plusTab;
		// Sem ensureConnectionTab(scratch) aqui — a aba "Sem conexao" nao
		// aparece mais na tira de saida (pedido explicito do usuario);
		// #promptConnectionSelection, chamado logo apos a janela terminar de
		// montar (ver construtor), e quem decide se ela precisa aparecer
		// como ultimo recurso (ver #ensureScratchTabFallback).
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
				closeTerminalConnection(sep);
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
		if (w.ownEditorTabs == null) {
			// Primeira vez que esta conexao fica ativa nesta sessao: constroi
			// os terminais dela do zero (a partir do salvo/restaurado, ver
			// Conexao#tabs) — so acontece uma vez por conexao.
			w.ownEditorTabs = newTerminalTabsPane();
			editorTabs = w.ownEditorTabs;
			plusTab = null;
			rebuildEditorTabs(w.tabs, w.selectedTab, w.tabResults);
			w.ownPlusTab = plusTab;
		} else {
			// Conexao ja tinha sido ativada antes nesta sessao: os terminais
			// dela continuam vivos (texto, resultados, conexao JDBC dedicada
			// de cada um) — so reponta os campos "atalho" pra eles, SEM
			// destruir/recriar nada (pedido explicito do usuario: trocar de
			// aba de conexao e voltar nao pode perder o que estava rodando
			// ali).
			editorTabs = w.ownEditorTabs;
			plusTab = w.ownPlusTab;
			if (w.selectedTab >= 0 && w.selectedTab < editorTabs.getTabCount()
					&& editorTabs.getComponentAt(w.selectedTab) != plusTab) {
				editorTabs.setSelectedIndex(w.selectedTab);
			}
			resultsController.showResultsForActiveEditor();
		}
		// "Sem conexao" nunca ganha aba propria so por ser ativada (ver
		// #promptConnectionSelection/#ensureScratchTabFallback, os UNICOS
		// lugares que decidem se ela precisa aparecer como ultimo recurso) —
		// sem este guard, qualquer caminho que reative o workspace SCRATCH
		// internamente (ex.: #closeConnectionTab caindo de volta pra ele)
		// a traria de volta pra tira de abas sempre, contrariando o pedido
		// explicito do usuario ("nao existe essa aba sem conexao").
		if (!SCRATCH.equals(w.name())) {
			ensureConnectionTab(w);
		}
		if (w.schema != null) {
			metadataCache.set(w.schema);
			completionProvider.refresh(w.loadedSchemas.values(), w.schema.name());
			objectExplorer.populateTree(w.schema);
		} else if (w.schemaList != null) {
			completionProvider.refresh(w.loadedSchemas.values(), null);
			objectExplorer.buildSchemaPicker(w.schemaList);
		} else {
			setCurrentSchema(null);
			completionProvider.refresh(w.loadedSchemas.values(), null);
			String label = (w.profile == null) ? "Sem conexao"
					: (w.mgr.isConnected() ? "Selecione um esquema" : "Desconectado");
			objectExplorer.showDisconnected(label);
		}
		refreshConnectionIndicators();
		refreshRunButtonState();
		refreshExecutingOverlay();
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
	ConexaoAtivaPort connectionManager() {
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

	MetadataRepository metadataService() {
		return metadataService;
	}

	MetadataCache metadataCache() {
		return metadataCache;
	}

	TableMetadataCache tableMetadataCache() {
		return tableMetadataCache;
	}

	SqlCompletionProviderRSyntax completionProvider() {
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
			activeWorkspace.setSchema(schema);
		}
	}

	/** Atualiza as bolinhas (conectados), o card de conexao ativa e o indicador de status do rodape. */
	private void refreshConnectionIndicators() {
		Set<String> connected = new HashSet<>();
		List<ConnectionStatusCard.ActiveConnection> activeConnections = new ArrayList<>();
		for (Conexao w : workspaces.values()) {
			if (w.profile != null && w.mgr.isConnected()) {
				connected.add(w.name);
				activeConnections.add(new ConnectionStatusCard.ActiveConnection(w.name, activeConnectionLabel(w)));
			}
		}
		String activeName = (activeWorkspace != null && activeWorkspace.profile != null) ? activeWorkspace.name : null;
		connectionsPanel.setConnectedNames(connected);
		connectionsPanel.setActiveName(activeName);
		connectionCard.setActiveConnections(activeConnections, activeName);
		if (activeWorkspace != null && activeWorkspace.profile != null && activeWorkspace.mgr.isConnected()) {
			setConnectedState(activeWorkspace.profile.name());
		} else {
			setDisconnectedState();
		}
	}

	/** Rotulo "nome — usuario@host" de uma conexao conectada, pro dropdown de troca rapida (ver ConnectionStatusCard). */
	private static String activeConnectionLabel(Conexao w) {
		if (w.profile == null) {
			return w.name;
		}
		return w.name + "  —  " + w.profile.user() + "@" + w.profile.host();
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
	// Ver com.nureal.ide.modulos.iachat.apresentacao.ChatWindow/ChatController/DefaultAgent. Toda a
	// logica de IA fica em modulos.iachat (Swing-free); aqui so montamos o grafo de
	// objetos (Provider/ToolExecutor/ContextProvider/Agent) a partir dos
	// servicos que o MainWindow ja tem, via IdeContextAccessor.

	/** Uma unica conversa persistente para o MVP — sem seletor de conversas ainda. */
	private static final String AI_CONVERSATION_ID = "default";

	/**
	 * Abre (ou mostra, se ja construido) o dock do Chat de IA na borda
	 * direita da janela (ver {@link #chatSplit}/{@link #chatDock}) — pedido
	 * explicito do usuario na revisao de UX ("chat abrindo em aba do editor
	 * ao inves de abrir uma janela na direita"). Antes o Chat era mais uma
	 * aba dentro de {@link #editorTabs} (Fase 2 do AI-CHAT-MASTER-PLAN.md);
	 * agora e um painel proprio, sempre no mesmo lugar quando aberto, sem
	 * competir por espaco com as abas de SQL. Fechar o dock (botao "X" do
	 * seu cabecalho, ver {@link #buildChatDockHeader}) so ESCONDE o painel
	 * — {@link #chatWindow} continua vivo (conversa em memoria preservada);
	 * reabrir o dock mostra exatamente onde parou, sem recarregar nada.
	 */
	private void openAiChat() {
		if (chatWindow == null) {
			AiPreferences aiPreferences = new AiPreferences();
			AiCredentialsStore aiCredentials = new AiCredentialsStore();
			chatHistoryStore = new ChatHistoryStore();
			Agent agent = buildAiAgent(aiPreferences, aiCredentials, chatHistoryStore);
			ChatActions actions = new ChatActions(this::runSqlFromChat, this::currentSqlFormatter, sql -> { },
					this::activeOrLastSqlForPresets);
			chatWindow = new ChatWindow(agent, chatHistoryStore, AI_CONVERSATION_ID,
					() -> openAiSettings(aiPreferences, aiCredentials), actions);
			wireChatToolbar(chatWindow, aiPreferences, aiCredentials);

			chatDock.removeAll();
			chatDock.add(buildChatDockHeader(), BorderLayout.NORTH);
			chatDock.add(chatWindow.component(), BorderLayout.CENTER);
		}
		setChatDockVisible(true);
	}

	/**
	 * Cabecalho minimo do dock do Chat — so o botao de fechar. O resto do
	 * cabecalho "de verdade" (combo de modelo, "+ Novo Chat", selo de
	 * esquema) ja vem de dentro do proprio {@code ChatPanel}, ver
	 * {@link #wireChatToolbar}.
	 */
	private JComponent buildChatDockHeader() {
		JButton close = Buttons.iconButton(IconType.CLOSE, 13, () -> GridTheme.MUTED_TEXT);
		close.setToolTipText("Fechar chat");
		close.addActionListener(e -> setChatDockVisible(false));
		JPanel row = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 4));
		row.setOpaque(false);
		row.add(close);
		return row;
	}

	/**
	 * Mostra/esconde o dock do Chat (ver {@link #chatSplit}), lembrando a
	 * ultima largura escolhida pelo usuario (mesmo principio de
	 * {@link #resultsLoc}) — largura padrao na 1a
	 * abertura: {@link #CHAT_DOCK_DEFAULT_WIDTH}px a partir da borda direita.
	 */
	private void setChatDockVisible(boolean visible) {
		if (chatDock.isVisible() == visible) {
			return;
		}
		if (visible) {
			chatDock.setVisible(true);
			chatSplit.setDividerSize(4);
			int total = chatSplit.getWidth();
			if (chatDockLoc > 0) {
				chatSplit.setDividerLocation(chatDockLoc);
			} else if (total > 0) {
				chatSplit.setDividerLocation(Math.max(200, total - CHAT_DOCK_DEFAULT_WIDTH));
			} else {
				// Janela ainda sem largura de verdade (dock aberto antes do 1o
				// layout acontecer) — fracao serve so de fallback ate o proximo
				// resize recalcular a posicao certa.
				chatSplit.setDividerLocation(0.7);
			}
		} else {
			chatDockLoc = chatSplit.getDividerLocation();
			chatDock.setVisible(false);
			chatSplit.setDividerSize(0);
			chatSplit.setDividerLocation(chatSplit.getWidth());
		}
		chatSplit.revalidate();
	}

	/**
	 * Combo de modelo (mostra o atual de imediato; substitui pela lista real
	 * assim que {@code provider.listModels()} responder, em segundo plano),
	 * "+ Novo Chat" e o selo de esquema ativo — ver
	 * {@code ChatPanel#setModelOptions}/{@code #setOnNewChat}/{@code #setSchemaLabel}.
	 */
	private void wireChatToolbar(ChatWindow window, AiPreferences aiPreferences, AiCredentialsStore aiCredentials) {
		AiPreferences.State prefs;
		try {
			prefs = aiPreferences.load();
		} catch (IOException e) {
			prefs = AiPreferences.State.defaults();
		}
		String currentModel = prefs.model();
		window.setModelOptions(currentModel.isBlank() ? List.of() : List.of(currentModel), currentModel);
		window.setSchemaLabel(chatSchemaLabel());
		window.setOnNewChat(window::startNewConversation);
		window.setOnModelChange(model -> onChatModelChanged(aiPreferences, aiCredentials, model));

		LLMProvider provider = LLMProviderFactory.create(prefs, aiCredentials);
		new SwingWorker<List<String>, Void>() {
			@Override
			protected List<String> doInBackground() {
				return provider.listModels();
			}

			@Override
			protected void done() {
				try {
					List<String> models = get();
					if (!models.isEmpty()) {
						window.setModelOptions(models, currentModel);
					}
				} catch (Exception ignored) {
					// Falha ao listar (rede/credencial) nao e bloqueante aqui —
					// o combo continua mostrando so o modelo ja configurado,
					// que ja funciona; erro de verdade (modelo inexistente)
					// aparece ao mandar a primeira mensagem, como antes desta fase.
				}
			}
		}.execute();
	}

	/** Usuario trocou o modelo no combo do chat: persiste e reconstroi o Agent (mesmo caminho de "Configuracoes de IA"). */
	private void onChatModelChanged(AiPreferences aiPreferences, AiCredentialsStore aiCredentials, String model) {
		try {
			AiPreferences.State current = aiPreferences.load();
			aiPreferences.save(new AiPreferences.State(current.provider(), current.baseUrl(), model,
					current.temperature(), current.timeoutSeconds(), current.streamingEnabled()));
		} catch (IOException e) {
			AppLogger.warning("Falha ao salvar o modelo escolhido no chat", e);
			return;
		}
		if (chatWindow != null) {
			chatWindow.updateAgent(buildAiAgent(aiPreferences, aiCredentials, chatHistoryStore));
		}
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
		AiSettingsDialog.open(this, aiPreferences, aiCredentials, () -> {
			if (chatWindow != null) {
				chatWindow.updateAgent(buildAiAgent(aiPreferences, aiCredentials, chatHistoryStore));
			}
		});
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
				List.of(new ListTablesTool(accessor), new DescribeTableTool(accessor), new ExecuteSqlTool(accessor)));
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
		ConexaoAtivaPort mgr = connectionManager();
		if (mgr == null || !mgr.isConnected() || mgr.profile() == null) {
			return null;
		}
		ConnectionProfile p = mgr.profile();
		return p.host() + ":" + p.port() + "/" + p.schema() + " (" + p.user() + ")";
	}

	private String currentEditorSqlForAi() {
		return sqlOf(currentEditor());
	}

	/**
	 * SQL da aba de SQL ATIVA, ou (se nenhuma aba de SQL tem foco no momento
	 * — o dock do Chat, ver {@link #chatDock}, nao rouba a selecao de
	 * {@link #editorTabs}, entao isso so acontece se o usuario nunca abriu
	 * nenhuma aba de SQL nesta sessao) a ULTIMA com foco antes disso. Sem
	 * isto, os presets de prompt (ver {@code ChatPanel}) nunca achariam SQL
	 * nenhum nesse caso, ja que {@link #currentEditor()} sozinho devolveria
	 * {@code null}.
	 */
	private String activeOrLastSqlForPresets() {
		SqlEditorPane editor = currentEditor();
		return sqlOf(editor != null ? editor : lastActiveEditor);
	}

	private static String sqlOf(SqlEditorPane editor) {
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
		ConexaoAtivaPort mgr = connectionManager();
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
		ConexaoAtivaPort mgr = connectionManager();
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
		// ConnectionsPanel/HistoryPanel/SavedQueriesPanel/sqlEditorsPanel (os
		// 4 conteudos dos popups de navegacao, ver AnchoredPopup) so ficam
		// dentro de uma janela de verdade ENQUANTO o proprio popup esta
		// aberto — o resto do tempo, seu ULTIMO parent e um JDialog ja
		// descartado, fora de Window.getWindows(). FlatLaf.updateUI() so
		// varre janelas ATIVAS, entao nunca alcancava esses paineis: a
		// proxima vez que o usuario abria QUALQUER um dos 4 popups,
		// continuava 100% no tema ANTERIOR (fundo, selecao, lista inteira)
		// — bug relatado pelo usuario com captura de tela ("conexoes nao
		// mudou junto"), que so tinha sido corrigido pra Conexoes ate agora
		// — Historico/SQLs/Salvas MIGRARAM pro mesmo modelo de popup
		// DEPOIS desse fix (ver o piloto "navegacao em popup ancorado"),
		// entao caiam no MESMO buraco sem ninguem ter notado ainda. Reaplica
		// manualmente aqui, igual FlatLaf faria se os paineis estivessem
		// numa janela visivel.
		javax.swing.SwingUtilities.updateComponentTreeUI(connectionsPanel);
		javax.swing.SwingUtilities.updateComponentTreeUI(historyPanel);
		javax.swing.SwingUtilities.updateComponentTreeUI(savedQueriesPanel);
		javax.swing.SwingUtilities.updateComponentTreeUI(sqlEditorsPanel);
		// "Objetos" migrou pro mesmo modelo de popup ancorado dos outros 4
		// (ver #showObjectsPopup) — mesma classe de bug ja corrigida acima:
		// sem isto, o autofiltro/arvore ficava preso no tema de quando o
		// popup foi aberto pela ultima vez.
		if (objectsBrowserPanel != null) {
			javax.swing.SwingUtilities.updateComponentTreeUI(objectsBrowserPanel);
		}
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
		// mensagem foi renderizada — sem isto, a aba do chat (se estiver
		// aberta) mantinha cards do tema antigo ate a proxima mensagem.
		if (chatWindow != null) {
			chatWindow.refreshTheme();
		}
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
						ws.setSchema(null);
					} else {
						ws.setSchema((SchemaInfo) result);
						ws.schemaList = null;
					}
					activateWorkspace(ws);
					if (pickSchema) {
						statusBar.setText(
								" Conectado  (" + ((List<?>) result).size() + " esquema(s) - duplo-clique para abrir)");
						// Sem schema resolvido ainda, nao da pra RODAR a instrucao
						// sozinho (falta contexto) — mas o callback ainda roda (ver
						// #runNewTabWithSql, unico chamador com onConnected != null
						// hoje), que sabe deixar o SQL numa aba nova em vez de
						// perde-lo, mesmo sem poder executar ainda. Bug corrigido:
						// antes o callback so rodava no ramo "else" (schema ja
						// resolvido) — quando a conexao escolhida exigia
						// duplo-clique pra escolher o esquema, o SQL digitado
						// simplesmente sumia (nenhuma aba nova era criada).
						if (onConnected != null) {
							onConnected.run();
						}
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
					// Se isto veio do seletor de "nenhuma conexao aberta" (ver
					// #promptConnectionSelection) e a conexao falhou, a tira de
					// abas continuaria vazia sem isto — nao deixa a tela sem
					// nenhuma aba pra mostrar o editor.
					ensureScratchTabFallback();
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
		w.setSchema(null);
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
						activeWorkspace.setSchema(schema);
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
					List<SchemaInfo> known = (activeWorkspace != null)
							? new ArrayList<>(activeWorkspace.loadedSchemas.values())
							: List.of(schema);
					completionProvider.refresh(known, schemaName);
					objectExplorer.populateTree(schema);
					// refreshConnectionIndicators (nao setConnectedState(schemaName)):
					// o card de conexao deve continuar mostrando o NOME DA CONEXAO
					// ("nureal-teste"), nao o esquema que acabou de ser aberto — bug
					// relatado pelo usuario com captura de tela (o nome "trocava" pro
					// esquema toda vez que ele selecionava um).
					refreshConnectionIndicators();
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
	 * usuario. Quando a aba atual NAO pertence a nenhum workspace com perfil
	 * conhecido (aba SCRATCH/"Sem conexao", que nunca teve uma base "certa"
	 * pra oferecer), abre um seletor com as conexoes salvas em vez de so
	 * avisar na barra de status (ver {@link #openConnectionPickerThenRun}) —
	 * pedido explicito do usuario ("ao executar abrir selecao de conexao").
	 */
	private void offerConnectThenRun() {
		Conexao ws = activeWorkspace;
		ConnectionProfile profile = (ws != null) ? ws.profile() : null;
		if (profile == null) {
			openConnectionPickerThenRun();
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

	/**
	 * Seletor de conexoes salvas, aberto quando o usuario tenta executar um
	 * terminal que ainda nao pertence a nenhuma conexao (aba SCRATCH). O
	 * terminal atual continua na aba "Sem conexao" (nao existe hoje como
	 * "mover uma aba de workspace" sem duplicar); em vez disso, o SQL ja
	 * digitado e levado para uma aba NOVA, criada ja dentro da conexao
	 * escolhida, e executado la — o usuario nao perde o que escreveu nem
	 * precisa colar de novo.
	 */
	private void openConnectionPickerThenRun() {
		Conexao sourceWorkspace = activeWorkspace;
		SqlEditorPane sourceEditor = currentEditor();
		String sql = (sourceEditor != null) ? sourceEditor.currentSql() : "";

		ConnectionProfile selected = pickSavedConnection(
				"Esta aba ainda nao esta conectada a nenhuma base. Escolha uma conexao:", "Conectar e executar");
		if (selected == null) {
			statusBar.setText(" Execucao cancelada: nao conectado.");
			return;
		}
		statusBar.setText(" Conectando a " + selected.name() + " para executar...");
		connectTo(selected, () -> {
			runNewTabWithSql(sql);
			// "Move" o terminal de origem pra conexao escolhida (nao so
			// copia): sem isto, o mesmo SQL ficava duplicado — uma copia na
			// aba nova da conexao escolhida, o original intacto ainda na
			// aba de origem. So remove a ORIGEM depois que o destino ja
			// existe e ja rodou.
			removeTerminalFrom(sourceWorkspace, sourceEditor);
		});
	}

	/**
	 * Dialogo de escolha entre as conexoes salvas ("Conectar"/"Nova conexao..."/
	 * "Cancelar") — compartilhado por {@link #openConnectionPickerThenRun}
	 * (executar sem conexao) e {@link #promptConnectionSelection} (nenhuma
	 * conexao aberta na IDE inteira). Devolve o perfil escolhido, ou
	 * {@code null} se cancelado OU se o usuario preferiu criar uma conexao
	 * nova agora (nesse caso {@code connectionsPanel.createNewConnection()}
	 * ja foi chamado — cabe ao chamador decidir o que fazer a seguir, ja que
	 * criar so SALVA o perfil, nao conecta).
	 */
	private ConnectionProfile pickSavedConnection(String message, String confirmLabel) {
		List<ConnectionProfile> profiles;
		try {
			profiles = connectionStore.load();
		} catch (Exception ex) {
			showError("Falha ao carregar conexoes salvas", ex);
			return null;
		}
		if (profiles.isEmpty()) {
			int create = JOptionPane.showConfirmDialog(this,
					"Voce ainda nao tem nenhuma conexao salva.\n\nCriar uma agora?",
					"Nenhuma conexao salva", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
			if (create == JOptionPane.YES_OPTION) {
				connectionsPanel.createNewConnection();
			}
			return null;
		}

		JList<ConnectionProfile> list = new JList<>(profiles.toArray(new ConnectionProfile[0]));
		list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		list.setSelectedIndex(0);
		list.setCellRenderer(new DefaultListCellRenderer() {
			private static final long serialVersionUID = 1L;

			@Override
			public Component getListCellRendererComponent(JList<?> l, Object value, int index, boolean isSelected,
					boolean cellHasFocus) {
				super.getListCellRendererComponent(l, value, index, isSelected, cellHasFocus);
				if (value instanceof ConnectionProfile p) {
					setText(p.name() + "  —  " + p.user() + "@" + p.host());
				}
				return this;
			}
		});
		JScrollPane scroll = new JScrollPane(list);
		scroll.setPreferredSize(new Dimension(scaledPx(360), scaledPx(220)));

		JPanel panel = new JPanel(new BorderLayout(0, 8));
		panel.add(new JLabel(message), BorderLayout.NORTH);
		panel.add(scroll, BorderLayout.CENTER);

		// JOptionPane montado na mao (nao mais JOptionPane.showOptionDialog):
		// pra duplo-clique na lista poder confirmar sozinho (pedido explicito
		// do usuario — "queria poder dar dois cliques e entrar tambem"),
		// precisa de uma referencia ao proprio optionPane pra chamar
		// setValue(...) de dentro do listener da lista — o metodo estatico de
		// conveniencia nao devolve isso, so o RESULTADO depois do dialogo ja
		// fechado. optionPane.setValue(...) e exatamente o que os botoes
		// internos do JOptionPane chamam sozinhos ao clicar; setar na mao
		// fecha o dialogo do mesmo jeito, sem duplicar logica de abrir/fechar.
		Object[] options = { confirmLabel, "Nova conexao...", "Cancelar" };
		JOptionPane optionPane = new JOptionPane(panel, JOptionPane.QUESTION_MESSAGE, JOptionPane.YES_NO_CANCEL_OPTION,
				null, options, options[0]);
		JDialog dialog = optionPane.createDialog(this, "Selecionar conexao");
		list.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				if (e.getClickCount() == 2 && list.getSelectedValue() != null) {
					optionPane.setValue(confirmLabel);
				}
			}
		});
		dialog.setVisible(true); // bloqueia ate o usuario escolher/fechar
		dialog.dispose();

		Object chosen = optionPane.getValue();
		if (confirmLabel.equals(chosen)) {
			return list.getSelectedValue();
		}
		if ("Nova conexao...".equals(chosen)) {
			connectionsPanel.createNewConnection();
		}
		return null;
	}

	/**
	 * Mostra o seletor de conexoes assim que a IDE nao tem NENHUMA conexao
	 * aberta (arranque, ou depois de fechar a ultima aba de conexao) —
	 * pedido explicito do usuario: "nao existe essa aba sem conexao, quando
	 * nao houver nenhuma conexao aberta abra uma caixa de selecao". A aba
	 * "Sem conexao" continua existindo so como estrutura INTERNA (varios
	 * caminhos do app dependem de sempre haver um workspace ativo — ver
	 * {@code Conexao} SCRATCH), mas nunca mais aparece na tira de abas de
	 * conexao a nao ser como ultimo recurso, se o usuario cancelar este
	 * seletor sem escolher nem criar nenhuma conexao (ver
	 * {@link #ensureScratchTabFallback}) — sem isso a tela ficaria vazia,
	 * sem nenhuma aba pra mostrar o editor.
	 */
	private void promptConnectionSelection() {
		ConnectionProfile selected = pickSavedConnection("Nenhuma conexao aberta. Escolha uma conexao para comecar:",
				"Conectar");
		if (selected == null) {
			ensureScratchTabFallback();
			return;
		}
		connectTo(selected);
	}

	/** So mostra a aba "Sem conexao" na tira se, mesmo assim, continuar sem NENHUMA conexao de verdade (so a aba "+" sobrando) — ver {@link #promptConnectionSelection}. */
	private void ensureScratchTabFallback() {
		if (connectionTabs.getTabCount() <= 1) {
			Conexao scratch = workspaces.get(SCRATCH);
			if (scratch != null) {
				ensureConnectionTab(scratch);
			}
		}
	}

	/** Abre uma aba nova (na conexao ja ativa no momento) com o SQL informado e ja executa — ver {@link #openConnectionPickerThenRun}. */
	private void runNewTabWithSql(String sql) {
		addQueryTab(nextQueryTitle(), sql == null ? "" : sql);
		onRun();
	}

	/**
	 * Remove {@code target} da aba de terminais de {@code w} DIRETAMENTE, sem
	 * passar pelos campos "atalho" {@code editorTabs}/{@code plusTab} (que a
	 * essa altura ja apontam pra OUTRO workspace — ver
	 * {@link #openConnectionPickerThenRun}, unico chamador: {@code w} aqui e
	 * sempre a origem, "Sem conexao", enquanto o workspace ATIVO ja e o
	 * destino). Nunca remove a ULTIMA aba real do workspace (mesma regra de
	 * {@link #closeQueryTab}) — se {@code w} so tinha esta aba, ela fica
	 * (vazia) em vez de deixar o workspace sem nenhuma aba.
	 */
	private void removeTerminalFrom(Conexao w, SqlEditorPane target) {
		if (w == null || target == null || w.ownEditorTabs == null) {
			return;
		}
		JTabbedPane pane = w.ownEditorTabs;
		int idx = pane.indexOfComponent(target);
		if (idx < 0) {
			return;
		}
		int realCount = 0;
		for (int i = 0; i < pane.getTabCount(); i++) {
			if (pane.getComponentAt(i) != w.ownPlusTab) {
				realCount++;
			}
		}
		if (realCount <= 1) {
			return;
		}
		pane.removeTabAt(idx);
		resultsController.forgetTab(target);
		closeTerminalConnection(target);
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
		// Conexao com varios esquemas: cada aba PODE ter um esquema preferido
		// (ver SqlEditorPane#getSchema/#setSchema), usado so para resolver
		// nomes de tabela NAO qualificados escritos nela — nunca uma
		// EXIGENCIA para rodar. Pedido explicito do usuario: "nao deveria ter
		// limitacao para eu rodar conectado a um esquema... eu poderia ter
		// acesso a mais de um esquema e combinar tabelas em queries" — quem
		// decide cruzar esquemas e o proprio SQL (schema.tabela), a IDE nunca
		// bloqueia nem reescreve a instrucao esperando uma escolha antes.
		// So troca o catalogo da conexao (USE) quando a aba TEM um esquema
		// preferido diferente do que a conexao esta apontando agora — sem
		// isso, um "SELECT * FROM tabela" sem qualificar nesta aba resolveria
		// no esquema ERRADO; com a aba sem preferencia nenhuma, roda direto
		// no que a conexao ja estiver, sem perguntar nada.
		if (activeWorkspace != null && activeWorkspace.schemaList() != null) {
			String preferred = editor.getSchema();
			if (preferred != null && !preferred.isBlank() && !preferred.equals(currentActiveSchemaName())) {
				switchCatalogThenRun(preferred, editor);
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
	 * {@link #switchCatalogThenRun}) — exige que a conexao ja esteja
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
	 * roda a instrucao de {@code editor} em seguida — chamado por
	 * {@link #onRun} quando a aba tem um esquema PREFERIDO diferente do que
	 * a conexao esta apontando agora (ver o comentario em {@link #onRun}
	 * sobre isto ser preferencia, nunca exigencia). Reaproveita
	 * {@link Conexao#loadedSchemas} quando esse esquema ja foi carregado
	 * antes nesta conexao (caso comum: usuario ja abriu/rodou nele antes) —
	 * so troca o catalogo, sem round-trip de metadados; consulta o banco de
	 * verdade so na PRIMEIRA vez que este esquema e usado na sessao.
	 */
	private void switchCatalogThenRun(String schemaName, SqlEditorPane editor) {
		Conexao ws = activeWorkspace;
		SchemaInfo cached = (ws != null) ? ws.loadedSchemas.get(schemaName) : null;
		if (cached != null) {
			new SwingWorker<Void, Void>() {
				@Override
				protected Void doInBackground() throws Exception {
					// Troca o catalogo na conexao DESTE terminal (nao mais na
					// "principal"): desde que a execucao passou a rodar numa
					// conexao dedicada por terminal (ver #terminalConnection),
					// trocar so a principal deixaria esta aba rodando no
					// esquema errado. withReconnect: mesma reconexao
					// transparente de #executeWithReconnect, pra uma conexao
					// morta nao virar erro logo na troca de esquema.
					return withReconnect(editor, NEVER_CANCELLED, conn -> {
						conn.setCatalog(schemaName);
						return null;
					});
				}

				@Override
				protected void done() {
					try {
						get();
						applySwitchedSchemaThenRun(ws, cached, schemaName, editor);
					} catch (Exception ex) {
						showError("Falha ao trocar para o esquema \"" + schemaName + "\"", ex);
					}
				}
			}.execute();
			return;
		}
		statusBar.setText(" Conectando no esquema \"" + schemaName + "\" desta aba...");
		new SwingWorker<SchemaInfo, Void>() {
			@Override
			protected SchemaInfo doInBackground() throws Exception {
				// withReconnect: mesma reconexao transparente de
				// #executeWithReconnect — conexao DESTE terminal, nao a
				// principal (ver comentario equivalente acima).
				return withReconnect(editor, NEVER_CANCELLED, conn -> {
					conn.setCatalog(schemaName); // define o banco padrao (USE schema)
					return metadataService.loadSchema(conn, schemaName);
				});
			}

			@Override
			protected void done() {
				try {
					applySwitchedSchemaThenRun(ws, get(), schemaName, editor);
				} catch (Exception ex) {
					showError("Falha ao conectar no esquema \"" + schemaName + "\"", ex);
					statusBar.setText(" Erro ao trocar de esquema");
				}
			}
		}.execute();
	}

	/** Aplica o esquema (ja carregado, do cache ou fresco) apos {@link #switchCatalogThenRun} e roda a instrucao — sempre na EDT. */
	private void applySwitchedSchemaThenRun(Conexao ws, SchemaInfo schema, String schemaName, SqlEditorPane editor) {
		if (ws != null) {
			ws.setSchema(schema);
		}
		metadataCache.set(schema);
		completionProvider.refresh(ws != null ? ws.loadedSchemas.values() : List.of(schema), schemaName);
		objectExplorer.populateTree(schema);
		// Ver comentario equivalente em #openSchema: mantem o nome da CONEXAO
		// no card, nao o esquema.
		refreshConnectionIndicators();
		updateWorkspaceContextBar();
		runStatements(editor);
	}

	/** Roda de fato as instrucoes SQL da aba {@code editor} — ver {@link #onRun}. */
	/**
	 * Conexao JDBC deste terminal (emprestada uma vez do pool e reutilizada
	 * nas proximas execucoes DO MESMO terminal — ver {@link #terminalConnections}).
	 * Emprestar de novo se a conexao guardada ja tiver sido fechada (ex.:
	 * pool derrubado por uma desconexao manual entre duas execucoes).
	 */
	private Connection terminalConnection(SqlEditorPane editor) throws SQLException {
		Connection existing = terminalConnections.get(editor);
		if (existing != null) {
			try {
				if (!existing.isClosed()) {
					return existing;
				}
			} catch (SQLException ignore) {
				// cai para emprestar uma nova abaixo
			}
		}
		Connection fresh = connectionManager().borrowConnection();
		// Uma conexao RECEM-emprestada do pool nasce no esquema padrao do
		// PERFIL (definido na URL JDBC de conexao), nao no esquema que o
		// usuario tenha aberto na arvore/aba enquanto isso — antes, com uma
		// unica conexao compartilhada por toda a IDE, isso nunca era um
		// problema (a troca de esquema, ver #openSchema/#switchCatalogThenRun,
		// sempre acontecia na MESMA conexao que ia rodar a consulta). Agora
		// cada terminal tem a sua propria conexao fisica, entao a primeira
		// vez que ele roda algo precisa alinhar essa conexao nova ao esquema
		// que o usuario espera — o preferido desta aba (ver
		// SqlEditorPane#getSchema, ja herdado do esquema atual quando a aba
		// foi criada) ou, na falta dele, o esquema atualmente aberto —
		// senao o usuario seria obrigado a qualificar toda tabela com
		// "esquema." so porque abriu um terminal novo. Bug relatado pelo
		// usuario logo apos a introducao do pool por terminal.
		String schemaName = editor.getSchema();
		if (schemaName == null || schemaName.isBlank()) {
			schemaName = currentActiveSchemaName();
		}
		if (schemaName != null && !schemaName.isBlank()) {
			try {
				fresh.setCatalog(schemaName);
			} catch (SQLException ex) {
				fresh.close();
				throw ex;
			}
		}
		terminalConnections.put(editor, fresh);
		return fresh;
	}

	/** Devolve ao pool a conexao dedicada de um terminal, se houver — ver {@link #terminalConnections}. */
	private void closeTerminalConnection(SqlEditorPane editor) {
		Connection conn = terminalConnections.remove(editor);
		if (conn != null) {
			try {
				conn.close();
			} catch (SQLException ignore) {
				// nada a fazer ao fechar
			}
		}
	}

	/**
	 * Confere se {@code conn} morreu DE VERDADE (nao so uma excecao qualquer
	 * na execucao) — usado por {@link #executeWithReconnect} pra decidir se
	 * vale a pena reconectar e tentar de novo, ou se o erro e da propria
	 * instrucao (sintaxe invalida, violacao de constraint etc., onde
	 * reconectar so repetiria o MESMO erro). {@code Connection#isClosed()}
	 * sozinho NAO detecta isso — so reflete um {@code close()} local
	 * explicito, nao um socket derrubado pelo servidor (idle timeout, queda
	 * de rede) — por isso {@code #terminalConnection} confiava nele e
	 * devolvia uma conexao morta sem perceber. {@code isValid(int)} de fato
	 * pinga o servidor (timeout curto, 2s, ja que isto roda em background —
	 * ver os dois chamadores de {@link #executeWithReconnect}).
	 */
	private static boolean isConnectionLost(Connection conn) {
		try {
			return conn.isClosed() || !conn.isValid(2);
		} catch (SQLException ex) {
			return true;
		}
	}

	/**
	 * Acao contra uma {@link Connection} de terminal que pode falhar por ela
	 * ter morrido — ver {@link #withReconnect}.
	 */
	@FunctionalInterface
	private interface TerminalConnectionAction<T> {
		T run(Connection conn) throws SQLException;
	}

	/**
	 * Roda {@code action} na conexao deste terminal e, se ela tiver morrido
	 * no meio (servidor derrubou por timeout/rede — ver
	 * {@link #isConnectionLost}), reconecta uma unica vez de forma
	 * TRANSPARENTE (fecha a conexao morta, empresta uma nova do pool via
	 * {@link #terminalConnection}, roda a MESMA acao de novo na conexao
	 * nova) em vez de propagar o erro — pedido explicito do usuario:
	 * "quando uma conexao desconecta e rodo uma instrucao ela deveria
	 * reconectar automaticamente sem eu tomar erro". A conexao nova fica
	 * guardada em {@link #terminalConnections} normalmente, entao a proxima
	 * acao desta aba ja reusa ela sem precisar reconectar de novo — cobre
	 * tambem o "depois de executar reconectar sozinho" do pedido original.
	 * Usado tanto por {@link #executeWithReconnect} (rodar as instrucoes)
	 * quanto por {@link #switchCatalogThenRun} (trocar de esquema antes de
	 * rodar) — os dois pontos desta janela que seguram e reusam a conexao
	 * de um terminal entre chamadas.
	 * <p>
	 * So reconecta quando {@link #isConnectionLost} confirma a conexao
	 * morta — uma acao que falhou por um motivo NORMAL (SQL invalido, FK
	 * violada, esquema inexistente etc.) continua propagando o erro como
	 * sempre, sem repetir (reconectar nao mudaria o resultado, so rodaria a
	 * mesma acao invalida duas vezes). {@code isCancelled} (quando a acao
	 * suporta cancelamento — ver {@link #executeWithReconnect}) tambem
	 * impede a nova tentativa se o usuario ja cancelou nesse meio tempo.
	 */
	private <T> T withReconnect(SqlEditorPane editor, Supplier<Boolean> isCancelled,
			TerminalConnectionAction<T> action) throws SQLException {
		Connection conn = terminalConnection(editor);
		try {
			return action.run(conn);
		} catch (SQLException ex) {
			if (isCancelled.get() || !isConnectionLost(conn)) {
				throw ex;
			}
			closeTerminalConnection(editor);
			Connection fresh = terminalConnection(editor);
			TerminalExecution exec = terminalExecutions.get(editor);
			if (exec != null) {
				exec.reconnected = true;
			}
			return action.run(fresh);
		}
	}

	/** Roda {@code statements} nesta aba com reconexao transparente — ver {@link #withReconnect}. */
	private List<QueryResult> executeWithReconnect(SqlEditorPane editor, List<String> statements,
			Supplier<Boolean> isCancelled) throws SQLException {
		Consumer<Statement> onStatementChange = st -> {
			TerminalExecution exec = terminalExecutions.get(editor);
			if (exec != null) {
				exec.statement = st;
			}
		};
		return withReconnect(editor, isCancelled,
				conn -> SqlExecutionEngine.executeStatements(conn, statements, isCancelled, onStatementChange));
	}

	/** Nunca cancelavel — usado por {@link #withReconnect} nos chamadores sem SwingWorker cancelavel (ex.: {@link #switchCatalogThenRun}). */
	private static final Supplier<Boolean> NEVER_CANCELLED = () -> false;

	private void runStatements(SqlEditorPane editor) {
		final List<String> statements = statementsToRun(editor);
		if (statements == null) {
			return;
		}
		prepareForExecution(editor, statements);

		SwingWorker<List<QueryResult>, Void> worker = new SwingWorker<>() {
			@Override
			protected List<QueryResult> doInBackground() throws SQLException {
				return executeWithReconnect(editor, statements, this::isCancelled);
			}

			@Override
			protected void done() {
				TerminalExecution exec = terminalExecutions.remove(editor);
				refreshRunButtonState();
				refreshExecutingOverlay();
				try {
					handleStatementResults(editor, statements, get());
					// Reconexao transparente (ver #executeWithReconnect):
					// a instrucao rodou normalmente, mas avisa discretamente
					// que a conexao tinha caido e foi renovada sozinha — sem
					// isto pareceria magica (ou um bug de demora) o usuario
					// nunca saber que a conexao antiga morreu no meio.
					if (exec != null && exec.reconnected) {
						statusBar.setText(" Conexao havia caido — reconectado automaticamente. Pronto.");
					}
				} catch (CancellationException ce) {
					statusBar.setText(" Execucao cancelada.");
				} catch (Exception ex) {
					showError("Erro ao executar SQL", ex);
					statusBar.setText(" Erro na execucao");
				}
			}
		};
		terminalExecutions.put(editor, new TerminalExecution(worker));
		refreshRunButtonState();
		refreshExecutingOverlay();
		worker.execute();
	}

	/** "Executar" so fica habilitado se a conexao do terminal ATUAL estiver ativa E ele nao estiver ja rodando algo. */
	private void refreshRunButtonState() {
		if (runButton == null) {
			return;
		}
		SqlEditorPane cur = currentEditor();
		boolean running = cur != null && terminalExecutions.containsKey(cur);
		boolean connected = activeWorkspace != null && activeWorkspace.mgr().isConnected();
		runButton.setEnabled(connected && !running);
	}

	/**
	 * A barra "Executando..." (ver {@code ResultsAreaController#showExecuting})
	 * e da area de RESULTADOS, compartilhada por todos os terminais — so faz
	 * sentido mostra-la quando o terminal ATUALMENTE VISIVEL e o que esta
	 * rodando algo, senao o usuario nao conseguiria nem OLHAR os resultados
	 * de um terminal ocioso so porque outro, em OUTRA aba, esta executando.
	 */
	private void refreshExecutingOverlay() {
		SqlEditorPane cur = currentEditor();
		resultsController.showExecuting(cur != null && terminalExecutions.containsKey(cur));
	}

	/**
	 * Cancela a execucao em andamento do terminal informado (Statement.cancel
	 * + SwingWorker), se houver — chamado pelo botao "Cancelar" da area de
	 * resultados (ver {@code ResultsAreaController#cancelExecution}), sempre
	 * para o terminal ATUALMENTE visivel (o unico cujo "Cancelar" o usuario
	 * consegue ver/clicar).
	 */
	void cancelExecution(SqlEditorPane editor) {
		TerminalExecution exec = (editor != null) ? terminalExecutions.get(editor) : null;
		if (exec == null) {
			return;
		}
		Statement st = exec.statement;
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
		exec.worker.cancel(true);
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
		// "Executar" desabilitado e a barra "Executando..." aparecem so
		// depois de registrar esta execucao em terminalExecutions (ver
		// #runStatements, chamador) — refreshRunButtonState/refreshExecutingOverlay
		// olham esse mapa, entao so refletem o terminal ATUAL, nunca todos.
		boolean usingSelection = editor.hasSelection();
		statusBar.setText(" Executando " + statements.size() + " instrucao(oes)"
				+ (usingSelection ? "  —  ATENCAO: rodando apenas a SELECAO" : "") + "...");
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
			if (currentSchema() != null) {
				objectExplorer.refreshObjectTree(false);
			} else if (activeWorkspace != null && activeWorkspace.mgr.isConnected()) {
				// DDL rodou sem nenhum esquema aberto (ex.: CREATE DATABASE numa
				// conexao que ainda nao tinha nenhum) — recarrega a lista de
				// esquemas pra ele aparecer na arvore sem precisar reconectar.
				refreshSchemaList();
			}
		}
		logExecutionHistory(editor, results);
	}

	/**
	 * Recarrega a lista de esquemas da conexao ativa e reconstroi o seletor na
	 * arvore de objetos — usado depois de DDL estrutural (ex.: CREATE DATABASE)
	 * rodado sem nenhum esquema aberto ainda (ver {@link #handleStatementResults}).
	 */
	private void refreshSchemaList() {
		Conexao ws = activeWorkspace;
		if (ws == null || !ws.mgr.isConnected()) {
			return;
		}
		new SwingWorker<List<String>, Void>() {
			@Override
			protected List<String> doInBackground() throws Exception {
				return metadataService.listSchemas(ws.mgr.getConnection());
			}

			@Override
			protected void done() {
				try {
					List<String> schemas = get();
					ws.schemaList = schemas;
					if (ws == activeWorkspace && ws.schema == null) {
						objectExplorer.buildSchemaPicker(schemas);
					}
				} catch (Exception ex) {
					// silencioso: so a lista de esquemas na sidebar fica desatualizada
					// ate o proximo refresh manual ou reconexao — nao interrompe o
					// fluxo do usuario por causa disso.
				}
			}
		}.execute();
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
		onSaveQuery(false);
	}

	/**
	 * Igual a {@link #onSaveQuery()}, com {@code forceNew}: quando {@code
	 * true}, ignora {@code editor.getSavedQueryId()} mesmo que a aba ja
	 * esteja ligada a uma query salva — sempre pede um titulo NOVO e cria
	 * uma copia separada, em vez de sobrescrever a original. Acionado por
	 * "Salvar como nova query..." no dropdown "Salvar ▾" da barra.
	 * A aba passa a apontar pra essa copia nova (ver
	 * {@code editor.setSavedQueryId}) — um proximo "Salvar" comum atualiza a
	 * COPIA, nao mais a query original.
	 */
	private void onSaveQuery(boolean forceNew) {
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
			String existingId = forceNew ? null : editor.getSavedQueryId();
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
		// Fecha o popup "Salvas" (ver #showSavedQueriesPopup) — mesmo padrao
		// dos outros popups de navegacao: a acao ja foi feita.
		savedQueriesPopup.close();
	}

	/** Nome da conexao ativa, ou {@code null} no workspace "sem conexao" (SCRATCH). */
	private String currentConnectionLabel() {
		return (activeWorkspace != null && activeWorkspace.profile != null) ? activeWorkspace.profile.name() : null;
	}

	// ---------- Historico de execucoes (log automatico — ver ExecutionHistoryStore) ----------

	/**
	 * Abre {@link #historyPanel} num painel flutuante ancorado a DIREITA do
	 * item "Historico" do rail lateral — ver {@link AnchoredPopup} pro
	 * mecanismo (compartilhado com Conexoes/SQLs/Salvas/Objetos).
	 */
	private void showHistoryPopup(JComponent anchor) {
		historyPanel.setPreferredSize(new Dimension(scaledPx(340), scaledPx(420)));
		historyPanel.setBorder(BorderFactory.createLineBorder(GridTheme.HEADER_BORDER, 1, true));
		historyPopup.toggle(anchor, historyPanel, Placement.RIGHT);
	}

	/** Abre {@link #sqlEditorsPanel} (atalho "+ nova aba" + lista das abas de SQL abertas) num painel flutuante ancorado a direita do item "SQLs" do rail — ver {@link AnchoredPopup}. */
	private void showSqlEditorsPopup(JComponent anchor) {
		sqlEditorsPanel.setPreferredSize(new Dimension(scaledPx(280), scaledPx(360)));
		sqlEditorsPanel.setBorder(BorderFactory.createLineBorder(GridTheme.HEADER_BORDER, 1, true));
		sqlEditorsPopup.toggle(anchor, sqlEditorsPanel, Placement.RIGHT);
	}

	/** Abre {@link #savedQueriesPanel} num painel flutuante ancorado a direita do item "Favoritos" do rail — ver {@link AnchoredPopup}. */
	private void showSavedQueriesPopup(JComponent anchor) {
		savedQueriesPanel.setPreferredSize(new Dimension(scaledPx(340), scaledPx(420)));
		savedQueriesPanel.setBorder(BorderFactory.createLineBorder(GridTheme.HEADER_BORDER, 1, true));
		savedQueriesPopup.toggle(anchor, savedQueriesPanel, Placement.RIGHT);
	}

	/** Abre {@link #objectsBrowserPanel} (arvore de Objetos) num painel flutuante ancorado a direita do item "Objetos" do rail — ver {@link AnchoredPopup}. */
	private void showObjectsPopup(JComponent anchor) {
		objectsBrowserPanel.setPreferredSize(new Dimension(scaledPx(320), scaledPx(480)));
		objectsBrowserPanel.setBorder(BorderFactory.createLineBorder(GridTheme.HEADER_BORDER, 1, true));
		objectsPopup.toggle(anchor, objectsBrowserPanel, Placement.RIGHT);
	}

	/**
	 * Reabre uma execucao do historico (duplo-clique/"Abrir em nova aba" no
	 * painel Historico) numa aba NOVA, ja marcada com o esquema em que rodou
	 * (se algum) — assim, ao clicar Executar de novo, cai direto no mesmo
	 * esquema sem precisar escolher de novo.
	 */
	private void openHistoryEntry(ExecutionHistoryStore.Entry entry) {
		if (!addQueryTab(historyTabTitle(entry.sql()), entry.sql())) {
			return;
		}
		SqlEditorPane editor = currentEditor();
		if (editor != null && entry.schema() != null) {
			editor.setSchema(entry.schema());
			scheduleSave();
		}
		statusBar.setText(" Execucao reaberta do historico.");
		// Escolher um item fecha o painel flutuante (ver #showHistoryPopup)
		// — mesmo padrao de menu/dropdown: a acao ja foi feita, nao ha
		// motivo pra continuar ocupando a tela.
		historyPopup.close();
	}

	/**
	 * Titulo da aba reaberta do Historico — ANTES sempre o literal
	 * "Historico", entao reabrir 2+ execucoes ao mesmo tempo dava abas
	 * IDENTICAS na barra, sem nenhum jeito de saber qual e qual sem clicar em
	 * cada uma (revisao de UX, a partir de um screenshot mostrando exatamente
	 * isso). Mesma receita ja usada para "SELECT nome_da_tabela" ao abrir uma
	 * tabela pelo Object Browser (ver {@code ObjectExplorerController#generateSelect}):
	 * 1a "linha" do SQL, achatada e truncada, com sufixo numerico (via
	 * {@link #titleExists}) se o mesmo SQL for reaberto mais de uma vez.
	 */
	private String historyTabTitle(String sql) {
		String flat = (sql == null) ? "" : sql.replaceAll("\\s+", " ").trim();
		if (flat.isEmpty()) {
			return "Historico";
		}
		int maxLen = 28;
		String baseTitle = flat.length() > maxLen ? flat.substring(0, maxLen - 1) + "…" : flat;
		String title = baseTitle;
		int n = 1;
		while (titleExists(title)) {
			title = baseTitle + " " + (++n);
		}
		return title;
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
