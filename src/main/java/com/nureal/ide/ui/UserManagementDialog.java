package com.nureal.ide.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;

import com.nureal.ide.core.dialect.DatabaseDialect;
import com.nureal.ide.core.metadata.model.DbUserInfo;

/**
 * Administracao de usuarios e privilegios do SERVIDOR conectado — "Gerenciar
 * usuarios e privilegios..." no menu de contexto da raiz do esquema (ver
 * {@code MainWindow#buildSchemaRootContextMenu}). Adicionado por pedido
 * explicito do usuario ("gerenciamento de usuario, permissoes... para
 * administradores das bases" — ver {@code GAP_ANALYSIS_DBA_DEV.md} na raiz
 * do repositorio, fase 1 do roadmap la descrito).
 * <p>
 * IMPORTANTE — isto administra os usuarios DO BANCO conectado (linhas de
 * {@code mysql.user}), nao usuarios da propria Nureal Database IDE: o app
 * continua desktop single-user, sem login proprio. Exige que a conexao ativa
 * tenha privilegio administrativo (tipicamente {@code CREATE USER}/
 * {@code GRANT OPTION}) — sem isso, o servidor recusa cada operacao com uma
 * mensagem de permissao, que aparece normalmente pelo mesmo caminho de erro
 * do restante do app (ver {@code onError} em cada acao).
 * <p>
 * Escopo assumido conscientemente MENOR que uma ferramenta como o MySQL
 * Workbench num ponto: a aba "Privilegios" NAO tenta reconstruir o estado
 * atual (quais caixinhas already marcadas) a partir de {@code SHOW GRANTS} —
 * fazer isso direito exigiria um parser de verdade da gramatica de GRANT do
 * MySQL. Em vez disso, "Privilegios" e so um formulario de ESCRITA (monta um
 * GRANT/REVOKE a partir do que foi marcado agora) e a aba "Ver SHOW GRANTS"
 * cobre a LEITURA (mostra o estado real, cru, como o proprio servidor
 * devolve) — as duas juntas cobrem o fluxo completo sem o parser.
 */
final class UserManagementDialog {

    private UserManagementDialog() {
    }

    /**
     * Abre o dialogo. {@code currentSchemaTables} so e usado para
     * pre-preencher o combo de tabela quando o schema escolhido no nivel de
     * privilegio for exatamente {@code currentSchemaName} (o unico schema
     * cujas tabelas ja estao carregadas em memoria sem uma nova consulta) —
     * para qualquer outro schema, o campo continua editavel (o usuario digita
     * o nome da tabela).
     */
    static void open(Component parent, DatabaseDialect dialect, List<DbUserInfo> initialUsers,
            List<String> schemaNames, String currentSchemaName, List<String> currentSchemaTables,
            DdlAssistantDialog.DdlRunner runner, QueryRunner queryRunner) {
        new Session(parent, dialect, initialUsers, schemaNames, currentSchemaName, currentSchemaTables,
                runner, queryRunner).show();
    }

    private static final String LEVEL_GLOBAL = "Global (todo o servidor)";
    private static final String LEVEL_SCHEMA = "Este schema (todas as tabelas)";
    private static final String LEVEL_TABLE = "Uma tabela especifica";

    private static final class Session {
        private final Window owner;
        private final DatabaseDialect dialect;
        private final List<String> schemaNames;
        private final String currentSchemaName;
        private final List<String> currentSchemaTables;
        private final DdlAssistantDialog.DdlRunner runner;
        private final QueryRunner queryRunner;

        private JDialog dialog;
        private final DefaultListModel<DbUserInfo> userListModel = new DefaultListModel<>();
        private JList<DbUserInfo> userList;
        private JLabel selectedUserLabel;
        private JTabbedPane userTabs;

        // Aba "Geral"
        private JCheckBox lockedCheckbox;
        private JPasswordField newPasswordField;

        // Aba "Privilegios"
        private JComboBox<String> levelCombo;
        private JComboBox<String> schemaCombo;
        private JComboBox<String> tableCombo;
        private JTextField columnsField;
        private JPanel privilegeCheckPanel;
        private final Map<String, JCheckBox> privilegeChecks = new LinkedHashMap<>();
        private JCheckBox grantOptionCheckbox;

        // Aba "SHOW GRANTS"
        private JTextArea grantsArea;

        // Aba "Roles"
        private final DefaultListModel<String> rolesListModel = new DefaultListModel<>();
        private JList<String> rolesList;

        Session(Component parent, DatabaseDialect dialect, List<DbUserInfo> initialUsers, List<String> schemaNames,
                String currentSchemaName, List<String> currentSchemaTables, DdlAssistantDialog.DdlRunner runner,
                QueryRunner queryRunner) {
            this.owner = (DialogUtil.owner(parent) instanceof Window w) ? w : null;
            this.dialect = dialect;
            this.schemaNames = schemaNames == null ? List.of() : schemaNames;
            this.currentSchemaName = currentSchemaName;
            this.currentSchemaTables = currentSchemaTables == null ? List.of() : currentSchemaTables;
            this.runner = runner;
            this.queryRunner = queryRunner;
            for (DbUserInfo u : initialUsers) {
                userListModel.addElement(u);
            }
        }

        void show() {
            dialog = new JDialog(owner, "Usuarios e privilegios do servidor", Dialog.ModalityType.APPLICATION_MODAL);
            dialog.setLayout(new BorderLayout());
            dialog.add(buildUserListPanel(), BorderLayout.WEST);
            dialog.add(buildRightSide(), BorderLayout.CENTER);
            dialog.add(buildFooter(), BorderLayout.SOUTH);
            dialog.getRootPane().registerKeyboardAction(e -> dialog.dispose(),
                    KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
            dialog.setSize(940, 620);
            dialog.setLocationRelativeTo(owner);
            if (!userListModel.isEmpty()) {
                userList.setSelectedIndex(0);
            }
            onUserSelectionChanged();
            dialog.setVisible(true);
        }

        // ---------- Lista de usuarios (esquerda) ----------

        private JComponent buildUserListPanel() {
            userList = new JList<>(userListModel);
            userList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            userList.addListSelectionListener(e -> {
                if (!e.getValueIsAdjusting()) {
                    onUserSelectionChanged();
                }
            });
            JScrollPane scroll = new JScrollPane(userList);
            scroll.setPreferredSize(new Dimension(220, 400));

            JButton addBtn = new JButton("+ Novo usuario...");
            JButton removeBtn = new JButton("- Excluir");
            JButton refreshBtn = new JButton("Atualizar lista");
            Buttons.styleSecondary(addBtn);
            Buttons.styleSecondary(removeBtn);
            Buttons.styleSecondary(refreshBtn);
            addBtn.addActionListener(a -> onNewUser());
            removeBtn.addActionListener(a -> onDeleteUser());
            refreshBtn.addActionListener(a -> refreshUserList(null));

            JPanel buttons = new JPanel(new GridLayout(3, 1, 0, 4));
            buttons.add(addBtn);
            buttons.add(removeBtn);
            buttons.add(refreshBtn);

            JPanel panel = new JPanel(new BorderLayout(0, 8));
            panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 6));
            JLabel title = Typography.sectionHeader("USUARIOS");
            panel.add(title, BorderLayout.NORTH);
            panel.add(scroll, BorderLayout.CENTER);
            panel.add(buttons, BorderLayout.SOUTH);
            return panel;
        }

        private DbUserInfo selectedUser() {
            return userList.getSelectedValue();
        }

        private void onUserSelectionChanged() {
            DbUserInfo u = selectedUser();
            boolean has = u != null;
            selectedUserLabel.setText(has ? "Usuario selecionado: " + u.label() : "Nenhum usuario selecionado");
            lockedCheckbox.setSelected(has && u.accountLocked());
            lockedCheckbox.setEnabled(has);
            newPasswordField.setEnabled(has);
            userTabs.setEnabled(has);
            grantsArea.setText("");
            rolesListModel.clear();
            if (has && userTabs.getSelectedIndex() == grantsTabIndex) {
                refreshGrants();
            }
        }

        // ---------- Lado direito: rotulo + abas ----------

        private JComponent buildRightSide() {
            selectedUserLabel = new JLabel("Nenhum usuario selecionado");
            selectedUserLabel.setFont(selectedUserLabel.getFont().deriveFont(java.awt.Font.BOLD));
            selectedUserLabel.setBorder(BorderFactory.createEmptyBorder(10, 10, 6, 10));

            userTabs = new JTabbedPane();
            userTabs.addTab("Geral", buildGeneralTab());
            userTabs.addTab("Privilegios", buildPrivilegesTab());
            grantsTabIndex = userTabs.getTabCount();
            userTabs.addTab("Ver SHOW GRANTS", buildGrantsTab());
            userTabs.addTab("Roles", buildRolesTab());
            userTabs.addChangeListener(e -> {
                if (selectedUser() == null) {
                    return;
                }
                if (userTabs.getSelectedIndex() == grantsTabIndex) {
                    refreshGrants();
                }
            });

            JPanel panel = new JPanel(new BorderLayout());
            panel.add(selectedUserLabel, BorderLayout.NORTH);
            panel.add(userTabs, BorderLayout.CENTER);
            return panel;
        }

        private int grantsTabIndex;

        // ---------- Aba: Geral ----------

        private JComponent buildGeneralTab() {
            JPanel panel = new JPanel(new GridBagLayout());
            panel.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
            GridBagConstraints c = new GridBagConstraints();
            c.insets = new Insets(4, 4, 4, 4);
            c.anchor = GridBagConstraints.WEST;

            c.gridx = 0;
            c.gridy = 0;
            panel.add(new JLabel("Nova senha:"), c);
            c.gridx = 1;
            c.fill = GridBagConstraints.HORIZONTAL;
            c.weightx = 1;
            newPasswordField = new JPasswordField(22);
            panel.add(newPasswordField, c);
            c.gridx = 2;
            c.weightx = 0;
            c.fill = GridBagConstraints.NONE;
            JButton setPassword = new JButton("Definir senha");
            Buttons.styleSecondary(setPassword);
            setPassword.addActionListener(a -> onSetPassword());
            panel.add(setPassword, c);

            c.gridx = 0;
            c.gridy = 1;
            c.gridwidth = 2;
            lockedCheckbox = new JCheckBox("Conta bloqueada (login recusado, usuario/privilegios preservados)");
            panel.add(lockedCheckbox, c);
            c.gridx = 2;
            c.gridwidth = 1;
            JButton applyLock = new JButton("Aplicar");
            Buttons.styleSecondary(applyLock);
            applyLock.addActionListener(a -> onApplyLock());
            panel.add(applyLock, c);

            c.gridx = 0;
            c.gridy = 2;
            c.gridwidth = 2;
            panel.add(new JLabel("Forcar troca de senha no proximo login:"), c);
            c.gridx = 2;
            c.gridwidth = 1;
            JButton expireBtn = new JButton("Expirar agora");
            Buttons.styleSecondary(expireBtn);
            expireBtn.addActionListener(a -> onExpirePassword());
            panel.add(expireBtn, c);

            JPanel wrap = new JPanel(new BorderLayout());
            wrap.add(panel, BorderLayout.NORTH);
            return wrap;
        }

        private void onSetPassword() {
            DbUserInfo u = requireSelected();
            if (u == null) {
                return;
            }
            String pw = new String(newPasswordField.getPassword());
            if (pw.isBlank()) {
                JOptionPane.showMessageDialog(dialog, "Informe a nova senha.", "Usuarios e privilegios",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }
            String sql = dialect.setPasswordStatement(u.user(), u.host(), pw);
            runner.run(List.of(sql), () -> {
                newPasswordField.setText("");
                info("Senha de " + u.label() + " atualizada.");
            }, ex -> error("Falha ao trocar a senha", ex));
        }

        private void onApplyLock() {
            DbUserInfo u = requireSelected();
            if (u == null) {
                return;
            }
            boolean lock = lockedCheckbox.isSelected();
            String sql = dialect.lockUserStatement(u.user(), u.host(), lock);
            runner.run(List.of(sql), () -> {
                info((lock ? "Conta de " : "Conta de ") + u.label() + (lock ? " bloqueada." : " desbloqueada."));
                refreshUserList(u);
            }, ex -> error("Falha ao " + (lock ? "bloquear" : "desbloquear") + " a conta", ex));
        }

        private void onExpirePassword() {
            DbUserInfo u = requireSelected();
            if (u == null) {
                return;
            }
            String sql = dialect.expirePasswordStatement(u.user(), u.host());
            runner.run(List.of(sql), () -> info("Senha de " + u.label() + " marcada como expirada."),
                    ex -> error("Falha ao expirar a senha", ex));
        }

        // ---------- Aba: Privilegios ----------

        private JComponent buildPrivilegesTab() {
            JPanel panel = new JPanel(new BorderLayout(0, 10));
            panel.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));

            JPanel form = new JPanel(new GridBagLayout());
            GridBagConstraints c = new GridBagConstraints();
            c.insets = new Insets(3, 4, 3, 4);
            c.anchor = GridBagConstraints.WEST;

            c.gridx = 0;
            c.gridy = 0;
            form.add(new JLabel("Nivel:"), c);
            c.gridx = 1;
            c.fill = GridBagConstraints.HORIZONTAL;
            c.weightx = 1;
            levelCombo = new JComboBox<>(new String[] { LEVEL_GLOBAL, LEVEL_SCHEMA, LEVEL_TABLE });
            levelCombo.addActionListener(a -> onLevelChanged());
            form.add(levelCombo, c);

            c.gridx = 0;
            c.gridy = 1;
            c.weightx = 0;
            c.fill = GridBagConstraints.NONE;
            form.add(new JLabel("Schema:"), c);
            c.gridx = 1;
            c.fill = GridBagConstraints.HORIZONTAL;
            c.weightx = 1;
            schemaCombo = new JComboBox<>(schemaNames.toArray(new String[0]));
            if (currentSchemaName != null) {
                schemaCombo.setSelectedItem(currentSchemaName);
            }
            schemaCombo.addActionListener(a -> onSchemaChanged());
            form.add(schemaCombo, c);

            c.gridx = 0;
            c.gridy = 2;
            c.weightx = 0;
            c.fill = GridBagConstraints.NONE;
            form.add(new JLabel("Tabela:"), c);
            c.gridx = 1;
            c.fill = GridBagConstraints.HORIZONTAL;
            c.weightx = 1;
            tableCombo = new JComboBox<>();
            tableCombo.setEditable(true);
            tableCombo.setToolTipText(
                    "Populado automaticamente so para o schema atualmente aberto na janela principal; "
                            + "para qualquer outro schema, digite o nome da tabela.");
            form.add(tableCombo, c);

            c.gridx = 0;
            c.gridy = 3;
            c.weightx = 0;
            c.fill = GridBagConstraints.NONE;
            form.add(new JLabel("Coluna(s) (opcional):"), c);
            c.gridx = 1;
            c.fill = GridBagConstraints.HORIZONTAL;
            c.weightx = 1;
            columnsField = new JTextField();
            columnsField.setToolTipText("Nomes separados por virgula — deixe em branco para conceder na tabela inteira. "
                    + "So um subconjunto de privilegios aceita nivel de coluna (ver lista abaixo).");
            columnsField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
                @Override
                public void insertUpdate(javax.swing.event.DocumentEvent e) {
                    rebuildPrivilegeChecks();
                }

                @Override
                public void removeUpdate(javax.swing.event.DocumentEvent e) {
                    rebuildPrivilegeChecks();
                }

                @Override
                public void changedUpdate(javax.swing.event.DocumentEvent e) {
                    rebuildPrivilegeChecks();
                }
            });
            form.add(columnsField, c);

            privilegeCheckPanel = new JPanel(new GridLayout(0, 3, 6, 2));
            JScrollPane checkScroll = new JScrollPane(privilegeCheckPanel);
            checkScroll.setBorder(BorderFactory.createTitledBorder("Privilegios"));
            checkScroll.setPreferredSize(new Dimension(600, 180));

            grantOptionCheckbox = new JCheckBox("Conceder tambem WITH GRANT OPTION (permite repassar estes privilegios a outros)");

            JButton grantBtn = new JButton("Conceder");
            JButton revokeBtn = new JButton("Revogar");
            Buttons.stylePrimary(grantBtn);
            Buttons.styleSecondary(revokeBtn);
            grantBtn.addActionListener(a -> onGrant());
            revokeBtn.addActionListener(a -> onRevoke());
            JPanel actionRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 6));
            actionRow.add(grantBtn);
            actionRow.add(revokeBtn);

            JPanel south = new JPanel(new BorderLayout());
            south.add(grantOptionCheckbox, BorderLayout.NORTH);
            south.add(actionRow, BorderLayout.SOUTH);

            panel.add(form, BorderLayout.NORTH);
            panel.add(checkScroll, BorderLayout.CENTER);
            panel.add(south, BorderLayout.SOUTH);

            // schemaCombo.setSelectedItem(currentSchemaName) acima ja rodou
            // ANTES do actionListener ser registrado (nao ha evento pra
            // pegar essa selecao inicial) — chama os dois a mao uma vez para
            // o combo de tabela/checkboxes nascerem coerentes com o nivel e
            // schema ja pre-selecionados, sem esperar o usuario mexer.
            onSchemaChanged();
            onLevelChanged();
            return panel;
        }

        private void onLevelChanged() {
            String level = (String) levelCombo.getSelectedItem();
            boolean schemaLevel = LEVEL_SCHEMA.equals(level) || LEVEL_TABLE.equals(level);
            boolean tableLevel = LEVEL_TABLE.equals(level);
            schemaCombo.setEnabled(schemaLevel);
            tableCombo.setEnabled(tableLevel);
            columnsField.setEnabled(tableLevel);
            rebuildPrivilegeChecks();
        }

        private void onSchemaChanged() {
            String schema = (String) schemaCombo.getSelectedItem();
            tableCombo.removeAllItems();
            if (schema != null && schema.equals(currentSchemaName)) {
                for (String t : currentSchemaTables) {
                    tableCombo.addItem(t);
                }
            }
        }

        /** Reconstroi as caixinhas de privilegio de acordo com o nivel (e se ha coluna preenchida). */
        private void rebuildPrivilegeChecks() {
            String level = (String) levelCombo.getSelectedItem();
            List<String> names;
            if (LEVEL_GLOBAL.equals(level)) {
                names = dialect.globalPrivilegeNames();
            } else if (LEVEL_TABLE.equals(level) && !columnsField.getText().isBlank()) {
                names = dialect.columnPrivilegeNames();
            } else {
                names = dialect.objectPrivilegeNames();
            }
            privilegeChecks.clear();
            privilegeCheckPanel.removeAll();
            for (String name : names) {
                JCheckBox box = new JCheckBox(name);
                privilegeChecks.put(name, box);
                privilegeCheckPanel.add(box);
            }
            privilegeCheckPanel.revalidate();
            privilegeCheckPanel.repaint();
        }

        private List<String> selectedPrivileges() {
            List<String> out = new ArrayList<>();
            for (Map.Entry<String, JCheckBox> e : privilegeChecks.entrySet()) {
                if (e.getValue().isSelected()) {
                    out.add(e.getKey());
                }
            }
            return out;
        }

        private String currentTarget() {
            String level = (String) levelCombo.getSelectedItem();
            if (LEVEL_GLOBAL.equals(level)) {
                return dialect.privilegeTarget(null, null, null);
            }
            String schema = (String) schemaCombo.getSelectedItem();
            if (LEVEL_SCHEMA.equals(level)) {
                return dialect.privilegeTarget(schema, null, null);
            }
            String table = tableCombo.getEditor().getItem() == null ? "" : tableCombo.getEditor().getItem().toString().trim();
            List<String> cols = splitCsv(columnsField.getText());
            return dialect.privilegeTarget(schema, table, cols);
        }

        private void onGrant() {
            DbUserInfo u = requireSelected();
            if (u == null) {
                return;
            }
            List<String> privileges = selectedPrivileges();
            if (privileges.isEmpty()) {
                JOptionPane.showMessageDialog(dialog, "Marque ao menos um privilegio.", "Usuarios e privilegios",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }
            String target = currentTarget();
            String sql = dialect.grantStatement(privileges, target, u.user(), u.host(), grantOptionCheckbox.isSelected());
            runner.run(List.of(sql), () -> info("Privilegios concedidos a " + u.label() + " em " + target + "."),
                    ex -> error("Falha ao conceder privilegios", ex));
        }

        private void onRevoke() {
            DbUserInfo u = requireSelected();
            if (u == null) {
                return;
            }
            List<String> privileges = selectedPrivileges();
            if (privileges.isEmpty()) {
                JOptionPane.showMessageDialog(dialog, "Marque ao menos um privilegio.", "Usuarios e privilegios",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }
            String target = currentTarget();
            String sql = dialect.revokeStatement(privileges, target, u.user(), u.host());
            runner.run(List.of(sql), () -> info("Privilegios revogados de " + u.label() + " em " + target + "."),
                    ex -> error("Falha ao revogar privilegios", ex));
        }

        // ---------- Aba: Ver SHOW GRANTS ----------

        private JComponent buildGrantsTab() {
            JPanel panel = new JPanel(new BorderLayout(0, 8));
            panel.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
            grantsArea = new JTextArea();
            grantsArea.setEditable(false);
            grantsArea.setFont(SqlEditorPane.monospaceFont(13));
            grantsArea.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
            JButton refresh = new JButton("Atualizar");
            Buttons.styleSecondary(refresh);
            refresh.addActionListener(a -> refreshGrants());
            JPanel south = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
            south.add(refresh);
            panel.add(new JScrollPane(grantsArea), BorderLayout.CENTER);
            panel.add(south, BorderLayout.SOUTH);
            return panel;
        }

        private void refreshGrants() {
            DbUserInfo u = selectedUser();
            if (u == null) {
                return;
            }
            grantsArea.setText("Carregando...");
            String sql = dialect.showGrantsQuery(u.user(), u.host());
            queryRunner.query(sql, rows -> {
                StringBuilder sb = new StringBuilder();
                for (Object[] row : rows) {
                    sb.append(row.length > 0 ? String.valueOf(row[0]) : "").append(";\n");
                }
                grantsArea.setText(sb.length() > 0 ? sb.toString() : "(nenhum privilegio concedido)");
                grantsArea.setCaretPosition(0);
            }, ex -> {
                grantsArea.setText("");
                error("Falha ao ler SHOW GRANTS (verifique se a conexao tem privilegio para ver os grants deste usuario)", ex);
            });
        }

        // ---------- Aba: Roles ----------

        private JComponent buildRolesTab() {
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
            refreshRoles.addActionListener(a -> refreshRoles());
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

        private void refreshRoles() {
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
                refreshRoles();
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
                refreshRoles();
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

        // ---------- Novo/excluir usuario ----------

        private void onNewUser() {
            JTextField userField = new JTextField(18);
            JTextField hostField = new JTextField("%", 18);
            JPasswordField pwField = new JPasswordField(18);
            JCheckBox expireBox = new JCheckBox("Forcar troca de senha no primeiro login", true);

            JPanel form = new JPanel(new GridBagLayout());
            GridBagConstraints c = new GridBagConstraints();
            c.insets = new Insets(3, 4, 3, 4);
            c.anchor = GridBagConstraints.WEST;
            c.gridx = 0;
            c.gridy = 0;
            form.add(new JLabel("Usuario:"), c);
            c.gridx = 1;
            form.add(userField, c);
            c.gridx = 0;
            c.gridy = 1;
            form.add(new JLabel("Host (% = qualquer):"), c);
            c.gridx = 1;
            form.add(hostField, c);
            c.gridx = 0;
            c.gridy = 2;
            form.add(new JLabel("Senha:"), c);
            c.gridx = 1;
            form.add(pwField, c);
            c.gridx = 0;
            c.gridy = 3;
            c.gridwidth = 2;
            form.add(expireBox, c);

            int result = JOptionPane.showConfirmDialog(dialog, form, "Novo usuario", JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.PLAIN_MESSAGE);
            if (result != JOptionPane.OK_OPTION) {
                return;
            }
            String user = userField.getText().trim();
            String host = hostField.getText().trim();
            String pw = new String(pwField.getPassword());
            if (user.isEmpty() || host.isEmpty() || pw.isEmpty()) {
                JOptionPane.showMessageDialog(dialog, "Preencha usuario, host e senha.", "Novo usuario",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }
            String sql = dialect.createUserStatement(user, host, pw, expireBox.isSelected());
            runner.run(List.of(sql), () -> {
                info("Usuario \"" + user + "@" + host + "\" criado.");
                refreshUserList(new DbUserInfo(user, host, false, expireBox.isSelected()));
            }, ex -> error("Falha ao criar o usuario", ex));
        }

        private void onDeleteUser() {
            DbUserInfo u = requireSelected();
            if (u == null) {
                return;
            }
            if (!confirm("Excluir o usuario \"" + u.label() + "\"? Isto remove todos os privilegios/roles dele junto.")) {
                return;
            }
            String sql = dialect.dropUserStatement(u.user(), u.host());
            runner.run(List.of(sql), () -> {
                info("Usuario \"" + u.label() + "\" excluido.");
                refreshUserList(null);
            }, ex -> error("Falha ao excluir o usuario", ex));
        }

        /** Reconsulta {@code listUsersQuery()} e repopula a lista; tenta reselecionar {@code toReselect} (por user+host), se informado. */
        private void refreshUserList(DbUserInfo toReselect) {
            queryRunner.query(dialect.listUsersQuery(), rows -> {
                userListModel.clear();
                DbUserInfo reselect = null;
                for (Object[] row : rows) {
                    String user = String.valueOf(row[0]);
                    String host = String.valueOf(row[1]);
                    boolean locked = row.length > 2 && "Y".equalsIgnoreCase(String.valueOf(row[2]));
                    boolean expired = row.length > 3 && "Y".equalsIgnoreCase(String.valueOf(row[3]));
                    DbUserInfo parsed = new DbUserInfo(user, host, locked, expired);
                    userListModel.addElement(parsed);
                    if (toReselect != null && user.equals(toReselect.user()) && host.equals(toReselect.host())) {
                        reselect = parsed;
                    }
                }
                if (reselect != null) {
                    userList.setSelectedValue(reselect, true);
                } else {
                    onUserSelectionChanged();
                }
            }, ex -> error("Falha ao listar usuarios (a conexao pode nao ter privilegio para ler mysql.user)", ex));
        }

        // ---------- Auxiliares ----------

        private DbUserInfo requireSelected() {
            DbUserInfo u = selectedUser();
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

        // ---------- Rodape ----------

        private JComponent buildFooter() {
            JButton close = new JButton("Fechar");
            Buttons.styleSecondary(close);
            close.addActionListener(a -> dialog.dispose());
            JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 8));
            panel.setBorder(BorderFactory.createEmptyBorder(0, 10, 4, 10));
            panel.add(close);
            return panel;
        }
    }
}
