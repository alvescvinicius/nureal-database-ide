package com.nureal.ide.ui;
import com.nureal.ide.compartilhado.designsystem.GridTheme;

import java.awt.Color;

import javax.swing.SwingConstants;

/**
 * Colunas realmente LOGICAS (tipo SQL BOOLEAN/BIT, ou classe Java
 * {@code Boolean} — ver {@link RendererFactory#classify}, que decide isto
 * SEMPRE pelo tipo de dado real, nunca pelo nome da coluna): mostram o texto
 * EXATO que o banco devolveu (true/false, 1/0, Y/N, sim/nao — nunca
 * reescrito, pedido explicito do usuario ja atendido antes).
 *
 * Rodada 2 do "Sistema Semantico de Cores": a cor VOLTOU a depender do VALOR
 * (TRUE = azul, FALSE = laranja — ver {@link GridTheme#COLOR_BOOLEAN_TRUE}/
 * {@link GridTheme#COLOR_BOOLEAN_FALSE}), revertendo a decisao da rodada
 * anterior (cor roxa uniforme por TIPO, {@link GridTheme#COLOR_BOOLEAN},
 * mantida so como fallback quando o valor nao e reconhecido como true/false).
 * Pedido explicito do usuario, com uma ressalva tambem explicita: nunca usar
 * vermelho puro para FALSE (por isso laranja, nao a mesma cor de
 * {@code GridTheme.COLOR_LOGIC_FALSE}, que segue vermelho e reservada a
 * indicadores de STATUS do app, ex. conectado/desconectado — nao esta grade).
 *
 * {@link #truthValue(Object)} reconhece as representacoes textuais/numericas
 * mais comuns que um banco pode devolver para BOOLEAN/BIT, sempre
 * case-insensitive: true/false, 1/0, y/n, s/n (sim/nao), on/off,
 * ativo/inativo, t/f.
 *
 * Uma coluna chamada "status" mas armazenada como INT/VARCHAR comum (ex.:
 * codigo de dominio 1/2/3) NAO passa por este renderer — a classificacao e
 * SEMPRE pelo tipo de dado real (ver {@link RendererFactory#classify}), nunca
 * pelo nome da coluna.
 */
final class BooleanCellRenderer extends AbstractTypedCellRenderer {

    private static final long serialVersionUID = 1L;

    @Override
    int alignment(Object value) {
        return SwingConstants.LEFT;
    }

    @Override
    Color colorFor(Object value) {
        Boolean truth = truthValue(value);
        if (truth == null) {
            return GridTheme.COLOR_BOOLEAN;
        }
        return truth ? GridTheme.COLOR_BOOLEAN_TRUE : GridTheme.COLOR_BOOLEAN_FALSE;
    }

    /**
     * Interpreta o valor bruto (qualquer tipo Java que o driver JDBC possa
     * devolver para BOOLEAN/BIT: {@code Boolean}, numero, ou texto) como
     * true/false, ou {@code null} se nao reconhecido — nesse caso {@link
     * #colorFor} cai de volta na cor uniforme antiga em vez de arriscar um
     * palpite errado.
     */
    private static Boolean truthValue(Object value) {
        if (value instanceof Boolean b) {
            return b;
        }
        if (value instanceof Number n) {
            double d = n.doubleValue();
            if (d == 1.0) {
                return Boolean.TRUE;
            }
            if (d == 0.0) {
                return Boolean.FALSE;
            }
            return null;
        }
        String s = value.toString().trim();
        return switch (s.toLowerCase(java.util.Locale.ROOT)) {
            case "true", "1", "y", "yes", "s", "sim", "on", "ativo", "t" -> Boolean.TRUE;
            case "false", "0", "n", "no", "nao", "não", "off", "inativo", "f" -> Boolean.FALSE;
            default -> null;
        };
    }
}
