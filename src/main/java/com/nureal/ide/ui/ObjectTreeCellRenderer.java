package com.nureal.ide.ui;
import com.nureal.ide.compartilhado.designsystem.IconType;
import com.nureal.ide.compartilhado.designsystem.IconTheme;
import com.nureal.ide.compartilhado.designsystem.Icons;
import com.nureal.ide.compartilhado.designsystem.Buttons;
import com.nureal.ide.compartilhado.designsystem.GridTheme;

import com.formdev.flatlaf.FlatLaf;

import javax.swing.Icon;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.util.Locale;

/**
 * Arvore de objetos.
 *
 * <ul>
 * <li>Icone de tipo por CONCEITO (tabela/view/procedure/function/trigger —
 * ver {@link #iconTypeFor}), na cor neutra {@link GridTheme#MUTED_TEXT} tanto
 * na categoria (Tabelas/Visualizacoes/Procedures/Functions/Triggers) quanto
 * em cada objeto dentro dela. Rodada 2 (pedido explicito do usuario, "vamos
 * melhorar e colocar icones"): reverte a Rodada 1, que tinha removido TODO
 * icone de tipo por ter ficado poluido visualmente com um bloco de fundo
 * colorido por categoria — desta vez o icone e so um glifo pequeno e
 * monocromatico ao lado do texto, sem nenhum fundo colorido, entao nao
 * reintroduz o problema original. Coluna continua SEM icone (o texto ja
 * mostra nome + tipo via HTML, ver {@link #columnHtml}) — so os niveis
 * categoria/tabela/view/rotina/trigger ganham icone. O unico "icone" que ja
 * existia antes e continua e a bolinha de status na RAIZ (schema), no lugar
 * do triangulo de expandir/recolher que o {@code JTree} nao desenha mais ali
 * (ver {@code MainWindow#buildObjectBrowser}, {@code setShowsRootHandles(false)})
 * — a MESMA bolinha verde/ambar/cinza da lista de conexoes (ver
 * {@link ConnectionsPanel#statusDot}).</li>
 * <li>Categoria e os objetos dentro dela: SEM cor de TEXTO por categoria
 * (mantido da Rodada 1 — "gritava" demais) — so o icone (Rodada 2) e o
 * negrito num cinza medio (nunca preto forte) enquanto o galho estiver
 * EXPANDIDO, ou seja, o "caminho" por onde se esta navegando agora (ver
 * {@link #applyPathStyle}). Uma categoria SEM nenhum objeto (contador "(0)")
 * fica sempre em cinza mudo e peso normal, nunca em negrito — o icone
 * continua aparecendo mesmo assim, so o texto fica mudo.</li>
 * <li>Fundo de linha so existe para a SELECAO (ver {@link #backgroundFor}) —
 * o MESMO cinza clarinho em QUALQUER linha, inclusive o schema. Cobre a
 * linha inteira via um truque padrao de JTree: o
 * componente vira {@code setOpaque(true)} e {@link #getPreferredSize()} e
 * esticado para cobrir a arvore inteira (ver {@link #applyRowBackground}) —
 * e assim que o {@code JTree} decide a largura de cada celula, entao o
 * fundo vai ate a borda direita sozinho, sem pintura extra por fora do
 * renderer nem client property especifica de L&amp;F.</li>
 * </ul>
 */
final class ObjectTreeCellRenderer extends DefaultTreeCellRenderer {

    private static final long serialVersionUID = 1L;

    /**
     * Tamanho/margem da setinha de "trocar esquema" desenhada na ponta
     * direita da linha do schema (raiz) — visiveis aqui (pacote) porque
     * {@code MainWindow} precisa dos MESMOS valores para saber se um clique
     * caiu em cima dela (ver {@code MainWindow#isSchemaSwitchArrowClick}).
     * A acao em si (trocar esquema no menu de contexto/botao do cabecalho)
     * ja existia; esta seta e so mais um jeito de chegar nela, sem precisar
     * do clique direito.
     */
    static final int SCHEMA_SWITCH_ICON_SIZE = 12;
    static final int SCHEMA_SWITCH_ICON_MARGIN = 10;

    /** Tamanho do icone de tipo (categoria/tabela/view/rotina/trigger) — pequeno, nao compete com o texto numa linha de arvore densa. */
    private static final int TYPE_ICON_SIZE = 14;

    private boolean paintSwitchArrow;

    ObjectTreeCellRenderer() {
        // openIcon/closedIcon/leafIcon sao os icones-padrao que o
        // DefaultTreeCellRenderer usaria (pasta aberta/fechada, arquivo)
        // quando getTreeCellRendererComponent nao definir um icone proprio —
        // nulos aqui para nao sobrarem mesmo nesses casos.
        setLeafIcon(null);
        setOpenIcon(null);
        setClosedIcon(null);
        // As cores de selecao/nao-selecao PADRAO do DefaultTreeCellRenderer
        // nao sao usadas — quem decide o fundo linha a linha e
        // applyRowBackground/backgroundFor, chamados a cada
        // getTreeCellRendererComponent.
        setTextSelectionColor(getTextNonSelectionColor());
    }

    @Override
    public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected,
            boolean expanded, boolean leaf, int row, boolean hasFocus) {
        super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
        setIcon(null);
        setFont(tree.getFont());

        DefaultMutableTreeNode node = (value instanceof DefaultMutableTreeNode n) ? n : null;
        ObjectExplorerController.ObjNode obj = (node != null && node.getUserObject() instanceof ObjectExplorerController.ObjNode o) ? o : null;
        boolean isSchema = obj != null && obj.type() == ObjectExplorerController.NodeType.SCHEMA;
        if (obj != null) {
            boolean emptyCategory = obj.type() == ObjectExplorerController.NodeType.CATEGORY && node.getChildCount() == 0;
            style(obj, emptyCategory, expanded);
        }
        paintSwitchArrow = isSchema;
        // A linha do schema precisa da largura esticada SEMPRE (nao so quando
        // selecionada) — sem isto nao ha como saber, em paintComponent, onde
        // fica a "ponta direita" da linha pra desenhar a setinha.
        applyRowBackground(tree, selected, isSchema, row);
        return this;
    }

    @Override
    public void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (!paintSwitchArrow) {
            return;
        }
        Icon icon = Icons.get(IconType.CHEVRON_LEFT, SCHEMA_SWITCH_ICON_SIZE, pathColor());
        int x = getWidth() - SCHEMA_SWITCH_ICON_MARGIN - SCHEMA_SWITCH_ICON_SIZE;
        int y = (getHeight() - SCHEMA_SWITCH_ICON_SIZE) / 2;
        icon.paintIcon(this, g, x, y);
    }

    /**
     * Fundo da linha — SO existe para a selecao (ver {@link #backgroundFor});
     * fora disso o label fica transparente, como um JLabel comum dentro de
     * um JTree. Quando ha cor, cobre a linha INTEIRA — ver javadoc da
     * classe. {@code stretchWidth}: forca a mesma largura esticada mesmo SEM
     * selecao — usado so pela linha do schema, que precisa saber sua propria
     * largura em {@link #paintComponent} pra desenhar a setinha na ponta
     * direita (ver {@link #SCHEMA_SWITCH_ICON_SIZE}).
     * <p>
     * {@code setPreferredSize(null)} SEMPRE primeiro, antes de ler
     * {@link #getPreferredSize()}: este renderer e uma UNICA instancia
     * reaproveitada para TODAS as linhas da arvore (padrao normal de
     * {@code TreeCellRenderer}) — uma chamada anterior de
     * {@code setPreferredSize(new Dimension(...))} (linha de baixo) FIXA
     * esse valor explicito no componente, e {@code getPreferredSize()}
     * continua devolvendo ele intacto em QUALQUER linha seguinte, mesmo com
     * texto diferente/maior, ate alguem limpar o override — sem isto, a
     * largura "natural" de uma linha com nome comprido nunca era medida de
     * verdade (ficava presa na largura da ULTIMA linha que tinha passado por
     * aqui antes dela), truncando o texto com "..." mesmo com espaco de
     * sobra pra crescer — bug relatado pelo usuario com captura de tela
     * (nomes de tabela cortados na arvore de Objetos).
     */
    private void applyRowBackground(JTree tree, boolean selected, boolean stretchWidth, int row) {
        setPreferredSize(null);
        Color bg = backgroundFor(selected, tree, row);
        if (bg == null) {
            setOpaque(false);
        } else {
            setOpaque(true);
            setBackground(bg);
        }
        if (bg == null && !stretchWidth) {
            return;
        }
        Dimension pref = getPreferredSize();
        // Estica ate a largura REAL da arvore (nunca um piso artificial
        // maior — ver historico desta linha) pra cobrir a linha inteira. Um
        // piso fixo de 1000px aqui inflava a largura PREFERIDA da arvore
        // toda (a linha do schema tem stretchWidth sempre true) bem alem do
        // espaco visivel da sidebar, acionando uma barra de rolagem
        // horizontal quase permanente mesmo com nomes curtos — relatado
        // pelo usuario com captura de tela. tree.getWidth() antes do
        // primeiro layout e 0, entao cai pro tamanho natural do texto
        // (pref.width) sem regressao.
        int width = Math.max(pref.width, tree.getWidth());
        setPreferredSize(new Dimension(width, pref.height));
    }

    /**
     * Cor de fundo da linha SELECIONADA: cinza clarinho (ver
     * {@link #selectionBackground}) para QUALQUER linha, inclusive o schema
     * (raiz) — mesma barra para todo mundo. NAO selecionada MAS sob o mouse:
     * {@link GridTheme#HOVER_BACKGROUND} (mesmo destaque suave de hover da
     * grade de resultados — ver {@link TreeHoverTracker}), estado que faltava
     * aqui antes (so existiam normal/selecionado). Selecao sempre tem
     * prioridade visual sobre hover. Fora isso, sempre {@code null} —
     * nenhuma categoria pinta fundo, so o texto (ver {@link #style}/
     * {@link #applyPathStyle}).
     */
    private static Color backgroundFor(boolean selected, JTree tree, int row) {
        if (selected) {
            return selectionBackground();
        }
        if (row >= 0 && row == TreeHoverTracker.hoverRow(tree)) {
            return GridTheme.HOVER_BACKGROUND;
        }
        return null;
    }

    private void style(ObjectExplorerController.ObjNode obj, boolean emptyCategory, boolean expanded) {
        switch (obj.type()) {
            case SCHEMA -> {
                setIcon(ConnectionsPanel.statusDot(MainWindow.ACCENT));
                setFont(getFont().deriveFont(Font.BOLD));
                // Nome do schema em maiusculo — destaque pedido pelo usuario
                // (so visual: obj.name()/obj.display() continuam com a
                // grafia original em qualquer lugar que os use por baixo,
                // ex.: abertura de conexao/schema).
                setText(obj.display().toUpperCase(Locale.ROOT));
            }
            // Categoria e os proprios objetos abriveis (tabela/view/
            // procedure/function/trigger): SEM cor de TEXTO por categoria
            // (removida a pedido do usuario — ficava "gritando" demais),
            // mas COM icone de tipo (Rodada 2 — ver javadoc da classe). O
            // unico destaque de TEXTO continua sendo o peso da fonte, e so
            // enquanto o galho estiver EXPANDIDO — ou seja, o "caminho" por
            // onde estamos navegando agora fica em negrito; o resto usa o
            // texto padrao da arvore.
            case CATEGORY -> {
                setIcon(typeIcon(obj.kind()));
                applyPathStyle(emptyCategory, expanded);
            }
            case TABLE, VIEW, ROUTINE, TRIGGER -> {
                setIcon(typeIcon(obj.kind()));
                applyPathStyle(false, expanded);
            }
            // Coluna: o texto (nome em negrito + tipo em cinza) e todo
            // montado via HTML em columnHtml — sem cor de categoria (fica
            // discreta de proposito, ja aninhada duas vezes).
            case COLUMN -> setText(columnHtml(obj.name(), obj.columnType()));
            case SCHEMA_PICK -> {
                // mantem o padrao — lista de escolha de schema, sem categoria.
            }
            // Linha sintetica de "busca sem resultado" (ver MainWindow#rebuildTree)
            // — sem icone, italico e no mesmo cinza mudo de uma categoria vazia,
            // pra ficar claramente uma MENSAGEM e nao um objeto clicavel de verdade.
            case EMPTY_MESSAGE -> {
                setForeground(GridTheme.MUTED_TEXT);
                setFont(getFont().deriveFont(Font.ITALIC));
            }
        }
    }

    /**
     * Estilo "caminho atual", sem cor por categoria: {@code muted} (categoria
     * vazia, contador "(0)") sempre fica cinza mudo e peso normal — nunca ha
     * nada pra expandir ali. Fora isso, so quando o galho esta EXPANDIDO
     * ({@code expanded}) o texto vira negrito num cinza medio (nao um preto
     * forte, ver {@link #pathColor}) — colapsado, fica no peso/cor padrao do
     * JTree, sem nenhum destaque.
     */
    private void applyPathStyle(boolean muted, boolean expanded) {
        if (muted) {
            setForeground(GridTheme.MUTED_TEXT);
            return;
        }
        if (expanded) {
            setForeground(pathColor());
            setFont(getFont().deriveFont(Font.BOLD));
        }
    }

    /** Cinza medio para o "caminho atual" em negrito — deliberadamente NAO preto forte. */
    private static Color pathColor() {
        return FlatLaf.isLafDark() ? new Color(0xC7CBD1) : new Color(0x4B5563);
    }

    /**
     * Icone de tipo para uma categoria ou objeto da arvore, a partir de
     * {@code obj.kind()} ("TABLE"/"VIEW"/"PROCEDURE"/"FUNCTION"/"TRIGGER" —
     * ver {@code MainWindow#rebuildTree}/{@code addTableCategory}/
     * {@code addNameCategory}, quem propaga esse valor). Sempre na cor
     * neutra {@link GridTheme#MUTED_TEXT} (nunca {@link IconTheme#colorFor},
     * que devolveria {@link IconTheme#INK} — preto fixo, ilegivel no tema
     * escuro) — resolvida de novo A CADA pintura (nao cacheada), entao segue
     * sozinha uma troca de tema claro/escuro sem precisar do truque de
     * {@code Buttons#bindThemedIcon} (que so existe para icones presos num
     * {@code JButton}/{@code JLabel} de vida longa, nunca recriado; este
     * renderer ja e chamado de novo a cada linha/repaint). {@code null}
     * quando {@code kind} nao mapeia pra nenhum tipo conhecido (schema/
     * coluna/lista de escolha de schema — esses casos nem chegam a chamar
     * este metodo, ver {@link #style}).
     */
    private static Icon typeIcon(String kind) {
        IconType type = iconTypeFor(kind);
        return (type == null) ? null : Icons.get(type, TYPE_ICON_SIZE, GridTheme.MUTED_TEXT);
    }

    private static IconType iconTypeFor(String kind) {
        if (kind == null) {
            return null;
        }
        return switch (kind) {
            case "TABLE" -> IconType.TABLE;
            case "VIEW" -> IconType.VIEW;
            case "PROCEDURE" -> IconType.PROCEDURE;
            case "FUNCTION" -> IconType.FUNCTION;
            case "TRIGGER" -> IconType.TRIGGER;
            // Sem icone dedicado de "evento agendado" no design system ainda —
            // reaproveita HISTORY (relogio), semanticamente proximo o
            // suficiente ("algo que acontece com o tempo") sem precisar de um
            // asset SVG novo so para esta categoria.
            case "EVENT" -> IconType.HISTORY;
            default -> null;
        };
    }

    /** Nome da coluna em negrito + tipo em cinza mudo, ex.: <b>id</b> : bigint. */
    private static String columnHtml(String name, String type) {
        String hexMuted = HtmlText.hex(GridTheme.MUTED_TEXT);
        String safeName = HtmlText.escape(name);
        String safeType = HtmlText.escape(type == null ? "" : type);
        return "<html><b>" + safeName + "</b><span style='color:" + hexMuted + "'> : " + safeType + "</span></html>";
    }

    /**
     * Cinza clarinho — a barra de selecao para QUALQUER linha, inclusive o
     * schema (ver {@link #backgroundFor}). Cor fixa (nao depende da
     * categoria nem da marca), neutra o bastante pra nao competir com a cor
     * do texto de nenhuma categoria.
     */
    private static Color selectionBackground() {
        return FlatLaf.isLafDark() ? new Color(0x3A3F47) : new Color(0xD8DCE3);
    }
}
