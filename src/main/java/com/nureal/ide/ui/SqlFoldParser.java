package com.nureal.ide.ui;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.folding.Fold;
import org.fife.ui.rsyntaxtextarea.folding.FoldParser;
import org.fife.ui.rsyntaxtextarea.folding.FoldType;

import javax.swing.text.BadLocationException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Folding (expandir/recolher) para SQL: recolhe blocos entre parenteses que
 * ocupem mais de uma linha (subconsultas, listas, argumentos) e comentarios de
 * bloco multi-linha. Strings e comentarios sao ignorados na varredura.
 */
public final class SqlFoldParser implements FoldParser {

    @Override
    public List<Fold> getFolds(RSyntaxTextArea textArea) {
        List<Fold> folds = new ArrayList<>();
        try {
            List<int[]> ranges = collectRanges(textArea.getText());
            // ordena por inicio (asc) e, no empate, por fim (desc) -> pais antes dos filhos
            ranges.sort((a, b) -> a[0] != b[0]
                    ? Integer.compare(a[0], b[0]) : Integer.compare(b[1], a[1]));

            Deque<int[]> openRanges = new ArrayDeque<>();
            Deque<Fold> openFolds = new ArrayDeque<>();
            for (int[] r : ranges) {
                while (!openRanges.isEmpty() && openRanges.peek()[1] <= r[0]) {
                    openRanges.pop();
                    openFolds.pop();
                }
                Fold fold;
                if (openFolds.isEmpty()) {
                    fold = new Fold(r[2], textArea, r[0]);
                    folds.add(fold);
                } else {
                    fold = openFolds.peek().createChild(r[2], r[0]);
                }
                fold.setEndOffset(r[1]);
                openRanges.push(r);
                openFolds.push(fold);
            }
        } catch (BadLocationException ex) {
            // offset invalido (texto mudou no meio): retorna o que ja montou
        }
        return folds;
    }

    /** {inicio, fim, tipoFold} de cada bloco multi-linha (parenteses e /* *&#47;). */
    private static List<int[]> collectRanges(String s) {
        List<int[]> ranges = new ArrayList<>();
        Deque<int[]> parens = new ArrayDeque<>(); // {offset, linha}
        int i = 0;
        int n = s.length();
        int line = 0;
        while (i < n) {
            char c = s.charAt(i);
            if (c == '\n') {
                line++;
                i++;
                continue;
            }
            // comentario de linha
            if ((c == '-' && i + 1 < n && s.charAt(i + 1) == '-') || c == '#') {
                while (i < n && s.charAt(i) != '\n') {
                    i++;
                }
                continue;
            }
            // comentario de bloco
            if (c == '/' && i + 1 < n && s.charAt(i + 1) == '*') {
                int start = i;
                int startLine = line;
                i += 2;
                while (i + 1 < n && !(s.charAt(i) == '*' && s.charAt(i + 1) == '/')) {
                    if (s.charAt(i) == '\n') {
                        line++;
                    }
                    i++;
                }
                i = Math.min(n, i + 2);
                if (line > startLine) {
                    ranges.add(new int[] {start, i - 1, FoldType.COMMENT});
                }
                continue;
            }
            // strings / identificadores citados
            if (c == '\'' || c == '"' || c == '`') {
                char q = c;
                i++;
                while (i < n) {
                    char d = s.charAt(i);
                    if (d == '\\' && (q == '\'' || q == '"') && i + 1 < n) {
                        i += 2;
                        continue;
                    }
                    if (d == q) {
                        if (i + 1 < n && s.charAt(i + 1) == q) {
                            i += 2;
                            continue;
                        }
                        i++;
                        break;
                    }
                    if (d == '\n') {
                        line++;
                    }
                    i++;
                }
                continue;
            }
            if (c == '(') {
                parens.push(new int[] {i, line});
                i++;
                continue;
            }
            if (c == ')') {
                if (!parens.isEmpty()) {
                    int[] open = parens.pop();
                    if (line > open[1]) {
                        ranges.add(new int[] {open[0], i, FoldType.CODE});
                    }
                }
                i++;
                continue;
            }
            i++;
        }
        return ranges;
    }
}
