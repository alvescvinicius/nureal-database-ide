package com.nureal.ide.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Window;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;

import com.nureal.ide.core.dialect.DatabaseDialect;

/**
 * Visor de variaveis ({@code SHOW GLOBAL VARIABLES}) e status
 * ({@code SHOW GLOBAL STATUS}) do servidor conectado — pesquisavel, somente
 * leitura (ver {@code GAP_ANALYSIS_DBA_DEV.md}, fase 2). NAO tenta distinguir
 * quais variaveis aceitam {@code SET GLOBAL} em runtime das que exigem
 * reiniciar o servidor — essa informacao nao vem de forma confiavel do
 * proprio {@code SHOW VARIABLES}, entao a UI so mostra nome+valor; mudar uma
 * variavel continua sendo uma acao manual (rodar o {@code SET GLOBAL} no
 * editor SQL), fora do escopo deste visor.
 */
final class ServerStatusDialog {

    private ServerStatusDialog() {
    }

    static void open(Component parent, DatabaseDialect dialect, QueryRunner queryRunner) {
        new Session(parent, dialect, queryRunner).show();
    }

    private static final class Session {
        private final Window owner;
        private final DatabaseDialect dialect;
        private final QueryRunner queryRunner;

        private JDialog dialog;

        Session(Component parent, DatabaseDialect dialect, QueryRunner queryRunner) {
            this.owner = (DialogUtil.owner(parent) instanceof Window w) ? w : null;
            this.dialect = dialect;
            this.queryRunner = queryRunner;
        }

        void show() {
            dialog = new JDialog(owner, "Variaveis e status do servidor", JDialog.ModalityType.MODELESS);
            dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
            dialog.setLayout(new BorderLayout());

            JTabbedPane tabs = new JTabbedPane();
            tabs.addTab("Variaveis (SHOW GLOBAL VARIABLES)", buildTab(dialect.globalVariablesQuery()));
            tabs.addTab("Status (SHOW GLOBAL STATUS)", buildTab(dialect.globalStatusQuery()));
            dialog.add(tabs, BorderLayout.CENTER);

            dialog.setSize(760, 560);
            dialog.setLocationRelativeTo(owner);
            dialog.setVisible(true);
        }

        /** Uma aba: campo de busca + tabela filtravel (nome/valor), com "Atualizar". */
        private JComponent buildTab(String query) {
            DefaultTableModel model = new DefaultTableModel(new String[] { "Nome", "Valor" }, 0) {
                private static final long serialVersionUID = 1L;

                @Override
                public boolean isCellEditable(int row, int column) {
                    return false;
                }
            };
            JTable table = MetadataTableStyle.createStyledTable(model);
            TableRowSorter<DefaultTableModel> sorter = new TableRowSorter<>(model);
            table.setRowSorter(sorter);

            JComponent panel = SearchableTableTab.build(table, sorter, () -> loadInto(query, model),
                    new Dimension(720, 460), null);

            loadInto(query, model);
            return panel;
        }

        private void loadInto(String query, DefaultTableModel model) {
            queryRunner.query(query, rows -> {
                model.setRowCount(0);
                for (Object[] row : rows) {
                    model.addRow(row);
                }
            }, ex -> JOptionPane.showMessageDialog(dialog, "Falha ao consultar o servidor:\n" + ex.getMessage(),
                    "Variaveis e status", JOptionPane.ERROR_MESSAGE));
        }
    }
}
