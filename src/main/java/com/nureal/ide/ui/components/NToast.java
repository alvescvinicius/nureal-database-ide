package com.nureal.ide.ui.components;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/**
 * Notificacao transitoria flutuante (toast/snackbar) do Nureal Design System —
 * substitui a barra de status FIXA que existia no rodape da janela (removida
 * per SPEC-0007 "Sidebar Workspace": "informacao temporaria nao deve ocupar
 * espaco fixo").
 * <p>
 * Nao muda NENHUM dos ~80 lugares do app que chamam
 * {@code statusBar.setText(" mensagem")}: {@link #attach} apenas ESCUTA esse
 * mesmo {@link JLabel} (via o evento "text", que {@code JLabel#setText}
 * ja dispara sozinho) e desenha a mensagem numa bolha flutuante sobre o
 * glass pane da janela, que some sozinha depois de alguns segundos — o
 * {@code JLabel} original continua existindo so como "modelo" (nunca mais
 * fica visivel em lugar nenhum do layout).
 */
public final class NToast {

    private static final int VISIBLE_MS = 3200;

    private final JFrame frame;
    private final JPanel bubble;
    private final JLabel bubbleLabel;
    private final JPanel layer;
    private final Timer hideTimer;

    private NToast(JFrame frame) {
        this.frame = frame;

        bubbleLabel = new JLabel();
        bubbleLabel.setForeground(Color.WHITE);
        bubbleLabel.setFont(bubbleLabel.getFont().deriveFont(12f));

        bubble = new JPanel() {
            private static final long serialVersionUID = 1L;

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(30, 32, 36, 235));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        bubble.setOpaque(false);
        bubble.setBorder(BorderFactory.createEmptyBorder(NTheme.SPACE_SM, NTheme.SPACE_MD, NTheme.SPACE_SM, NTheme.SPACE_MD));
        bubble.add(bubbleLabel);
        bubble.setVisible(false);

        // JPanel com layout NULO: a bolha e posicionada manualmente (canto
        // inferior central da janela) toda vez que o texto muda, ja que o
        // glass pane nao tem layout manager algum por padrao.
        layer = new JPanel(null);
        layer.setOpaque(false);
        layer.add(bubble);

        hideTimer = new Timer(VISIBLE_MS, e -> hide());
        hideTimer.setRepeats(false);
    }

    /**
     * Liga o toast a {@code messageSource}: toda vez que o texto dele mudar
     * (e nao estiver em branco), mostra a bolha por alguns segundos. Reusa o
     * glass pane da janela ({@link JFrame#setGlassPane}) — so fica visivel
     * enquanto a bolha esta na tela, entao nunca intercepta clique nenhum do
     * resto da janela fora dessa janela de tempo.
     */
    public static NToast attach(JFrame frame, JLabel messageSource) {
        NToast toast = new NToast(frame);
        frame.setGlassPane(toast.layer);
        messageSource.addPropertyChangeListener("text", evt -> {
            String text = messageSource.getText();
            if (text != null && !text.isBlank()) {
                SwingUtilities.invokeLater(() -> toast.show(text.trim()));
            }
        });
        return toast;
    }

    private void show(String text) {
        bubbleLabel.setText(text);
        Dimension pref = bubble.getPreferredSize();
        int maxWidth = Math.max(200, frame.getWidth() - 80);
        int width = Math.min(pref.width, maxWidth);
        int height = pref.height;
        int x = Math.max(16, (frame.getWidth() - width) / 2);
        int y = Math.max(16, frame.getHeight() - height - 24);
        bubble.setBounds(x, y, width, height);
        bubble.setVisible(true);
        layer.setVisible(true);
        hideTimer.restart();
    }

    private void hide() {
        bubble.setVisible(false);
        layer.setVisible(false);
    }
}
