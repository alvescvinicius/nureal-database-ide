package com.nureal.ide.core.update;

/**
 * Versao da build atualmente rodando — fonte unica de verdade para "que
 * versao sou eu", usada tanto pelo checador de atualizacao (compara contra o
 * ultimo release do GitHub) quanto por qualquer tela que queira mostrar a
 * versao ao usuario (ex.: futuro dialogo "Sobre").
 *
 * NAO le o {@code <version>} do pom.xml diretamente (isso so existe em tempo
 * de BUILD, nao sobrevive dentro do jar/instalador rodando na maquina do
 * usuario) — le o atributo de manifesto {@code Implementation-Version} do
 * proprio jar, que o {@code maven-shade-plugin} agora grava a partir do
 * {@code ${project.version}} (ver pom.xml, transformer do shade-plugin) toda
 * vez que o fat jar e empacotado. O workflow de release
 * (.github/workflows/release.yml) sincroniza o pom.xml com a tag do Git
 * ANTES de empacotar (via {@code versions:set}), entao a cadeia inteira fica
 * consistente numa unica fonte: <b>a tag do Git e quem manda</b> — tag vX.Y.Z
 * vira {@code project.version=X.Y.Z} vira {@code Implementation-Version} no
 * manifesto vira {@code --app-version} do jpackage no mesmo instalador.
 *
 * Rodando FORA de um jar empacotado (ex.: {@code mvn exec:java} durante o
 * desenvolvimento, ou direto da IDE) o manifesto simplesmente nao existe —
 * {@link Package#getImplementationVersion()} devolve {@code null} nesse caso,
 * e {@link #current()} cai no valor {@link #DEV_VERSION}. O checador de
 * atualizacao trata este valor como "build de desenvolvimento" e NAO faz a
 * checagem automatica no startup (ver {@code UpdateChecker}/uso em
 * MainWindow) — so a checagem manual ("Verificar atualizacoes...") funciona,
 * pra nao incomodar quem esta desenvolvendo com um aviso de atualizacao sem
 * sentido a cada `mvn exec:java`.
 */
public final class AppVersion {

    /** Versao reportada quando o app roda sem manifesto (fora de um jar empacotado). */
    public static final String DEV_VERSION = "0.0.0-dev";

    private AppVersion() {
    }

    /** Versao atual desta build ({@link #DEV_VERSION} se rodando sem manifesto). */
    public static String current() {
        String v = AppVersion.class.getPackage().getImplementationVersion();
        return (v == null || v.isBlank()) ? DEV_VERSION : v;
    }

    /** {@code true} quando rodando fora de um jar empacotado (ver javadoc da classe). */
    public static boolean isDevBuild() {
        return DEV_VERSION.equals(current());
    }
}
