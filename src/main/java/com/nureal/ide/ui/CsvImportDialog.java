package com.nureal.ide.ui;
import com.nureal.ide.compartilhado.designsystem.Buttons;
import com.nureal.ide.compartilhado.designsystem.Typography;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.FlowLayout;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

import javax.swing.BorderFactory;
import javax.swing.DefaultCellEditor;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.KeyStroke;
import javax.swing.table.DefaultTableModel;

import com.nureal.ide.modulos.metadados.dominio.entidades.ColumnInfo;

/**
 * Importa um arquivo CSV ja lido/parseado (ver {@code CsvUtil}, chamado por
 * {@code MainWindow} ANTES de abrir este dialogo) para uma tabela existente —
 * pedido do usuario (ver {@code GAP_ANALYSIS_DBA_DEV.md}, fase 3: "popular
 * uma tabela de teste ainda exige escrever INSERT na mao ou usar outra
 * ferramenta"). Mapeamento de coluna e feito manualmente numa grade (uma
 * linha por coluna do CSV, combo com as colunas da tabela de destino ou
 * "-- ignorar --"), com um palpite inicial por nome (comparacao sem
 * diferenciar maiusculas/acentuacao de caixa).
 */
final class CsvImportDialog {

    private CsvImportDialog() {
    }

    private static final String IGNORE = "-- ignorar --";

    /**
     * Executa a insercao em lote em background. {@code onProgress} e chamado
     * periodicamente (ja na EDT) com a quantidade de linhas processadas ATE
     * AGORA (nao por lote) — a UI so precisa atualizar uma barra de
     * progresso com esse numero.
     */
    @FunctionalInterface
    interface ImportRunner {
        void run(String schema, String table, List<String> targetColumns, List<String[]> rows,
                IntConsumer onProgress, Runnable onSuccess, Consumer<Exception> onError);
    }

    static void open(Component parent, String schema, String table, List<ColumnInfo> tableColumns,
            List<String> csvHeaders, List<String[]> csvRows, ImportRunner runner) {
        new Session(parent, schema, table, tableColumns, csvHeaders, csvRows, runner).show();
    }

    private static final class Session {
        private final Window owner;
        private final String schema;
        private final String table;
        private final List<ColumnInfo> tableColumns;
        private final List<String> csvHeaders;
        private final List<String[]> csvRows;
        private final ImportRunner runner;

        private JDialog dialog;
        private DefaultTableModel mappingModel;
        private JProgressBar progressBar;
        private JLabel progressLabel;
        private JButton importButton;

        Session(Component parent, String schema, String table, List<ColumnInfo> tableColumns,
                List<String> csvHeaders, List<String[]> csvRows, ImportRunner runner) {
            this.owner = (DialogUtil.owner(parent) instanceof Window w) ? w : null;
            this.schema = schema;
            this.table = table;
            this.tableColumns = tableColumns;
            this.csvHeaders = csvHeaders;
            this.csvRows = csvRows;
            this.runner = runner;
        }

        void show() {
            dialog = new JDialog(owner, "Importar CSV para \"" + table + "\"", Dialog.ModalityType.APPLICATION_MODAL);
            dialog.setLayout(new BorderLayout());

            JLabel info = new JLabel("  " + csvRows.size() + " linha(s) lida(s) do arquivo. Mapeie cada coluna do CSV para uma coluna da tabela (ou deixe \"" + IGNORE + "\").");
            Typography.tertiary(info);
            info.setBorder(BorderFactory.createEmptyBorder(8, 4, 4, 4));

            JTabbedPane tabs = new JTabbedPane();
            tabs.addTab("Mapeamento de colunas", buildMappingTab());
            tabs.addTab("Previa dos dados (ate 20 linhas)", buildPreviewTab());

            dialog.add(info, BorderLayout.NORTH);
            dialog.add(tabs, BorderLayout.CENTER);
            dialog.add(buildFooter(), BorderLayout.SOUTH);
            // Esc fecha — mesmo atalho ja usado nos dialogs "de formulario
            // guiado" (DDL/view/trigger/rotina/usuarios), faltava aqui
            // (achado numa auditoria pedida pelo usuario).
            dialog.getRootPane().registerKeyboardAction(e -> dialog.dispose(),
                    KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
            dialog.setSize(820, 560);
            dialog.setLocationRelativeTo(owner);
            dialog.setVisible(true);
        }

        private JComponent buildMappingTab() {
            String[] headers = { "Coluna do CSV", "Coluna de destino" };
            mappingModel = new DefaultTableModel(headers, 0) {
                private static final long serialVersionUID = 1L;

                @Override
                public boolean isCellEditable(int row, int column) {
                    return column == 1;
                }
            };
            List<String> targetOptions = new ArrayList<>();
            targetOptions.add(IGNORE);
            for (ColumnInfo c : tableColumns) {
                targetOptions.add(c.name());
            }
            for (String csvCol : csvHeaders) {
                String guess = guessTarget(csvCol, targetOptions);
                mappingModel.addRow(new Object[] { csvCol, guess });
            }
            JTable table = MetadataTableStyle.createStyledTable(mappingModel);
            table.getColumnModel().getColumn(1)
                    .setCellEditor(new DefaultCellEditor(new JComboBox<>(targetOptions.toArray(new String[0]))));
            table.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);

            JPanel panel = new JPanel(new BorderLayout());
            panel.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
            panel.add(new JScrollPane(table), BorderLayout.CENTER);
            return panel;
        }

        /** Primeira coluna de destino cujo nome bate (sem diferenciar maiusculas/underscore/espaco) com o cabecalho do CSV; senao "-- ignorar --". */
        private static String guessTarget(String csvHeader, List<String> targetOptions) {
            String normalized = normalize(csvHeader);
            for (String option : targetOptions) {
                if (!option.equals(IGNORE) && normalize(option).equals(normalized)) {
                    return option;
                }
            }
            return IGNORE;
        }

        private static String normalize(String s) {
            return s == null ? "" : s.trim().toLowerCase(Locale.ROOT).replace(" ", "").replace("_", "");
        }

        private JComponent buildPreviewTab() {
            String[] headers = csvHeaders.toArray(new String[0]);
            DefaultTableModel previewModel = new DefaultTableModel(headers, 0) {
                private static final long serialVersionUID = 1L;

                @Override
                public boolean isCellEditable(int row, int column) {
                    return false;
                }
            };
            int limit = Math.min(20, csvRows.size());
            for (int i = 0; i < limit; i++) {
                previewModel.addRow(csvRows.get(i));
            }
            JTable table = MetadataTableStyle.createStyledTable(previewModel);
            JPanel panel = new JPanel(new BorderLayout());
            panel.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
            panel.add(new JScrollPane(table), BorderLayout.CENTER);
            return panel;
        }

        private JComponent buildFooter() {
            progressBar = new JProgressBar(0, csvRows.size());
            progressBar.setStringPainted(true);
            progressBar.setVisible(false);
            progressLabel = new JLabel();
            Typography.tertiary(progressLabel);

            JButton close = new JButton("Fechar");
            Buttons.styleSecondary(close);
            close.addActionListener(a -> dialog.dispose());

            importButton = new JButton("Importar " + csvRows.size() + " linha(s)");
            Buttons.stylePrimary(importButton);
            importButton.addActionListener(a -> onImport());

            JPanel progressPanel = new JPanel(new BorderLayout(6, 0));
            progressPanel.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
            progressPanel.add(progressLabel, BorderLayout.WEST);
            progressPanel.add(progressBar, BorderLayout.CENTER);

            JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 8));
            buttons.add(close);
            buttons.add(importButton);

            JPanel south = new JPanel(new BorderLayout());
            south.add(progressPanel, BorderLayout.NORTH);
            south.add(buttons, BorderLayout.SOUTH);
            return south;
        }

        private void onImport() {
            List<String> targetColumns = new ArrayList<>();
            List<Integer> csvColumnIndexes = new ArrayList<>();
            for (int r = 0; r < mappingModel.getRowCount(); r++) {
                String target = String.valueOf(mappingModel.getValueAt(r, 1));
                if (!IGNORE.equals(target)) {
                    targetColumns.add(target);
                    csvColumnIndexes.add(r);
                }
            }
            if (targetColumns.isEmpty()) {
                JOptionPane.showMessageDialog(dialog, "Mapeie ao menos uma coluna.", "Importar CSV",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }
            List<String[]> rowsToInsert = new ArrayList<>(csvRows.size());
            for (String[] row : csvRows) {
                String[] mapped = new String[csvColumnIndexes.size()];
                for (int i = 0; i < csvColumnIndexes.size(); i++) {
                    int csvIdx = csvColumnIndexes.get(i);
                    mapped[i] = csvIdx < row.length ? row[csvIdx] : null;
                }
                rowsToInsert.add(mapped);
            }
            importButton.setEnabled(false);
            progressBar.setVisible(true);
            progressBar.setValue(0);
            progressLabel.setText("Importando...");
            runner.run(schema, table, targetColumns, rowsToInsert,
                    processed -> {
                        progressBar.setValue(processed);
                        progressLabel.setText(processed + " / " + rowsToInsert.size());
                    },
                    () -> {
                        progressLabel.setText("Concluido.");
                        JOptionPane.showMessageDialog(dialog,
                                rowsToInsert.size() + " linha(s) importada(s) para \"" + table + "\".",
                                "Importar CSV", JOptionPane.INFORMATION_MESSAGE);
                        dialog.dispose();
                    },
                    ex -> {
                        importButton.setEnabled(true);
                        progressLabel.setText("Falhou.");
                        JOptionPane.showMessageDialog(dialog, "Falha ao importar:\n" + ex.getMessage(),
                                "Importar CSV", JOptionPane.ERROR_MESSAGE);
                    });
        }
    }
}
