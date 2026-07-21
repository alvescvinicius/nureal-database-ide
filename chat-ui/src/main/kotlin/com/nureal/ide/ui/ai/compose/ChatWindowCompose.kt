package com.nureal.ide.ui.ai.compose

import androidx.compose.ui.awt.ComposeWindow
import com.nureal.ide.core.ai.agent.Agent
import com.nureal.ide.core.ai.context.ContextProvider
import com.nureal.ide.core.ai.history.ChatHistoryStore
import com.nureal.ide.core.log.AppLogger
import com.nureal.ide.core.ui.ChatWindowPreferences
import com.nureal.ide.ui.ai.ChatActions
import java.awt.GraphicsConfiguration
import java.awt.Rectangle
import java.awt.Toolkit
import java.awt.Window
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.io.IOException
import javax.swing.Timer

/**
 * Equivalente Compose de `ChatWindow.java` — mesmo padrao (SINGLETON,
 * `open(...)` estatico que so traz a janela existente pra frente em chamadas
 * repetidas) e MESMA logica de posicao padrao/persistencia de bounds via
 * [ChatWindowPreferences] (reusada sem alteracao — e so aritmetica de
 * `Rectangle`/AWT, nao depende de Swing especificamente). `ComposeWindow`
 * extends `java.awt.Frame`, entao toda a API de bounds/listeners usada la
 * funciona identica aqui — com uma diferenca real: `Frame` (ao contrario de
 * `Dialog`/`Window`) nao tem construtor com "owner", entao esta janela NAO
 * fica formalmente associada a `owner` no sentido do AWT (nao minimiza junto,
 * por exemplo) — `owner` aqui serve so pra calcular a posicao padrao (mesmo
 * monitor/bounds), ver [defaultBounds].
 */
class ComposeChatWindow private constructor(
    owner: Window,
    agent: Agent,
    historyStore: ChatHistoryStore,
    conversationId: String,
    onOpenSettings: Runnable,
    actions: ChatActions,
    contextProvider: ContextProvider,
) {
    private val window = ComposeWindow(owner.graphicsConfiguration)
    private val session = ChatSession(agent, historyStore, conversationId)
    private val preferences = ChatWindowPreferences()
    private val saveBoundsDebounce: Timer = Timer(400) { persistBounds() }

    init {
        window.title = "Chat com IA"
        window.setContent {
            ChatScreen(session, actions, contextProvider) { onOpenSettings.run() }
        }
        window.bounds = loadBoundsOrDefault(owner)

        saveBoundsDebounce.isRepeats = false
        window.addComponentListener(object : ComponentAdapter() {
            override fun componentResized(e: ComponentEvent) {
                saveBoundsDebounce.restart()
            }

            override fun componentMoved(e: ComponentEvent) {
                saveBoundsDebounce.restart()
            }
        })
        window.addWindowListener(object : WindowAdapter() {
            override fun windowClosing(e: WindowEvent) {
                window.dispose()
            }

            override fun windowClosed(e: WindowEvent) {
                instance = null
                saveBoundsDebounce.stop()
                persistBounds()
            }
        })
    }

    private fun show() {
        window.isVisible = true
        window.toFront()
        window.requestFocus()
    }

    private fun applyAgent(newAgent: Agent) {
        session.updateAgent(newAgent)
    }

    private fun loadBoundsOrDefault(owner: Window): Rectangle {
        return try {
            val saved = preferences.load()
            if (saved.isSet) Rectangle(saved.x(), saved.y(), saved.width(), saved.height()) else defaultBounds(owner)
        } catch (e: IOException) {
            AppLogger.warning("Falha ao carregar posicao salva do chat de IA", e)
            defaultBounds(owner)
        }
    }

    private fun persistBounds() {
        val bounds = window.bounds
        try {
            preferences.save(ChatWindowPreferences.State(bounds.x, bounds.y, bounds.width, bounds.height))
        } catch (e: IOException) {
            AppLogger.warning("Falha ao salvar posicao do chat de IA", e)
        }
    }

    companion object {
        private var instance: ComposeChatWindow? = null

        @JvmStatic
        fun open(
            owner: Window,
            agent: Agent,
            historyStore: ChatHistoryStore,
            conversationId: String,
            onOpenSettings: Runnable,
            actions: ChatActions,
            contextProvider: ContextProvider,
        ) {
            val existing = instance
            if (existing != null) {
                existing.applyAgent(agent)
                existing.show()
                return
            }
            val created = ComposeChatWindow(owner, agent, historyStore, conversationId, onOpenSettings, actions,
                    contextProvider)
            instance = created
            created.show()
        }

        /** Espelha `ChatWindow.updateAgent` (Swing) — chamado por MainWindow apos salvar as configuracoes de IA. */
        @JvmStatic
        fun updateAgent(agent: Agent) {
            instance?.applyAgent(agent)
        }
    }
}

/** Encostada na borda direita da janela dona, com a mesma altura — mesma logica de `ChatWindow.defaultBounds`. */
private fun defaultBounds(owner: Window): Rectangle {
    val ownerBounds = owner.bounds
    val width = 440
    val height = ownerBounds.height
    var x = ownerBounds.x + ownerBounds.width
    val y = ownerBounds.y

    val screen = screenBoundsFor(owner)
    if (x + width > screen.x + screen.width) {
        x = maxOf(screen.x, screen.x + screen.width - width)
    }
    return Rectangle(x, y, width, height)
}

private fun screenBoundsFor(owner: Window): Rectangle {
    val gc: GraphicsConfiguration? = owner.graphicsConfiguration
    return gc?.bounds ?: Rectangle(Toolkit.getDefaultToolkit().screenSize)
}
