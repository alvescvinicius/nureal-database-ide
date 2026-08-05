package com.nureal.ide.ui;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Formata um instante ({@code epoch millis}) como tempo relativo ("agora",
 * "ha 5 min", "ha 2 dia(s)", caindo pra data/hora absoluta depois de uma
 * semana) ou absoluto ({@code dd/MM/yyyy HH:mm:ss}) — usado pelas linhas de
 * {@code HistoryPanel}/{@code SavedQueriesPanel} (execucao/atualizacao mais
 * recente).
 * <p>
 * Extraido de 2 copias identicas (uma em cada painel) encontradas numa
 * auditoria pedida pelo usuario — ponto unico agora.
 */
final class RelativeTime {

    private RelativeTime() {
    }

    static String relative(long epochMillis) {
        if (epochMillis <= 0) {
            return "";
        }
        long diffSec = Math.max(0, (System.currentTimeMillis() - epochMillis) / 1000);
        if (diffSec < 60) {
            return "agora";
        }
        long min = diffSec / 60;
        if (min < 60) {
            return "ha " + min + " min";
        }
        long hours = min / 60;
        if (hours < 24) {
            return "ha " + hours + "h";
        }
        long days = hours / 24;
        if (days < 7) {
            return "ha " + days + " dia(s)";
        }
        return absolute(epochMillis);
    }

    static String absolute(long epochMillis) {
        return DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")
                .format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()));
    }
}
