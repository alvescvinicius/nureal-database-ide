package com.nureal.ide.chatui

import androidx.compose.material.Text
import androidx.compose.ui.window.singleWindowApplication

/**
 * Spike descartavel: so valida que o toolchain Kotlin + Compose Multiplatform
 * Desktop (via Gradle, isolado do build Maven principal) compila e abre uma
 * janela de verdade, antes de portar o chat de verdade pra ca.
 */
fun main() = singleWindowApplication(title = "Nureal Chat UI - Smoke Test") {
    Text("Hello from Compose Desktop!")
}
