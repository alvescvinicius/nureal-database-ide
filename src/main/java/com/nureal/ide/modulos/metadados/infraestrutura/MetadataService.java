package com.nureal.ide.modulos.metadados.infraestrutura;
import com.nureal.ide.modulos.metadados.dominio.contratos.MetadataRepository;

import com.nureal.ide.modulos.dialeto.dominio.contratos.DatabaseDialect;
import com.nureal.ide.modulos.metadados.dominio.entidades.SchemaForeignKey;
import com.nureal.ide.modulos.metadados.dominio.entidades.SchemaInfo;
import com.nureal.ide.modulos.metadados.dominio.entidades.TableDetails;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Adaptador puro: delega toda a leitura de metadados para
 * {@link DatabaseDialect} (ver {@code MetadataCapability}), que e quem de
 * fato sabe consultar o catalogo do banco ativo (MySQL via
 * {@code information_schema}, cada driver futuro do seu jeito). Esta classe
 * so existe para preservar o PORT {@link MetadataRepository} que o resto do
 * app (autocomplete, assistente de DDL, Explorador de Objetos) ja depende.
 */
public class MetadataService implements MetadataRepository {

    private final DatabaseDialect dialect;

    public MetadataService(DatabaseDialect dialect) {
        this.dialect = dialect;
    }

    @Override
    public List<String> listSchemas(Connection conn) throws SQLException {
        return dialect.listSchemas(conn);
    }

    @Override
    public SchemaInfo loadSchema(Connection conn, String schema) throws SQLException {
        return dialect.loadSchema(conn, schema);
    }

    @Override
    public TableDetails loadTableDetails(Connection conn, String schema, String table) throws SQLException {
        return dialect.loadTableDetails(conn, schema, table);
    }

    @Override
    public List<SchemaForeignKey> loadSchemaForeignKeys(Connection conn, String schema) throws SQLException {
        return dialect.loadSchemaForeignKeys(conn, schema);
    }

    @Override
    public Map<String, Set<String>> loadSchemaPrimaryKeys(Connection conn, String schema) throws SQLException {
        return dialect.loadSchemaPrimaryKeys(conn, schema);
    }
}
