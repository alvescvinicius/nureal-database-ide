package com.nureal.ide.ui;

import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Window;

/**
 * Resolve o "dono" correto para centralizar dialogos (JOptionPane, JDialog,
 * JFileChooser): sempre a JANELA de nivel superior (JFrame/JDialog) que
 * contem o componente que disparou a acao — nunca o componente em si.
 *
 * Sem isto, dialogos abertos a partir de paineis pequenos (ex:
 * {@link ConnectionsPanel}, {@link SavedQueriesPanel}) ou de uma celula
 * dentro de uma grade grande (JTable) ficavam centralizados em cima DAQUELE
 * componente especifico — na pratica isso aparecia em qualquer canto da
 * tela, dependendo de onde o painel/celula estava posicionado dentro da
 * janela principal, em vez de sempre no centro da aplicacao.
 * {@code JOptionPane.showXxxDialog(parent, ...)} centraliza com base no
 * "parent" passado (via {@code Dialog#setLocationRelativeTo} internamente) —
 * passar a JANELA em vez do componente resolve isso de uma vez para todos os
 * dialogos do sistema (pedido explicito do usuario: "todos deveriam abrir
 * sempre centralizados na tela").
 */
final class DialogUtil {

    private DialogUtil() {
    }

    /**
     * A janela (JFrame/JDialog) que contem {@code c}, ou o proprio {@code c}
     * se ele ja for uma janela ou ainda nao estiver em nenhuma (ex: durante
     * construcao/testes) — nesse caso {@code null}/o proprio componente ainda
     * funciona como fallback seguro para as APIs do Swing.
     */
    static Component owner(Component c) {
        if (c == null) {
            return null;
        }
        if (c instanceof Window) {
            return c;
        }
        Window w = SwingUtilities.getWindowAncestor(c);
        return (w != null) ? w : c;
    }
}
