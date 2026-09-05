package com.nureal.ide.ui;
import com.nureal.ide.compartilhado.designsystem.Buttons;
import com.nureal.ide.compartilhado.designsystem.Typography;

import com.nureal.ide.modulos.dialeto.dominio.contratos.ReplicationCapability;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTabbedPane;
import javax.swing.KeyStroke;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.util.function.Consumer;

/**
 * Visor de eventos agendados ({@code CREATE EVENT}) do schema aberto e de
 * status de replicacao do servidor — fase 4 do GAP_ANALYSIS_DBA_DEV.md,
 * mesma familia NAO-MODAL de {@link ProcessListDialog}/{@link ServerStatusDialog}
 * (um monitor de referencia so e util se o usuario puder continuar
 * trabalhando enquanto o consulta). Somente leitura: nao oferece editar
 * eventos nem reconfigurar replicacao (ambos exigiriam um assistente proprio
 * — fica para uma proxima versao se houver pedido).
 */
final class EventsReplicationDialog {

    private EventsReplicationDialog() {
    }

    static void open(Component parent, String schemaName, ReplicationCapability dialect,
            QueryRunner queryRunner, ColumnQueryRunner columnQueryRunner) {
        new Session(parent, schemaName, dialect, queryRunner, columnQueryRunner).show();
    }

    private static final String[] EVENT_COLUMNS = { "Nome", "Status", "Tipo", "Proxima execucao",
            "Intervalo", "Unidade", "Inicio", "Fim", "Ao completar", "Ultima execucao", "Definidor" };

    private static final class Session {
        private final Window owner;
        private final String schemaName;
        private final ReplicationCapability dialect;
        private final QueryRunner queryRunner;
        private final ColumnQueryRunner columnQueryRunner;

        private JDialog dialog;
        private DefaultTableModel eventsModel;

        Session(Component parent, String schemaName, ReplicationCapability dialect,
                QueryRunner queryRunner, ColumnQueryRunner columnQueryRunner) {
            this.owner = (DialogUtil.owner(parent) instanceof Window w) ? w : null;
            this.schemaName = schemaName;
            this.dialect = dialect;
            this.queryRunner = queryRunner;
            this.columnQueryRunner = columnQueryRunner;
        }

        void show() {
            dialog = new JDialog(owner, "Eventos e replicacao — " + schemaName, JDialog.ModalityType.MODELESS);
            dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
            dialog.setLayout(new BorderLayout());

            JTabbedPane tabs = new JTabbedPane();
            tabs.addTab("Eventos agendados", buildEventsTab());
            tabs.addTab("Replicacao (esta instancia como REPLICA)",
                    buildReplicationTab(dialect.replicaStatusQuery(),
                            "Esta instancia nao esta configurada como replica de nenhum servidor (ou nao ha privilegio REPLICATION CLIENT)."));
            tabs.addTab("Replicacao (esta instancia como ORIGEM)",
                    buildReplicationTab(dialect.sourceStatusQuery(),
                            "Sem posicao de binary log — o log binario pode estar desligado nesta instancia."));
            dialog.add(tabs, BorderLayout.CENTER);

            // Esc fecha — mesmo atalho ja usado nos dialogs "de formulario
            // guiado" (DDL/view/trigger/rotina/usuarios), faltava aqui
            // (achado numa auditoria pedida pelo usuario).
            dialog.getRootPane().registerKeyboardAction(e -> dialog.dispose(),
                    KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
            dialog.setSize(880, 560);
            dialog.setLocationRelativeTo(owner);
            dialog.setVisible(true);
        }

        private JComponent buildEventsTab() {
            eventsModel = new DefaultTableModel(EVENT_COLUMNS, 0) {
                private static final long serialVersionUID = 1L;

                @Override
                public boolean isCellEditable(int row, int column) {
                    return false;
                }
            };
            JTable table = MetadataTableStyle.createStyledTable(eventsModel);
            TableRowSorter<DefaultTableModel> sorter = new TableRowSorter<>(eventsModel);
            table.setRowSorter(sorter);

            JLabel note = new JLabel("  Eventos criados com CREATE EVENT — inclui pausados (status DISABLED).");
            Typography.tertiary(note);

            JComponent panel = SearchableTableTab.build(table, sorter, this::refreshEvents,
                    new Dimension(760, 420), note);

            refreshEvents();
            return panel;
        }

        private void refreshEvents() {
            queryRunner.query(dialect.eventsQuery(schemaName), rows -> {
                eventsModel.setRowCount(0);
                for (Object[] row : rows) {
                    eventsModel.addRow(row);
                }
            }, ex -> JOptionPane.showMessageDialog(dialog, "Falha ao listar eventos:\n" + ex.getMessage(),
                    "Eventos agendados", JOptionPane.ERROR_MESSAGE));
        }

        /**
         * Uma aba de status de replicacao: {@code SHOW SLAVE STATUS}/
         * {@code SHOW MASTER STATUS} tem colunas que VARIAM por versao do
         * servidor e normalmente uma UNICA linha com MUITOS campos — por
         * isso mostrado verticalmente (Campo | Valor), igual ao {@code \G}
         * do cliente {@code mysql}, em vez de uma tabela larga com uma linha
         * so (ilegivel). {@code emptyMessage} cobre o caso normal de "esta
         * instancia nao faz esse papel na replicacao" (zero linhas), que sem
         * isso pareceria uma tela vazia quebrada.
         */
        private JComponent buildReplicationTab(String query, String emptyMessage) {
            DefaultTableModel model = new DefaultTableModel(new String[] { "Campo", "Valor" }, 0) {
                private static final long serialVersionUID = 1L;

                @Override
                public boolean isCellEditable(int row, int column) {
                    return false;
                }
            };
            JTable table = MetadataTableStyle.createStyledTable(model);
            JLabel emptyLabel = new JLabel("  " + emptyMessage);
            Typography.tertiary(emptyLabel);
            emptyLabel.setVisible(false);

            JButton refresh = new JButton("Atualizar");
            Buttons.styleSecondary(refresh);
            Consumer<Boolean> setEmptyState = empty -> {
                table.setVisible(!empty);
                emptyLabel.setVisible(empty);
            };
            Runnable load = () -> columnQueryRunner.query(query, (columns, rows) -> {
                model.setRowCount(0);
                model.setColumnCount(0);
                model.setColumnIdentifiers(new String[] { "Campo", "Valor" });
                if (rows.isEmpty()) {
                    setEmptyState.accept(true);
                    return;
                }
                setEmptyState.accept(false);
                Object[] firstRow = rows.get(0);
                for (int i = 0; i < columns.size() && i < firstRow.length; i++) {
                    model.addRow(new Object[] { columns.get(i), firstRow[i] });
                }
            }, ex -> JOptionPane.showMessageDialog(dialog, "Falha ao consultar status de replicacao:\n" + ex.getMessage(),
                    "Replicacao", JOptionPane.ERROR_MESSAGE));
            refresh.addActionListener(a -> load.run());

            JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 6));
            top.add(refresh);

            JScrollPane scroll = new JScrollPane(table);
            scroll.setPreferredSize(new Dimension(760, 420));

            JPanel center = new JPanel(new BorderLayout());
            center.add(scroll, BorderLayout.CENTER);
            center.add(emptyLabel, BorderLayout.NORTH);

            JPanel panel = new JPanel(new BorderLayout());
            panel.add(top, BorderLayout.NORTH);
            panel.add(center, BorderLayout.CENTER);

            load.run();
            return panel;
        }
    }
}
