package com.nureal.ide.ui;
import com.nureal.ide.compartilhado.designsystem.IconType;
import com.nureal.ide.compartilhado.designsystem.Icons;
import com.nureal.ide.compartilhado.designsystem.Buttons;
import com.nureal.ide.compartilhado.designsystem.Typography;
import com.nureal.ide.compartilhado.designsystem.GridTheme;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.GridBagLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.SwingWorker;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;

import com.formdev.flatlaf.FlatLaf;
import com.nureal.ide.modulos.backupexportacao.infraestrutura.ExcelExporter;
import com.nureal.ide.modulos.backupexportacao.dominio.contratos.TabelaExportavel;
import com.nureal.ide.modulos.metadados.dominio.entidades.ColumnDetail;
import com.nureal.ide.modulos.metadados.dominio.entidades.TableDetails;

/**
 * Area de resultados: casca visual (abas, overlay de "executando",
 * estado vazio), a grade de cada resultado (edicao direta, paginacao sob
 * demanda via cursor) e exportacao (Excel/CSV). Extraida do
 * {@link MainWindow} (SPEC-0008 Etapa 3 — arquivo tinha passado do limite
 * de 2000 linhas que a propria spec proibe) — mesmo padrao ja usado no
 * {@link ObjectExplorerController} (colaborador com referencia de volta
 * pro MainWindow via "owner").
 * <p>
 * {@code resultsByTab}/{@code lastResults}/{@code openCursors} moraram
 * pra ca junto com a logica que os le/escreve — MainWindow ainda toca
 * alguns deles de fora (fechar aba, trocar de workspace, rodar SQL), mas
 * sempre atraves dos metodos de pacote abaixo, nunca direto no campo.
 */
final class ResultsAreaController {

	/** Pixels de folga da barra de rolagem vertical pra considerar "perto do fim" — ver {@link #installAutoLoadOnScroll}. */
	private static final int SCROLL_LOAD_THRESHOLD_PX = 120;

	private final MainWindow owner;

	private JTabbedPane resultTabs;
	private JPanel resultsCards;
	private JButton resultsOrientationButton;
	private JComponent resultsOverlay;
	private JPanel executingCard;
	private List<QueryResult> lastResults = new ArrayList<>();
	private final Map<SqlEditorPane, List<QueryResult>> resultsByTab = new HashMap<>();
	private final List<SqlExecutionEngine.ResultCursor> openCursors = new ArrayList<>();

	ResultsAreaController(MainWindow owner) {
		this.owner = owner;
	}

	// ---------- Casca visual ----------

	JComponent buildResultsArea() {
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

		resultsCards = new JPanel(new java.awt.CardLayout());
		resultsCards.add(buildEmptyState(), "empty");
		resultsCards.add(tabsPanel, "tabs");

		JButton orientationToggle = new JButton();
		orientationToggle.addActionListener(e -> owner.toggleResultsOrientation());
		updateOrientationToggleIcon(orientationToggle);
		orientationToggle.addPropertyChangeListener("UI", e -> updateOrientationToggleIcon(orientationToggle));
		this.resultsOrientationButton = orientationToggle;

		JButton expandResultsButton = new JButton();
		Buttons.bindThemedIcon(expandResultsButton, IconType.EXPAND, 14, () -> GridTheme.MUTED_TEXT);
		expandResultsButton.setToolTipText("Expandir/recolher resultados (oculta paineis laterais)");
		expandResultsButton.addActionListener(e -> owner.toggleResultsFocusMode());

		for (JButton btn : new JButton[] { orientationToggle, expandResultsButton }) {
			Buttons.styleIconButton(btn);
		}

		// Sem o icone "Exportar todos" que ficava aqui: era um duplicado
		// exato do item "Exportar todos (uma aba por resultado)..." que ja
		// existe dentro do menu "Exportar" de CADA resultado (ver
		// ResultStatusBar) — revisao de UX pediu um unico lugar descobrivel
		// pra exportar, nao dois botoes pra mesma acao em cantos diferentes
		// da tela.
		JPanel headerIcons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 3, 0));
		headerIcons.setOpaque(false);
		headerIcons.add(orientationToggle);
		headerIcons.add(expandResultsButton);

		JPanel header = new JPanel(new BorderLayout());
		header.setOpaque(false);
		header.add(owner.sectionHeader("RESULTADOS"), BorderLayout.WEST);
		header.add(headerIcons, BorderLayout.EAST);

		JPanel panel = new JPanel(new BorderLayout(0, 8));
		panel.setBorder(BorderFactory.createEmptyBorder(4, 8, 8, 8));
		panel.add(header, BorderLayout.NORTH);
		panel.add(overlayStack(resultsCards), BorderLayout.CENTER);
		return panel;
	}

	/** Reaplica icone/tooltip do botao de orientacao — chamado por {@code MainWindow#toggleResultsOrientation}. */
	void refreshOrientationIcon() {
		if (resultsOrientationButton != null) {
			updateOrientationToggleIcon(resultsOrientationButton);
		}
	}

	private void updateOrientationToggleIcon(JButton button) {
		button.setIcon(owner.resultsVertical ? Icons.get(IconType.PANEL_LEFT, 14, GridTheme.MUTED_TEXT)
				: Icons.get(IconType.PANEL_BOTTOM, 14, GridTheme.MUTED_TEXT));
		button.setToolTipText(owner.resultsVertical ? "Mudar para resultados embaixo do editor (horizontal)"
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
		Typography.primary(label);

		JProgressBar spinner = new JProgressBar();
		spinner.setIndeterminate(true);
		spinner.setPreferredSize(new Dimension(200, 6));
		spinner.setMaximumSize(new Dimension(200, 6));
		spinner.setAlignmentX(Component.CENTER_ALIGNMENT);

		JButton cancel = new JButton("Cancelar");
		cancel.setAlignmentX(Component.CENTER_ALIGNMENT);
		cancel.addActionListener(e -> cancelExecution());
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
	 * Cores do card "Executando consulta..." — reaplicadas sempre que o
	 * overlay for exibido (ver {@link #showExecuting}) e tambem por
	 * {@code MainWindow#toggleTheme}, pegando o tema ATUAL.
	 */
	void styleExecutingOverlay() {
		if (executingCard == null) {
			return;
		}
		boolean dark = FlatLaf.isLafDark();
		executingCard.setBackground(dark ? new Color(0x2B, 0x2D, 0x30) : new Color(0xFF, 0xFF, 0xFF));
		executingCard.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(dark ? new Color(0x44, 0x48, 0x4D) : new Color(0xE0, 0xE3, 0xE7)),
				BorderFactory.createEmptyBorder(18, 28, 18, 28)));
	}

	void showExecuting(boolean executing) {
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
		owner.statusBar().setText(" Cancelando execucao...");
		Statement st = owner.runningStatement;
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
		if (owner.runWorker != null) {
			owner.runWorker.cancel(true);
		}
	}

	private JComponent buildEmptyState() {
		JLabel icon = new JLabel();
		Buttons.bindThemedIcon(icon, IconType.TABLE, 46, () -> GridTheme.MUTED_TEXT);
		icon.setAlignmentX(Component.CENTER_ALIGNMENT);

		JLabel title = new JLabel("Execute uma consulta para ver os resultados");
		title.setFont(title.getFont().deriveFont(14f));
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
		((java.awt.CardLayout) resultsCards.getLayout()).show(resultsCards, "empty");
	}

	private void showResultsCard() {
		((java.awt.CardLayout) resultsCards.getLayout()).show(resultsCards, "tabs");
	}

	// ---------- Exibicao dos resultados ----------

	void showResultsForActiveEditor() {
		SqlEditorPane editor = owner.currentEditor();
		List<QueryResult> results = (editor == null) ? null : resultsByTab.get(editor);
		if (results == null) {
			lastResults = new ArrayList<>();
			resultTabs.removeAll();
			showEmptyState();
			return;
		}
		showResults(results);
	}

	/** Reconstroi a aba de resultados atual (mesmos dados) — chamado apos zoom/densidade mudar. */
	void reshowIfVisible() {
		if (resultTabs != null && resultTabs.getTabCount() > 0) {
			showResults(lastResults);
		}
	}

	void showResults(List<QueryResult> results) {
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
			resultTabs.setToolTipTextAt(resultTabs.getTabCount() - 1, MainWindow.sqlTooltip(r.sql()));
			error = error || r.error();
		}
		if (resultTabs.getTabCount() > 0) {
			resultTabs.setSelectedIndex(0);
			showResultsCard();
		} else {
			showEmptyState();
		}
		owner.statusBar().setText(" " + results.size() + " instrucao(oes) executada(s), " + grids + " com resultado"
				+ (error ? " - parou em erro" : ""));
	}

	/** Guarda/esquece os resultados de UMA aba de SQL — ver {@code Conexao#tabResults}/{@code MainWindow#closeQueryTab}. */
	void forgetTab(SqlEditorPane tab) {
		resultsByTab.remove(tab);
	}

	void rememberTab(SqlEditorPane tab, List<QueryResult> results) {
		resultsByTab.put(tab, results);
	}

	List<QueryResult> resultsFor(SqlEditorPane tab) {
		return resultsByTab.get(tab);
	}

	/**
	 * Painel de grade de uma aba de resultado: monta {@link ResultGrid} +
	 * {@link ResultStatusBar} atraves de {@link ResultView}. So decide OS
	 * CALLBACKS que dependem do ciclo de vida do cursor JDBC (paginacao/
	 * leitura, responsabilidade sua, nao da grade nem da barra) — nenhuma
	 * logica de layout do resultado mora aqui.
	 */
	private JComponent buildGridPanel(QueryResult r) {
		ResultTableModel model = (ResultTableModel) r.model();
		String schemaName = (owner.currentSchema() != null) ? owner.currentSchema().name() : null;
		ResultGrid grid = new ResultGrid(model, owner.connectionManager(), schemaName, owner.tableMetadataCache(),
				() -> exportResult(r), owner::scaledPx, owner.resultRowHeightBasePx());

		ResultStatusBar resultStatusBar = new ResultStatusBar();
		grid.keepSelectionOnFocusTo(resultStatusBar.asComponent());
		Runnable refresh = () -> resultStatusBar.refresh(r.model().getRowCount(), r.execMs(), r.fetchMs(),
				r.cursor() != null && !r.cursor().exhausted);
		// Um UNICO "ocupado" compartilhado entre paginacao (auto-scroll/
		// "carregar tudo") e a contagem exata (ver #showExactTotal) — as duas
		// leem do MESMO Statement/Connection deste resultado (ver
		// SqlExecutionEngine.ResultCursor#st), entao nunca podem rodar ao
		// mesmo tempo uma da outra.
		boolean[] busy = { false };
		resultStatusBar.onLoadAll(() -> {
			if (busy[0]) {
				return;
			}
			busy[0] = true;
			loadAll(r, () -> {
				refresh.run();
				// loadAll so termina de verdade quando o cursor esgota (ver
				// #loadAll); ate la cada "chunk" publicado chama refresh
				// tambem, entao so libera o "ocupado" quando o cursor JA
				// estiver esgotado (nao no meio do caminho).
				if (r.cursor() == null || r.cursor().exhausted) {
					busy[0] = false;
				}
			});
		});
		installAutoLoadOnScroll(grid, r, refresh, busy);
		resultStatusBar.onShowExactTotal(() -> showExactTotal(r, resultStatusBar, busy));
		resultStatusBar.onExportThis(() -> exportResult(r));
		resultStatusBar.onExportAll(this::exportAll);
		resultStatusBar.onExportCsv(() -> exportResultCsv(grid.table(), r.title()));
		resultStatusBar.onExportJson(() -> exportResultJson(grid.table(), r.title()));
		grid.onSelectionSummary(resultStatusBar::updateSelectionSummary);
		refresh.run();

		wireGridEditing(grid, resultStatusBar, model, schemaName);

		return new ResultView(grid, resultStatusBar).asComponent();
	}

	/**
	 * Rolagem perto do fim da grade busca a proxima pagina sozinha — pedido
	 * explicito do usuario na revisao de UX ("queria eliminar esses botoes
	 * no final da tela para dar impressao de continuidade"): antes disso a
	 * UNICA forma de ver mais linhas era clicar em "Carregar mais N", um
	 * botao permanente no rodape. {@code busy[0]} (compartilhado com
	 * "Carregar tudo" e "Ver total exato", ver {@link #buildGridPanel})
	 * evita disparar duas leituras ao mesmo tempo no MESMO cursor/conexao —
	 * o evento de rolagem dispara varias vezes enquanto o usuario arrasta a
	 * barra, e uma pagina/contagem so pode estar em andamento por vez.
	 */
	private void installAutoLoadOnScroll(ResultGrid grid, QueryResult r, Runnable refresh, boolean[] busy) {
		JScrollPane scroll = grid.scrollPane();
		if (scroll == null) {
			return;
		}
		scroll.getVerticalScrollBar().addAdjustmentListener(e -> {
			if (busy[0]) {
				return;
			}
			SqlExecutionEngine.ResultCursor cursor = r.cursor();
			if (cursor == null || cursor.exhausted) {
				return;
			}
			java.awt.Adjustable bar = e.getAdjustable();
			boolean nearBottom = bar.getValue() + bar.getVisibleAmount() >= bar.getMaximum() - SCROLL_LOAD_THRESHOLD_PX;
			if (!nearBottom) {
				return;
			}
			busy[0] = true;
			loadPage(r, SqlExecutionEngine.PAGE_SIZE, () -> {
				busy[0] = false;
				refresh.run();
			});
		});
	}

	/**
	 * "Ver total exato" (ver {@code ResultStatusBar#onShowExactTotal}): roda
	 * {@code SELECT COUNT(*) FROM (<sql original>) AS contagem} — respeita
	 * filtros/joins/agrupamentos da consulta original, ao contrario de so
	 * contar linhas visiveis na grade — numa NOVA {@link Statement}, na
	 * MESMA {@link Connection} do cursor original (ver
	 * {@code SqlExecutionEngine.ResultCursor#st}). Deliberadamente opt-in
	 * (nunca automatico): pode ser lenta em tabela grande, por isso um
	 * botao, nao um calculo em toda pagina carregada.
	 * <p>
	 * {@code busy[0]} garante que isto nunca roda ao mesmo tempo que uma
	 * pagina sendo carregada do MESMO cursor (ver {@link #installAutoLoadOnScroll}/
	 * {@link #loadAll}) — duas leituras simultaneas na mesma {@link Connection}
	 * JDBC nao sao seguras em geral.
	 */
	private void showExactTotal(QueryResult r, ResultStatusBar resultStatusBar, boolean[] busy) {
		SqlExecutionEngine.ResultCursor cursor = r.cursor();
		if (cursor == null || busy[0]) {
			return;
		}
		String sql = r.sql().strip();
		if (sql.endsWith(";")) {
			sql = sql.substring(0, sql.length() - 1);
		}
		String countSql = "SELECT COUNT(*) FROM (" + sql + ") AS contagem_ide";
		busy[0] = true;
		resultStatusBar.setExactTotalBusy(true);
		new SwingWorker<Long, Void>() {
			@Override
			protected Long doInBackground() throws SQLException {
				try (Statement st = cursor.st.getConnection().createStatement();
						ResultSet rs = st.executeQuery(countSql)) {
					return rs.next() ? rs.getLong(1) : null;
				}
			}

			@Override
			protected void done() {
				busy[0] = false;
				resultStatusBar.setExactTotalBusy(false);
				try {
					Long total = get();
					if (total != null) {
						resultStatusBar.showExactTotal(total);
					}
				} catch (Exception ex) {
					com.nureal.ide.core.log.AppLogger.warning("Falha ao contar o total exato do resultado", ex);
					owner.statusBar().setText(" Nao foi possivel contar o total exato: "
							+ ((ex.getCause() != null) ? ex.getCause().getMessage() : ex.getMessage()));
				}
			}
		}.execute();
	}

	// ---------- Edicao direta na grade (update/insert/delete) ----------

	/**
	 * Liga os botoes de edicao da barra de resultado a {@link GridEditController}
	 * da grade, e tenta habilitar a edicao em si (ver {@link #tryEnableEditing}).
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

		resultStatusBar.onToggleEditMode(() -> {
			if (editController.isEditModeOn()) {
				if (editController.hasPendingChanges()) {
					JOptionPane.showMessageDialog(owner,
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
	 * PRESENTE no resultado.
	 */
	private void tryEnableEditing(String schemaName, ResultTableModel model, Runnable onEnabled) {
		if (schemaName == null) {
			return; // workspace "sem conexao" (SCRATCH) nunca tem metadados de tabela
		}
		String table = uniqueSourceTable(model);
		if (table == null) {
			return;
		}
		TableDetails details = owner.tableMetadataCache().get(owner.connectionManager(), schemaName, table,
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
		if (!owner.connectionManager().isConnected()) {
			owner.statusBar().setText(" Conecte-se a uma base antes de salvar alteracoes.");
			return;
		}
		int pending = editController.pendingCount();
		int ok = JOptionPane.showConfirmDialog(owner,
				"Salvar " + pending + " alteracao(oes) pendente(s) na tabela \"" + editController.target().table()
						+ "\"?\nIsto grava direto no banco (uma unica transacao; tudo ou nada).",
				"Salvar alteracoes", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
		if (ok != JOptionPane.YES_OPTION) {
			return;
		}
		Connection conn = owner.connectionManager().getConnection();
		com.nureal.ide.modulos.dialeto.dominio.contratos.DatabaseDialect dialect = owner.connectionManager().dialect();
		resultStatusBar.setEditBusy(true);
		SwingWorker<GridEditController.ApplyResult, Void> worker = new SwingWorker<>() {
			@Override
			protected GridEditController.ApplyResult doInBackground() throws SQLException {
				return editController.apply(conn, dialect);
			}

			@Override
			protected void done() {
				resultStatusBar.setEditBusy(false);
				try {
					GridEditController.ApplyResult result = get();
					owner.statusBar().setText(" Alteracoes salvas: " + result.inserted() + " inserida(s), "
							+ result.updated() + " atualizada(s), " + result.deleted() + " excluida(s).");
					grid.table().repaint();
				} catch (Exception ex) {
					owner.showError("Falha ao salvar as alteracoes da grade", ex);
				} finally {
					refreshEditUi.run();
				}
			}
		};
		worker.execute();
	}

	// ---------- Paginacao sob demanda ----------

	/** Le ate {@code max} linhas do cursor em segundo plano e entao chama {@code refresh}. */
	private void loadPage(QueryResult r, int max, Runnable refresh) {
		SqlExecutionEngine.ResultCursor c = r.cursor();
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
					((ResultTableModel) r.model()).editController().onRowsAppended(before);
					if (rows.size() < max) {
						c.exhausted = true;
						c.close();
						openCursors.remove(c);
					}
				} catch (Exception ex) {
					Throwable cause = (ex.getCause() != null) ? ex.getCause() : ex;
					com.nureal.ide.core.log.AppLogger.warning("Falha ao carregar mais linhas", ex);
					c.exhausted = true;
					c.close();
					openCursors.remove(c);
					owner.statusBar().setText(" Erro ao carregar mais linhas: " + cause.getMessage());
				}
				refresh.run();
			}
		}.execute();
	}

	/** Le todas as linhas restantes do cursor em segundo plano. */
	private void loadAll(QueryResult r, Runnable refresh) {
		SqlExecutionEngine.ResultCursor c = r.cursor();
		if (c == null || c.exhausted) {
			return;
		}
		owner.statusBar().setText(" Carregando todas as linhas...");
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
					owner.statusBar().setText(" Todas as linhas carregadas (" + r.model().getRowCount() + ").");
				} catch (Exception ex) {
					com.nureal.ide.core.log.AppLogger.warning("Falha ao carregar linhas", ex);
					owner.statusBar().setText(" Erro ao carregar linhas: " + ex.getMessage());
				}
				refresh.run();
			}
		}.execute();
	}

	void closeOpenCursors() {
		for (SqlExecutionEngine.ResultCursor c : openCursors) {
			c.close();
		}
		openCursors.clear();
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

	/** Exporta um resultado especifico (este) para um arquivo Excel. */
	private void exportResult(QueryResult r) {
		if (r.model() == null) {
			JOptionPane.showMessageDialog(owner, "Este resultado nao possui dados tabulares para exportar.",
					"Exportar para Excel", JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		File file = owner.chooseSaveFile(r.title());
		if (file != null) {
			List<ExcelExporter.TableSheet> sheets = new ArrayList<>();
			sheets.add(new ExcelExporter.TableSheet(r.title(), r.model()));
			sheets.add(instructionsSheet(List.of(r)));
			owner.doExport(sheets, file);
		}
	}

	/**
	 * Exporta este resultado para CSV (todas as linhas/colunas VISIVEIS na
	 * grade — respeita filtro/ordenacao atuais, ver {@link GridExporter}).
	 */
	private void exportResultCsv(JTable table, String title) {
		JFileChooser fc = new JFileChooser();
		fc.setDialogTitle("Exportar CSV");
		fc.setSelectedFile(new File(title + ".csv"));
		fc.setFileFilter(new FileNameExtensionFilter("CSV (*.csv)", "csv"));
		if (fc.showSaveDialog(owner) != JFileChooser.APPROVE_OPTION) {
			return;
		}
		File file = fc.getSelectedFile();
		if (!file.getName().toLowerCase(Locale.ROOT).endsWith(".csv")) {
			file = new File(file.getParentFile(), file.getName() + ".csv");
		}
		try {
			GridExporter.exportCsv(table, file.toPath());
		} catch (IOException ex) {
			owner.showError("Falha ao exportar CSV", ex);
		}
	}

	/**
	 * Exporta este resultado para JSON (mesmas linhas/colunas visiveis da
	 * grade — respeita filtro/ordenacao atuais, ver {@link GridExporter}).
	 * Ja existia so no menu de clique-direito da grade ({@code ResultContextMenu});
	 * revisao de UX pediu consolidar exportacao num unico lugar descobrivel
	 * (o botao "Exportar" desta barra), entao ganhou tambem este atalho.
	 */
	private void exportResultJson(JTable table, String title) {
		JFileChooser fc = new JFileChooser();
		fc.setDialogTitle("Exportar JSON");
		fc.setSelectedFile(new File(title + ".json"));
		fc.setFileFilter(new FileNameExtensionFilter("JSON (*.json)", "json"));
		if (fc.showSaveDialog(owner) != JFileChooser.APPROVE_OPTION) {
			return;
		}
		File file = fc.getSelectedFile();
		if (!file.getName().toLowerCase(Locale.ROOT).endsWith(".json")) {
			file = new File(file.getParentFile(), file.getName() + ".json");
		}
		try {
			GridExporter.exportJson(table, file.toPath());
		} catch (IOException ex) {
			owner.showError("Falha ao exportar JSON", ex);
		}
	}

	private void exportSingle(int idx) {
		if (idx < 0 || idx >= lastResults.size()) {
			return;
		}
		QueryResult r = lastResults.get(idx);
		if (r.model() == null) {
			JOptionPane.showMessageDialog(owner, "Esta aba nao possui dados tabulares para exportar.",
					"Exportar para Excel", JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		File file = owner.chooseSaveFile(r.title());
		if (file != null) {
			List<ExcelExporter.TableSheet> sheets = new ArrayList<>();
			sheets.add(new ExcelExporter.TableSheet(r.title(), r.model()));
			sheets.add(instructionsSheet(List.of(r)));
			owner.doExport(sheets, file);
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
			JOptionPane.showMessageDialog(owner, "Nenhum resultado tabular para exportar.", "Exportar para Excel",
					JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		sheets.add(instructionsSheet(lastResults));
		File file = owner.chooseSaveFile("resultados");
		if (file != null) {
			owner.doExport(sheets, file);
		}
	}

	/** Monta a aba "Instrucoes SQL" (Resultado x SQL executado), estilo PL/SQL Developer. */
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
		// Adaptador minimo: este modelo e um DefaultTableModel puro (nao um
		// ResultTableModel), entao nao implementa TabelaExportavel sozinho.
		TabelaExportavel exportable = new TabelaExportavel() {
			@Override
			public int linhas() {
				return m.getRowCount();
			}

			@Override
			public int colunas() {
				return m.getColumnCount();
			}

			@Override
			public String nomeColuna(int coluna) {
				return m.getColumnName(coluna);
			}

			@Override
			public Object valor(int linha, int coluna) {
				return m.getValueAt(linha, coluna);
			}
		};
		return new ExcelExporter.TableSheet("Instrucoes SQL", exportable);
	}
}
