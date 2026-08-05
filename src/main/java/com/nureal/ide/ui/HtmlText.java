package com.nureal.ide.ui;

import java.awt.Color;

/**
 * Helpers pra montar texto HTML embutido em {@code JLabel} (unico jeito do
 * Swing colorir/formatar PARTE do texto de um label, ver
 * {@code javax.swing.JLabel#setText}) — {@link #escape} evita que caracteres
 * como {@code <}/{@code >}/{@code &} vindos de dados do usuario (nome de
 * conexao, SQL, titulo de query salva) quebrem o HTML montado a mao;
 * {@link #hex} converte uma cor do tema pra string CSS, ja que HTML embutido
 * so aceita cor como texto.
 * <p>
 * Extraido de 3 copias identicas encontradas numa auditoria pedida pelo
 * usuario ({@code HistoryPanel$EntryRenderer}, {@code SavedQueriesPanel$QueryRenderer}
 * e {@code ObjectTreeCellRenderer}) — ponto unico agora, sem risco de as
 * copias divergirem se a escapagem precisar cobrir mais um caractere no
 * futuro.
 */
final class HtmlText {

    private HtmlText() {
    }

    /** Escapa {@code &}/{@code <}/{@code >} pra uso seguro dentro de um {@code <html>...</html>} de JLabel. */
    static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** Converte para {@code #RRGGBB}, formato que CSS embutido em HTML de JLabel aceita (nao aceita {@code java.awt.Color} direto). */
    static String hex(Color c) {
        return String.format("#%06X", c.getRGB() & 0xFFFFFF);
    }
}
