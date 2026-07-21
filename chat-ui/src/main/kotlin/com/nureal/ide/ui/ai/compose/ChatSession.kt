package com.nureal.ide.ui.ai.compose

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nureal.ide.core.ai.agent.Agent
import com.nureal.ide.core.ai.history.ChatHistoryStore
import com.nureal.ide.core.ai.provider.AiEvent
import com.nureal.ide.core.ai.provider.ChatMessage
import com.nureal.ide.core.ai.provider.ToolCall
import com.nureal.ide.core.log.AppLogger
import java.io.IOException

/** Uma entrada da lista de mensagens renderizadas — ver [ChatSession.entries]. */
sealed interface ChatEntry {

    class Message(val role: String, initialContent: String, streaming: Boolean) : ChatEntry {
        var content: String by mutableStateOf(initialContent)
        var streaming: Boolean by mutableStateOf(streaming)
        var isError: Boolean by mutableStateOf(false)
    }

    /** Card de status de uma tool em execucao — `label` e sobrescrito com o resumo quando ela termina. */
    class Tool(val callId: String, initialLabel: String) : ChatEntry {
        var label: String by mutableStateOf(initialLabel)
    }
}

private val OPEN_SQL_FENCE = Regex("```(\\w*)")

/**
 * Equivalente Compose de `ChatController.java`: fala com o [Agent] (nunca com
 * `LLMProvider` diretamente) e mantem o estado observavel que [ChatScreen]
 * renderiza. Eventos do Agent chegam numa thread de background; ao contrario
 * do Swing (que exige `SwingUtilities.invokeLater` porque seus componentes
 * NAO sao thread-safe), o sistema de snapshot state do Compose aceita
 * escrita de qualquer thread — a recomposicao e agendada sozinha.
 */
class ChatSession(
    private var agent: Agent,
    private val historyStore: ChatHistoryStore,
    private val conversationId: String,
) {
    val entries = mutableStateListOf<ChatEntry>()

    var sending: Boolean by mutableStateOf(false)
        private set

    var status: String? by mutableStateOf(null)
        private set

    private var activeTurnId: String? = null
    private val pendingToolCards = mutableMapOf<String, ChatEntry.Tool>()

    init {
        loadHistory()
    }

    /** Chamado quando as configuracoes de IA sao salvas com a janela ja aberta. */
    fun updateAgent(newAgent: Agent) {
        agent = newAgent
    }

    fun send(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || sending) {
            return
        }
        entries.add(ChatEntry.Message("user", trimmed, streaming = false))
        saveHistory(trimmed, ChatMessage.ROLE_USER)
        sending = true
        status = "Pensando..."

        val liveText = StringBuilder()
        val liveMessage = ChatEntry.Message("assistant", "", streaming = true)
        entries.add(liveMessage)

        activeTurnId = agent.chat(conversationId, trimmed) { event -> handleEvent(event, liveMessage, liveText) }
    }

    fun cancel() {
        activeTurnId?.let { agent.cancel(it) }
    }

    private fun handleEvent(event: AiEvent, liveMessage: ChatEntry.Message, liveText: StringBuilder) {
        when (event) {
            is AiEvent.Started -> status = "Pensando..."

            is AiEvent.Chunk -> {
                liveText.append(event.delta())
                liveMessage.content = liveText.toString()
                status = if (insideOpenSqlFence(liveText)) "Gerando SQL..." else "Recebendo resposta..."
            }

            is AiEvent.ToolCallsRequested -> {
                status = statusForTools(event.calls())
                for (call in event.calls()) {
                    val entry = ChatEntry.Tool(call.id(), labelForTool(call.name()))
                    pendingToolCards[call.id()] = entry
                    entries.add(entry)
                }
            }

            is AiEvent.ToolCallResult -> {
                pendingToolCards.remove(event.call().id())?.let { entry ->
                    entry.label = (if (event.success()) "✓ " else "❌ ") + event.summary()
                }
            }

            is AiEvent.Completed -> {
                val finalContent = event.response().message().content()
                liveMessage.content = finalContent
                liveMessage.streaming = false
                if (finalContent.isNotBlank()) {
                    saveHistory(finalContent, ChatMessage.ROLE_ASSISTANT)
                }
                finishTurn()
            }

            is AiEvent.Failed -> {
                liveMessage.content = event.error().message ?: "Erro desconhecido."
                liveMessage.isError = true
                liveMessage.streaming = false
                finishTurn()
            }

            is AiEvent.Cancelled -> {
                liveMessage.content = if (liveText.isNotEmpty()) "$liveText\n\n(cancelado)" else "(cancelado)"
                liveMessage.streaming = false
                finishTurn()
            }
        }
    }

    private fun finishTurn() {
        activeTurnId = null
        sending = false
        status = null
    }

    private fun loadHistory() {
        try {
            historyStore.find(conversationId).ifPresent { conversation ->
                for (message in conversation.messages()) {
                    entries.add(ChatEntry.Message(message.role(), message.content(), streaming = false))
                }
            }
        } catch (e: IOException) {
            AppLogger.warning("Falha ao carregar historico do chat de IA", e)
        }
    }

    private fun saveHistory(content: String, role: String) {
        try {
            historyStore.appendMessage(conversationId, deriveTitle(content), role, content)
        } catch (e: IOException) {
            AppLogger.warning("Falha ao salvar historico da conversa $conversationId", e)
        }
    }

    private fun deriveTitle(firstMessage: String): String {
        val trimmed = firstMessage.trim().replace('\n', ' ')
        if (trimmed.isEmpty()) {
            return "Nova conversa"
        }
        return if (trimmed.length > 60) trimmed.substring(0, 60) + "..." else trimmed
    }
}

private fun insideOpenSqlFence(text: CharSequence): Boolean {
    var fenceCount = 0
    var lastLanguage: String? = null
    for (match in OPEN_SQL_FENCE.findAll(text)) {
        fenceCount++
        if (fenceCount % 2 == 1) {
            lastLanguage = match.groupValues[1]
        }
    }
    return fenceCount % 2 == 1 && lastLanguage.equals("sql", ignoreCase = true)
}

private fun statusForTools(calls: List<ToolCall>): String =
    if (calls.any { isMetadataTool(it.name()) }) "Consultando metadata..." else "Executando ferramenta..."

private fun labelForTool(toolName: String): String =
    if (isMetadataTool(toolName)) "Consultando metadata..." else "Executando $toolName..."

private fun isMetadataTool(toolName: String): Boolean = toolName == "list_tables" || toolName == "describe_table"
