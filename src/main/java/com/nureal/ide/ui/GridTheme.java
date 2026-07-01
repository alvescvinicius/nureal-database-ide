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
 */
final class GridTheme {

    private GridTheme() {
    }

    // ---------- Cores por tipo de dado (renderers) ----------
    static final Color COLOR_IDENTIFIER = new Color(0xE65100);
    static final Color COLOR_NUMERIC = new Color(0x00838F);
    static final Color COLOR_TEMPORAL = new Color(0x7B1FA2);
    static final Color COLOR_LOGIC_TRUE = new Color(0x2E7D32);
    static final Color COLOR_LOGIC_FALSE = new Color(0xC62828);
    static final Color COLOR_BINARY = new Color(0xF50057);
    static final Color COLOR_TEXTUAL = new Color(0x263238);
    static final Color COLOR_NULL = new Color(0x90A4AE);

    // ---------- Selecao / celula ativa ----------
    static final Color SELECTION_BACKGROUND = new Color(0xB0BEC5);
    static final Color SELECTION_FOREGROUND = new Color(0x263238);
    static final Color ACTIVE_CELL_BORDER = new Color(0x37474F);

    // ---------- Zebra / hover (linhas nao selecionadas) ----------
    static final Color ZEBRA_EVEN = Color.WHITE;
    static final Color ZEBRA_ODD = new Color(0xF7F8FA);
    static final Color HOVER_BACKGROUND = new Color(0xEEF2F5);

    // ---------- Grid lines ----------
    static final Color GRID_LINE = new Color(0xEDEFF2);

    // ---------- Cabecalho ----------
    static final Color HEADER_BACKGROUND = new Color(0xF1F3F5);
    static final Color HEADER_FOREGROUND = new Color(0x334155);
    static final Color HEADER_BORDER = new Color(0xCBD5E1);
    static final Color SORT_INDICATOR_ACTIVE = new Color(0x059669);
    static final Color SORT_INDICATOR_INACTIVE = new Color(0xB0B8C1);

    // ---------- Coluna de numeracao (gutter) ----------
    static final Color GUTTER_BACKGROUND = new Color(0xF3F4F6);
    static final Color GUTTER_FOREGROUND = new Color(0x9AA3AF);

    // ---------- Texto auxiliar (labels de metadados, filtro, etc.) ----------
    static final Color MUTED_TEXT = new Color(0x6B7280);
}
