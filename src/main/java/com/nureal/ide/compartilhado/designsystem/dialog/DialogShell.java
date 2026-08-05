package com.nureal.ide.compartilhado.designsystem.dialog;

import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.KeyStroke;
import java.awt.BorderLayout;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * Base comum para os 12+ dialogs do app (ver
 * .specs/12-interface-design-system-e-dialogos.md): elimina a duplicacao
 * mecanica de {@code new JDialog(owner, title, modality)} +
 * {@code setDefaultCloseOperation} + {@code setLayout(BorderLayout)} +
 * {@code setLocationRelativeTo(owner)} + {@code setVisible(true)} que hoje
 * cada dialog reimplementa na mao.
 *
 * <p>Nao resolve o "owner" sozinho (isso continua sendo
 * {@code ui.DialogUtil#owner}, ainda nao migrado para ca — ver README deste
 * pacote): quem chama {@link #create} ja passa a {@link Window} resolvida,
 * exatamente como cada dialog ja fazia antes. {@link DialogShell} so cuida
 * do que vem DEPOIS disso.
 *
 * <p>O rodape padrao de botoes OK/Cancelar (Fechar/acao primaria) ja existe
 * em {@code Buttons.dialogFooter(JDialog, JButton)} — {@link DialogShell}
 * nao duplica essa parte, so a construcao/ciclo de vida da propria janela.
 * Um ponto unico de exibicao de erro de validacao fica para quando um
 * segundo caso de uso real aparecer (regra dos tres usos) — os dialogs de
 * formulario guiado (DDL/view/trigger/routine) ja tem seu proprio Output
 * tipado ou {@code SqlBuilderValidationException} e mostram o erro via
 * {@code JOptionPane} no ponto de uso, sem um padrao unico ainda.
 */
public final class DialogShell {

    private final JDialog dialog;

    private DialogShell(JDialog dialog) {
        this.dialog = dialog;
    }

    /** Cria o {@link JDialog} (layout {@link BorderLayout}, fecha com dispose, Esc fecha) sem exibi-lo ainda. */
    public static DialogShell create(Window owner, String title, JDialog.ModalityType modality) {
        JDialog dialog = new JDialog(owner, title, modality);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.setLayout(new BorderLayout());
        // Mesmo atalho ja usado nos dialogs "de formulario guiado" (DDL/
        // view/trigger/rotina/usuarios) — faltava aqui, no UNICO ponto
        // compartilhado de construcao de dialog, entao qualquer consumidor
        // futuro do DialogShell ja ganha de graca (achado numa auditoria
        // pedida pelo usuario: Esc fechava so 5 de ~16 dialogs do app).
        dialog.getRootPane().registerKeyboardAction(e -> dialog.dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        return new DialogShell(dialog);
    }

    /** O {@link JDialog} por baixo — para quem precisar de uma chamada que este shell ainda nao cobre. */
    public JDialog dialog() {
        return dialog;
    }

    public DialogShell center(JComponent content) {
        dialog.add(content, BorderLayout.CENTER);
        return this;
    }

    public DialogShell north(JComponent content) {
        dialog.add(content, BorderLayout.NORTH);
        return this;
    }

    public DialogShell south(JComponent content) {
        dialog.add(content, BorderLayout.SOUTH);
        return this;
    }

    /** Roda {@code onClosed} quando a janela e fechada (X, Esc ou dispose por codigo) — tipicamente para liberar timers/listeners. */
    public DialogShell onClosed(Runnable onClosed) {
        dialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                onClosed.run();
            }
        });
        return this;
    }

    /** Dimensiona, centraliza no owner e exibe. */
    public void show(int width, int height) {
        dialog.setSize(width, height);
        dialog.setLocationRelativeTo(dialog.getOwner());
        dialog.setVisible(true);
    }
}
