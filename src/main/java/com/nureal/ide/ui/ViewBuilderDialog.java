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
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.BorderFactory;
import javax.swing.JButton;
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
 * Assistente para CRIAR ou EDITAR uma VIEW — pedido explicito do usuario:
 * "preciso de tudo que necessario para construcao de esquemas, tabelas,
 * views, triggers e etc". Mesma familia de dialogo guiado do
 * {@link DdlAssistantDialog} (nome + corpo editavel, pre-visualizacao do DDL
 * final formatado antes de executar), bem mais simples porque uma view e so
 * um nome + uma consulta SELECT.
 * <p>
 * No modo EDITAR, o corpo do SELECT vem de {@code SHOW CREATE VIEW} (ver
 * {@link DatabaseDialect#definitionQuery}) com o cabecalho
 * ALGORITHM/DEFINER/SQL SECURITY/nome removido por uma heuristica de regex
 * (ver {@link #extractSelectBody}) — melhor esforco: se a definicao bruta
 * fugir do formato usual do MySQL, o corpo inteiro (sem remover nada) e
 * mostrado, e o usuario revisa antes de executar. Editar sempre gera
 * {@code CREATE OR REPLACE VIEW} (nao ha ALTER VIEW parcial no MySQL);
 * renomear a view nao e suportado, mesmo criterio do assistente de tabela.
 */
final class ViewBuilderDialog {

    private ViewBuilderDialog() {
    }

    /** Abre o assistente no modo "criar view nova". */
    static void openCreate(Component parent, DatabaseDialect dialect, Predicate<String> nameTaken,
            DdlAssistantDialog.DdlRunner runner, Consumer<String> sendToEditor, Runnable onSuccess) {
        new Session(parent, dialect, null, "", nameTaken, runner, sendToEditor, onSuccess).show();
    }

    /** Abre o assistente no modo "editar view existente" — {@code rawDefinition} e o resultado bruto de SHOW CREATE VIEW. */
    static void openEdit(Component parent, DatabaseDialect dialect, String viewName, String rawDefinition,
            DdlAssistantDialog.DdlRunner runner, Consumer<String> sendToEditor, Runnable onSuccess) {
        new Session(parent, dialect, viewName, extractSelectBody(rawDefinition), null, runner, sendToEditor,
                onSuccess).show();
    }

    private static final Pattern IDENTIFIER = Pattern.compile("^[A-Za-z_][A-Za-z0-9_$]*$");
    // Reluctante: para no PRIMEIRO "AS" apos a primeira ocorrencia de "VIEW" —
    // no formato padrao do MySQL (CREATE [ALGORITHM=...] [DEFINER=...] [SQL
    // SECURITY ...] VIEW `nome` AS select...), esse e exatamente o separador
    // entre o cabecalho/nome e o corpo do SELECT.
    private static final Pattern VIEW_AS = Pattern.compile("(?is)\\bVIEW\\b.*?\\bAS\\b\\s*(.*)$");

    /** Extrai so o SELECT de um "SHOW CREATE VIEW" — ver javadoc da classe. */
    private static String extractSelectBody(String rawDefinition) {
        if (rawDefinition == null) {
            return "";
        }
        Matcher m = VIEW_AS.matcher(rawDefinition.trim());
        return m.find() ? m.group(1).trim() : rawDefinition.trim();
    }

    private static final class Session {
        private final Window owner;
        private final DatabaseDialect dialect;
        private final String viewName; // null = modo "criar"
        private final String initialSelect;
        private final Predicate<String> nameTaken;
        private final DdlAssistantDialog.DdlRunner runner;
        private final Consumer<String> sendToEditor;
        private final Runnable onSuccess;
        private final boolean editMode;

        private JDialog dialog;
        private JTextField nameField;
        private RSyntaxTextArea selectArea;
        private RSyntaxTextArea previewArea;
        private JTabbedPane tabs;
        private int previewTabIndex;

        Session(Component parent, DatabaseDialect dialect, String viewName, String initialSelect,
                Predicate<String> nameTaken, DdlAssistantDialog.DdlRunner runner, Consumer<String> sendToEditor,
                Runnable onSuccess) {
            this.owner = (DialogUtil.owner(parent) instanceof Window w) ? w : null;
            this.dialect = dialect;
            this.viewName = viewName;
            this.initialSelect = initialSelect;
            this.nameTaken = nameTaken;
            this.runner = runner;
            this.sendToEditor = sendToEditor;
            this.onSuccess = onSuccess;
            this.editMode = viewName != null;
        }

        void show() {
            dialog = new JDialog(owner, editMode ? "Editar view \"" + viewName + "\"" : "Nova view",
                    Dialog.ModalityType.APPLICATION_MODAL);
            dialog.setLayout(new BorderLayout());
            dialog.add(buildHeader(), BorderLayout.NORTH);
            dialog.add(buildTabs(), BorderLayout.CENTER);
            dialog.add(buildFooter(), BorderLayout.SOUTH);
            dialog.getRootPane().registerKeyboardAction(e -> dialog.dispose(),
                    KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
            dialog.setSize(760, 560);
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
            form.add(new JLabel("Nome da view:"), c);
            c.gridx = 1;
            c.weightx = 1;
            c.fill = GridBagConstraints.HORIZONTAL;
            nameField = new JTextField(editMode ? viewName : "", 30);
            nameField.setEditable(!editMode);
            if (editMode) {
                nameField.setToolTipText("Renomear view nao e suportado por este assistente.");
            }
            form.add(nameField, c);

            JLabel banner = new JLabel(editMode
                    ? "Modo EDITAR: gera CREATE OR REPLACE VIEW com o SELECT abaixo (substitui a view inteira)."
                    : "Modo CRIAR: informe o nome e o SELECT que a view vai expor.");
            Typography.tertiary(banner);
            banner.setBorder(BorderFactory.createEmptyBorder(0, 10, 6, 10));

            JPanel wrap = new JPanel(new BorderLayout());
            wrap.add(banner, BorderLayout.NORTH);
            wrap.add(form, BorderLayout.CENTER);
            return wrap;
        }

        private JComponent buildTabs() {
            tabs = new JTabbedPane();
            tabs.addTab("SELECT da view", buildSelectTab());
            previewTabIndex = tabs.getTabCount();
            tabs.addTab("DDL (pre-visualizacao)", buildPreviewTab());
            tabs.addChangeListener(e -> {
                if (tabs.getSelectedIndex() == previewTabIndex) {
                    refreshPreview();
                }
            });
            return tabs;
        }

        private JComponent buildSelectTab() {
            JPanel panel = new JPanel(new BorderLayout(0, 8));
            panel.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
            selectArea = new RSyntaxTextArea(initialSelect);
            SqlEditorPane.styleEditableSql(selectArea);
            selectArea.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
            panel.add(new JLabel(editMode ? "Edite o SELECT (substitui a view inteira ao executar):"
                    : "Escreva o SELECT que a view vai expor:"), BorderLayout.NORTH);
            panel.add(new JScrollPane(selectArea), BorderLayout.CENTER);
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
            } catch (ValidationException ex) {
                previewArea.setText("-- " + ex.getMessage());
            }
            previewArea.setCaretPosition(0);
        }

        private JComponent buildFooter() {
            JButton close = new JButton("Fechar");
            close.addActionListener(a -> dialog.dispose());
            Buttons.styleSecondary(close);

            JButton execute = new JButton(editMode ? "Executar CREATE OR REPLACE VIEW" : "Executar CREATE VIEW");
            Buttons.stylePrimary(execute);
            execute.addActionListener(a -> onExecute());

            JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 8));
            panel.setBorder(BorderFactory.createEmptyBorder(0, 10, 4, 10));
            panel.add(close);
            panel.add(execute);
            return panel;
        }

        private void onExecute() {
            String sql;
            try {
                sql = buildStatement();
            } catch (ValidationException ex) {
                JOptionPane.showMessageDialog(dialog, ex.getMessage(), "Nova/editar view", JOptionPane.WARNING_MESSAGE);
                tabs.setSelectedIndex(0);
                return;
            }
            if (runner == null) {
                return;
            }
            runner.run(java.util.List.of(sql), () -> {
                JOptionPane.showMessageDialog(dialog,
                        editMode ? "View \"" + viewName + "\" atualizada com sucesso."
                                : "View \"" + nameField.getText().trim() + "\" criada com sucesso.",
                        "Nova/editar view", JOptionPane.INFORMATION_MESSAGE);
                if (onSuccess != null) {
                    onSuccess.run();
                }
                dialog.dispose();
            }, ex -> JOptionPane.showMessageDialog(dialog, "Falha ao executar:\n" + ex.getMessage(),
                    "Nova/editar view", JOptionPane.ERROR_MESSAGE));
        }

        private static final class ValidationException extends RuntimeException {
            private static final long serialVersionUID = 1L;

            ValidationException(String message) {
                super(message);
            }
        }

        private String buildStatement() {
            String select = selectArea.getText().trim();
            if (select.isEmpty()) {
                throw new ValidationException("Escreva o SELECT que a view vai expor.");
            }
            if (!select.toUpperCase(java.util.Locale.ROOT).startsWith("SELECT")) {
                throw new ValidationException("O corpo da view deve comecar com SELECT.");
            }
            if (editMode) {
                return dialect.replaceViewStatement(viewName, select);
            }
            String name = nameField.getText().trim();
            if (name.isEmpty()) {
                throw new ValidationException("Informe o nome da view.");
            }
            if (!IDENTIFIER.matcher(name).matches()) {
                throw new ValidationException(
                        "Nome de view invalido: \"" + name + "\". Use letras, numeros e _ (nao pode comecar com numero).");
            }
            if (nameTaken != null && nameTaken.test(name)) {
                throw new ValidationException("Ja existe uma tabela/view chamada \"" + name + "\".");
            }
            return dialect.createViewStatement(name, select);
        }
    }
}
