package com.nureal.ide.modulos.exportacaorelacional.dominio;

import com.nureal.ide.modulos.metadados.dominio.entidades.SchemaForeignKey;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrdenarTabelasHandlerTest {

    private final OrdenarTabelasHandler handler = new OrdenarTabelasHandler();

    @Test
    void paiVemAntesDoFilho() {
        List<SchemaForeignKey> grafo = List.of(
                new SchemaForeignKey("fk", "pedido", List.of("cliente_id"), "cliente", List.of("id"), "", ""));
        Set<String> tabelas = new LinkedHashSet<>(List.of("pedido", "cliente"));

        List<String> ordem = handler.ordenar(tabelas, grafo);

        assertTrue(ordem.indexOf("cliente") < ordem.indexOf("pedido"));
    }

    @Test
    void cadeiaDeTresNiveisFicaNaOrdemCorreta() {
        List<SchemaForeignKey> grafo = List.of(
                new SchemaForeignKey("fk1", "item_pedido", List.of("pedido_id"), "pedido", List.of("id"), "", ""),
                new SchemaForeignKey("fk2", "pedido", List.of("cliente_id"), "cliente", List.of("id"), "", ""));
        Set<String> tabelas = new LinkedHashSet<>(List.of("item_pedido", "pedido", "cliente"));

        List<String> ordem = handler.ordenar(tabelas, grafo);

        assertTrue(ordem.indexOf("cliente") < ordem.indexOf("pedido"));
        assertTrue(ordem.indexOf("pedido") < ordem.indexOf("item_pedido"));
    }

    @Test
    void cicloDeFkNaoTravaEDevolveTodasAsTabelas() {
        // a -> b -> a (ciclo) — nao deve entrar em loop infinito nem lancar excecao.
        List<SchemaForeignKey> grafo = List.of(
                new SchemaForeignKey("fk1", "a", List.of("b_id"), "b", List.of("id"), "", ""),
                new SchemaForeignKey("fk2", "b", List.of("a_id"), "a", List.of("id"), "", ""));
        Set<String> tabelas = new LinkedHashSet<>(List.of("a", "b"));

        List<String> ordem = handler.ordenar(tabelas, grafo);

        assertEquals(2, ordem.size());
        assertTrue(ordem.containsAll(List.of("a", "b")));
    }

    @Test
    void semRelacionamentoMantemTodasAsTabelas() {
        Set<String> tabelas = new LinkedHashSet<>(List.of("a", "b", "c"));

        List<String> ordem = handler.ordenar(tabelas, List.of());

        assertEquals(3, ordem.size());
    }
}
