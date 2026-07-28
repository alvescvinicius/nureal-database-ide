package com.nureal.ide.modulos.autocomplete.aplicacao;

import com.nureal.ide.modulos.metadados.dominio.entidades.ColumnInfo;
import com.nureal.ide.modulos.metadados.dominio.entidades.ForeignKeyInfo;
import com.nureal.ide.modulos.metadados.dominio.entidades.SchemaInfo;
import com.nureal.ide.modulos.metadados.dominio.entidades.TableInfo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeradorDeSugestoesTest {

    private static SchemaInfo schemaDeTeste() {
        TableInfo usuarios = new TableInfo("usuarios", List.of(
                new ColumnInfo("id", "int", 1),
                new ColumnInfo("nome", "varchar", 2)));
        TableInfo pedidos = new TableInfo("pedidos", List.of(
                new ColumnInfo("id", "int", 1),
                new ColumnInfo("id_usuario", "int", 2)));
        return new SchemaInfo("db", List.of(usuarios, pedidos));
    }

    @Test
    void contextoGeralSugereTabelasEPalavrasChave() {
        GeradorDeSugestoes gerador = new GeradorDeSugestoes(List.of("SELECT", "FROM"));
        gerador.refreshAll(List.of(schemaDeTeste()), "db");

        // Antes de qualquer clausula (SELECT/FROM/etc.), o contexto e GENERAL.
        List<SugestaoDeCompletion> sugestoes = gerador.gerar("", 0, "");

        assertTrue(sugestoes.stream().anyMatch(s -> s.texto().equals("usuarios")));
        assertTrue(sugestoes.stream().anyMatch(s -> s.texto().equals("pedidos")));
        assertTrue(sugestoes.stream().anyMatch(s -> s.texto().equals("SELECT")));
    }

    @Test
    void contextoDeTabelaSugereTabelasSemPalavrasChave() {
        GeradorDeSugestoes gerador = new GeradorDeSugestoes(List.of("SELECT", "FROM"));
        gerador.refreshAll(List.of(schemaDeTeste()), "db");

        String sql = "SELECT * FROM ";
        List<SugestaoDeCompletion> sugestoes = gerador.gerar(sql, sql.length(), "");

        assertTrue(sugestoes.stream().anyMatch(s -> s.texto().equals("usuarios")));
        assertTrue(sugestoes.stream().anyMatch(s -> s.texto().equals("pedidos")));
        assertFalse(sugestoes.stream().anyMatch(s -> s.texto().equals("SELECT")));
    }

    @Test
    void contextoDeColunaNaoSugereTabelasNemPalavrasChave() {
        GeradorDeSugestoes gerador = new GeradorDeSugestoes(List.of("SELECT", "FROM"));
        gerador.refreshAll(List.of(schemaDeTeste()), "db");

        String sql = "SELECT  FROM usuarios";
        List<SugestaoDeCompletion> sugestoes = gerador.gerar(sql, 7, "");

        assertTrue(sugestoes.stream().anyMatch(s -> s.texto().equals("id")));
        assertTrue(sugestoes.stream().anyMatch(s -> s.texto().equals("nome")));
        assertFalse(sugestoes.stream().anyMatch(s -> s.texto().equals("usuarios")));
        assertFalse(sugestoes.stream().anyMatch(s -> s.texto().equals("SELECT")));
    }

    @Test
    void prefixoDigitadoFiltraEOrdenaPeloMaisProximo() {
        GeradorDeSugestoes gerador = new GeradorDeSugestoes(List.of());
        TableInfo t1 = new TableInfo("tit_titulo_tb", List.of());
        TableInfo t2 = new TableInfo("tit_titulo_venda_tb", List.of());
        gerador.refreshAll(List.of(new SchemaInfo("db", List.of(t1, t2))), "db");

        List<SugestaoDeCompletion> sugestoes = gerador.gerar("tit_ti", 6, "tit_ti");

        assertEquals(2, sugestoes.size());
        assertEquals("tit_titulo_tb", sugestoes.get(0).texto());
        assertEquals("tit_titulo_venda_tb", sugestoes.get(1).texto());
    }

    @Test
    void tabelaRelacionadaPorFkEntraComSnippetDeJoinPronto() {
        GeradorDeSugestoes gerador = new GeradorDeSugestoes(List.of());
        TableInfo pedidos = new TableInfo("pedidos", List.of());
        TableInfo usuarios = new TableInfo("usuarios", List.of());
        gerador.refreshAll(List.of(new SchemaInfo("db", List.of(pedidos, usuarios))), "db");
        gerador.setForeignKeyLookup(tableName -> "pedidos".equalsIgnoreCase(tableName)
                ? List.of(new ForeignKeyInfo("fk_pedido_usuario", List.of("id_usuario"),
                        "usuarios", List.of("id"), "CASCADE", "CASCADE"))
                : List.of());

        String sql = "SELECT * FROM pedidos JOIN ";
        List<SugestaoDeCompletion> sugestoes = gerador.gerar(sql, sql.length(), "");

        SugestaoDeCompletion relacionada = sugestoes.stream()
                .filter(s -> s.texto().equals("usuarios"))
                .findFirst().orElseThrow();
        assertTrue(relacionada.snippet() != null && relacionada.snippet().contains("ON"));
    }

    @Test
    void sugereTabelasDeOutrosEsquemasQualificadasComSchemaPonto() {
        // Pedido explicito do usuario: rodar/completar cruzando esquemas
        // (ex.: "SELECT * FROM db1.t1 JOIN db2.t2") nao deveria exigir
        // selecionar um esquema so — refreshAll recebe TODOS os esquemas
        // conhecidos da conexao, "db1" marcado como o corrente.
        GeradorDeSugestoes gerador = new GeradorDeSugestoes(List.of("SELECT", "FROM"));
        SchemaInfo db1 = new SchemaInfo("db1", List.of(new TableInfo("pedidos", List.of())));
        SchemaInfo db2 = new SchemaInfo("db2", List.of(new TableInfo("produtos", List.of())));
        gerador.refreshAll(List.of(db1, db2), "db1");

        List<SugestaoDeCompletion> sugestoes = gerador.gerar("", 0, "");

        // Tabela do esquema CORRENTE continua sem qualificar (comportamento
        // identico ao de antes desta funcionalidade, o caso comum).
        assertTrue(sugestoes.stream().anyMatch(s -> s.texto().equals("pedidos")));
        // Tabela de OUTRO esquema vem qualificada — o texto inserido no
        // editor precisa ser SQL valido sozinho, nao so "produtos" (que nao
        // existiria no esquema corrente).
        assertTrue(sugestoes.stream().anyMatch(s -> s.texto().equals("db2.produtos")));
    }
}
