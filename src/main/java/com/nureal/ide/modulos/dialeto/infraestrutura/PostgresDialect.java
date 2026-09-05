package com.nureal.ide.modulos.dialeto.infraestrutura;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.nureal.ide.modulos.dialeto.dominio.contratos.AdminCapability;
import com.nureal.ide.modulos.dialeto.dominio.contratos.DatabaseDialect;
import com.nureal.ide.modulos.dialeto.dominio.contratos.ReplicationCapability;
import com.nureal.ide.modulos.dialeto.dominio.contratos.SecurityCapability;
import com.nureal.ide.modulos.conexoes.dominio.entidades.ConnectionProfile;
import com.nureal.ide.modulos.metadados.dominio.entidades.ColumnDetail;
import com.nureal.ide.modulos.metadados.dominio.entidades.ColumnInfo;
import com.nureal.ide.modulos.metadados.dominio.entidades.ForeignKeyInfo;
import com.nureal.ide.modulos.metadados.dominio.entidades.IndexInfo;
import com.nureal.ide.modulos.metadados.dominio.entidades.NewColumnSpec;
import com.nureal.ide.modulos.metadados.dominio.entidades.NewTableSpec;
import com.nureal.ide.modulos.metadados.dominio.entidades.SchemaForeignKey;
import com.nureal.ide.modulos.metadados.dominio.entidades.SchemaInfo;
import com.nureal.ide.modulos.metadados.dominio.entidades.TableDetails;
import com.nureal.ide.modulos.metadados.dominio.entidades.TableInfo;

/**
 * Implementacao para PostgreSQL — segundo driver de verdade da IDE (depois
 * de {@link MySqlDialect}), usado para validar que as 4 capacidades
 * OBRIGATORIAS de {@link DatabaseDialect} realmente bastam pra um banco
 * diferente funcionar (conectar, navegar objetos, gerar DDL), sem precisar
 * tocar em nenhum outro modulo da IDE.
 * <p>
 * So implementa as 4 capacidades OBRIGATORIAS — {@link #security()},
 * {@link #admin()} e {@link #replication()} devolvem {@code Optional.empty()}:
 * administracao de usuarios/roles, monitoramento de sessoes e replicacao no
 * Postgres tem modelo e sintaxe proprios, bem diferentes do MySQL (ex.:
 * {@code pg_stat_activity} em vez de {@code PROCESSLIST}, replicacao via
 * streaming/slots em vez de eventos agendados) — uma tela dedicada pra cada
 * um fica para uma proxima fase; por ora a IDE mostra uma mensagem de status
 * (ver {@code ObjectExplorerController#openUserManagement}/{@code
 * #openProcessList} etc) em vez de abrir um dialogo que nao funcionaria.
 * <p>
 * Diferenca de modelo de dados importante em relacao ao MySQL: no MySQL
 * "schema" e "database" sao sinonimos; no Postgres um "schema" e um
 * NAMESPACE dentro de um "database" (a conexao JDBC sempre aponta pra UM
 * database especifico). Pra manter a mesma experiencia de navegacao da IDE
 * (escolher um "esquema" na arvore), {@link ConnectionProfile#schema()} e
 * tratado aqui como o DATABASE ao qual conectar (default {@code "postgres"},
 * a base de manutencao que sempre existe, se deixado em branco) — e
 * {@link #listSchemas}/{@link #createSchemaStatement}/{@link #dropSchemaStatement}
 * navegam os SCHEMAS (namespaces) DENTRO desse database, nao uma lista de
 * databases do servidor.
 * <p>
 * Limitacoes conhecidas (documentadas aqui em vez de escondidas):
 * <ul>
 * <li>{@link #definitionQuery} para {@code TABLE} monta uma aproximacao do
 * CREATE TABLE a partir de {@code information_schema} (colunas/tipos/nulo/
 * default) — nao inclui indices, chaves estrangeiras nem particionamento,
 * ao contrario do {@code SHOW CREATE TABLE} do MySQL, que e exato. Para
 * VIEW/FUNCTION/PROCEDURE usa {@code pg_get_viewdef}/{@code
 * pg_get_functiondef}, exatos.</li>
 * <li>{@link #dropTriggerStatement} nao recebe o nome da tabela (mesma
 * assinatura usada pelo MySQL, onde o nome do trigger e globalmente unico
 * no schema) — resolve a tabela em tempo de execucao via
 * {@code information_schema.triggers} dentro de um bloco {@code DO}.</li>
 * <li>{@link #dropProcedureStatement}/{@link #dropFunctionStatement} nao
 * recebem os tipos dos parametros (necessarios no Postgres pra desambiguar
 * sobrecarga) — funcionam apenas quando o nome nao esta sobrecarregado,
 * mesma simplificacao que o assistente de DDL ja assume hoje pro MySQL.</li>
 * </ul>
 */
public class PostgresDialect implements DatabaseDialect {

    @Override
    public Optional<SecurityCapability> security() {
        return Optional.empty();
    }

    @Override
    public Optional<AdminCapability> admin() {
        return Optional.empty();
    }

    @Override
    public Optional<ReplicationCapability> replication() {
        return Optional.empty();
    }

    @Override
    public String id() {
        return "postgresql";
    }

    @Override
    public String driverClassName() {
        return "org.postgresql.Driver";
    }

    /** Ver javadoc da classe: {@code profile.schema()} e o DATABASE de conexao, nao um schema Postgres. */
    @Override
    public String buildJdbcUrl(ConnectionProfile profile) {
        String database = (profile.schema() == null || profile.schema().isBlank()) ? "postgres" : profile.schema();
        return "jdbc:postgresql://" + profile.host() + ":" + profile.port() + "/" + database;
    }

    @Override
    public List<String> sessionInitStatements() {
        return List.of();
    }

    private static final String SCHEMAS_SQL = "SELECT schema_name FROM information_schema.schemata "
            + "WHERE schema_name NOT IN ('pg_catalog', 'information_schema') "
            + "AND schema_name NOT LIKE 'pg_toast%' AND schema_name NOT LIKE 'pg_temp\\_%' "
            + "ORDER BY schema_name";

    // CASE formata o tipo com tamanho/precisao embutido (ex.: "varchar(255)",
    // "numeric(10,2)"), igual ao COLUMN_TYPE que o MySQL ja devolve pronto —
    // o information_schema do Postgres separa isso em colunas distintas
    // (character_maximum_length, numeric_precision/scale).
    private static final String TYPE_CASE_SQL = "CASE "
            + "WHEN data_type = 'character varying' AND character_maximum_length IS NOT NULL "
            + "  THEN 'varchar(' || character_maximum_length || ')' "
            + "WHEN data_type = 'character' AND character_maximum_length IS NOT NULL "
            + "  THEN 'char(' || character_maximum_length || ')' "
            + "WHEN data_type = 'numeric' AND numeric_precision IS NOT NULL "
            + "  THEN 'numeric(' || numeric_precision || ',' || COALESCE(numeric_scale, 0) || ')' "
            + "ELSE data_type END";

    private static final String COLUMNS_SQL = "SELECT table_name, column_name, " + TYPE_CASE_SQL + " AS column_type, "
            + "ordinal_position "
            + "FROM information_schema.columns "
            + "WHERE table_schema = ? "
            + "ORDER BY table_name, ordinal_position";

    private static final String TABLES_SQL = "SELECT table_name, table_type "
            + "FROM information_schema.tables "
            + "WHERE table_schema = ? "
            + "ORDER BY table_name";

    private static final String ROUTINES_SQL = "SELECT routine_name, routine_type "
            + "FROM information_schema.routines "
            + "WHERE routine_schema = ? "
            + "ORDER BY routine_name";

    // DISTINCT: o information_schema do Postgres tem uma linha por EVENTO do
    // trigger (INSERT/UPDATE/DELETE) — sem isso um trigger com "FOR INSERT OR
    // UPDATE" apareceria duplicado.
    private static final String TRIGGERS_SQL = "SELECT DISTINCT trigger_name "
            + "FROM information_schema.triggers "
            + "WHERE trigger_schema = ? "
            + "ORDER BY trigger_name";

    /** Lista os SCHEMAS (namespaces) do database conectado — ver javadoc da classe. */
    @Override
    public List<String> listSchemas(Connection conn) throws SQLException {
        List<String> schemas = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(SCHEMAS_SQL);
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                schemas.add(rs.getString(1));
            }
        }
        return schemas;
    }

    /** Eventos agendados sao um conceito exclusivo do MySQL — Postgres sempre devolve a lista vazia aqui. */
    @Override
    public SchemaInfo loadSchema(Connection conn, String schema) throws SQLException {
        Map<String, List<ColumnInfo>> columnsByObject = new LinkedHashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(COLUMNS_SQL)) {
            ps.setString(1, schema);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String object = rs.getString("table_name");
                    ColumnInfo col = new ColumnInfo(
                            rs.getString("column_name"),
                            rs.getString("column_type"),
                            rs.getInt("ordinal_position"));
                    columnsByObject.computeIfAbsent(object, k -> new ArrayList<>()).add(col);
                }
            }
        }

        List<TableInfo> tables = new ArrayList<>();
        List<TableInfo> views = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(TABLES_SQL)) {
            ps.setString(1, schema);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString("table_name");
                    String type = rs.getString("table_type");
                    List<ColumnInfo> cols = columnsByObject.getOrDefault(name, List.of());
                    TableInfo info = new TableInfo(name, cols);
                    if (type != null && type.toUpperCase(Locale.ROOT).contains("VIEW")) {
                        views.add(info);
                    } else {
                        tables.add(info);
                    }
                }
            }
        }

        List<String> procedures = new ArrayList<>();
        List<String> functions = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(ROUTINES_SQL)) {
            ps.setString(1, schema);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString("routine_name");
                    String type = rs.getString("routine_type");
                    if (type != null && type.toUpperCase(Locale.ROOT).contains("FUNCTION")) {
                        functions.add(name);
                    } else {
                        procedures.add(name);
                    }
                }
            }
        }

        List<String> triggers = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(TRIGGERS_SQL)) {
            ps.setString(1, schema);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    triggers.add(rs.getString("trigger_name"));
                }
            }
        }

        return new SchemaInfo(schema, tables, views, procedures, functions, triggers, List.of());
    }

    private static final String COLUMN_DETAILS_SQL = "SELECT ordinal_position, column_name, " + TYPE_CASE_SQL
            + " AS column_type, is_nullable, column_default, "
            + "col_description(format('%I.%I', table_schema, table_name)::regclass::oid, ordinal_position) AS column_comment "
            + "FROM information_schema.columns "
            + "WHERE table_schema = ? AND table_name = ? "
            + "ORDER BY ordinal_position";

    private static final String PK_COLUMNS_FOR_TABLE_SQL = "SELECT kcu.column_name "
            + "FROM information_schema.table_constraints tc "
            + "JOIN information_schema.key_column_usage kcu "
            + "  ON kcu.constraint_name = tc.constraint_name AND kcu.constraint_schema = tc.constraint_schema "
            + "WHERE tc.constraint_type = 'PRIMARY KEY' AND tc.table_schema = ? AND tc.table_name = ?";

    // pg_index/pg_class/pg_am: o information_schema NAO padroniza metadados
    // de indice (nao existe INDEX_NAME/STATISTICS portavel) — todo banco usa
    // seu proprio catalogo aqui, este e o do Postgres.
    private static final String INDEXES_SQL = "SELECT ix.relname AS index_name, i.indisunique AS is_unique, "
            + "am.amname AS index_type, a.attname AS column_name, "
            + "array_position(i.indkey, a.attnum) AS seq "
            + "FROM pg_index i "
            + "JOIN pg_class t ON t.oid = i.indrelid "
            + "JOIN pg_class ix ON ix.oid = i.indexrelid "
            + "JOIN pg_am am ON am.oid = ix.relam "
            + "JOIN pg_namespace n ON n.oid = t.relnamespace "
            + "JOIN pg_attribute a ON a.attrelid = t.oid AND a.attnum = ANY(i.indkey) "
            + "WHERE n.nspname = ? AND t.relname = ? "
            + "ORDER BY ix.relname, seq";

    // pg_constraint direto (nao information_schema): o padrao ANSI de
    // constraint_column_usage NAO preserva a ORDEM da coluna referenciada
    // pareada com a coluna local numa FK COMPOSTA (mais de uma coluna) —
    // so key_column_usage tem ordinal_position/position_in_unique_constraint,
    // e position_in_unique_constraint nao existe em constraint_column_usage
    // (confirmado rodando contra um Postgres de verdade). unnest(...) WITH
    // ORDINALITY nos dois arrays de oid de coluna do pg_constraint
    // (conkey = colunas locais, confkey = colunas referenciadas) e a forma
    // catalogada de parear coluna-a-coluna na ordem certa.
    private static final String UPDATE_DELETE_RULE_CASE = "CASE %s "
            + "WHEN 'a' THEN 'NO ACTION' WHEN 'r' THEN 'RESTRICT' WHEN 'c' THEN 'CASCADE' "
            + "WHEN 'n' THEN 'SET NULL' WHEN 'd' THEN 'SET DEFAULT' END";

    private static final String FOREIGN_KEYS_SQL = "SELECT con.conname AS constraint_name, "
            + "att2.attname AS column_name, cl.relname AS referenced_table, att.attname AS referenced_column, "
            + String.format(UPDATE_DELETE_RULE_CASE, "con.confupdtype") + " AS update_rule, "
            + String.format(UPDATE_DELETE_RULE_CASE, "con.confdeltype") + " AS delete_rule "
            + "FROM pg_constraint con "
            + "JOIN pg_class rel ON rel.oid = con.conrelid "
            + "JOIN pg_namespace ns ON ns.oid = rel.relnamespace "
            + "JOIN pg_class cl ON cl.oid = con.confrelid "
            + "JOIN unnest(con.conkey) WITH ORDINALITY AS u(attnum, ord) ON true "
            + "JOIN pg_attribute att2 ON att2.attrelid = con.conrelid AND att2.attnum = u.attnum "
            + "JOIN unnest(con.confkey) WITH ORDINALITY AS v(attnum, ord) ON v.ord = u.ord "
            + "JOIN pg_attribute att ON att.attrelid = con.confrelid AND att.attnum = v.attnum "
            + "WHERE con.contype = 'f' AND ns.nspname = ? AND rel.relname = ? "
            + "ORDER BY con.conname, u.ord";

    private static final String FOREIGN_KEYS_FOR_SCHEMA_SQL = "SELECT con.conname AS constraint_name, "
            + "rel.relname AS table_name, att2.attname AS column_name, "
            + "cl.relname AS referenced_table, att.attname AS referenced_column, "
            + String.format(UPDATE_DELETE_RULE_CASE, "con.confupdtype") + " AS update_rule, "
            + String.format(UPDATE_DELETE_RULE_CASE, "con.confdeltype") + " AS delete_rule "
            + "FROM pg_constraint con "
            + "JOIN pg_class rel ON rel.oid = con.conrelid "
            + "JOIN pg_namespace ns ON ns.oid = rel.relnamespace "
            + "JOIN pg_class cl ON cl.oid = con.confrelid "
            + "JOIN unnest(con.conkey) WITH ORDINALITY AS u(attnum, ord) ON true "
            + "JOIN pg_attribute att2 ON att2.attrelid = con.conrelid AND att2.attnum = u.attnum "
            + "JOIN unnest(con.confkey) WITH ORDINALITY AS v(attnum, ord) ON v.ord = u.ord "
            + "JOIN pg_attribute att ON att.attrelid = con.confrelid AND att.attnum = v.attnum "
            + "WHERE con.contype = 'f' AND ns.nspname = ? "
            + "ORDER BY rel.relname, con.conname, u.ord";

    private static final String PRIMARY_KEYS_FOR_SCHEMA_SQL = "SELECT tc.table_name, kcu.column_name "
            + "FROM information_schema.table_constraints tc "
            + "JOIN information_schema.key_column_usage kcu "
            + "  ON kcu.constraint_name = tc.constraint_name AND kcu.constraint_schema = tc.constraint_schema "
            + "WHERE tc.constraint_type = 'PRIMARY KEY' AND tc.table_schema = ? "
            + "ORDER BY tc.table_name, kcu.ordinal_position";

    @Override
    public TableDetails loadTableDetails(Connection conn, String schema, String table) throws SQLException {
        return new TableDetails(
                loadColumnDetails(conn, schema, table),
                loadIndexes(conn, schema, table),
                loadForeignKeys(conn, schema, table));
    }

    private List<ColumnDetail> loadColumnDetails(Connection conn, String schema, String table) throws SQLException {
        Set<String> pkCols = new HashSet<>();
        try (PreparedStatement ps = conn.prepareStatement(PK_COLUMNS_FOR_TABLE_SQL)) {
            ps.setString(1, schema);
            ps.setString(2, table);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    pkCols.add(rs.getString(1));
                }
            }
        }
        List<ColumnDetail> columns = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(COLUMN_DETAILS_SQL)) {
            ps.setString(1, schema);
            ps.setString(2, table);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString("column_name");
                    String defaultValue = rs.getString("column_default");
                    // Coluna SERIAL/GENERATED ... AS IDENTITY sempre tem um
                    // DEFAULT chamando nextval() de uma sequence — mesmo
                    // papel do EXTRA="auto_increment" do MySQL (ver
                    // GerarLinhasFakeHandler/TablePopulatorDialog/
                    // DdlAssistantDialog, que checam essa substring).
                    boolean autoIncrement = defaultValue != null && defaultValue.startsWith("nextval(");
                    columns.add(new ColumnDetail(
                            rs.getInt("ordinal_position"),
                            name,
                            rs.getString("column_type"),
                            "YES".equalsIgnoreCase(rs.getString("is_nullable")),
                            pkCols.contains(name) ? "PRI" : "",
                            defaultValue,
                            autoIncrement ? "auto_increment" : "",
                            rs.getString("column_comment")));
                }
            }
        }
        return columns;
    }

    private List<IndexInfo> loadIndexes(Connection conn, String schema, String table) throws SQLException {
        Map<String, List<String>> cols = new LinkedHashMap<>();
        Map<String, Boolean> unique = new HashMap<>();
        Map<String, String> type = new HashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(INDEXES_SQL)) {
            ps.setString(1, schema);
            ps.setString(2, table);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString("index_name");
                    cols.computeIfAbsent(name, k -> new ArrayList<>())
                            .add(rs.getString("column_name"));
                    unique.put(name, rs.getBoolean("is_unique"));
                    type.put(name, rs.getString("index_type"));
                }
            }
        }
        List<IndexInfo> indexes = new ArrayList<>();
        cols.forEach((name, columns) -> indexes.add(
                new IndexInfo(name, unique.get(name), type.get(name), columns)));
        return indexes;
    }

    private List<ForeignKeyInfo> loadForeignKeys(Connection conn, String schema, String table) throws SQLException {
        Map<String, List<String>> cols = new LinkedHashMap<>();
        Map<String, List<String>> refCols = new LinkedHashMap<>();
        Map<String, String> refTable = new HashMap<>();
        Map<String, String> onUpdate = new HashMap<>();
        Map<String, String> onDelete = new HashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(FOREIGN_KEYS_SQL)) {
            ps.setString(1, schema);
            ps.setString(2, table);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString("constraint_name");
                    cols.computeIfAbsent(name, k -> new ArrayList<>())
                            .add(rs.getString("column_name"));
                    refCols.computeIfAbsent(name, k -> new ArrayList<>())
                            .add(rs.getString("referenced_column"));
                    refTable.put(name, rs.getString("referenced_table"));
                    onUpdate.put(name, rs.getString("update_rule"));
                    onDelete.put(name, rs.getString("delete_rule"));
                }
            }
        }
        List<ForeignKeyInfo> fks = new ArrayList<>();
        cols.forEach((name, columns) -> fks.add(new ForeignKeyInfo(
                name, columns, refTable.get(name), refCols.get(name),
                onUpdate.get(name), onDelete.get(name))));
        return fks;
    }

    @Override
    public List<SchemaForeignKey> loadSchemaForeignKeys(Connection conn, String schema) throws SQLException {
        Map<String, List<String>> cols = new LinkedHashMap<>();
        Map<String, List<String>> refCols = new LinkedHashMap<>();
        Map<String, String> fromTable = new HashMap<>();
        Map<String, String> refTable = new HashMap<>();
        Map<String, String> onUpdate = new HashMap<>();
        Map<String, String> onDelete = new HashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(FOREIGN_KEYS_FOR_SCHEMA_SQL)) {
            ps.setString(1, schema);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString("constraint_name");
                    cols.computeIfAbsent(name, k -> new ArrayList<>())
                            .add(rs.getString("column_name"));
                    refCols.computeIfAbsent(name, k -> new ArrayList<>())
                            .add(rs.getString("referenced_column"));
                    fromTable.put(name, rs.getString("table_name"));
                    refTable.put(name, rs.getString("referenced_table"));
                    onUpdate.put(name, rs.getString("update_rule"));
                    onDelete.put(name, rs.getString("delete_rule"));
                }
            }
        }
        List<SchemaForeignKey> fks = new ArrayList<>();
        cols.forEach((name, columns) -> fks.add(new SchemaForeignKey(
                name, fromTable.get(name), columns, refTable.get(name), refCols.get(name),
                onUpdate.get(name), onDelete.get(name))));
        return fks;
    }

    @Override
    public Map<String, Set<String>> loadSchemaPrimaryKeys(Connection conn, String schema) throws SQLException {
        Map<String, Set<String>> byTable = new HashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(PRIMARY_KEYS_FOR_SCHEMA_SQL)) {
            ps.setString(1, schema);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    byTable.computeIfAbsent(rs.getString("table_name"), k -> new LinkedHashSet<>())
                            .add(rs.getString("column_name"));
                }
            }
        }
        return byTable;
    }

    /** Ver javadoc da classe (limitacoes de {@code definitionQuery} por tipo de objeto). */
    @Override
    public String definitionQuery(String objectKind, String objectName) {
        String quotedRegclass = quoteLiteral(quoteIdentifier(objectName));
        return switch (objectKind.toUpperCase(Locale.ROOT)) {
            case "VIEW" -> "SELECT 'CREATE OR REPLACE VIEW " + quoteIdentifier(objectName) + " AS' || chr(10) || "
                    + "pg_get_viewdef(" + quotedRegclass + "::regclass, true) AS definition";
            case "PROCEDURE", "FUNCTION" -> "SELECT pg_get_functiondef(" + quoteLiteral(objectName)
                    + "::regproc) AS definition";
            case "TRIGGER" -> "SELECT pg_get_triggerdef(oid) AS definition FROM pg_trigger "
                    + "WHERE tgname = " + quoteLiteral(objectName) + " AND NOT tgisinternal LIMIT 1";
            default -> "SELECT 'CREATE TABLE " + quoteIdentifier(objectName) + " (' || chr(10) || "
                    + "string_agg('  ' || quote_ident(column_name) || ' ' || " + TYPE_CASE_SQL
                    + " || CASE WHEN is_nullable = 'NO' THEN ' NOT NULL' ELSE '' END "
                    + "|| CASE WHEN column_default IS NOT NULL THEN ' DEFAULT ' || column_default ELSE '' END, "
                    + "','  || chr(10) ORDER BY ordinal_position) || chr(10) || ');' AS definition "
                    + "FROM information_schema.columns "
                    + "WHERE table_name = " + quoteLiteral(objectName);
        };
    }

    @Override
    public String quoteIdentifier(String ident) {
        return "\"" + ident.replace("\"", "\"\"") + "\"";
    }

    @Override
    public String randomSampleQuery(String table, String column, int limit) {
        return "SELECT " + quoteIdentifier(column) + " FROM " + quoteIdentifier(table)
                + " ORDER BY random() LIMIT " + limit;
    }

    @Override
    public String createSchemaStatement(String name) {
        return "CREATE SCHEMA " + quoteIdentifier(name);
    }

    @Override
    public String dropSchemaStatement(String name) {
        return "DROP SCHEMA " + quoteIdentifier(name) + " CASCADE";
    }

    /**
     * Monta o CREATE TABLE mais, quando houver, {@code COMMENT ON TABLE}/
     * {@code COMMENT ON COLUMN} separados (Postgres nao aceita comentario
     * inline em CREATE TABLE) — tudo numa UNICA string com {@code ;}
     * separando cada comando: o driver JDBC do Postgres (protocolo "simple
     * query", ao contrario do MySQL) executa varios comandos separados por
     * ponto e virgula numa unica chamada a {@code Statement#executeUpdate},
     * entao isto continua compativel com {@code ConstruirDdlDeTabelaHandler},
     * que trata o retorno como UM elemento de {@code List<String>}. Indices
     * extras tambem viram {@code CREATE INDEX} separados aqui (Postgres nao
     * tem clausula de indice dentro do CREATE TABLE como o MySQL).
     */
    @Override
    public String createTableStatement(NewTableSpec spec) {
        StringBuilder sql = new StringBuilder();
        sql.append("CREATE TABLE ").append(quoteIdentifier(spec.name())).append(" (\n");

        List<String> pkColumns = new ArrayList<>();
        List<NewColumnSpec> cols = spec.columns();
        List<ForeignKeyInfo> fks = spec.foreignKeys() == null ? List.of() : spec.foreignKeys();
        int extraClauses = (pkColumnNames(cols).isEmpty() ? 0 : 1) + fks.size();

        for (int i = 0; i < cols.size(); i++) {
            NewColumnSpec c = cols.get(i);
            sql.append("  ").append(columnDefinition(c));
            if (i < cols.size() - 1 || extraClauses > 0) {
                sql.append(",\n");
            } else {
                sql.append("\n");
            }
            if (c.primaryKey()) {
                pkColumns.add(quoteIdentifier(c.name()));
            }
        }
        int remaining = extraClauses;
        if (!pkColumns.isEmpty()) {
            sql.append("  PRIMARY KEY (").append(String.join(", ", pkColumns)).append(")");
            remaining--;
            sql.append(remaining > 0 ? ",\n" : "\n");
        }
        for (ForeignKeyInfo fk : fks) {
            sql.append("  ").append(foreignKeyDefinition(spec.name(), fk));
            remaining--;
            sql.append(remaining > 0 ? ",\n" : "\n");
        }
        sql.append(")");

        List<IndexInfo> idxs = spec.indexes() == null ? List.of() : spec.indexes();
        for (IndexInfo idx : idxs) {
            sql.append(";\n").append(indexStatement(spec.name(), idx));
        }
        if (spec.comment() != null && !spec.comment().isBlank()) {
            sql.append(";\nCOMMENT ON TABLE ").append(quoteIdentifier(spec.name()))
                    .append(" IS ").append(quoteLiteral(spec.comment()));
        }
        for (NewColumnSpec c : cols) {
            if (c.comment() != null && !c.comment().isBlank()) {
                sql.append(";\nCOMMENT ON COLUMN ").append(quoteIdentifier(spec.name())).append(".")
                        .append(quoteIdentifier(c.name())).append(" IS ").append(quoteLiteral(c.comment()));
            }
        }
        return sql.toString();
    }

    /**
     * Aditivo, igual ao MySQL: ADD COLUMN e ADD CONSTRAINT (FK) entram no
     * MESMO ALTER TABLE (Postgres aceita multiplas sub-clausulas separadas
     * por virgula, como o MySQL); indices novos viram {@code CREATE INDEX}
     * SEPARADOS (Postgres nao tem {@code ADD INDEX}).
     */
    @Override
    public List<String> alterTableAddStatements(String tableName, List<NewColumnSpec> newColumns,
            List<ForeignKeyInfo> newForeignKeys, List<IndexInfo> newIndexes) {
        List<NewColumnSpec> cols = newColumns == null ? List.of() : newColumns;
        List<ForeignKeyInfo> fks = newForeignKeys == null ? List.of() : newForeignKeys;
        List<IndexInfo> idxs = newIndexes == null ? List.of() : newIndexes;
        if (cols.isEmpty() && fks.isEmpty() && idxs.isEmpty()) {
            return List.of();
        }
        List<String> statements = new ArrayList<>();
        List<String> parts = new ArrayList<>();
        for (NewColumnSpec c : cols) {
            parts.add("ADD COLUMN " + columnDefinition(c));
        }
        for (ForeignKeyInfo fk : fks) {
            parts.add("ADD " + foreignKeyDefinition(tableName, fk));
        }
        if (!parts.isEmpty()) {
            statements.add("ALTER TABLE " + quoteIdentifier(tableName) + "\n  " + String.join(",\n  ", parts));
        }
        for (IndexInfo idx : idxs) {
            statements.add(indexStatement(tableName, idx));
        }
        return statements;
    }

    /**
     * Diferente do MySQL: nao existe {@code MODIFY COLUMN} no Postgres — uma
     * mudanca de coluna vira ATE 3 sub-clausulas {@code ALTER COLUMN}
     * (tipo, nulo, default), todas dentro do MESMO {@code ALTER TABLE} que
     * as remocoes (Postgres aceita sub-clausulas de tipos diferentes
     * misturadas na mesma instrucao). Comentario de coluna sempre vira um
     * {@code COMMENT ON COLUMN} SEPARADO (nunca cabe dentro de ALTER TABLE).
     */
    @Override
    public List<String> alterTableModifyStatements(String tableName, List<NewColumnSpec> modifiedColumns,
            List<String> droppedColumns, List<String> droppedForeignKeys, List<String> droppedIndexes) {
        List<NewColumnSpec> mods = modifiedColumns == null ? List.of() : modifiedColumns;
        List<String> dropCols = droppedColumns == null ? List.of() : droppedColumns;
        List<String> dropFks = droppedForeignKeys == null ? List.of() : droppedForeignKeys;
        List<String> dropIdxs = droppedIndexes == null ? List.of() : droppedIndexes;
        if (mods.isEmpty() && dropCols.isEmpty() && dropFks.isEmpty() && dropIdxs.isEmpty()) {
            return List.of();
        }
        List<String> statements = new ArrayList<>();
        List<String> parts = new ArrayList<>();
        for (String fk : dropFks) {
            parts.add("DROP CONSTRAINT " + quoteIdentifier(fk));
        }
        for (String col : dropCols) {
            parts.add("DROP COLUMN " + quoteIdentifier(col));
        }
        for (NewColumnSpec c : mods) {
            String type = c.sqlType().toUpperCase(Locale.ROOT)
                    + (c.length() != null && !c.length().isBlank() ? "(" + c.length().trim() + ")" : "");
            parts.add("ALTER COLUMN " + quoteIdentifier(c.name()) + " TYPE " + type);
            parts.add("ALTER COLUMN " + quoteIdentifier(c.name())
                    + (c.nullable() ? " DROP NOT NULL" : " SET NOT NULL"));
            if (c.defaultValue() != null && !c.defaultValue().isBlank()) {
                parts.add("ALTER COLUMN " + quoteIdentifier(c.name()) + " SET DEFAULT "
                        + defaultLiteral(c.defaultValue().trim()));
            } else {
                parts.add("ALTER COLUMN " + quoteIdentifier(c.name()) + " DROP DEFAULT");
            }
        }
        if (!parts.isEmpty()) {
            statements.add("ALTER TABLE " + quoteIdentifier(tableName) + "\n  " + String.join(",\n  ", parts));
        }
        // DROP INDEX fica FORA do ALTER TABLE de proposito: indices no
        // Postgres nao sao "da tabela" sintaticamente (sao objetos do
        // schema), diferente do MySQL onde DROP INDEX so faz sentido dentro
        // de um ALTER TABLE.
        for (String idx : dropIdxs) {
            statements.add("DROP INDEX " + quoteIdentifier(idx));
        }
        for (NewColumnSpec c : mods) {
            if (c.comment() != null && !c.comment().isBlank()) {
                statements.add("COMMENT ON COLUMN " + quoteIdentifier(tableName) + "." + quoteIdentifier(c.name())
                        + " IS " + quoteLiteral(c.comment()));
            }
        }
        return statements;
    }

    @Override
    public String createViewStatement(String name, String selectSql) {
        return "CREATE VIEW " + quoteIdentifier(name) + " AS\n" + selectSql;
    }

    @Override
    public String replaceViewStatement(String name, String selectSql) {
        return "CREATE OR REPLACE VIEW " + quoteIdentifier(name) + " AS\n" + selectSql;
    }

    @Override
    public String dropViewStatement(String name) {
        return "DROP VIEW " + quoteIdentifier(name);
    }

    /**
     * Postgres nao aceita corpo procedural direto num CREATE TRIGGER (ao
     * contrario do MySQL): o trigger sempre chama uma FUNCTION dedicada.
     * Gera as DUAS instrucoes ({@code CREATE FUNCTION ... RETURNS TRIGGER}
     * seguido de {@code CREATE TRIGGER}) como uma unica string — mesma
     * tecnica multi-comando de {@link #createTableStatement}.
     */
    @Override
    public String createTriggerStatement(String triggerName, String timing, String event, String tableName,
            String body) {
        String fnName = quoteIdentifier(triggerName + "_fn");
        return "CREATE OR REPLACE FUNCTION " + fnName + "() RETURNS TRIGGER AS $$\nBEGIN\n" + body
                + "\nRETURN NEW;\nEND;\n$$ LANGUAGE plpgsql;\n"
                + "CREATE TRIGGER " + quoteIdentifier(triggerName) + " " + timing.toUpperCase(Locale.ROOT) + " "
                + event.toUpperCase(Locale.ROOT) + " ON " + quoteIdentifier(tableName)
                + "\nFOR EACH ROW EXECUTE FUNCTION " + fnName + "()";
    }

    /**
     * Ver javadoc da classe: a assinatura nao recebe a tabela do trigger,
     * entao resolve em tempo de execucao via {@code information_schema.triggers}
     * dentro de um bloco {@code DO}.
     */
    @Override
    public String dropTriggerStatement(String triggerName) {
        String literal = quoteLiteral(triggerName);
        return "DO $$\nDECLARE tbl text;\nBEGIN\n"
                + "  SELECT event_object_table INTO tbl FROM information_schema.triggers "
                + "WHERE trigger_name = " + literal + " LIMIT 1;\n"
                + "  IF tbl IS NOT NULL THEN\n"
                + "    EXECUTE format('DROP TRIGGER %I ON %I', " + literal + ", tbl);\n"
                + "  END IF;\n"
                + "END $$";
    }

    @Override
    public String createProcedureStatement(String name, List<String> parameters, String body) {
        String params = parameters == null ? "" : String.join(", ", parameters);
        return "CREATE PROCEDURE " + quoteIdentifier(name) + " (" + params + ")\nLANGUAGE plpgsql AS $$\nBEGIN\n"
                + body + "\nEND;\n$$";
    }

    /** {@code deterministic} vira {@code IMMUTABLE} (Postgres nao tem DETERMINISTIC/NOT DETERMINISTIC do MySQL; sem o flag, o default ja e VOLATILE). */
    @Override
    public String createFunctionStatement(String name, List<String> parameters, String returnType,
            boolean deterministic, String body) {
        String params = parameters == null ? "" : String.join(", ", parameters);
        return "CREATE FUNCTION " + quoteIdentifier(name) + " (" + params + ")\nRETURNS "
                + returnType.toUpperCase(Locale.ROOT) + "\nLANGUAGE plpgsql"
                + (deterministic ? " IMMUTABLE" : "") + " AS $$\nBEGIN\n" + body + "\nEND;\n$$";
    }

    @Override
    public String dropProcedureStatement(String name) {
        return "DROP PROCEDURE " + quoteIdentifier(name);
    }

    @Override
    public String dropFunctionStatement(String name) {
        return "DROP FUNCTION " + quoteIdentifier(name);
    }

    private static List<String> pkColumnNames(List<NewColumnSpec> cols) {
        List<String> names = new ArrayList<>();
        for (NewColumnSpec c : cols) {
            if (c.primaryKey()) {
                names.add(c.name());
            }
        }
        return names;
    }

    private String indexStatement(String tableName, IndexInfo idx) {
        List<String> quotedCols = idx.columns().stream().map(this::quoteIdentifier).toList();
        String name = (idx.name() == null || idx.name().isBlank())
                ? autoName(idx.unique() ? "uq" : "idx", tableName, String.join("_", idx.columns()))
                : idx.name();
        String kind = idx.unique() ? "CREATE UNIQUE INDEX" : "CREATE INDEX";
        return kind + " " + quoteIdentifier(name) + " ON " + quoteIdentifier(tableName)
                + " (" + String.join(", ", quotedCols) + ")";
    }

    /** Nome padrao (limitado a 63 chars, limite de identificador do Postgres) quando o usuario deixa em branco. */
    private static String autoName(String... parts) {
        String base = String.join("_", parts);
        return base.length() > 63 ? base.substring(0, 63) : base;
    }

    private String foreignKeyDefinition(String tableName, ForeignKeyInfo fk) {
        List<String> localCols = fk.columns().stream().map(this::quoteIdentifier).toList();
        List<String> refCols = fk.referencedColumns().stream().map(this::quoteIdentifier).toList();
        String name = (fk.name() == null || fk.name().isBlank())
                ? autoName("fk", tableName, String.join("_", fk.columns()))
                : fk.name();
        StringBuilder def = new StringBuilder();
        def.append("CONSTRAINT ").append(quoteIdentifier(name))
                .append(" FOREIGN KEY (").append(String.join(", ", localCols)).append(")")
                .append(" REFERENCES ").append(quoteIdentifier(fk.referencedTable()))
                .append(" (").append(String.join(", ", refCols)).append(")");
        if (fk.onUpdate() != null && !fk.onUpdate().isBlank() && !"NO ACTION".equalsIgnoreCase(fk.onUpdate())) {
            def.append(" ON UPDATE ").append(fk.onUpdate().toUpperCase(Locale.ROOT));
        }
        if (fk.onDelete() != null && !fk.onDelete().isBlank() && !"NO ACTION".equalsIgnoreCase(fk.onDelete())) {
            def.append(" ON DELETE ").append(fk.onDelete().toUpperCase(Locale.ROOT));
        }
        return def.toString();
    }

    /** {@code SERIAL}/{@code BIGSERIAL}/{@code SMALLSERIAL} em vez de {@code AUTO_INCREMENT} (sem tamanho — sao tipos, nao modificadores). */
    private String columnDefinition(NewColumnSpec c) {
        StringBuilder def = new StringBuilder();
        def.append(quoteIdentifier(c.name())).append(" ");
        String upperType = c.sqlType().toUpperCase(Locale.ROOT);
        if (c.autoIncrement()) {
            if (upperType.contains("BIGINT")) {
                def.append("BIGSERIAL");
            } else if (upperType.contains("SMALLINT")) {
                def.append("SMALLSERIAL");
            } else {
                def.append("SERIAL");
            }
        } else {
            def.append(upperType);
            if (c.length() != null && !c.length().isBlank()) {
                def.append("(").append(c.length().trim()).append(")");
            }
        }
        def.append(c.nullable() ? " NULL" : " NOT NULL");
        if (c.defaultValue() != null && !c.defaultValue().isBlank()) {
            def.append(" DEFAULT ").append(defaultLiteral(c.defaultValue().trim()));
        }
        return def.toString();
    }

    private static String defaultLiteral(String value) {
        String upper = value.toUpperCase(Locale.ROOT);
        if (upper.equals("NULL") || upper.equals("TRUE") || upper.equals("FALSE")
                || upper.startsWith("CURRENT_TIMESTAMP") || upper.startsWith("NOW(")
                || value.matches("-?\\d+(\\.\\d+)?")) {
            return value;
        }
        return quoteLiteral(value);
    }

    private static String quoteLiteral(String value) {
        return "'" + value.replace("'", "''") + "'";
    }

    @Override
    public List<String> keywords() {
        return List.of(
                "SELECT", "FROM", "WHERE", "INSERT", "UPDATE", "DELETE", "INTO", "VALUES",
                "SET", "JOIN", "INNER", "LEFT", "RIGHT", "OUTER", "ON", "GROUP", "BY",
                "ORDER", "HAVING", "LIMIT", "OFFSET", "DISTINCT", "AS", "AND", "OR", "NOT",
                "NULL", "IS", "IN", "LIKE", "ILIKE", "BETWEEN", "EXISTS", "CREATE", "ALTER", "DROP",
                "TABLE", "INDEX", "VIEW", "PRIMARY", "KEY", "FOREIGN", "REFERENCES",
                "COUNT", "SUM", "AVG", "MIN", "MAX", "CASE", "WHEN", "THEN", "ELSE", "END",
                "RETURNING", "SERIAL", "BIGSERIAL", "SCHEMA", "SEQUENCE", "CASCADE", "WITH");
    }
}
