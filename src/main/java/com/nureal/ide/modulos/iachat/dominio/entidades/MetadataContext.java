package com.nureal.ide.modulos.iachat.dominio.entidades;
import com.nureal.ide.modulos.iachat.infraestrutura.tool.DescribeTableTool;

import java.util.List;

/**
 * Resumo do schema conectado — so contagem e nomes, nunca colunas/indices/
 * FKs (isso fica sob demanda via tools, ver {@code DescribeTableTool}; injetar
 * tudo sempre no prompt seria caro e na maioria das vezes desnecessario).
 * Derivado do {@code SchemaInfo} ja cacheado — sem round-trip novo ao banco.
 */
public record MetadataContext(int tableCount, int viewCount, List<String> tableNames) {

    public static final MetadataContext EMPTY = new MetadataContext(0, 0, List.of());

    public MetadataContext {
        tableNames = tableNames == null ? List.of() : List.copyOf(tableNames);
    }
}
