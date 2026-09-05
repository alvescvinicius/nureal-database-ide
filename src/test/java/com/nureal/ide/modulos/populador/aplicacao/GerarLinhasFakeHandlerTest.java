package com.nureal.ide.modulos.populador.aplicacao;

import com.nureal.ide.modulos.metadados.dominio.entidades.ColumnDetail;
import com.nureal.ide.modulos.metadados.dominio.entidades.TableDetails;
import com.nureal.ide.modulos.populador.dominio.GeneratorKind;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GerarLinhasFakeHandlerTest {

    private final GerarLinhasFakeHandler handler = new GerarLinhasFakeHandler();

    @Test
    void colunaAutoIncrementNuncaEntraNaLinha() {
        ColumnDetail id = new ColumnDetail(1, "id", "int", false, "PRI", null, "auto_increment", null);
        ColumnDetail nome = new ColumnDetail(2, "nome", "varchar(50)", false, "", null, "", null);
        TableDetails tabela = new TableDetails(List.of(id, nome), List.of(), List.of());

        List<Map<String, Object>> linhas = handler.gerar(tabela, Map.of(), Map.of(), 5, new Random(1));

        assertEquals(5, linhas.size());
        for (Map<String, Object> linha : linhas) {
            assertFalse(linha.containsKey("id"));
            assertTrue(linha.containsKey("nome"));
        }
    }

    @Test
    void colunaFkUsaSomenteValoresDaAmostra() {
        ColumnDetail clienteId = new ColumnDetail(1, "cliente_id", "int", false, "MUL", null, "", null);
        TableDetails tabela = new TableDetails(List.of(clienteId), List.of(), List.of());
        Map<String, List<Object>> amostras = Map.of("cliente_id", List.of(10, 20, 30));

        List<Map<String, Object>> linhas = handler.gerar(tabela, Map.of(), amostras, 20, new Random(1));

        for (Map<String, Object> linha : linhas) {
            assertTrue(List.of(10, 20, 30).contains(linha.get("cliente_id")));
        }
    }

    @Test
    void colunaFkComAmostraVaziaFicaDeForaDaLinha() {
        ColumnDetail clienteId = new ColumnDetail(1, "cliente_id", "int", true, "MUL", null, "", null);
        TableDetails tabela = new TableDetails(List.of(clienteId), List.of(), List.of());
        Map<String, List<Object>> amostras = Map.of("cliente_id", List.of());

        List<Map<String, Object>> linhas = handler.gerar(tabela, Map.of(), amostras, 3, new Random(1));

        for (Map<String, Object> linha : linhas) {
            assertFalse(linha.containsKey("cliente_id"));
        }
    }

    @Test
    void geradorEscolhidoPeloUsuarioSobrescreveDetectado() {
        ColumnDetail campo = new ColumnDetail(1, "campo_qualquer", "varchar(20)", false, "", null, "", null);
        TableDetails tabela = new TableDetails(List.of(campo), List.of(), List.of());

        List<Map<String, Object>> linhas = handler.gerar(tabela, Map.of("campo_qualquer", GeneratorKind.CPF),
                Map.of(), 3, new Random(1));

        for (Map<String, Object> linha : linhas) {
            assertTrue(linha.get("campo_qualquer").toString().matches("\\d{3}\\.\\d{3}\\.\\d{3}-\\d{2}"));
        }
    }
}
