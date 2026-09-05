package com.nureal.ide.ui;

import com.nureal.ide.compartilhado.designsystem.Buttons;
import com.nureal.ide.compartilhado.designsystem.Typography;
import com.nureal.ide.modulos.conexoes.dominio.contratos.ConexaoAtivaPort;
import com.nureal.ide.modulos.dialeto.dominio.contratos.DatabaseDialect;
import com.nureal.ide.modulos.exportacaorelacional.dominio.FecharDependenciasHandler;
import com.nureal.ide.modulos.exportacaorelacional.dominio.OrdenarTabelasHandler;
import com.nureal.ide.modulos.exportacaorelacional.dominio.RowFetcher;
import com.nureal.ide.modulos.exportacaorelacional.infraestrutura.InsertScriptBuilder;
import com.nureal.ide.modulos.exportacaorelacional.infraestrutura.JdbcRowFetcher;
import com.nureal.ide.modulos.metadados.dominio.entidades.SchemaForeignKey;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.FlowLayout;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.datatransfer.StringSelection;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.KeyStroke;
import javax.swing.SwingWorker;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 * "Exportar com dependencias (INSERT)...": a partir das linhas SELECIONADAS
 * na grade de um {@code SELECT}, gera um script {@code .sql} com essas
 * linhas MAIS as linhas PAI (sempre — necessarias pra as FKs nao falharem
 * em outro banco) e, opcionalmente, as linhas FILHAS (que referenciam as
 * selecionadas) — em ordem de dependencia. NUNCA executa nada contra um
 * banco: o pedido e gerar o script pra rodar em OUTRO banco depois (ver
 * .claude/plans, "Exportador relacional").
 */
final class RelationalExportDialog {

    private RelationalExportDialog() {
    }

    static void open(Component ownerComponent, ConexaoAtivaPort connectionManager, String schema,
            TableMetadataCache metadataCache, JTable table) {
        String tabela = GridClipboard.detectSourceTable(table);
        if (tabela == null) {
            JOptionPane.showMessageDialog(ownerComponent,
                    "Nao foi possivel identificar uma UNICA tabela de origem para este resultado "
                            + "(JOIN, expressao ou funcao) — a exportacao com dependencias so funciona para um "
                            + "SELECT de uma tabela so.",
                    "Exportar com dependencias", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (table.getSelectedRowCount() == 0) {
            JOptionPane.showMessageDialog(ownerComponent, "Selecione ao menos uma linha antes de exportar.",
                    "Exportar com dependencias", JOptionPane.WARNING_MESSAGE);
            return;
        }
        ResultTableModel model = (ResultTableModel) table.getModel();
        List<Map<String, Object>> linhasSemente = new ArrayList<>();
        for (int viewRow : table.getSelectedRows()) {
            int modelRow = table.convertRowIndexToModel(viewRow);
            Map<String, Object> linha = new LinkedHashMap<>();
            for (int c = 0; c < model.getColumnCount(); c++) {
                String real = model.realColumnName(c);
                if (real != null) {
                    linha.put(real, model.getValueAt(modelRow, c));
                }
            }
            linhasSemente.add(linha);
        }
        new Session(ownerComponent, connectionManager, schema, metadataCache, tabela, linhasSemente).show();
    }

    private static final class Session {
        private final Window owner;
        private final ConexaoAtivaPort connectionManager;
        private final String schema;
        private final TableMetadataCache metadataCache;
        private final String tabela;
        private final List<Map<String, Object>> linhasSemente;

        private JDialog dialog;
        private JCheckBox incluirFilhosCheck;
        private JButton gerarButton;
        private JButton salvarButton;
        private JButton copiarButton;
        private JPanel centerPanel;
        private JLabel statusLabel;
        private org.fife.ui.rsyntaxtextarea.RSyntaxTextArea previewArea;
        private String scriptGerado;

        Session(Component ownerComponent, ConexaoAtivaPort connectionManager, String schema,
                TableMetadataCache metadataCache, String tabela, List<Map<String, Object>> linhasSemente) {
            this.owner = (ownerComponent instanceof Window w) ? w
                    : (ownerComponent == null ? null : javax.swing.SwingUtilities.getWindowAncestor(ownerComponent));
            this.connectionManager = connectionManager;
            this.schema = schema;
            this.metadataCache = metadataCache;
            this.tabela = tabela;
            this.linhasSemente = linhasSemente;
        }

        void show() {
            dialog = new JDialog(owner, "Exportar com dependencias — " + tabela, Dialog.ModalityType.APPLICATION_MODAL);
            dialog.setLayout(new BorderLayout());
            dialog.add(buildHeader(), BorderLayout.NORTH);
            centerPanel = new JPanel(new BorderLayout());
            centerPanel.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));
            centerPanel.add(buildPlaceholder(), BorderLayout.CENTER);
            dialog.add(centerPanel, BorderLayout.CENTER);
            dialog.add(buildFooter(), BorderLayout.SOUTH);
            dialog.getRootPane().registerKeyboardAction(e -> dialog.dispose(),
                    KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
            dialog.setSize(760, 560);
            dialog.setLocationRelativeTo(owner);
            dialog.setVisible(true);
        }

        private JComponent buildHeader() {
            JPanel panel = new JPanel(new BorderLayout(0, 4));
            panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 6, 10));
            JLabel title = new JLabel(schema + "." + tabela + " — " + linhasSemente.size() + " linha(s) selecionada(s)");
            Typography.primary(title);
            incluirFilhosCheck = new JCheckBox("Incluir registros dependentes (filhos que referenciam as linhas selecionadas)");
            JLabel info = new JLabel("Linhas PAI (necessarias para as chaves estrangeiras) sao sempre incluidas.");
            Typography.tertiary(info);
            panel.add(title, BorderLayout.NORTH);
            JPanel middle = new JPanel(new BorderLayout());
            middle.add(incluirFilhosCheck, BorderLayout.NORTH);
            middle.add(info, BorderLayout.SOUTH);
            panel.add(middle, BorderLayout.CENTER);
            return panel;
        }

        private JComponent buildPlaceholder() {
            statusLabel = new JLabel(" Clique em \"Gerar script\" para coletar as dependencias.");
            Typography.tertiary(statusLabel);
            JPanel panel = new JPanel(new BorderLayout());
            panel.add(statusLabel, BorderLayout.NORTH);
            return panel;
        }

        private JComponent buildFooter() {
            gerarButton = new JButton("Gerar script");
            gerarButton.addActionListener(a -> onGerar());
            copiarButton = new JButton("Copiar");
            copiarButton.setEnabled(false);
            copiarButton.addActionListener(a -> Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new StringSelection(scriptGerado), null));
            salvarButton = new JButton("Salvar como .sql...");
            salvarButton.setEnabled(false);
            salvarButton.addActionListener(a -> onSalvar());
            Buttons.styleSecondary(copiarButton);
            Buttons.styleSecondary(salvarButton);

            JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 8));
            left.add(copiarButton);
            left.add(salvarButton);

            JPanel south = new JPanel(new BorderLayout());
            south.add(left, BorderLayout.WEST);
            south.add(Buttons.dialogFooter(dialog, gerarButton), BorderLayout.EAST);
            return south;
        }

        private void onGerar() {
            gerarButton.setEnabled(false);
            incluirFilhosCheck.setEnabled(false);
            statusLabel.setText(" Coletando dependencias...");
            boolean incluirFilhos = incluirFilhosCheck.isSelected();

            new SwingWorker<String, Void>() {
                @Override
                protected String doInBackground() throws Exception {
                    Connection conn = connectionManager.getConnection();
                    DatabaseDialect dialect = connectionManager.dialect();
                    List<SchemaForeignKey> grafo = dialect.loadSchemaForeignKeys(conn, schema);
                    RowFetcher fetcher = new JdbcRowFetcher(conn, dialect);
                    FecharDependenciasHandler.Resultado resultado = new FecharDependenciasHandler().fechar(grafo,
                            tabela, linhasSemente, incluirFilhos, fetcher);
                    List<String> ordem = new OrdenarTabelasHandler().ordenar(resultado.linhasPorTabela().keySet(),
                            grafo);
                    String script = InsertScriptBuilder.build(dialect, ordem, resultado.linhasPorTabela());
                    if (resultado.limiteAtingido()) {
                        script = "-- AVISO: limite de linhas atingido — a exportacao pode estar incompleta "
                                + "(cascata de dependencias grande demais). Considere exportar sem \"filhos\" "
                                + "ou selecionar menos linhas.\n\n" + script;
                    }
                    return script;
                }

                @Override
                protected void done() {
                    gerarButton.setEnabled(true);
                    incluirFilhosCheck.setEnabled(true);
                    try {
                        scriptGerado = get();
                        showPreview();
                    } catch (Exception ex) {
                        Throwable cause = (ex.getCause() != null) ? ex.getCause() : ex;
                        statusLabel.setText(" Falha ao coletar dependencias.");
                        JOptionPane.showMessageDialog(dialog,
                                "Falha ao coletar dependencias:\n" + cause.getMessage(), "Exportar com dependencias",
                                JOptionPane.ERROR_MESSAGE);
                    }
                }
            }.execute();
        }

        private void showPreview() {
            if (previewArea == null) {
                previewArea = new org.fife.ui.rsyntaxtextarea.RSyntaxTextArea();
                SqlEditorPane.styleAsReadOnlySql(previewArea);
                previewArea.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
                centerPanel.removeAll();
                centerPanel.add(new JScrollPane(previewArea), BorderLayout.CENTER);
            }
            previewArea.setText(scriptGerado);
            previewArea.setCaretPosition(0);
            centerPanel.revalidate();
            centerPanel.repaint();
            copiarButton.setEnabled(true);
            salvarButton.setEnabled(true);
        }

        private void onSalvar() {
            JFileChooser fc = new JFileChooser();
            fc.setDialogTitle("Salvar script SQL");
            fc.setSelectedFile(new java.io.File(tabela + "_dependencias.sql"));
            fc.setFileFilter(new FileNameExtensionFilter("Script SQL (*.sql)", "sql"));
            if (fc.showSaveDialog(dialog) != JFileChooser.APPROVE_OPTION) {
                return;
            }
            java.io.File file = fc.getSelectedFile();
            if (!file.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".sql")) {
                file = new java.io.File(file.getParentFile(), file.getName() + ".sql");
            }
            try {
                Files.writeString(file.toPath(), scriptGerado, StandardCharsets.UTF_8);
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(dialog, "Falha ao salvar arquivo:\n" + ex.getMessage(),
                        "Exportar com dependencias", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
}
