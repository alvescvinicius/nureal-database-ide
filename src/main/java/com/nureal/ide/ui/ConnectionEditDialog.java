package com.nureal.ide.ui;

import com.nureal.ide.core.connection.ConnectionProfile;

import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.function.Predicate;

/**
 * Formulario para criar ou editar uma conexao. Retorna null se cancelar.
 */
public final class ConnectionEditDialog {

    private ConnectionEditDialog() {
    }

    /**
     * existing == null cria nova; caso contrario, edita.
     *
     * @param nameTaken avaliado com o nome (ja resolvido/trim) que o usuario
     *                  esta prestes a salvar; se retornar {@code true}
     *                  (ja existe OUTRA conexao com esse nome), o formulario
     *                  mostra um aviso e permanece aberto para o usuario
     *                  corrigir, em vez de fechar e deixar duas conexoes com
     *                  o mesmo nome — o que alem de confundir na lista,
     *                  quebra o indicador de "conectado" (ConnectionsPanel
     *                  guarda conexoes ativas num Set&lt;String&gt; DE NOMES:
     *                  duas conexoes com o mesmo nome aparecem ambas como
     *                  conectadas quando so uma esta).
     */
    public static ConnectionProfile show(Component parent, ConnectionProfile existing, Predicate<String> nameTaken) {
        ConnectionProfile base = (existing != null) ? existing : ConnectionProfile.mysqlDefault();

        JTextField name = new JTextField(base.name(), 22);
        JTextField host = new JTextField(base.host(), 22);
        JTextField port = new JTextField(String.valueOf(base.port()), 22);
        JTextField schema = new JTextField(base.schema(), 22);
        JTextField user = new JTextField(base.user(), 22);
        JPasswordField password = new JPasswordField(base.password(), 22);
        // Texto corrigido: a senha (junto com o resto do arquivo de conexoes)
        // e cifrada de verdade com AES-256/GCM pelo LocalVault antes de ir
        // para o disco (ver ConnectionStore) — a rotulagem antiga
        // ("ofuscada, nao criptografada") datava de um prototipo anterior a
        // isso e ficou desatualizada, subestimando a seguranca real.
        JCheckBox savePassword = new JCheckBox("Salvar senha (criptografada com AES-256)",
                base.savePassword());

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.anchor = GridBagConstraints.WEST;

        int row = 0;
        addRow(form, c, row++, "Nome:", name);
        addRow(form, c, row++, "Host:", host);
        addRow(form, c, row++, "Porta:", port);
        addRow(form, c, row++, "Schema (vazio = listar todos):", schema);
        addRow(form, c, row++, "Usuario:", user);
        addRow(form, c, row++, "Senha:", password);

        c.gridx = 1;
        c.gridy = row;
        form.add(savePassword, c);

        String title = (existing == null) ? "Nova conexao" : "Editar conexao";
        Component owner = DialogUtil.owner(parent);

        // Loop em vez de um showConfirmDialog unico: se o nome digitado ja
        // estiver em uso, avisa e REABRE o mesmo formulario (campos mantem o
        // que o usuario ja tinha digitado) para corrigir, em vez de fechar e
        // deixar duas conexoes com o mesmo nome.
        while (true) {
            // Centraliza na JANELA do chamador, nao no componente exato
            // passado (ex: ConnectionsPanel, um painel pequeno na lateral).
            int result = JOptionPane.showConfirmDialog(
                    owner, form, title, JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (result != JOptionPane.OK_OPTION) {
                return null;
            }

            int portValue;
            try {
                portValue = Integer.parseInt(port.getText().trim());
            } catch (NumberFormatException e) {
                portValue = 3306;
            }

            String connName = name.getText().trim();
            if (connName.isEmpty()) {
                connName = host.getText().trim() + "/" + schema.getText().trim();
            }

            if (nameTaken != null && nameTaken.test(connName)) {
                JOptionPane.showMessageDialog(owner,
                        "Ja existe uma conexao chamada \"" + connName + "\".\nEscolha outro nome.",
                        "Nome duplicado", JOptionPane.WARNING_MESSAGE);
                continue;
            }

            return new ConnectionProfile(
                    connName,
                    host.getText().trim(),
                    portValue,
                    schema.getText().trim(),
                    user.getText().trim(),
                    new String(password.getPassword()),
                    savePassword.isSelected());
        }
    }

    private static void addRow(JPanel form, GridBagConstraints c, int row, String label, Component field) {
        c.gridx = 0;
        c.gridy = row;
        c.weightx = 0;
        form.add(new JLabel(label), c);
        c.gridx = 1;
        c.weightx = 1;
        form.add(field, c);
    }
}
