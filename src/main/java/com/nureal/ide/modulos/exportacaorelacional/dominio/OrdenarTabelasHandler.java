package com.nureal.ide.modulos.exportacaorelacional.dominio;

import com.nureal.ide.modulos.metadados.dominio.entidades.SchemaForeignKey;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Ordena as tabelas envolvidas na exportacao (pai antes de filho) por DFS
 * pos-ordem — algoritmo classico de ordenacao topologica. Um ciclo de FK
 * (raro — auto-referencia ou referencia circular entre 2+ tabelas) nao trava
 * nem lanca excecao: a tabela que fecharia o ciclo so nao e revisitada (ver
 * {@code emProgresso}), a ordem final fica "melhor esforco" pra esse trecho.
 * Rede de seguranca adicional pra esse caso raro: o script final embrulha
 * tudo em {@code SET FOREIGN_KEY_CHECKS=0/1} (ver {@code InsertScriptBuilder}),
 * entao mesmo uma ordem imperfeita nao falha ao rodar.
 */
public final class OrdenarTabelasHandler {

    public List<String> ordenar(Set<String> tabelas, List<SchemaForeignKey> grafo) {
        Map<String, Set<String>> dependeDe = new HashMap<>();
        for (String t : tabelas) {
            dependeDe.put(t, new HashSet<>());
        }
        for (SchemaForeignKey fk : grafo) {
            if (tabelas.contains(fk.fromTable()) && tabelas.contains(fk.toTable())
                    && !fk.fromTable().equalsIgnoreCase(fk.toTable())) {
                dependeDe.get(fk.fromTable()).add(fk.toTable());
            }
        }
        List<String> ordenado = new ArrayList<>();
        Set<String> visitado = new HashSet<>();
        Set<String> emProgresso = new HashSet<>();
        for (String t : tabelas) {
            visitar(t, dependeDe, visitado, emProgresso, ordenado);
        }
        return ordenado;
    }

    private void visitar(String t, Map<String, Set<String>> dependeDe, Set<String> visitado, Set<String> emProgresso,
            List<String> ordenado) {
        if (visitado.contains(t) || !emProgresso.add(t)) {
            return; // ja processado, ou ciclo detectado (nao reprocessa)
        }
        for (String dep : dependeDe.getOrDefault(t, Set.of())) {
            visitar(dep, dependeDe, visitado, emProgresso, ordenado);
        }
        emProgresso.remove(t);
        visitado.add(t);
        ordenado.add(t);
    }
}
