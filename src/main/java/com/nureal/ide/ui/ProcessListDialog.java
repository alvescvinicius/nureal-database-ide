package com.nureal.ide.ui;
import com.nureal.ide.compartilhado.designsystem.Buttons;
import com.nureal.ide.compartilhado.designsystem.Typography;
import com.nureal.ide.compartilhado.designsystem.dialog.DialogShell;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.Timer;
import javax.swing.table.DefaultTableModel;

import com.nureal.ide.modulos.dialeto.dominio.contratos.DatabaseDialect;

/**
 * Monitor de sessoes do servidor ({@code information_schema.PROCESSLIST}),
 * com botao para encerrar ({@code KILL}) a sessao selecionada — pedido do
 * usuario ("gerenciamento... para administradores das bases", ver
 * {@code GAP_ANALYSIS_DBA_DEV.md}, fase 2: "essencial para um DBA que
 * precisa liberar um lock ou parar uma query travada sem abrir outro
 * cliente"). NAO-MODAL de proposito (mesma familia do
 * {@link FkInspectorWindow}): um monitor "ao vivo" so e util se o usuario
 * puder continuar trabalhando (inclusive rodando a query que quer
 * investigar) enquanto o observa.
 */
final class ProcessListDialog {

    private ProcessListDialog() {
    }

    static void open(Component parent, DatabaseDialect dialect, QueryRunner queryRunner,
            DdlAssistantDialog.DdlRunner runner) {
        new Session(parent, dialect, queryRunner, runner).show();
    }

    private static final String[] COLUMNS = { "ID", "Usuario", "Host", "Banco", "Comando", "Tempo (s)", "Estado", "Info (SQL em execucao)" };

    private static final class Session {
        private final Window owner;
        private final DatabaseDialect dialect;
        private final QueryRunner queryRunner;
        private final DdlAssistantDialog.DdlRunner runner;

        private JDialog dialog;
        private DefaultTableModel model;
        private JTable table;
        private Timer autoRefreshTimer;
        /**
         * Incrementado a cada {@link #refresh()}: o auto-refresh dispara uma
         * consulta NOVA a cada 3s sem esperar a anterior terminar, entao duas
         * podem estar "em voo" ao mesmo tempo — se o servidor demorar mais
         * que 3s pra responder (justo quando um DBA estaria de olho neste
         * monitor), a resposta mais LENTA/antiga pode chegar DEPOIS de uma
         * mais nova e sobrescrever a grade com dados desatualizados. Cada
         * callback so aplica o resultado se o numero de sequencia ainda for
         * o mais recente — achado numa auditoria pedida pelo usuario.
         */
        private int refreshSeq;

        Session(Component parent, DatabaseDialect dialect, QueryRunner queryRunner, DdlAssistantDialog.DdlRunner runner) {
            this.owner = (DialogUtil.owner(parent) instanceof Window w) ? w : null;
            this.dialect = dialect;
            this.queryRunner = queryRunner;
            this.runner = runner;
        }

        void show() {
            DialogShell shell = DialogShell.create(owner, "Sessoes ativas do servidor", JDialog.ModalityType.MODELESS);
            dialog = shell.dialog();
            shell.center(buildTable());
            shell.north(buildToolbar());
            // Para o timer de auto-refresh nao continuar rodando em segundo
            // plano (e vazando memoria/tempo de CPU) depois que a janela e
            // fechada — dialogo NAO-MODAL nao bloqueia o resto do app, entao
            // o usuario pode muito bem fechar esta janela sem passar por um
            // botao dedicado.
            shell.onClosed(() -> {
                if (autoRefreshTimer != null) {
                    autoRefreshTimer.stop();
                }
            });
            refresh();
            shell.show(920, 520);
        }

        private JComponent buildTable() {
            model = new DefaultTableModel(COLUMNS, 0) {
                private static final long serialVersionUID = 1L;

                @Override
                public boolean isCellEditable(int row, int column) {
                    return false;
                }
            };
            table = MetadataTableStyle.createStyledTable(model);
            JScrollPane scroll = new JScrollPane(table);
            scroll.setPreferredSize(new Dimension(900, 440));
            return scroll;
        }

        private JComponent buildToolbar() {
            JButton refreshBtn = new JButton("Atualizar agora");
            JButton killBtn = new JButton("Encerrar sessao selecionada (KILL)");
            Buttons.styleSecondary(refreshBtn);
            Buttons.styleSecondary(killBtn);
            refreshBtn.addActionListener(a -> refresh());
            killBtn.addActionListener(a -> onKill());

            JCheckBox autoRefresh = new JCheckBox("Atualizar automaticamente a cada 3s");
            autoRefresh.addActionListener(a -> {
                if (autoRefresh.isSelected()) {
                    if (autoRefreshTimer == null) {
                        autoRefreshTimer = new Timer(3000, e -> refresh());
                    }
                    autoRefreshTimer.start();
                } else if (autoRefreshTimer != null) {
                    autoRefreshTimer.stop();
                }
            });

            JLabel note = new JLabel("  \"Comando\"=Sleep e conexao ociosa, normal; \"Info\" mostra o SQL em execucao agora, quando houver.");
            Typography.tertiary(note);

            JComponent bar = new javax.swing.JPanel(new FlowLayout(FlowLayout.LEFT, 6, 6));
            bar.add(refreshBtn);
            bar.add(killBtn);
            bar.add(autoRefresh);
            JComponent wrap = new javax.swing.JPanel(new BorderLayout());
            wrap.add(bar, BorderLayout.NORTH);
            wrap.add(note, BorderLayout.SOUTH);
            wrap.setBorder(BorderFactory.createEmptyBorder(4, 6, 0, 6));
            return wrap;
        }

        private void refresh() {
            int seq = ++refreshSeq;
            queryRunner.query(dialect.processListQuery(), rows -> {
                if (seq != refreshSeq) {
                    return; // resposta de uma consulta ja superada por outra mais recente — descarta
                }
                model.setRowCount(0);
                for (Object[] row : rows) {
                    model.addRow(row);
                }
            }, ex -> {
                if (seq != refreshSeq) {
                    return;
                }
                JOptionPane.showMessageDialog(dialog,
                        "Falha ao listar sessoes (a conexao pode nao ter privilegio PROCESS):\n" + ex.getMessage(),
                        "Sessoes ativas", JOptionPane.ERROR_MESSAGE);
            });
        }

        private void onKill() {
            int viewRow = table.getSelectedRow();
            if (viewRow < 0) {
                JOptionPane.showMessageDialog(dialog, "Selecione uma sessao na lista.", "Sessoes ativas",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }
            int modelRow = table.convertRowIndexToModel(viewRow);
            Object idValue = model.getValueAt(modelRow, 0);
            long id;
            try {
                id = Long.parseLong(String.valueOf(idValue));
            } catch (NumberFormatException ex) {
                return;
            }
            int confirm = JOptionPane.showConfirmDialog(dialog,
                    "Encerrar a sessao " + id + "? Qualquer instrucao em andamento nela sera interrompida (rollback).",
                    "Sessoes ativas", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (confirm != JOptionPane.YES_OPTION) {
                return;
            }
            String sql = dialect.killStatement(id);
            runner.run(List.of(sql), () -> {
                JOptionPane.showMessageDialog(dialog, "Sessao " + id + " encerrada.", "Sessoes ativas",
                        JOptionPane.INFORMATION_MESSAGE);
                refresh();
            }, ex -> JOptionPane.showMessageDialog(dialog, "Falha ao encerrar a sessao:\n" + ex.getMessage(),
                    "Sessoes ativas", JOptionPane.ERROR_MESSAGE));
        }
    }
}
