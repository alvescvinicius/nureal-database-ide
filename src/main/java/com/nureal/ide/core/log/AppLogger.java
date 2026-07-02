package com.nureal.ide.core.log;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/**
 * Logger centralizado da aplicacao, baseado em {@code java.util.logging}
 * (ja incluso no JDK, sem dependencia nova).
 *
 * Grava em {@code ~/.nureal-ide/nureal-ide.log} (rotacionado, ate 3 arquivos
 * de 1 MB cada). Serve para registrar a causa raiz de erros que hoje so
 * aparecem num dialogo e sao descartados — util para diagnosticar problemas
 * que o usuario nao reportou na hora.
 *
 * Uso tipico num catch generico:
 * <pre>
 *     } catch (Exception ex) {
 *         AppLogger.warning("Falha ao salvar preferencias de UI", ex);
 *         ...
 *     }
 * </pre>
 */
public final class AppLogger {

    private static final String DIR_NAME = ".nureal-ide";
    private static final String LOG_FILE_NAME = "nureal-ide.log";
    private static final Logger LOGGER = Logger.getLogger("com.nureal.ide");

    private static volatile boolean initialized = false;

    private AppLogger() {
    }

    /**
     * Inicializa o handler de arquivo (uma vez por execucao). Chamado no
     * inicio de {@code App.main}. Se por algum motivo o arquivo de log nao
     * puder ser criado (ex.: sem permissao na pasta do usuario), a aplicacao
     * continua normalmente — so fica sem persistir os logs em disco.
     */
    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        try {
            Path dir = Paths.get(System.getProperty("user.home"), DIR_NAME);
            Files.createDirectories(dir);
            Path logFile = dir.resolve(LOG_FILE_NAME);
            FileHandler handler = new FileHandler(logFile.toString(), 1_000_000, 3, true);
            handler.setFormatter(new SimpleFormatter());
            handler.setLevel(Level.ALL);
            LOGGER.addHandler(handler);
            LOGGER.setLevel(Level.ALL);
            LOGGER.setUseParentHandlers(false);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Nao foi possivel criar o arquivo de log em disco", e);
        }
    }

    /** Erro que impediu uma operacao de completar (geralmente mostrado ao usuario tambem). */
    public static void warning(String message, Throwable t) {
        LOGGER.log(Level.WARNING, message, t);
    }

    /** Erro grave / inesperado. */
    public static void severe(String message, Throwable t) {
        LOGGER.log(Level.SEVERE, message, t);
    }

    /** Situacao esperada/tolerada (ex.: fallback silencioso), so para rastreabilidade. */
    public static void fine(String message, Throwable t) {
        LOGGER.log(Level.FINE, message, t);
    }
}
