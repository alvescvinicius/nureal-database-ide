package com.nureal.ide.modulos.iachat.apresentacao;

import java.util.List;
import java.util.function.Consumer;

import javax.swing.JComponent;

import com.nureal.ide.modulos.iachat.dominio.contratos.Agent;
import com.nureal.ide.modulos.iachat.infraestrutura.ChatHistoryStore;

/**
 * Fabrica do Chat de IA embutido: ja foi uma janela {@code JDialog}
 * flutuante SINGLETON, depois virou uma aba de {@code MainWindow#editorTabs}
 * (Fase 2 do {@code AI-CHAT-MASTER-PLAN.md}) — revisao de UX trocou por um
 * PAINEL fixo na borda direita da janela ({@code MainWindow#chatDock}, ver
 * {@code MainWindow#openAiChat}), pedido explicito do usuario ("chat abrindo
 * em aba do editor ao inves de abrir uma janela na direita"). Quem controla
 * "so uma instancia aberta por vez"/mostrar-esconder e sempre quem chama
 * ({@code MainWindow}), nao esta classe. Continua existindo como fabrica
 * (constroi {@link ChatPanel}+{@link ChatController} a partir dos mesmos
 * argumentos de antes) porque {@link ChatPanel}/{@link ChatController} sao
 * package-private — {@code MainWindow} (pacote {@code ui}, diferente de
 * {@code modulos.iachat.apresentacao}) precisa deste unico ponto de entrada
 * publico.
 */
public final class ChatWindow {

    private final ChatController controller;
    private final ChatPanel panel;

    public ChatWindow(Agent agent, ChatHistoryStore historyStore, String conversationId, Runnable onOpenSettings,
            ChatActions actions) {
        this.panel = new ChatPanel();
        panel.setOnOpenSettings(onOpenSettings);
        this.controller = new ChatController(panel, agent, historyStore, conversationId, actions);
    }

    /** Componente Swing pronto pra embutir no dock do chat (ver {@code MainWindow#openAiChat}). */
    public JComponent component() {
        return panel;
    }

    /** Chamado apos salvar as configuracoes de IA, para esta instancia usar o {@link Agent} atualizado. */
    public void updateAgent(Agent agent) {
        controller.updateAgent(agent);
    }

    /**
     * Chamado por {@code MainWindow#toggleTheme} — os cards do chat (ver
     * {@code MessageRenderer}) tem cores proprias presas no tema de quando
     * foram renderizados.
     */
    public void refreshTheme() {
        controller.refreshTheme();
    }

    /** "+ Novo Chat" (ver {@code ChatController#startNewConversation}) — o historico anterior continua salvo. */
    public void startNewConversation() {
        controller.startNewConversation();
    }

    /** Preenche o combo de modelo do topo (ver {@code ChatPanel#setModelOptions}). */
    public void setModelOptions(List<String> models, String selected) {
        panel.setModelOptions(models, selected);
    }

    /** Disparado quando o usuario escolhe um modelo diferente no combo (ver {@code ChatPanel#setOnModelChange}). */
    public void setOnModelChange(Consumer<String> onModelChange) {
        panel.setOnModelChange(onModelChange);
    }

    /** Botao "+ Novo Chat" (ver {@code ChatPanel#setOnNewChat}). */
    public void setOnNewChat(Runnable onNewChat) {
        panel.setOnNewChat(onNewChat);
    }

    /** Selo de esquema/conexao ativa no topo (ver {@code ChatPanel#setSchemaLabel}). */
    public void setSchemaLabel(String label) {
        panel.setSchemaLabel(label);
    }
}
