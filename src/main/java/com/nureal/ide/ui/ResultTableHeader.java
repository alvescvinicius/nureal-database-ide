package com.nureal.ide.ui;

import javax.swing.JTable;
import javax.swing.SortOrder;
import javax.swing.table.JTableHeader;
import java.awt.Cursor;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;

/**
 * Cabecalho da grade de resultados: instala o {@link ColumnHeaderRenderer}
 * (nome + duas setinhas de ordenacao sempre a direita), trata os cliques
 * (metade de cima da zona de ordenacao -&gt; {@link ColumnSorter} crescente;
 * metade de baixo -&gt; decrescente; resto do cabecalho -&gt;
 * {@link SelectionManager#selectColumn}) e o duplo-clique na divisoria
 * (-&gt; {@link ColumnAutoFit}), e liga o popup de metadados no hover
 * ({@link ColumnMetadataPopup}).
 *
 * Substitui o antigo {@code ForeignKeyHeaderSupport}: o indicador de FK
 * continua existindo (agora no {@link ColumnHeaderRenderer}, via
 * {@link ColumnMetadata}), mas o antigo popup de FK por CLIQUE foi
 * substituido pelo popup de metadados por HOVER, mais completo.
 */
final class ResultTableHeader {

    // Faixa de captura da divisoria (redimensionar/duplo-clique de autofit).
    // Pedido explicito do usuario para ficar mais facil de acertar o cursor
    // de redimensionar: era fina (4px) demais pra confiar em acertar o
    // duplo-clique de primeira.
    private static final int RESIZE_HANDLE_PX = 6;

    private ResultTableHeader() {
    }

    static JTableHeader install(JTable table, ColumnSorter sorter, SelectionManager selection,
            ColumnHeaderRenderer.MetadataSource metadataSource, ColumnHeaderRenderer renderer) {
        JTableHeader header = new JTableHeader(table.getColumnModel());
        header.setReorderingAllowed(false);
        header.setDefaultRenderer(renderer);

        // Ancora do arrasto "estilo Excel" (clicar num cabecalho e arrastar
        // para os lados seleciona todas as colunas no caminho, ao vivo,
        // igual a arrastar pela numeracao de linha — ver RowNumberGutter).
        // Array de 1 posicao so pra ficar mutavel dentro das classes anonimas
        // abaixo (variavel local capturada precisa ser efetivamente final).
        // -1 = nenhum arrasto de selecao em andamento (fora de uma coluna
        // valida, OU o clique caiu na divisoria/na seta de ordenacao — essas
        // duas acoes tem seu proprio significado e NUNCA viram arrasto de
        // selecao, pra nao conflitar: redimensionar e nativo do Swing, e so
        // reage a manter o clique DENTRO da faixa fina da divisoria).
        int[] dragAnchorColumn = { -1 };

        // "Foto" de se TODAS as colunas estavam selecionadas ANTES do gesto
        // de clique atual comecar a mexer em qualquer coisa — tirada no
        // PRIMEIRO clique (clickCount==1) de mousePressed, ANTES de qualquer
        // logica que possa alterar a selecao. Necessaria pro duplo-clique na
        // divisoria (ver mouseClicked mais abaixo): o PRIMEIRO clique de um
        // duplo-clique pode errar por 1-2px a faixa estreita da divisoria
        // (RESIZE_HANDLE_PX, jitter natural da mao) e, sem essa foto, cair no
        // fluxo de "clique simples numa coluna", que RESETA a selecao para
        // aquela UNICA coluna antes mesmo do duplo-clique ser reconhecido —
        // bug relatado pelo usuario: "com todas selecionadas, duplo-clique
        // redimensiona so uma coluna". Usando o estado de ANTES do gesto (e
        // nao o estado JA CORROMPIDO no momento do mouseClicked), o
        // duplo-clique sempre acerta a intencao original do usuario.
        boolean[] allColumnsSelectedAtGestureStart = { false };

        header.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.getClickCount() == 1) {
                    allColumnsSelectedAtGestureStart[0] = table.getColumnCount() > 0
                            && table.getSelectedColumnCount() == table.getColumnCount();
                }
                // Sem isto, um clique no cabecalho (selecionar coluna, ordenar,
                // autofit na divisoria) NUNCA move o foco de teclado para a
                // JTable — o header e um Component separado, e o foco fica
                // onde estava antes (outra aba, o editor SQL, etc). Como
                // Ctrl+C/Ctrl+A/Esc/setas sao KeyBindings WHEN_FOCUSED
                // instalados NA TABELA (ver SelectionManager#installKeyBindings),
                // eles simplesmente nao disparavam depois de selecionar uma
                // coluna pelo cabecalho — o mesmo padrao ja usado no clique da
                // coluna de numeracao (ver RowNumberGutter#build).
                table.requestFocusInWindow();
                dragAnchorColumn[0] = -1;
                int viewColumn = header.columnAtPoint(e.getPoint());
                if (viewColumn < 0 || e.getClickCount() != 1) {
                    return; // duplo-clique (autofit) e decidido no RELEASE, ver mouseClicked
                }
                if (columnAtDivider(header, e.getPoint()) >= 0) {
                    return; // inicio de um redimensionamento (nativo do Swing) — nao seleciona nada
                }
                if (arrowAtPoint(header, viewColumn, e.getPoint()) != null) {
                    return; // clique na setinha de ordenacao — decidido no RELEASE, nao arrasta
                }
                if (e.isShiftDown() || e.isControlDown()) {
                    return; // Shift/Ctrl ja tem seu proprio significado (decidido no RELEASE); nao encadeia com arrasto
                }
                // Clique simples numa coluna valida, fora de divisoria/seta:
                // seleciona ela desde JA (visivel no mouse-down, como no
                // Excel) e vira a ancora de um possivel arrasto a seguir.
                dragAnchorColumn[0] = viewColumn;
                selectAndHighlight(viewColumn);
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int dividerColumn = columnAtDivider(header, e.getPoint());
                    if (dividerColumn >= 0) {
                        // Estilo Excel: com TODAS as colunas selecionadas (Ctrl+A,
                        // clique no canto, ou arrastar pelo cabecalho de ponta a
                        // ponta), duplo-clique numa divisoria qualquer ajusta
                        // TODAS de uma vez — sem selecao (ou so ALGUMAS colunas
                        // selecionadas), ajusta so a coluna a ESQUERDA da
                        // divisoria clicada, como sempre foi.
                        //
                        // ALTERNA (nao so expande): se ja esta no tamanho que o
                        // autofit calcularia agora, o duplo-clique ENCOLHE
                        // (tamanho minimizado, ver ColumnAutoFit#applyMinimizedWidth);
                        // caso contrario, EXPANDE ao conteudo — sem isto,
                        // duplo-clicar numa coluna ja expandida nao tinha
                        // efeito visivel nenhum (reaplicava a mesma largura),
                        // pedido explicito do usuario comparando com o Excel.
                        // Encolhe para o tamanho MINIMIZADO, nao para a
                        // largura padrao: numa coluna de conteudo curto (ex.:
                        // "id"), o autofit "expandido" e a largura "padrao"
                        // dao o MESMO numero de pixels (a largura padrao e o
                        // PISO do autofit, ver ColumnAutoFit#computePackedWidth)
                        // — usar a largura padrao como alvo do encolher fazia
                        // o duplo-clique parecer nao fazer nada justamente
                        // nas colunas mais estreitas, tipicamente a primeira
                        // da grade — bug relatado pelo usuario.
                        //
                        // Usa a FOTO tirada no inicio do gesto (ver
                        // allColumnsSelectedAtGestureStart acima), nao o
                        // estado AO VIVO — este ja pode ter sido corrompido
                        // pelo PRIMEIRO clique do proprio duplo-clique, se
                        // ele errou a faixa da divisoria por 1-2px.
                        boolean allColumnsSelected = allColumnsSelectedAtGestureStart[0];
                        if (allColumnsSelected) {
                            if (ColumnAutoFit.allPacked(table)) {
                                ColumnAutoFit.shrinkToMinimum(table);
                            } else {
                                ColumnAutoFit.packColumns(table);
                            }
                        } else if (ColumnAutoFit.isPacked(table, dividerColumn)) {
                            ColumnAutoFit.applyMinimizedWidth(table, dividerColumn);
                        } else {
                            ColumnAutoFit.packColumn(table, dividerColumn);
                        }
                        return;
                    }
                }
                if (e.getClickCount() != 1) {
                    return;
                }
                int viewColumn = header.columnAtPoint(e.getPoint());
                if (viewColumn < 0) {
                    return;
                }
                // A zona da setinha de ordenacao (ultimos ~24px da coluna) e
                // BEM mais larga que a faixa da divisoria de redimensionar
                // (ultimos 6px — ver RESIZE_HANDLE_PX), entao as duas SEMPRE
                // se sobrepoem perto da borda direita — numa coluna estreita
                // (ex.: "id"), a zona de ordenacao pode tomar a coluna
                // INTEIRA. Sem esta checagem, um clique bem em cima da
                // divisoria (tentando redimensionar) sempre "ganhava" como
                // clique de ordenacao em vez de iniciar o arrasto — pedido
                // explicito do usuario, que relatou nao conseguir mais
                // redimensionar colunas estreitas por causa disso. A
                // divisoria agora tem prioridade nessa faixa sobreposta,
                // igual ja acontecia no cursor (ver mouseMoved) e no PRESS
                // (ver acima) — so falta aqui, no clique de ordenacao.
                boolean onDivider = columnAtDivider(header, e.getPoint()) >= 0;
                SortOrder arrow = onDivider ? null : arrowAtPoint(header, viewColumn, e.getPoint());
                if (arrow != null) {
                    int modelColumn = table.getColumnModel().getColumn(viewColumn).getModelIndex();
                    sorter.setDirection(modelColumn, arrow, e.isControlDown());
                    header.repaint();
                } else if (e.isShiftDown() || e.isControlDown()) {
                    // Clique simples (sem modificador) ja foi resolvido no
                    // PRESS acima (dragAnchorColumn) — repetir aqui so
                    // selecionaria a mesma coluna de novo, inofensivo mas
                    // redundante. Shift/Ctrl, por nao arrastarem, continuam
                    // decididos so aqui, no release, como sempre foram.
                    selection.selectColumn(viewColumn, e.isControlDown(), e.isShiftDown());
                    int modelColumn = table.getColumnModel().getColumn(viewColumn).getModelIndex();
                    renderer.setHighlight(modelColumn);
                    header.repaint();
                }
            }

            private void selectAndHighlight(int viewColumn) {
                selection.selectColumn(viewColumn, false, false);
                // Mesmo realce amarelo usado pela busca de coluna do
                // ResultGrid (ver ColumnHeaderRenderer#setHighlight): clicar
                // no cabecalho para selecionar a coluna tambem marca ela
                // visualmente, pedido explicito do usuario.
                int modelColumn = table.getColumnModel().getColumn(viewColumn).getModelIndex();
                renderer.setHighlight(modelColumn);
                header.repaint();
            }
        });
        header.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int viewColumn = header.columnAtPoint(e.getPoint());
                boolean overDivider = columnAtDivider(header, e.getPoint()) >= 0;
                boolean overSortZone = !overDivider && viewColumn >= 0
                        && arrowAtPoint(header, viewColumn, e.getPoint()) != null;
                header.setCursor(Cursor.getPredefinedCursor(overDivider
                        ? Cursor.E_RESIZE_CURSOR
                        : (overSortZone ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR)));
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (dragAnchorColumn[0] < 0) {
                    return; // arrasto comecou na divisoria/seta/Shift/Ctrl — nao e arrasto de selecao
                }
                int viewColumn = header.columnAtPoint(e.getPoint());
                if (viewColumn < 0) {
                    // Arrastou pra fora da faixa de colunas (antes da 1a ou
                    // depois da ultima): gruda na extremidade mais proxima,
                    // igual ao Excel continuar estendendo mesmo passando um
                    // pouco do cabecalho.
                    viewColumn = (e.getX() <= 0) ? 0 : table.getColumnCount() - 1;
                }
                if (viewColumn < 0) {
                    return; // grade sem nenhuma coluna
                }
                selection.selectColumn(viewColumn, false, true); // true = estende da ancora (dragAnchorColumn) ate aqui
                int modelColumn = table.getColumnModel().getColumn(viewColumn).getModelIndex();
                renderer.setHighlight(modelColumn);
                header.repaint();
            }
        });

        table.setTableHeader(header);
        ColumnMetadataPopup.install(table, header, metadataSource);
        return header;
    }

    /**
     * Qual das duas setinhas (ver {@link ColumnHeaderRenderer}) o ponto esta
     * em cima, ou {@code null} se estiver fora da zona de ordenacao (fixa,
     * sempre a direita). A zona inteira e dividida ao MEIO na vertical:
     * metade de cima -&gt; {@code ASCENDING} (setinha ▲), metade de baixo -&gt;
     * {@code DESCENDING} (setinha ▼) — mesma divisao usada pelo
     * {@code GridLayout(2, 1)} do renderer, entao o clique sempre acerta a
     * setinha que o usuario esta vendo.
     */
    private static SortOrder arrowAtPoint(JTableHeader header, int viewColumn, Point p) {
        Rectangle rect = header.getHeaderRect(viewColumn);
        if (p.x < rect.x + rect.width - ColumnHeaderRenderer.SORT_ZONE_WIDTH - 6) {
            return null;
        }
        return (p.y < rect.y + rect.height / 2.0) ? SortOrder.ASCENDING : SortOrder.DESCENDING;
    }

    /**
     * Indice (view) da coluna cuja divisoria DIREITA esta proxima do ponto,
     * ou -1. Visibilidade de pacote (nao {@code private}): tambem consultado
     * por {@link ColumnMetadataPopup}, para NAO abrir o popup de metadados
     * quando o mouse esta em cima da divisoria (cursor de redimensionar) —
     * antes disso, os dois disputavam a mesma faixa de pixels perto da
     * borda, e o popup aparecia bem na hora que o usuario tentava agarrar a
     * divisoria pra redimensionar.
     */
    static int columnAtDivider(JTableHeader header, Point p) {
        int col = header.columnAtPoint(p);
        if (col < 0) {
            return -1;
        }
        Rectangle rect = header.getHeaderRect(col);
        if (Math.abs(p.x - (rect.x + rect.width)) <= RESIZE_HANDLE_PX) {
            return col;
        }
        if (Math.abs(p.x - rect.x) <= RESIZE_HANDLE_PX && col > 0) {
            return col - 1; // divisoria a esquerda pertence a coluna anterior
        }
        return -1;
    }
}
