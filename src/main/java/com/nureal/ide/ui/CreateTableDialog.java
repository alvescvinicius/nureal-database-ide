package com.nureal.ide.ui;

import com.nureal.ide.core.metadata.model.NewColumnSpec;
import com.nureal.ide.core.metadata.model.NewTableSpec;

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
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Modal para criar uma tabela nova: nome da tabela + comentario + grade de
 * colunas (nome, tipo, tamanho, nulo, chave primaria, auto increment,
 * default, comentario), com botoes para adicionar/remover linhas. Retorna
 * {@code null} se o usuario cancelar. Nao monta SQL nenhum aqui — so
 * valida e coleta a especificacao (ver {@link NewTableSpec}); quem traduz
 * em DDL e o {@code DatabaseDialect} (ver
 * {@code MySqlDialect#createTableStatement}), chamado pelo {@code MainWindow}.
 */
final class CreateTableDialog {

    private static final String[] TYPES = {
            "VARCHAR", "CHAR", "TEXT", "MEDIUMTEXT", "LONGTEXT",
            "INT", "BIGINT", "SMALLINT", "TINYINT",
            "DECIMAL", "FLOAT", "DOUBLE",
            "DATE", "DATETIME", "TIMESTAMP", "TIME",
            "BOOLEAN", "JSON", "BLOB"
    };

    private static final String[] COLUMN_HEADERS =
            { "Nome", "Tipo", "Tamanho", "Nulo", "PK", "AI", "Default", "Comentario" };

    private static final Pattern IDENTIFIER = Pattern.compile("^[A-Za-z_][A-Za-z0-9_$]*$");

    private CreateTableDialog() {
    }

    /**
     * @param tableNameTaken avaliado com o nome (ja em trim) que o usuario
     *                       esta prestes a criar; se retornar {@code true}
     *                       (ja existe tabela/view com esse nome no schema
     *                       aberto), mostra um aviso e mantem o formulario
     *                       aberto para corrigir.
     */
    static NewTableSpec show(Component parent, Predicate<String> tableNameTaken) {
        Window owner = (DialogUtil.owner(parent) instanceof Window w) ? w : null;

        JTextField tableNameField = new JTextField(22);
        JTextField commentField = new JTextField(22);

        DefaultTableModel model = new DefaultTableModel(COLUMN_HEADERS, 0) {
            private static final long serialVersionUID = 1L;

            @Override
            public Class<?> getColumnClass(int columnIndex) {
                return (columnIndex == 3 || columnIndex == 4 || columnIndex == 5)
                        ? Boolean.class : String.class;
            }
        };
        addDefaultRow(model);

        JTable table = new JTable(model);
        table.setRowHeight(24);
        table.getColumnModel().getColumn(1).setCellEditor(new DefaultCellEditor(new JComboBox<>(TYPES)));
        table.getColumnModel().getColumn(0).setPreferredWidth(130);
        table.getColumnModel().getColumn(1).setPreferredWidth(110);
        table.getColumnModel().getColumn(2).setPreferredWidth(70);
        table.getColumnModel().getColumn(3).setPreferredWidth(45);
        table.getColumnModel().getColumn(4).setPreferredWidth(35);
        table.getColumnModel().getColumn(5).setPreferredWidth(35);
        table.getColumnModel().getColumn(6).setPreferredWidth(100);
        table.getColumnModel().getColumn(7).setPreferredWidth(140);
        table.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);

        JButton addRow = new JButton("+ Coluna");
        addRow.addActionListener(a -> {
            stopEditing(table);
            addDefaultRow(model);
            int last = model.getRowCount() - 1;
            table.setRowSelectionInterval(last, last);
            table.scrollRectToVisible(table.getCellRect(last, 0, true));
        });
        JButton removeRow = new JButton("- Coluna");
        removeRow.addActionListener(a -> {
            stopEditing(table);
            int[] rows = table.getSelectedRows();
            for (int i = rows.length - 1; i >= 0; i--) {
                model.removeRow(rows[i]);
            }
        });
        JPanel gridButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
        gridButtons.add(addRow);
        gridButtons.add(removeRow);

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.anchor = GridBagConstraints.WEST;
        c.gridx = 0;
        c.gridy = 0;
        form.add(new JLabel("Nome da tabela:"), c);
        c.gridx = 1;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        form.add(tableNameField, c);
        c.gridx = 0;
        c.gridy = 1;
        c.weightx = 0;
        c.fill = GridBagConstraints.NONE;
        form.add(new JLabel("Comentario:"), c);
        c.gridx = 1;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        form.add(commentField, c);

        JScrollPane scroll = new JScrollPane(table);
        scroll.setPreferredSize(new Dimension(660, 220));
        JPanel center = new JPanel(new BorderLayout());
        center.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
        center.add(scroll, BorderLayout.CENTER);
        center.add(gridButtons, BorderLayout.SOUTH);

        JButton ok = new JButton("Criar tabela");
        JButton cancel = new JButton("Cancelar");
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 6));
        buttons.add(cancel);
        buttons.add(ok);

        JPanel content = new JPanel(new BorderLayout(8, 8));
        content.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        content.add(form, BorderLayout.NORTH);
        content.add(center, BorderLayout.CENTER);
        content.add(buttons, BorderLayout.SOUTH);

        JDialog dialog = new JDialog(owner, "Nova tabela", Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setLayout(new BorderLayout());
        dialog.add(content, BorderLayout.CENTER);
        dialog.getRootPane().setDefaultButton(ok);
        dialog.getRootPane().registerKeyboardAction(e -> dialog.dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);

        NewTableSpec[] result = new NewTableSpec[1];
        ok.addActionListener(a -> {
            stopEditing(table);
            NewTableSpec spec = buildSpec(tableNameField, commentField, model, tableNameTaken, dialog);
            if (spec != null) {
                result[0] = spec;
                dialog.dispose();
            }
        });
        cancel.addActionListener(a -> dialog.dispose());

        dialog.pack();
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true); // bloqueia (modal) ate dispose()
        return result[0];
    }

    private static void addDefaultRow(DefaultTableModel model) {
        if (model.getRowCount() == 0) {
            // Primeira coluna: chute razoavel de PK auto-incremento, o caso
            // mais comum ao criar uma tabela do zero (estilo PL/SQL Developer).
            model.addRow(new Object[] { "id", "INT", "", Boolean.FALSE, Boolean.TRUE, Boolean.TRUE, "", "" });
        } else {
            model.addRow(new Object[] { "", "VARCHAR", "255", Boolean.TRUE, Boolean.FALSE, Boolean.FALSE, "", "" });
        }
    }

    private static void stopEditing(JTable table) {
        if (table.isEditing()) {
            table.getCellEditor().stopCellEditing();
        }
    }

    /** Valida os campos e monta a especificacao; retorna null (sem fechar o dialogo) se algo estiver invalido. */
    private static NewTableSpec buildSpec(JTextField tableNameField, JTextField commentField,
            DefaultTableModel model, Predicate<String> tableNameTaken, Component dialogOwner) {
        String tableName = tableNameField.getText().trim();
        if (tableName.isEmpty()) {
            warn(dialogOwner, "Informe o nome da tabela.");
            return null;
        }
        if (!IDENTIFIER.matcher(tableName).matches()) {
            warn(dialogOwner, "Nome de tabela invalido: \"" + tableName
                    + "\".\nUse letras, numeros e _ (nao pode comecar com numero).");
            return null;
        }
        if (tableNameTaken != null && tableNameTaken.test(tableName)) {
            warn(dialogOwner, "Ja existe uma tabela chamada \"" + tableName + "\".\nEscolha outro nome.");
            return null;
        }

        List<NewColumnSpec> columns = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int r = 0; r < model.getRowCount(); r++) {
            String name = str(model.getValueAt(r, 0)).trim();
            String type = str(model.getValueAt(r, 1)).trim();
            String length = str(model.getValueAt(r, 2)).trim();
            boolean nullable = bool(model.getValueAt(r, 3));
            boolean pk = bool(model.getValueAt(r, 4));
            boolean ai = bool(model.getValueAt(r, 5));
            String def = str(model.getValueAt(r, 6)).trim();
            String comment = str(model.getValueAt(r, 7)).trim();
            if (name.isEmpty() && type.isEmpty()) {
                continue; // linha em branco (sobrou de um "+ Coluna" nao usado): ignora
            }
            if (name.isEmpty()) {
                warn(dialogOwner, "Linha " + (r + 1) + ": informe o nome da coluna.");
                return null;
            }
            if (!IDENTIFIER.matcher(name).matches()) {
                warn(dialogOwner, "Nome de coluna invalido: \"" + name + "\".");
                return null;
            }
            if (!seen.add(name.toLowerCase(Locale.ROOT))) {
                warn(dialogOwner, "Coluna repetida: \"" + name + "\".");
                return null;
            }
            columns.add(new NewColumnSpec(name, type.isEmpty() ? "VARCHAR" : type, length, nullable, pk, ai, def,
                    comment));
        }
        if (columns.isEmpty()) {
            warn(dialogOwner, "Adicione pelo menos uma coluna.");
            return null;
        }
        return new NewTableSpec(tableName, columns, commentField.getText().trim());
    }

    private static void warn(Component owner, String message) {
        JOptionPane.showMessageDialog(owner, message, "Nova tabela", JOptionPane.WARNING_MESSAGE);
    }

    private static String str(Object v) {
        return v == null ? "" : v.toString();
    }

    private static boolean bool(Object v) {
        return v instanceof Boolean b && b;
    }
}
