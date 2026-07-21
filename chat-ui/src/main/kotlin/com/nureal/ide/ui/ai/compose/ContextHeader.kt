package com.nureal.ide.ui.ai.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nureal.ide.core.ai.context.AgentContext
import com.nureal.ide.core.ai.context.ConnectionContext
import com.nureal.ide.core.ai.context.ContextProvider
import com.nureal.ide.core.ai.specialist.SpecialistRegistry
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/** Intervalo do polling do [ContextProvider] — cada leitura e barata (sem round-trip ao banco, ver DefaultContextProvider). */
private const val CONTEXT_POLL_INTERVAL_MS = 1000L

/**
 * Le [ContextProvider] continuamente (nao so quando uma mensagem e enviada,
 * ao contrario do que o Agent faz) — pra que o painel de contexto reflita
 * uma troca de conexao/schema feita em QUALQUER lugar da IDE quase
 * imediatamente, mesmo com o chat parado sem nenhuma mensagem em andamento.
 */
@Composable
fun rememberAgentContext(contextProvider: ContextProvider): AgentContext {
    var context by remember { mutableStateOf(AgentContext.EMPTY) }
    LaunchedEffect(contextProvider) {
        while (isActive) {
            context = contextProvider.collect()
            delay(CONTEXT_POLL_INTERVAL_MS)
        }
    }
    return context
}

/**
 * Chips de contexto (Conexao/Banco/Sistema/Especialista) — sempre visiveis,
 * ver a especificacao do usuario. So mostra o que ha de fato: sem conexao
 * ativa, a lista fica vazia (nunca mostra chip com valor vazio/"null").
 */
@Composable
fun ContextHeader(context: AgentContext) {
    val chips = buildContextChips(context.connection())
    if (chips.isEmpty()) {
        return
    }
    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(chips) { (label, value) -> ContextChip(label, value) }
    }
}

private fun buildContextChips(connection: ConnectionContext): List<Pair<String, String>> {
    val chips = mutableListOf<Pair<String, String>>()
    connection.label()?.takeIf { it.isNotBlank() }?.let { chips += "Conexão" to it }
    connection.schema()?.takeIf { it.isNotBlank() }?.let { chips += "Banco" to it }
    connection.databaseProductName()?.takeIf { it.isNotBlank() }?.let { product ->
        val system = if (connection.databaseVersion().isNullOrBlank()) product else "$product ${connection.databaseVersion()}"
        chips += "Sistema" to system
        SpecialistRegistry.resolve(product).ifPresent { specialist -> chips += "Especialista" to specialist.displayName() }
    }
    return chips
}

/** Componente reutilizavel — MESMO estilo de card (fundo/cantos) de [CardContainer], em formato compacto de chip. */
@Composable
fun ContextChip(label: String, value: String) {
    Column(
        modifier = Modifier
            .background(CardTheme.cardBackground(), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = CardTheme.muted())
        Text(value, fontSize = 12.sp)
    }
}
