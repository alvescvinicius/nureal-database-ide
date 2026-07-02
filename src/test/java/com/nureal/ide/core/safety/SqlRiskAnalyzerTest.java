package com.nureal.ide.core.safety;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Testes da logica de "isto e arriscado e merece confirmacao" — pura
 * (entrada -&gt; saida, sem Swing/JDBC), entao cobrir com testes nao exige
 * mexer em nenhum codigo existente.
 */
class SqlRiskAnalyzerTest {

    @Test
    void nuloOuVazioNaoERisco() {
        assertNull(SqlRiskAnalyzer.riskReason(null));
        assertNull(SqlRiskAnalyzer.riskReason(""));
        assertNull(SqlRiskAnalyzer.riskReason("   "));
        assertFalse(SqlRiskAnalyzer.isRisky(null));
    }

    @Test
    void selectNuncaERisco() {
        assertNull(SqlRiskAnalyzer.riskReason("SELECT * FROM users"));
        assertFalse(SqlRiskAnalyzer.isRisky("SELECT * FROM users"));
    }

    @Test
    void deleteSemWhereERisco() {
        String reason = SqlRiskAnalyzer.riskReason("DELETE FROM users");
        assertTrue(SqlRiskAnalyzer.isRisky("DELETE FROM users"));
        assertEquals("DELETE sem WHERE — apaga TODAS as linhas da tabela.", reason);
    }

    @Test
    void deleteComWhereNoNivelPrincipalNaoERisco() {
        assertNull(SqlRiskAnalyzer.riskReason("DELETE FROM users WHERE id = 1"));
    }

    @Test
    void whereDentroDeSubconsultaNaoProtegeOdeleteExterno() {
        // O WHERE existe, mas so dentro dos parenteses da subquery (nivel > 0)
        // — nao conta como filtro do DELETE externo, que continua sem WHERE
        // no nivel principal.
        String reason = SqlRiskAnalyzer.riskReason("DELETE FROM (SELECT * FROM users WHERE id = 1)");
        assertEquals("DELETE sem WHERE — apaga TODAS as linhas da tabela.", reason);
    }

    @Test
    void whereNoNivelPrincipalDoDeleteProtegeMesmoComSubqueryNoWhere() {
        // Aqui o WHERE esta no nivel 0 do DELETE (ainda que contenha uma
        // subquery dentro dele) — protege normalmente.
        assertNull(SqlRiskAnalyzer.riskReason(
                "DELETE FROM users WHERE id IN (SELECT id FROM tmp)"));
    }

    @Test
    void updateSemWhereERisco() {
        assertEquals("UPDATE sem WHERE — altera TODAS as linhas da tabela.",
                SqlRiskAnalyzer.riskReason("UPDATE users SET active = 0"));
    }

    @Test
    void updateComWhereNaoERisco() {
        assertNull(SqlRiskAnalyzer.riskReason("UPDATE users SET active = 0 WHERE id = 1"));
    }

    @Test
    void truncateSempreERisco() {
        assertEquals("TRUNCATE — apaga TODAS as linhas (irreversivel).",
                SqlRiskAnalyzer.riskReason("TRUNCATE TABLE users"));
    }

    @Test
    void dropSempreERisco() {
        assertEquals("DROP — remove o objeto do banco (irreversivel).",
                SqlRiskAnalyzer.riskReason("DROP TABLE users"));
    }

    @Test
    void alterSempreERisco() {
        assertEquals("ALTER — altera a estrutura do objeto (DDL).",
                SqlRiskAnalyzer.riskReason("ALTER TABLE users ADD COLUMN x INT"));
    }

    @Test
    void renameSempreERisco() {
        assertEquals("RENAME — renomeia objeto (DDL).",
                SqlRiskAnalyzer.riskReason("RENAME TABLE a TO b"));
    }

    @Test
    void createSempreERisco() {
        assertEquals("CREATE — comando de definicao de estrutura (DDL).",
                SqlRiskAnalyzer.riskReason("CREATE TABLE x (id INT)"));
    }

    @Test
    void primeiraPalavraIgnoraCaixaEComentarios() {
        // "Delete" com D maiusculo continua sendo reconhecido
        assertTrue(SqlRiskAnalyzer.isRisky("Delete From users"));
        // Comentario de linha contendo "WHERE" nao conta como WHERE de verdade
        assertTrue(SqlRiskAnalyzer.isRisky("DELETE FROM users -- WHERE id = 1\n"));
    }

    @Test
    void isStructuralChangeCobreApenasDdlDeEstrutura() {
        assertTrue(SqlRiskAnalyzer.isStructuralChange("CREATE TABLE x (id INT)"));
        assertTrue(SqlRiskAnalyzer.isStructuralChange("ALTER TABLE x ADD COLUMN y INT"));
        assertTrue(SqlRiskAnalyzer.isStructuralChange("DROP TABLE x"));
        assertTrue(SqlRiskAnalyzer.isStructuralChange("RENAME TABLE a TO b"));

        // TRUNCATE e arriscado, mas nao e considerado troca estrutural
        // (nao recarrega o navegador de objetos) — comportamento do codigo atual.
        assertFalse(SqlRiskAnalyzer.isStructuralChange("TRUNCATE TABLE x"));
        assertFalse(SqlRiskAnalyzer.isStructuralChange("SELECT 1"));
        assertFalse(SqlRiskAnalyzer.isStructuralChange(null));
        assertFalse(SqlRiskAnalyzer.isStructuralChange(""));
    }
}
