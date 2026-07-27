package com.nureal.ide.modulos.atualizacao.infraestrutura;

import java.util.Locale;

/**
 * Versao "estilo semver" simplificada — so os 3 numeros MAJOR.MINOR.PATCH,
 * usada para decidir se uma versao e mais nova que outra (ver
 * {@link #isNewer(String, String)}, unico ponto de entrada usado pelo resto
 * do app).
 *
 * Deliberadamente NAO implementa a especificacao SemVer completa (pre-release
 * tem prioridade MENOR que a versao base, build metadata e ignorado etc.) —
 * o unico uso real aqui e comparar a tag de um release do GitHub (ex.:
 * {@code "v0.5.0"}) contra {@link AppVersion#current()} (ex.:
 * {@code "0.4.4-SNAPSHOT"} ou {@code "0.5.0"}): qualquer sufixo depois de
 * "-" ou "+" e simplesmente cortado antes de comparar os 3 numeros, e
 * qualquer parte nao numerica vira 0. Suficiente pro caso de uso (saber se
 * "tem uma versao mais nova no GitHub"), sem a complexidade de uma
 * implementacao SemVer completa que ninguem aqui precisa.
 */
final class SemVer implements Comparable<SemVer> {

    private final int major;
    private final int minor;
    private final int patch;

    private SemVer(int major, int minor, int patch) {
        this.major = major;
        this.minor = minor;
        this.patch = patch;
    }

    /**
     * Interpreta {@code text} como MAJOR.MINOR.PATCH — aceita prefixo "v"/"V"
     * (tags do GitHub, ex.: "v0.5.0"), corta qualquer sufixo de pre-release/
     * build metadata ("-SNAPSHOT", "-beta.1", "+abc123"), e trata qualquer
     * componente ausente ou nao numerico como 0 (nunca lanca excecao — uma
     * tag/versao mal formada so vira "0.0.0", pior caso e um falso negativo
     * de "tem atualizacao", nunca um crash).
     */
    static SemVer parse(String text) {
        String s = (text == null) ? "" : text.trim();
        if (s.startsWith("v") || s.startsWith("V")) {
            s = s.substring(1);
        }
        int cut = s.length();
        int dash = s.indexOf('-');
        if (dash >= 0) {
            cut = Math.min(cut, dash);
        }
        int plus = s.indexOf('+');
        if (plus >= 0) {
            cut = Math.min(cut, plus);
        }
        s = s.substring(0, cut);
        String[] parts = s.split("\\.", -1);
        return new SemVer(numberAt(parts, 0), numberAt(parts, 1), numberAt(parts, 2));
    }

    private static int numberAt(String[] parts, int index) {
        if (index >= parts.length) {
            return 0;
        }
        String digits = parts[index].replaceAll("[^0-9]", "");
        if (digits.isEmpty()) {
            return 0;
        }
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    @Override
    public int compareTo(SemVer other) {
        if (major != other.major) {
            return Integer.compare(major, other.major);
        }
        if (minor != other.minor) {
            return Integer.compare(minor, other.minor);
        }
        return Integer.compare(patch, other.patch);
    }

    @Override
    public String toString() {
        return String.format(Locale.ROOT, "%d.%d.%d", major, minor, patch);
    }

    /** {@code true} quando {@code latest} representa uma versao estritamente maior que {@code current}. */
    static boolean isNewer(String latest, String current) {
        return parse(latest).compareTo(parse(current)) > 0;
    }
}
