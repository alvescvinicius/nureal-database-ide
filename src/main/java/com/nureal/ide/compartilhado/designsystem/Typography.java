package com.nureal.ide.compartilhado.designsystem;

import java.awt.Font;
import java.util.function.Consumer;

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
public final class Typography {

    private Typography() {
    }

    /** Titulos, cabecalhos de painel, nome de conexao/tabela, botoes principais. */
    public static void primary(JComponent c) {
        c.setFont(c.getFont().deriveFont(Font.BOLD));
        c.setForeground(GridTheme.HEADER_FOREGROUND);
    }

    /**
     * Conteudo normal (listas/arvores/grade/editor/menus) — peso Regular,
     * cor padrao de texto do tema.
     * <p>
     * Usava {@link GridTheme#COLOR_TEXTUAL} ate esta correcao — mas esse
     * campo e EDITOR-ONLY (ver o javadoc extenso de {@link GridTheme}: "o
     * editor SQL le estes campos DIRETAMENTE... NUNCA por colorFor"), o
     * VERDE de string literal do syntax highlight do editor, nao uma cor de
     * texto geral. Usa-lo aqui pintava rotulos comuns (ex.: "Backup e
     * Restauracao", "SQL Editors (N)") de verde em QUALQUER tema — bug de
     * legibilidade relatado pelo usuario ("melhore o tema light... melhor
     * visualizacao dos textos"), visivel nos dois temas (claro e escuro).
     * {@link GridTheme#COLOR_DEFAULT_TEXT} e a cor certa: e literalmente
     * "cor PADRAO de texto na exibicao de dados" do tema (quase preto no
     * claro, quase branco no escuro).
     */
    public static void secondary(JComponent c) {
        c.setFont(c.getFont().deriveFont(Font.PLAIN));
        c.setForeground(GridTheme.COLOR_DEFAULT_TEXT);
    }

    /** Informacao auxiliar/descricao/status/placeholder — a cor mais discreta da paleta. */
    public static void tertiary(JComponent c) {
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
    public static JLabel sectionHeader(String text) {
        return selfStyling(text, label -> {
            label.setFont(label.getFont().deriveFont(11f));
            primary(label);
        });
    }

    /**
     * {@link JLabel} que reaplica {@code style} sozinho em {@link JLabel#updateUI()}
     * — chamado pelo Swing em CADA componente da janela toda vez que
     * {@code FlatLaf.updateUI()} roda (ver {@code MainWindow#toggleTheme}).
     * <p>
     * {@link #primary}/{@link #secondary}/{@link #tertiary} sozinhos so
     * pintam a cor UMA VEZ, no instante da chamada (ver javadoc da classe):
     * um rotulo construido enquanto o app estava no tema ESCURO (padrao ao
     * abrir, ver {@code App#main}) ficava com a cor ESCURA gravada pra
     * sempre — ao trocar pro tema CLARO, o FUNDO ao redor atualizava
     * sozinho (via {@code FlatLaf.updateUI()}, que O SWING ja sabe redesenhar),
     * mas o TEXTO continuava na cor do tema anterior (quase branco), ficando
     * invisivel sobre o fundo agora branco — bug relatado pelo usuario
     * ("no tema claro tem varios textos que nao estao visiveis"), com
     * captura mostrando os rotulos do grupo FERRAMENTAS em branco.
     * <p>
     * Usado por {@link #sectionHeader} (cabecalhos "CONEXOES"/"OBJETOS"/
     * "FERRAMENTAS" etc., em qualquer painel) e por
     * {@code MainWindow#sidebarRow} (rotulos "Backup e Restauracao" etc.) —
     * os dois pontos onde o rotulo NASCE dentro do proprio Design System /
     * MainWindow, entao dá pra embutir o auto-refresh na criacao, sem exigir
     * que cada chamador de {@link #primary}/{@link #secondary}/{@link #tertiary}
     * (que recebem um {@link JComponent} JA EXISTENTE, de tipo arbitrario)
     * se lembre de reaplicar manualmente.
     */
    public static JLabel selfStyling(String text, Consumer<JLabel> style) {
        JLabel label = new JLabel(text) {
            private static final long serialVersionUID = 1L;

            @Override
            public void updateUI() {
                super.updateUI();
                // Guard (mesmo padrao ja usado em ResultRecordView#updateUI):
                // o PRIMEIRO updateUI() e disparado de DENTRO do super(text)
                // do construtor de JLabel — nesse instante o campo sintetico
                // que guarda "style" (variavel capturada por esta classe
                // anonima) ainda nao foi atribuido pela JVM (so acontece
                // DEPOIS que super() retorna), entao chega null aqui na
                // primeira chamada. A atribuicao de verdade (linha
                // "style.accept(label)" abaixo) cobre esse primeiro caso.
                if (style != null) {
                    style.accept(this);
                }
            }
        };
        style.accept(label);
        return label;
    }
}
