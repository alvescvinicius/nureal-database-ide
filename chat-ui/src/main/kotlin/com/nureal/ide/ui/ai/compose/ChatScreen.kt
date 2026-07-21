package com.nureal.ide.ui.ai.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.Button
import androidx.compose.material.IconButton
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nureal.ide.ui.ai.ChatActions

/** Equivalente Compose de `ChatPanel.java` + parte de `ChatController.java` (so o que e puramente visual). */
@Composable
fun ChatScreen(session: ChatSession, actions: ChatActions, onOpenSettings: () -> Unit) {
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(session.entries.size) {
        if (session.entries.isNotEmpty()) {
            listState.animateScrollToItem(session.entries.size - 1)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth().padding(6.dp), horizontalArrangement = Arrangement.End) {
            IconButton(onClick = onOpenSettings) { Text("⚙") }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(session.entries) { entry ->
                when (entry) {
                    is ChatEntry.Message -> MessageEntryView(entry, actions)
                    is ChatEntry.Tool -> ToolCardView(entry.label)
                }
            }
        }

        session.status?.let {
            Text(it, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), fontSize = 11.sp,
                color = CardTheme.muted())
        }

        Row(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.Bottom) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Pergunte algo...") },
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = {
                if (session.sending) {
                    session.cancel()
                } else {
                    val text = input.trim()
                    if (text.isNotEmpty()) {
                        input = ""
                        session.send(text)
                    }
                }
            }) {
                Text(if (session.sending) "Cancelar" else "Enviar")
            }
        }
    }
}

@Composable
private fun MessageEntryView(entry: ChatEntry.Message, actions: ChatActions) {
    Column {
        Text(roleLabel(entry.role), fontWeight = FontWeight.Bold, fontSize = 11.sp, color = CardTheme.muted())
        Spacer(Modifier.height(3.dp))
        when {
            entry.isError -> ErrorCardView(entry.content)
            entry.streaming -> Text(entry.content)
            else -> ParsedCards(entry.content, actions)
        }
    }
}

@Composable
private fun ParsedCards(content: String, actions: ChatActions) {
    val cards = remember(content) { parseContent(content) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (spec in cards) {
            when (spec) {
                is CardSpec.TextBlock -> TextCardView(spec.text)
                is CardSpec.Heading -> HeadingView(spec.level, spec.text)
                is CardSpec.Admonition -> AdmonitionCardView(spec.warning, spec.body)
                is CardSpec.CodeBlock ->
                    if (spec.language.equals("sql", ignoreCase = true)) {
                        SqlCodeCardView(spec.code, actions)
                    } else {
                        GenericCodeCardView(spec.language, spec.code)
                    }
            }
        }
    }
}

private fun roleLabel(role: String): String = when (role) {
    "user" -> "Você"
    "assistant" -> "Assistente"
    "tool" -> "Ferramenta"
    else -> role
}
