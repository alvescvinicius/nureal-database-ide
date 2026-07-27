package com.nureal.ide.ui;
import com.nureal.ide.compartilhado.designsystem.GridTheme;

import java.awt.Color;
import java.util.function.Supplier;

import javax.swing.SwingConstants;

/**
 * Colunas numericas, alinhadas a direita — uma instancia para
 * {@link RendererFactory.Group#INTEGER} e outra para
 * {@link RendererFactory.Group#DECIMAL}: mesmo alinhamento e formatacao.
 * Rodada 2 do "Sistema Semantico de Cores": as duas usam a MESMA cor agora
 * ({@code GridTheme.COLOR_DEFAULT_TEXT}, via {@code GridTheme#colorFor} —
 * numero deixou de ter cor propria na grade, so o EDITOR SQL ainda distingue
 * inteiro/decimal por cor); {@code colorSupplier} continua existindo (chamado
 * a cada pintura) so pra acompanhar sozinho a troca de tema claro/escuro.
 */
final class NumberCellRenderer extends AbstractTypedCellRenderer {

    private static final long serialVersionUID = 1L;

    private final Supplier<Color> colorSupplier;

    NumberCellRenderer(Supplier<Color> colorSupplier) {
        this.colorSupplier = colorSupplier;
    }

    @Override
    int alignment(Object value) {
        return SwingConstants.RIGHT;
    }

    @Override
    Color colorFor(Object value) {
        return colorSupplier.get();
    }

    /**
     * DECIMAL/NUMERIC chega do JDBC como {@link java.math.BigDecimal}, que
     * PRESERVA a escala exata da coluna — uma coluna definida como
     * {@code DECIMAL(20,8)} devolve "112500.00000000" mesmo quando o valor
     * "real" e um inteiro redondo, poluindo a grade com zeros sem nenhum
     * significado. Pedido explicito do usuario: podar os zeros a direita da
     * parte decimal, mas nunca abaixo de duas casas (".00" minimo) e nunca
     * cortando um digito significativo (ver {@link #stripTrailingZeros}).
     *
     * INTEGER (Long/Integer/etc., sem parte decimal) passa direto por
     * {@code toString()} — a poda so faz sentido quando ha um ponto decimal.
     */
    @Override
    String formatValue(Object value) {
        if (value instanceof java.math.BigDecimal bd) {
            return stripTrailingZeros(bd.toPlainString());
        }
        return super.formatValue(value);
    }

    /**
     * Remove zeros a direita da parte decimal de {@code raw} (uma string
     * numerica no formato "123.456", sem notacao cientifica — garantido pelo
     * chamador via {@link java.math.BigDecimal#toPlainString()}).
     *
     * Regras (exemplos completos no pedido original do usuario):
     * "112500.00000000" -&gt; "112500.00" (nada sobra -&gt; minimo de duas casas)
     * "2.40000000"       -&gt; "2.40"       (sobra 1 digito -&gt; completa pra duas)
     * "150.12340000"     -&gt; "150.1234"   (sobram 4 digitos significativos -&gt; preserva todos)
     * "150.12345678"     -&gt; "150.12345678" (sem zero a direita -&gt; nada muda)
     */
    private static String stripTrailingZeros(String raw) {
        int dot = raw.indexOf('.');
        if (dot < 0) {
            return raw;
        }
        String intPart = raw.substring(0, dot);
        String fracPart = raw.substring(dot + 1);
        int end = fracPart.length();
        while (end > 0 && fracPart.charAt(end - 1) == '0') {
            end--;
        }
        fracPart = fracPart.substring(0, end);
        if (fracPart.isEmpty()) {
            fracPart = "00";
        } else if (fracPart.length() == 1) {
            fracPart = fracPart + "0";
        }
        return intPart + "." + fracPart;
    }
}
