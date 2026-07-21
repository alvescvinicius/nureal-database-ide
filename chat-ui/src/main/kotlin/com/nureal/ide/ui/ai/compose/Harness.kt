package com.nureal.ide.ui.ai.compose

import androidx.compose.ui.awt.ComposeWindow
import com.nureal.ide.core.ai.agent.Agent
import com.nureal.ide.core.ai.history.ChatHistoryStore
import com.nureal.ide.core.ai.provider.AiEvent
import com.nureal.ide.core.ai.provider.ChatMessage
import com.nureal.ide.core.ai.provider.ChatResponse
import com.nureal.ide.core.ai.provider.ChatUsage
import com.nureal.ide.core.ai.provider.ToolCall
import com.nureal.ide.core.format.SqlFormatter
import com.nureal.ide.ui.ai.ChatActions
import java.nio.file.Files
import java.util.UUID
import java.util.function.Consumer
import javax.swing.SwingUtilities

/**
 * Agent fake e sincrono-ish (so streama num thread separado) — mesma ideia
 * do `FakeProvider` de `DefaultAgentTest`, aqui no nivel de [Agent] direto,
 * pra nao precisar de nenhum LLMProvider real so pra validar o port Compose
 * (todos os tipos de card + o card de tool).
 */
private class FakeAgent : Agent {
    override fun chat(conversationId: String, userMessage: String, onEvent: Consumer<AiEvent>): String {
        val turnId = UUID.randomUUID().toString()
        Thread {
            onEvent.accept(AiEvent.Started(turnId))

            // Rodada 1: pede uma tool.
            val call = ToolCall("call-1", "list_tables", mapOf())
            onEvent.accept(AiEvent.ToolCallsRequested(turnId, listOf(call), ""))
            Thread.sleep(300)
            onEvent.accept(AiEvent.ToolCallResult(turnId, call, true, "12 tabelas encontradas"))
            Thread.sleep(200)

            // Rodada 2: resposta final com todos os tipos de card.
            val reply = """
                # Analise da consulta

                Aqui esta um exemplo com card de SQL, codigo generico, dica e aviso.

                > [!TIP]
                > Sempre filtre por uma coluna indexada.

                > [!WARNING]
                > Esse DELETE nao tem WHERE -- cuidado.

                ```sql
                SELECT id, name FROM users WHERE status = 1 ORDER BY created_at DESC LIMIT 100;
                ```

                ```json
                {"status": 1, "indexado": true}
                ```

                Isso deveria bastar pra validar todos os cards.
            """.trimIndent()
            for (chunk in reply.chunked(12)) {
                onEvent.accept(AiEvent.Chunk(turnId, chunk))
                Thread.sleep(10)
            }
            onEvent.accept(
                AiEvent.Completed(
                    turnId,
                    ChatResponse(ChatMessage(ChatMessage.ROLE_ASSISTANT, reply), "stop", ChatUsage.EMPTY, listOf()),
                ),
            )
        }.start()
        return turnId
    }

    override fun cancel(turnId: String) {
        // fake nao suporta cancelamento de verdade
    }
}

/**
 * So pra rodar via `./gradlew run` e validar o port sem MainWindow nem
 * provider real: constroi [ChatSession]/[ChatScreen] direto (sem passar por
 * [ComposeChatWindow], ja validado separadamente) e dispara uma mensagem
 * automaticamente pra exercitar o pipeline inteiro (parsing de markdown,
 * SwingPanel com RSyntaxTextArea de verdade, card de tool) sem precisar
 * clicar em nada.
 */
fun main() {
    val historyFile = Files.createTempFile("chat-ui-harness", ".conf")
    val historyStore = ChatHistoryStore(historyFile)
    val actions = ChatActions(
        { sql -> println("[Executar] $sql") },
        { SqlFormatter() },
        { sql -> println("[Explicar] $sql") },
    )
    val session = ChatSession(FakeAgent(), historyStore, "harness")

    SwingUtilities.invokeLater {
        val window = ComposeWindow()
        window.title = "Chat com IA (harness)"
        window.setSize(480, 760)
        window.setContent {
            ChatScreen(session, actions) { println("[Configuracoes]") }
        }
        window.isVisible = true
    }

    Thread {
        Thread.sleep(1000)
        SwingUtilities.invokeLater { session.send("Otimize esta consulta") }
    }.start()
}
