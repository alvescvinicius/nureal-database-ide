package com.nureal.ide.core.backup;

import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Consumer;

import com.nureal.ide.core.backup.MySqlDumpRunner.BackupOptions;
import com.nureal.ide.core.backup.MySqlDumpRunner.RestoreOptions;
import com.nureal.ide.core.backup.MySqlDumpRunner.RunResult;

/**
 * Backup e restauracao de um schema — extraido de {@link MySqlDumpRunner}
 * (ver .specs/09-modulo-backup-exportacao.md, regra 2) para permitir, no
 * futuro, uma implementacao por banco (ex.: {@code pg_dump} para Postgres)
 * sem alterar quem consome. {@link MySqlDumpRunner} continua dependente de
 * binarios externos via {@link ProcessBuilder} — isso e uma caracteristica
 * esperada de infraestrutura, nao um problema a resolver.
 */
public interface BackupPort {

    RunResult backup(BackupOptions opts, Path outputFile, Consumer<String> onLogLine)
            throws IOException, InterruptedException;

    RunResult restore(RestoreOptions opts, Path inputFile, Consumer<String> onLogLine)
            throws IOException, InterruptedException;
}
