package com.nureal.ide.ui.components;

import java.awt.Color;

import javax.swing.UIManager;

/**
 * Design tokens do Nureal Design System — cor/espacamento canonicos, sempre
 * derivados do tema FlatLaf ATUAL (nunca RGB fixo de um unico tema), pra
 * qualquer componente {@code N*} nao reinventar sua propria paleta. Extraido
 * de {@code MessageRenderer} (era privado la, so pro chat) — primeiro passo
 * do Nureal Design System (NDS): formalizar o que ja funcionava num lugar
 * unico, reutilizavel por qualquer tela.
 */
public final class NTheme {

    /** Escala unica de espacamento (px) — nenhum componente deve usar valor arbitrario fora daqui. */
    public static final int SPACE_XS = 4;
    public static final int SPACE_SM = 8;
    public static final int SPACE_MD = 12;
    public static final int SPACE_LG = 16;
    public static final int SPACE_XL = 20;
    public static final int SPACE_XXL = 24;
    public static final int SPACE_XXXL = 32;

    /** Raio padrao dos cantos arredondados de superficies (cards, chips etc.). */
    public static final int CARD_ARC = 10;

    private NTheme() {
    }

    public static boolean isDark() {
        Color background = UIManager.getColor("Panel.background");
        return background != null
                && (background.getRed() * 0.299 + background.getGreen() * 0.587 + background.getBlue() * 0.114) < 128;
    }

    /** Cor de destaque (cabecalho/borda) por tipo — sempre par claro/escuro, nunca uma cor fixa unica. */
    public static Color accentColor(NAccent accent) {
        return switch (accent) {
            case ERROR -> themed(new Color(0xE5484D), new Color(0xF87171));
            case WARNING -> themed(new Color(0xB38600), new Color(0xF5C842));
            case INFO -> themed(new Color(0x2563EB), new Color(0x60A5FA));
            case SQL -> themed(new Color(0x0F766E), new Color(0x2DD4BF));
            case TOOL -> themed(new Color(0x7C3AED), new Color(0xA78BFA));
            case NEUTRAL -> mutedColor();
        };
    }

    /** Versao translucida de {@link #accentColor} — fundo de {@code NBadge}, nunca a cor solida (reservada pro texto/destaque). */
    public static Color accentBackground(NAccent accent) {
        Color base = accentColor(accent);
        return new Color(base.getRed(), base.getGreen(), base.getBlue(), 40);
    }

    /** Fundo de uma superficie elevada (card/chip): {@code Panel.background} do tema atual, um pouco mais clara/escura. */
    public static Color surfaceBackground() {
        Color base = UIManager.getColor("Panel.background");
        if (base == null) {
            return new Color(0, 0, 0, 20);
        }
        int delta = isDark() ? 18 : -10;
        return new Color(clamp(base.getRed() + delta), clamp(base.getGreen() + delta), clamp(base.getBlue() + delta));
    }

    public static Color mutedColor() {
        Color c = UIManager.getColor("Label.disabledForeground");
        return c != null ? c : Color.GRAY;
    }

    private static Color themed(Color light, Color dark) {
        return isDark() ? dark : light;
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }
}
