package com.nureal.ide.ui;

import com.nureal.ide.compartilhado.designsystem.Buttons;
import com.nureal.ide.compartilhado.designsystem.Typography;
import com.nureal.ide.modulos.conexoes.dominio.contratos.ConexaoAtivaPort;
import com.nureal.ide.modulos.dialeto.dominio.contratos.DatabaseDialect;
import com.nureal.ide.modulos.exportacaorelacional.dominio.FecharTabelasHandler;
import com.nureal.ide.modulos.exportacaorelacional.dominio.OrdenarTabelasHandler;
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
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

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
import javax.swing.KeyStroke;
import javax.swing.SwingWorker;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 * "Gerar DDL com hierarquia...": monta um script {@code CREATE TABLE} com a
 * tabela escolhida MAIS as tabelas relacionadas por chave estrangeira — PAIS
 * (tabelas que ela referencia) sempre inclusas, FILHAS (tabelas que a
 * referenciam) opcionais — em ordem de dependencia, pronto pra rodar num
 * banco vazio. Mesma estrutura de dialogo/pedido do usuario que
 * {@link RelationalExportDialog} ("assim como nos exports de inserts"), so
 * que gerando {@code CREATE TABLE} em vez de {@code INSERT}. NUNCA executa
 * nada contra um banco — so gera o texto (copiar/salvar/enviar pro editor).
 */
final class TableDdlHierarchyDialog {

    private TableDdlHierarchyDialog() {
    }

    static void open(Component ownerComponent, ConexaoAtivaPort connectionManager, String schema,
            TableMetadataCache metadataCache, String tableName, Consumer<String> onSendToEditor) {
        new Session(ownerComponent, connectionManager, schema, metadataCache, tableName, onSendToEditor).show();
    }

    private static final class Session {
        private final Window owner;
        private final ConexaoAtivaPort connectionManager;
        private final String schema;
        private final TableMetadataCache metadataCache;
        private final String tableName;
        private final Consumer<String> onSendToEditor;

        private JDialog dialog;
        private JCheckBox incluirFilhosCheck;
        private JButton gerarButton;
        private JButton salvarButton;
        private JButton copiarButton;
        private JButton enviarEditorButton;
        private JPanel centerPanel;
        private JLabel statusLabel;
        private org.fife.ui.rsyntaxtextarea.RSyntaxTextArea previewArea;
        private String scriptGerado;

        Session(Component ownerComponent, ConexaoAtivaPort connectionManager, String schema,
                TableMetadataCache metadataCache, String tableName, Consumer<String> onSendToEditor) {
            this.owner = (ownerComponent instanceof Window w) ? w
                    : (ownerComponent == null ? null : javax.swing.SwingUtilities.getWindowAncestor(ownerComponent));
            this.connectionManager = connectionManager;
            this.schema = schema;
            this.metadataCache = metadataCache;
            this.tableName = tableName;
            this.onSendToEditor = onSendToEditor;
        }

        void show() {
            dialog = new JDialog(owner, "Gerar DDL com hierarquia — " + tableName,
                    Dialog.ModalityType.APPLICATION_MODAL);
            dialog.setLayout(new BorderLayout());
            dialog.add(buildHeader(), BorderLayout.NORTH);
            centerPanel = new JPanel(new BorderLayout());
            centerPanel.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));
            statusLabel = new JLabel(" Clique em \"Gerar script\" para coletar as tabelas relacionadas.");
            Typography.tertiary(statusLabel);
            JPanel placeholder = new JPanel(new BorderLayout());
            placeholder.add(statusLabel, BorderLayout.NORTH);
            centerPanel.add(placeholder, BorderLayout.CENTER);
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
            JLabel title = new JLabel(schema + "." + tableName);
            Typography.primary(title);
            incluirFilhosCheck = new JCheckBox("Incluir tabelas dependentes (filhas que referenciam esta tabela)");
            JLabel info = new JLabel("Tabelas PAI (referenciadas por esta) sao sempre incluidas.");
            Typography.tertiary(info);
            panel.add(title, BorderLayout.NORTH);
            JPanel middle = new JPanel(new BorderLayout());
            middle.add(incluirFilhosCheck, BorderLayout.NORTH);
            middle.add(info, BorderLayout.SOUTH);
            panel.add(middle, BorderLayout.CENTER);
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
            enviarEditorButton = new JButton("Enviar para o editor");
            enviarEditorButton.setEnabled(false);
            enviarEditorButton.addActionListener(a -> onEnviarParaEditor());
            Buttons.styleSecondary(copiarButton);
            Buttons.styleSecondary(salvarButton);
            Buttons.styleSecondary(enviarEditorButton);

            JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 8));
            left.add(copiarButton);
            left.add(salvarButton);
            left.add(enviarEditorButton);

            JPanel south = new JPanel(new BorderLayout());
            south.add(left, BorderLayout.WEST);
            south.add(Buttons.dialogFooter(dialog, gerarButton), BorderLayout.EAST);
            return south;
        }

        private void onGerar() {
            gerarButton.setEnabled(false);
            incluirFilhosCheck.setEnabled(false);
            statusLabel.setText(" Coletando tabelas relacionadas...");
            boolean incluirFilhos = incluirFilhosCheck.isSelected();

            new SwingWorker<String, Void>() {
                @Override
                protected String doInBackground() throws Exception {
                    Connection conn = connectionManager.getConnection();
                    DatabaseDialect dialect = connectionManager.dialect();
                    List<SchemaForeignKey> grafo = dialect.loadSchemaForeignKeys(conn, schema);
                    Set<String> tabelas = new FecharTabelasHandler().fechar(grafo, tableName, incluirFilhos);
                    List<String> ordem = new OrdenarTabelasHandler().ordenar(tabelas, grafo);
                    StringBuilder sb = new StringBuilder();
                    sb.append("-- Gerado pelo Assistente de DDL da Nureal Database IDE\n");
                    sb.append("SET FOREIGN_KEY_CHECKS=0;\n\n");
                    for (String tabela : ordem) {
                        String ddl = ObjectExplorerController.fetchTableDdl(conn, dialect, tabela);
                        sb.append("-- ").append(tabela).append('\n').append(ddl.stripTrailing()).append(";\n\n");
                    }
                    sb.append("SET FOREIGN_KEY_CHECKS=1;\n");
                    return sb.toString();
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
                        statusLabel.setText(" Falha ao coletar tabelas relacionadas.");
                        JOptionPane.showMessageDialog(dialog,
                                "Falha ao coletar tabelas relacionadas:\n" + cause.getMessage(),
                                "Gerar DDL com hierarquia", JOptionPane.ERROR_MESSAGE);
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
            enviarEditorButton.setEnabled(true);
        }

        private void onSalvar() {
            JFileChooser fc = new JFileChooser();
            fc.setDialogTitle("Salvar script SQL");
            fc.setSelectedFile(new java.io.File(tableName + "_ddl_hierarquia.sql"));
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
                        "Gerar DDL com hierarquia", JOptionPane.ERROR_MESSAGE);
            }
        }

        private void onEnviarParaEditor() {
            dialog.dispose();
            onSendToEditor.accept(scriptGerado);
        }
    }
}
