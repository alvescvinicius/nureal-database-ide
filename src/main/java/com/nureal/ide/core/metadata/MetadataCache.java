package com.nureal.ide.core.metadata;

import com.nureal.ide.core.metadata.model.SchemaInfo;

/**
 * Guarda em memoria a estrutura carregada do banco, para o autocomplete
 * nunca precisar consultar o banco enquanto o usuario digita.
 *
 * Evolucao prevista: indice Trie por prefixo e invalidacao automatica ao rodar DDL.
 */
public class MetadataCache {

    private volatile SchemaInfo schema;

    public void set(SchemaInfo schema) {
        this.schema = schema;
    }

    public SchemaInfo get() {
        return schema;
    }

    public boolean isLoaded() {
        return schema != null;
    }
}
