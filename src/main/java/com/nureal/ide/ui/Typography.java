package com.nureal.ide.ui;

import java.awt.Font;

import javax.swing.JComponent;
import javax.swing.JLabel;

/**
 * Escala tipografica UNICA da aplicacao (spec de padronizacao visual do
 * usuario, secao 1 "Tipografia": "definir apenas tres niveis de destaque").
 * Antes desta classe cada painel decidia peso/cor "no olho" pra representar
 * o MESMO papel visual — auditoria encontrou {@code MUTED_TEXT} usado em
 * titulos de painel (que a spec pede como PRIMARIO, alto contraste) so
 * porque cada arquivo (ConnectionsPanel/HistoryPanel/SavedQueriesPanel/
 * MainWindow) tinha sua PROPRIA copia colada do mesmo trecho.
 *
 * So PESO e COR sao normalizados aqui — o TAMANHO continua escolha de cada
 * chamador (um titulo pequeno em versalete de painel lateral e um titulo
 * grande de estado vazio podem ser igualmente "primarios" em peso/contraste,
 * so diferem no tamanho pelo CONTEXTO, nao pelo nivel de destaque).
 *
 * <ul>
 *   <li>{@link #primary}: titulos, cabecalhos de painel, nome de
 *       conexao/tabela, botoes principais — peso Bold, cor de MAIOR
 *       contraste ({@link GridTheme#HEADER_FOREGROUND}).</li>
 *   <li>{@link #secondary}: conteudo normal de listas/arvores/grade/editor/
 *       menus — peso Regular, tom levemente suavizado
 *       ({@link GridTheme#COLOR_TEXTUAL}).</li>
 *   <li>{@link #tertiary}: informacao auxiliar (descricao/status/
 *       placeholder) — peso Regular, a cor MAIS discreta da paleta
 *       ({@link GridTheme#MUTED_TEXT}), nunca competindo com o conteudo
 *       principal.</li>
 * </ul>
 *
 * As cores vem de {@link GridTheme} (mesma fonte unica de verdade ja usada
 * pelo resto do app) — aplicar aqui e um instantaneo no momento da chamada,
 * igual qualquer outro {@code setForeground} direto ja espalhado pela base;
 * os poucos lugares que precisam de recolorir AO VIVO numa troca de tema
 * (SqlEditorPane, ResultGrid, dialogos com JRootPane customizado) ja cuidam
 * disso sozinhos.
 */
final class Typography {

    private Typography() {
    }

    /** Titulos, cabecalhos de painel, nome de conexao/tabela, botoes principais. */
    static void primary(JComponent c) {
        c.setFont(c.getFont().deriveFont(Font.BOLD));
        c.setForeground(GridTheme.HEADER_FOREGROUND);
    }

    /** Conteudo normal (listas/arvores/grade/editor/menus) — peso Regular, tom levemente suavizado. */
    static void secondary(JComponent c) {
        c.setFont(c.getFont().deriveFont(Font.PLAIN));
        c.setForeground(GridTheme.COLOR_TEXTUAL);
    }

    /** Informacao auxiliar/descricao/status/placeholder — a cor mais discreta da paleta. */
    static void tertiary(JComponent c) {
        c.setFont(c.getFont().deriveFont(Font.PLAIN));
        c.setForeground(GridTheme.MUTED_TEXT);
    }

    /**
     * Titulo de painel lateral (ex.: "CONEXOES", "OBJETOS", "HISTORICO",
     * "QUERIES SALVAS") — 11px, {@link #primary} (Bold + maior contraste).
     * Ponto UNICO desta receita: antes cada painel (ConnectionsPanel,
     * HistoryPanel, SavedQueriesPanel, MainWindow#sectionHeader) tinha sua
     * PROPRIA copia colada do mesmo trecho, e foi assim que 3 delas
     * acumularam um {@code putClientProperty("FlatLaf.styleClass", "small")}
     * que MainWindow nunca teve — client property morta (nenhuma regra
     * "small" cadastrada no tema FlatLaf deste app, ver
     * {@code src/main/resources}), sobrando so como um resquicio confuso de
     * uma tentativa antiga. Reunir tudo aqui elimina os dois problemas de
     * uma vez: a duplicacao E a divergencia entre os 4 cabecalhos.
     */
    static JLabel sectionHeader(String text) {
        JLabel label = new JLabel(text);
        label.setFont(label.getFont().deriveFont(11f));
        primary(label);
        return label;
    }
}
