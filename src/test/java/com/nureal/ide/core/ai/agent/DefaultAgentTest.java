package com.nureal.ide.core.ai.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.nureal.ide.core.ai.config.AiPreferences;
import com.nureal.ide.core.ai.context.AgentContext;
import com.nureal.ide.core.ai.history.ChatHistoryStore;
import com.nureal.ide.core.ai.provider.AiEvent;
import com.nureal.ide.core.ai.provider.ChatMessage;
import com.nureal.ide.core.ai.provider.ChatRequest;
import com.nureal.ide.core.ai.provider.ChatResponse;
import com.nureal.ide.core.ai.provider.ChatUsage;
import com.nureal.ide.core.ai.provider.LLMProvider;
import com.nureal.ide.core.ai.provider.ProviderException;
import com.nureal.ide.core.ai.provider.ProviderType;
import com.nureal.ide.core.ai.provider.ToolCall;
import com.nureal.ide.core.ai.tool.Tool;
import com.nureal.ide.core.ai.tool.ToolExecutor;
import com.nureal.ide.core.ai.tool.ToolRequest;
import com.nureal.ide.core.ai.tool.ToolResult;

/**
 * Testes do {@link DefaultAgent} contra um {@link LLMProvider} fake e
 * sincrono (sem threads reais — o Agent nao assume nenhum comportamento de
 * threading do provider, entao um fake sincrono e suficiente e deixa o
 * teste deterministico).
 */
class DefaultAgentTest {

    private Path tempDir;
    private ChatHistoryStore historyStore;

    @BeforeEach
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory("nureal-agent-test");
        historyStore = new ChatHistoryStore(tempDir.resolve("chat-history.conf"));
    }

    @AfterEach
    void tearDown() throws IOException {
        Files.deleteIfExists(historyStore.location());
        Files.deleteIfExists(tempDir);
    }

    private static AiPreferences.State prefs() {
        return new AiPreferences.State(ProviderType.OLLAMA, "http://localhost:11434", "llama3.1", 0.2, 60, true);
    }

    @Test
    void chatSimplesEmiteEventosESalvaHistorico() throws IOException {
        FakeProvider provider = new FakeProvider();
        provider.enqueueTextResponse("Ola! Como posso ajudar?");

        DefaultAgent agent = new DefaultAgent(provider, () -> AgentContext.EMPTY, new ToolExecutor(List.of()),
                historyStore, DefaultAgentTest::prefs);

        List<AiEvent> events = new ArrayList<>();
        agent.chat("conv1", "oi", events::add);

        AiEvent last = events.get(events.size() - 1);
        assertInstanceOf(AiEvent.Completed.class, last);
        assertEquals("Ola! Como posso ajudar?", ((AiEvent.Completed) last).response().message().content());

        ChatHistoryStore.Conversation conv = historyStore.find("conv1").orElseThrow();
        assertEquals(2, conv.messages().size());
        assertEquals("user", conv.messages().get(0).role());
        assertEquals("oi", conv.messages().get(0).content());
        assertEquals("assistant", conv.messages().get(1).role());
        assertEquals("Ola! Como posso ajudar?", conv.messages().get(1).content());
    }

    @Test
    void toolCallExecutaToolESoReportaARespostaFinal() throws IOException {
        FakeProvider provider = new FakeProvider();
        provider.enqueueToolCallResponse("call1", "echo_tool", Map.of("value", "abc"));
        provider.enqueueTextResponse("O valor e abc.");

        Tool echoTool = new Tool() {
            @Override
            public String getName() {
                return "echo_tool";
            }

            @Override
            public String getDescription() {
                return "ecoa o valor recebido";
            }

            @Override
            public Map<String, Object> getParametersSchema() {
                return Map.of();
            }

            @Override
            public ToolResult execute(ToolRequest request) {
                return ToolResult.ok("valor=" + request.arguments().get("value"), null, 1);
            }
        };

        DefaultAgent agent = new DefaultAgent(provider, () -> AgentContext.EMPTY,
                new ToolExecutor(List.of(echoTool)), historyStore, DefaultAgentTest::prefs);

        List<AiEvent> events = new ArrayList<>();
        agent.chat("conv2", "qual o valor?", events::add);

        AiEvent last = events.get(events.size() - 1);
        assertInstanceOf(AiEvent.Completed.class, last);
        assertEquals("O valor e abc.", ((AiEvent.Completed) last).response().message().content());

        long completions = events.stream().filter(e -> e instanceof AiEvent.Completed).count();
        assertEquals(1, completions, "a rodada de tool nao deve vazar como uma resposta final separada");

        assertEquals(2, provider.requestsSent.size(), "uma chamada para decidir a tool, outra para a resposta final");
        ChatRequest secondRequest = provider.requestsSent.get(1);
        boolean hasToolResultMessage = secondRequest.messages().stream()
                .anyMatch(m -> m.role().equals(ChatMessage.ROLE_TOOL) && m.content().equals("valor=abc"));
        assertTrue(hasToolResultMessage, "a segunda chamada deve incluir o resultado da tool no historico de mensagens");

        ChatHistoryStore.Conversation conv = historyStore.find("conv2").orElseThrow();
        assertEquals(2, conv.messages().size(), "so a pergunta do usuario e a resposta final devem ir pro historico");
    }

    @Test
    void cancelPropagaParaOProviderDaRequisicaoAtiva() {
        FakeProvider provider = new FakeProvider();
        provider.blockUntilCancelled = true;

        DefaultAgent agent = new DefaultAgent(provider, () -> AgentContext.EMPTY, new ToolExecutor(List.of()),
                historyStore, DefaultAgentTest::prefs);

        String turnId = agent.chat("conv3", "oi", event -> { });
        agent.cancel(turnId);

        assertTrue(provider.cancelledRequestIds.contains(provider.lastRequestId));
    }

    @Test
    void semModeloConfiguradoFalhaImediatamente() {
        FakeProvider provider = new FakeProvider();
        DefaultAgent agent = new DefaultAgent(provider, () -> AgentContext.EMPTY, new ToolExecutor(List.of()),
                historyStore, () -> new AiPreferences.State(ProviderType.OLLAMA, "http://localhost:11434", "", 0.2, 60, true));

        List<AiEvent> events = new ArrayList<>();
        agent.chat("conv4", "oi", events::add);

        assertInstanceOf(AiEvent.Failed.class, events.get(events.size() - 1));
        assertTrue(provider.requestsSent.isEmpty(), "nao deveria nem chamar o provider sem modelo configurado");
    }

    /** Provider fake e sincrono: entrega os eventos na mesma thread da chamada. */
    private static final class FakeProvider implements LLMProvider {
        final Deque<ChatResponse> queuedResponses = new ArrayDeque<>();
        final List<ChatRequest> requestsSent = new ArrayList<>();
        final List<String> cancelledRequestIds = new ArrayList<>();
        String lastRequestId;
        boolean blockUntilCancelled;

        void enqueueTextResponse(String text) {
            queuedResponses.add(new ChatResponse(new ChatMessage(ChatMessage.ROLE_ASSISTANT, text), "stop",
                    ChatUsage.EMPTY, List.of()));
        }

        void enqueueToolCallResponse(String callId, String toolName, Map<String, Object> args) {
            queuedResponses.add(new ChatResponse(new ChatMessage(ChatMessage.ROLE_ASSISTANT, ""), "tool_calls",
                    ChatUsage.EMPTY, List.of(new ToolCall(callId, toolName, args))));
        }

        @Override
        public boolean health() {
            return true;
        }

        @Override
        public List<String> listModels() {
            return List.of();
        }

        @Override
        public ChatResponse chat(ChatRequest request) {
            throw new UnsupportedOperationException("DefaultAgent deveria usar stream(), nao chat()");
        }

        @Override
        public String stream(ChatRequest request, Consumer<AiEvent> onEvent) {
            requestsSent.add(request);
            String requestId = UUID.randomUUID().toString();
            lastRequestId = requestId;
            onEvent.accept(new AiEvent.Started(requestId));
            if (blockUntilCancelled) {
                return requestId;
            }
            ChatResponse response = queuedResponses.poll();
            if (response == null) {
                onEvent.accept(new AiEvent.Failed(requestId,
                        new ProviderException.UnexpectedResponse("sem resposta enfileirada no fake")));
                return requestId;
            }
            if (!response.message().content().isEmpty()) {
                onEvent.accept(new AiEvent.Chunk(requestId, response.message().content()));
            }
            onEvent.accept(new AiEvent.Completed(requestId, response));
            return requestId;
        }

        @Override
        public void cancel(String requestId) {
            cancelledRequestIds.add(requestId);
        }
    }
}
