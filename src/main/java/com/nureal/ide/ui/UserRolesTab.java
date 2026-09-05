package com.nureal.ide.ui;
import com.nureal.ide.compartilhado.designsystem.Buttons;
import com.nureal.ide.compartilhado.designsystem.Typography;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.List;
import java.util.function.Supplier;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

import com.nureal.ide.modulos.dialeto.dominio.contratos.SecurityCapability;
import com.nureal.ide.modulos.metadados.dominio.entidades.DbUserInfo;

/**
 * Aba "Roles" do {@link UserManagementDialog} — extraida do {@code Session}
 * interno (SPEC-0008 Etapa 3: arquivo tinha passado dos 800 linhas, faixa
 * "avaliar divisao"). Cria/exclui roles e atribui/remove do usuario
 * selecionado na lista da ESQUERDA do dialogo — por isso recebe
 * {@code selectedUser} como {@link Supplier} em vez de guardar o proprio
 * {@link DbUserInfo}, ja que a selecao pertence ao {@code Session}, nao a
 * esta aba.
 */
final class UserRolesTab {

    private final SecurityCapability dialect;
    private final QueryRunner queryRunner;
    private final DdlAssistantDialog.DdlRunner runner;
    private final JDialog dialog;
    private final Supplier<DbUserInfo> selectedUser;

    private final DefaultListModel<String> rolesListModel = new DefaultListModel<>();
    private JList<String> rolesList;

    UserRolesTab(SecurityCapability dialect, QueryRunner queryRunner, DdlAssistantDialog.DdlRunner runner,
            JDialog dialog, Supplier<DbUserInfo> selectedUser) {
        this.dialect = dialect;
        this.queryRunner = queryRunner;
        this.runner = runner;
        this.dialog = dialog;
        this.selectedUser = selectedUser;
    }

    /** Limpa a lista de roles exibida — chamado quando a selecao de usuario muda (ver Session#onUserSelectionChanged). */
    void clear() {
        rolesListModel.clear();
    }

    JComponent build() {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));

        JLabel note = new JLabel(
                "<html>Requer MySQL 8+. Lista abaixo e melhor esforco: so mostra roles ja atribuidas a algum "
                        + "usuario (nao ha um jeito portatil de listar roles nunca atribuidas).</html>");
        Typography.tertiary(note);

        rolesList = new JList<>(rolesListModel);
        JScrollPane scroll = new JScrollPane(rolesList);
        scroll.setPreferredSize(new Dimension(300, 220));

        JButton newRole = new JButton("+ Nova role...");
        JButton deleteRole = new JButton("- Excluir role");
        JButton refreshRoles = new JButton("Atualizar lista");
        JButton assign = new JButton("Atribuir ao usuario selecionado");
        JButton unassign = new JButton("Remover do usuario selecionado");
        for (JButton b : new JButton[] { newRole, deleteRole, refreshRoles }) {
            Buttons.styleSecondary(b);
        }
        Buttons.stylePrimary(assign);
        Buttons.styleSecondary(unassign);
        newRole.addActionListener(a -> onNewRole());
        deleteRole.addActionListener(a -> onDeleteRole());
        refreshRoles.addActionListener(a -> refresh());
        assign.addActionListener(a -> onAssignRole());
        unassign.addActionListener(a -> onUnassignRole());

        JPanel leftButtons = new JPanel(new GridLayout(3, 1, 0, 4));
        leftButtons.add(newRole);
        leftButtons.add(deleteRole);
        leftButtons.add(refreshRoles);

        JPanel left = new JPanel(new BorderLayout(0, 6));
        left.add(scroll, BorderLayout.CENTER);
        left.add(leftButtons, BorderLayout.SOUTH);

        JPanel rightButtons = new JPanel(new GridLayout(2, 1, 0, 6));
        rightButtons.add(assign);
        rightButtons.add(unassign);
        JPanel right = new JPanel(new BorderLayout());
        right.add(rightButtons, BorderLayout.NORTH);

        JPanel center = new JPanel(new BorderLayout(10, 0));
        center.add(left, BorderLayout.WEST);
        center.add(right, BorderLayout.CENTER);

        panel.add(note, BorderLayout.NORTH);
        panel.add(center, BorderLayout.CENTER);
        return panel;
    }

    private void refresh() {
        queryRunner.query(dialect.knownRolesQuery(), rows -> {
            rolesListModel.clear();
            for (Object[] row : rows) {
                if (row.length > 0 && row[0] != null) {
                    rolesListModel.addElement(String.valueOf(row[0]));
                }
            }
        }, ex -> error("Falha ao listar roles (o servidor pode nao suportar roles — exige MySQL 8+)", ex));
    }

    private void onNewRole() {
        String name = JOptionPane.showInputDialog(dialog, "Nome da nova role:", "Nova role",
                JOptionPane.PLAIN_MESSAGE);
        if (name == null || name.isBlank()) {
            return;
        }
        String sql = dialect.createRoleStatement(name.trim());
        runner.run(List.of(sql), () -> {
            info("Role \"" + name.trim() + "\" criada.");
            refresh();
        }, ex -> error("Falha ao criar a role", ex));
    }

    private void onDeleteRole() {
        String role = rolesList.getSelectedValue();
        if (role == null) {
            JOptionPane.showMessageDialog(dialog, "Selecione uma role na lista.", "Usuarios e privilegios",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (!confirm("Excluir a role \"" + role + "\"? Usuarios que a tinham atribuida perdem os privilegios dela.")) {
            return;
        }
        String sql = dialect.dropRoleStatement(role);
        runner.run(List.of(sql), () -> {
            info("Role \"" + role + "\" excluida.");
            refresh();
        }, ex -> error("Falha ao excluir a role", ex));
    }

    private void onAssignRole() {
        DbUserInfo u = requireSelected();
        String role = rolesList.getSelectedValue();
        if (u == null || role == null) {
            JOptionPane.showMessageDialog(dialog, "Selecione um usuario (lista da esquerda) e uma role.",
                    "Usuarios e privilegios", JOptionPane.WARNING_MESSAGE);
            return;
        }
        List<String> stmts = List.of(
                dialect.grantRoleStatement(role, u.user(), u.host()),
                dialect.setDefaultRoleStatement(role, u.user(), u.host()));
        runner.run(stmts, () -> info("Role \"" + role + "\" atribuida a " + u.label() + " (ja ativa por padrao)."),
                ex -> error("Falha ao atribuir a role", ex));
    }

    private void onUnassignRole() {
        DbUserInfo u = requireSelected();
        String role = rolesList.getSelectedValue();
        if (u == null || role == null) {
            JOptionPane.showMessageDialog(dialog, "Selecione um usuario (lista da esquerda) e uma role.",
                    "Usuarios e privilegios", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String sql = dialect.revokeRoleStatement(role, u.user(), u.host());
        runner.run(List.of(sql), () -> info("Role \"" + role + "\" removida de " + u.label() + "."),
                ex -> error("Falha ao remover a role", ex));
    }

    private DbUserInfo requireSelected() {
        DbUserInfo u = selectedUser.get();
        if (u == null) {
            JOptionPane.showMessageDialog(dialog, "Selecione um usuario na lista.", "Usuarios e privilegios",
                    JOptionPane.WARNING_MESSAGE);
        }
        return u;
    }

    private boolean confirm(String message) {
        return JOptionPane.showConfirmDialog(dialog, message, "Usuarios e privilegios",
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) == JOptionPane.YES_OPTION;
    }

    private void info(String message) {
        JOptionPane.showMessageDialog(dialog, message, "Usuarios e privilegios", JOptionPane.INFORMATION_MESSAGE);
    }

    private void error(String prefix, Exception ex) {
        JOptionPane.showMessageDialog(dialog, prefix + ":\n" + ex.getMessage(), "Usuarios e privilegios",
                JOptionPane.ERROR_MESSAGE);
    }
}
