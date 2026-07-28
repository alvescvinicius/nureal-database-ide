package com.nureal.ide.ui;
import com.nureal.ide.compartilhado.designsystem.Buttons;
import com.nureal.ide.compartilhado.designsystem.Typography;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.AbstractAction;
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
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;

import com.nureal.ide.compartilhado.validacao.SqlIdentifiers;
import com.nureal.ide.modulos.assistenteddl.aplicacao.ConstruirDdlDeTabelaHandler;
import com.nureal.ide.modulos.assistenteddl.aplicacao.ConstruirDdlDeTabelaInput;
import com.nureal.ide.modulos.assistenteddl.aplicacao.ConstruirDdlDeTabelaOutput;
import com.nureal.ide.modulos.assistenteddl.dominio.NormalizationAdvisor;
import com.nureal.ide.modulos.dialeto.dominio.contratos.DatabaseDialect;
import com.nureal.ide.core.format.SqlFormatter;
import com.nureal.ide.modulos.metadados.dominio.entidades.ColumnDetail;
import com.nureal.ide.modulos.metadados.dominio.entidades.ForeignKeyInfo;
import com.nureal.ide.modulos.metadados.dominio.entidades.IndexInfo;
import com.nureal.ide.modulos.metadados.dominio.entidades.NewColumnSpec;
import com.nureal.ide.modulos.metadados.dominio.entidades.SchemaInfo;
import com.nureal.ide.modulos.metadados.dominio.entidades.TableDetails;
import com.nureal.ide.modulos.metadados.dominio.entidades.TableInfo;

/**
 * Assistente de DDL guiado: cria uma tabela nova OU adiciona colunas/chaves
 * estrangeiras/indices a uma tabela existente, com abas separadas para cada
 * decisao (colunas, FKs, indices), uma aba de SUGESTOES DE NORMALIZACAO (ver
 * {@link NormalizationAdvisor}) que reage ao que foi preenchido, e uma aba de
 * pre-visualizacao do DDL final (ja formatado — ver {@link SqlFormatter})
 * antes de executar.
 * <p>
 * No modo "alterar tabela": colunas/FKs/indices NOVOS (ver
 * {@code DatabaseDialect#alterTableAddStatements}) ficam nas grades de baixo
 * de cada aba, editaveis livremente. As colunas/FKs/indices JA EXISTENTES
 * aparecem em uma grade "atual" no topo de cada aba — colunas existentes
 * podem ter tipo/tamanho/nulo/default/comentario editados (gera MODIFY
 * COLUMN) e qualquer coluna/FK/indice existente pode ser marcado para
 * remover (gera DROP COLUMN/DROP FOREIGN KEY/DROP INDEX) — ver
 * {@code DatabaseDialect#alterTableModifyStatements}. Renomear coluna e
 * remover a chave primaria continuam fora do escopo guiado (risco/ambiguidade
 * altos demais para um assistente). Qualquer remocao pedida exige uma
 * confirmacao extra antes de executar (ver {@link Session#onExecute}).
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
        private DefaultTableModel existingColumnsModel; // alter mode: editaveis (MODIFY) + "Remover" (DROP)
        private JTable newColumnsTable; // a mesma tabela de columnsModel — guardada para focar a linha apos "Duplicar"
        private JTable existingColumnsTable; // alter mode: a mesma tabela de existingColumnsModel
        /** Ctrl+C/Ctrl+V/"Duplicar" de uma linha de coluna — ver {@link DdlColumnRowClipboard}. Montado em {@link #buildColumnsTab}. */
        private DdlColumnRowClipboard columnClipboard;
        private DefaultTableModel fkModel;
        private DefaultTableModel existingFkModel; // alter mode: so "Remover" (DROP FOREIGN KEY)
        private DefaultTableModel indexModel;
        private DefaultTableModel existingIndexModel; // alter mode: so "Remover" (DROP INDEX, exceto PRIMARY)
        private JTextArea suggestionsArea;
        private org.fife.ui.rsyntaxtextarea.RSyntaxTextArea previewArea;
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
                    ? "Modo ALTERAR: adicione colunas/chaves/indices novos nas grades de baixo, ou edite/marque "
                            + "\"Remover\" nas grades \"atuais\" no topo de cada aba (isto gera MODIFY/DROP — pede confirmacao extra)."
                    : "Modo CRIAR: monte a tabela do zero, com sugestoes de normalizacao antes de executar.");
            // Nivel TERCIARIO (texto auxiliar) — ver Typography.
            Typography.tertiary(banner);
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
                panel.add(buildExistingColumnsPanel(), BorderLayout.NORTH);
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
            newColumnsTable = MetadataTableStyle.createStyledTable(columnsModel);
            JTable table = newColumnsTable; // nome curto local, mesmo estilo do resto do metodo
            table.getColumnModel().getColumn(1).setCellEditor(new DefaultCellEditor(new JComboBox<>(TYPES)));
            table.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
            // So agora as duas grades/modelos existem (a de "Colunas atuais" e
            // opcional, so no modo alterar) — o clipboard precisa dos dois pra
            // reconhecer de qual grade uma linha copiada veio (ver
            // DdlColumnRowClipboard#snapshot).
            columnClipboard = new DdlColumnRowClipboard(columnsModel, existingColumnsModel, newColumnsTable, alterMode);
            if (existingColumnsTable != null) {
                columnClipboard.bindCopy(existingColumnsTable);
            }
            columnClipboard.bindCopy(table);
            columnClipboard.bindPaste(table);

            JPanel buttons = buildColumnButtonsRow(table);

            JPanel newWrap = new JPanel(new BorderLayout(0, 4));
            newWrap.add(new JLabel(alterMode ? "Colunas novas a adicionar:" : "Colunas:"), BorderLayout.NORTH);
            JScrollPane scroll = new JScrollPane(table);
            scroll.setPreferredSize(new Dimension(880, alterMode ? 200 : 320));
            newWrap.add(scroll, BorderLayout.CENTER);
            newWrap.add(buttons, BorderLayout.SOUTH);
            panel.add(newWrap, BorderLayout.CENTER);
            return panel;
        }

        /**
         * Painel "Colunas atuais" (so no modo alterar): "Coluna"/"Chave"/"Extra"
         * ficam so-leitura (nome nao muda — ver banner; chave/extra sao
         * preservados por baixo dos panos ao montar o MODIFY COLUMN, ver
         * collectModifiedColumns()); Tipo/Tamanho/Nulo/Default/Comentario sao
         * editaveis (viram MODIFY COLUMN se algum valor mudar); "Remover"
         * marca DROP COLUMN.
         */
        private JComponent buildExistingColumnsPanel() {
            String[] existingHeaders = { "Coluna", "Tipo", "Tamanho", "Nulo", "Chave", "Extra", "Default",
                    "Comentario", "Remover" };
            existingColumnsModel = new DefaultTableModel(existingHeaders, 0) {
                private static final long serialVersionUID = 1L;

                @Override
                public Class<?> getColumnClass(int columnIndex) {
                    return (columnIndex == 3 || columnIndex == 8) ? Boolean.class : String.class;
                }

                @Override
                public boolean isCellEditable(int row, int column) {
                    return column != 0 && column != 4 && column != 5;
                }
            };
            for (ColumnDetail cd : existingDetails.columns()) {
                String[] parts = parseType(cd.type());
                existingColumnsModel.addRow(new Object[] { cd.name(), parts[0], parts[1], cd.nullable(),
                        cd.key(), cd.extra(), nullToEmpty(cd.defaultValue()), nullToEmpty(cd.comment()),
                        Boolean.FALSE });
            }
            existingColumnsTable = MetadataTableStyle.createStyledTable(existingColumnsModel);
            existingColumnsTable.getColumnModel().getColumn(1)
                    .setCellEditor(new DefaultCellEditor(new JComboBox<>(TYPES)));
            existingColumnsTable.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
            JScrollPane existingScroll = new JScrollPane(existingColumnsTable);
            existingScroll.setPreferredSize(new Dimension(880, 140));
            JPanel existingWrap = new JPanel(new BorderLayout(0, 4));
            existingWrap.add(new JLabel("Colunas atuais (edite para MODIFY, marque \"Remover\" para DROP):"),
                    BorderLayout.NORTH);
            existingWrap.add(existingScroll, BorderLayout.CENTER);
            return existingWrap;
        }

        /** Linha de botoes "+ Coluna"/"- Coluna"/"Duplicar" abaixo da grade de colunas novas. */
        private JPanel buildColumnButtonsRow(JTable table) {
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
            // "Duplicar": mesmo resultado do Ctrl+C + Ctrl+V (ver
            // DdlColumnRowClipboard#bindCopy/#bindPaste), so num unico clique —
            // copia a linha selecionada em QUALQUER uma das duas grades (esta,
            // ou "Colunas atuais" no modo alterar) e cola como uma coluna
            // nova, com tudo igual exceto o Nome (em branco, unico campo
            // obrigatorio que precisa ser diferente). Descoberta mais facil
            // que os atalhos de teclado pra quem nao usa.
            JButton duplicateRow = new JButton("Duplicar");
            duplicateRow.setToolTipText(
                    "Copia a linha selecionada (aqui ou em \"Colunas atuais\") como uma coluna nova — "
                            + "so o Nome fica em branco, pronto para editar.");
            duplicateRow.addActionListener(a -> {
                stopEditing(table);
                if (table.getSelectedRow() >= 0) {
                    columnClipboard.duplicate(table);
                } else if (existingColumnsTable != null && existingColumnsTable.getSelectedRow() >= 0) {
                    columnClipboard.duplicate(existingColumnsTable);
                } else {
                    JOptionPane.showMessageDialog(dialog,
                            "Selecione uma linha (nesta tabela ou em \"Colunas atuais\") para duplicar.",
                            "Duplicar coluna", JOptionPane.INFORMATION_MESSAGE);
                }
            });
            Buttons.styleSecondary(addRow);
            Buttons.styleSecondary(removeRow);
            Buttons.styleSecondary(duplicateRow);
            JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
            buttons.add(addRow);
            buttons.add(removeRow);
            buttons.add(duplicateRow);
            return buttons;
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

            JComponent north = null;
            if (alterMode) {
                String[] existingHeaders = { "Nome (constraint)", "Coluna(s) local(is)", "Tabela referenciada",
                        "Coluna(s) referenciada(s)", "ON UPDATE", "ON DELETE", "Remover" };
                existingFkModel = new DefaultTableModel(existingHeaders, 0) {
                    private static final long serialVersionUID = 1L;

                    @Override
                    public Class<?> getColumnClass(int columnIndex) {
                        return columnIndex == 6 ? Boolean.class : String.class;
                    }

                    @Override
                    public boolean isCellEditable(int row, int column) {
                        return column == 6;
                    }
                };
                for (ForeignKeyInfo fk : existingDetails.foreignKeys()) {
                    existingFkModel.addRow(new Object[] { fk.name(), String.join(", ", fk.columns()),
                            fk.referencedTable(), String.join(", ", fk.referencedColumns()),
                            nullToEmpty(fk.onUpdate()), nullToEmpty(fk.onDelete()), Boolean.FALSE });
                }
                JTable existingTable = MetadataTableStyle.createStyledTable(existingFkModel);
                JScrollPane existingScroll = new JScrollPane(existingTable);
                existingScroll.setPreferredSize(new Dimension(880, 120));
                JPanel existingWrap = new JPanel(new BorderLayout(0, 4));
                existingWrap.add(new JLabel("Chaves estrangeiras atuais (marque \"Remover\" para DROP FOREIGN KEY):"),
                        BorderLayout.NORTH);
                existingWrap.add(existingScroll, BorderLayout.CENTER);
                north = existingWrap;
            }

            String[] headers = { "Coluna(s) local(is)", "Tabela referenciada", "Coluna(s) referenciada(s)",
                    "ON UPDATE", "ON DELETE" };
            fkModel = new DefaultTableModel(headers, 0);
            JTable table = MetadataTableStyle.createStyledTable(fkModel);
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
            Buttons.styleSecondary(addRow);
            Buttons.styleSecondary(removeRow);
            Buttons.styleSecondary(suggest);
            JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
            buttons.add(addRow);
            buttons.add(removeRow);
            buttons.add(suggest);

            JScrollPane scroll = new JScrollPane(table);
            JPanel newWrap = new JPanel(new BorderLayout(0, 4));
            newWrap.add(new JLabel(alterMode ? "Chaves estrangeiras novas a adicionar:" : "Chaves estrangeiras:"),
                    BorderLayout.NORTH);
            newWrap.add(scroll, BorderLayout.CENTER);
            newWrap.add(buttons, BorderLayout.SOUTH);
            if (north != null) {
                panel.add(north, BorderLayout.NORTH);
            }
            panel.add(newWrap, BorderLayout.CENTER);
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
            // Modo alterar: a tabela pode JA ter uma FK nessa coluna (ver
            // existingFkModel) — sem isto, "Sugerir a partir de colunas *_id"
            // oferecia uma constraint FK duplicada pra uma coluna que ja tinha
            // FK antes mesmo do usuario adicionar qualquer FK nova nesta sessao.
            if (existingFkModel != null) {
                for (int r = 0; r < existingFkModel.getRowCount(); r++) {
                    for (String col : splitCsv(str(existingFkModel.getValueAt(r, 1)))) {
                        alreadyCovered.add(col.toLowerCase(Locale.ROOT));
                    }
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

            JComponent north = null;
            if (alterMode) {
                // Indice "PRIMARY" (chave primaria) fica de fora desta grade de
                // proposito: removê-lo exige DROP PRIMARY KEY (sintaxe/risco
                // diferentes de um DROP INDEX comum) — mesmo criterio ja usado
                // para nao oferecer PK/AI em colunas novas no modo alterar.
                String[] existingHeaders = { "Nome", "Coluna(s)", "Unico", "Remover" };
                existingIndexModel = new DefaultTableModel(existingHeaders, 0) {
                    private static final long serialVersionUID = 1L;

                    @Override
                    public Class<?> getColumnClass(int columnIndex) {
                        return columnIndex == 3 ? Boolean.class : String.class;
                    }

                    @Override
                    public boolean isCellEditable(int row, int column) {
                        return column == 3;
                    }
                };
                for (IndexInfo idx : existingDetails.indexes()) {
                    if ("PRIMARY".equalsIgnoreCase(idx.name())) {
                        continue;
                    }
                    existingIndexModel.addRow(new Object[] { idx.name(), String.join(", ", idx.columns()),
                            idx.unique(), Boolean.FALSE });
                }
                JTable existingTable = MetadataTableStyle.createStyledTable(existingIndexModel);
                JScrollPane existingScroll = new JScrollPane(existingTable);
                existingScroll.setPreferredSize(new Dimension(880, 120));
                JPanel existingWrap = new JPanel(new BorderLayout(0, 4));
                existingWrap.add(new JLabel("Indices atuais (marque \"Remover\" para DROP INDEX; chave primaria nao aparece aqui):"),
                        BorderLayout.NORTH);
                existingWrap.add(existingScroll, BorderLayout.CENTER);
                north = existingWrap;
            }

            String[] headers = { "Nome (opcional)", "Coluna(s)", "Unico" };
            indexModel = new DefaultTableModel(headers, 0) {
                private static final long serialVersionUID = 1L;

                @Override
                public Class<?> getColumnClass(int columnIndex) {
                    return columnIndex == 2 ? Boolean.class : String.class;
                }
            };
            JTable table = MetadataTableStyle.createStyledTable(indexModel);
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
            Buttons.styleSecondary(addRow);
            Buttons.styleSecondary(removeRow);
            Buttons.styleSecondary(suggest);
            JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
            buttons.add(addRow);
            buttons.add(removeRow);
            buttons.add(suggest);

            JScrollPane scroll = new JScrollPane(table);
            JPanel newWrap = new JPanel(new BorderLayout(0, 4));
            newWrap.add(new JLabel(alterMode ? "Indices novos a adicionar:" : "Indices:"), BorderLayout.NORTH);
            newWrap.add(scroll, BorderLayout.CENTER);
            newWrap.add(buttons, BorderLayout.SOUTH);
            if (north != null) {
                panel.add(north, BorderLayout.NORTH);
            }
            panel.add(newWrap, BorderLayout.CENTER);
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
            Buttons.styleSecondary(refresh);
            panel.add(new JScrollPane(suggestionsArea), BorderLayout.CENTER);
            JPanel south = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
            south.add(refresh);
            panel.add(south, BorderLayout.SOUTH);
            return panel;
        }

        private void refreshSuggestions() {
            List<NewColumnSpec> allColumns = collectAllColumnsForAdvisor();
            List<ForeignKeyInfo> fks = collectForeignKeys(true, new ArrayList<>());
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
            // RSyntaxTextArea com o MESMO destaque de sintaxe/paleta semantica
            // do editor de consultas (ver SqlEditorPane#styleAsReadOnlySql) —
            // antes era um JTextArea puro (so a fonte monoespacada, nenhuma
            // cor); pedido do "Sistema Semantico de Cores por Tipo de Dado".
            previewArea = new org.fife.ui.rsyntaxtextarea.RSyntaxTextArea();
            SqlEditorPane.styleAsReadOnlySql(previewArea);
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
            Buttons.styleSecondary(refresh);
            Buttons.styleSecondary(copy);
            Buttons.styleSecondary(toEditor);
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
            ConstruirDdlDeTabelaOutput output = buildStatements();
            if (!output.sucesso()) {
                return "-- " + output.mensagemErro();
            }
            List<String> statements = output.statements();
            if (statements.isEmpty()) {
                return alterMode
                        ? "-- Nada a fazer ainda: adicione uma coluna/chave/indice novo, edite uma coluna "
                                + "existente ou marque \"Remover\" em alguma grade \"atual\"."
                        : "-- Adicione ao menos uma coluna na aba \"Colunas\".";
            }
            SqlFormatter formatter = new SqlFormatter(SqlFormatter.KeywordCase.UPPER, SqlFormatter.Style.STANDARD,
                    false);
            StringBuilder sb = new StringBuilder();
            for (String s : statements) {
                sb.append(formatter.format(s)).append(";\n\n");
            }
            return sb.toString().trim();
        }

        // ---------- Rodape ----------

        private JComponent buildFooter() {
            JButton execute = new JButton(alterMode ? "Executar ALTER TABLE" : "Executar CREATE TABLE");
            execute.addActionListener(a -> onExecute());
            return Buttons.dialogFooter(dialog, execute);
        }

        private void onExecute() {
            ConstruirDdlDeTabelaOutput output = buildStatements();
            if (!output.sucesso()) {
                JOptionPane.showMessageDialog(dialog, output.mensagemErro(), "Assistente de DDL",
                        JOptionPane.WARNING_MESSAGE);
                tabs.setSelectedIndex(0);
                return;
            }
            List<String> statements = output.statements();
            if (statements.isEmpty()) {
                JOptionPane.showMessageDialog(dialog,
                        alterMode ? "Adicione, modifique ou marque para remover ao menos uma coluna/chave/indice."
                                : "Adicione ao menos uma coluna.",
                        "Assistente de DDL", JOptionPane.WARNING_MESSAGE);
                return;
            }
            // Qualquer remocao (coluna/FK/indice) OU modificacao de coluna
            // existente (MODIFY COLUMN pode truncar/rejeitar dados, ver
            // hasDestructiveChanges) pede confirmacao extra, separada da
            // execucao em si (mesmo criterio ja usado em outras acoes
            // destrutivas do app, ex.: Backup/Restore).
            if (alterMode && hasDestructiveChanges()) {
                boolean onlyModified = collectDroppedColumns().isEmpty() && collectDroppedForeignKeys().isEmpty()
                        && collectDroppedIndexes().isEmpty();
                String message = onlyModified
                        ? "Esta alteracao vai MODIFICAR coluna(s) existentes da tabela \"" + alterTableName
                                + "\" (tipo, tamanho, nulo ou default) — dependendo da mudanca, dados existentes "
                                + "podem ser truncados ou rejeitados, e a operacao nao pode ser desfeita."
                        : "Esta alteracao vai REMOVER e/ou MODIFICAR permanentemente coluna(s), chave(s) "
                                + "estrangeira(s) e/ou indice(s) existentes da tabela \"" + alterTableName
                                + "\" — dados dessas colunas podem ser perdidos ou truncados, e a operacao nao "
                                + "pode ser desfeita.";
                int choice = JOptionPane.showConfirmDialog(dialog,
                        message + "\n\nConfira a aba \"DDL (pre-visualizacao)\" antes, se ainda nao conferiu. "
                                + "Continuar mesmo assim?",
                        "Confirmar alteracao", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                if (choice != JOptionPane.YES_OPTION) {
                    return;
                }
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

        /**
         * Coleta o formulario e delega a montagem do DDL a
         * {@link ConstruirDdlDeTabelaHandler} — erros de LINHA (grade de
         * colunas/FKs) sao detectados aqui mesmo (so quem tem acesso aos
         * componentes Swing sabe o numero da linha) e viram
         * {@code DADOS_INCOMPLETOS} direto, sem chegar a chamar o handler;
         * os demais (nome de tabela vazio/invalido/duplicado, sem colunas)
         * sao responsabilidade do handler.
         */
        private ConstruirDdlDeTabelaOutput buildStatements() {
            List<String> erros = new ArrayList<>();
            List<NewColumnSpec> newColumns = collectNewColumns(erros);
            if (!erros.isEmpty()) {
                return ConstruirDdlDeTabelaOutput.erro(
                        ConstruirDdlDeTabelaOutput.ErroDeValidacao.DADOS_INCOMPLETOS, erros.get(0));
            }
            List<ForeignKeyInfo> fks = collectForeignKeys(false, erros);
            if (!erros.isEmpty()) {
                return ConstruirDdlDeTabelaOutput.erro(
                        ConstruirDdlDeTabelaOutput.ErroDeValidacao.DADOS_INCOMPLETOS, erros.get(0));
            }
            List<IndexInfo> idxs = collectIndexes(false);

            String tableName = alterMode ? null : nameField.getText().trim();
            boolean tableNameJaExiste = !alterMode && tableNameTaken != null && tableNameTaken.test(tableName);
            ConstruirDdlDeTabelaInput input = new ConstruirDdlDeTabelaInput(
                    alterMode, alterTableName, tableName, tableNameJaExiste,
                    alterMode ? null : commentField.getText().trim(),
                    newColumns, fks, idxs,
                    collectModifiedColumns(), collectDroppedColumns(), collectDroppedForeignKeys(), collectDroppedIndexes());
            return new ConstruirDdlDeTabelaHandler(dialect).executar(input);
        }

        /**
         * Colunas NOVAS digitadas na grade editavel (ignora linhas em branco),
         * validando nomes. Em vez de lancar excecao no primeiro problema (como
         * antes, via {@code SqlBuilderValidationException}), acrescenta a
         * mensagem em {@code erros} e para de coletar (mesmo efeito pratico:
         * so a PRIMEIRA mensagem chega a ser mostrada ao usuario).
         */
        private List<NewColumnSpec> collectNewColumns(List<String> erros) {
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
                    erros.add("Linha " + (r + 1) + " da grade de colunas: informe o nome.");
                    return columns;
                }
                if (!SqlIdentifiers.isValid(name)) {
                    erros.add("Nome de coluna invalido: \"" + name + "\".");
                    return columns;
                }
                if (!seen.add(name.toLowerCase(Locale.ROOT))) {
                    erros.add("Coluna repetida (ou ja existente na tabela): \"" + name + "\".");
                    return columns;
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

        /**
         * Colunas EXISTENTES cujo tipo/tamanho/nulo/default/comentario mudou na
         * grade "atual" (aba Colunas) — viram MODIFY COLUMN. Chave primaria e
         * AUTO_INCREMENT nao sao editaveis nesta grade (ver isCellEditable de
         * {@link #existingColumnsModel}); o AUTO_INCREMENT original e sempre
         * preservado para nao ser derrubado sem querer ao gerar o MODIFY (o
         * MySQL exige restatar TODO o atributo da coluna, nao so o que mudou).
         * Colunas marcadas para remover (coluna 8, "Remover") ficam de fora —
         * viram DROP COLUMN em {@link #collectDroppedColumns()}, nao MODIFY.
         */
        private List<NewColumnSpec> collectModifiedColumns() {
            List<NewColumnSpec> mods = new ArrayList<>();
            if (!alterMode || existingColumnsModel == null) {
                return mods;
            }
            List<ColumnDetail> originals = existingDetails.columns();
            for (int r = 0; r < existingColumnsModel.getRowCount() && r < originals.size(); r++) {
                if (bool(existingColumnsModel.getValueAt(r, 8))) {
                    continue; // marcada para remover — nao faz sentido tambem "modificar"
                }
                ColumnDetail original = originals.get(r);
                String[] origParts = parseType(original.type());
                String type = str(existingColumnsModel.getValueAt(r, 1)).trim();
                String length = str(existingColumnsModel.getValueAt(r, 2)).trim();
                boolean nullable = bool(existingColumnsModel.getValueAt(r, 3));
                String def = str(existingColumnsModel.getValueAt(r, 6)).trim();
                String comment = str(existingColumnsModel.getValueAt(r, 7)).trim();
                boolean changed = !type.equalsIgnoreCase(origParts[0]) || !length.equals(origParts[1])
                        || nullable != original.nullable() || !def.equals(nullToEmpty(original.defaultValue()))
                        || !comment.equals(nullToEmpty(original.comment()));
                if (!changed) {
                    continue;
                }
                boolean autoIncrement = original.extra() != null
                        && original.extra().toLowerCase(Locale.ROOT).contains("auto_increment");
                mods.add(new NewColumnSpec(original.name(), type.isEmpty() ? origParts[0] : type, length, nullable,
                        false, autoIncrement, def, comment));
            }
            return mods;
        }

        /** Nomes das colunas EXISTENTES marcadas "Remover" na aba Colunas — viram DROP COLUMN. */
        private List<String> collectDroppedColumns() {
            List<String> dropped = new ArrayList<>();
            if (!alterMode || existingColumnsModel == null) {
                return dropped;
            }
            List<ColumnDetail> originals = existingDetails.columns();
            for (int r = 0; r < existingColumnsModel.getRowCount() && r < originals.size(); r++) {
                if (bool(existingColumnsModel.getValueAt(r, 8))) {
                    dropped.add(originals.get(r).name());
                }
            }
            return dropped;
        }

        /** Nomes das constraints de FK EXISTENTES marcadas "Remover" — viram DROP FOREIGN KEY. */
        private List<String> collectDroppedForeignKeys() {
            List<String> dropped = new ArrayList<>();
            if (!alterMode || existingFkModel == null) {
                return dropped;
            }
            for (int r = 0; r < existingFkModel.getRowCount(); r++) {
                if (bool(existingFkModel.getValueAt(r, 6))) {
                    dropped.add(str(existingFkModel.getValueAt(r, 0)));
                }
            }
            return dropped;
        }

        /** Nomes de indice EXISTENTES marcados "Remover" (exceto PRIMARY, ja fora da grade) — viram DROP INDEX. */
        private List<String> collectDroppedIndexes() {
            List<String> dropped = new ArrayList<>();
            if (!alterMode || existingIndexModel == null) {
                return dropped;
            }
            for (int r = 0; r < existingIndexModel.getRowCount(); r++) {
                if (bool(existingIndexModel.getValueAt(r, 3))) {
                    dropped.add(str(existingIndexModel.getValueAt(r, 0)));
                }
            }
            return dropped;
        }

        /**
         * Se ha QUALQUER remocao OU modificacao de coluna pendente (usado so
         * para decidir se pede confirmacao extra). MODIFY COLUMN entra aqui
         * tambem, nao so DROP: reduzir o tamanho de um VARCHAR ou trocar
         * "aceita nulo" para "nao aceita" pode truncar ou rejeitar dados
         * existentes silenciosamente — tao arriscado quanto uma remocao, so
         * que sem o aviso explicito de "REMOVER" no texto. Nao tenta
         * distinguir um MODIFY inofensivo (ex.: so aumentar o tamanho) de um
         * arriscado: o custo de uma confirmacao a mais para uma mudanca
         * inofensiva e bem menor que o de truncar dados sem avisar.
         */
        private boolean hasDestructiveChanges() {
            return !collectDroppedColumns().isEmpty() || !collectDroppedForeignKeys().isEmpty()
                    || !collectDroppedIndexes().isEmpty() || !collectModifiedColumns().isEmpty();
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

        private List<ForeignKeyInfo> collectForeignKeys(boolean lenient, List<String> erros) {
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
                        erros.add(
                                "Linha " + (r + 1) + " da aba \"Chaves estrangeiras\": preencha coluna(s) local(is), "
                                        + "tabela e coluna(s) referenciada(s).");
                        return out;
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

        private static String nullToEmpty(String s) {
            return s == null ? "" : s;
        }
    }
}
