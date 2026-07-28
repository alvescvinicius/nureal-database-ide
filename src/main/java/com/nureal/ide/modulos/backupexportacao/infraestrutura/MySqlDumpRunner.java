package com.nureal.ide.modulos.backupexportacao.infraestrutura;
import com.nureal.ide.modulos.backupexportacao.dominio.contratos.BackupPort;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Wrapper fino em volta dos binarios de linha de comando {@code mysqldump}/
 * {@code mysql} — fase 4 do GAP_ANALYSIS_DBA_DEV.md: "backup/restore e hoje a
 * unica tarefa realmente critica de um DBA que exige sair da IDE e abrir um
 * terminal". Esta IDE conecta via JDBC (mysql-connector-j), que NAO inclui
 * esses binarios — por isso PROCESSO EXTERNO (ver {@link ProcessBuilder}),
 * assumindo que o usuario tem o MySQL Client Tools instalado (mesma
 * dependencia que qualquer script de backup ja precisaria) e informando um
 * caminho customizado quando nao estiver no PATH (ver {@code BackupRestoreDialog}).
 *
 * NAO tenta acompanhar progresso REAL (percentual) — nem {@code mysqldump} nem
 * {@code mysql} emitem isso por padrao sem opcoes extras dependentes de
 * versao. Em vez de fingir uma barra de progresso enganosa, so repassa cada
 * linha de {@code stderr} (avisos, "-- Warning" etc.) ao vivo via {@code
 * onLogLine} e deixa a UI mostrar "indeterminado, aguarde" — escopo menor
 * assumido conscientemente.
 */
public final class MySqlDumpRunner implements BackupPort {

    /** Credenciais/alvo comuns a backup e restore — nunca passadas como argumento de linha de comando (ver {@link #writeDefaultsFile}). */
    public record ConnectionTarget(String host, int port, String user, String password) {
    }

    public record BackupOptions(String executablePath, ConnectionTarget target, String schema,
            List<String> tables, boolean structureOnly, boolean includeRoutines, boolean includeTriggers,
            boolean singleTransaction) {
    }

    public record RestoreOptions(String executablePath, ConnectionTarget target, String schema) {
    }

    /**
     * Resultado de uma execucao: codigo de saida (0 = processo terminou sem
     * erro fatal) e se alguma linha de {@code stderr} parecia um erro real
     * (ver {@link #LINHA_DE_ERRO}). As duas coisas sao independentes: o
     * {@code mysqldump} pode sair com codigo 0 mesmo tendo falhado em uma
     * parte do dump (ex.: sem privilegio {@code PROCESS} para
     * routines/triggers) — ele avisa em stderr e segue em frente. Por isso
     * {@link #success()} exige as duas condicoes, para nao reportar
     * "concluido com sucesso" quando na verdade so uma parte do dump falhou.
     */
    public record RunResult(int exitCode, boolean hadErrorOutput) {
        public boolean success() {
            return exitCode == 0 && !hadErrorOutput;
        }
    }

    /**
     * Roda {@code mysqldump}, gravando a saida diretamente no arquivo
     * informado (via {@link ProcessBuilder#redirectOutput}, sem passar pelos
     * streams do Java — dumps grandes ficam mais rapidos e nao pressionam a
     * memoria do app). BLOQUEIA a thread chamadora ate o processo terminar —
     * chamar sempre de uma thread de segundo plano (ver {@code SwingWorker}
     * em {@code BackupRestoreDialog}).
     */
    @Override
    public RunResult backup(BackupOptions opts, Path outputFile, Consumer<String> onLogLine)
            throws IOException, InterruptedException {
        Path defaultsFile = writeDefaultsFile(opts.target());
        try {
            List<String> command = new ArrayList<>();
            command.add(resolveExecutable(opts.executablePath(), "mysqldump"));
            command.add("--defaults-extra-file=" + defaultsFile);
            if (opts.singleTransaction()) {
                command.add("--single-transaction");
            }
            if (opts.structureOnly()) {
                command.add("--no-data");
            }
            if (opts.includeRoutines()) {
                command.add("--routines");
            }
            if (opts.includeTriggers()) {
                command.add("--triggers");
            }
            command.add(opts.schema());
            if (opts.tables() != null) {
                command.addAll(opts.tables());
            }

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectOutput(outputFile.toFile());
            return runAndStreamErrors(pb, onLogLine);
        } finally {
            Files.deleteIfExists(defaultsFile);
        }
    }

    /**
     * Roda {@code mysql < arquivo.sql}, alimentando o processo com o conteudo
     * do arquivo (via {@link ProcessBuilder#redirectInput}). Mesma
     * observacao de {@link #backup} sobre bloquear a thread chamadora.
     */
    @Override
    public RunResult restore(RestoreOptions opts, Path inputFile, Consumer<String> onLogLine)
            throws IOException, InterruptedException {
        Path defaultsFile = writeDefaultsFile(opts.target());
        try {
            List<String> command = List.of(
                    resolveExecutable(opts.executablePath(), "mysql"),
                    "--defaults-extra-file=" + defaultsFile,
                    opts.schema());
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectInput(inputFile.toFile());
            return runAndStreamErrors(pb, onLogLine);
        } finally {
            Files.deleteIfExists(defaultsFile);
        }
    }

    private static String resolveExecutable(String configured, String defaultName) {
        return (configured == null || configured.isBlank()) ? defaultName : configured.trim();
    }

    /**
     * Reconhece a linha de erro real que {@code mysqldump}/{@code mysql}
     * emitem em stderr (ex.: {@code "mysqldump: Error: 'Access denied..."})
     * — distinta de avisos benignos ("-- Warning: ...") que nao indicam
     * falha. Usada por {@link #runAndStreamErrors} para nao deixar um erro
     * assim passar despercebido quando o processo ainda sai com codigo 0.
     */
    private static final java.util.regex.Pattern LINHA_DE_ERRO = java.util.regex.Pattern
            .compile("^\\S+:\\s*Error", java.util.regex.Pattern.CASE_INSENSITIVE);

    private static RunResult runAndStreamErrors(ProcessBuilder pb, Consumer<String> onLogLine)
            throws IOException, InterruptedException {
        Process process = pb.start();
        boolean[] hadErrorOutput = {false};
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (LINHA_DE_ERRO.matcher(line).find()) {
                    hadErrorOutput[0] = true;
                }
                if (onLogLine != null) {
                    onLogLine.accept(line);
                }
            }
        }
        int exitCode = process.waitFor();
        return new RunResult(exitCode, hadErrorOutput[0]);
    }

    /**
     * Grava usuario/senha/host/porta num arquivo temporario {@code [client]}
     * em vez de argumentos de linha de comando ({@code --password=...}) —
     * argumentos de processo ficam visiveis a QUALQUER outro processo do
     * mesmo usuario (ex.: {@code ps aux} em Unix), expondo a senha. O arquivo
     * e apagado (ver {@code finally} em {@link #backup}/{@link #restore})
     * assim que o processo termina; restringido a leitura/escrita do DONO
     * quando o sistema de arquivos suporta permissoes POSIX (Windows nao
     * suporta — o arquivo fica so no diretorio temporario do usuario, cujo
     * ACL padrao ja restringe a outras contas).
     */
    private static Path writeDefaultsFile(ConnectionTarget target) throws IOException {
        Path file = Files.createTempFile("nureal-ide-mysql-", ".cnf");
        try {
            Set<PosixFilePermission> perms = PosixFilePermissions.fromString("rw-------");
            Files.setPosixFilePermissions(file, perms);
        } catch (UnsupportedOperationException ignored) {
            // Windows: sem permissoes POSIX — segue com a ACL padrao do
            // diretorio temporario do usuario.
        }
        String content = "[client]\n"
                + "host=" + target.host() + "\n"
                + "port=" + target.port() + "\n"
                + "user=" + target.user() + "\n"
                + "password=" + (target.password() == null ? "" : target.password()) + "\n";
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }
}
