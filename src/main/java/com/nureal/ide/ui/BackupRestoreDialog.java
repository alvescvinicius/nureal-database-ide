package com.nureal.ide.ui;
import com.nureal.ide.compartilhado.designsystem.Buttons;
import com.nureal.ide.compartilhado.designsystem.GridTheme;

import com.nureal.ide.modulos.backupexportacao.infraestrutura.MySqlDumpRunner.BackupOptions;
import com.nureal.ide.modulos.backupexportacao.infraestrutura.MySqlDumpRunner.ConnectionTarget;
import com.nureal.ide.modulos.backupexportacao.infraestrutura.MySqlDumpRunner.RestoreOptions;
import com.nureal.ide.modulos.backupexportacao.infraestrutura.MySqlDumpRunner.RunResult;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.io.File;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Backup ({@code mysqldump}) e restauracao ({@code mysql}) do esquema aberto
 * — fase 4 do GAP_ANALYSIS_DBA_DEV.md, a lacuna mais critica identificada
 * ("hoje exige sair da IDE e abrir um terminal"). Ver {@code MySqlDumpRunner}
 * para o porque de depender de PROCESSO EXTERNO (JDBC nao inclui esses
 * binarios) e da estrategia de credenciais (arquivo temporario, nunca
 * argumento de linha de comando).
 *
 * MODAL (mesmo padrao de {@link CsvImportDialog}, nao NAO-MODAL como
 * {@link ProcessListDialog}/{@link ServerStatusDialog}): e uma acao unica de
 * inicio-fim com barra de progresso, nao um monitor continuo.
 */
final class BackupRestoreDialog {

    private BackupRestoreDialog() {
    }

    @FunctionalInterface
    interface BackupRunner {
        void run(BackupOptions options, Path outputFile, Consumer<String> onLogLine,
                Consumer<RunResult> onDone, Consumer<Exception> onError);
    }

    @FunctionalInterface
    interface RestoreRunner {
        void run(RestoreOptions options, Path inputFile, Consumer<String> onLogLine,
                Consumer<RunResult> onDone, Consumer<Exception> onError);
    }

    static void open(Component parent, String schemaName, ConnectionTarget target, List<String> tableNames,
            BackupRunner backupRunner, RestoreRunner restoreRunner) {
        new Session(parent, schemaName, target, tableNames, backupRunner, restoreRunner).show();
    }

    private static final class Session {
        private final Window owner;
        private final String schemaName;
        private final ConnectionTarget target;
        private final List<String> tableNames;
        private final BackupRunner backupRunner;
        private final RestoreRunner restoreRunner;

        private JDialog dialog;

        // --- Backup ---
        private JTextField dumpPathField;
        private JRadioButton wholeSchemaRadio;
        private JRadioButton specificTablesRadio;
        private JList<String> tablesList;
        private JCheckBox singleTransactionCheck;
        private JCheckBox structureOnlyCheck;
        private JCheckBox routinesCheck;
        private JCheckBox triggersCheck;
        private JTextField backupFileField;
        private JButton startBackupButton;
        private JProgressBar backupProgress;
        private JTextArea backupLog;

        // --- Restore ---
        private JTextField mysqlPathField;
        private JTextField restoreFileField;
        private JCheckBox confirmRiskCheck;
        private JButton startRestoreButton;
        private JProgressBar restoreProgress;
        private JTextArea restoreLog;

        Session(Component parent, String schemaName, ConnectionTarget target, List<String> tableNames,
                BackupRunner backupRunner, RestoreRunner restoreRunner) {
            this.owner = (DialogUtil.owner(parent) instanceof Window w) ? w : null;
            this.schemaName = schemaName;
            this.target = target;
            this.tableNames = tableNames;
            this.backupRunner = backupRunner;
            this.restoreRunner = restoreRunner;
        }

        void show() {
            dialog = new JDialog(owner, "Backup e restauracao — " + schemaName, Dialog.ModalityType.APPLICATION_MODAL);
            dialog.setLayout(new BorderLayout());
            JTabbedPane tabs = new JTabbedPane();
            tabs.addTab("Backup (mysqldump)", buildBackupTab());
            tabs.addTab("Restaurar (mysql)", buildRestoreTab());
            dialog.add(tabs, BorderLayout.CENTER);
            // Esc fecha — mesmo atalho ja usado nos dialogs "de formulario
            // guiado" (DDL/view/trigger/rotina/usuarios), faltava aqui
            // (achado numa auditoria pedida pelo usuario).
            dialog.getRootPane().registerKeyboardAction(e -> dialog.dispose(),
                    KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
            dialog.setSize(720, 620);
            dialog.setLocationRelativeTo(owner);
            dialog.setVisible(true);
        }

        // ==================== Backup ====================

        private JComponent buildBackupTab() {
            dumpPathField = new JTextField("mysqldump");
            JButton browseDump = new JButton("Procurar...");
            Buttons.styleSecondary(browseDump);
            browseDump.addActionListener(a -> browseExecutable(dumpPathField));

            wholeSchemaRadio = new JRadioButton("Todo o esquema \"" + schemaName + "\"", true);
            specificTablesRadio = new JRadioButton("Tabelas especificas");
            ButtonGroup scopeGroup = new ButtonGroup();
            scopeGroup.add(wholeSchemaRadio);
            scopeGroup.add(specificTablesRadio);

            tablesList = new JList<>(tableNames.toArray(new String[0]));
            tablesList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
            tablesList.setEnabled(false);
            wholeSchemaRadio.addActionListener(a -> tablesList.setEnabled(false));
            specificTablesRadio.addActionListener(a -> tablesList.setEnabled(true));

            singleTransactionCheck = new JCheckBox("Transacao unica (recomendado — evita travar as tabelas durante o backup)", true);
            structureOnlyCheck = new JCheckBox("Somente estrutura (sem dados)");
            routinesCheck = new JCheckBox("Incluir procedures/functions");
            triggersCheck = new JCheckBox("Incluir triggers");

            String suggested = "backup-" + schemaName + "-"
                    + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".sql";
            backupFileField = new JTextField(suggested);
            backupFileField.setEditable(false);
            JButton chooseFile = new JButton("Escolher...");
            Buttons.styleSecondary(chooseFile);
            chooseFile.addActionListener(a -> chooseBackupFile(suggested));

            JPanel form = new JPanel();
            form.setLayout(new javax.swing.BoxLayout(form, javax.swing.BoxLayout.Y_AXIS));
            form.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
            form.add(labeledRow("Executavel mysqldump:", dumpPathField, browseDump));
            form.add(verticalGap());
            form.add(wholeSchemaRadio);
            form.add(specificTablesRadio);
            JScrollPane tablesScroll = new JScrollPane(tablesList);
            tablesScroll.setPreferredSize(new Dimension(300, 120));
            tablesScroll.setMaximumSize(new Dimension(4000, 120));
            form.add(tablesScroll);
            form.add(verticalGap());
            form.add(singleTransactionCheck);
            form.add(structureOnlyCheck);
            form.add(routinesCheck);
            form.add(triggersCheck);
            form.add(verticalGap());
            form.add(labeledRow("Arquivo de destino:", backupFileField, chooseFile));

            startBackupButton = new JButton("Iniciar backup");
            Buttons.stylePrimary(startBackupButton);
            startBackupButton.addActionListener(a -> startBackup());

            backupProgress = new JProgressBar();
            backupProgress.setIndeterminate(false);
            backupProgress.setVisible(false);

            backupLog = new JTextArea(8, 60);
            backupLog.setEditable(false);
            backupLog.setFont(SqlEditorPane.monospaceFont(12));

            JPanel footer = new JPanel(new BorderLayout(0, 6));
            footer.setBorder(BorderFactory.createEmptyBorder(0, 10, 10, 10));
            JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            buttonRow.add(startBackupButton);
            footer.add(buttonRow, BorderLayout.NORTH);
            footer.add(backupProgress, BorderLayout.CENTER);
            JScrollPane logScroll = new JScrollPane(backupLog);
            footer.add(logScroll, BorderLayout.SOUTH);

            JPanel panel = new JPanel(new BorderLayout());
            panel.add(new JScrollPane(form), BorderLayout.CENTER);
            panel.add(footer, BorderLayout.SOUTH);
            return panel;
        }

        private void chooseBackupFile(String suggested) {
            JFileChooser fc = new JFileChooser();
            fc.setDialogTitle("Salvar backup como");
            fc.setSelectedFile(new File(suggested));
            fc.setFileFilter(new FileNameExtensionFilter("Script SQL (*.sql)", "sql"));
            if (fc.showSaveDialog(dialog) != JFileChooser.APPROVE_OPTION) {
                return;
            }
            File file = fc.getSelectedFile();
            if (!file.getName().toLowerCase(Locale.ROOT).endsWith(".sql")) {
                file = new File(file.getParentFile(), file.getName() + ".sql");
            }
            backupFileField.setText(file.getAbsolutePath());
        }

        private void startBackup() {
            String filePath = backupFileField.getText();
            if (filePath == null || filePath.isBlank()) {
                JOptionPane.showMessageDialog(dialog, "Escolha o arquivo de destino do backup.",
                        "Backup", JOptionPane.WARNING_MESSAGE);
                return;
            }
            List<String> tables = specificTablesRadio.isSelected() ? tablesList.getSelectedValuesList() : List.of();
            if (specificTablesRadio.isSelected() && tables.isEmpty()) {
                JOptionPane.showMessageDialog(dialog, "Selecione ao menos uma tabela, ou escolha \"Todo o esquema\".",
                        "Backup", JOptionPane.WARNING_MESSAGE);
                return;
            }
            BackupOptions options = new BackupOptions(dumpPathField.getText(), target, schemaName, tables,
                    structureOnlyCheck.isSelected(), routinesCheck.isSelected(), triggersCheck.isSelected(),
                    singleTransactionCheck.isSelected());
            startBackupButton.setEnabled(false);
            backupProgress.setVisible(true);
            backupProgress.setIndeterminate(true);
            backupLog.setText("");
            appendLog(backupLog, "Iniciando backup para \"" + filePath + "\"...");
            backupRunner.run(options, new File(filePath).toPath(),
                    line -> appendLog(backupLog, line),
                    result -> {
                        startBackupButton.setEnabled(true);
                        backupProgress.setIndeterminate(false);
                        if (result.success()) {
                            backupProgress.setValue(100);
                            appendLog(backupLog, "Backup concluido com sucesso.");
                            JOptionPane.showMessageDialog(dialog, "Backup salvo em \"" + filePath + "\".",
                                    "Backup", JOptionPane.INFORMATION_MESSAGE);
                        } else if (result.exitCode() == 0 && result.hadErrorOutput()) {
                            backupProgress.setValue(100);
                            appendLog(backupLog, "Backup concluido, mas com erros durante a execucao — veja o log acima.");
                            JOptionPane.showMessageDialog(dialog,
                                    "O backup terminou, mas houve erros durante a execucao (ex.: falta de "
                                            + "privilegio para extrair alguma routine/trigger) — o arquivo em \""
                                            + filePath + "\" pode estar incompleto. Veja o log para detalhes.",
                                    "Backup", JOptionPane.WARNING_MESSAGE);
                        } else {
                            appendLog(backupLog, "mysqldump encerrou com codigo " + result.exitCode() + ".");
                            JOptionPane.showMessageDialog(dialog,
                                    "O backup terminou com erro (codigo " + result.exitCode()
                                            + ") — veja o log para detalhes.",
                                    "Backup", JOptionPane.ERROR_MESSAGE);
                        }
                    },
                    ex -> {
                        startBackupButton.setEnabled(true);
                        backupProgress.setIndeterminate(false);
                        appendLog(backupLog, "Falha: " + ex.getMessage());
                        JOptionPane.showMessageDialog(dialog,
                                "Nao foi possivel rodar o mysqldump:\n" + ex.getMessage()
                                        + "\n\nVerifique se o MySQL Client Tools esta instalado e se o caminho do executavel esta correto.",
                                "Backup", JOptionPane.ERROR_MESSAGE);
                    });
        }

        // ==================== Restore ====================

        private JComponent buildRestoreTab() {
            mysqlPathField = new JTextField("mysql");
            JButton browseMysql = new JButton("Procurar...");
            Buttons.styleSecondary(browseMysql);
            browseMysql.addActionListener(a -> browseExecutable(mysqlPathField));

            restoreFileField = new JTextField();
            restoreFileField.setEditable(false);
            JButton chooseFile = new JButton("Escolher...");
            Buttons.styleSecondary(chooseFile);
            chooseFile.addActionListener(a -> chooseRestoreFile());

            JLabel warning = new JLabel("<html>Isto executa o conteudo do arquivo diretamente no esquema \""
                    + schemaName + "\" — pode sobrescrever ou apagar dados existentes. Nao ha confirmacao adicional do MySQL.</html>");
            warning.setForeground(GridTheme.COLOR_LOGIC_FALSE);

            confirmRiskCheck = new JCheckBox("Entendo os riscos e quero prosseguir.");
            confirmRiskCheck.addActionListener(a -> updateRestoreButtonState());

            JPanel form = new JPanel();
            form.setLayout(new javax.swing.BoxLayout(form, javax.swing.BoxLayout.Y_AXIS));
            form.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
            form.add(labeledRow("Executavel mysql:", mysqlPathField, browseMysql));
            form.add(verticalGap());
            form.add(labeledRow("Arquivo .sql de origem:", restoreFileField, chooseFile));
            form.add(verticalGap());
            warning.setAlignmentX(Component.LEFT_ALIGNMENT);
            form.add(warning);
            form.add(confirmRiskCheck);

            startRestoreButton = new JButton("Iniciar restauracao");
            Buttons.stylePrimary(startRestoreButton);
            startRestoreButton.setEnabled(false);
            startRestoreButton.addActionListener(a -> startRestore());

            restoreProgress = new JProgressBar();
            restoreProgress.setVisible(false);

            restoreLog = new JTextArea(8, 60);
            restoreLog.setEditable(false);
            restoreLog.setFont(SqlEditorPane.monospaceFont(12));

            JPanel footer = new JPanel(new BorderLayout(0, 6));
            footer.setBorder(BorderFactory.createEmptyBorder(0, 10, 10, 10));
            JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            buttonRow.add(startRestoreButton);
            footer.add(buttonRow, BorderLayout.NORTH);
            footer.add(restoreProgress, BorderLayout.CENTER);
            footer.add(new JScrollPane(restoreLog), BorderLayout.SOUTH);

            JPanel panel = new JPanel(new BorderLayout());
            panel.add(form, BorderLayout.NORTH);
            panel.add(footer, BorderLayout.SOUTH);
            return panel;
        }

        private void chooseRestoreFile() {
            JFileChooser fc = new JFileChooser();
            fc.setDialogTitle("Escolher arquivo .sql para restaurar");
            fc.setFileFilter(new FileNameExtensionFilter("Script SQL (*.sql)", "sql"));
            if (fc.showOpenDialog(dialog) != JFileChooser.APPROVE_OPTION) {
                return;
            }
            restoreFileField.setText(fc.getSelectedFile().getAbsolutePath());
            updateRestoreButtonState();
        }

        private void updateRestoreButtonState() {
            boolean hasFile = restoreFileField.getText() != null && !restoreFileField.getText().isBlank();
            startRestoreButton.setEnabled(hasFile && confirmRiskCheck.isSelected());
        }

        private void startRestore() {
            String filePath = restoreFileField.getText();
            int confirm = JOptionPane.showConfirmDialog(dialog,
                    "Restaurar \"" + filePath + "\" no esquema \"" + schemaName + "\" agora?",
                    "Restaurar", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (confirm != JOptionPane.YES_OPTION) {
                return;
            }
            RestoreOptions options = new RestoreOptions(mysqlPathField.getText(), target, schemaName);
            startRestoreButton.setEnabled(false);
            restoreProgress.setVisible(true);
            restoreProgress.setIndeterminate(true);
            restoreLog.setText("");
            appendLog(restoreLog, "Restaurando \"" + filePath + "\"...");
            restoreRunner.run(options, new File(filePath).toPath(),
                    line -> appendLog(restoreLog, line),
                    result -> {
                        restoreProgress.setIndeterminate(false);
                        updateRestoreButtonState();
                        if (result.success()) {
                            restoreProgress.setValue(100);
                            appendLog(restoreLog, "Restauracao concluida com sucesso.");
                            JOptionPane.showMessageDialog(dialog, "Restauracao concluida.",
                                    "Restaurar", JOptionPane.INFORMATION_MESSAGE);
                        } else if (result.exitCode() == 0 && result.hadErrorOutput()) {
                            restoreProgress.setValue(100);
                            appendLog(restoreLog, "Restauracao concluida, mas com erros durante a execucao — veja o log acima.");
                            JOptionPane.showMessageDialog(dialog,
                                    "A restauracao terminou, mas houve erros durante a execucao — o esquema \""
                                            + schemaName + "\" pode estar incompleto. Veja o log para detalhes.",
                                    "Restaurar", JOptionPane.WARNING_MESSAGE);
                        } else {
                            appendLog(restoreLog, "mysql encerrou com codigo " + result.exitCode() + ".");
                            JOptionPane.showMessageDialog(dialog,
                                    "A restauracao terminou com erro (codigo " + result.exitCode()
                                            + ") — veja o log para detalhes.",
                                    "Restaurar", JOptionPane.ERROR_MESSAGE);
                        }
                    },
                    ex -> {
                        restoreProgress.setIndeterminate(false);
                        updateRestoreButtonState();
                        appendLog(restoreLog, "Falha: " + ex.getMessage());
                        JOptionPane.showMessageDialog(dialog,
                                "Nao foi possivel rodar o mysql:\n" + ex.getMessage()
                                        + "\n\nVerifique se o MySQL Client Tools esta instalado e se o caminho do executavel esta correto.",
                                "Restaurar", JOptionPane.ERROR_MESSAGE);
                    });
        }

        // ==================== Utilidades compartilhadas ====================

        private void browseExecutable(JTextField field) {
            JFileChooser fc = new JFileChooser();
            fc.setDialogTitle("Selecionar executavel");
            fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
            if (fc.showOpenDialog(dialog) == JFileChooser.APPROVE_OPTION) {
                field.setText(fc.getSelectedFile().getAbsolutePath());
            }
        }

        private static void appendLog(JTextArea area, String line) {
            area.append(line + "\n");
            area.setCaretPosition(area.getDocument().getLength());
        }

        private static JComponent labeledRow(String label, JTextField field, JButton button) {
            JPanel row = new JPanel(new BorderLayout(6, 0));
            row.setAlignmentX(Component.LEFT_ALIGNMENT);
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
            JLabel l = new JLabel(label);
            l.setPreferredSize(new Dimension(160, l.getPreferredSize().height));
            row.add(l, BorderLayout.WEST);
            row.add(field, BorderLayout.CENTER);
            row.add(button, BorderLayout.EAST);
            return row;
        }

        private static JComponent verticalGap() {
            JPanel gap = new JPanel();
            gap.setPreferredSize(new Dimension(1, 10));
            gap.setMaximumSize(new Dimension(Integer.MAX_VALUE, 10));
            gap.setOpaque(false);
            return gap;
        }
    }
}
