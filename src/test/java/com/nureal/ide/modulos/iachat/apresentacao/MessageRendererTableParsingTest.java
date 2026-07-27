package com.nureal.ide.modulos.iachat.apresentacao;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Testa so a parte pura (sem Swing) da deteccao/parsing de tabela GFM
 * extraida de {@link MessageRenderer} — nenhum teste deste projeto constroi
 * {@code JTable}/{@code JPanel} reais (custo alto, depende de display), ver
 * o mesmo padrao em outras suites deste modulo.
 */
class MessageRendererTableParsingTest {

    @Test
    void separadorPadraoEReconhecido() {
        assertTrue(MessageRenderer.isTableSeparatorRow("|---|---|---|"));
        assertTrue(MessageRenderer.isTableSeparatorRow("| :--- | :---: | ---: |"));
        assertTrue(MessageRenderer.isTableSeparatorRow("---|---"));
    }

    @Test
    void linhaHorizontalSemPipeNaoEConfundidaComSeparador() {
        // "---" sozinho e um separador horizontal comum de markdown, nao uma
        // linha de tabela — precisa ter pelo menos um "|" pra contar.
        assertFalse(MessageRenderer.isTableSeparatorRow("---"));
        assertFalse(MessageRenderer.isTableSeparatorRow("texto qualquer"));
    }

    @Test
    void linhaComTextoEPipeNaoEConfundidaComSeparador() {
        assertFalse(MessageRenderer.isTableSeparatorRow("| ID | Nome |"));
    }

    @Test
    void detectaInicioDeTabelaPeloHeaderMaisSeparador() {
        String[] lines = { "| ID | Nome |", "|---|---|", "| 1 | Ana |" };
        assertTrue(MessageRenderer.isTableHeaderRow(lines, 0));
    }

    @Test
    void naoDetectaTabelaSemLinhaSeparadoraLogoAbaixo() {
        String[] lines = { "| ID | Nome |", "so um paragrafo normal" };
        assertFalse(MessageRenderer.isTableHeaderRow(lines, 0));
    }

    @Test
    void dividECelulasRemovendoPipesDasPontas() {
        List<String> cells = MessageRenderer.splitTableRow("| 1 | Ana | ativo |");
        assertEquals(List.of("1", "Ana", "ativo"), cells);
    }

    @Test
    void dividECelulasSemPipesNasPontasTambemFunciona() {
        List<String> cells = MessageRenderer.splitTableRow("1 | Ana | ativo");
        assertEquals(List.of("1", "Ana", "ativo"), cells);
    }

    @Test
    void celulaComNegritoOuCodigoInlineFicaSemAMarcacao() {
        List<String> cells = MessageRenderer.splitTableRow("| **1** | `operation_order` |");
        assertEquals(List.of("1", "operation_order"), cells);
    }

    @Test
    void stripInlineMarkdownRemoveNegritoECodigo() {
        assertEquals("em andamento", MessageRenderer.stripInlineMarkdown("**em andamento**"));
        assertEquals("operation_order", MessageRenderer.stripInlineMarkdown("`operation_order`"));
        assertEquals("texto normal", MessageRenderer.stripInlineMarkdown("texto normal"));
    }
}
