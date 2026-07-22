package com.nureal.ide.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JRootPane;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTree;
import javax.swing.KeyStroke;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

import com.nureal.ide.core.log.AppLogger;
import com.nureal.ide.core.metadata.model.ColumnDetail;
import com.nureal.ide.core.metadata.model.ColumnInfo;
import com.nureal.ide.core.metadata.model.DbUserInfo;
import com.nureal.ide.core.metadata.model.ForeignKeyInfo;
import com.nureal.ide.core.metadata.model.IndexInfo;
import com.nureal.ide.core.metadata.model.SchemaForeignKey;
import com.nureal.ide.core.metadata.model.SchemaInfo;
import com.nureal.ide.core.metadata.model.TableDetails;
import com.nureal.ide.core.metadata.model.TableInfo;
import com.nureal.ide.core.sql.SqlTypeKind;
import com.nureal.ide.core.sql.TableAliasGenerator;
import com.nureal.ide.ui.components.NSearchField;

/**
 * Explorador de objetos do banco: arvore de tabelas/views/procedures/
 * functions/triggers, filtro, menus de contexto, DDL (criar/alterar/dropar
 * tabela/view/trigger/routine/schema), import CSV, manutencao de tabela,
 * gerar SELECT/JOIN e a tela de propriedades/metadados de um objeto.
 * <p>
 * Extraido do {@link MainWindow} (que tinha crescido para ~6800 linhas) para
 * reduzir o tamanho do arquivo e isolar esta responsabilidade — ver pedido do
 * usuario de dividir arquivos grandes demais. Mantem uma referencia de volta
 * pro {@code owner} (mesmo padrao de {@link Conexao} guardar seu proprio
 * estado) para reusar conexao ativa, schema atual, barra de status etc., sem
 * duplicar nenhum desses campos.
 */
final class ObjectExplorerController {

	private final MainWindow owner;

	private JTree objectTree;
	private NSearchField objectSearch;
	private JComponent objectBrowserPanel;

	/**
	 * Historico de objetos abertos a partir do editor SQL (CTRL+Clique ou
	 * F12), do mais antigo (fundo da pilha) ao mais recente (topo). ALT+Seta-
	 * esquerda ({@link #navigateBack}) volta um passo nesta pilha, reabrindo
	 * a tela de propriedades do objeto anterior — nao rastreia nada aberto
	 * via duplo-clique na arvore, so o que veio do editor mesmo.
	 */
	private final java.util.Deque<ObjNode> objectNavHistory = new java.util.ArrayDeque<>();

	private final ObjectDataTransfer dataTransfer;

	ObjectExplorerController(MainWindow owner) {
		this.owner = owner;
		this.dataTransfer = new ObjectDataTransfer(owner);
	}

	// ---------- Construcao do painel ----------

	JComponent buildObjectBrowser() {
		objectTree = new JTree(new DefaultTreeModel(new DefaultMutableTreeNode("Sem conexao")));
		objectTree.setRootVisible(true);
		objectTree.setShowsRootHandles(false);
		objectTree.putClientProperty("JTree.paintSelection", false);
		objectTree.setRowHeight(owner.scaledPx(ConnectionsPanel.DEFAULT_ROW_HEIGHT));
		objectTree.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 4));
		objectTree.setCellRenderer(new ObjectTreeCellRenderer());
		TreeHoverTracker.installOnTree(objectTree);
		objectTree.setToggleClickCount(0);
		objectTree.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
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

		objectSearch = new NSearchField("Buscar objeto...");
		objectSearch.setEnabled(false);
		objectSearch.onTextChange(this::applyObjectFilter);

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
		headerRow.add(owner.sectionHeader("OBJETOS"), BorderLayout.WEST);
		headerRow.add(headerButtons, BorderLayout.EAST);

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

	/** Mostra a raiz como texto simples (sem conexao/sem schema) e desabilita a busca — ver {@code MainWindow#activateWorkspace}. */
	void showDisconnected(String label) {
		objectSearch.setEnabled(false);
		objectTree.setModel(new DefaultTreeModel(new DefaultMutableTreeNode(label)));
	}

	/** Reaplica a altura de linha derivada do zoom/modo compacto (ver {@code MainWindow#refreshDynamicSizing}). */
	void setRowHeight(int px) {
		if (objectTree != null) {
			objectTree.setRowHeight(px);
		}
	}

	/** Reaplica o padding derivado do modo compacto (ver {@code MainWindow#applyDensityToPanels}). */
	void applyDensityBorder(int outer) {
		if (objectBrowserPanel != null) {
			objectBrowserPanel.setBorder(BorderFactory.createEmptyBorder(outer, outer, outer, outer));
		}
	}

	// ---------- Populacao / filtro da arvore ----------

	void populateTree(SchemaInfo schema) {
		owner.setCurrentSchema(schema);
		objectSearch.setEnabled(true);
		rebuildTree(objectSearch.getText());
	}

	private void applyObjectFilter() {
		if (owner.currentSchema() != null) {
			rebuildTree(objectSearch.getText());
		}
	}

	/**
	 * Recarrega os metadados do esquema atual (tabelas, views, procedures,
	 * functions, triggers) sem mudar a conexao nem a aba selecionada. Chamado
	 * automaticamente apos DDL bem-sucedido e tambem pelo botao de atualizar
	 * (icone) e pelo atalho Ctrl+R.
	 */
	void refreshObjectTree(boolean manual) {
		if (!owner.connectionManager().isConnected() || owner.currentSchema() == null) {
			if (manual && owner.statusBar() != null) {
				owner.statusBar().setText(" Conecte-se e abra um esquema antes de atualizar os objetos.");
			}
			return;
		}
		String schemaName = owner.currentSchema().name();
		if (manual && owner.statusBar() != null) {
			owner.statusBar().setText(" Atualizando objetos de " + schemaName + "...");
		}
		new SwingWorker<SchemaInfo, Void>() {
			@Override
			protected SchemaInfo doInBackground() throws Exception {
				Connection conn = owner.connectionManager().getConnection();
				return owner.metadataService().loadSchema(conn, schemaName);
			}

			@Override
			protected void done() {
				try {
					SchemaInfo schema = get();
					if (owner.activeWorkspace() != null) {
						owner.activeWorkspace().schema = schema;
					}
					owner.metadataCache().set(schema);
					owner.completionProvider().refresh(schema);
					owner.tableMetadataCache().clear();
					populateTree(schema);
					if (owner.statusBar() != null) {
						owner.statusBar().setText(" Objetos atualizados (" + schema.tables().size() + " tabelas).");
					}
				} catch (Exception ex) {
					owner.showError("Falha ao atualizar objetos", ex);
					if (owner.statusBar() != null) {
						owner.statusBar().setText(" Erro ao atualizar objetos.");
					}
				}
			}
		}.execute();
	}

	void buildSchemaPicker(List<String> schemas) {
		owner.setCurrentSchema(null);
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

	private void rebuildTree(String filter) {
		String f = filter == null ? "" : filter.trim().toLowerCase(Locale.ROOT);
		boolean filtering = !f.isEmpty();
		SchemaInfo schema = owner.currentSchema();

		DefaultMutableTreeNode root = new DefaultMutableTreeNode(
				new ObjNode(NodeType.SCHEMA, schema.name(), schema.name(), null, null, null));

		addTableCategory(root, "Tabelas", schema.tables(), NodeType.TABLE, "TABLE", f, filtering);
		addTableCategory(root, "Visualizacoes", schema.views(), NodeType.VIEW, "VIEW", f, filtering);
		addNameCategory(root, "Procedures", schema.procedures(), NodeType.ROUTINE, "PROCEDURE", f, filtering);
		addNameCategory(root, "Functions", schema.functions(), NodeType.ROUTINE, "FUNCTION", f, filtering);
		addNameCategory(root, "Triggers", schema.triggers(), NodeType.TRIGGER, "TRIGGER", f, filtering);

		if (filtering && root.getChildCount() == 0) {
			root.add(new DefaultMutableTreeNode(new ObjNode(NodeType.EMPTY_MESSAGE,
					"Nenhum objeto encontrado para \"" + filter.trim() + "\"", "", null, null, null)));
		}

		objectTree.setModel(new DefaultTreeModel(root));
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
			for (ColumnInfo c : t.columns()) {
				tn.add(new DefaultMutableTreeNode(
						new ObjNode(NodeType.COLUMN, c.name() + " : " + c.type(), c.name(), kind, null, c.type())));
			}
			cat.add(tn);
			shown++;
		}
		if (!filtering || shown > 0) {
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

	// ---------- Clique / menus de contexto ----------

	private void handleObjectTreeDoubleClick(MouseEvent e) {
		TreePath path = objectTree.getPathForLocation(e.getX(), e.getY());
		if (path == null) {
			return;
		}
		Object node = ((DefaultMutableTreeNode) path.getLastPathComponent()).getUserObject();
		if (node instanceof ObjNode obj) {
			if (obj.type() == NodeType.SCHEMA_PICK) {
				owner.openSchema(obj.name());
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
		if (objectTree.isExpanded(path)) {
			objectTree.collapsePath(path);
		} else {
			objectTree.expandPath(path);
		}
	}

	private void pasteObjectNameIntoEditor(String name) {
		SqlEditorPane editor = owner.currentEditor();
		if (editor == null) {
			return;
		}
		editor.textArea().replaceSelection(name);
		editor.textArea().requestFocusInWindow();
	}

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

	private JPopupMenu buildSchemaRootContextMenu() {
		JPopupMenu menu = new JPopupMenu();
		JMenuItem switchSchema = new JMenuItem("Trocar esquema...");
		boolean canSwitch = owner.activeWorkspace() != null
				&& owner.activeWorkspace().schemaList != null
				&& !owner.activeWorkspace().schemaList.isEmpty();
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
		JMenuItem manageUsers = new JMenuItem("Gerenciar usuarios e privilegios...");
		manageUsers.addActionListener(a -> openUserManagement());
		menu.add(manageUsers);
		JMenuItem processList = new JMenuItem("Sessoes ativas (PROCESSLIST)...");
		processList.addActionListener(a -> openProcessList());
		menu.add(processList);
		JMenuItem serverStatus = new JMenuItem("Variaveis e status do servidor...");
		serverStatus.addActionListener(a -> openServerStatus());
		menu.add(serverStatus);
		menu.addSeparator();
		JMenuItem erDiagram = new JMenuItem("Diagrama ER...");
		erDiagram.addActionListener(a -> openErDiagram());
		menu.add(erDiagram);
		JMenuItem eventsReplication = new JMenuItem("Eventos e replicacao...");
		eventsReplication.addActionListener(a -> openEventsReplication());
		menu.add(eventsReplication);
		JMenuItem backupRestore = new JMenuItem("Backup e restauracao...");
		backupRestore.addActionListener(a -> dataTransfer.openBackupRestore());
		menu.add(backupRestore);
		return menu;
	}

	private JPopupMenu buildSchemaPickContextMenu(String schemaName) {
		JPopupMenu menu = new JPopupMenu();
		JMenuItem open = new JMenuItem("Abrir");
		open.addActionListener(a -> owner.openSchema(schemaName));
		menu.add(open);
		JMenuItem erDiagram = new JMenuItem("Diagrama ER...");
		erDiagram.addActionListener(a -> owner.openSchema(schemaName, this::openErDiagram));
		menu.add(erDiagram);
		menu.addSeparator();
		JMenuItem delete = new JMenuItem("Excluir esquema...");
		delete.addActionListener(a -> deleteSchema(schemaName));
		menu.add(delete);
		return menu;
	}

	private JPopupMenu buildTablesCategoryContextMenu() {
		JPopupMenu menu = new JPopupMenu();
		JMenuItem createTable = new JMenuItem("Nova tabela...");
		createTable.addActionListener(a -> createTable());
		menu.add(createTable);
		return menu;
	}

	private JPopupMenu buildViewsCategoryContextMenu() {
		JPopupMenu menu = new JPopupMenu();
		JMenuItem createViewItem = new JMenuItem("Nova view...");
		createViewItem.addActionListener(a -> createView());
		menu.add(createViewItem);
		return menu;
	}

	private JPopupMenu buildTriggersCategoryContextMenu() {
		JPopupMenu menu = new JPopupMenu();
		JMenuItem createTriggerItem = new JMenuItem("Novo trigger...");
		createTriggerItem.addActionListener(a -> createTrigger());
		menu.add(createTriggerItem);
		return menu;
	}

	private JPopupMenu buildRoutinesCategoryContextMenu(String kind) {
		JPopupMenu menu = new JPopupMenu();
		JMenuItem createRoutineItem =
				new JMenuItem("PROCEDURE".equals(kind) ? "Nova procedure..." : "Nova function...");
		createRoutineItem.addActionListener(a -> createRoutine(kind));
		menu.add(createRoutineItem);
		return menu;
	}

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

	private void switchSchema() {
		if (owner.activeWorkspace() == null || !owner.activeWorkspace().mgr.isConnected()) {
			owner.statusBar().setText(" Conecte-se a uma base antes de trocar de esquema.");
			return;
		}
		if (owner.activeWorkspace().schemaList == null || owner.activeWorkspace().schemaList.isEmpty()) {
			owner.statusBar().setText(" Esta conexao usa um esquema fixo definido no cadastro.");
			return;
		}
		owner.activeWorkspace().schema = null;
		owner.setCurrentSchema(null);
		buildSchemaPicker(owner.activeWorkspace().schemaList);
		owner.statusBar().setText(" Selecione um esquema (" + owner.activeWorkspace().schemaList.size() + " disponiveis).");
	}

	private void createSchema() {
		if (owner.activeWorkspace() == null || !owner.activeWorkspace().mgr.isConnected()) {
			owner.statusBar().setText(" Conecte-se a um servidor antes de criar um esquema.");
			return;
		}
		String input = JOptionPane.showInputDialog(owner, "Nome do novo esquema:", "");
		if (input == null || input.trim().isEmpty()) {
			return;
		}
		String schemaName = input.trim();
		Conexao ws = owner.activeWorkspace();
		owner.statusBar().setText(" Criando esquema \"" + schemaName + "\"...");
		new SwingWorker<List<String>, Void>() {
			@Override
			protected List<String> doInBackground() throws Exception {
				Connection conn = ws.mgr.getConnection();
				try (Statement st = conn.createStatement()) {
					st.executeUpdate(owner.dialect().createSchemaStatement(schemaName));
				}
				return owner.metadataService().listSchemas(conn);
			}

			@Override
			protected void done() {
				try {
					List<String> schemas = get();
					ws.schemaList = schemas;
					owner.statusBar().setText(" Esquema \"" + schemaName + "\" criado.");
					if (ws == owner.activeWorkspace() && ws.schema == null) {
						buildSchemaPicker(schemas);
					}
					int open = JOptionPane.showConfirmDialog(owner,
							"Esquema \"" + schemaName + "\" criado.\n\nDeseja abri-lo agora?",
							"Criar esquema", JOptionPane.YES_NO_OPTION);
					if (open == JOptionPane.YES_OPTION && ws == owner.activeWorkspace()) {
						owner.openSchema(schemaName);
					}
				} catch (Exception ex) {
					owner.showError("Falha ao criar esquema", ex);
					owner.statusBar().setText(" Falha ao criar esquema.");
				}
			}
		}.execute();
	}

	private void deleteSchema(String schemaName) {
		if (owner.activeWorkspace() == null || !owner.activeWorkspace().mgr.isConnected()) {
			owner.statusBar().setText(" Conecte-se a um servidor antes de excluir um esquema.");
			return;
		}
		String typed = JOptionPane.showInputDialog(owner,
				"Isto apaga TODAS as tabelas, dados, views, triggers e procedures\n"
						+ "do esquema \"" + schemaName + "\" — SEM VOLTA.\n\n"
						+ "Para confirmar, digite o nome exato do esquema:",
				"Excluir esquema \"" + schemaName + "\"", JOptionPane.WARNING_MESSAGE);
		if (typed == null) {
			return;
		}
		if (!typed.equals(schemaName)) {
			JOptionPane.showMessageDialog(owner,
					"O nome digitado nao confere com \"" + schemaName + "\". Nada foi excluido.",
					"Excluir esquema", JOptionPane.WARNING_MESSAGE);
			return;
		}
		Conexao ws = owner.activeWorkspace();
		boolean wasOpenSchema = owner.currentSchema() != null && schemaName.equals(owner.currentSchema().name());
		owner.statusBar().setText(" Excluindo esquema \"" + schemaName + "\"...");
		new SwingWorker<List<String>, Void>() {
			@Override
			protected List<String> doInBackground() throws Exception {
				Connection conn = ws.mgr.getConnection();
				try (Statement st = conn.createStatement()) {
					st.executeUpdate(owner.dialect().dropSchemaStatement(schemaName));
				}
				return owner.metadataService().listSchemas(conn);
			}

			@Override
			protected void done() {
				try {
					List<String> schemas = get();
					ws.schemaList = schemas;
					owner.statusBar().setText(" Esquema \"" + schemaName + "\" excluido.");
					if (wasOpenSchema && ws == owner.activeWorkspace()) {
						owner.setCurrentSchema(null);
					}
					if (ws == owner.activeWorkspace()) {
						buildSchemaPicker(schemas);
						owner.updateWorkspaceContextBar();
					}
				} catch (Exception ex) {
					owner.showError("Falha ao excluir o esquema", ex);
					owner.statusBar().setText(" Falha ao excluir esquema.");
				}
			}
		}.execute();
	}

	private void createTable() {
		if (owner.activeWorkspace() == null || !owner.activeWorkspace().mgr.isConnected() || owner.currentSchema() == null) {
			owner.statusBar().setText(" Abra um esquema antes de criar uma tabela.");
			return;
		}
		Set<String> existingNames = new HashSet<>();
		for (TableInfo t : owner.currentSchema().tables()) {
			existingNames.add(t.name().toLowerCase(Locale.ROOT));
		}
		for (TableInfo v : owner.currentSchema().views()) {
			existingNames.add(v.name().toLowerCase(Locale.ROOT));
		}
		Conexao ws = owner.activeWorkspace();
		String schemaName = owner.currentSchema().name();
		DdlAssistantDialog.openCreate(owner, owner.currentSchema(), owner.dialect(),
				name -> existingNames.contains(name.toLowerCase(Locale.ROOT)),
				(statements, onOk, onErr) -> runDdlStatements(ws, statements, onOk, onErr),
				this::sendDdlToEditor,
				() -> {
					if (ws == owner.activeWorkspace() && schemaName.equals(owner.currentSchema().name())) {
						refreshObjectTree(false);
					}
				});
	}

	private void alterTable(ObjNode obj) {
		if (owner.activeWorkspace() == null || !owner.activeWorkspace().mgr.isConnected() || owner.currentSchema() == null) {
			owner.statusBar().setText(" Abra um esquema antes de alterar uma tabela.");
			return;
		}
		Conexao ws = owner.activeWorkspace();
		String schemaName = owner.currentSchema().name();
		String tableName = obj.name();
		owner.statusBar().setText(" Carregando estrutura de \"" + tableName + "\"...");
		new SwingWorker<TableDetails, Void>() {
			@Override
			protected TableDetails doInBackground() throws Exception {
				Connection conn = ws.mgr.getConnection();
				return owner.metadataService().loadTableDetails(conn, schemaName, tableName);
			}

			@Override
			protected void done() {
				try {
					TableDetails details = get();
					owner.statusBar().setText(" Pronto.");
					DdlAssistantDialog.openAlter(owner, owner.currentSchema(), tableName, details, owner.dialect(),
							(statements, onOk, onErr) -> runDdlStatements(ws, statements, onOk, onErr),
							ObjectExplorerController.this::sendDdlToEditor,
							() -> {
								if (ws == owner.activeWorkspace() && schemaName.equals(owner.currentSchema().name())) {
									refreshObjectTree(false);
								}
							});
				} catch (Exception ex) {
					owner.showError("Falha ao carregar estrutura da tabela", ex);
					owner.statusBar().setText(" Falha ao carregar estrutura da tabela.");
				}
			}
		}.execute();
	}

	private void createView() {
		if (owner.activeWorkspace() == null || !owner.activeWorkspace().mgr.isConnected() || owner.currentSchema() == null) {
			owner.statusBar().setText(" Abra um esquema antes de criar uma view.");
			return;
		}
		Set<String> existingNames = new HashSet<>();
		for (TableInfo t : owner.currentSchema().tables()) {
			existingNames.add(t.name().toLowerCase(Locale.ROOT));
		}
		for (TableInfo v : owner.currentSchema().views()) {
			existingNames.add(v.name().toLowerCase(Locale.ROOT));
		}
		Conexao ws = owner.activeWorkspace();
		String schemaName = owner.currentSchema().name();
		ViewBuilderDialog.openCreate(owner, owner.dialect(),
				name -> existingNames.contains(name.toLowerCase(Locale.ROOT)),
				(statements, onOk, onErr) -> runDdlStatements(ws, statements, onOk, onErr),
				this::sendDdlToEditor,
				() -> {
					if (ws == owner.activeWorkspace() && schemaName.equals(owner.currentSchema().name())) {
						refreshObjectTree(false);
					}
				});
	}

	private void editView(ObjNode obj) {
		if (owner.activeWorkspace() == null || !owner.activeWorkspace().mgr.isConnected() || owner.currentSchema() == null) {
			owner.statusBar().setText(" Abra um esquema antes de editar uma view.");
			return;
		}
		Conexao ws = owner.activeWorkspace();
		String schemaName = owner.currentSchema().name();
		String viewName = obj.name();
		owner.statusBar().setText(" Carregando definicao de \"" + viewName + "\"...");
		new SwingWorker<String, Void>() {
			@Override
			protected String doInBackground() throws Exception {
				Connection conn = ws.mgr.getConnection();
				String sql = owner.dialect().definitionQuery("VIEW", viewName);
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
					owner.statusBar().setText(" Pronto.");
					ViewBuilderDialog.openEdit(owner, owner.dialect(), viewName, rawDefinition,
							(statements, onOk, onErr) -> runDdlStatements(ws, statements, onOk, onErr),
							ObjectExplorerController.this::sendDdlToEditor,
							() -> {
								if (ws == owner.activeWorkspace() && schemaName.equals(owner.currentSchema().name())) {
									refreshObjectTree(false);
								}
							});
				} catch (Exception ex) {
					owner.showError("Falha ao carregar a definicao da view", ex);
					owner.statusBar().setText(" Falha ao carregar a definicao da view.");
				}
			}
		}.execute();
	}

	private void dropView(ObjNode obj) {
		if (owner.activeWorkspace() == null || !owner.activeWorkspace().mgr.isConnected() || owner.currentSchema() == null) {
			owner.statusBar().setText(" Abra um esquema antes de excluir uma view.");
			return;
		}
		String viewName = obj.name();
		int choice = JOptionPane.showConfirmDialog(owner,
				"Excluir a view \"" + viewName + "\" permanentemente? Esta operacao nao pode ser desfeita.",
				"Excluir view", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
		if (choice != JOptionPane.YES_OPTION) {
			return;
		}
		Conexao ws = owner.activeWorkspace();
		String schemaName = owner.currentSchema().name();
		runDdlStatements(ws, List.of(owner.dialect().dropViewStatement(viewName)), () -> {
			owner.statusBar().setText(" View \"" + viewName + "\" excluida.");
			if (ws == owner.activeWorkspace() && schemaName.equals(owner.currentSchema().name())) {
				refreshObjectTree(false);
			}
		}, ex -> {
			owner.statusBar().setText(" Falha ao excluir a view.");
			owner.showError("Falha ao excluir a view", ex);
		});
	}

	private void createTrigger() {
		if (owner.activeWorkspace() == null || !owner.activeWorkspace().mgr.isConnected() || owner.currentSchema() == null) {
			owner.statusBar().setText(" Abra um esquema antes de criar um trigger.");
			return;
		}
		List<String> tableNames = owner.currentSchema().tables().stream().map(TableInfo::name).toList();
		Set<String> existingNames = new HashSet<>();
		for (String t : owner.currentSchema().triggers()) {
			existingNames.add(t.toLowerCase(Locale.ROOT));
		}
		Conexao ws = owner.activeWorkspace();
		String schemaName = owner.currentSchema().name();
		TriggerBuilderDialog.openCreate(owner, owner.dialect(), tableNames,
				name -> existingNames.contains(name.toLowerCase(Locale.ROOT)),
				(statements, onOk, onErr) -> runDdlStatements(ws, statements, onOk, onErr),
				this::sendDdlToEditor,
				() -> {
					if (ws == owner.activeWorkspace() && schemaName.equals(owner.currentSchema().name())) {
						refreshObjectTree(false);
					}
				});
	}

	private void editTrigger(ObjNode obj) {
		if (owner.activeWorkspace() == null || !owner.activeWorkspace().mgr.isConnected() || owner.currentSchema() == null) {
			owner.statusBar().setText(" Abra um esquema antes de editar um trigger.");
			return;
		}
		Conexao ws = owner.activeWorkspace();
		String schemaName = owner.currentSchema().name();
		String triggerName = obj.name();
		List<String> tableNames = owner.currentSchema().tables().stream().map(TableInfo::name).toList();
		owner.statusBar().setText(" Carregando definicao de \"" + triggerName + "\"...");
		new SwingWorker<String, Void>() {
			@Override
			protected String doInBackground() throws Exception {
				Connection conn = ws.mgr.getConnection();
				String sql = owner.dialect().definitionQuery("TRIGGER", triggerName);
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
					owner.statusBar().setText(" Pronto.");
					TriggerBuilderDialog.openEdit(owner, owner.dialect(), tableNames, triggerName, rawDefinition,
							(statements, onOk, onErr) -> runDdlStatements(ws, statements, onOk, onErr),
							ObjectExplorerController.this::sendDdlToEditor,
							() -> {
								if (ws == owner.activeWorkspace() && schemaName.equals(owner.currentSchema().name())) {
									refreshObjectTree(false);
								}
							});
				} catch (Exception ex) {
					owner.showError("Falha ao carregar a definicao do trigger", ex);
					owner.statusBar().setText(" Falha ao carregar a definicao do trigger.");
				}
			}
		}.execute();
	}

	private void dropTrigger(ObjNode obj) {
		if (owner.activeWorkspace() == null || !owner.activeWorkspace().mgr.isConnected() || owner.currentSchema() == null) {
			owner.statusBar().setText(" Abra um esquema antes de excluir um trigger.");
			return;
		}
		String triggerName = obj.name();
		int choice = JOptionPane.showConfirmDialog(owner,
				"Excluir o trigger \"" + triggerName + "\" permanentemente? Esta operacao nao pode ser desfeita.",
				"Excluir trigger", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
		if (choice != JOptionPane.YES_OPTION) {
			return;
		}
		Conexao ws = owner.activeWorkspace();
		String schemaName = owner.currentSchema().name();
		runDdlStatements(ws, List.of(owner.dialect().dropTriggerStatement(triggerName)), () -> {
			owner.statusBar().setText(" Trigger \"" + triggerName + "\" excluido.");
			if (ws == owner.activeWorkspace() && schemaName.equals(owner.currentSchema().name())) {
				refreshObjectTree(false);
			}
		}, ex -> {
			owner.statusBar().setText(" Falha ao excluir o trigger.");
			owner.showError("Falha ao excluir o trigger", ex);
		});
	}

	private void createRoutine(String initialKind) {
		if (owner.activeWorkspace() == null || !owner.activeWorkspace().mgr.isConnected() || owner.currentSchema() == null) {
			owner.statusBar().setText(" Abra um esquema antes de criar uma procedure/function.");
			return;
		}
		Set<String> existingProcedures = new HashSet<>();
		for (String p : owner.currentSchema().procedures()) {
			existingProcedures.add(p.toLowerCase(Locale.ROOT));
		}
		Set<String> existingFunctions = new HashSet<>();
		for (String f : owner.currentSchema().functions()) {
			existingFunctions.add(f.toLowerCase(Locale.ROOT));
		}
		Conexao ws = owner.activeWorkspace();
		String schemaName = owner.currentSchema().name();
		RoutineBuilderDialog.openCreate(owner, owner.dialect(), initialKind,
				(kind, name) -> ("PROCEDURE".equals(kind) ? existingProcedures : existingFunctions)
						.contains(name.toLowerCase(Locale.ROOT)),
				(statements, onOk, onErr) -> runDdlStatements(ws, statements, onOk, onErr),
				this::sendDdlToEditor,
				() -> {
					if (ws == owner.activeWorkspace() && schemaName.equals(owner.currentSchema().name())) {
						refreshObjectTree(false);
					}
				});
	}

	private void dropRoutine(ObjNode obj) {
		if (owner.activeWorkspace() == null || !owner.activeWorkspace().mgr.isConnected() || owner.currentSchema() == null) {
			owner.statusBar().setText(" Abra um esquema antes de excluir uma procedure/function.");
			return;
		}
		boolean isProcedure = "PROCEDURE".equals(obj.kind());
		String routineName = obj.name();
		String label = isProcedure ? "procedure" : "function";
		int choice = JOptionPane.showConfirmDialog(owner,
				"Excluir a " + label + " \"" + routineName + "\" permanentemente? Esta operacao nao pode ser desfeita.",
				"Excluir " + label, JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
		if (choice != JOptionPane.YES_OPTION) {
			return;
		}
		Conexao ws = owner.activeWorkspace();
		String schemaName = owner.currentSchema().name();
		String dropSql = isProcedure ? owner.dialect().dropProcedureStatement(routineName)
				: owner.dialect().dropFunctionStatement(routineName);
		runDdlStatements(ws, List.of(dropSql), () -> {
			owner.statusBar().setText(" " + (isProcedure ? "Procedure" : "Function") + " \"" + routineName + "\" excluida.");
			if (ws == owner.activeWorkspace() && schemaName.equals(owner.currentSchema().name())) {
				refreshObjectTree(false);
			}
		}, ex -> {
			owner.statusBar().setText(" Falha ao excluir a " + label + ".");
			owner.showError("Falha ao excluir a " + label, ex);
		});
	}

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

	void runQuery(Conexao ws, String sql, java.util.function.Consumer<List<Object[]>> onRows,
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

	void runQueryWithColumns(Conexao ws, String sql,
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

	private void openUserManagement() {
		if (owner.activeWorkspace() == null || !owner.activeWorkspace().mgr.isConnected()) {
			owner.statusBar().setText(" Conecte-se a um servidor antes de gerenciar usuarios.");
			return;
		}
		Conexao ws = owner.activeWorkspace();
		String schemaNameNow = owner.currentSchema() != null ? owner.currentSchema().name() : null;
		List<String> currentSchemaTables = owner.currentSchema() != null
				? owner.currentSchema().tables().stream().map(TableInfo::name).toList()
				: List.of();
		owner.statusBar().setText(" Carregando usuarios do servidor...");
		runQuery(ws, owner.dialect().listUsersQuery(), userRows -> {
			List<DbUserInfo> users = new ArrayList<>();
			for (Object[] row : userRows) {
				String user = String.valueOf(row[0]);
				String host = String.valueOf(row[1]);
				boolean locked = row.length > 2 && "Y".equalsIgnoreCase(String.valueOf(row[2]));
				boolean expired = row.length > 3 && "Y".equalsIgnoreCase(String.valueOf(row[3]));
				users.add(new DbUserInfo(user, host, locked, expired));
			}
			owner.statusBar().setText(" Carregando lista de esquemas...");
			runQuery(ws, owner.dialect().schemasQuery(), schemaRows -> {
				List<String> schemaNames = new ArrayList<>();
				for (Object[] row : schemaRows) {
					schemaNames.add(String.valueOf(row[0]));
				}
				owner.statusBar().setText(" Pronto.");
				UserManagementDialog.open(owner, owner.dialect(), users, schemaNames, schemaNameNow, currentSchemaTables,
						(statements, onOk, onErr) -> runDdlStatements(ws, statements, onOk, onErr),
						(sql, onRows, onErr) -> runQuery(ws, sql, onRows, onErr));
			}, ex -> {
				owner.statusBar().setText(" Falha ao listar esquemas.");
				JOptionPane.showMessageDialog(owner, "Falha ao listar esquemas:\n" + ex.getMessage(),
						"Usuarios e privilegios", JOptionPane.ERROR_MESSAGE);
			});
		}, ex -> {
			owner.statusBar().setText(" Falha ao listar usuarios.");
			JOptionPane.showMessageDialog(owner,
					"Falha ao listar usuarios do servidor (a conexao pode nao ter privilegio para ler mysql.user):\n"
							+ ex.getMessage(),
					"Usuarios e privilegios", JOptionPane.ERROR_MESSAGE);
		});
	}

	private void openProcessList() {
		if (owner.activeWorkspace() == null || !owner.activeWorkspace().mgr.isConnected()) {
			owner.statusBar().setText(" Conecte-se a um servidor antes de ver as sessoes ativas.");
			return;
		}
		Conexao ws = owner.activeWorkspace();
		ProcessListDialog.open(owner, owner.dialect(), (sql, onRows, onErr) -> runQuery(ws, sql, onRows, onErr),
				(statements, onOk, onErr) -> runDdlStatements(ws, statements, onOk, onErr));
	}

	private void openServerStatus() {
		if (owner.activeWorkspace() == null || !owner.activeWorkspace().mgr.isConnected()) {
			owner.statusBar().setText(" Conecte-se a um servidor antes de ver variaveis/status.");
			return;
		}
		Conexao ws = owner.activeWorkspace();
		ServerStatusDialog.open(owner, owner.dialect(), (sql, onRows, onErr) -> runQuery(ws, sql, onRows, onErr));
	}

	private void openErDiagram() {
		if (owner.activeWorkspace() == null || !owner.activeWorkspace().mgr.isConnected() || owner.currentSchema() == null) {
			owner.statusBar().setText(" Abra um esquema antes de ver o diagrama ER.");
			return;
		}
		Conexao ws = owner.activeWorkspace();
		String schemaName = owner.currentSchema().name();
		List<TableInfo> tables = owner.currentSchema().tables();
		owner.statusBar().setText(" Carregando relacionamentos de \"" + schemaName + "\"...");
		new SwingWorker<Object[], Void>() {
			@Override
			protected Object[] doInBackground() throws Exception {
				Connection conn = ws.mgr.getConnection();
				List<SchemaForeignKey> fks = owner.metadataService().loadSchemaForeignKeys(conn, schemaName);
				Map<String, Set<String>> pks = owner.metadataService().loadSchemaPrimaryKeys(conn, schemaName);
				return new Object[] { fks, pks };
			}

			@SuppressWarnings("unchecked")
			@Override
			protected void done() {
				try {
					Object[] result = get();
					owner.statusBar().setText(" Pronto.");
					ErDiagramWindow.open(owner, schemaName, tables,
							(List<SchemaForeignKey>) result[0], (Map<String, Set<String>>) result[1]);
				} catch (Exception ex) {
					owner.showError("Falha ao carregar relacionamentos do esquema", ex);
					owner.statusBar().setText(" Falha ao carregar o diagrama ER.");
				}
			}
		}.execute();
	}

	private void openEventsReplication() {
		if (owner.activeWorkspace() == null || !owner.activeWorkspace().mgr.isConnected() || owner.currentSchema() == null) {
			owner.statusBar().setText(" Abra um esquema antes de ver eventos/replicacao.");
			return;
		}
		Conexao ws = owner.activeWorkspace();
		String schemaName = owner.currentSchema().name();
		EventsReplicationDialog.open(owner, schemaName, owner.dialect(),
				(sql, onRows, onErr) -> runQuery(ws, sql, onRows, onErr),
				(sql, onResult, onErr) -> runQueryWithColumns(ws, sql, onResult, onErr));
	}

	/** Abre uma aba de editor nova com o DDL gerado pelo assistente — botao "Enviar para o editor". */
	private void sendDdlToEditor(String ddl) {
		String title = "DDL";
		int n = 1;
		while (owner.titleExists(title)) {
			title = "DDL " + (++n);
		}
		if (owner.addQueryTab(title, ddl)) {
			owner.statusBar().setText(" DDL enviado para uma nova aba do editor.");
		} else {
			owner.statusBar().setText(" Nao foi possivel abrir uma aba nova (limite de abas atingido).");
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
			importCsvItem.addActionListener(a -> dataTransfer.importCsv(obj));
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

	private JMenu buildTableMaintenanceMenu(ObjNode obj) {
		JMenu menu = new JMenu("Manutencao");
		menu.add(maintenanceItem("Otimizar (OPTIMIZE TABLE)", obj, owner.dialect()::optimizeTableStatement));
		menu.add(maintenanceItem("Recalcular estatisticas (ANALYZE TABLE)", obj, owner.dialect()::analyzeTableStatement));
		menu.add(maintenanceItem("Verificar integridade (CHECK TABLE)", obj, owner.dialect()::checkTableStatement));
		return menu;
	}

	private JMenuItem maintenanceItem(String label, ObjNode obj,
			java.util.function.BiFunction<String, String, String> statementBuilder) {
		JMenuItem item = new JMenuItem(label);
		item.addActionListener(a -> runTableMaintenance(obj, statementBuilder.apply(owner.currentSchema().name(), obj.name())));
		return item;
	}

	private void runTableMaintenance(ObjNode obj, String sql) {
		if (owner.activeWorkspace() == null || !owner.activeWorkspace().mgr.isConnected() || owner.currentSchema() == null) {
			owner.statusBar().setText(" Abra um esquema antes de rodar manutencao de tabela.");
			return;
		}
		Conexao ws = owner.activeWorkspace();
		owner.statusBar().setText(" Executando manutencao em \"" + obj.name() + "\"...");
		runQuery(ws, sql, rows -> {
			owner.statusBar().setText(" Pronto.");
			StringBuilder sb = new StringBuilder();
			for (Object[] row : rows) {
				String msgType = row.length > 2 ? String.valueOf(row[2]) : "";
				String msgText = row.length > 3 ? String.valueOf(row[3]) : "";
				sb.append(msgType).append(": ").append(msgText).append('\n');
			}
			JOptionPane.showMessageDialog(owner,
					sb.length() > 0 ? sb.toString() : "Concluido (sem mensagem do servidor).",
					"Manutencao de tabela — " + obj.name(), JOptionPane.INFORMATION_MESSAGE);
		}, ex -> {
			owner.statusBar().setText(" Falha na manutencao de \"" + obj.name() + "\".");
			JOptionPane.showMessageDialog(owner, "Falha ao executar manutencao:\n" + ex.getMessage(),
					"Manutencao de tabela", JOptionPane.ERROR_MESSAGE);
		});
	}

	private void generateSelect(ObjNode obj) {
		List<ColumnInfo> cols = (obj.table() != null) ? obj.table().columns() : List.of();
		StringBuilder sql = new StringBuilder("SELECT");
		if (cols.isEmpty()) {
			sql.append(" *\n");
		} else {
			sql.append('\n');
			for (int i = 0; i < cols.size(); i++) {
				sql.append("    ").append(cols.get(i).name());
				sql.append(i < cols.size() - 1 ? ",\n" : "\n");
			}
		}
		sql.append("FROM ").append(obj.name()).append(";\n");

		String baseTitle = "SELECT " + obj.name();
		String title = baseTitle;
		int n = 1;
		while (owner.titleExists(title)) {
			title = baseTitle + " " + (++n);
		}
		if (owner.addQueryTab(title, sql.toString())) {
			owner.statusBar().setText(" SELECT gerado numa nova aba do editor.");
		} else {
			owner.statusBar().setText(" Nao foi possivel abrir uma aba nova (limite de abas atingido).");
		}
	}

	private JMenuItem buildGenerateJoinItem(ObjNode obj) {
		String schemaName = (owner.currentSchema() != null) ? owner.currentSchema().name() : null;
		TableDetails details = owner.tableMetadataCache().get(owner.connectionManager(), schemaName, obj.name(), () -> { });
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
		JMenu submenu = new JMenu("Gerar JOIN");
		for (ForeignKeyInfo fk : fks) {
			String label = fk.referencedTable() + " (" + String.join(", ", fk.columns()) + ")";
			submenu.add(buildJoinTypeMenu(obj.name(), fk, label));
		}
		return submenu;
	}

	List<ForeignKeyInfo> lookupForeignKeysForCompletion(String tableName) {
		if (owner.currentSchema() == null) {
			return List.of();
		}
		TableDetails details = owner.tableMetadataCache().get(owner.connectionManager(), owner.currentSchema().name(), tableName, () -> { });
		return (details != null) ? details.foreignKeys() : List.of();
	}

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

	private void insertJoinStatement(String tableName, ForeignKeyInfo fk, String joinKeyword) {
		SqlEditorPane editor = owner.currentEditor();
		if (editor == null) {
			owner.statusBar().setText(" Abra uma aba de editor antes de gerar o JOIN.");
			return;
		}
		String refTable = fk.referencedTable();
		String alias = TableAliasGenerator.deriveAlias(tableName);
		String refAlias = TableAliasGenerator.deriveDistinctAlias(refTable, tableName, alias);
		List<String> cols = fk.columns();
		List<String> refCols = fk.referencedColumns();
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

	private static void insertOnNewLineBelowCursor(org.fife.ui.rsyntaxtextarea.RSyntaxTextArea textArea, String text) {
		try {
			int caret = textArea.getCaretPosition();
			int line = textArea.getLineOfOffset(caret);
			int lineEnd = textArea.getLineEndOffset(line);
			boolean lastLine = (line == textArea.getLineCount() - 1);
			String insertText = lastLine ? ("\n" + text + "\n") : (text + "\n");
			textArea.insert(insertText, lineEnd);
			textArea.setCaretPosition(Math.min(lineEnd + insertText.length(), textArea.getDocument().getLength()));
		} catch (javax.swing.text.BadLocationException ex) {
			AppLogger.warning("Falha ao inserir SQL gerado no editor", ex);
		}
	}

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

	private static boolean isOpenableObject(NodeType type) {
		return type == NodeType.TABLE || type == NodeType.VIEW
				|| type == NodeType.ROUTINE || type == NodeType.TRIGGER;
	}

	void openEditorObject(String kind, String name, TableInfo table) {
		NodeType type = switch (kind) {
			case "TABLE" -> NodeType.TABLE;
			case "VIEW" -> NodeType.VIEW;
			case "TRIGGER" -> NodeType.TRIGGER;
			default -> NodeType.ROUTINE;
		};
		ObjNode node = new ObjNode(type, name, name, kind, table, null);
		objectNavHistory.push(node);
		showObjectProperties(node);
	}

	void navigateBack() {
		if (!objectNavHistory.isEmpty()) {
			objectNavHistory.pop();
		}
		ObjNode previous = objectNavHistory.peek();
		if (previous != null) {
			showObjectProperties(previous);
		} else if (owner.statusBar() != null) {
			owner.statusBar().setText(" Sem objeto anterior no historico de navegacao.");
		}
	}

	private void showObjectProperties(ObjNode obj) {
		JComponent title = SelectableLabel.of(obj.name());
		title.setFont(title.getFont().deriveFont(14f));
		Typography.primary(title);
		JComponent sub = SelectableLabel.of(prettyKind(obj.kind()) + "  ·  " + owner.currentSchema().name());
		Runnable applySubColor = () -> Typography.tertiary(sub);
		applySubColor.run();

		JDialog dialog = new JDialog(owner, prettyKind(obj.kind()) + " - " + obj.name(), false) {
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
		dialog.setLocationRelativeTo(owner);
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
			colModel = metadataModel("Colunas", "Coluna", "Tipo", "Nulo", "Chave", "Default", "Extra",
					"Comentario");
			tabs.addTab("Colunas", metadataGrid("Colunas", colModel, 1));
			if ("TABLE".equals(obj.kind())) {
				idxModel = metadataModel("Indices", "Indice", "Unico", "Tipo", "Colunas");
				tabs.addTab("Indices", metadataGrid("Indices", idxModel));
				fkModel = metadataModel("Chaves estrangeiras", "Constraint", "Coluna(s)", "Referencia",
						"Coluna(s) ref.", "On Update", "On Delete");
				tabs.addTab("Chaves estrangeiras", metadataGrid("Chaves estrangeiras", fkModel));
			}
		}

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

	private ResultTableModel metadataModel(String title, String... columns) {
		Vector<String> names = new Vector<>(Arrays.asList(columns));
		Class<?>[] types = new Class<?>[columns.length];
		Arrays.fill(types, String.class);
		String[] nulls = new String[columns.length];
		return new ResultTableModel(names, types, nulls, nulls, nulls);
	}

	private JComponent metadataGrid(String title, ResultTableModel model) {
		String schemaName = (owner.currentSchema() != null) ? owner.currentSchema().name() : null;
		ResultGrid grid = new ResultGrid(model, owner.connectionManager(), schemaName, owner.tableMetadataCache(),
				() -> exportMetadataTable(title, model), owner::scaledPx, owner.resultRowHeightBasePx());
		return grid;
	}

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

	private void exportMetadataTable(String title, ResultTableModel model) {
		File file = owner.chooseSaveFile(title);
		if (file != null) {
			owner.doExport(List.of(new com.nureal.ide.core.export.ExcelExporter.TableSheet(title, model)), file);
		}
	}

	private void loadTableDetailsInto(ObjNode obj, ResultTableModel colModel, ResultTableModel idxModel,
			ResultTableModel fkModel) {
		new SwingWorker<TableDetails, Void>() {
			@Override
			protected TableDetails doInBackground() throws Exception {
				Connection conn = owner.connectionManager().getConnection();
				return owner.metadataService().loadTableDetails(conn, owner.currentSchema().name(), obj.name());
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
					owner.statusBar().setText(" Erro ao carregar detalhes: " + c.getMessage());
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
				if (!owner.connectionManager().isConnected()) {
					return "Sem conexao ativa.";
				}
				Connection conn = owner.connectionManager().getConnection();
				String sql = owner.dialect().definitionQuery(obj.kind(), obj.name());
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
		EMPTY_MESSAGE
	}

	/**
	 * No da arvore: tipo, texto exibido, nome cru do objeto, o tipo para o DDL
	 * (kind, null para schema/categoria/coluna), a tabela associada quando
	 * houver e o tipo SQL da coluna (columnType, so preenchido para
	 * NodeType.COLUMN).
	 */
	record ObjNode(NodeType type, String display, String name, String kind, TableInfo table, String columnType) {
		@Override
		public String toString() {
			return display;
		}
	}
}
