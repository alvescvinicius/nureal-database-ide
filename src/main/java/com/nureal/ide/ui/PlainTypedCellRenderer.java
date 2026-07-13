package com.nureal.ide.ui;

import java.awt.Color;
import java.util.function.Supplier;

import javax.swing.SwingConstants;

/**
 * Renderer generico para grupos "de texto plano" da grade — mesmo
 * alinhamento (esquerda) e mesma formatacao ({@code toString()} padrao),
 * so muda a COR conforme o grupo que o instancia (ver {@link RendererFactory}):
 * {@link RendererFactory.Group#TEXTUAL}, {@link RendererFactory.Group#ENUM} e
 * {@link RendererFactory.Group#UUID} (Rodada 2: sem cor propria, usam
 * {@code GridTheme.COLOR_DEFAULT_TEXT} via {@code GridTheme#colorFor}),
 * {@link RendererFactory.Group#JSON} e {@link RendererFactory.Group#XML}
 * (continuam com cor propria, significado estrutural) — ver
 * {@code GridTheme#colorFor(com.nureal.ide.core.sql.SqlTypeKind)} para a
 * fonte unica de cada cor.
 *
 * Existe para nao precisar de 5 classes quase identicas: {@code colorSupplier}
 * e chamado a cada pintura (nunca cacheado), entao acompanha
 * {@code GridTheme#applyPalette} sozinho quando o usuario alterna claro/escuro
 * (mesmo motivo documentado em {@code AbstractTypedCellRenderer#COLOR_NULL}).
 */
final class PlainTypedCellRenderer extends AbstractTypedCellRenderer {

    private static final long serialVersionUID = 1L;

    private final Supplier<Color> colorSupplier;

    PlainTypedCellRenderer(Supplier<Color> colorSupplier) {
        this.colorSupplier = colorSupplier;
    }

    @Override
    int alignment(Object value) {
        return SwingConstants.LEFT;
    }

    @Override
    Color colorFor(Object value) {
        return colorSupplier.get();
    }
}
