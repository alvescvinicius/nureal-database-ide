package com.nureal.ide.modulos.exportacaorelacional.dominio;

import com.nureal.ide.modulos.metadados.dominio.entidades.SchemaForeignKey;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A partir das linhas SELECIONADAS (semente) de uma tabela, caminha o grafo
 * de FK do schema (ja carregado inteiro de uma vez — ver
 * {@code MetadataService#loadSchemaForeignKeys}) buscando:
 * <ul>
 * <li>PAIS (sempre): tabelas que a semente REFERENCIA (suas proprias FKs) —
 * necessario pra um INSERT nao falhar por integridade referencial.</li>
 * <li>FILHOS (opcional, {@code incluirFilhos}): tabelas que REFERENCIAM a
 * semente — pedido explicito do usuario ("as duas opcoes, usuario escolhe
 * na hora").</li>
 * </ul>
 * Puro (sem JDBC de verdade — {@link RowFetcher} e injetado), testavel com
 * um grafo/fetcher fake em memoria.
 * <p>
 * Simplificacao assumida conscientemente: para FK COMPOSTA (2+ colunas),
 * usa so a PRIMEIRA coluna do par local/referenciado pra caminhar o grafo
 * (mesma simplificacao ja aceita em {@code populador.ForeignKeySampler} para
 * amostragem) — o INSERT final ainda sai com TODAS as colunas da linha
 * buscada, so a navegacao do grafo em si nao garante 100% de precisao numa
 * FK composta (caso raro).
 */
public final class FecharDependenciasHandler {

    /** Teto de linhas total coletadas — protege contra uma cascata de "filhos" gigante numa tabela muito referenciada. */
    private static final int LIMITE_LINHAS = 20_000;

    public record Resultado(Map<String, List<Map<String, Object>>> linhasPorTabela, boolean limiteAtingido) {
    }

    private record Fronteira(String tabela, List<Map<String, Object>> linhas) {
    }

    public Resultado fechar(List<SchemaForeignKey> grafo, String tabelaSemente,
            List<Map<String, Object>> linhasSemente, boolean incluirFilhos, RowFetcher fetcher) throws Exception {
        Map<String, List<Map<String, Object>>> linhasPorTabela = new LinkedHashMap<>();
        linhasPorTabela.put(tabelaSemente, new ArrayList<>(linhasSemente));
        Set<String> visitado = new HashSet<>();
        Deque<Fronteira> fila = new ArrayDeque<>();
        fila.add(new Fronteira(tabelaSemente, linhasSemente));

        int total = linhasSemente.size();
        boolean limiteAtingido = false;

        while (!fila.isEmpty() && !limiteAtingido) {
            Fronteira f = fila.poll();

            for (SchemaForeignKey fk : grafo) {
                if (!fk.fromTable().equalsIgnoreCase(f.tabela())) {
                    continue;
                }
                List<Object> valores = valoresDistintos(f.linhas(), primeiraColuna(fk.fromColumns()));
                List<Map<String, Object>> novas = buscarNovas(fetcher, fk.toTable(), primeiraColuna(fk.toColumns()),
                        valores, visitado, linhasPorTabela);
                if (!novas.isEmpty()) {
                    total += novas.size();
                    if (total > LIMITE_LINHAS) {
                        limiteAtingido = true;
                        break;
                    }
                    fila.add(new Fronteira(fk.toTable(), novas));
                }
            }
            if (limiteAtingido) {
                break;
            }

            if (incluirFilhos) {
                for (SchemaForeignKey fk : grafo) {
                    if (!fk.toTable().equalsIgnoreCase(f.tabela())) {
                        continue;
                    }
                    List<Object> valores = valoresDistintos(f.linhas(), primeiraColuna(fk.toColumns()));
                    List<Map<String, Object>> novas = buscarNovas(fetcher, fk.fromTable(),
                            primeiraColuna(fk.fromColumns()), valores, visitado, linhasPorTabela);
                    if (!novas.isEmpty()) {
                        total += novas.size();
                        if (total > LIMITE_LINHAS) {
                            limiteAtingido = true;
                            break;
                        }
                        fila.add(new Fronteira(fk.fromTable(), novas));
                    }
                }
            }
        }
        return new Resultado(linhasPorTabela, limiteAtingido);
    }

    private static String primeiraColuna(List<String> colunas) {
        return colunas.get(0);
    }

    private static List<Object> valoresDistintos(List<Map<String, Object>> linhas, String coluna) {
        Set<Object> vistos = new HashSet<>();
        List<Object> valores = new ArrayList<>();
        for (Map<String, Object> linha : linhas) {
            Object v = linha.get(coluna);
            if (v != null && vistos.add(v)) {
                valores.add(v);
            }
        }
        return valores;
    }

    /**
     * Busca em {@code fetcher} so os valores AINDA NAO visitados
     * (deduplicacao via {@code visitado.add}, que devolve {@code false} pra
     * quem ja tinha sido processado) — evita reprocessar/duplicar linha e
     * evita loop infinito num ciclo de FK.
     */
    private static List<Map<String, Object>> buscarNovas(RowFetcher fetcher, String tabela, String coluna,
            List<Object> candidatos, Set<String> visitado, Map<String, List<Map<String, Object>>> linhasPorTabela)
            throws Exception {
        List<Object> paraBuscar = new ArrayList<>();
        for (Object v : candidatos) {
            if (visitado.add(tabela.toLowerCase() + ":" + coluna.toLowerCase() + ":" + v)) {
                paraBuscar.add(v);
            }
        }
        if (paraBuscar.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> encontradas = fetcher.fetch(tabela, coluna, paraBuscar);
        if (!encontradas.isEmpty()) {
            linhasPorTabela.computeIfAbsent(tabela, k -> new ArrayList<>()).addAll(encontradas);
        }
        return encontradas;
    }
}
