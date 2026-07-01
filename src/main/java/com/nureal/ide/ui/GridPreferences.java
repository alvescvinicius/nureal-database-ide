package com.nureal.ide.ui;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Persiste largura de colunas, colunas ocultas e ultima ordenacao da grade
 * de resultados em {@code ~/.nureal-ide/grid.conf} — mesmo formato
 * chave=valor simples das outras stores do projeto ({@link
 * com.nureal.ide.core.ui.UiPreferences}, {@code FormatPreferences}), so que
 * organizado em SECOES, uma por "formato" de resultado (conjunto de nomes de
 * coluna), ja que cada consulta pode trazer colunas diferentes:
 *
 * <pre>
 * [3:a1b2c3d4]
 * width.ID=80
 * width.NOME=220
 * hidden=CRIADO_EM
 * sort=NOME:ASC
 * </pre>
 *
 * A secao e identificada por um fingerprint curto (quantidade de colunas +
 * hash dos nomes) — nao pelo SQL, que muda a toda hora; assim, resultados
 * repetidos da MESMA consulta (ou de consultas diferentes com as mesmas
 * colunas) reaproveitam as mesmas preferencias automaticamente.
 */
final class GridPreferences {

    private static final Path FILE = Paths.get(System.getProperty("user.home"), ".nureal-ide", "grid.conf");

    private GridPreferences() {
    }

    record Snapshot(Map<String, Integer> widths, Set<String> hidden, List<String> sortSpec) {
        static Snapshot empty() {
            return new Snapshot(Map.of(), Set.of(), List.of());
        }
    }

    static String fingerprint(List<String> columnNames) {
        String joined = String.join("|", columnNames);
        return columnNames.size() + ":" + Integer.toHexString(joined.hashCode());
    }

    static Snapshot load(String fingerprint) {
        try {
            Map<String, List<String>> sections = readSections();
            List<String> lines = sections.get(fingerprint);
            if (lines == null) {
                return Snapshot.empty();
            }
            Map<String, Integer> widths = new LinkedHashMap<>();
            Set<String> hidden = new LinkedHashSet<>();
            List<String> sort = new ArrayList<>();
            for (String line : lines) {
                int eq = line.indexOf('=');
                if (eq < 0) {
                    continue;
                }
                String key = line.substring(0, eq).trim();
                String value = line.substring(eq + 1).trim();
                if (key.startsWith("width.")) {
                    try {
                        widths.put(key.substring("width.".length()), Integer.parseInt(value));
                    } catch (NumberFormatException ignore) {
                        // ignora entrada corrompida
                    }
                } else if (key.equals("hidden") && !value.isEmpty()) {
                    hidden.addAll(List.of(value.split(",")));
                } else if (key.equals("sort") && !value.isEmpty()) {
                    sort.addAll(List.of(value.split(",")));
                }
            }
            return new Snapshot(widths, hidden, sort);
        } catch (IOException | UncheckedIOException ex) {
            return Snapshot.empty();
        }
    }

    static void save(String fingerprint, Snapshot snapshot) {
        try {
            Map<String, List<String>> sections = readSections();
            List<String> lines = new ArrayList<>();
            snapshot.widths().forEach((col, width) -> lines.add("width." + col + "=" + width));
            if (!snapshot.hidden().isEmpty()) {
                lines.add("hidden=" + String.join(",", snapshot.hidden()));
            }
            if (!snapshot.sortSpec().isEmpty()) {
                lines.add("sort=" + String.join(",", snapshot.sortSpec()));
            }
            sections.put(fingerprint, lines);
            writeSections(sections);
        } catch (IOException ignore) {
            // preferencias de grade sao "nice to have" — falha ao salvar nao deve quebrar a UI
        }
    }

    private static Map<String, List<String>> readSections() throws IOException {
        Map<String, List<String>> sections = new LinkedHashMap<>();
        if (!Files.exists(FILE)) {
            return sections;
        }
        List<String> raw = Files.readAllLines(FILE, StandardCharsets.UTF_8);
        String current = null;
        for (String line : raw) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                current = trimmed.substring(1, trimmed.length() - 1);
                sections.putIfAbsent(current, new ArrayList<>());
            } else if (current != null) {
                sections.get(current).add(trimmed);
            }
        }
        return sections;
    }

    private static void writeSections(Map<String, List<String>> sections) throws IOException {
        Path parent = FILE.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        StringBuilder sb = new StringBuilder();
        sb.append("# Nureal Database IDE - preferencias da grade de resultados\n\n");
        for (Map.Entry<String, List<String>> entry : sections.entrySet()) {
            sb.append('[').append(entry.getKey()).append("]\n");
            for (String line : entry.getValue()) {
                sb.append(line).append('\n');
            }
            sb.append('\n');
        }
        Files.write(FILE, sb.toString().getBytes(StandardCharsets.UTF_8));
    }
}
