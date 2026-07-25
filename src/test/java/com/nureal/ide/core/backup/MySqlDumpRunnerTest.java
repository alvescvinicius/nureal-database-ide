package com.nureal.ide.core.backup;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Caracteriza {@link MySqlDumpRunner} apos ele passar a ser instanciavel
 * implementando {@link BackupPort} (ver
 * .specs/09-modulo-backup-exportacao.md, regra 2) — nao invoca um
 * {@code mysqldump}/{@code mysql} real (exigiria os binarios instalados no
 * ambiente de build), apenas confirma que um executavel inexistente falha
 * de forma previsivel (IOException), o mesmo comportamento de antes da
 * migracao de estatico para instancia.
 */
class MySqlDumpRunnerTest {

	private final BackupPort backupPort = new MySqlDumpRunner();

	@Test
	void backupComExecutavelInexistenteLancaIOException(@TempDir Path dir) {
		MySqlDumpRunner.ConnectionTarget target = new MySqlDumpRunner.ConnectionTarget("localhost", 3306, "root", "");
		MySqlDumpRunner.BackupOptions options = new MySqlDumpRunner.BackupOptions(
				"nureal-bogus-executable-que-nao-existe", target, "app", List.of(), false, false, false, true);
		Path outputFile = dir.resolve("saida.sql");

		assertThrows(IOException.class, () -> backupPort.backup(options, outputFile, line -> { }));
	}

	@Test
	void restoreComExecutavelInexistenteLancaIOException(@TempDir Path dir) throws IOException {
		MySqlDumpRunner.ConnectionTarget target = new MySqlDumpRunner.ConnectionTarget("localhost", 3306, "root", "");
		MySqlDumpRunner.RestoreOptions options = new MySqlDumpRunner.RestoreOptions(
				"nureal-bogus-executable-que-nao-existe", target, "app");
		Path inputFile = dir.resolve("entrada.sql");
		java.nio.file.Files.writeString(inputFile, "-- dump vazio\n");

		assertThrows(IOException.class, () -> backupPort.restore(options, inputFile, line -> { }));
	}
}
