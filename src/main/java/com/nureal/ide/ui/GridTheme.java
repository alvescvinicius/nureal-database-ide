package com.nureal.ide.ui;

import java.awt.Color;

/**
 * Paleta de cores da grade de resultados — ponto UNICO de verdade para todas
 * as cores usadas por {@link AbstractTypedCellRenderer} e suas subclasses,
 * {@link ColumnHeaderRenderer}, {@link ResultGrid}, {@link RowNumberGutter} e
 * {@link ColumnMetadataPopup}.
 *
 * Antes desta classe, as mesmas cores (ex.: a borda {@code 0xCBD5E1} do
 * cabecalho e do canto do gutter) estavam definidas em duplicidade em varios
 * arquivos — bastava mudar uma paleta para os locais ficarem inconsistentes
 * entre si. Qualquer ajuste futuro de tema (ex.: um modo escuro dedicado a
 * grade) muda em um unico lugar.
 *
 * <p>
 * ESTE E EXATAMENTE ESSE AJUSTE: os campos eram {@code static final}, ou
 * seja, a grade de resultados sempre pintava com a paleta CLARA, mesmo com o
 * app inteiro no tema escuro (FlatDarkLaf) — cabecalho, zebra e gutter
 * apareciam como uma caixa branca "fora do tema" no meio de uma janela
 * escura, e {@link TextCellRenderer} chegava a pintar o texto de toda celula
 * textual em {@code 0x263238} (quase preto) por cima de um fundo escuro,
 * ilegivel. Os campos agora sao mutaveis; {@link #applyPalette(boolean)}
 * troca todos de uma vez para a variante clara ou escura, chamado no
 * arranque (tema claro, o padrao do app) e de novo em
 * {@code MainWindow#toggleTheme} sempre que o usuario alterna o tema.
 */
final class GridTheme {

    private GridTheme() {
    }

    // ---------- Cores por tipo de dado (renderers) ----------
    static Color COLOR_IDENTIFIER;
    static Color COLOR_NUMERIC;

    // ---------- Destaque de chave (grid) — ver AbstractTypedCellRenderer ----------
    // Sobrepoe a cor do TIPO da coluna (nao mexe no alinhamento nem na logica
    // dos 6 grupos de RendererFactory) quando a coluna e, de fato, chave
    // primaria ou estrangeira da tabela de origem — metadados REAIS do banco
    // (via ColumnMetadataResolver), nao heuristica de nome. PK = dourado; FK
    // reaproveita o mesmo laranja de COLOR_IDENTIFIER (mesma familia visual
    // de "chave/identificador").
    static Color COLOR_PRIMARY_KEY;
    static Color COLOR_TEMPORAL;
    static Color COLOR_LOGIC_TRUE;
    static Color COLOR_LOGIC_FALSE;
    static Color COLOR_BINARY;
    static Color COLOR_TEXTUAL;
    static Color COLOR_NULL;

    // ---------- Selecao / celula ativa ----------
    static Color SELECTION_BACKGROUND;
    static Color SELECTION_FOREGROUND;
    static Color ACTIVE_CELL_BORDER;

    // ---------- Zebra / hover (linhas nao selecionadas) ----------
    static Color ZEBRA_EVEN;
    static Color ZEBRA_ODD;
    static Color HOVER_BACKGROUND;

    // ---------- Edicao na grade (ver GridEditController) ----------
    /** Celula com valor editado, ainda nao salvo. */
    static Color EDIT_DIRTY_CELL;
    /** Linha inteira nova (inserida, ainda nao salva). */
    static Color EDIT_NEW_ROW;
    /** Linha marcada para exclusao ao salvar. */
    static Color EDIT_DELETED_ROW;

    // ---------- Grid lines ----------
    static Color GRID_LINE;

    // ---------- Cabecalho ----------
    static Color HEADER_BACKGROUND;
    static Color HEADER_FOREGROUND;
    static Color HEADER_BORDER;
    static Color SORT_INDICATOR_ACTIVE;
    static Color SORT_INDICATOR_INACTIVE;
    /** Cabecalho da coluna atualmente "encontrada" (ver ResultGrid#highlightSelectedColumn). */
    static Color HEADER_HIGHLIGHT_BACKGROUND;
    static Color HEADER_HIGHLIGHT_BORDER;

    // ---------- Coluna de numeracao (gutter) ----------
    static Color GUTTER_BACKGROUND;
    static Color GUTTER_FOREGROUND;

    // ---------- Texto auxiliar (labels de metadados, filtro, etc.) ----------
    static Color MUTED_TEXT;

    static {
        applyPalette(false);
    }

    /**
     * Troca TODA a paleta de uma vez para a variante clara ou escura —
     * chamado no arranque e sempre que {@code MainWindow#toggleTheme} alterna
     * o Look and Feel (FlatLightLaf/FlatDarkLaf). Grades ja construidas (ver
     * {@code ResultGrid}) leem estes campos a cada pintura de celula, entao
     * passam a refletir a nova paleta assim que repintarem; a UNICA excecao
     * ja corrigida era {@code AbstractTypedCellRenderer#COLOR_NULL}, que
     * copiava o valor uma unica vez — ver o javadoc de la.
     */
    static void applyPalette(boolean dark) {
        if (dark) {
            COLOR_IDENTIFIER = new Color(0xFF, 0xB7, 0x4D);
            COLOR_NUMERIC = new Color(0x4D, 0xD0, 0xE1);
            COLOR_PRIMARY_KEY = new Color(0xFF, 0xD5, 0x4F);
            COLOR_TEMPORAL = new Color(0xCE, 0x93, 0xD8);
            COLOR_LOGIC_TRUE = new Color(0x66, 0xBB, 0x6A);
            COLOR_LOGIC_FALSE = new Color(0xE5, 0x73, 0x73);
            COLOR_BINARY = new Color(0xFF, 0x80, 0xAB);
            COLOR_TEXTUAL = new Color(0xD7, 0xDE, 0xE3);
            COLOR_NULL = new Color(0x8A, 0x97, 0xA0);

            SELECTION_BACKGROUND = new Color(0x3E, 0x4A, 0x52);
            SELECTION_FOREGROUND = new Color(0xEC, 0xEF, 0xF1);
            ACTIVE_CELL_BORDER = new Color(0x90, 0xA4, 0xAE);

            ZEBRA_EVEN = new Color(0x2B, 0x2D, 0x30);
            ZEBRA_ODD = new Color(0x31, 0x34, 0x38);
            HOVER_BACKGROUND = new Color(0x3A, 0x3F, 0x44);

            EDIT_DIRTY_CELL = new Color(0x4A, 0x3B, 0x14);
            EDIT_NEW_ROW = new Color(0x1B, 0x43, 0x32);
            EDIT_DELETED_ROW = new Color(0x4A, 0x1F, 0x1F);

            GRID_LINE = new Color(0x3A, 0x3F, 0x44);

            HEADER_BACKGROUND = new Color(0x24, 0x26, 0x29);
            HEADER_FOREGROUND = new Color(0xCB, 0xD5, 0xE1);
            HEADER_BORDER = new Color(0x44, 0x48, 0x4D);
            SORT_INDICATOR_ACTIVE = new Color(0x34, 0xD3, 0x99);
            SORT_INDICATOR_INACTIVE = new Color(0x5B, 0x64, 0x70);
            HEADER_HIGHLIGHT_BACKGROUND = new Color(0x58, 0x4B, 0x14);
            HEADER_HIGHLIGHT_BORDER = new Color(0xCA, 0x8A, 0x04);

            GUTTER_BACKGROUND = new Color(0x24, 0x26, 0x29);
            GUTTER_FOREGROUND = new Color(0x8B, 0x95, 0xA1);

            MUTED_TEXT = new Color(0x9A, 0xA3, 0xAF);
        } else {
            COLOR_IDENTIFIER = new Color(0xE6, 0x51, 0x00);
            COLOR_NUMERIC = new Color(0x00, 0x83, 0x8F);
            COLOR_PRIMARY_KEY = new Color(0xB8, 0x86, 0x0B);
            COLOR_TEMPORAL = new Color(0x7B, 0x1F, 0xA2);
            COLOR_LOGIC_TRUE = new Color(0x2E, 0x7D, 0x32);
            COLOR_LOGIC_FALSE = new Color(0xC6, 0x28, 0x28);
            COLOR_BINARY = new Color(0xF5, 0x00, 0x57);
            COLOR_TEXTUAL = new Color(0x26, 0x32, 0x38);
            COLOR_NULL = new Color(0x90, 0xA4, 0xAE);

            // Era 0xB0BEC5 (Blue Grey 200) — cinza escuro demais, cansava a
            // vista em uso prolongado (pedido explicito do usuario). Mesma
            // familia de cor (Blue Grey), so um tom bem mais claro (100), pra
            // continuar dando contraste claro contra zebra/hover sem pesar na
            // leitura.
            SELECTION_BACKGROUND = new Color(0xCF, 0xD8, 0xDC);
            SELECTION_FOREGROUND = new Color(0x26, 0x32, 0x38);
            ACTIVE_CELL_BORDER = new Color(0x37, 0x47, 0x4F);

            ZEBRA_EVEN = Color.WHITE;
            ZEBRA_ODD = new Color(0xF7, 0xF8, 0xFA);
            HOVER_BACKGROUND = new Color(0xEE, 0xF2, 0xF5);

            EDIT_DIRTY_CELL = new Color(0xFE, 0xF3, 0xC7);
            EDIT_NEW_ROW = new Color(0xDC, 0xFC, 0xE7);
            EDIT_DELETED_ROW = new Color(0xFE, 0xE2, 0xE2);

            GRID_LINE = new Color(0xED, 0xEF, 0xF2);

            HEADER_BACKGROUND = new Color(0xF1, 0xF3, 0xF5);
            HEADER_FOREGROUND = new Color(0x33, 0x41, 0x55);
            HEADER_BORDER = new Color(0xCB, 0xD5, 0xE1);
            SORT_INDICATOR_ACTIVE = new Color(0x05, 0x96, 0x69);
            SORT_INDICATOR_INACTIVE = new Color(0xB0, 0xB8, 0xC1);
            HEADER_HIGHLIGHT_BACKGROUND = new Color(0xFE, 0xF0, 0x8A);
            HEADER_HIGHLIGHT_BORDER = new Color(0xCA, 0x8A, 0x04);

            GUTTER_BACKGROUND = new Color(0xF3, 0xF4, 0xF6);
            GUTTER_FOREGROUND = new Color(0x9A, 0xA3, 0xAF);

            MUTED_TEXT = new Color(0x6B, 0x72, 0x80);
        }
    }
}
