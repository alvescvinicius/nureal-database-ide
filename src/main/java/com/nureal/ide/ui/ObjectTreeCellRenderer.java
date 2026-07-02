package com.nureal.ide.ui;

import com.formdev.flatlaf.FlatLaf;

import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
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
 * objetos dentro dela: SO o nome em negrito, na cor da categoria — nenhum
 * fundo. Uma categoria SEM nenhum objeto (contador "(0)") fica em cinza
 * mudo e sem negrito, para nao competir visualmente com as que tem
 * conteudo de verdade (ver {@link #applyCategoryColor}).</li>
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
        if (obj != null) {
            boolean emptyCategory = obj.type() == MainWindow.NodeType.CATEGORY && node.getChildCount() == 0;
            style(obj, emptyCategory);
        }
        applyRowBackground(tree, selected);
        return this;
    }

    /**
     * Fundo da linha — SO existe para a selecao (ver {@link #backgroundFor});
     * fora disso o label fica transparente, como um JLabel comum dentro de
     * um JTree. Quando ha cor, cobre a linha INTEIRA — ver javadoc da
     * classe.
     */
    private void applyRowBackground(JTree tree, boolean selected) {
        Color bg = backgroundFor(selected);
        if (bg == null) {
            setOpaque(false);
            return;
        }
        setOpaque(true);
        setBackground(bg);
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
     * {@link #style}/{@link #applyCategoryColor}).
     */
    private static Color backgroundFor(boolean selected) {
        return selected ? selectionBackground() : null;
    }

    private void style(MainWindow.ObjNode obj, boolean emptyCategory) {
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
            // Cabecalho da categoria: negrito + cor da categoria (e' um
            // titulo de secao, pode pesar mais) — OU, quando ela nao tem
            // nenhum objeto dentro (contador "(0)"), cinza mudo e peso
            // normal, pra nao chamar atencao a toa.
            case CATEGORY -> applyCategoryColor(obj.kind(), emptyCategory, true);
            // Os proprios objetos abriveis (tabela/view/procedure/function/
            // trigger): cor da categoria, mas SEM negrito — negrito em
            // cada uma das linhas (o cabecalho ja e negrito) deixava tudo
            // com o mesmo peso visual, uma "parede" de verde forte sem
            // hierarquia entre o titulo da secao e os itens dentro dela.
            // Cor sozinha (sem negrito) ja basta pra identificar a
            // categoria, e fica bem mais leve de olhar numa lista longa.
            case TABLE, VIEW, ROUTINE, TRIGGER -> applyCategoryColor(obj.kind(), false, false);
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
     * Cor (+ opcionalmente negrito) do texto para uma categoria. {@code
     * muted}: cinza e peso normal, ignora {@code bold} (categoria vazia,
     * contador "(0)"). Caso contrario: cor propria da categoria (ver
     * {@link #textColorFor}), em negrito so quando {@code bold} — usado
     * para diferenciar o CABECALHO da categoria (negrito, "titulo de
     * secao") dos objetos dentro dela (cor sozinha, sem negrito — ver
     * {@link #style}).
     */
    private void applyCategoryColor(String kind, boolean muted, boolean bold) {
        if (muted) {
            setForeground(GridTheme.MUTED_TEXT);
            return;
        }
        Color text = textColorFor(kind);
        if (text == null) {
            return;
        }
        setForeground(text);
        if (bold) {
            setFont(getFont().deriveFont(Font.BOLD));
        }
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

    /**
     * Cor de TEXTO de uma categoria, indexada pelo MESMO "kind" ja usado no
     * resto da IDE para identificar o objeto ("TABLE", "VIEW", "PROCEDURE",
     * "FUNCTION", "TRIGGER" — ver {@code MainWindow.ObjNode#kind}). Duas
     * variantes: clara (tema padrao) e uma mais viva/clara (tema escuro,
     * senao ficaria escura demais sobre fundo escuro), escolhida via
     * {@link FlatLaf#isLafDark()} — reavaliada a cada pintura, entao segue
     * o toggle de tema automaticamente, sem precisar recriar a arvore.
     */
    private static Color textColorFor(String kind) {
        if (kind == null) {
            return null;
        }
        boolean dark = FlatLaf.isLafDark();
        return switch (kind) {
            case "TABLE" -> dark ? new Color(0x5EEAD4) : new Color(0x0F766E);
            case "VIEW" -> dark ? new Color(0xC4B5FD) : new Color(0x6D28D9);
            case "PROCEDURE" -> dark ? new Color(0xFDBA74) : new Color(0xC2410C);
            case "FUNCTION" -> dark ? new Color(0x86EFAC) : new Color(0x15803D);
            case "TRIGGER" -> dark ? new Color(0xF9A8D4) : new Color(0xBE185D);
            default -> null;
        };
    }
}
