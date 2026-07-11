package com.nureal.ide.ui;

import java.awt.Color;

import javax.swing.SwingConstants;

/**
 * Colunas TEXTUAIS classificadas como "enum-like" pelo {@link EnumColumnDetector}
 * (poucos valores curtos que se repetem — ex.: RECEITA/DESPESA,
 * ATIVO/INATIVO): texto plano (igual {@link TextCellRenderer}), so que colorido
 * por VALOR — ver {@link GridTheme#badgeColorsFor(String)} — mesma cor para o
 * mesmo texto em qualquer coluna/grade do app.
 * <p>
 * ANTES desenhava um "pill" (fundo arredondado atras do texto). Removido a
 * pedido do usuario: o retangulo colorido ficava competindo com o fundo
 * zebra/hover da propria celula e "bugava" visualmente a grade (linhas com
 * blocos solidos coloridos em vez de so texto colorido, ver captura de tela
 * relatada). Sem {@link #paintComponent} sobrescrito, a base
 * ({@link AbstractTypedCellRenderer}) ja pinta o texto plano na cor devolvida
 * por {@link #colorFor} — igual a qualquer outro tipo de coluna.
 */
final class BadgeCellRenderer extends AbstractTypedCellRenderer {

    private static final long serialVersionUID = 1L;

    @Override
    int alignment(Object value) {
        return SwingConstants.LEFT;
    }

    @Override
    Color colorFor(Object value) {
        // So o texto (indice 1 do par [fundo, texto]) — o fundo do pill nao
        // e mais usado, mas continua existindo em GridTheme.BADGE_PALETTE
        // porque o texto de cada par ja foi calibrado pra 1 contraste bom
        // direto sobre a zebra clara/escura da grade (mesmo criterio que as
        // outras cores por tipo, ex. COLOR_LOGIC_TRUE/COLOR_IDENTIFIER).
        return GridTheme.badgeColorsFor(value.toString())[1];
    }
}
