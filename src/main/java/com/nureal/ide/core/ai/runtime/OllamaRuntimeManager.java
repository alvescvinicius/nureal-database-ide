package com.nureal.ide.core.ai.runtime;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import com.nureal.ide.core.ai.provider.OllamaProvider;
import com.nureal.ide.core.log.AppLogger;

/**
 * Sobe/derruba o Ollama embutido como um subprocesso gerenciado. Chamado
 * sempre numa thread de background (nunca a EDT) — assim como
 * {@code LLMProvider}, nao assume nenhum comportamento de threading de quem
 * chama, so nunca bloqueia sozinho por conta propria alem do necessario.
 *
 * Principio de seguranca: NUNCA mata um Ollama que a propria IDE nao
 * iniciou (ver {@link #stopIfOwned()}) — um usuario que ja tinha um Ollama
 * seu rodando continua no controle dele.
 */
public final class OllamaRuntimeManager {

    private static final Duration HEALTH_POLL_INTERVAL = Duration.ofMillis(400);
    private static final Duration START_TIMEOUT = Duration.ofSeconds(15);

    private final AtomicReference<Process> ownedProcess = new AtomicReference<>();
    private final AtomicBoolean shutdownHookRegistered = new AtomicBoolean(false);

    /**
     * Garante que ha um Ollama saudavel em {@code baseUrl}: se ja houver
     * (do proprio usuario, ou de uma chamada anterior), nao faz nada; senao,
     * tenta subir o binario embutido (ou "ollama" via PATH, como fallback) e
     * espera ficar pronto. Lanca {@link OllamaStartException} (nao trava a
     * IDE — quem chama decide como mostrar o erro) se nao conseguir.
     */
    public void ensureRunning(String baseUrl, Consumer<String> onStatus) {
        OllamaProvider probe = new OllamaProvider(baseUrl, Duration.ofSeconds(3));
        if (probe.health()) {
            onStatus.accept("Ollama já está rodando.");
            return;
        }

        onStatus.accept("Iniciando o Ollama local...");
        Path bundled = OllamaBinaryLocator.locate().orElse(null);
        List<String> command = resolveCommand(bundled);

        Process process;
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.environment().put("OLLAMA_HOST", hostAndPort(baseUrl));
            pb.redirectErrorStream(true);
            process = pb.start();
        } catch (IOException e) {
            throw new OllamaStartException(
                    "Não foi possível iniciar o Ollama (nem embutido, nem instalado no PATH). "
                            + "Instale o Ollama manualmente ou aponte para um servidor externo nas "
                            + "configurações de IA.", e);
        }
        ownedProcess.set(process);
        registerShutdownHook();
        streamProcessOutput(process);

        waitUntilHealthy(probe, process);
        onStatus.accept("Ollama pronto.");
    }

    private void waitUntilHealthy(OllamaProvider probe, Process process) {
        long deadline = System.currentTimeMillis() + START_TIMEOUT.toMillis();
        while (System.currentTimeMillis() < deadline) {
            if (probe.health()) {
                return;
            }
            if (!process.isAlive()) {
                throw new OllamaStartException("O processo do Ollama encerrou inesperadamente ao iniciar.", null);
            }
            sleep(HEALTH_POLL_INTERVAL);
        }
        throw new OllamaStartException("O Ollama não respondeu a tempo ao iniciar.", null);
    }

    /** Para o processo, mas SOMENTE se foi esta instancia quem o iniciou (ver javadoc da classe). */
    public void stopIfOwned() {
        Process process = ownedProcess.getAndSet(null);
        if (process == null || !process.isAlive()) {
            return;
        }
        process.destroy();
        try {
            if (!process.waitFor(3, TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }
    }

    /** Pacote-visivel (nao private) para ser testavel sem precisar spawnar processo nenhum. */
    static List<String> resolveCommand(Path bundledBinary) {
        return bundledBinary != null
                ? List.of(bundledBinary.toString(), "serve")
                : List.of("ollama", "serve");
    }

    private void registerShutdownHook() {
        if (shutdownHookRegistered.compareAndSet(false, true)) {
            Runtime.getRuntime().addShutdownHook(new Thread(this::stopIfOwned, "ollama-shutdown"));
        }
    }

    private void streamProcessOutput(Process process) {
        Thread logThread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    AppLogger.fine("[ollama] " + line, null);
                }
            } catch (IOException ignored) {
                // processo encerrou/stream fechado — nada a fazer
            }
        }, "ollama-log");
        logThread.setDaemon(true);
        logThread.start();
    }

    private static String hostAndPort(String baseUrl) {
        URI uri = URI.create(baseUrl);
        int port = uri.getPort() > 0 ? uri.getPort() : 11434;
        String host = uri.getHost() != null ? uri.getHost() : "localhost";
        return host + ":" + port;
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Falha ao iniciar o Ollama embutido/local — mensagem sempre amigavel em PT-BR. */
    public static final class OllamaStartException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public OllamaStartException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
