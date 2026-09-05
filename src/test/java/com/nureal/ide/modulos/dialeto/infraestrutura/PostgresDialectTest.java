package com.nureal.ide.modulos.dialeto.infraestrutura;

import com.nureal.ide.modulos.conexoes.dominio.entidades.ConnectionProfile;
import com.nureal.ide.modulos.dialeto.dominio.contratos.DatabaseDialect;
import com.nureal.ide.modulos.dialeto.dominio.entidades.ProviderType;
import com.nureal.ide.modulos.metadados.dominio.entidades.ForeignKeyInfo;
import com.nureal.ide.modulos.metadados.dominio.entidades.IndexInfo;
import com.nureal.ide.modulos.metadados.dominio.entidades.NewColumnSpec;
import com.nureal.ide.modulos.metadados.dominio.entidades.NewTableSpec;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Caracteriza {@link PostgresDialect} — segundo driver de verdade da IDE.
 * Foco na sintaxe que DIFERE do MySQL (aspas duplas, SERIAL em vez de
 * AUTO_INCREMENT, MODIFY COLUMN inexistente, indices fora do CREATE TABLE),
 * ja que a estrutura geral ja e coberta pelos testes equivalentes de
 * {@code MySqlDialect}.
 */
class PostgresDialectTest {

    private final PostgresDialect dialect = new PostgresDialect();

    @Test
    void naoImplementaNenhumaCapacidadeOpcional() {
        DatabaseDialect asDialect = dialect;
        assertTrue(asDialect.security().isEmpty());
        assertTrue(asDialect.admin().isEmpty());
        assertTrue(asDialect.replication().isEmpty());
    }

    @Test
    void quoteIdentifierUsaAspasDuplasDobrandoAspasInternas() {
        assertEquals("\"tabela\"", dialect.quoteIdentifier("tabela"));
        assertEquals("\"a\"\"b\"", dialect.quoteIdentifier("a\"b"));
    }

    @Test
    void buildJdbcUrlUsaSchemaComoDatabaseEDefaultPostgresQuandoEmBranco() {
        ConnectionProfile comSchema = new ConnectionProfile("x", "db.local", 5432, "app", "u", "p", false,
                ProviderType.POSTGRESQL);
        assertEquals("jdbc:postgresql://db.local:5432/app", dialect.buildJdbcUrl(comSchema));

        ConnectionProfile semSchema = new ConnectionProfile("x", "db.local", 5432, "  ", "u", "p", false,
                ProviderType.POSTGRESQL);
        assertEquals("jdbc:postgresql://db.local:5432/postgres", dialect.buildJdbcUrl(semSchema));
    }

    @Test
    void createTableStatementUsaSerialParaColunaAutoIncrementEComentariosSeparados() {
        NewColumnSpec id = new NewColumnSpec("id", "INT", null, false, true, true, null, null);
        NewColumnSpec nome = new NewColumnSpec("nome", "VARCHAR", "100", false, false, false, null, "nome do cliente");
        NewTableSpec spec = new NewTableSpec("cliente", List.of(id, nome), "tabela de clientes", List.of(), List.of());

        String sql = dialect.createTableStatement(spec);

        assertTrue(sql.contains("\"id\" SERIAL NOT NULL"), sql);
        assertTrue(sql.contains("\"nome\" VARCHAR(100) NOT NULL"), sql);
        assertTrue(sql.contains("PRIMARY KEY (\"id\")"), sql);
        assertTrue(sql.contains("COMMENT ON TABLE \"cliente\" IS 'tabela de clientes'"), sql);
        assertTrue(sql.contains("COMMENT ON COLUMN \"cliente\".\"nome\" IS 'nome do cliente'"), sql);
    }

    @Test
    void createTableStatementColocaIndicesComoCreateIndexSeparado() {
        NewColumnSpec id = new NewColumnSpec("id", "INT", null, false, true, true, null, null);
        IndexInfo idx = new IndexInfo("idx_nome", false, null, List.of("nome"));
        NewTableSpec spec = new NewTableSpec("cliente", List.of(id), null, List.of(), List.of(idx));

        String sql = dialect.createTableStatement(spec);

        assertTrue(sql.contains("CREATE INDEX \"idx_nome\" ON \"cliente\" (\"nome\")"), sql);
        assertFalse(sql.toUpperCase().contains("INDEX \"IDX_NOME\" (\"NOME\")\n)"));
    }

    @Test
    void alterTableAddStatementsSeparaIndicesDoAlterTable() {
        IndexInfo idx = new IndexInfo("idx_x", true, null, List.of("x"));
        List<String> statements = dialect.alterTableAddStatements("t", List.of(), List.of(), List.of(idx));

        assertEquals(1, statements.size());
        assertEquals("CREATE UNIQUE INDEX \"idx_x\" ON \"t\" (\"x\")", statements.get(0));
    }

    @Test
    void alterTableModifyStatementsMontaAlterColumnEmVezDeModifyColumn() {
        NewColumnSpec mod = new NewColumnSpec("idade", "INT", null, true, false, false, "18", null);
        List<String> statements = dialect.alterTableModifyStatements("pessoa", List.of(mod), List.of(), List.of(),
                List.of());

        assertEquals(1, statements.size());
        String sql = statements.get(0);
        assertTrue(sql.contains("ALTER COLUMN \"idade\" TYPE INT"), sql);
        assertTrue(sql.contains("ALTER COLUMN \"idade\" DROP NOT NULL"), sql);
        assertTrue(sql.contains("ALTER COLUMN \"idade\" SET DEFAULT 18"), sql);
        assertFalse(sql.toUpperCase().contains("MODIFY COLUMN"));
    }

    @Test
    void alterTableModifyStatementsUsaDropConstraintParaForeignKey() {
        List<String> statements = dialect.alterTableModifyStatements("pessoa", List.of(), List.of(), List.of("fk_x"),
                List.of());

        assertEquals(1, statements.size());
        assertTrue(statements.get(0).contains("DROP CONSTRAINT \"fk_x\""));
    }

    @Test
    void foreignKeyDefinitionUsaConstraintForeignKeyReferences() {
        ForeignKeyInfo fk = new ForeignKeyInfo("fk_pedido_cliente", List.of("cliente_id"), "cliente", List.of("id"),
                "CASCADE", "RESTRICT");
        List<String> statements = dialect.alterTableAddStatements("pedido", List.of(), List.of(fk), List.of());

        assertEquals(1, statements.size());
        String sql = statements.get(0);
        assertTrue(sql.contains("ADD CONSTRAINT \"fk_pedido_cliente\" FOREIGN KEY (\"cliente_id\")"), sql);
        assertTrue(sql.contains("REFERENCES \"cliente\" (\"id\")"), sql);
        assertTrue(sql.contains("ON UPDATE CASCADE"), sql);
        assertTrue(sql.contains("ON DELETE RESTRICT"), sql);
    }

    @Test
    void randomSampleQueryUsaRandomEmVezDeRand() {
        assertEquals("SELECT \"col\" FROM \"tab\" ORDER BY random() LIMIT 5",
                dialect.randomSampleQuery("tab", "col", 5));
    }

    @Test
    void createSchemaEDropSchemaUsamCascade() {
        assertEquals("CREATE SCHEMA \"vendas\"", dialect.createSchemaStatement("vendas"));
        assertEquals("DROP SCHEMA \"vendas\" CASCADE", dialect.dropSchemaStatement("vendas"));
    }

    @Test
    void createTriggerStatementGeraFunctionETrigger() {
        String sql = dialect.createTriggerStatement("trg_x", "before", "insert", "pedido", "-- corpo");

        assertTrue(sql.contains("CREATE OR REPLACE FUNCTION \"trg_x_fn\"() RETURNS TRIGGER"), sql);
        assertTrue(sql.contains("CREATE TRIGGER \"trg_x\" BEFORE INSERT ON \"pedido\""), sql);
        assertTrue(sql.contains("EXECUTE FUNCTION \"trg_x_fn\"()"), sql);
    }
}
