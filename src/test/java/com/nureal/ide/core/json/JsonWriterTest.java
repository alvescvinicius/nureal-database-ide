package com.nureal.ide.core.json;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Testes do escritor de JSON — logica pura (entrada -&gt; saida), mesmo
 * espirito de {@code SqlRiskAnalyzerTest}. Cobre principalmente o
 * round-trip com {@link JsonParser}, ja que o escritor existe para montar
 * corpos de requisicao que o proprio {@link JsonParser} nunca precisa
 * reler de volta.
 */
class JsonWriterTest {

    @Test
    void escapaCaracteresEspeciaisEmStrings() {
        assertEquals("\"a\\\"b\\\\c\\nd\"", JsonWriter.write("a\"b\\c\nd"));
    }

    @Test
    void numerosInteirosNaoGanhamCasasDecimais() {
        assertEquals("42", JsonWriter.write(42));
        assertEquals("3", JsonWriter.write(3.0));
        assertEquals("0.2", JsonWriter.write(0.2));
    }

    @Test
    void booleanosENulo() {
        assertEquals("true", JsonWriter.write(Boolean.TRUE));
        assertEquals("false", JsonWriter.write(Boolean.FALSE));
        assertEquals("null", JsonWriter.write(null));
    }

    @Test
    void objetoPreservaOrdemDeInsercao() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("b", 1);
        map.put("a", 2);
        assertEquals("{\"b\":1,\"a\":2}", JsonWriter.write(map));
    }

    @Test
    void arrayDeObjetos() {
        List<Map<String, Object>> list = List.of(Map.of("x", 1), Map.of("y", 2));
        String json = JsonWriter.write(list);
        assertEquals(List.of(Map.of("x", 1.0), Map.of("y", 2.0)), JsonParser.parse(json));
    }

    @Test
    void roundTripComEstruturaAninhadaViaJsonParser() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", "llama3.1");
        body.put("stream", true);
        body.put("messages", List.of(Map.of("role", "user", "content", "oi")));

        String json = JsonWriter.write(body);
        Object reparsed = JsonParser.parse(json);

        @SuppressWarnings("unchecked")
        Map<String, Object> root = (Map<String, Object>) reparsed;
        assertEquals("llama3.1", root.get("model"));
        assertEquals(Boolean.TRUE, root.get("stream"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> messages = (List<Map<String, Object>>) root.get("messages");
        assertEquals("user", messages.get(0).get("role"));
        assertEquals("oi", messages.get(0).get("content"));
    }
}
