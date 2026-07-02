package com.nureal.ide.core.format;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Testes do formatador de SQL — classe pura (entrada -&gt; saida, sem
 * Swing/JDBC). Os textos esperados abaixo foram conferidos rodando o
 * proprio {@link SqlFormatter} (nao sao "achismo"): qualquer mudanca de
 * comportamento no formatador precisa atualizar este teste de proposito,
 * o que serve como rede de seguranca contra regressoes acidentais de
 * espacamento/alinhamento.
 */
class SqlFormatterTest {

    @Test
    void nuloRetornaVazio() {
        assertEquals("", new SqlFormatter().format(null));
    }

    @Test
    void vazioOuEmBrancoRetornaOMesmoTexto() {
        assertEquals("   ", new SqlFormatter().format("   "));
    }

    @Test
    void styleRiverAlinhaClausulasADireitaEMaiusculiza() {
        String out = new SqlFormatter().format(
                "select id, name from users where id = 1 and active = 1");
        String expected = String.join("\n",
                "SELECT id,",
                "       name",
                "  FROM users",
                " WHERE id = 1",
                "   AND active = 1");
        assertEquals(expected, out);
    }

    @Test
    void styleStandardIndentaConteudoAbaixoDaClausula() {
        SqlFormatter fmt = new SqlFormatter(SqlFormatter.KeywordCase.UPPER, SqlFormatter.Style.STANDARD, false);
        String out = fmt.format("select id, name from users where id = 1");
        String expected = String.join("\n",
                "SELECT",
                "    id,",
                "    name",
                "FROM",
                "    users",
                "WHERE",
                "    id = 1");
        assertEquals(expected, out);
    }

    @Test
    void styleCommaFirstColocaVirgulaNoInicioDaLinha() {
        SqlFormatter fmt = new SqlFormatter(SqlFormatter.KeywordCase.UPPER, SqlFormatter.Style.COMMA_FIRST, false);
        String out = fmt.format("select id, name, email from users");
        String expected = String.join("\n",
                "SELECT",
                "      id",
                "    , name",
                "    , email",
                "FROM users");
        assertEquals(expected, out);
    }

    @Test
    void keywordCaseLowerForcaMinusculas() {
        SqlFormatter fmt = new SqlFormatter(SqlFormatter.KeywordCase.LOWER, SqlFormatter.Style.RIVER, false);
        String out = fmt.format("SELECT id FROM users WHERE id = 1");
        String expected = String.join("\n",
                "select id",
                "  from users",
                " where id = 1");
        assertEquals(expected, out);
    }

    @Test
    void keywordCasePreservePreservaCaixaOriginal() {
        SqlFormatter fmt = new SqlFormatter(SqlFormatter.KeywordCase.PRESERVE, SqlFormatter.Style.RIVER, false);
        String out = fmt.format("Select id From users");
        String expected = String.join("\n",
                "Select id",
                "  From users");
        assertEquals(expected, out);
    }

    @Test
    void conteudoDeStringLiteralNuncaEAlteradoMesmoParecendoComPalavraChave() {
        String out = new SqlFormatter().format("select 'select from where' as texto from dual");
        // O literal deve aparecer intacto (minusculo, aspas simples) mesmo
        // com o preset UPPER ligado para o resto da instrucao.
        assertTrue(out.contains("'select from where'"));
        String expected = String.join("\n",
                "SELECT 'select from where' AS texto",
                "  FROM dual");
        assertEquals(expected, out);
    }
}
