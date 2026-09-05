package com.nureal.ide.modulos.exportacaorelacional.dominio;

import com.nureal.ide.modulos.metadados.dominio.entidades.SchemaForeignKey;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Grafo fake: {@code item_pedido.pedido_id -> pedido.id} e
 * {@code pedido.cliente_id -> cliente.id}. Semente = 1 linha de "pedido".
 */
class FecharDependenciasHandlerTest {

    private final FecharDependenciasHandler handler = new FecharDependenciasHandler();

    private static final List<SchemaForeignKey> GRAFO = List.of(
            new SchemaForeignKey("fk_pedido_cliente", "pedido", List.of("cliente_id"), "cliente", List.of("id"),
                    "", ""),
            new SchemaForeignKey("fk_item_pedido", "item_pedido", List.of("pedido_id"), "pedido", List.of("id"),
                    "", ""));

    /** {@link RowFetcher} fake em memoria: filtra por igualdade de coluna, sem JDBC nenhum. */
    private static final class FakeRowFetcher implements RowFetcher {
        private final Map<String, List<Map<String, Object>>> dados;
        final List<String> chamadas = new ArrayList<>();

        FakeRowFetcher(Map<String, List<Map<String, Object>>> dados) {
            this.dados = dados;
        }

        @Override
        public List<Map<String, Object>> fetch(String tabela, String coluna, List<Object> valores) {
            chamadas.add(tabela + "." + coluna + "=" + valores);
            List<Map<String, Object>> out = new ArrayList<>();
            for (Map<String, Object> linha : dados.getOrDefault(tabela, List.of())) {
                if (valores.contains(linha.get(coluna))) {
                    out.add(linha);
                }
            }
            return out;
        }
    }

    private static Map<String, List<Map<String, Object>>> baseDeDados() {
        return Map.of(
                "cliente", List.of(Map.of("id", 1, "nome", "Ana")),
                "pedido", List.of(Map.of("id", 100, "cliente_id", 1, "total", 50)),
                "item_pedido", List.of(
                        Map.of("id", 1000, "pedido_id", 100, "produto", "X"),
                        Map.of("id", 1001, "pedido_id", 999, "produto", "Y") // outro pedido, nao deve aparecer
                ));
    }

    @Test
    void semFilhosTrazSoAsLinhasPaiNecessarias() throws Exception {
        FakeRowFetcher fetcher = new FakeRowFetcher(baseDeDados());
        List<Map<String, Object>> semente = List.of(Map.of("id", 100, "cliente_id", 1, "total", 50));

        FecharDependenciasHandler.Resultado resultado = handler.fechar(GRAFO, "pedido", semente, false, fetcher);

        assertEquals(1, resultado.linhasPorTabela().get("pedido").size());
        assertEquals(1, resultado.linhasPorTabela().get("cliente").size());
        assertFalse(resultado.linhasPorTabela().containsKey("item_pedido"));
        assertFalse(resultado.limiteAtingido());
    }

    @Test
    void comFilhosTrazTambemQuemReferenciaASemente() throws Exception {
        FakeRowFetcher fetcher = new FakeRowFetcher(baseDeDados());
        List<Map<String, Object>> semente = List.of(Map.of("id", 100, "cliente_id", 1, "total", 50));

        FecharDependenciasHandler.Resultado resultado = handler.fechar(GRAFO, "pedido", semente, true, fetcher);

        assertEquals(1, resultado.linhasPorTabela().get("cliente").size());
        List<Map<String, Object>> itens = resultado.linhasPorTabela().get("item_pedido");
        assertEquals(1, itens.size()); // so o item do pedido 100, nao o do pedido 999
        assertEquals(1000, itens.get(0).get("id"));
    }

    @Test
    void naoRebuscaValorJaVisitado() throws Exception {
        FakeRowFetcher fetcher = new FakeRowFetcher(baseDeDados());
        List<Map<String, Object>> semente = List.of(
                Map.of("id", 100, "cliente_id", 1, "total", 50),
                Map.of("id", 101, "cliente_id", 1, "total", 20)); // mesmo cliente_id=1 duas vezes

        handler.fechar(GRAFO, "pedido", semente, false, fetcher);

        long buscasPorCliente = fetcher.chamadas.stream().filter(c -> c.startsWith("cliente.id")).count();
        assertTrue(buscasPorCliente <= 1, "cliente_id=1 repetido na semente nao deveria gerar 2 buscas na tabela pai");
    }
}
