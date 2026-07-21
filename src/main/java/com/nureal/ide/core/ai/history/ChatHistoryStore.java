package com.nureal.ide.core.ai.history;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Persiste o historico de conversas do chat de IA em
 *   ~/.nureal-ide/chat-history.conf
 *
 * Mesma estrategia de leitura-tudo-regrava-tudo de {@code ExecutionHistoryStore}
 * (aceitavel pelo mesmo motivo: poucas centenas de linhas). Cada conversa e um
 * bloco {@code [conversation]} com metadados em {@code key=value} e uma linha
 * {@code msg=} por mensagem ({@code role|timestamp|conteudoBase64} — Base64
 * pelo mesmo motivo do historico de execucoes: preservar quebras de linha
 * dentro do formato flat).
 */
public class ChatHistoryStore {

    /** Conversas mais antigas somem quando o total passa disso. */
    private static final int MAX_CONVERSATIONS = 50;
    /** Mensagens mais antigas de uma conversa somem quando o total passa disso. */
    private static final int MAX_MESSAGES_PER_CONVERSATION = 200;

    private static final String DIR_NAME = ".nureal-ide";
    private static final String FILE_NAME = "chat-history.conf";
    private static final String HEADER = "[conversation]";

    private final Path file;

    public ChatHistoryStore() {
        this(Paths.get(System.getProperty("user.home"), DIR_NAME, FILE_NAME));
    }

    public ChatHistoryStore(Path file) {
        this.file = file;
    }

    public Path location() {
        return file;
    }

    public record StoredMessage(String role, String content, long timestamp) {
    }

    public record Conversation(String id, String title, long createdAt, long updatedAt,
                                List<StoredMessage> messages) {
        public Conversation {
            messages = List.copyOf(messages);
        }
    }

    /** Le todas as conversas. Vazio se o arquivo ainda nao existe. */
    public synchronized List<Conversation> loadAll() throws IOException {
        List<Conversation> out = new ArrayList<>();
        if (!Files.exists(file)) {
            return out;
        }
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);

        String id = null;
        String title = null;
        long createdAt = 0;
        long updatedAt = 0;
        List<StoredMessage> messages = new ArrayList<>();
        boolean open = false;

        for (String raw : lines) {
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (line.equals(HEADER)) {
                flush(out, id, title, createdAt, updatedAt, messages);
                open = true;
                id = null;
                title = null;
                createdAt = 0;
                updatedAt = 0;
                messages = new ArrayList<>();
                continue;
            }
            if (!open) {
                continue;
            }
            if (line.startsWith("msg=")) {
                StoredMessage message = parseMessage(line.substring("msg=".length()));
                if (message != null) {
                    messages.add(message);
                }
                continue;
            }
            int eq = line.indexOf('=');
            if (eq < 0) {
                continue;
            }
            String key = line.substring(0, eq).trim();
            String value = line.substring(eq + 1);
            switch (key) {
                case "id" -> id = value.trim();
                case "title" -> title = decode(value.trim());
                case "createdAt" -> createdAt = parseLong(value.trim());
                case "updatedAt" -> updatedAt = parseLong(value.trim());
                default -> {
                    // ignora chaves desconhecidas (versoes futuras)
                }
            }
        }
        flush(out, id, title, createdAt, updatedAt, messages);
        return out;
    }

    private static void flush(List<Conversation> out, String id, String title, long createdAt, long updatedAt,
            List<StoredMessage> messages) {
        if (id == null) {
            return;
        }
        out.add(new Conversation(id, title == null ? "" : title, createdAt, updatedAt, messages));
    }

    private static StoredMessage parseMessage(String encoded) {
        String[] parts = encoded.split("\\|", 3);
        if (parts.length != 3) {
            return null;
        }
        return new StoredMessage(parts[0], decode(parts[2]), parseLong(parts[1]));
    }

    /** Grava a lista inteira, criando a pasta se necessario. */
    public synchronized void saveAll(List<Conversation> conversations) throws IOException {
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        StringBuilder sb = new StringBuilder();
        sb.append("# Nureal Database IDE - historico de conversas do chat de IA\n");
        sb.append("# titulo e conteudo das mensagens em Base64 para preservar quebras de linha.\n\n");
        for (Conversation c : conversations) {
            sb.append(HEADER).append('\n');
            sb.append("id=").append(c.id()).append('\n');
            sb.append("title=").append(encode(c.title())).append('\n');
            sb.append("createdAt=").append(c.createdAt()).append('\n');
            sb.append("updatedAt=").append(c.updatedAt()).append('\n');
            for (StoredMessage m : c.messages()) {
                sb.append("msg=").append(m.role()).append('|').append(m.timestamp()).append('|')
                        .append(encode(m.content())).append('\n');
            }
            sb.append('\n');
        }
        Files.write(file, sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Acrescenta uma mensagem a conversa (criando-a se ainda nao existir).
     * Descarta mensagens/conversas mais antigas se passarem dos limites.
     */
    public synchronized Conversation appendMessage(String conversationId, String title, String role, String content)
            throws IOException {
        List<Conversation> all = loadAll();
        Conversation existing = all.stream().filter(c -> c.id().equals(conversationId)).findFirst().orElse(null);

        long now = System.currentTimeMillis();
        List<StoredMessage> messages = new ArrayList<>(existing == null ? List.of() : existing.messages());
        messages.add(new StoredMessage(role, content, now));
        if (messages.size() > MAX_MESSAGES_PER_CONVERSATION) {
            messages = new ArrayList<>(messages.subList(messages.size() - MAX_MESSAGES_PER_CONVERSATION, messages.size()));
        }

        String resolvedTitle = existing != null ? existing.title()
                : (title == null || title.isBlank() ? "Nova conversa" : title);
        Conversation updated = new Conversation(conversationId, resolvedTitle,
                existing != null ? existing.createdAt() : now, now, messages);

        all.removeIf(c -> c.id().equals(conversationId));
        all.add(updated);
        if (all.size() > MAX_CONVERSATIONS) {
            all.sort(Comparator.comparingLong(Conversation::updatedAt));
            all = new ArrayList<>(all.subList(all.size() - MAX_CONVERSATIONS, all.size()));
        }
        saveAll(all);
        return updated;
    }

    public synchronized Optional<Conversation> find(String conversationId) throws IOException {
        return loadAll().stream().filter(c -> c.id().equals(conversationId)).findFirst();
    }

    public synchronized void delete(String conversationId) throws IOException {
        List<Conversation> all = loadAll();
        all.removeIf(c -> c.id().equals(conversationId));
        saveAll(all);
    }

    public synchronized void clear() throws IOException {
        saveAll(new ArrayList<>());
    }

    private static String encode(String plain) {
        return Base64.getEncoder().encodeToString(plain.getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(String encoded) {
        try {
            return new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return "";
        }
    }

    private static long parseLong(String s) {
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
