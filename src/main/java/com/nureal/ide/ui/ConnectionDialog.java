package com.nureal.ide.ui;

import com.nureal.ide.core.connection.ConnectionProfile;

import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import java.awt.BorderLayout;
import java.awt.Component;

/**
 * Pede a senha em tempo de conexao, quando ela nao foi salva no perfil.
 * Retorna null se o usuario cancelar.
 */
public final class ConnectionDialog {

    private ConnectionDialog() {
    }

    public static String promptPassword(Component parent, ConnectionProfile profile) {
        JPasswordField password = new JPasswordField(20);

        JPanel panel = new JPanel(new BorderLayout(6, 6));
        panel.add(new JLabel("Senha para " + profile.user() + "@" + profile.host() + ":"),
                BorderLayout.NORTH);
        panel.add(password, BorderLayout.CENTER);

        // Centraliza na JANELA do chamador, nao no componente exato passado
        // (que pode ser um painel pequeno como ConnectionsPanel, nao a janela
        // inteira) — ver DialogUtil.
        int result = JOptionPane.showConfirmDialog(
                DialogUtil.owner(parent), panel, "Senha - " + profile.name(),
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result != JOptionPane.OK_OPTION) {
            return null;
        }
        return new String(password.getPassword());
    }
}
