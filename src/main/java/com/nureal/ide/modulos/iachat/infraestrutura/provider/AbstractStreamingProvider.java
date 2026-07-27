package com.nureal.ide.modulos.iachat.infraestrutura.provider;
import com.nureal.ide.modulos.iachat.dominio.contratos.LLMProvider;
import com.nureal.ide.modulos.iachat.dominio.entidades.ChatRequest;
import com.nureal.ide.modulos.iachat.dominio.contratos.ConversationSession;
import com.nureal.ide.modulos.iachat.dominio.entidades.AiEvent;
import com.nureal.ide.modulos.iachat.dominio.entidades.ProviderException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Boilerplate comum a todo {@link LLMProvider} que faz streaming via HTTP: executor
 * de background dedicado, mapa de requisicoes em andamento (pra {@link #cancel}) e o
 * evento {@link AiEvent.Started} — tudo isto vivia so dentro do {@code OllamaProvider}
 * antes de existir um segundo provider; agora que ha varios, virou a base comum.
 *
 * Cada subclasse so implementa {@link #doStream}: monta a requisicao, le a resposta
 * (streaming) e emite os {@link AiEvent} — inclusive tratando suas PROPRIAS excecoes
 * de IO/timeout/conexao com mensagens amigaveis especificas do provider (o nome do
 * servico na mensagem de erro muda por provider, entao fica melhor cada um cuidar
 * disso do que a base tentar generalizar).
 */
abstract class AbstractStreamingProvider implements LLMProvider {

    protected final Duration timeout;
    protected final HttpClient httpClient;

    private final ExecutorService streamExecutor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "llm-stream");
        t.setDaemon(true);
        return t;
    });
    private final Map<String, AtomicBoolean> inFlight = new ConcurrentHashMap<>();

    protected AbstractStreamingProvider(Duration timeout) {
        this.timeout = timeout;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .build();
    }

    @Override
    public final String stream(ChatRequest request, Consumer<AiEvent> onEvent) {
        return submitStreamTask(onEvent, (requestId, ev, cancelled) -> doStream(requestId, request, ev, cancelled));
    }

    @Override
    public final void cancel(String requestId) {
        AtomicBoolean flag = inFlight.get(requestId);
        if (flag != null) {
            flag.set(true);
        }
    }

    /**
     * Reusa o mesmo executor/mapa de cancelamento/evento {@link AiEvent.Started}
     * de {@link #stream} pra qualquer outra chamada assincrona de um provider —
     * usado por {@link ConversationSession}s (ex.: {@code ClaudeSession},
     * {@code GeminiSession}) que precisam disparar rodadas de tool-calling
     * adicionais fora do fluxo normal de {@link #stream(ChatRequest, Consumer)},
     * sem duplicar esse boilerplate.
     */
    protected final String submitStreamTask(Consumer<AiEvent> onEvent, StreamTask task) {
        String requestId = UUID.randomUUID().toString();
        AtomicBoolean cancelled = new AtomicBoolean(false);
        inFlight.put(requestId, cancelled);
        streamExecutor.submit(() -> {
            onEvent.accept(new AiEvent.Started(requestId));
            try {
                task.run(requestId, onEvent, cancelled);
            } catch (RuntimeException unexpected) {
                onEvent.accept(new AiEvent.Failed(requestId, new ProviderException.UnexpectedResponse(
                        "Erro inesperado ao conversar com " + providerName() + ": " + unexpected.getMessage(),
                        unexpected)));
            } finally {
                inFlight.remove(requestId);
            }
        });
        return requestId;
    }

    /** Nome amigavel do provider, usado em mensagens de erro genericas (ex.: "Claude", "OpenAI"). */
    protected abstract String providerName();

    /**
     * Implementacoes devem tratar suas proprias excecoes de IO/timeout/conexao
     * internamente (emitindo {@link AiEvent.Failed} com mensagem amigavel) e nunca
     * deixar uma excecao checada escapar — este metodo so retorna normalmente,
     * sucesso ou falha ja tendo sido reportados via {@code onEvent}.
     */
    protected abstract void doStream(String requestId, ChatRequest request, Consumer<AiEvent> onEvent,
            AtomicBoolean cancelled);

    /** Uma chamada assincrona submetida via {@link #submitStreamTask}. */
    @FunctionalInterface
    protected interface StreamTask {
        void run(String requestId, Consumer<AiEvent> onEvent, AtomicBoolean cancelled);
    }
}
