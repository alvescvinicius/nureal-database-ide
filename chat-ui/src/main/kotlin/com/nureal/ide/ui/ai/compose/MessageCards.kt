package com.nureal.ide.ui.ai.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nureal.ide.ui.SqlEditorPane
import com.nureal.ide.ui.ai.ChatActions
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea
import org.fife.ui.rsyntaxtextarea.SyntaxConstants
import org.fife.ui.rsyntaxtextarea.Theme
import org.fife.ui.rtextarea.RTextScrollPane
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.awt.Color as AwtColor

/** Container comum de todo card — cantos arredondados + fundo/cabecalho pelo tema atual (ver [CardTheme]). */
@Composable
fun CardContainer(kind: CardKind, header: String?, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        backgroundColor = CardTheme.cardBackground(),
        elevation = 0.dp,
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            if (!header.isNullOrBlank()) {
                Text(header, color = CardTheme.accent(kind), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
            }
            content()
        }
    }
}

@Composable
fun TextCardView(text: String) {
    CardContainer(CardKind.TEXT, null) { Text(text) }
}

@Composable
fun HeadingView(level: Int, text: String) {
    val size = when (level) {
        1 -> 17.sp
        2 -> 16.sp
        else -> 15.sp
    }
    Text(text, fontWeight = FontWeight.Bold, fontSize = size, modifier = Modifier.padding(vertical = 4.dp))
}

@Composable
fun AdmonitionCardView(warning: Boolean, body: String) {
    val kind = if (warning) CardKind.WARNING else CardKind.INFO
    CardContainer(kind, if (warning) "⚠ Atenção" else "ℹ Dica") { Text(body) }
}

@Composable
fun ErrorCardView(message: String) {
    CardContainer(CardKind.ERROR, "❌ Erro") { Text(message) }
}

@Composable
fun ToolCardView(label: String) {
    CardContainer(CardKind.TOOL, "🔧 Ferramenta") { Text(label) }
}

@Composable
fun SqlCodeCardView(code: String, actions: ChatActions) {
    val trimmedCode = remember(code) { code.trimEnd() }
    val areaRef = remember { mutableStateOf<RSyntaxTextArea?>(null) }

    CardContainer(CardKind.SQL, "SQL") {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = {
                areaRef.value?.let { area ->
                    Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(area.text), null)
                }
            }) { Text("Copiar") }
            TextButton(onClick = {
                areaRef.value?.let { area -> area.text = actions.sqlFormatterSupplier().get().format(area.text) }
            }) { Text("Formatar") }
            TextButton(onClick = {
                areaRef.value?.let { area -> actions.onExplainSql().accept(area.text) }
            }) { Text("Explicar") }
            TextButton(onClick = {
                areaRef.value?.let { area -> actions.onExecuteSql().accept(area.text) }
            }) { Text("Executar") }
        }
        Spacer(Modifier.height(4.dp))
        SwingPanel(
            modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp, max = 400.dp),
            factory = {
                val area = RSyntaxTextArea(trimmedCode)
                SqlEditorPane.styleAsReadOnlySql(area)
                area.rows = trimmedCode.split("\n").size.coerceIn(1, 20)
                area.setHighlightCurrentLine(false)
                areaRef.value = area
                RTextScrollPane(area).apply {
                    setLineNumbersEnabled(false)
                    setFoldIndicatorEnabled(false)
                }
            },
        )
    }
}

@Composable
fun GenericCodeCardView(language: String?, code: String) {
    val trimmedCode = remember(code) { code.trimEnd() }

    CardContainer(CardKind.CODE, language?.uppercase() ?: "Código") {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = {
                Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(trimmedCode), null)
            }) { Text("Copiar") }
        }
        Spacer(Modifier.height(4.dp))
        SwingPanel(
            modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp, max = 400.dp),
            factory = {
                val area = RSyntaxTextArea(trimmedCode)
                area.isEditable = false
                area.syntaxEditingStyle = styleFor(language)
                area.isCodeFoldingEnabled = false
                area.setHighlightCurrentLine(false)
                themeGenericCode(area)
                RTextScrollPane(area).apply {
                    setLineNumbersEnabled(false)
                    setFoldIndicatorEnabled(false)
                }
            },
        )
    }
}

private fun styleFor(language: String?): String = when (language?.lowercase()) {
    "sql" -> SyntaxConstants.SYNTAX_STYLE_SQL
    "java" -> SyntaxConstants.SYNTAX_STYLE_JAVA
    "json" -> SyntaxConstants.SYNTAX_STYLE_JSON
    else -> SyntaxConstants.SYNTAX_STYLE_NONE
}

/**
 * Blocos de codigo NAO-sql (json/java/etc.) — so garante que o tema
 * embutido do RSyntaxTextArea acompanhe claro/escuro (mesma logica de
 * `MessageRenderer.themeGenericCode`), sem a paleta semantica especifica de
 * SQL que `SqlEditorPane.styleAsReadOnlySql` aplica.
 */
private fun themeGenericCode(area: RSyntaxTextArea) {
    val dark = CardTheme.isDark()
    val resource = if (dark) "/org/fife/ui/rsyntaxtextarea/themes/dark.xml"
        else "/org/fife/ui/rsyntaxtextarea/themes/default.xml"
    RSyntaxTextArea::class.java.getResourceAsStream(resource)?.use { Theme.load(it).apply(area) }
    area.background = if (dark) AwtColor(0x1E, 0x1E, 0x1E) else AwtColor.WHITE
    area.foreground = if (dark) AwtColor(0xD4, 0xD4, 0xD4) else AwtColor.BLACK
}
