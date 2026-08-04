package com.nureal.ide.ui;

import java.math.BigDecimal;

/**
 * Resumo da selecao atual da grade de resultados, estilo barra de status do
 * Excel — ver {@link ResultGrid#updateSelectionSummary()} (quem calcula) e
 * {@link ResultStatusBar#updateSelectionSummary} (quem exibe).
 *
 * {@code sum}/{@code average}/{@code min}/{@code max} sao {@code null} quando
 * {@code numericCount == 0} (selecao sem nenhum valor numerico — so texto,
 * datas, nulos).
 */
record SelectionStats(int cellCount, int numericCount, BigDecimal sum, BigDecimal average,
        BigDecimal min, BigDecimal max) {
}
