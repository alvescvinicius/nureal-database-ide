package com.nureal.ide.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BiPredicate;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.DefaultCellEditor;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.table.DefaultTableModel;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;

import com.nureal.ide.core.dialect.DatabaseDialect;
import com.nureal.ide.core.format.SqlFormatter;

/**
 * Assistente para CRIAR uma PROCEDURE ou FUNCTION — pedido explicito do
 * usuario: "preciso de tudo que necessario para construcao de esquemas,
 * tabelas, views, triggers e etc". Mesma familia de dialogo guiado do
 * {@link DdlAssistantDialog}/{@link ViewBuilderDialog}/
 * {@link TriggerBuilderDialog}: cabecalho (tipo, nome, parametros, e para
 * FUNCTION o tipo de retorno + DETERMINISTIC), corpo editavel (entre
 * {@code BEGIN...END}) e pre-visualizacao do DDL final antes de executar.
 * <p>
 * So CRIA (nao ha modo "editar"): o {@code MainWindow} ja tem uma forma de
 * ver o {@code SHOW CREATE PROCEDURE}/{@code FUNCTION} de uma rotina
 * existente (aba "DDL" do dialogo de propriedades do objeto), e MySQL nao tem
 * {@code ALTER PROCEDURE}/{@code ALTER FUNCTION} para o CORPO (so para
 * caracteristicas como comentario/SQL SECURITY) — "editar" de verdade seria
 * DROP + CREATE, exigindo re-parsear parametros e tipo de retorno do SQL
 * bruto (lista de parametros com virgulas dentro de parenteses de tipo, ex.
 * "DECIMAL(10,2)", torna esse parsing bem mais arriscado que o de VIEW/
 * TRIGGER) — fora do escopo desta rodada; o usuario pode copiar o DDL atual
 * (aba "DDL" das propriedades) para o editor e ajustar a mao.
 */
final class RoutineBuilderDialog {

    private RoutineBuilderDialog() {
    }

    private static final String[] KINDS = { "PROCEDURE", "FUNCTION" };
    private static final String[] PARAM_MODES = { "IN", "OUT", "INOUT" };
    private static final String[] TYPES = {
            "VARCHAR", "CHAR", "TEXT", "INT", "BIGINT", "SMALLINT", "TINYINT",
            "DECIMAL", "FLOAT", "DOUBLE", "DATE", "DATETIME", "TIMESTAMP", "TIME", "BOOLEAN", "JSON"
    };

    /**
     * Abre o assistente no modo "criar" — {@code initialKind} preseleciona
     * PROCEDURE ou FUNCTION (ex.: "Nova function..." vindo do no "Functions"
     * da arvore ja abre com FUNCTION escolhido, sem o usuario precisar trocar
     * no combo). {@code nameTaken} recebe (kind, nome) porque procedure e
     * function tem namespaces SEPARADOS no MySQL (pode existir uma procedure
     * e uma function com o MESMO nome ao mesmo tempo).
     */
    static void openCreate(Component parent, DatabaseDialect dialect, String initialKind,
            BiPredicate<String, String> nameTaken, DdlAssistantDialog.DdlRunner runner,
            Consumer<String> sendToEditor, Runnable onSuccess) {
        new Session(parent, dialect, initialKind, nameTaken, runner, sendToEditor, onSuccess).show();
    }

    private static final class Session {
        private final Window owner;
        private final DatabaseDialect dialect;
        private final BiPredicate<String, String> nameTaken;
        private final DdlAssistantDialog.DdlRunner runner;
        private final Consumer<String> sendToEditor;
        private final Runnable onSuccess;
        private final String initialKind;

        private JDialog dialog;
        private JComboBox<String> kindCombo;
        private JTextField nameField;
        private DefaultTableModel paramsModel;
        private JTable paramsTable;
        private JLabel returnTypeLabel;
        private JComboBox<String> returnTypeCombo;
        private JLabel deterministicLabel;
        private JCheckBox deterministicCheck;
        private RSyntaxTextArea bodyArea;
        private RSyntaxTextArea previewArea;
        private JTabbedPane tabs;
        private int previewTabIndex;

        Session(Component parent, DatabaseDialect dialect, String initialKind, BiPredicate<String, String> nameTaken,
                DdlAssistantDialog.DdlRunner runner, Consumer<String> sendToEditor, Runnable onSuccess) {
            this.owner = (DialogUtil.owner(parent) instanceof Window w) ? w : null;
            this.dialect = dialect;
            this.nameTaken = nameTaken;
            this.runner = runner;
            this.sendToEditor = sendToEditor;
            this.onSuccess = onSuccess;
            this.initialKind = (initialKind == null) ? "PROCEDURE" : initialKind;
        }

        void show() {
            dialog = new JDialog(owner, "Nova procedure/function", Dialog.ModalityType.APPLICATION_MODAL);
            dialog.setLayout(new BorderLayout());
            dialog.add(buildHeader(), BorderLayout.NORTH);
            dialog.add(buildTabs(), BorderLayout.CENTER);
            dialog.add(buildFooter(), BorderLayout.SOUTH);
            dialog.getRootPane().registerKeyboardAction(e -> dialog.dispose(),
                    KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
            dialog.setSize(820, 620);
            dialog.setLocationRelativeTo(owner);
            dialog.setVisible(true);
            updateKindVisibility();
        }

        private JComponent buildHeader() {
            JPanel form = new JPanel(new GridBagLayout());
            form.setBorder(BorderFactory.createEmptyBorder(10, 10, 6, 10));
            GridBagConstraints c = new GridBagConstraints();
            c.insets = new Insets(3, 4, 3, 4);
            c.anchor = GridBagConstraints.WEST;

            c.gridx = 0;
            c.gridy = 0;
            form.add(new JLabel("Tipo:"), c);
            c.gridx = 1;
            c.weightx = 1;
            c.fill = GridBagConstraints.HORIZONTAL;
            kindCombo = new JComboBox<>(KINDS);
            kindCombo.setSelectedItem(initialKind);
            kindCombo.addItemListener(e -> updateKindVisibility());
            form.add(kindCombo, c);

            c.gridx = 0;
            c.gridy = 1;
            c.weightx = 0;
            c.fill = GridBagConstraints.NONE;
            form.add(new JLabel("Nome:"), c);
            c.gridx = 1;
            c.weightx = 1;
            c.fill = GridBagConstraints.HORIZONTAL;
            nameField = new JTextField(24);
            form.add(nameField, c);

            c.gridx = 0;
            c.gridy = 2;
            c.weightx = 0;
            c.fill = GridBagConstraints.NONE;
            returnTypeLabel = new JLabel("Tipo de retorno (RETURNS):");
            form.add(returnTypeLabel, c);
            c.gridx = 1;
            c.weightx = 1;
            c.fill = GridBagConstraints.HORIZONTAL;
            returnTypeCombo = new JComboBox<>(TYPES);
            form.add(returnTypeCombo, c);

            c.gridx = 0;
            c.gridy = 3;
            c.weightx = 0;
            c.fill = GridBagConstraints.NONE;
            deterministicLabel = new JLabel("Deterministica:");
            form.add(deterministicLabel, c);
            c.gridx = 1;
            c.weightx = 1;
            c.fill = GridBagConstraints.HORIZONTAL;
            deterministicCheck = new JCheckBox(
                    "DETERMINISTIC (marque se a function sempre devolve o mesmo resultado para os mesmos argumentos)",
                    true);
            form.add(deterministicCheck, c);

            JLabel banner = new JLabel(
                    "So CRIA (sem modo editar aqui) — para ver o DDL de uma rotina existente use \"Propriedades...\" (aba DDL).");
            Typography.tertiary(banner);
            banner.setBorder(BorderFactory.createEmptyBorder(0, 10, 6, 10));

            JPanel wrap = new JPanel(new BorderLayout());
            wrap.add(banner, BorderLayout.NORTH);
            wrap.add(form, BorderLayout.CENTER);
            return wrap;
        }

        private void updateKindVisibility() {
            boolean isFunction = "FUNCTION".equals(kindCombo.getSelectedItem());
            returnTypeLabel.setVisible(isFunction);
            returnTypeCombo.setVisible(isFunction);
            deterministicLabel.setVisible(isFunction);
            deterministicCheck.setVisible(isFunction);
            // "Modo" (IN/OUT/INOUT) so faz sentido em PROCEDURE — FUNCTION do
            // MySQL so aceita parametros IN implicitos; a coluna some do
            // grid em vez de ficar la, editavel, sem efeito nenhum no DDL.
            paramsTable.getColumnModel().getColumn(0).setMinWidth(isFunction ? 0 : 70);
            paramsTable.getColumnModel().getColumn(0).setMaxWidth(isFunction ? 0 : 200);
            paramsTable.getColumnModel().getColumn(0).setPreferredWidth(isFunction ? 0 : 70);
        }

        private JComponent buildTabs() {
            tabs = new JTabbedPane();
            tabs.addTab("Parametros", buildParamsTab());
            tabs.addTab("Corpo", buildBodyTab());
            previewTabIndex = tabs.getTabCount();
            tabs.addTab("DDL (pre-visualizacao)", buildPreviewTab());
            tabs.addChangeListener(e -> {
                if (tabs.getSelectedIndex() == previewTabIndex) {
                    refreshPreview();
                }
            });
            return tabs;
        }

        private JComponent buildParamsTab() {
            JPanel panel = new JPanel(new BorderLayout(0, 8));
            panel.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));

            String[] headers = { "Modo", "Nome", "Tipo", "Tamanho" };
            paramsModel = new DefaultTableModel(headers, 0);
            paramsTable = MetadataTableStyle.createStyledTable(paramsModel);
            paramsTable.getColumnModel().getColumn(0)
                    .setCellEditor(new DefaultCellEditor(new JComboBox<>(PARAM_MODES)));
            paramsTable.getColumnModel().getColumn(2).setCellEditor(new DefaultCellEditor(new JComboBox<>(TYPES)));
            paramsTable.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);

            JButton addRow = new JButton("+ Parametro");
            addRow.addActionListener(a -> paramsModel.addRow(new Object[] { "IN", "", "VARCHAR", "255" }));
            JButton removeRow = new JButton("- Parametro");
            removeRow.addActionListener(a -> {
                if (paramsTable.isEditing()) {
                    paramsTable.getCellEditor().stopCellEditing();
                }
                int[] rows = paramsTable.getSelectedRows();
                for (int i = rows.length - 1; i >= 0; i--) {
                    paramsModel.removeRow(rows[i]);
                }
            });
            Buttons.styleSecondary(addRow);
            Buttons.styleSecondary(removeRow);
            JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
            buttons.add(addRow);
            buttons.add(removeRow);

            panel.add(new JScrollPane(paramsTable), BorderLayout.CENTER);
            panel.add(buttons, BorderLayout.SOUTH);
            return panel;
        }

        private JComponent buildBodyTab() {
            JPanel panel = new JPanel(new BorderLayout(0, 8));
            panel.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
            bodyArea = new RSyntaxTextArea();
            SqlEditorPane.styleEditableSql(bodyArea);
            bodyArea.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
            panel.add(new JLabel("Comandos a executar (entre BEGIN e END):"), BorderLayout.NORTH);
            panel.add(new JScrollPane(bodyArea), BorderLayout.CENTER);
            return panel;
        }

        private JComponent buildPreviewTab() {
            JPanel panel = new JPanel(new BorderLayout(0, 8));
            panel.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
            previewArea = new RSyntaxTextArea();
            SqlEditorPane.styleAsReadOnlySql(previewArea);
            previewArea.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
            JButton refresh = new JButton("Atualizar pre-visualizacao");
            refresh.addActionListener(a -> refreshPreview());
            Buttons.styleSecondary(refresh);
            JPanel south = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
            south.add(refresh);
            panel.add(new JScrollPane(previewArea), BorderLayout.CENTER);
            panel.add(south, BorderLayout.SOUTH);
            return panel;
        }

        private void refreshPreview() {
            try {
                String sql = buildStatement();
                SqlFormatter formatter = new SqlFormatter(SqlFormatter.KeywordCase.UPPER, SqlFormatter.Style.STANDARD,
                        false);
                previewArea.setText(formatter.format(sql) + ";");
            } catch (SqlBuilderValidationException ex) {
                previewArea.setText("-- " + ex.getMessage());
            }
            previewArea.setCaretPosition(0);
        }

        private JComponent buildFooter() {
            JButton execute = new JButton("Executar CREATE");
            execute.addActionListener(a -> onExecute());
            return Buttons.dialogFooter(dialog, execute);
        }

        private void onExecute() {
            String sql;
            try {
                sql = buildStatement();
            } catch (SqlBuilderValidationException ex) {
                JOptionPane.showMessageDialog(dialog, ex.getMessage(), "Nova procedure/function",
                        JOptionPane.WARNING_MESSAGE);
                tabs.setSelectedIndex(0);
                return;
            }
            if (runner == null) {
                return;
            }
            String kind = (String) kindCombo.getSelectedItem();
            String name = nameField.getText().trim();
            runner.run(List.of(sql), () -> {
                JOptionPane.showMessageDialog(dialog,
                        ("PROCEDURE".equals(kind) ? "Procedure" : "Function") + " \"" + name + "\" criada com sucesso.",
                        "Nova procedure/function", JOptionPane.INFORMATION_MESSAGE);
                if (onSuccess != null) {
                    onSuccess.run();
                }
                dialog.dispose();
            }, ex -> JOptionPane.showMessageDialog(dialog, "Falha ao executar:\n" + ex.getMessage(),
                    "Nova procedure/function", JOptionPane.ERROR_MESSAGE));
        }

        private String buildStatement() {
            String kind = (String) kindCombo.getSelectedItem();
            String name = nameField.getText().trim();
            if (name.isEmpty()) {
                throw new SqlBuilderValidationException("Informe o nome da " + ("PROCEDURE".equals(kind) ? "procedure" : "function") + ".");
            }
            if (!SqlIdentifiers.isValid(name)) {
                throw new SqlBuilderValidationException("Nome invalido: \"" + name + "\". Use letras, numeros e _ (nao pode comecar com numero).");
            }
            if (nameTaken != null && nameTaken.test(kind, name)) {
                throw new SqlBuilderValidationException("Ja existe uma " + ("PROCEDURE".equals(kind) ? "procedure" : "function")
                        + " chamada \"" + name + "\".");
            }
            String body = bodyArea.getText().trim();
            if (body.isEmpty()) {
                throw new SqlBuilderValidationException("Escreva pelo menos um comando no corpo.");
            }
            boolean isFunction = "FUNCTION".equals(kind);
            List<String> params = collectParams(isFunction);
            if (isFunction) {
                Object returnType = returnTypeCombo.getSelectedItem();
                if (returnType == null || returnType.toString().isBlank()) {
                    throw new SqlBuilderValidationException("Escolha o tipo de retorno (RETURNS).");
                }
                return dialect.createFunctionStatement(name, params, returnType.toString(),
                        deterministicCheck.isSelected(), body);
            }
            return dialect.createProcedureStatement(name, params, body);
        }

        /** Monta cada linha da grade de parametros num texto pronto para o dialeto (ex.: "IN p_id INT(11)"). */
        private List<String> collectParams(boolean isFunction) {
            List<String> out = new ArrayList<>();
            for (int r = 0; r < paramsModel.getRowCount(); r++) {
                String mode = str(paramsModel.getValueAt(r, 0)).trim();
                String name = str(paramsModel.getValueAt(r, 1)).trim();
                String type = str(paramsModel.getValueAt(r, 2)).trim();
                String length = str(paramsModel.getValueAt(r, 3)).trim();
                if (name.isEmpty() && type.isEmpty()) {
                    continue;
                }
                if (name.isEmpty()) {
                    throw new SqlBuilderValidationException("Ha um parametro sem nome na aba \"Parametros\".");
                }
                if (!SqlIdentifiers.isValid(name)) {
                    throw new SqlBuilderValidationException("Nome de parametro invalido: \"" + name + "\".");
                }
                StringBuilder def = new StringBuilder();
                // FUNCTION do MySQL nao aceita modo (IN e sempre implicito) —
                // so PROCEDURE escreve o modo por extenso.
                if (!isFunction) {
                    def.append(mode.isEmpty() ? "IN" : mode).append(' ');
                }
                def.append(name).append(' ').append(type.isEmpty() ? "VARCHAR" : type.toUpperCase(Locale.ROOT));
                if (!length.isEmpty()) {
                    def.append('(').append(length).append(')');
                }
                out.add(def.toString());
            }
            return out;
        }

        private static String str(Object v) {
            return v == null ? "" : v.toString();
        }
    }
}
