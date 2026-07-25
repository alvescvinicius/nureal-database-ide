package com.nureal.ide.ui;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.SwingWorker;

import com.nureal.ide.core.backup.BackupPort;
import com.nureal.ide.core.backup.MySqlDumpRunner;
import com.nureal.ide.core.csv.CsvUtil;
import com.nureal.ide.core.metadata.model.ColumnInfo;

/**
 * Backup/restauracao (mysqldump) e importacao de CSV — extraido do
 * {@code Session} interno de {@link ObjectExplorerController} (SPEC-0008
 * Etapa 3: arquivo tinha passado dos 1200 linhas que a propria spec exige
 * dividir). As duas operacoes tem em comum mover dados em MASSA para
 * dentro/fora do banco — cada uma tem exatamente UM ponto de chamada em
 * {@link ObjectExplorerController} (menu de contexto), sem nenhum outro
 * acoplamento alem da referencia de volta pro {@code owner} (mesmo padrao
 * ja usado por {@code ObjectExplorerController} pra {@link MainWindow}).
 */
final class ObjectDataTransfer {

    private final MainWindow owner;
    private final BackupPort backupPort = new MySqlDumpRunner();

    ObjectDataTransfer(MainWindow owner) {
        this.owner = owner;
    }

    void openBackupRestore() {
        if (owner.activeWorkspace() == null || !owner.activeWorkspace().mgr.isConnected() || owner.currentSchema() == null
                || owner.activeWorkspace().profile == null) {
            owner.statusBar().setText(" Abra um esquema antes de fazer backup/restauracao.");
            return;
        }
        String schemaName = owner.currentSchema().name();
        var profile = owner.activeWorkspace().profile;
        MySqlDumpRunner.ConnectionTarget target = new MySqlDumpRunner.ConnectionTarget(
                profile.host(), profile.port(), profile.user(), profile.password());
        List<String> tableNames = new ArrayList<>();
        for (var t : owner.currentSchema().tables()) {
            tableNames.add(t.name());
        }
        BackupRestoreDialog.open(owner, schemaName, target, tableNames,
                (options, outputFile, onLogLine, onDone, onError) ->
                        runBackup(options, outputFile, onLogLine, onDone, onError),
                (options, inputFile, onLogLine, onDone, onError) ->
                        runRestore(options, inputFile, onLogLine, onDone, onError));
    }

    private void runBackup(MySqlDumpRunner.BackupOptions options, java.nio.file.Path outputFile,
            java.util.function.Consumer<String> onLogLine, java.util.function.Consumer<MySqlDumpRunner.RunResult> onDone,
            java.util.function.Consumer<Exception> onError) {
        new SwingWorker<MySqlDumpRunner.RunResult, String>() {
            @Override
            protected MySqlDumpRunner.RunResult doInBackground() throws Exception {
                return backupPort.backup(options, outputFile, this::publish);
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

    private void runRestore(MySqlDumpRunner.RestoreOptions options, java.nio.file.Path inputFile,
            java.util.function.Consumer<String> onLogLine, java.util.function.Consumer<MySqlDumpRunner.RunResult> onDone,
            java.util.function.Consumer<Exception> onError) {
        new SwingWorker<MySqlDumpRunner.RunResult, String>() {
            @Override
            protected MySqlDumpRunner.RunResult doInBackground() throws Exception {
                return backupPort.restore(options, inputFile, this::publish);
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

    void importCsv(ObjectExplorerController.ObjNode obj) {
        if (owner.activeWorkspace() == null || !owner.activeWorkspace().mgr.isConnected() || owner.currentSchema() == null) {
            owner.statusBar().setText(" Abra um esquema antes de importar CSV.");
            return;
        }
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Importar CSV para \"" + obj.name() + "\"");
        fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Arquivo CSV (*.csv)", "csv"));
        if (fc.showOpenDialog(owner) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File file = fc.getSelectedFile();
        Conexao ws = owner.activeWorkspace();
        String schemaName = owner.currentSchema().name();
        List<ColumnInfo> tableColumns = obj.table() != null ? obj.table().columns() : List.of();
        owner.statusBar().setText(" Lendo " + file.getName() + "...");
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
                    owner.statusBar().setText(" Pronto.");
                    if (rows.isEmpty()) {
                        JOptionPane.showMessageDialog(owner,
                                "O arquivo nao tem linhas de dados (so cabecalho, ou esta vazio).",
                                "Importar CSV", JOptionPane.WARNING_MESSAGE);
                        return;
                    }
                    CsvImportDialog.open(owner, schemaName, obj.name(), tableColumns, headers, rows,
                            (schema, tableName, targetColumns, csvRows, onProgress, onOk, onErr) -> runCsvImport(ws,
                                    schema, tableName, targetColumns, csvRows, onProgress, onOk, onErr));
                } catch (Exception ex) {
                    owner.statusBar().setText(" Falha ao ler o arquivo CSV.");
                    JOptionPane.showMessageDialog(owner, "Falha ao ler o arquivo:\n" + ex.getMessage(),
                            "Importar CSV", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

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
                        .append(owner.dialect().quoteIdentifier(schema)).append('.').append(owner.dialect().quoteIdentifier(table))
                        .append(" (");
                for (int i = 0; i < columns.size(); i++) {
                    sql.append(i > 0 ? ", " : "").append(owner.dialect().quoteIdentifier(columns.get(i)));
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
}
