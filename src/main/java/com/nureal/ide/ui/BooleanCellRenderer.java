package com.nureal.ide.ui;

import java.awt.Color;
import java.util.Locale;

import javax.swing.SwingConstants;

/**
 * Colunas realmente LOGICAS (tipo SQL BOOLEAN/BIT, ou classe Java
 * {@code Boolean} — ver {@link RendererFactory#classify}, que decide isto
 * SEMPRE pelo tipo de dado real, nunca pelo nome da coluna): valores BINARIOS
 * reconhecidos (true/false, sim/nao, ativo/inativo, 1/0, y/n) continuam
 * mostrando o texto EXATO que o banco devolveu (nunca reescrito), so com a
 * cor semantica de verde/vermelho — pedido explicito do usuario (relatado com
 * uma captura de tela de um check verde no lugar do valor real): "nao deveria
 * ser assim, e sim mostrar exatamente o que o banco retorna". ANTES esta
 * classe substituia a palavra inteira por um icone de check/X desenhado a
 * mao, escondendo se o valor de origem era "1", "true", "Y" etc.
 *
 * Uma coluna chamada "status" mas armazenada como INT/VARCHAR comum (ex.:
 * codigo de dominio 1/2/3) NAO passa mais por este renderer — o antigo atalho
 * "nome contem status -&gt; logico" foi removido a pedido do usuario, que
 * reportou uma coluna assim ganhando cores de "enum" em vez da MESMA cor de
 * qualquer outra coluna do seu tipo real (INT/BIGDECIMAL/etc): "deveria
 * seguir a cor do tipo de dados dele".
 */
final class BooleanCellRenderer extends AbstractTypedCellRenderer {

    private static final long serialVersionUID = 1L;

    @Override
    int alignment(Object value) {
        return SwingConstants.LEFT;
    }

    @Override
    Color colorFor(Object value) {
        String v = value.toString().trim().toUpperCase(Locale.ROOT);
        return switch (v) {
            case "TRUE", "ATIVO", "SIM", "YES", "Y", "1" -> GridTheme.COLOR_LOGIC_TRUE;
            case "FALSE", "INATIVO", "NAO", "NO", "N", "0" -> GridTheme.COLOR_LOGIC_FALSE;
            default -> null; // valor logico nao reconhecido: cor padrao do tema
        };
    }
}
