package com.nureal.ide.core.ai.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Testes puros de resolucao de caminho — sem depender de nenhum binario
 * Ollama real nem de propriedades de sistema globais (usa o overload
 * pacote-visivel {@code locate(String, String)}).
 */
class OllamaBinaryLocatorTest {

    private Path tempDir;

    @AfterEach
    void cleanUp() throws IOException {
        if (tempDir != null && Files.exists(tempDir)) {
            Files.walk(tempDir)
                    .sorted((a, b) -> b.getNameCount() - a.getNameCount())
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException ignored) {
                            // limpeza de teste — nao critico se falhar
                        }
                    });
        }
    }

    @Test
    void semAppPathDevolveVazio() {
        assertTrue(OllamaBinaryLocator.locate(null, "Windows 10").isEmpty());
        assertTrue(OllamaBinaryLocator.locate("", "Windows 10").isEmpty());
        assertTrue(OllamaBinaryLocator.locate("   ", "Windows 10").isEmpty());
    }

    @Test
    void appPathSemBinarioNoLugarEsperadoDevolveVazio() throws IOException {
        tempDir = Files.createTempDirectory("nureal-ollama-locator-test");
        Path launcher = tempDir.resolve("Nureal Database IDE.exe");
        Files.createFile(launcher);
        // nao cria app/ollama-bin/ollama.exe de proposito

        assertTrue(OllamaBinaryLocator.locate(launcher.toString(), "Windows 10").isEmpty());
    }

    @Test
    void windowsAcharBinarioEmAppOllamaBin() throws IOException {
        tempDir = Files.createTempDirectory("nureal-ollama-locator-test");
        Path launcher = tempDir.resolve("Nureal Database IDE.exe");
        Files.createFile(launcher);
        Path expectedBinary = Files.createDirectories(tempDir.resolve("app").resolve("ollama-bin"))
                .resolve("ollama.exe");
        Files.createFile(expectedBinary);

        Optional<Path> found = OllamaBinaryLocator.locate(launcher.toString(), "Windows 10");
        assertTrue(found.isPresent());
        assertEquals(expectedBinary, found.get());
    }

    @Test
    void linuxAcharBinarioSemExtensao() throws IOException {
        tempDir = Files.createTempDirectory("nureal-ollama-locator-test");
        Path launcher = tempDir.resolve("Nureal Database IDE");
        Files.createFile(launcher);
        Path expectedBinary = Files.createDirectories(tempDir.resolve("app").resolve("ollama-bin"))
                .resolve("ollama");
        Files.createFile(expectedBinary);

        Optional<Path> found = OllamaBinaryLocator.locate(launcher.toString(), "Linux");
        assertTrue(found.isPresent());
        assertEquals(expectedBinary, found.get());
    }

    @Test
    void macSobeUmNivelExtraAntesDeAppOllamaBin() throws IOException {
        // Layout simulado: <root>/Contents/MacOS/<launcher> e
        // <root>/Contents/app/ollama-bin/ollama
        tempDir = Files.createTempDirectory("nureal-ollama-locator-test");
        Path contents = tempDir.resolve("Contents");
        Path macOsDir = Files.createDirectories(contents.resolve("MacOS"));
        Path launcher = macOsDir.resolve("Nureal Database IDE");
        Files.createFile(launcher);
        Path expectedBinary = Files.createDirectories(contents.resolve("app").resolve("ollama-bin"))
                .resolve("ollama");
        Files.createFile(expectedBinary);

        Optional<Path> found = OllamaBinaryLocator.locate(launcher.toString(), "Mac OS X");
        assertTrue(found.isPresent());
        assertEquals(expectedBinary, found.get());
    }
}
