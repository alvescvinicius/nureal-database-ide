package com.nureal.ide.modulos.populador.aplicacao;

import com.nureal.ide.modulos.metadados.dominio.entidades.ColumnDetail;
import com.nureal.ide.modulos.metadados.dominio.entidades.TableDetails;
import com.nureal.ide.modulos.populador.dominio.FakeDataGenerator;
import com.nureal.ide.modulos.populador.dominio.GeneratorKind;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * Monta as linhas fake em memoria (sem JDBC, testavel sozinho) — quem
 * executa de fato o INSERT e {@code modulos.populador.infraestrutura.PopuladorExecutor}.
 */
public final class GerarLinhasFakeHandler {

    /**
     * @param tabela             colunas/FKs da tabela alvo
     * @param geradorPorColuna   gerador escolhido (auto-detectado ou trocado
     *                           pelo usuario no dialogo) para cada coluna
     *                           NORMAL (nao FK, nao auto-increment)
     * @param amostrasFkPorColuna valores de PK ja existentes na tabela pai,
     *                           por coluna LOCAL de FK (ver
     *                           {@code ForeignKeySampler}) — coluna ausente
     *                           deste mapa e tratada como NORMAL
     * @param quantidade         numero de linhas a gerar
     * @param rnd                fonte de aleatoriedade (injetada para testes deterministicos)
     * @return uma linha por {@code Map<coluna, valor>}, na ordem de insercao dos campos preenchidos
     */
    public List<Map<String, Object>> gerar(TableDetails tabela, Map<String, GeneratorKind> geradorPorColuna,
            Map<String, List<Object>> amostrasFkPorColuna, int quantidade, Random rnd) {
        List<Map<String, Object>> linhas = new ArrayList<>(quantidade);
        for (int i = 0; i < quantidade; i++) {
            Map<String, Object> linha = new LinkedHashMap<>();
            for (ColumnDetail col : tabela.columns()) {
                if (isAutoIncrement(col)) {
                    continue; // o proprio banco gera
                }
                List<Object> amostra = amostrasFkPorColuna.get(col.name());
                if (amostra != null) {
                    if (!amostra.isEmpty()) {
                        linha.put(col.name(), amostra.get(rnd.nextInt(amostra.size())));
                    }
                    // amostra vazia (tabela pai sem linhas): coluna fica de
                    // fora da linha — se for NOT NULL, quem chama ja deve
                    // ter barrado a operacao ANTES de chegar aqui (ver
                    // validacao previa no dialogo, mesmo padrao do
                    // ConstruirDdlDeTabelaHandler).
                    continue;
                }
                GeneratorKind kind = geradorPorColuna.getOrDefault(col.name(), FakeDataGenerator.detectar(col));
                linha.put(col.name(), FakeDataGenerator.gerar(kind, col, rnd));
            }
            linhas.add(linha);
        }
        return linhas;
    }

    private static boolean isAutoIncrement(ColumnDetail col) {
        return col.extra() != null && col.extra().toLowerCase(Locale.ROOT).contains("auto_increment");
    }
}
