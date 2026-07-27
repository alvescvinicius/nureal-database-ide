package com.nureal.ide.modulos.iachat.infraestrutura;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Testes de persistencia do {@link ChatHistoryStore}, mesmo espirito de um teste de {@code ExecutionHistoryStore}. */
class ChatHistoryStoreTest {

    private Path tempDir;
    private ChatHistoryStore store;

    @BeforeEach
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory("nureal-chat-history-test");
        store = new ChatHistoryStore(tempDir.resolve("chat-history.conf"));
    }

    @AfterEach
    void tearDown() throws IOException {
        Files.deleteIfExists(store.location());
        Files.deleteIfExists(tempDir);
    }

    @Test
    void loadAllVazioQuandoArquivoNaoExiste() throws IOException {
        assertTrue(store.loadAll().isEmpty());
    }

    @Test
    void appendMessageCriaConversaEAcumulaMensagens() throws IOException {
        store.appendMessage("c1", "Ola tudo bem?", "user", "Ola tudo bem?");
        store.appendMessage("c1", null, "assistant", "Tudo otimo!");

        ChatHistoryStore.Conversation conv = store.find("c1").orElseThrow();
        assertEquals("Ola tudo bem?", conv.title());
        assertEquals(2, conv.messages().size());
        assertEquals("user", conv.messages().get(0).role());
        assertEquals("Ola tudo bem?", conv.messages().get(0).content());
        assertEquals("assistant", conv.messages().get(1).role());
        assertEquals("Tudo otimo!", conv.messages().get(1).content());
    }

    @Test
    void preservaConteudoComQuebrasDeLinhaEBarraVertical() throws IOException {
        String content = "linha1\nlinha2 com | barra\nlinha3";
        store.appendMessage("c1", "titulo", "user", content);

        assertEquals(content, store.find("c1").orElseThrow().messages().get(0).content());
    }

    @Test
    void persisteEntreInstancias() throws IOException {
        store.appendMessage("c1", "titulo", "user", "oi");

        ChatHistoryStore reopened = new ChatHistoryStore(store.location());
        assertEquals(1, reopened.find("c1").orElseThrow().messages().size());
    }

    @Test
    void deleteRemoveApenasAConversaPedida() throws IOException {
        store.appendMessage("c1", "t1", "user", "oi");
        store.appendMessage("c2", "t2", "user", "oi2");

        store.delete("c1");

        assertTrue(store.find("c1").isEmpty());
        assertTrue(store.find("c2").isPresent());
    }

    @Test
    void clearRemoveTudo() throws IOException {
        store.appendMessage("c1", "t1", "user", "oi");
        store.appendMessage("c2", "t2", "user", "oi2");

        store.clear();

        assertTrue(store.loadAll().isEmpty());
    }
}
