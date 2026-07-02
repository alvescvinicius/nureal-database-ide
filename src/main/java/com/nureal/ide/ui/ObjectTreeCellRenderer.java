package com.nureal.ide.ui;

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
 * Arvore de objetos — visual DELIBERADAMENTE simples (2a versao; a primeira,
 * com um bloco de fundo colorido cobrindo cada linha inteira por categoria,
 * ficou "gritando" demais e foi abandonada a pedido do usuario):
 *
 * <ul>
 * <li>Sem icone de tipo (schema/tabela/view/funcao/procedure/trigger) e sem
 * icone padrao de pasta/arquivo do Swing — a lista ficava poluida
 * visualmente. O unico "icone" que sobrevive e a bolinha de status na RAIZ
 * (schema), no lugar do triangulo de expandir/recolher que o {@code JTree}
 * nao desenha mais ali (ver {@code MainWindow#buildObjectBrowser},
 * {@code setShowsRootHandles(false)}) — a MESMA bolinha verde/ambar/cinza da
 * lista de conexoes (ver {@link ConnectionsPanel#statusDot}).</li>
 * <li>Categoria (Tabelas/Visualizacoes/Procedures/Functions/Triggers) e os
 * objetos dentro dela: SEM cor por categoria (removida a pedido do usuario —
 * "gritava" demais) — texto no peso/cor padrao da arvore. O UNICO destaque e
 * negrito num cinza medio (nunca preto forte) enquanto o galho estiver
 * EXPANDIDO, ou seja, o "caminho" por onde se esta navegando agora (ver
 * {@link #applyPathStyle}). Uma categoria SEM nenhum objeto (contador "(0)")
 * fica sempre em cinza mudo e peso normal, nunca em negrito.</li>
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
        MainWindow.ObjNode obj = (node != null && node.getUserObject() instanceof MainWindow.ObjNode o) ? o : null;
        boolean isSchema = obj != null && obj.type() == MainWindow.NodeType.SCHEMA;
        if (obj != null) {
            boolean emptyCategory = obj.type() == MainWindow.NodeType.CATEGORY && node.getChildCount() == 0;
            style(obj, emptyCategory, expanded);
        }
        paintSwitchArrow = isSchema;
        // A linha do schema precisa da largura esticada SEMPRE (nao so quando
        // selecionada) — sem isto nao ha como saber, em paintComponent, onde
        // fica a "ponta direita" da linha pra desenhar a setinha.
        applyRowBackground(tree, selected, isSchema);
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
     */
    private void applyRowBackground(JTree tree, boolean selected, boolean stretchWidth) {
        Color bg = backgroundFor(selected);
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
        // Superestima a largura (arvore inteira, com folga) em vez de tentar
        // calcular a posicao X exata da celula (indentacao + icones variam
        // por profundidade) — o excesso e cortado pelo clip normal de
        // pintura do Swing, nunca aparece por cima de outra coisa.
        int width = Math.max(pref.width, Math.max(tree.getWidth(), 1000));
        setPreferredSize(new Dimension(width, pref.height));
    }

    /**
     * Cor de fundo da linha SELECIONADA: cinza clarinho (ver
     * {@link #selectionBackground}) para QUALQUER linha, inclusive o schema
     * (raiz) — mesma barra para todo mundo. NAO selecionada: sempre
     * {@code null} — nenhuma categoria pinta fundo, so o texto (ver
     * {@link #style}/{@link #applyPathStyle}).
     */
    private static Color backgroundFor(boolean selected) {
        return selected ? selectionBackground() : null;
    }

    private void style(MainWindow.ObjNode obj, boolean emptyCategory, boolean expanded) {
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
            // procedure/function/trigger): SEM cor por categoria (removida a
            // pedido do usuario — ficava "gritando" demais). O unico
            // destaque agora e peso da fonte, e so enquanto o galho estiver
            // EXPANDIDO — ou seja, o "caminho" por onde estamos navegando
            // agora fica em negrito; o resto usa o texto padrao da arvore.
            case CATEGORY -> applyPathStyle(emptyCategory, expanded);
            case TABLE, VIEW, ROUTINE, TRIGGER -> applyPathStyle(false, expanded);
            // Coluna: o texto (nome em negrito + tipo em cinza) e todo
            // montado via HTML em columnHtml — sem cor de categoria (fica
            // discreta de proposito, ja aninhada duas vezes).
            case COLUMN -> setText(columnHtml(obj.name(), obj.columnType()));
            case SCHEMA_PICK -> {
                // mantem o padrao — lista de escolha de schema, sem categoria.
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

    /** Nome da coluna em negrito + tipo em cinza mudo, ex.: <b>id</b> : bigint. */
    private static String columnHtml(String name, String type) {
        String hexMuted = String.format("#%06X", GridTheme.MUTED_TEXT.getRGB() & 0xFFFFFF);
        String safeName = escape(name);
        String safeType = escape(type == null ? "" : type);
        return "<html><b>" + safeName + "</b><span style='color:" + hexMuted + "'> : " + safeType + "</span></html>";
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
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
