package com.nureal.ide.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.datatransfer.StringSelection;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.BorderFactory;
import javax.swing.DefaultCellEditor;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.table.DefaultTableModel;

import com.nureal.ide.core.ddl.NormalizationAdvisor;
import com.nureal.ide.core.dialect.DatabaseDialect;
import com.nureal.ide.core.format.SqlFormatter;
import com.nureal.ide.core.metadata.model.ColumnDetail;
import com.nureal.ide.core.metadata.model.ForeignKeyInfo;
import com.nureal.ide.core.metadata.model.IndexInfo;
import com.nureal.ide.core.metadata.model.NewColumnSpec;
import com.nureal.ide.core.metadata.model.NewTableSpec;
import com.nureal.ide.core.metadata.model.SchemaInfo;
import com.nureal.ide.core.metadata.model.TableDetails;
import com.nureal.ide.core.metadata.model.TableInfo;

/**
 * Assistente de DDL guiado: cria uma tabela nova OU adiciona colunas/chaves
 * estrangeiras/indices a uma tabela existente, com abas separadas para cada
 * decisao (colunas, FKs, indices), uma aba de SUGESTOES DE NORMALIZACAO (ver
 * {@link NormalizationAdvisor}) que reage ao que foi preenchido, e uma aba de
 * pre-visualizacao do DDL final (ja formatado — ver {@link SqlFormatter})
 * antes de executar.
 * <p>
 * No modo "alterar tabela", so PERMITE ADICOES (novas colunas, FKs, indices):
 * mudar/remover algo que ja existe fica fora do escopo guiado (risco de
 * perda de dados exige revisao manual do usuario) — ver
 * {@code DatabaseDialect#alterTableAddStatements}. As colunas atuais
 * aparecem em uma grade separada, so leitura, para dar contexto.
 */
final class DdlAssistantDialog {

    private DdlAssistantDialog() {
    }

    /** Executa os comandos DDL prontos (ja montados) e chama de volta na EDT. */
    interface DdlRunner {
        void run(List<String> statements, Runnable onSuccess, Consumer<Exception> onError);
    }

    /** Abre o assistente no modo "criar tabela nova". */
    static void openCreate(Component parent, SchemaInfo schema, DatabaseDialect dialect,
            java.util.function.Predicate<String> tableNameTaken, DdlRunner runner,
            Consumer<String> sendToEditor, Runnable onSuccess) {
        new Session(parent, schema, dialect, null, null, tableNameTaken, runner, sendToEditor, onSuccess).show();
    }

    /** Abre o assistente no modo "alterar tabela existente" (so adicoes). */
    static void openAlter(Component parent, SchemaInfo schema, String tableName, TableDetails details,
            DatabaseDialect dialect, DdlRunner runner, Consumer<String> sendToEditor, Runnable onSuccess) {
        new Session(parent, schema, dialect, tableName, details, null, runner, sendToEditor, onSuccess).show();
    }

    private static final String[] TYPES = {
            "VARCHAR", "CHAR", "TEXT", "MEDIUMTEXT", "LONGTEXT",
            "INT", "BIGINT", "SMALLINT", "TINYINT",
            "DECIMAL", "FLOAT", "DOUBLE",
            "DATE", "DATETIME", "TIMESTAMP", "TIME",
            "BOOLEAN", "JSON", "BLOB"
    };
    private static final String[] FK_ACTIONS = { "RESTRICT", "CASCADE", "SET NULL", "NO ACTION" };
    private static final Pattern IDENTIFIER = Pattern.compile("^[A-Za-z_][A-Za-z0-9_$]*$");
    private static final Pattern TYPE_PARTS = Pattern.compile("^\\s*([A-Za-z_]+)\\s*(?:\\(([^)]*)\\))?");

    /** Uma execucao do assistente: estado + toda a UI. Instanciada a cada abertura. */
    private static final class Session {
        private final Window owner;
        private final SchemaInfo schema;
        private final DatabaseDialect dialect;
        private final String alterTableName; // null = modo "criar"
        private final TableDetails existingDetails; // so preenchido no modo "alterar"
        private final java.util.function.Predicate<String> tableNameTaken;
        private final DdlRunner runner;
        private final Consumer<String> sendToEditor;
        private final Runnable onSuccess;
        private final boolean alterMode;

        private JDialog dialog;
        private JTextField nameField;
        private JTextField commentField;
        private DefaultTableModel columnsModel;
        private DefaultTableModel fkModel;
        private DefaultTableModel indexModel;
        private JTextArea suggestionsArea;
        private JTextArea previewArea;
        private JTabbedPane tabs;
        private int suggestionsTabIndex;
        private int previewTabIndex;

        Session(Component parent, SchemaInfo schema, DatabaseDialect dialect, String alterTableName,
                TableDetails existingDetails, java.util.function.Predicate<String> tableNameTaken,
                DdlRunner runner, Consumer<String> sendToEditor, Runnable onSuccess) {
            this.owner = (DialogUtil.owner(parent) instanceof Window w) ? w : null;
            this.schema = schema;
            this.dialect = dialect;
            this.alterTableName = alterTableName;
            this.existingDetails = existingDetails;
            this.tableNameTaken = tableNameTaken;
            this.runner = runner;
            this.sendToEditor = sendToEditor;
            this.onSuccess = onSuccess;
            this.alterMode = alterTableName != null;
        }

        void show() {
            dialog = new JDialog(owner, alterMode ? "Assistente de DDL — Alterar tabela \"" + alterTableName + "\""
                    : "Assistente de DDL — Nova tabela", Dialog.ModalityType.APPLICATION_MODAL);
            dialog.setLayout(new BorderLayout());
            dialog.add(buildHeader(), BorderLayout.NORTH);
            dialog.add(buildTabs(), BorderLayout.CENTER);
            dialog.add(buildFooter(), BorderLayout.SOUTH);
            dialog.getRootPane().registerKeyboardAction(e -> dialog.dispose(),
                    KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
            dialog.setSize(920, 680);
            dialog.setLocationRelativeTo(owner);
            dialog.setVisible(true);
        }

        // ---------- Cabecalho: nome/comentario da tabela ----------

        private JComponent buildHeader() {
            JPanel form = new JPanel(new GridBagLayout());
            form.setBorder(BorderFactory.createEmptyBorder(10, 10, 6, 10));
            GridBagConstraints c = new GridBagConstraints();
            c.insets = new Insets(3, 4, 3, 4);
            c.anchor = GridBagConstraints.WEST;

            c.gridx = 0;
            c.gridy = 0;
            form.add(new JLabel("Nome da tabela:"), c);
            c.gridx = 1;
            c.weightx = 1;
            c.fill = GridBagConstraints.HORIZONTAL;
            nameField = new JTextField(alterMode ? alterTableName : "", 24);
            nameField.setEditable(!alterMode);
            if (alterMode) {
                nameField.setToolTipText("Renomear tabela nao e suportado por este assistente.");
            }
            form.add(nameField, c);

            c.gridx = 0;
            c.gridy = 1;
            c.weightx = 0;
            c.fill = GridBagConstraints.NONE;
            form.add(new JLabel("Comentario:"), c);
            c.gridx = 1;
            c.weightx = 1;
            c.fill = GridBagConstraints.HORIZONTAL;
            commentField = new JTextField(24);
            form.add(commentField, c);

            JLabel banner = new JLabel(alterMode
                    ? "Modo ALTERAR: so adiciona colunas/chaves/indices novos (nunca modifica ou remove o que ja existe)."
                    : "Modo CRIAR: monte a tabela do zero, com sugestoes de normalizacao antes de executar.");
            banner.setForeground(new Color(0x6B7280));
            banner.setBorder(BorderFactory.createEmptyBorder(0, 10, 6, 10));

            JPanel wrap = new JPanel(new BorderLayout());
            wrap.add(banner, BorderLayout.NORTH);
            wrap.add(form, BorderLayout.CENTER);
            return wrap;
        }

        // ---------- Abas ----------

        private JComponent buildTabs() {
            tabs = new JTabbedPane();
            tabs.addTab("Colunas", buildColumnsTab());
            tabs.addTab("Chaves estrangeiras", buildForeignKeysTab());
            tabs.addTab("Indices", buildIndexesTab());
            suggestionsTabIndex = tabs.getTabCount();
            tabs.addTab("Sugestoes de normalizacao", buildSuggestionsTab());
            previewTabIndex = tabs.getTabCount();
            tabs.addTab("DDL (pre-visualizacao)", buildPreviewTab());
            tabs.addChangeListener(e -> {
                int idx = tabs.getSelectedIndex();
                if (idx == suggestionsTabIndex) {
                    refreshSuggestions();
                } else if (idx == previewTabIndex) {
                    refreshPreview();
                }
            });
            return tabs;
        }

        // ---------- Aba: Colunas ----------

        private JComponent buildColumnsTab() {
            JPanel panel = new JPanel(new BorderLayout(0, 8));
            panel.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));

            if (alterMode) {
                DefaultTableModel existingModel = new DefaultTableModel(
                        new String[] { "Coluna", "Tipo", "Nulo", "Chave", "Default", "Extra" }, 0) {
                    private static final long serialVersionUID = 1L;

                    @Override
                    public boolean isCellEditable(int row, int column) {
                        return false;
                    }
                };
                for (ColumnDetail cd : existingDetails.columns()) {
                    existingModel.addRow(new Object[] { cd.name(), cd.type(), cd.nullable() ? "sim" : "nao",
                            cd.key(), cd.defaultValue(), cd.extra() });
                }
                JTable existingTable = new JTable(existingModel);
                existingTable.setRowHeight(22);
                JScrollPane existingScroll = new JScrollPane(existingTable);
                existingScroll.setPreferredSize(new Dimension(880, 140));
                JPanel existingWrap = new JPanel(new BorderLayout(0, 4));
                existingWrap.add(new JLabel("Colunas atuais (somente leitura):"), BorderLayout.NORTH);
                existingWrap.add(existingScroll, BorderLayout.CENTER);
                panel.add(existingWrap, BorderLayout.NORTH);
            }

            String[] headers = { "Nome", "Tipo", "Tamanho", "Nulo", "PK", "AI", "Default", "Comentario" };
            columnsModel = new DefaultTableModel(headers, 0) {
                private static final long serialVersionUID = 1L;

                @Override
                public Class<?> getColumnClass(int columnIndex) {
                    return (columnIndex == 3 || columnIndex == 4 || columnIndex == 5) ? Boolean.class : String.class;
                }

                @Override
                public boolean isCellEditable(int row, int column) {
                    // No modo alterar, nao oferecemos PK/AI para colunas novas — mexer na
                    // chave primaria de uma tabela existente e sensivel demais para um
                    // botao de assistente; fica para SQL manual revisado a mao.
                    if (alterMode && (column == 4 || column == 5)) {
                        return false;
                    }
                    return true;
                }
            };
            if (!alterMode) {
                addDefaultColumnRow(columnsModel);
            }
            JTable table = new JTable(columnsModel);
            table.setRowHeight(24);
            table.getColumnModel().getColumn(1).setCellEditor(new DefaultCellEditor(new JComboBox<>(TYPES)));
            table.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);

            JButton addRow = new JButton("+ Coluna");
            addRow.addActionListener(a -> {
                stopEditing(table);
                columnsModel.addRow(new Object[] { "", "VARCHAR", "255", Boolean.TRUE, Boolean.FALSE, Boolean.FALSE,
                        "", "" });
            });
            JButton removeRow = new JButton("- Coluna");
            removeRow.addActionListener(a -> {
                stopEditing(table);
                int[] rows = table.getSelectedRows();
                for (int i = rows.length - 1; i >= 0; i--) {
                    columnsModel.removeRow(rows[i]);
                }
            });
            JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
            buttons.add(addRow);
            buttons.add(removeRow);

            JPanel newWrap = new JPanel(new BorderLayout(0, 4));
            newWrap.add(new JLabel(alterMode ? "Colunas novas a adicionar:" : "Colunas:"), BorderLayout.NORTH);
            JScrollPane scroll = new JScrollPane(table);
            scroll.setPreferredSize(new Dimension(880, alterMode ? 200 : 320));
            newWrap.add(scroll, BorderLayout.CENTER);
            newWrap.add(buttons, BorderLayout.SOUTH);
            panel.add(newWrap, BorderLayout.CENTER);
            return panel;
        }

        private static void addDefaultColumnRow(DefaultTableModel model) {
            if (model.getRowCount() == 0) {
                model.addRow(new Object[] { "id", "INT", "", Boolean.FALSE, Boolean.TRUE, Boolean.TRUE, "", "" });
            }
        }

        // ---------- Aba: Chaves estrangeiras ----------

        private JComponent buildForeignKeysTab() {
            JPanel panel = new JPanel(new BorderLayout(0, 8));
            panel.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));

            String[] headers = { "Coluna(s) local(is)", "Tabela referenciada", "Coluna(s) referenciada(s)",
                    "ON UPDATE", "ON DELETE" };
            fkModel = new DefaultTableModel(headers, 0);
            JTable table = new JTable(fkModel);
            table.setRowHeight(24);
            List<String> tableNames = new ArrayList<>();
            for (TableInfo t : schema.tables()) {
                tableNames.add(t.name());
            }
            table.getColumnModel().getColumn(1)
                    .setCellEditor(new DefaultCellEditor(new JComboBox<>(tableNames.toArray(new String[0]))));
            table.getColumnModel().getColumn(3).setCellEditor(new DefaultCellEditor(new JComboBox<>(FK_ACTIONS)));
            table.getColumnModel().getColumn(4).setCellEditor(new DefaultCellEditor(new JComboBox<>(FK_ACTIONS)));
            table.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);

            JButton addRow = new JButton("+ FK");
            addRow.addActionListener(a -> fkModel.addRow(new Object[] { "", "", "id", "RESTRICT", "RESTRICT" }));
            JButton removeRow = new JButton("- FK");
            removeRow.addActionListener(a -> {
                stopEditing(table);
                int[] rows = table.getSelectedRows();
                for (int i = rows.length - 1; i >= 0; i--) {
                    fkModel.removeRow(rows[i]);
                }
            });
            JButton suggest = new JButton("Sugerir a partir de colunas *_id");
            suggest.setToolTipText(
                    "Procura colunas terminadas em \"_id\" (novas e existentes) sem FK ainda e tenta casar "
                            + "com uma tabela do schema pelo nome — confira/ajuste antes de executar.");
            suggest.addActionListener(a -> {
                stopEditing(table);
                suggestForeignKeys();
            });
            JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
            buttons.add(addRow);
            buttons.add(removeRow);
            buttons.add(suggest);

            JScrollPane scroll = new JScrollPane(table);
            panel.add(scroll, BorderLayout.CENTER);
            panel.add(buttons, BorderLayout.SOUTH);
            return panel;
        }

        /** Casa "cliente_id" -&gt; tabela "clientes"/"cliente" (singular/plural simples), se existir no schema. */
        private void suggestForeignKeys() {
            Set<String> alreadyCovered = new LinkedHashSet<>();
            for (int r = 0; r < fkModel.getRowCount(); r++) {
                for (String col : splitCsv(str(fkModel.getValueAt(r, 0)))) {
                    alreadyCovered.add(col.toLowerCase(Locale.ROOT));
                }
            }
            int added = 0;
            for (NewColumnSpec c : collectAllColumnsForAdvisor()) {
                String name = c.name().toLowerCase(Locale.ROOT);
                if (!name.endsWith("_id") || alreadyCovered.contains(name)) {
                    continue;
                }
                String prefix = name.substring(0, name.length() - 3);
                TableInfo matched = findBestTableMatch(prefix);
                if (matched == null) {
                    continue;
                }
                String refCol = guessReferencedColumn(matched);
                fkModel.addRow(new Object[] { c.name(), matched.name(), refCol, "RESTRICT", "RESTRICT" });
                alreadyCovered.add(name);
                added++;
            }
            JOptionPane.showMessageDialog(dialog,
                    added > 0 ? added + " chave(s) estrangeira(s) sugerida(s) — confira e ajuste antes de executar."
                            : "Nenhuma coluna \"*_id\" nova encontrada para sugerir (ou todas ja tem FK).",
                    "Sugestao de chaves estrangeiras", JOptionPane.INFORMATION_MESSAGE);
        }

        private TableInfo findBestTableMatch(String prefix) {
            for (TableInfo t : schema.tables()) {
                String tn = t.name().toLowerCase(Locale.ROOT);
                if (tn.equals(prefix) || tn.equals(prefix + "s") || tn.equals(prefix + "es")
                        || (tn.endsWith("s") && tn.substring(0, tn.length() - 1).equals(prefix))) {
                    return t;
                }
            }
            return null;
        }

        /** "id" se existir na tabela; senao a 1a coluna (costuma ser a PK por convencao). */
        private static String guessReferencedColumn(TableInfo table) {
            for (var col : table.columns()) {
                if ("id".equalsIgnoreCase(col.name())) {
                    return col.name();
                }
            }
            return table.columns().isEmpty() ? "id" : table.columns().get(0).name();
        }

        // ---------- Aba: Indices ----------

        private JComponent buildIndexesTab() {
            JPanel panel = new JPanel(new BorderLayout(0, 8));
            panel.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));

            String[] headers = { "Nome (opcional)", "Coluna(s)", "Unico" };
            indexModel = new DefaultTableModel(headers, 0) {
                private static final long serialVersionUID = 1L;

                @Override
                public Class<?> getColumnClass(int columnIndex) {
                    return columnIndex == 2 ? Boolean.class : String.class;
                }
            };
            JTable table = new JTable(indexModel);
            table.setRowHeight(24);
            table.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);

            JButton addRow = new JButton("+ Indice");
            addRow.addActionListener(a -> indexModel.addRow(new Object[] { "", "", Boolean.FALSE }));
            JButton removeRow = new JButton("- Indice");
            removeRow.addActionListener(a -> {
                stopEditing(table);
                int[] rows = table.getSelectedRows();
                for (int i = rows.length - 1; i >= 0; i--) {
                    indexModel.removeRow(rows[i]);
                }
            });
            JButton suggest = new JButton("Sugerir indices para as FKs");
            suggest.addActionListener(a -> {
                stopEditing(table);
                suggestIndexesForForeignKeys();
            });
            JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
            buttons.add(addRow);
            buttons.add(removeRow);
            buttons.add(suggest);

            JScrollPane scroll = new JScrollPane(table);
            panel.add(scroll, BorderLayout.CENTER);
            panel.add(buttons, BorderLayout.SOUTH);
            return panel;
        }

        private void suggestIndexesForForeignKeys() {
            Set<String> covered = new LinkedHashSet<>();
            for (int r = 0; r < indexModel.getRowCount(); r++) {
                covered.add(str(indexModel.getValueAt(r, 1)).toLowerCase(Locale.ROOT).trim());
            }
            int added = 0;
            for (int r = 0; r < fkModel.getRowCount(); r++) {
                String cols = str(fkModel.getValueAt(r, 0)).trim();
                if (cols.isEmpty() || covered.contains(cols.toLowerCase(Locale.ROOT))) {
                    continue;
                }
                indexModel.addRow(new Object[] { "", cols, Boolean.FALSE });
                covered.add(cols.toLowerCase(Locale.ROOT));
                added++;
            }
            if (added == 0) {
                JOptionPane.showMessageDialog(dialog,
                        "Todas as FKs ja tem um indice equivalente (ou nao ha FK definida ainda).",
                        "Sugestao de indices", JOptionPane.INFORMATION_MESSAGE);
            }
        }

        // ---------- Aba: Sugestoes de normalizacao ----------

        private JComponent buildSuggestionsTab() {
            JPanel panel = new JPanel(new BorderLayout(0, 8));
            panel.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
            suggestionsArea = new JTextArea();
            suggestionsArea.setEditable(false);
            suggestionsArea.setLineWrap(true);
            suggestionsArea.setWrapStyleWord(true);
            suggestionsArea.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
            JButton refresh = new JButton("Atualizar sugestoes");
            refresh.addActionListener(a -> refreshSuggestions());
            panel.add(new JScrollPane(suggestionsArea), BorderLayout.CENTER);
            JPanel south = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
            south.add(refresh);
            panel.add(south, BorderLayout.SOUTH);
            return panel;
        }

        private void refreshSuggestions() {
            List<NewColumnSpec> allColumns = collectAllColumnsForAdvisor();
            List<ForeignKeyInfo> fks = collectForeignKeys(true);
            List<IndexInfo> idxs = collectIndexes(true);
            String tableName = alterMode ? alterTableName : nameField.getText().trim();
            List<String> suggestions = NormalizationAdvisor.analyze(tableName, allColumns, fks, idxs);
            if (suggestions.isEmpty()) {
                suggestionsArea.setText(
                        "Nenhum ponto chamou atencao nas regras automaticas — isso NAO substitui revisao "
                                + "humana, so cobre os casos mais comuns (chave primaria ausente, tipos de dado, "
                                + "grupos repetitivos, dependencias parcial/transitiva e indices de FK).");
            } else {
                StringBuilder sb = new StringBuilder();
                for (String s : suggestions) {
                    sb.append("• ").append(s).append("\n\n");
                }
                suggestionsArea.setText(sb.toString());
            }
            suggestionsArea.setCaretPosition(0);
        }

        // ---------- Aba: DDL ----------

        private JComponent buildPreviewTab() {
            JPanel panel = new JPanel(new BorderLayout(0, 8));
            panel.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
            previewArea = new JTextArea();
            previewArea.setEditable(false);
            previewArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
            previewArea.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
            JButton refresh = new JButton("Atualizar pre-visualizacao");
            refresh.addActionListener(a -> refreshPreview());
            JButton copy = new JButton("Copiar");
            copy.addActionListener(a -> {
                Toolkit.getDefaultToolkit().getSystemClipboard()
                        .setContents(new StringSelection(previewArea.getText()), null);
            });
            JButton toEditor = new JButton("Enviar para o editor");
            toEditor.addActionListener(a -> {
                if (sendToEditor != null) {
                    sendToEditor.accept(previewArea.getText());
                }
            });
            JPanel south = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
            south.add(refresh);
            south.add(copy);
            south.add(toEditor);
            panel.add(new JScrollPane(previewArea), BorderLayout.CENTER);
            panel.add(south, BorderLayout.SOUTH);
            return panel;
        }

        private void refreshPreview() {
            previewArea.setText(buildDdlPreview());
            previewArea.setCaretPosition(0);
        }

        /** Monta o DDL final (CREATE ou ALTER) ja formatado, sem executar nada. */
        private String buildDdlPreview() {
            try {
                List<String> statements = buildStatements();
                if (statements.isEmpty()) {
                    return alterMode ? "-- Nenhuma coluna, chave estrangeira ou indice novo foi adicionado ainda."
                            : "-- Adicione ao menos uma coluna na aba \"Colunas\".";
                }
                SqlFormatter formatter = new SqlFormatter(SqlFormatter.KeywordCase.UPPER, SqlFormatter.Style.STANDARD,
                        false);
                StringBuilder sb = new StringBuilder();
                for (String s : statements) {
                    sb.append(formatter.format(s)).append(";\n\n");
                }
                return sb.toString().trim();
            } catch (ValidationException ex) {
                return "-- " + ex.getMessage();
            }
        }

        // ---------- Rodape ----------

        private JComponent buildFooter() {
            JButton close = new JButton("Fechar");
            close.addActionListener(a -> dialog.dispose());
            JButton execute = new JButton(alterMode ? "Executar ALTER TABLE" : "Executar CREATE TABLE");
            execute.setBackground(new Color(0x059669));
            execute.setForeground(Color.WHITE);
            execute.addActionListener(a -> onExecute());
            JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 8));
            panel.add(close);
            panel.add(execute);
            return panel;
        }

        private void onExecute() {
            List<String> statements;
            try {
                statements = buildStatements();
            } catch (ValidationException ex) {
                JOptionPane.showMessageDialog(dialog, ex.getMessage(), "Assistente de DDL",
                        JOptionPane.WARNING_MESSAGE);
                tabs.setSelectedIndex(0);
                return;
            }
            if (statements.isEmpty()) {
                JOptionPane.showMessageDialog(dialog,
                        alterMode ? "Adicione ao menos uma coluna, chave estrangeira ou indice novo."
                                : "Adicione ao menos uma coluna.",
                        "Assistente de DDL", JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (runner == null) {
                return;
            }
            runner.run(statements, () -> {
                JOptionPane.showMessageDialog(dialog,
                        alterMode ? "Tabela \"" + alterTableName + "\" alterada com sucesso."
                                : "Tabela \"" + nameField.getText().trim() + "\" criada com sucesso.",
                        "Assistente de DDL", JOptionPane.INFORMATION_MESSAGE);
                if (onSuccess != null) {
                    onSuccess.run();
                }
                dialog.dispose();
            }, ex -> JOptionPane.showMessageDialog(dialog, "Falha ao executar:\n" + ex.getMessage(),
                    "Assistente de DDL", JOptionPane.ERROR_MESSAGE));
        }

        // ---------- Coleta/validacao do estado das abas ----------

        private static final class ValidationException extends RuntimeException {
            private static final long serialVersionUID = 1L;

            ValidationException(String message) {
                super(message);
            }
        }

        private List<String> buildStatements() {
            List<NewColumnSpec> newColumns = collectNewColumns();
            List<ForeignKeyInfo> fks = collectForeignKeys(false);
            List<IndexInfo> idxs = collectIndexes(false);
            if (alterMode) {
                return dialect.alterTableAddStatements(alterTableName, newColumns, fks, idxs);
            }
            String tableName = nameField.getText().trim();
            if (tableName.isEmpty()) {
                throw new ValidationException("Informe o nome da tabela.");
            }
            if (!IDENTIFIER.matcher(tableName).matches()) {
                throw new ValidationException(
                        "Nome de tabela invalido: \"" + tableName + "\". Use letras, numeros e _ (nao pode comecar com numero).");
            }
            if (tableNameTaken != null && tableNameTaken.test(tableName)) {
                throw new ValidationException("Ja existe uma tabela chamada \"" + tableName + "\".");
            }
            if (newColumns.isEmpty()) {
                throw new ValidationException("Adicione pelo menos uma coluna.");
            }
            NewTableSpec spec = new NewTableSpec(tableName, newColumns, commentField.getText().trim(), fks, idxs);
            return List.of(dialect.createTableStatement(spec));
        }

        /** Colunas NOVAS digitadas na grade editavel (ignora linhas em branco), validando nomes. */
        private List<NewColumnSpec> collectNewColumns() {
            List<NewColumnSpec> columns = new ArrayList<>();
            Set<String> seen = new LinkedHashSet<>();
            if (alterMode) {
                for (ColumnDetail cd : existingDetails.columns()) {
                    seen.add(cd.name().toLowerCase(Locale.ROOT));
                }
            }
            for (int r = 0; r < columnsModel.getRowCount(); r++) {
                String name = str(columnsModel.getValueAt(r, 0)).trim();
                String type = str(columnsModel.getValueAt(r, 1)).trim();
                String length = str(columnsModel.getValueAt(r, 2)).trim();
                boolean nullable = bool(columnsModel.getValueAt(r, 3));
                boolean pk = !alterMode && bool(columnsModel.getValueAt(r, 4));
                boolean ai = !alterMode && bool(columnsModel.getValueAt(r, 5));
                String def = str(columnsModel.getValueAt(r, 6)).trim();
                String comment = str(columnsModel.getValueAt(r, 7)).trim();
                if (name.isEmpty() && type.isEmpty()) {
                    continue;
                }
                if (name.isEmpty()) {
                    throw new ValidationException("Linha " + (r + 1) + " da grade de colunas: informe o nome.");
                }
                if (!IDENTIFIER.matcher(name).matches()) {
                    throw new ValidationException("Nome de coluna invalido: \"" + name + "\".");
                }
                if (!seen.add(name.toLowerCase(Locale.ROOT))) {
                    throw new ValidationException("Coluna repetida (ou ja existente na tabela): \"" + name + "\".");
                }
                columns.add(new NewColumnSpec(name, type.isEmpty() ? "VARCHAR" : type, length, nullable, pk, ai, def,
                        comment));
            }
            return columns;
        }

        /**
         * Colunas EXISTENTES (modo alterar) + novas — usado so pelas
         * sugestoes/atalhos (advisor, "sugerir FK"), nunca para montar o DDL
         * de verdade (isso e {@link #collectNewColumns()}).
         */
        private List<NewColumnSpec> collectAllColumnsForAdvisor() {
            List<NewColumnSpec> all = new ArrayList<>();
            if (alterMode) {
                for (ColumnDetail cd : existingDetails.columns()) {
                    String[] parts = parseType(cd.type());
                    all.add(new NewColumnSpec(cd.name(), parts[0], parts[1], cd.nullable(),
                            "PRI".equalsIgnoreCase(cd.key()), cd.extra() != null
                                    && cd.extra().toLowerCase(Locale.ROOT).contains("auto_increment"),
                            cd.defaultValue(), cd.comment()));
                }
            }
            for (int r = 0; r < columnsModel.getRowCount(); r++) {
                String name = str(columnsModel.getValueAt(r, 0)).trim();
                String type = str(columnsModel.getValueAt(r, 1)).trim();
                if (name.isEmpty()) {
                    continue;
                }
                all.add(new NewColumnSpec(name, type.isEmpty() ? "VARCHAR" : type,
                        str(columnsModel.getValueAt(r, 2)).trim(), bool(columnsModel.getValueAt(r, 3)),
                        !alterMode && bool(columnsModel.getValueAt(r, 4)), !alterMode && bool(columnsModel.getValueAt(r, 5)),
                        str(columnsModel.getValueAt(r, 6)).trim(), str(columnsModel.getValueAt(r, 7)).trim()));
            }
            return all;
        }

        /** "varchar(255)" -&gt; {"VARCHAR","255"}; "int" -&gt; {"INT",""}; "tinyint(1) unsigned" -&gt; {"TINYINT","1"}. */
        private static String[] parseType(String columnType) {
            if (columnType == null) {
                return new String[] { "VARCHAR", "" };
            }
            Matcher m = TYPE_PARTS.matcher(columnType);
            if (m.find()) {
                String base = m.group(1).toUpperCase(Locale.ROOT);
                String len = m.group(2) == null ? "" : m.group(2);
                return new String[] { base, len };
            }
            return new String[] { columnType.toUpperCase(Locale.ROOT), "" };
        }

        private List<ForeignKeyInfo> collectForeignKeys(boolean lenient) {
            List<ForeignKeyInfo> out = new ArrayList<>();
            for (int r = 0; r < fkModel.getRowCount(); r++) {
                List<String> cols = splitCsv(str(fkModel.getValueAt(r, 0)));
                String refTable = str(fkModel.getValueAt(r, 1)).trim();
                List<String> refCols = splitCsv(str(fkModel.getValueAt(r, 2)));
                String onUpdate = str(fkModel.getValueAt(r, 3)).trim();
                String onDelete = str(fkModel.getValueAt(r, 4)).trim();
                if (cols.isEmpty() && refTable.isEmpty()) {
                    continue; // linha em branco
                }
                if (!lenient) {
                    if (cols.isEmpty() || refTable.isEmpty() || refCols.isEmpty()) {
                        throw new ValidationException(
                                "Linha " + (r + 1) + " da aba \"Chaves estrangeiras\": preencha coluna(s) local(is), "
                                        + "tabela e coluna(s) referenciada(s).");
                    }
                }
                out.add(new ForeignKeyInfo(null, cols, refTable, refCols,
                        onUpdate.isEmpty() ? "RESTRICT" : onUpdate, onDelete.isEmpty() ? "RESTRICT" : onDelete));
            }
            return out;
        }

        private List<IndexInfo> collectIndexes(boolean lenient) {
            List<IndexInfo> out = new ArrayList<>();
            for (int r = 0; r < indexModel.getRowCount(); r++) {
                String name = str(indexModel.getValueAt(r, 0)).trim();
                List<String> cols = splitCsv(str(indexModel.getValueAt(r, 1)));
                boolean unique = bool(indexModel.getValueAt(r, 2));
                if (cols.isEmpty()) {
                    continue;
                }
                out.add(new IndexInfo(name.isEmpty() ? null : name, unique, "BTREE", cols));
            }
            return out;
        }

        private static List<String> splitCsv(String s) {
            List<String> out = new ArrayList<>();
            if (s == null) {
                return out;
            }
            for (String part : s.split(",")) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    out.add(trimmed);
                }
            }
            return out;
        }

        private static void stopEditing(JTable table) {
            if (table.isEditing()) {
                table.getCellEditor().stopCellEditing();
            }
        }

        private static String str(Object v) {
            return v == null ? "" : v.toString();
        }

        private static boolean bool(Object v) {
            return v instanceof Boolean b && b;
        }
    }
}
