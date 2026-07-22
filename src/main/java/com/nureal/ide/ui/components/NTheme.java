package com.nureal.ide.ui.components;

import java.awt.Color;

import javax.swing.UIManager;

import com.formdev.flatlaf.FlatLaf;
import com.nureal.ide.ui.GridTheme;
import com.nureal.ide.ui.Spacing;

/**
 * Ponte do Nureal Design System (NDS) com a paleta/escala JA EXISTENTE do
 * app ({@link Spacing}, {@link GridTheme}) — NUNCA reinventa valores
 * proprios. Antes desta revisao, esta classe tinha sua PROPRIA escala de
 * espacamento e paleta de cor (descoberto durante a construcao dos
 * primeiros componentes N*: o app ja tinha {@code Spacing}/{@code Typography}/
 * {@code GridTheme} de uma rodada de refinamento visual anterior, com
 * valores DIFERENTES dos que este arquivo inventou) — exatamente o tipo de
 * "dois estilos para o mesmo componente" que o NDS existe pra eliminar.
 * Corrigido: {@code ui.components} agora consome {@link Spacing}/
 * {@link GridTheme} diretamente (pacote FILHO consumindo o pai, nunca o
 * contrario), unica fonte de verdade pra qualquer tela, nova ou antiga.
 */
public final class NTheme {

    /** Escala unica de espacamento — ver {@link Spacing} (fonte unica, nao duplicada aqui). */
    public static final int SPACE_XS = Spacing.XS;
    public static final int SPACE_SM = Spacing.SM;
    public static final int SPACE_MD = Spacing.MD;

    /** Raio padrao dos cantos arredondados de superficies (cards, chips etc.). */
    public static final int CARD_ARC = 10;

    private NTheme() {
    }

    public static boolean isDark() {
        return FlatLaf.isLafDark();
    }

    /** Cor de destaque (cabecalho/borda) por tipo — ver {@link GridTheme}, campos {@code ACCENT_*}. */
    public static Color accentColor(NAccent accent) {
        return switch (accent) {
            case ERROR -> GridTheme.ACCENT_ERROR;
            case WARNING -> GridTheme.ACCENT_WARNING;
            case INFO -> GridTheme.ACCENT_INFO;
            case SQL -> GridTheme.ACCENT_SQL;
            case TOOL -> GridTheme.ACCENT_TOOL;
            case NEUTRAL -> GridTheme.MUTED_TEXT;
        };
    }

    /** Versao translucida de {@link #accentColor} — fundo de {@code NBadge}, nunca a cor solida (reservada pro texto/destaque). */
    public static Color accentBackground(NAccent accent) {
        Color base = accentColor(accent);
        return new Color(base.getRed(), base.getGreen(), base.getBlue(), 40);
    }

    /**
     * Fundo de uma superficie elevada (card/chip): {@code Panel.background} do
     * tema atual, um pouco mais clara (escuro) ou mais escura (claro). Sem
     * equivalente em {@link GridTheme} (que e sobre grade/editor, nao sobre
     * cards genericos) — unica cor genuinamente nova do NDS.
     */
    public static Color surfaceBackground() {
        Color base = UIManager.getColor("Panel.background");
        if (base == null) {
            return new Color(0, 0, 0, 20);
        }
        int delta = isDark() ? 18 : -10;
        return new Color(clamp(base.getRed() + delta), clamp(base.getGreen() + delta), clamp(base.getBlue() + delta));
    }

    /** Cor de texto discreta — ver {@link GridTheme#MUTED_TEXT} (fonte unica, nao duplicada aqui). */
    public static Color mutedColor() {
        return GridTheme.MUTED_TEXT;
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }
}
