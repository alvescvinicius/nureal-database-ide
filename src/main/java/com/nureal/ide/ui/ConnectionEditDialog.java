package com.nureal.ide.ui;
import com.nureal.ide.compartilhado.designsystem.Buttons;
import com.nureal.ide.compartilhado.designsystem.GridTheme;
import com.nureal.ide.compartilhado.designsystem.Spacing;
import com.nureal.ide.compartilhado.designsystem.Typography;
import com.nureal.ide.compartilhado.designsystem.dialog.DialogShell;

import com.nureal.ide.modulos.conexoes.infraestrutura.ConnectionManager;
import com.nureal.ide.modulos.conexoes.dominio.entidades.ConnectionProfile;
import com.nureal.ide.modulos.dialeto.infraestrutura.MySqlDialect;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
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
 * Formulario para criar ou editar uma conexao. Retorna {@code null} se
 * cancelar.
 * <p>
 * Reescrito como {@link JDialog} de verdade via {@link DialogShell} (nao
 * mais um {@code JOptionPane} generico) — mesmo motivo documentado em
 * {@code MainWindow#showConnectionsPopup}: um {@code JOptionPane} some
 * sozinho quando o {@code JPopupMenu} que o hospedava perde o foco pra ele
 * mesmo, e nao redimensiona direito quando o texto do "Testar conexao"
 * cresce. Ganha de graca, so por usar {@link DialogShell}: Esc fecha o
 * dialogo (ver {@code DialogShell#create}).
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
        Window owner = (DialogUtil.owner(parent) instanceof Window w) ? w : null;
        String title = (existing == null) ? "Nova conexao" : "Editar conexao";

        DialogShell shell = DialogShell.create(owner, title, JDialog.ModalityType.APPLICATION_MODAL);
        JDialog dialog = shell.dialog();

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
        form.setBorder(BorderFactory.createEmptyBorder(Spacing.MD, Spacing.MD, Spacing.SM, Spacing.MD));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;

        int row = 0;
        addRow(form, c, row++, "Nome:", name);
        addRow(form, c, row++, "Host:", host);
        addRow(form, c, row++, "Porta:", port);
        addRow(form, c, row++, "Schema (vazio = listar todos):", schema);
        addRow(form, c, row++, "Usuario:", user);
        addRow(form, c, row++, "Senha:", password);

        c.gridx = 1;
        c.gridy = row++;
        c.weightx = 1;
        form.add(savePassword, c);

        JPanel testRow = buildTestRow(host, port, schema, user, password, dialog);
        c.gridx = 0;
        c.gridy = row++;
        c.gridwidth = 2;
        form.add(testRow, c);
        c.gridwidth = 1;

        // Guarda o resultado FORA do listener (a acao do botao roda depois
        // que setVisible(true) ja bloqueou aqui embaixo, ver o modal
        // ApplicationModal do DialogShell.create) — so preenchido quando a
        // validacao passa E o usuario confirma; cancelar/fechar deixa nulo.
        ConnectionProfile[] result = new ConnectionProfile[1];
        JButton save = new JButton("Salvar");
        save.addActionListener(e -> {
            ConnectionProfile resolved = validateAndBuild(
                    dialog, name, host, port, schema, user, password, savePassword, nameTaken);
            if (resolved != null) {
                result[0] = resolved;
                dialog.dispose();
            }
        });

        shell.center(form);
        shell.south(Buttons.dialogFooter(dialog, save));

        dialog.pack();
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);
        return result[0];
    }

    /**
     * Valida os campos e monta o {@link ConnectionProfile} final, ou
     * devolve {@code null} (e destaca o campo com problema + mostra o
     * aviso) sem fechar o dialogo, para o usuario corrigir.
     * <p>
     * Antes desta reescrita, host nunca era validado e uma porta invalida
     * (ex.: "abc") virava silenciosamente 3306 sem avisar o usuario — agora
     * os dois bloqueiam o salvamento com uma mensagem clara.
     */
    private static ConnectionProfile validateAndBuild(JDialog dialog, JTextField name, JTextField host,
            JTextField port, JTextField schema, JTextField user, JPasswordField password, JCheckBox savePassword,
            Predicate<String> nameTaken) {
        // Limpa destaques de erro de uma tentativa anterior antes de validar
        // de novo — sem isto um campo ficava "vermelho" para sempre mesmo
        // depois do usuario corrigir.
        name.putClientProperty("JComponent.outline", null);
        host.putClientProperty("JComponent.outline", null);
        port.putClientProperty("JComponent.outline", null);

        String hostValue = host.getText().trim();
        if (hostValue.isEmpty()) {
            host.putClientProperty("JComponent.outline", "error");
            host.requestFocusInWindow();
            JOptionPane.showMessageDialog(dialog, "Informe o host do servidor.",
                    "Campo obrigatorio", JOptionPane.WARNING_MESSAGE);
            return null;
        }

        Integer portValue = parsePortStrict(port.getText().trim());
        if (portValue == null) {
            port.putClientProperty("JComponent.outline", "error");
            port.requestFocusInWindow();
            JOptionPane.showMessageDialog(dialog,
                    "Porta invalida — use um numero entre 1 e 65535 (ou deixe em branco para usar 3306).",
                    "Porta invalida", JOptionPane.WARNING_MESSAGE);
            return null;
        }

        String connName = name.getText().trim();
        if (connName.isEmpty()) {
            connName = hostValue + "/" + schema.getText().trim();
        }

        if (nameTaken != null && nameTaken.test(connName)) {
            JOptionPane.showMessageDialog(dialog,
                    "Ja existe uma conexao chamada \"" + connName + "\".\nEscolha outro nome.",
                    "Nome duplicado", JOptionPane.WARNING_MESSAGE);
            // Destaque de erro nativo do FlatLaf (contorno vermelho) no
            // campo especifico que precisa ser corrigido — alem do aviso
            // em popup, fica claro qual campo esta errado.
            name.putClientProperty("JComponent.outline", "error");
            name.requestFocusInWindow();
            return null;
        }

        return new ConnectionProfile(
                connName,
                hostValue,
                portValue,
                schema.getText().trim(),
                user.getText().trim(),
                new String(password.getPassword()),
                savePassword.isSelected());
    }

    /**
     * Botao "Testar conexao": abre uma conexao de verdade com os dados JA
     * DIGITADOS no formulario (sem precisar salvar antes) e mostra o
     * resultado ao lado, sem fechar o dialogo — pedido explicito para
     * conferir host/porta/usuario/senha antes de confirmar. So MySQL por
     * enquanto (mesmo dialeto unico usado no resto do app hoje, ver
     * MainWindow#dialect); quando entrar um segundo banco, este dialeto fixo
     * vira um parametro escolhido pelo usuario no formulario.
     * <p>
     * Largura do rotulo de status TRAVADA em HTML (ver {@link #htmlStatus}) —
     * sem isto, o texto ("Testando conexao...", ou pior, uma mensagem de erro
     * de dezenas de caracteres tipo "Access denied for user...") cresce
     * DEPOIS do primeiro {@code pack()}, e o GridBagLayout, ao tentar encaixar
     * essa largura nova SEM a janela crescer junto, espreme a coluna inteira
     * (a MESMA dos campos Nome/Host/Porta/...) ate o minimo. Com largura
     * fixa, o texto QUEBRA LINHA (altera altura, nao largura) em vez de
     * forcar a coluna a crescer — {@link #repack} so precisa ajustar a
     * ALTURA, chamado direto no {@code dialog} de verdade (nao mais um
     * {@code SwingUtilities.getWindowAncestor} pra "adivinhar" a janela do
     * JOptionPane, ja que agora {@code dialog} e a propria janela).
     */
    private static JPanel buildTestRow(JTextField host, JTextField port, JTextField schema, JTextField user,
            JPasswordField password, JDialog dialog) {
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
            repack(dialog);

            ConnectionProfile candidate = new ConnectionProfile(
                    "test", host.getText().trim(), parsePortLenient(port), schema.getText().trim(),
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
                    repack(dialog);
                }
            }.execute();
        });
        return testRow;
    }

    /** Reempacota a janela do dialogo apos o texto do status mudar de altura — ver o comentario em {@link #buildTestRow}. */
    private static void repack(JDialog dialog) {
        dialog.pack();
    }

    /**
     * Envolve {@code text} em HTML com largura FIXA (280px) — ver o
     * comentario no botao "Testar conexao" sobre por que a largura precisa
     * ser travada. Mensagens mais longas que 280px QUEBRAM LINHA (o
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

    /** Porta digitada, com 3306 (padrao MySQL) como fallback se nao for um numero valido — so pro botao "Testar conexao" (nao bloqueia a experimentacao com um texto ainda incompleto). */
    private static int parsePortLenient(JTextField port) {
        Integer strict = parsePortStrict(port.getText().trim());
        return (strict != null) ? strict : 3306;
    }

    /** Porta digitada: vazio vira 3306 (padrao MySQL); texto presente mas invalido (nao numerico ou fora de 1-65535) devolve {@code null} — usado no SALVAR de verdade (ver {@link #validateAndBuild}), que rejeita em vez de aceitar silenciosamente algo que o usuario nao quis dizer. */
    private static Integer parsePortStrict(String text) {
        if (text.isEmpty()) {
            return 3306;
        }
        try {
            int value = Integer.parseInt(text);
            return (value >= 1 && value <= 65535) ? value : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static void addRow(JPanel form, GridBagConstraints c, int row, String label, Component field) {
        JLabel l = new JLabel(label);
        Typography.tertiary(l);
        c.gridx = 0;
        c.gridy = row;
        c.weightx = 0;
        form.add(l, c);
        c.gridx = 1;
        c.weightx = 1;
        form.add(field, c);
    }
}
