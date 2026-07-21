package com.nureal.ide.core.ai.provider;

/**
 * Erro ao falar com um {@link LLMProvider}. Sempre com mensagem amigavel em
 * PT-BR (mostrada direto na UI do chat) — o detalhe tecnico, se houver, vai
 * na causa ({@link #getCause()}), nunca na mensagem exibida ao usuario.
 */
public abstract class ProviderException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    protected ProviderException(String message) {
        super(message);
    }

    protected ProviderException(String message, Throwable cause) {
        super(message, cause);
    }

    /** Nao foi possivel abrir conexao com o provider (ex.: Ollama nao esta rodando, ou sem internet pra um provider em nuvem). */
    public static final class ConnectionError extends ProviderException {
        private static final long serialVersionUID = 1L;

        public ConnectionError(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** A requisicao excedeu o tempo limite configurado. */
    public static final class Timeout extends ProviderException {
        private static final long serialVersionUID = 1L;

        public Timeout(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** O modelo pedido nao existe (nao instalado no Ollama, ou nome invalido/sem acesso no provider em nuvem). */
    public static final class InvalidModel extends ProviderException {
        private static final long serialVersionUID = 1L;

        public InvalidModel(String message) {
            super(message);
        }
    }

    /** Nenhuma API key configurada pra este provider (ver Configuracoes de IA). */
    public static final class MissingCredential extends ProviderException {
        private static final long serialVersionUID = 1L;

        public MissingCredential(String message) {
            super(message);
        }
    }

    /** O provider respondeu, mas indicou que nao pode atender agora (ex.: HTTP 503). */
    public static final class ProviderUnavailable extends ProviderException {
        private static final long serialVersionUID = 1L;

        public ProviderUnavailable(String message) {
            super(message);
        }
    }

    /** Resposta em formato inesperado (JSON invalido, campos ausentes, status HTTP nao mapeado). */
    public static final class UnexpectedResponse extends ProviderException {
        private static final long serialVersionUID = 1L;

        public UnexpectedResponse(String message) {
            super(message);
        }

        public UnexpectedResponse(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
