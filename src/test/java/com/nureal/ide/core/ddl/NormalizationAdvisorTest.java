package com.nureal.ide.core.ddl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.nureal.ide.core.metadata.model.ForeignKeyInfo;
import com.nureal.ide.core.metadata.model.IndexInfo;
import com.nureal.ide.core.metadata.model.NewColumnSpec;

/**
 * Testes do motor de sugestoes do assistente de DDL — cada teste isola UMA
 * regra (constrói o cenario minimo que a aciona) para servir de rede de
 * seguranca contra regressao e de documentacao viva do que cada regra
 * detecta.
 */
class NormalizationAdvisorTest {

    private static NewColumnSpec col(String name, String type, boolean pk) {
        return new NewColumnSpec(name, type, "", !pk, pk, false, "", "");
    }

    @Test
    void semColunaNenhumaNaoSugereNada() {
        assertTrue(NormalizationAdvisor.analyze("pedidos", List.of(), List.of(), List.of()).isEmpty());
    }

    @Test
    void semPrimaryKeySugereChavePrimaria() {
        List<NewColumnSpec> cols = List.of(col("nome", "VARCHAR", false));
        List<String> out = NormalizationAdvisor.analyze("clientes", cols, List.of(), List.of());
        assertTrue(out.stream().anyMatch(s -> s.contains("PRIMARY KEY")), out.toString());
    }

    @Test
    void comPrimaryKeyNaoSugereChavePrimaria() {
        List<NewColumnSpec> cols = List.of(col("id", "INT", true), col("nome", "VARCHAR", false));
        List<String> out = NormalizationAdvisor.analyze("clientes", cols, List.of(), List.of());
        assertFalse(out.stream().anyMatch(s -> s.contains("PRIMARY KEY")), out.toString());
    }

    @Test
    void dinheiroComFloatSugereDecimal() {
        List<NewColumnSpec> cols = List.of(col("id", "INT", true), col("preco", "FLOAT", false));
        List<String> out = NormalizationAdvisor.analyze("produtos", cols, List.of(), List.of());
        assertTrue(out.stream().anyMatch(s -> s.contains("preco") && s.contains("DECIMAL")), out.toString());
    }

    @Test
    void dinheiroComDecimalNaoSugereNada() {
        List<NewColumnSpec> cols = List.of(col("id", "INT", true), col("preco", "DECIMAL", false));
        List<String> out = NormalizationAdvisor.analyze("produtos", cols, List.of(), List.of());
        assertFalse(out.stream().anyMatch(s -> s.contains("DECIMAL")), out.toString());
    }

    @Test
    void flagComIntSugereBoolean() {
        List<NewColumnSpec> cols = List.of(col("id", "INT", true), col("ativo", "INT", false));
        List<String> out = NormalizationAdvisor.analyze("clientes", cols, List.of(), List.of());
        assertTrue(out.stream().anyMatch(s -> s.contains("ativo") && s.contains("BOOLEAN")), out.toString());
    }

    @Test
    void emailSemIndiceUnicoSugereUnique() {
        List<NewColumnSpec> cols = List.of(col("id", "INT", true), col("email", "VARCHAR", false));
        List<String> out = NormalizationAdvisor.analyze("clientes", cols, List.of(), List.of());
        assertTrue(out.stream().anyMatch(s -> s.contains("email") && s.contains("UNIQUE")), out.toString());
    }

    @Test
    void emailComIndiceUnicoNaoSugereNada() {
        List<NewColumnSpec> cols = List.of(col("id", "INT", true), col("email", "VARCHAR", false));
        List<IndexInfo> idx = List.of(new IndexInfo("uq_email", true, "BTREE", List.of("email")));
        List<String> out = NormalizationAdvisor.analyze("clientes", cols, List.of(), idx);
        assertFalse(out.stream().anyMatch(s -> s.contains("UNIQUE")), out.toString());
    }

    @Test
    void colunasComSufixoNumericoSugeremTabelaFilha() {
        List<NewColumnSpec> cols = List.of(
                col("id", "INT", true), col("telefone1", "VARCHAR", false), col("telefone2", "VARCHAR", false));
        List<String> out = NormalizationAdvisor.analyze("clientes", cols, List.of(), List.of());
        assertTrue(out.stream().anyMatch(s -> s.contains("telefone1") && s.contains("1a Forma Normal")), out.toString());
    }

    @Test
    void umaUnicaColunaComSufixoNumericoNaoSugereNada() {
        List<NewColumnSpec> cols = List.of(col("id", "INT", true), col("telefone1", "VARCHAR", false));
        List<String> out = NormalizationAdvisor.analyze("clientes", cols, List.of(), List.of());
        assertFalse(out.stream().anyMatch(s -> s.contains("1a Forma Normal")), out.toString());
    }

    @Test
    void dependenciaParcialComPkCompostaESugerida() {
        List<NewColumnSpec> cols = List.of(
                col("pedido_id", "INT", true), col("produto_id", "INT", true),
                col("produto_descricao", "VARCHAR", false));
        List<String> out = NormalizationAdvisor.analyze("pedido_itens", cols, List.of(), List.of());
        assertTrue(out.stream().anyMatch(s -> s.contains("produto_descricao") && s.contains("2a Forma Normal")),
                out.toString());
    }

    @Test
    void colunaDescritivaJuntoDeFkSugereDependenciaTransitiva() {
        List<NewColumnSpec> cols = List.of(
                col("id", "INT", true), col("cliente_id", "INT", false), col("cliente_nome", "VARCHAR", false));
        List<String> out = NormalizationAdvisor.analyze("pedidos", cols, List.of(), List.of());
        assertTrue(out.stream().anyMatch(s -> s.contains("cliente_nome") && s.contains("3a Forma Normal")),
                out.toString());
    }

    @Test
    void chavePrimariaPropriaEmIdNaoVirouPrefixoDeFkFalsoPositivo() {
        // "pedido_id" e a PK da PROPRIA tabela "pedidos" (PK simples, uma so
        // coluna) — "pedido_data" nao deveria ser apontada como dependencia
        // transitiva de uma FK que nao existe.
        List<NewColumnSpec> cols = List.of(col("pedido_id", "INT", true), col("pedido_data", "DATETIME", false));
        List<String> out = NormalizationAdvisor.analyze("pedidos", cols, List.of(), List.of());
        assertFalse(out.stream().anyMatch(s -> s.contains("3a Forma Normal")), out.toString());
    }

    @Test
    void nomeDeTabelaComMaiusculaOuEspacoSugereSnakeCase() {
        List<NewColumnSpec> cols = List.of(col("id", "INT", true));
        List<String> out = NormalizationAdvisor.analyze("Clientes Ativos", cols, List.of(), List.of());
        assertTrue(out.stream().anyMatch(s -> s.contains("snake_case")), out.toString());
    }

    @Test
    void foreignKeySemIndiceCorrespondenteGeraLembrete() {
        List<NewColumnSpec> cols = List.of(col("id", "INT", true), col("cliente_id", "INT", false));
        List<ForeignKeyInfo> fks = List.of(new ForeignKeyInfo(null, List.of("cliente_id"), "clientes",
                List.of("id"), "CASCADE", "RESTRICT"));
        List<String> out = NormalizationAdvisor.analyze("pedidos", cols, fks, List.of());
        assertTrue(out.stream().anyMatch(s -> s.contains("cliente_id") && s.contains("indice")), out.toString());
    }
}
