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
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;

import com.nureal.ide.core.dialect.DatabaseDialect;
import com.nureal.ide.core.format.SqlFormatter;

/**
 * Assistente para CRIAR ou EDITAR um TRIGGER — pedido explicito do usuario:
 * "preciso de tudo que necessario para construcao de esquemas, tabelas,
 * views, triggers e etc". Mesma familia de dialogo guiado do
 * {@link DdlAssistantDialog}/{@link ViewBuilderDialog}: cabecalho (nome,
 * BEFORE/AFTER, INSERT/UPDATE/DELETE, tabela), corpo editavel (o que fica
 * entre {@code BEGIN...END}) e pre-visualizacao do DDL final antes de
 * executar.
 * <p>
 * O MySQL nao tem {@code ALTER TRIGGER} nem {@code CREATE OR REPLACE
 * TRIGGER}: "editar" um trigger e sempre {@code DROP TRIGGER} seguido de
 * {@code CREATE TRIGGER} — duas instrucoes separadas (ver
 * {@link #buildStatements}), executadas nesta ordem pelo mesmo
 * {@link DdlAssistantDialog.DdlRunner} usado pelos outros assistentes. Risco
 * aceito e avisado na propria UI: se o CREATE falhar depois do DROP ter
 * funcionado (ex.: erro de sintaxe no corpo editado), o trigger fica
 * removido ate o usuario corrigir e executar de novo — nao ha um jeito
 * atomico de "substituir" um trigger no MySQL.
 * <p>
 * No modo EDITAR, timing/evento/tabela/corpo vem de {@code SHOW CREATE
 * TRIGGER} (ver {@link DatabaseDialect#definitionQuery}), parseados por uma
 * heuristica de regex (ver {@link #parseExisting}) — melhor esforco: se o
 * parsing falhar, o dialogo abre com os campos em branco e o corpo bruto
 * completo na aba de corpo, para o usuario ajustar a mao antes de executar.
 * Renomear o trigger nao e suportado.
 */
final class TriggerBuilderDialog {

    private TriggerBuilderDialog() {
    }

    private static final String[] TIMINGS = { "BEFORE", "AFTER" };
    private static final String[] EVENTS = { "INSERT", "UPDATE", "DELETE" };
    private static final Pattern IDENTIFIER = Pattern.compile("^[A-Za-z_][A-Za-z0-9_$]*$");

    /** Cabecalho padrao do MySQL: "... TRIGGER `nome` BEFORE|AFTER INSERT|UPDATE|DELETE ON `tabela` FOR EACH ROW <resto>". */
    private static final Pattern TRIGGER_HEADER = Pattern.compile(
            "(?is)TRIGGER\\s+`?[^`\\s(]+`?\\s+(BEFORE|AFTER)\\s+(INSERT|UPDATE|DELETE)\\s+ON\\s+`?([^`\\s(]+)`?"
                    + "\\s+FOR\\s+EACH\\s+ROW\\s*(.*)$");

    /** Abre o assistente no modo "criar trigger novo". {@code tableNames} alimenta o combo "ON tabela". */
    static void openCreate(Component parent, DatabaseDialect dialect, List<String> tableNames,
            Predicate<String> nameTaken, DdlAssistantDialog.DdlRunner runner, Consumer<String> sendToEditor,
            Runnable onSuccess) {
        new Session(parent, dialect, tableNames, null, "BEFORE", "INSERT", tableNames.isEmpty() ? "" : tableNames.get(0),
                "", nameTaken, runner, sendToEditor, onSuccess).show();
    }

    /** Abre o assistente no modo "editar trigger existente" — {@code rawDefinition} e o resultado bruto de SHOW CREATE TRIGGER. */
    static void openEdit(Component parent, DatabaseDialect dialect, List<String> tableNames, String triggerName,
            String rawDefinition, DdlAssistantDialog.DdlRunner runner, Consumer<String> sendToEditor,
            Runnable onSuccess) {
        String[] parsed = parseExisting(rawDefinition);
        new Session(parent, dialect, tableNames, triggerName, parsed[0], parsed[1], parsed[2], parsed[3], null,
                runner, sendToEditor, onSuccess).show();
    }

    /** {timing, event, table, body} — melhor esforco (ver javadoc da classe); campos em branco se o parsing falhar. */
    private static String[] parseExisting(String rawDefinition) {
        if (rawDefinition == null) {
            return new String[] { "BEFORE", "INSERT", "", "" };
        }
        Matcher m = TRIGGER_HEADER.matcher(rawDefinition.trim());
        if (!m.find()) {
            return new String[] { "BEFORE", "INSERT", "", rawDefinition.trim() };
        }
        String timing = m.group(1).toUpperCase(Locale.ROOT);
        String event = m.group(2).toUpperCase(Locale.ROOT);
        String table = m.group(3);
        String rest = m.group(4) == null ? "" : m.group(4).trim();
        return new String[] { timing, event, table, extractBody(rest) };
    }

    /** Se {@code rest} tem BEGIN...END, devolve so o que esta entre o PRIMEIRO BEGIN e o ULTIMO END; senao, {@code rest} inteiro. */
    private static String extractBody(String rest) {
        Matcher beginM = Pattern.compile("(?i)\\bBEGIN\\b").matcher(rest);
        Matcher endM = Pattern.compile("(?i)\\bEND\\b\\s*;?\\s*$").matcher(rest);
        if (beginM.find() && endM.find() && endM.start() > beginM.end()) {
            return rest.substring(beginM.end(), endM.start()).trim();
        }
        return rest.trim();
    }

    private static final class Session {
        private final Window owner;
        private final DatabaseDialect dialect;
        private final List<String> tableNames;
        private final String triggerName; // null = modo "criar"
        private final String initialTiming;
        private final String initialEvent;
        private final String initialTable;
        private final String initialBody;
        private final Predicate<String> nameTaken;
        private final DdlAssistantDialog.DdlRunner runner;
        private final Consumer<String> sendToEditor;
        private final Runnable onSuccess;
        private final boolean editMode;

        private JDialog dialog;
        private JTextField nameField;
        private JComboBox<String> timingCombo;
        private JComboBox<String> eventCombo;
        private JComboBox<String> tableCombo;
        private RSyntaxTextArea bodyArea;
        private RSyntaxTextArea previewArea;
        private JTabbedPane tabs;
        private int previewTabIndex;

        Session(Component parent, DatabaseDialect dialect, List<String> tableNames, String triggerName,
                String initialTiming, String initialEvent, String initialTable, String initialBody,
                Predicate<String> nameTaken, DdlAssistantDialog.DdlRunner runner, Consumer<String> sendToEditor,
                Runnable onSuccess) {
            this.owner = (DialogUtil.owner(parent) instanceof Window w) ? w : null;
            this.dialect = dialect;
            this.tableNames = tableNames;
            this.triggerName = triggerName;
            this.initialTiming = initialTiming;
            this.initialEvent = initialEvent;
            this.initialTable = initialTable;
            this.initialBody = initialBody;
            this.nameTaken = nameTaken;
            this.runner = runner;
            this.sendToEditor = sendToEditor;
            this.onSuccess = onSuccess;
            this.editMode = triggerName != null;
        }

        void show() {
            dialog = new JDialog(owner, editMode ? "Editar trigger \"" + triggerName + "\"" : "Novo trigger",
                    Dialog.ModalityType.APPLICATION_MODAL);
            dialog.setLayout(new BorderLayout());
            dialog.add(buildHeader(), BorderLayout.NORTH);
            dialog.add(buildTabs(), BorderLayout.CENTER);
            dialog.add(buildFooter(), BorderLayout.SOUTH);
            dialog.getRootPane().registerKeyboardAction(e -> dialog.dispose(),
                    KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
            dialog.setSize(760, 580);
            dialog.setLocationRelativeTo(owner);
            dialog.setVisible(true);
        }

        private JComponent buildHeader() {
            JPanel form = new JPanel(new GridBagLayout());
            form.setBorder(BorderFactory.createEmptyBorder(10, 10, 6, 10));
            GridBagConstraints c = new GridBagConstraints();
            c.insets = new Insets(3, 4, 3, 4);
            c.anchor = GridBagConstraints.WEST;

            c.gridx = 0;
            c.gridy = 0;
            form.add(new JLabel("Nome do trigger:"), c);
            c.gridx = 1;
            c.weightx = 1;
            c.fill = GridBagConstraints.HORIZONTAL;
            nameField = new JTextField(editMode ? triggerName : "", 24);
            nameField.setEditable(!editMode);
            if (editMode) {
                nameField.setToolTipText("Renomear trigger nao e suportado por este assistente.");
            }
            form.add(nameField, c);

            c.gridx = 0;
            c.gridy = 1;
            c.weightx = 0;
            c.fill = GridBagConstraints.NONE;
            form.add(new JLabel("Momento:"), c);
            c.gridx = 1;
            timingCombo = new JComboBox<>(TIMINGS);
            timingCombo.setSelectedItem(initialTiming);
            form.add(timingCombo, c);

            c.gridx = 0;
            c.gridy = 2;
            form.add(new JLabel("Evento:"), c);
            c.gridx = 1;
            eventCombo = new JComboBox<>(EVENTS);
            eventCombo.setSelectedItem(initialEvent);
            form.add(eventCombo, c);

            c.gridx = 0;
            c.gridy = 3;
            form.add(new JLabel("Tabela (ON):"), c);
            c.gridx = 1;
            c.weightx = 1;
            c.fill = GridBagConstraints.HORIZONTAL;
            tableCombo = new JComboBox<>(tableNames.toArray(new String[0]));
            if (initialTable != null && !initialTable.isEmpty()) {
                tableCombo.setSelectedItem(initialTable);
            }
            form.add(tableCombo, c);

            JLabel banner = new JLabel(editMode
                    ? "Modo EDITAR: MySQL nao tem ALTER TRIGGER — executar aqui faz DROP TRIGGER seguido de CREATE TRIGGER."
                    : "Modo CRIAR: escolha momento/evento/tabela e escreva o corpo (entre BEGIN e END) na aba ao lado.");
            Typography.tertiary(banner);
            banner.setBorder(BorderFactory.createEmptyBorder(0, 10, 6, 10));

            JPanel wrap = new JPanel(new BorderLayout());
            wrap.add(banner, BorderLayout.NORTH);
            wrap.add(form, BorderLayout.CENTER);
            return wrap;
        }

        private JComponent buildTabs() {
            tabs = new JTabbedPane();
            tabs.addTab("Corpo do trigger", buildBodyTab());
            previewTabIndex = tabs.getTabCount();
            tabs.addTab("DDL (pre-visualizacao)", buildPreviewTab());
            tabs.addChangeListener(e -> {
                if (tabs.getSelectedIndex() == previewTabIndex) {
                    refreshPreview();
                }
            });
            return tabs;
        }

        private JComponent buildBodyTab() {
            JPanel panel = new JPanel(new BorderLayout(0, 8));
            panel.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
            bodyArea = new RSyntaxTextArea(initialBody);
            SqlEditorPane.styleEditableSql(bodyArea);
            bodyArea.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
            panel.add(new JLabel("Comandos a executar (entre BEGIN e END; ex.: SET NEW.coluna = ...; ou INSERT INTO log ...;):"),
                    BorderLayout.NORTH);
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
                List<String> statements = buildStatements();
                SqlFormatter formatter = new SqlFormatter(SqlFormatter.KeywordCase.UPPER, SqlFormatter.Style.STANDARD,
                        false);
                StringBuilder sb = new StringBuilder();
                for (String s : statements) {
                    sb.append(formatter.format(s)).append(";\n\n");
                }
                previewArea.setText(sb.toString().trim());
            } catch (ValidationException ex) {
                previewArea.setText("-- " + ex.getMessage());
            }
            previewArea.setCaretPosition(0);
        }

        private JComponent buildFooter() {
            JButton close = new JButton("Fechar");
            close.addActionListener(a -> dialog.dispose());
            Buttons.styleSecondary(close);

            JButton execute = new JButton(editMode ? "Executar (recriar trigger)" : "Executar CREATE TRIGGER");
            Buttons.stylePrimary(execute);
            execute.addActionListener(a -> onExecute());

            JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 8));
            panel.setBorder(BorderFactory.createEmptyBorder(0, 10, 4, 10));
            panel.add(close);
            panel.add(execute);
            return panel;
        }

        private void onExecute() {
            List<String> statements;
            try {
                statements = buildStatements();
            } catch (ValidationException ex) {
                JOptionPane.showMessageDialog(dialog, ex.getMessage(), "Novo/editar trigger",
                        JOptionPane.WARNING_MESSAGE);
                tabs.setSelectedIndex(0);
                return;
            }
            if (editMode) {
                int choice = JOptionPane.showConfirmDialog(dialog,
                        "MySQL nao tem ALTER TRIGGER: para atualizar, o trigger \"" + triggerName + "\" sera "
                                + "REMOVIDO e recriado em seguida. Se o CREATE falhar (ex.: erro de sintaxe), o "
                                + "trigger fica removido ate voce corrigir e executar de novo.\n\nContinuar?",
                        "Confirmar recriacao", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                if (choice != JOptionPane.YES_OPTION) {
                    return;
                }
            }
            if (runner == null) {
                return;
            }
            runner.run(statements, () -> {
                JOptionPane.showMessageDialog(dialog,
                        editMode ? "Trigger \"" + triggerName + "\" recriado com sucesso."
                                : "Trigger \"" + nameField.getText().trim() + "\" criado com sucesso.",
                        "Novo/editar trigger", JOptionPane.INFORMATION_MESSAGE);
                if (onSuccess != null) {
                    onSuccess.run();
                }
                dialog.dispose();
            }, ex -> JOptionPane.showMessageDialog(dialog, "Falha ao executar:\n" + ex.getMessage(),
                    "Novo/editar trigger", JOptionPane.ERROR_MESSAGE));
        }

        private static final class ValidationException extends RuntimeException {
            private static final long serialVersionUID = 1L;

            ValidationException(String message) {
                super(message);
            }
        }

        private List<String> buildStatements() {
            String body = bodyArea.getText().trim();
            if (body.isEmpty()) {
                throw new ValidationException("Escreva pelo menos um comando no corpo do trigger.");
            }
            Object tableSelected = tableCombo.getSelectedItem();
            if (tableSelected == null || tableSelected.toString().isBlank()) {
                throw new ValidationException("Escolha a tabela (ON) do trigger.");
            }
            String table = tableSelected.toString();
            String timing = (String) timingCombo.getSelectedItem();
            String event = (String) eventCombo.getSelectedItem();

            if (editMode) {
                return List.of(dialect.dropTriggerStatement(triggerName),
                        dialect.createTriggerStatement(triggerName, timing, event, table, body));
            }
            String name = nameField.getText().trim();
            if (name.isEmpty()) {
                throw new ValidationException("Informe o nome do trigger.");
            }
            if (!IDENTIFIER.matcher(name).matches()) {
                throw new ValidationException(
                        "Nome de trigger invalido: \"" + name + "\". Use letras, numeros e _ (nao pode comecar com numero).");
            }
            if (nameTaken != null && nameTaken.test(name)) {
                throw new ValidationException("Ja existe um trigger chamado \"" + name + "\".");
            }
            return List.of(dialect.createTriggerStatement(name, timing, event, table, body));
        }
    }
}
