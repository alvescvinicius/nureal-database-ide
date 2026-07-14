package com.nureal.ide.ui;

import com.nureal.ide.core.connection.ConnectionManager;
import com.nureal.ide.core.connection.ConnectionProfile;
import com.nureal.ide.core.dialect.MySqlDialect;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JDialog;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.sql.SQLException;
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
        c.gridy = row++;
        form.add(savePassword, c);

        // Botao "Testar conexao": abre uma conexao de verdade com os dados
        // JA DIGITADOS no formulario (sem precisar salvar antes) e mostra o
        // resultado ao lado, sem fechar o dialogo — pedido explicito para
        // conferir host/porta/usuario/senha antes de confirmar. So MySQL por
        // enquanto (mesmo dialeto unico usado no resto do app hoje, ver
        // MainWindow#dialect); quando entrar um segundo banco, este dialeto
        // fixo vira um parametro escolhido pelo usuario no formulario.
        //
        // Largura do rotulo de status TRAVADA em HTML (ver #htmlStatus) —
        // sem isto, o texto ("Testando conexao...", ou pior, uma mensagem de
        // erro de dezenas de caracteres tipo "Access denied for user...")
        // cresce DEPOIS que o JOptionPane ja calculou o tamanho da janela
        // (pack() so acontece uma vez, ao abrir; nao ha auto-resize quando um
        // filho muda de tamanho depois). O GridBagLayout, ao tentar encaixar
        // essa largura nova SEM a janela crescer junto, espreme a coluna
        // inteira (a MESMA dos campos Nome/Host/Porta/...) ate o minimo —
        // bug relatado pelo usuario: campos viram caixinhas vazias ao clicar
        // em "Testar conexao". Com largura fixa, o texto QUEBRA LINHA (altera
        // altura, nao largura) em vez de forcar a coluna a crescer.
        JButton testButton = new JButton("Testar conexao");
        JLabel testStatus = new JLabel(htmlStatus(" "));
        testStatus.setFont(testStatus.getFont().deriveFont(Font.PLAIN, 11f));

        JPanel testRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        testRow.setOpaque(false);
        testRow.add(testButton);
        testRow.add(testStatus);

        testButton.addActionListener(e -> {
            testButton.setEnabled(false);
            testStatus.setForeground(GridTheme.MUTED_TEXT);
            testStatus.setText(htmlStatus("Testando conexao..."));
            repackDialog(testRow);

            ConnectionProfile candidate = new ConnectionProfile(
                    "test", host.getText().trim(), parsePort(port), schema.getText().trim(),
                    user.getText().trim(), new String(password.getPassword()), false);

            new SwingWorker<String, Void>() {
                @Override
                protected String doInBackground() {
                    try {
                        ConnectionManager.testConnection(new MySqlDialect(), candidate);
                        return null;
                    } catch (SQLException ex) {
                        return ex.getMessage();
                    }
                }

                @Override
                protected void done() {
                    String error;
                    try {
                        error = get();
                    } catch (Exception ex) {
                        error = ex.getMessage();
                    }
                    if (error == null) {
                        testStatus.setForeground(GridTheme.COLOR_LOGIC_TRUE);
                        testStatus.setText(htmlStatus("Conexao bem-sucedida"));
                    } else {
                        testStatus.setForeground(GridTheme.COLOR_LOGIC_FALSE);
                        testStatus.setText(htmlStatus("Falha: " + error));
                    }
                    testButton.setEnabled(true);
                    repackDialog(testRow);
                }
            }.execute();
        });

        c.gridx = 0;
        c.gridy = row++;
        c.gridwidth = 2;
        form.add(testRow, c);
        c.gridwidth = 1;

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
            // Limpa o destaque de erro de uma tentativa anterior antes de
            // validar de novo — sem isto o campo ficava "vermelho" para
            // sempre mesmo depois do usuario corrigir o nome.
            name.putClientProperty("JComponent.outline", null);

            int portValue = parsePort(port);

            String connName = name.getText().trim();
            if (connName.isEmpty()) {
                connName = host.getText().trim() + "/" + schema.getText().trim();
            }

            if (nameTaken != null && nameTaken.test(connName)) {
                JOptionPane.showMessageDialog(owner,
                        "Ja existe uma conexao chamada \"" + connName + "\".\nEscolha outro nome.",
                        "Nome duplicado", JOptionPane.WARNING_MESSAGE);
                // Destaque de erro nativo do FlatLaf (contorno vermelho) no
                // campo especifico que precisa ser corrigido — alem do aviso
                // em popup, agora fica claro qual campo esta errado quando o
                // formulario reabre (estado "erro" pedido na revisao visual
                // de inputs; antes so existia normal/foco/desabilitado).
                name.putClientProperty("JComponent.outline", "error");
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

    /**
     * Reempacota a janela do dialogo apos o texto do status mudar de altura
     * (ex.: mensagem de erro que ocupa 2-3 linhas dentro da largura fixa de
     * {@link #htmlStatus} — ver o comentario la sobre a largura). O
     * {@code JOptionPane} so calcula o tamanho da janela UMA VEZ, ao abrir
     * (pack() interno) — sem chamar {@code pack()} de novo aqui, o dialogo
     * fica com a MESMA altura de antes do teste, e o painel de mensagem
     * (agora mais alto) empurra/corta a barra de botoes OK/Cancelar, que fica
     * cortada embaixo (bug relatado pelo usuario). Redimensiona so a JANELA
     * do dialogo (nunca a janela PRINCIPAL do app por engano) — {@code
     * SwingUtilities.getWindowAncestor} a partir de um componente que esta
     * DENTRO do formulario do JOptionPane sempre resolve para o JDialog
     * interno que o proprio JOptionPane cria, nao para a janela do
     * ConnectionsPanel/MainWindow que abriu o formulario.
     */
    private static void repackDialog(Component insideDialog) {
        Window window = SwingUtilities.getWindowAncestor(insideDialog);
        if (window instanceof JDialog dialog) {
            dialog.pack();
        }
    }

    /**
     * Envolve {@code text} em HTML com largura FIXA (280px) — ver o
     * comentario no botao "Testar conexao" sobre por que a largura precisa
     * ser travada (senao o GridBagLayout espreme os campos do formulario ate
     * o minimo quando o texto do status cresce depois que o dialogo ja foi
     * dimensionado). Mensagens mais longas que 280px QUEBRAM LINHA (o
     * dialogo cresce em altura, nunca em largura) em vez de alargar a
     * coluna. Escapa os 3 caracteres especiais de HTML — a mensagem pode vir
     * de uma SQLException do driver JDBC, texto fora do nosso controle.
     */
    private static String htmlStatus(String text) {
        String escaped = text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
        return "<html><div style='width:280px'>" + escaped + "</div></html>";
    }

    /** Porta digitada, com 3306 (padrao MySQL) como fallback se nao for um numero valido. */
    private static int parsePort(JTextField port) {
        try {
            return Integer.parseInt(port.getText().trim());
        } catch (NumberFormatException e) {
            return 3306;
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
