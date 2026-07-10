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

    // ---------- Regra da maioria para caixa de alias ----------

    @Test
    void aliasDeTabelaSegueAMaioriaDeCaixaEPropagaParaReferenciasQualificadas() {
        SqlFormatter fmt = new SqlFormatter(SqlFormatter.KeywordCase.UPPER, SqlFormatter.Style.STANDARD, false);
        // Alias "p" foi escrito minusculo, "C" e "D" maiusculos: maioria (2x1)
        // e maiuscula, entao "p" vira "P" — tanto na definicao (FROM) quanto
        // em toda referencia qualificada "p.coluna" (inclusive no SELECT e no
        // WHERE, longe de onde o alias foi definido).
        String out = fmt.format(
                "select p.nome, C.email, D.total from pedidos p, clientes C, detalhes D where p.cliente_id = C.id");
        assertTrue(out.contains("pedidos P"), out);
        assertTrue(out.contains("clientes C"), out);
        assertTrue(out.contains("detalhes D"), out);
        assertTrue(out.contains("P.nome"), out);
        assertTrue(out.contains("C.email"), out);
        assertTrue(out.contains("D.total"), out);
        assertTrue(out.contains("P.cliente_id"), out);
        assertTrue(out.contains("C.id"), out);
    }

    @Test
    void semMaioriaClaraDeCaixaDeAliasMantemComoFoiEscrito() {
        SqlFormatter fmt = new SqlFormatter(SqlFormatter.KeywordCase.UPPER, SqlFormatter.Style.STANDARD, false);
        // Um alias minusculo ("p") e um maiusculo ("C"): empate (1x1), entao
        // nada e reescrito — evita "inventar" uma preferencia que o usuario
        // nao demonstrou na maioria das vezes que escreveu.
        String out = fmt.format("select p.nome, C.email from pedidos p, clientes C where p.id = C.id");
        assertTrue(out.contains("pedidos p"), out);
        assertTrue(out.contains("clientes C"), out);
        assertTrue(out.contains("p.id"), out);
        assertTrue(out.contains("C.id"), out);
    }

    @Test
    void aliasComAsSeguemAMesmaRegraDeMaioriaIncluindoAliasDeColuna() {
        SqlFormatter fmt = new SqlFormatter(SqlFormatter.KeywordCase.UPPER, SqlFormatter.Style.STANDARD, false);
        // "Alpha" tem caixa mista (nao vota), "BETA" e maiuscula (1 voto),
        // "prod" e "cat" sao minusculas (2 votos): maioria minuscula, entao
        // TODOS os alias (inclusive o de caixa mista e o maiusculo) viram
        // minusculo.
        String out = fmt.format(
                "select col1 AS Alpha, col2 * 1.1 AS BETA from produtos AS prod, categorias AS cat");
        assertTrue(out.contains("AS alpha"), out);
        assertTrue(out.contains("AS beta"), out);
        assertTrue(out.contains("AS prod"), out);
        assertTrue(out.contains("AS cat"), out);
    }

    @Test
    void identificadorEntreCrasesNuncaTemACaixaAlterada() {
        SqlFormatter fmt = new SqlFormatter(SqlFormatter.KeywordCase.UPPER, SqlFormatter.Style.STANDARD, false);
        // Alias entre crases e sinal de que o usuario quer preservar o nome
        // exatamente como escreveu — nunca deve virar voto nem ser reescrito,
        // mesmo se todo o resto da instrucao apontar pra maioria diferente.
        String out = fmt.format(
                "select p.nome, C.email, D.total from pedidos p, clientes C, detalhes `D` where p.id = C.id");
        assertTrue(out.contains("`D`"), out);
    }
}
