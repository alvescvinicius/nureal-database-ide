package com.nureal.ide.modulos.exportacaorelacional.dominio;

import com.nureal.ide.modulos.metadados.dominio.entidades.SchemaForeignKey;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FecharTabelasHandlerTest {

    private final FecharTabelasHandler handler = new FecharTabelasHandler();

    private static final List<SchemaForeignKey> GRAFO = List.of(
            new SchemaForeignKey("fk_pedido_cliente", "pedido", List.of("cliente_id"), "cliente", List.of("id"),
                    "", ""),
            new SchemaForeignKey("fk_item_pedido", "item_pedido", List.of("pedido_id"), "pedido", List.of("id"),
                    "", ""));

    @Test
    void semFilhosTrazSoOsPaisAteOTopo() {
        Set<String> tabelas = handler.fechar(GRAFO, "pedido", false);

        assertEquals(Set.of("pedido", "cliente"), tabelas);
        assertFalse(tabelas.contains("item_pedido"));
    }

    @Test
    void comFilhosTrazTambemQuemReferenciaATabela() {
        Set<String> tabelas = handler.fechar(GRAFO, "pedido", true);

        assertEquals(Set.of("pedido", "cliente", "item_pedido"), tabelas);
    }

    @Test
    void tabelaSemRelacionamentoDevolveSoElaMesma() {
        Set<String> tabelas = handler.fechar(GRAFO, "cliente", false);

        assertEquals(Set.of("cliente"), tabelas);
    }

    @Test
    void clienteComFilhosTrazACadeiaInteiraParaBaixo() {
        Set<String> tabelas = handler.fechar(GRAFO, "cliente", true);

        assertTrue(tabelas.containsAll(List.of("cliente", "pedido", "item_pedido")));
        assertEquals(3, tabelas.size());
    }
}
