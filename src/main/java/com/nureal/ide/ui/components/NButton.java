package com.nureal.ide.ui.components;

import java.awt.Color;

import javax.swing.JButton;

import com.formdev.flatlaf.FlatClientProperties;
import com.nureal.ide.ui.MainWindow;

/**
 * Botao padronizado do Nureal Design System — MESMO estilo ja usado (mas
 * repetido em varios lugares: {@code MainWindow#buildMainToolbar},
 * {@code MessageRenderer}) pros tres papeis reais que a IDE ja distingue
 * visualmente: acao primaria solida (verde da marca), acao secundaria em
 * contorno, acao leve/de barra de ferramentas. Nenhum valor novo — so
 * formaliza o {@code putClientProperty} que ja se repetia.
 */
public final class NButton extends JButton {

    private static final long serialVersionUID = 1L;

    /** Papel visual do botao — ver javadoc da classe pra onde cada um ja era usado antes do NDS. */
    public enum Kind {
        /** Acao principal da tela (ex.: Executar, Enviar) — fundo solido no verde da marca. */
        PRIMARY,
        /** Acao secundaria (ex.: Formatar, Explicar, Salvar) — contorno, sem competir com a primaria. */
        SECONDARY,
        /** Acao leve, tipicamente numa barra de ferramentas/toolbar de card (ex.: Copiar). */
        GHOST,
    }

    public NButton(String text) {
        this(text, Kind.SECONDARY);
    }

    public NButton(String text, Kind kind) {
        super(text);
        applyKind(kind);
    }

    private void applyKind(Kind kind) {
        switch (kind) {
            case PRIMARY -> {
                putClientProperty(FlatClientProperties.STYLE,
                        "arc: 8; focusWidth: 0; innerFocusWidth: 0; borderWidth: 0");
                setBackground(MainWindow.ACCENT);
                setForeground(Color.WHITE);
            }
            case SECONDARY -> {
                putClientProperty("JButton.buttonType", "roundRect");
                putClientProperty(FlatClientProperties.STYLE, "arc: 8; borderWidth: 1");
            }
            case GHOST -> putClientProperty("JButton.buttonType", "toolBarButton");
        }
    }
}
