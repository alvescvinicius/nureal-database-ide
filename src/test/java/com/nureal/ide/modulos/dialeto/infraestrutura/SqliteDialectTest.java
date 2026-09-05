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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Caracteriza {@link SqliteDialect} — terceiro driver de verdade da IDE.
 * Foco no que e ESTRUTURALMENTE diferente do MySQL/Postgres: arquivo em vez
 * de host/porta, AUTOINCREMENT so em INTEGER PRIMARY KEY solitaria, ausencia
 * de schemas multiplos e de ALTER TABLE completo (validado contra um banco
 * SQLite de verdade separadamente, nao neste teste puro).
 */
class SqliteDialectTest {

    private final SqliteDialect dialect = new SqliteDialect();

    @Test
    void naoImplementaNenhumaCapacidadeOpcional() {
        DatabaseDialect asDialect = dialect;
        assertTrue(asDialect.security().isEmpty());
        assertTrue(asDialect.admin().isEmpty());
        assertTrue(asDialect.replication().isEmpty());
    }

    @Test
    void listSchemasSempreDevolveApenasMain() throws Exception {
        assertEquals(List.of("main"), dialect.listSchemas(null));
    }

    @Test
    void buildJdbcUrlUsaHostComoCaminhoDeArquivo() {
        ConnectionProfile profile = new ConnectionProfile("x", "/tmp/meubanco.db", 0, "", "", "", false,
                ProviderType.SQLITE);
        assertEquals("jdbc:sqlite:/tmp/meubanco.db", dialect.buildJdbcUrl(profile));
    }

    @Test
    void createTableStatementUsaIntegerPrimaryKeyAutoincrement() {
        NewColumnSpec id = new NewColumnSpec("id", "INTEGER", null, false, true, true, null, null);
        NewColumnSpec nome = new NewColumnSpec("nome", "TEXT", null, false, false, false, null, null);
        NewTableSpec spec = new NewTableSpec("cliente", List.of(id, nome), null, List.of(), List.of());

        String sql = dialect.createTableStatement(spec);

        assertTrue(sql.contains("\"id\" INTEGER PRIMARY KEY AUTOINCREMENT"), sql);
        assertTrue(sql.contains("\"nome\" TEXT NOT NULL"), sql);
        assertTrue(!sql.contains("PRIMARY KEY (\"id\")"), sql);
    }

    @Test
    void createTableStatementColocaIndicesComoCreateIndexSeparado() {
        NewColumnSpec id = new NewColumnSpec("id", "INTEGER", null, false, true, true, null, null);
        IndexInfo idx = new IndexInfo("idx_x", false, null, List.of("id"));
        NewTableSpec spec = new NewTableSpec("t", List.of(id), null, List.of(), List.of(idx));

        String sql = dialect.createTableStatement(spec);

        assertTrue(sql.contains("CREATE INDEX \"idx_x\" ON \"t\" (\"id\")"), sql);
    }

    @Test
    void alterTableAddStatementsLancaExcecaoParaForeignKeyNova() {
        ForeignKeyInfo fk = new ForeignKeyInfo("fk_x", List.of("cliente_id"), "cliente", List.of("id"), null, null);

        assertThrows(UnsupportedOperationException.class,
                () -> dialect.alterTableAddStatements("pedido", List.of(), List.of(fk), List.of()));
    }

    @Test
    void alterTableAddStatementsAceitaColunaEIndiceNovos() {
        NewColumnSpec col = new NewColumnSpec("total", "REAL", null, true, false, false, null, null);
        IndexInfo idx = new IndexInfo("idx_y", true, null, List.of("total"));

        List<String> statements = dialect.alterTableAddStatements("pedido", List.of(col), List.of(), List.of(idx));

        assertEquals(2, statements.size());
        assertTrue(statements.get(0).contains("ADD COLUMN \"total\" REAL NULL"), statements.get(0));
        assertEquals("CREATE UNIQUE INDEX \"idx_y\" ON \"pedido\" (\"total\")", statements.get(1));
    }

    @Test
    void alterTableModifyStatementsLancaExcecaoParaModificarColunaExistente() {
        NewColumnSpec mod = new NewColumnSpec("idade", "INTEGER", null, true, false, false, null, null);

        assertThrows(UnsupportedOperationException.class,
                () -> dialect.alterTableModifyStatements("pessoa", List.of(mod), List.of(), List.of(), List.of()));
    }

    @Test
    void alterTableModifyStatementsLancaExcecaoParaDropDeForeignKey() {
        assertThrows(UnsupportedOperationException.class,
                () -> dialect.alterTableModifyStatements("pessoa", List.of(), List.of(), List.of("fk_x"), List.of()));
    }

    @Test
    void alterTableModifyStatementsAceitaDropColunaEDropIndice() {
        List<String> statements = dialect.alterTableModifyStatements("pessoa", List.of(), List.of("apelido"),
                List.of(), List.of("idx_velho"));

        assertEquals(2, statements.size());
        assertEquals("DROP INDEX \"idx_velho\"", statements.get(0));
        assertEquals("ALTER TABLE \"pessoa\" DROP COLUMN \"apelido\"", statements.get(1));
    }

    @Test
    void createSchemaEDropSchemaLancamExcecaoClara() {
        assertThrows(UnsupportedOperationException.class, () -> dialect.createSchemaStatement("x"));
        assertThrows(UnsupportedOperationException.class, () -> dialect.dropSchemaStatement("x"));
    }

    @Test
    void procedureEFunctionLancamExcecaoClara() {
        assertThrows(UnsupportedOperationException.class,
                () -> dialect.createProcedureStatement("p", List.of(), "body"));
        assertThrows(UnsupportedOperationException.class,
                () -> dialect.createFunctionStatement("f", List.of(), "INT", false, "body"));
        assertThrows(UnsupportedOperationException.class, () -> dialect.dropProcedureStatement("p"));
        assertThrows(UnsupportedOperationException.class, () -> dialect.dropFunctionStatement("f"));
    }

    @Test
    void replaceViewStatementFazDropSeguidoDeCreate() {
        String sql = dialect.replaceViewStatement("v", "SELECT 1");

        assertTrue(sql.contains("DROP VIEW IF EXISTS \"v\""), sql);
        assertTrue(sql.contains("CREATE VIEW \"v\" AS"), sql);
    }

    @Test
    void createTriggerStatementUsaCorpoInlineEntreBeginEnd() {
        String sql = dialect.createTriggerStatement("trg_x", "before", "insert", "t", "SELECT 1;");

        assertTrue(sql.contains("CREATE TRIGGER \"trg_x\" BEFORE INSERT ON \"t\""), sql);
        assertTrue(sql.contains("BEGIN\nSELECT 1;\nEND"), sql);
    }

    @Test
    void dropTriggerStatementNaoPrecisaDeTabela() {
        assertEquals("DROP TRIGGER \"trg_x\"", dialect.dropTriggerStatement("trg_x"));
    }

    @Test
    void definitionQueryUsaSqliteMaster() {
        assertEquals("SELECT sql FROM sqlite_master WHERE type = 'table' AND name = 'cliente'",
                dialect.definitionQuery("TABLE", "cliente"));
    }

    @Test
    void randomSampleQueryUsaRandomMaiusculo() {
        assertEquals("SELECT \"col\" FROM \"tab\" ORDER BY RANDOM() LIMIT 5",
                dialect.randomSampleQuery("tab", "col", 5));
    }
}
