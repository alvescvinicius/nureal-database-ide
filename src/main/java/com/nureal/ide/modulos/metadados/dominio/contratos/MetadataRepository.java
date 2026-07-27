package com.nureal.ide.modulos.metadados.dominio.contratos;
import com.nureal.ide.modulos.metadados.infraestrutura.MetadataService;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.nureal.ide.modulos.metadados.dominio.entidades.SchemaForeignKey;
import com.nureal.ide.modulos.metadados.dominio.entidades.SchemaInfo;
import com.nureal.ide.modulos.metadados.dominio.entidades.TableDetails;

/**
 * Leitura da estrutura do banco (schemas/tabelas/colunas/FKs/indices) —
 * extraido de {@link MetadataService} (ver
 * .specs/04-modulo-dialeto-e-metadados.md, regra 2) para que outros modulos
 * (autocomplete, assistente de DDL, ia-chat) dependam deste contrato, nao da
 * implementacao concreta ligada a JDBC.
 */
public interface MetadataRepository {

    /** Lista os esquemas (databases) acessiveis ao usuario conectado. */
    List<String> listSchemas(Connection conn) throws SQLException;

    List<SchemaForeignKey> loadSchemaForeignKeys(Connection conn, String schema) throws SQLException;

    Map<String, Set<String>> loadSchemaPrimaryKeys(Connection conn, String schema) throws SQLException;

    SchemaInfo loadSchema(Connection conn, String schema) throws SQLException;

    TableDetails loadTableDetails(Connection conn, String schema, String table) throws SQLException;
}
