package com.nureal.ide.ui.ai.compose

import androidx.compose.ui.graphics.Color
import com.formdev.flatlaf.FlatLaf
import javax.swing.UIManager

/**
 * Ponte com o tema atual do FlatLaf — MESMA paleta de
 * {@code MessageRenderer.java} (`accentColor`/`cardBackground`), replicada
 * aqui pra manter consistencia visual entre a versao Swing (ainda em
 * producao) e esta versao Compose enquanto a migracao nao termina. Consulta
 * o UIManager a cada chamada (sem cache) — barato o suficiente, e evita
 * ficar preso num tema antigo apos um toggle em runtime.
 */
enum class CardKind { TEXT, SQL, CODE, INFO, WARNING, TOOL, ERROR }

object CardTheme {

    fun isDark(): Boolean = FlatLaf.isLafDark()

    fun accent(kind: CardKind): Color = when (kind) {
        CardKind.ERROR -> themed(Color(0xFFE5484D), Color(0xFFF87171))
        CardKind.WARNING -> themed(Color(0xFFB38600), Color(0xFFF5C842))
        CardKind.INFO -> themed(Color(0xFF2563EB), Color(0xFF60A5FA))
        CardKind.SQL -> themed(Color(0xFF0F766E), Color(0xFF2DD4BF))
        CardKind.TOOL -> themed(Color(0xFF7C3AED), Color(0xFFA78BFA))
        CardKind.CODE, CardKind.TEXT -> muted()
    }

    fun muted(): Color {
        val c = UIManager.getColor("Label.disabledForeground") ?: return Color.Gray
        return Color(c.red, c.green, c.blue, c.alpha)
    }

    /** Fundo do card: `Panel.background` do tema atual, um pouco mais claro (escuro) ou mais escuro (claro). */
    fun cardBackground(): Color {
        val base = UIManager.getColor("Panel.background")
            ?: return if (isDark()) Color(0xFF3C3F41) else Color(0xFFF0F0F0)
        val delta = if (isDark()) 18 else -10
        fun clamp(v: Int) = v.coerceIn(0, 255)
        return Color(clamp(base.red + delta), clamp(base.green + delta), clamp(base.blue + delta))
    }

    private fun themed(light: Color, dark: Color): Color = if (isDark()) dark else light
}
