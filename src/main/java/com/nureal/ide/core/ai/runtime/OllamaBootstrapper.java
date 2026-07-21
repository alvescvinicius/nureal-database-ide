package com.nureal.ide.core.ai.runtime;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import com.nureal.ide.core.ai.config.AiPreferences;
import com.nureal.ide.core.ai.provider.OllamaProvider;
import com.nureal.ide.core.ai.provider.ProviderException;
import com.nureal.ide.core.ai.provider.PullEvent;

/**
 * Orquestra o "modo embutido" do Ollama: garante o processo rodando (ver
 * {@link OllamaRuntimeManager}) e, se nenhum modelo estiver instalado nem
 * configurado, baixa o {@link AiPreferences#DEFAULT_MODEL} automaticamente.
 * Sempre chamado numa thread de background (nunca a EDT) — Swing-free, so
 * devolve texto de status via {@code onStatus} pra quem chama decidir como
 * mostrar (ver {@code ui.ai}).
 */
public final class OllamaBootstrapper {

    private final OllamaRuntimeManager runtimeManager;

    public OllamaBootstrapper(OllamaRuntimeManager runtimeManager) {
        this.runtimeManager = runtimeManager;
    }

    /**
     * Garante o Ollama embutido pronto pra uso. Devolve o nome do modelo
     * padrao SE acabou de baixá-lo agora (quem chama deve persistir isso em
     * {@link AiPreferences}), ou {@code null} se nada mudou (modelo já
     * configurado, ou já havia algum instalado). Lanca
     * {@link OllamaRuntimeManager.OllamaStartException} ou
     * {@link ProviderException} em caso de falha — nunca trava a IDE, quem
     * chama decide como reportar.
     */
    public String ensureReady(AiPreferences.State prefs, Consumer<String> onStatus) {
        runtimeManager.ensureRunning(prefs.baseUrl(), onStatus);

        if (prefs.model() != null && !prefs.model().isBlank()) {
            return null;
        }
        OllamaProvider provider = new OllamaProvider(prefs.baseUrl(), Duration.ofSeconds(prefs.timeoutSeconds()));
        if (!provider.listModels().isEmpty()) {
            return null;
        }
        return pullDefaultModel(provider, onStatus);
    }

    private String pullDefaultModel(OllamaProvider provider, Consumer<String> onStatus) {
        String model = AiPreferences.DEFAULT_MODEL;
        onStatus.accept("Baixando modelo " + model + " pela primeira vez...");

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<ProviderException> failure = new AtomicReference<>();
        provider.pullModel(model, event -> {
            if (event instanceof PullEvent.Progress p) {
                String pct = p.percent() >= 0 ? " " + p.percent() + "%" : "";
                onStatus.accept("Baixando modelo " + model + "..." + pct);
            } else if (event instanceof PullEvent.Completed) {
                latch.countDown();
            } else if (event instanceof PullEvent.Failed f) {
                failure.set(f.error());
                latch.countDown();
            } else if (event instanceof PullEvent.Cancelled) {
                latch.countDown();
            }
        });
        awaitUninterruptibly(latch);

        if (failure.get() != null) {
            throw failure.get();
        }
        onStatus.accept("Modelo " + model + " pronto.");
        return model;
    }

    private static void awaitUninterruptibly(CountDownLatch latch) {
        boolean interrupted = false;
        while (true) {
            try {
                latch.await();
                break;
            } catch (InterruptedException e) {
                interrupted = true;
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
