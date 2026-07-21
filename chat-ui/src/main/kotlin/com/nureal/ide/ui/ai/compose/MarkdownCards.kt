package com.nureal.ide.ui.ai.compose

/**
 * Parser de markdown minimo — MESMA logica de `MessageRenderer.java`
 * (blocos de codigo, titulos `#`..`######`, admonicoes GFM
 * `> [!TIP]`/`> [!WARNING]`), portada 1:1 pra Kotlin. Continua sem suporte a
 * markdown completo (negrito/listas/tabelas/links).
 */
sealed interface CardSpec {
    data class TextBlock(val text: String) : CardSpec
    data class Heading(val level: Int, val text: String) : CardSpec
    data class Admonition(val warning: Boolean, val body: String) : CardSpec
    data class CodeBlock(val language: String?, val code: String) : CardSpec
}

private val CODE_FENCE = Regex("```(\\w*)\\r?\\n([\\s\\S]*?)```")
private val ADMONITION_START = Regex("^>\\s?\\[!(\\w+)]\\s*$")
private val HEADING = Regex("^(#{1,6})\\s+(.*)$")

fun parseContent(content: String): List<CardSpec> {
    val specs = mutableListOf<CardSpec>()
    var last = 0
    var foundAny = false
    for (match in CODE_FENCE.findAll(content)) {
        foundAny = true
        val before = content.substring(last, match.range.first)
        if (before.isNotBlank()) {
            specs += parseTextSegment(before.trim())
        }
        val language = match.groupValues[1].ifBlank { null }
        specs += CardSpec.CodeBlock(language, match.groupValues[2])
        last = match.range.last + 1
    }
    val rest = content.substring(last)
    if (rest.isNotBlank() || !foundAny) {
        specs += parseTextSegment(rest.trim())
    }
    return specs
}

private fun parseTextSegment(text: String): List<CardSpec> {
    val specs = mutableListOf<CardSpec>()
    val lines = text.split(Regex("\r?\n"))
    val plain = StringBuilder()
    var i = 0

    fun flushPlain() {
        val t = plain.toString().trim()
        if (t.isNotEmpty()) {
            specs += CardSpec.TextBlock(t)
        }
        plain.setLength(0)
    }

    while (i < lines.size) {
        val headingMatch = HEADING.matchEntire(lines[i])
        val admonitionMatch = ADMONITION_START.matchEntire(lines[i])
        when {
            headingMatch != null -> {
                flushPlain()
                specs += CardSpec.Heading(headingMatch.groupValues[1].length, headingMatch.groupValues[2].trim())
                i++
            }
            admonitionMatch != null -> {
                flushPlain()
                val kind = admonitionMatch.groupValues[1].uppercase()
                val body = StringBuilder()
                i++
                while (i < lines.size && lines[i].startsWith(">")) {
                    val line = lines[i].substring(1).trimStart()
                    if (body.isNotEmpty()) {
                        body.append('\n')
                    }
                    body.append(line)
                    i++
                }
                specs += CardSpec.Admonition(kind == "WARNING" || kind == "CAUTION", body.toString())
            }
            else -> {
                if (plain.isNotEmpty()) {
                    plain.append('\n')
                }
                plain.append(lines[i])
                i++
            }
        }
    }
    flushPlain()
    return specs
}
