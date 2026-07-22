package com.nureal.ide.ui;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import javax.swing.JOptionPane;
import javax.swing.SwingWorker;

import com.nureal.ide.core.metadata.model.TableDetails;
import com.nureal.ide.core.metadata.model.TableInfo;

/**
 * Criar/alterar/dropar tabela/view/trigger/procedure/function — extraido do
 * {@link ObjectExplorerController} (SPEC-0008 Etapa 3: arquivo tinha passado
 * dos 1200 linhas que a propria spec exige dividir). Cada metodo publico
 * daqui tem exatamente UM ponto de chamada, sempre a partir de um item de
 * menu de contexto montado em {@code ObjectExplorerController} — os dois
 * unicos pontos de acoplamento de volta pro controller sao
 * {@link ObjectExplorerController#refreshObjectTree} (recarrega a arvore
 * depois de um DDL bem-sucedido) e {@link ObjectExplorerController#pickDefinitionColumn}
 * (tambem usado pela tela de propriedades do objeto, que continua no
 * controller) — nao ha estado proprio, so as duas referencias de volta
 * ({@code owner}/{@code controller}).
 */
final class ObjectDdlActions {

    private final MainWindow owner;
    private final ObjectExplorerController controller;

    ObjectDdlActions(MainWindow owner, ObjectExplorerController controller) {
        this.owner = owner;
        this.controller = controller;
    }

    void createTable() {
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
                        controller.refreshObjectTree(false);
                    }
                });
    }

    void alterTable(ObjectExplorerController.ObjNode obj) {
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
                            ObjectDdlActions.this::sendDdlToEditor,
                            () -> {
                                if (ws == owner.activeWorkspace() && schemaName.equals(owner.currentSchema().name())) {
                                    controller.refreshObjectTree(false);
                                }
                            });
                } catch (Exception ex) {
                    owner.showError("Falha ao carregar estrutura da tabela", ex);
                    owner.statusBar().setText(" Falha ao carregar estrutura da tabela.");
                }
            }
        }.execute();
    }

    void createView() {
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
                        controller.refreshObjectTree(false);
                    }
                });
    }

    void editView(ObjectExplorerController.ObjNode obj) {
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
                        int idx = ObjectExplorerController.pickDefinitionColumn(rs.getMetaData());
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
                            ObjectDdlActions.this::sendDdlToEditor,
                            () -> {
                                if (ws == owner.activeWorkspace() && schemaName.equals(owner.currentSchema().name())) {
                                    controller.refreshObjectTree(false);
                                }
                            });
                } catch (Exception ex) {
                    owner.showError("Falha ao carregar a definicao da view", ex);
                    owner.statusBar().setText(" Falha ao carregar a definicao da view.");
                }
            }
        }.execute();
    }

    void dropView(ObjectExplorerController.ObjNode obj) {
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
                controller.refreshObjectTree(false);
            }
        }, ex -> {
            owner.statusBar().setText(" Falha ao excluir a view.");
            owner.showError("Falha ao excluir a view", ex);
        });
    }

    void createTrigger() {
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
                        controller.refreshObjectTree(false);
                    }
                });
    }

    void editTrigger(ObjectExplorerController.ObjNode obj) {
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
                        int idx = ObjectExplorerController.pickDefinitionColumn(rs.getMetaData());
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
                            ObjectDdlActions.this::sendDdlToEditor,
                            () -> {
                                if (ws == owner.activeWorkspace() && schemaName.equals(owner.currentSchema().name())) {
                                    controller.refreshObjectTree(false);
                                }
                            });
                } catch (Exception ex) {
                    owner.showError("Falha ao carregar a definicao do trigger", ex);
                    owner.statusBar().setText(" Falha ao carregar a definicao do trigger.");
                }
            }
        }.execute();
    }

    void dropTrigger(ObjectExplorerController.ObjNode obj) {
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
                controller.refreshObjectTree(false);
            }
        }, ex -> {
            owner.statusBar().setText(" Falha ao excluir o trigger.");
            owner.showError("Falha ao excluir o trigger", ex);
        });
    }

    void createRoutine(String initialKind) {
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
                        controller.refreshObjectTree(false);
                    }
                });
    }

    void dropRoutine(ObjectExplorerController.ObjNode obj) {
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
                controller.refreshObjectTree(false);
            }
        }, ex -> {
            owner.statusBar().setText(" Falha ao excluir a " + label + ".");
            owner.showError("Falha ao excluir a " + label, ex);
        });
    }

    void runDdlStatements(Conexao ws, List<String> statements, Runnable onOk,
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
}
