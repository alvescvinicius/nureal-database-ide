package com.nureal.ide.ui;

import com.nureal.ide.compartilhado.designsystem.Buttons;
import com.nureal.ide.compartilhado.designsystem.Typography;
import com.nureal.ide.modulos.dialeto.dominio.contratos.DatabaseDialect;
import com.nureal.ide.modulos.metadados.dominio.entidades.ColumnDetail;
import com.nureal.ide.modulos.metadados.dominio.entidades.TableDetails;
import com.nureal.ide.modulos.populador.aplicacao.GerarLinhasFakeHandler;
import com.nureal.ide.modulos.populador.dominio.FakeDataGenerator;
import com.nureal.ide.modulos.populador.dominio.GeneratorKind;
import com.nureal.ide.modulos.populador.infraestrutura.PopuladorExecutor;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

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
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableModel;

/**
 * "Popular tabela...": gera N linhas fake respeitando tipo/tamanho de cada
 * coluna (ver {@link FakeDataGenerator}) e, para colunas FK, sorteia valores
 * JA EXISTENTES na tabela pai ({@code amostrasFk}, calculada ANTES de abrir
 * este dialogo — ver {@code ObjectDataTransfer#populateTable}) — nunca cria
 * linha na tabela pai. Mesmo padrao de {@code DdlAssistantDialog}: formulario
 * -&gt; grade editavel -&gt; executar, mas sem preview de SQL (o INSERT roda
 * direto, em lote, com barra de progresso — mais parecido com
 * {@code CsvImportDialog}).
 */
final class TablePopulatorDialog {

    private static final int AVISO_LINHAS = 50_000;
    /** Coluna "Gerador" da grade — indice fixo, ver {@link #buildColumnsTable}. */
    private static final int COL_GERADOR = 2;

    private TablePopulatorDialog() {
    }

    static void open(MainWindow owner, Conexao ws, String schemaName, String tableName, TableDetails details,
            Map<String, List<Object>> amostrasFk) {
        new Session(owner, ws, schemaName, tableName, details, amostrasFk).show();
    }

    private static final class Session {
        private final MainWindow owner;
        private final Conexao ws;
        private final String schemaName;
        private final String tableName;
        private final TableDetails details;
        private final Map<String, List<Object>> amostrasFk;

        private final Window ownerWindow;
        private JDialog dialog;
        private DefaultTableModel columnsModel;
        private JTextField quantityField;
        private JProgressBar progressBar;
        private JLabel progressLabel;
        private JButton executeButton;
        private JButton cancelRunButton;
        private final boolean[] cancelado = { false };

        Session(MainWindow owner, Conexao ws, String schemaName, String tableName, TableDetails details,
                Map<String, List<Object>> amostrasFk) {
            this.owner = owner;
            this.ws = ws;
            this.schemaName = schemaName;
            this.tableName = tableName;
            this.details = details;
            this.amostrasFk = amostrasFk;
            this.ownerWindow = (DialogUtil.owner(owner) instanceof Window w) ? w : null;
        }

        void show() {
            dialog = new JDialog(ownerWindow, "Popular tabela — " + tableName, Dialog.ModalityType.APPLICATION_MODAL);
            dialog.setLayout(new BorderLayout());
            dialog.add(buildHeader(), BorderLayout.NORTH);
            dialog.add(buildColumnsPanel(), BorderLayout.CENTER);
            dialog.add(buildFooter(), BorderLayout.SOUTH);
            dialog.getRootPane().registerKeyboardAction(e -> dialog.dispose(),
                    KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
            dialog.setSize(760, 560);
            dialog.setLocationRelativeTo(ownerWindow);
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
            form.add(new JLabel("Tabela:"), c);
            c.gridx = 1;
            form.add(new JLabel(schemaName + "." + tableName), c);

            c.gridx = 0;
            c.gridy = 1;
            form.add(new JLabel("Quantidade de linhas:"), c);
            c.gridx = 1;
            quantityField = new JTextField("100", 8);
            form.add(quantityField, c);

            JLabel banner = new JLabel("Colunas FK usam valores JA EXISTENTES na tabela pai (nunca cria linha nela)."
                    + " Corrija o gerador de uma coluna pelo combo, se a deteccao automatica errar.");
            Typography.tertiary(banner);
            banner.setBorder(BorderFactory.createEmptyBorder(0, 10, 6, 10));

            JPanel wrap = new JPanel(new BorderLayout());
            wrap.add(banner, BorderLayout.NORTH);
            wrap.add(form, BorderLayout.CENTER);
            return wrap;
        }

        /** {@code [Coluna | Tipo | Gerador]} — FK/auto-increment ficam com "Gerador" informativo, nao editavel. */
        private JComponent buildColumnsPanel() {
            JPanel panel = new JPanel(new BorderLayout(0, 4));
            panel.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));
            panel.add(new JLabel("Colunas:"), BorderLayout.NORTH);
            panel.add(buildColumnsTable(), BorderLayout.CENTER);
            return panel;
        }

        private JComponent buildColumnsTable() {
            String[] headers = { "Coluna", "Tipo", "Gerador" };
            columnsModel = new DefaultTableModel(headers, 0) {
                @Override
                public boolean isCellEditable(int row, int column) {
                    return column == COL_GERADOR && columnsModel.getValueAt(row, COL_GERADOR) instanceof GeneratorKind;
                }
            };
            for (ColumnDetail cd : details.columns()) {
                Object gerador;
                if (isAutoIncrement(cd)) {
                    gerador = "Auto increment (gerado pelo banco)";
                } else if (amostrasFk.containsKey(cd.name())) {
                    int n = amostrasFk.get(cd.name()).size();
                    gerador = n > 0 ? ("Chave estrangeira (amostra de " + n + " valores existentes)")
                            : "Chave estrangeira — TABELA PAI VAZIA, sem valores para amostrar";
                } else {
                    gerador = FakeDataGenerator.detectar(cd);
                }
                columnsModel.addRow(new Object[] { cd.name(), cd.type(), gerador });
            }
            JTable table = MetadataTableStyle.createStyledTable(columnsModel);
            table.getColumnModel().getColumn(COL_GERADOR)
                    .setCellEditor(new DefaultCellEditor(new JComboBox<>(GeneratorKind.values())));
            JScrollPane scroll = new JScrollPane(table);
            scroll.setPreferredSize(new Dimension(720, 320));
            return scroll;
        }

        private JComponent buildFooter() {
            JPanel south = new JPanel(new BorderLayout());
            south.add(buildProgressRow(), BorderLayout.NORTH);
            executeButton = new JButton("Popular tabela");
            executeButton.addActionListener(a -> onExecute());
            south.add(Buttons.dialogFooter(dialog, executeButton), BorderLayout.SOUTH);
            return south;
        }

        private JComponent buildProgressRow() {
            JPanel row = new JPanel(new BorderLayout(8, 0));
            row.setBorder(BorderFactory.createEmptyBorder(4, 10, 0, 10));
            progressBar = new JProgressBar(0, 100);
            progressBar.setStringPainted(true);
            progressBar.setVisible(false);
            progressLabel = new JLabel(" ");
            Typography.tertiary(progressLabel);
            cancelRunButton = new JButton("Cancelar");
            cancelRunButton.setVisible(false);
            cancelRunButton.addActionListener(a -> cancelado[0] = true);
            Buttons.styleSecondary(cancelRunButton);
            row.add(progressLabel, BorderLayout.NORTH);
            row.add(progressBar, BorderLayout.CENTER);
            row.add(cancelRunButton, BorderLayout.EAST);
            return row;
        }

        private void onExecute() {
            int quantidade;
            try {
                quantidade = Integer.parseInt(quantityField.getText().trim());
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(dialog, "Informe um numero valido de linhas.", "Popular tabela",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (quantidade <= 0) {
                JOptionPane.showMessageDialog(dialog, "A quantidade de linhas deve ser maior que zero.",
                        "Popular tabela", JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (quantidade > AVISO_LINHAS) {
                int choice = JOptionPane.showConfirmDialog(dialog,
                        quantidade + " linhas pode ser lento e usar bastante memoria/tempo de banco.\n\nContinuar?",
                        "Popular tabela", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                if (choice != JOptionPane.YES_OPTION) {
                    return;
                }
            }
            List<String> fkSemAmostraObrigatoria = colunasFkSemAmostraObrigatoria();
            if (!fkSemAmostraObrigatoria.isEmpty()) {
                JOptionPane.showMessageDialog(dialog,
                        "Nao e possivel popular: a(s) coluna(s) obrigatoria(s) " + fkSemAmostraObrigatoria
                                + " referenciam tabela(s) pai VAZIA(S) (sem nenhuma linha pra amostrar). "
                                + "Popule a tabela pai primeiro.",
                        "Popular tabela", JOptionPane.ERROR_MESSAGE);
                return;
            }

            Map<String, GeneratorKind> geradorPorColuna = collectGeradorPorColuna();
            List<Map<String, Object>> linhas = new GerarLinhasFakeHandler().gerar(details, geradorPorColuna,
                    amostrasFk, quantidade, new Random());
            runInsert(linhas);
        }

        /** Colunas FK, NOT NULL, cuja amostra veio vazia (tabela pai sem linhas) — bloqueiam a execucao. */
        private List<String> colunasFkSemAmostraObrigatoria() {
            List<String> bloqueadas = new ArrayList<>();
            for (ColumnDetail cd : details.columns()) {
                List<Object> amostra = amostrasFk.get(cd.name());
                if (amostra != null && amostra.isEmpty() && !cd.nullable()) {
                    bloqueadas.add(cd.name());
                }
            }
            return bloqueadas;
        }

        private Map<String, GeneratorKind> collectGeradorPorColuna() {
            Map<String, GeneratorKind> mapa = new java.util.LinkedHashMap<>();
            for (int r = 0; r < columnsModel.getRowCount(); r++) {
                Object valor = columnsModel.getValueAt(r, COL_GERADOR);
                if (valor instanceof GeneratorKind kind) {
                    mapa.put(String.valueOf(columnsModel.getValueAt(r, 0)), kind);
                }
            }
            return mapa;
        }

        private static boolean isAutoIncrement(ColumnDetail cd) {
            return cd.extra() != null && cd.extra().toLowerCase(Locale.ROOT).contains("auto_increment");
        }

        private void runInsert(List<Map<String, Object>> linhas) {
            cancelado[0] = false;
            executeButton.setEnabled(false);
            cancelRunButton.setVisible(true);
            progressBar.setVisible(true);
            progressBar.setMaximum(linhas.size());
            progressBar.setValue(0);
            progressLabel.setText(" Inserindo linhas...");

            DatabaseDialect dialect = owner.dialect();
            SwingWorker<Integer, Integer> worker = new SwingWorker<>() {
                @Override
                protected Integer doInBackground() throws Exception {
                    // Conexao DEDICADA (nao a "principal" compartilhada): o
                    // executor liga/desliga autoCommit e roda commit/rollback
                    // — mexer nisso na conexao principal afetaria qualquer
                    // outra leitura concorrente (arvore de objetos,
                    // autocomplete) que dependa dela continuar em autoCommit
                    // normal (mesmo motivo pelo qual a execucao de SQL nos
                    // terminais usa uma conexao propria por aba, ver
                    // MainWindow#terminalConnection).
                    Connection conn = ws.mgr.borrowConnection();
                    try {
                        return PopuladorExecutor.inserir(conn, dialect, tableName, linhas,
                                inseridas -> publish(inseridas), () -> cancelado[0]);
                    } finally {
                        conn.close();
                    }
                }

                @Override
                protected void process(List<Integer> chunks) {
                    if (!chunks.isEmpty()) {
                        int inseridas = chunks.get(chunks.size() - 1);
                        progressBar.setValue(inseridas);
                        progressLabel.setText(" " + inseridas + " de " + linhas.size() + " linha(s) inseridas...");
                    }
                }

                @Override
                protected void done() {
                    executeButton.setEnabled(true);
                    cancelRunButton.setVisible(false);
                    try {
                        int inseridas = get();
                        progressLabel.setText(" " + inseridas + " linha(s) inseridas.");
                        String msg = cancelado[0]
                                ? "Cancelado: " + inseridas + " linha(s) ja inseridas em \"" + tableName + "\"."
                                : inseridas + " linha(s) inseridas em \"" + tableName + "\" com sucesso.";
                        JOptionPane.showMessageDialog(dialog, msg, "Popular tabela",
                                JOptionPane.INFORMATION_MESSAGE);
                        if (!cancelado[0]) {
                            dialog.dispose();
                        }
                    } catch (Exception ex) {
                        Throwable cause = (ex.getCause() != null) ? ex.getCause() : ex;
                        progressBar.setVisible(false);
                        progressLabel.setText(" Falha ao popular a tabela.");
                        JOptionPane.showMessageDialog(dialog, "Falha ao popular a tabela:\n" + cause.getMessage(),
                                "Popular tabela", JOptionPane.ERROR_MESSAGE);
                    }
                }
            };
            worker.execute();
        }
    }
}
