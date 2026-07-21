package com.nureal.ide.ui.components;

import javax.swing.JButton;

import com.nureal.ide.ui.Buttons;

/**
 * Botao padronizado do Nureal Design System — ponte pra {@link Buttons}
 * (ja existia, com os MESMOS 3 papeis, so package-private ate agora).
 * Descoberto ao construir o rail de icones do {@code MainWindow}: a versao
 * original deste arquivo tinha reinventado os 3 estilos com margens
 * ligeiramente diferentes das de {@link Buttons} — corrigido pra delegar,
 * mesma reconciliacao ja feita com {@code Spacing}/{@code Typography}/
 * {@code GridTheme} (ver commit da reconciliacao de tema).
 */
public final class NButton extends JButton {

    private static final long serialVersionUID = 1L;

    /** Papel visual do botao — ver {@link Buttons} pra onde cada um ja era usado antes do NDS. */
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
        switch (kind) {
            case PRIMARY -> Buttons.stylePrimary(this);
            case SECONDARY -> Buttons.styleSecondary(this);
            case GHOST -> Buttons.styleIconButton(this);
        }
    }
}
