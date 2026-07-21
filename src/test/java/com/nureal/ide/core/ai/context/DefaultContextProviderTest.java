package com.nureal.ide.core.ai.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.nureal.ide.core.connection.ConnectionManager;
import com.nureal.ide.core.history.ExecutionHistoryStore;
import com.nureal.ide.core.metadata.MetadataService;
import com.nureal.ide.core.metadata.model.SchemaInfo;
import com.nureal.ide.core.metadata.model.TableInfo;

class DefaultContextProviderTest {

    @Test
    void collectMontaConnectionContextComRotuloETipo() {
        FakeAccessor accessor = new FakeAccessor();
        accessor.connectionLabel = "localhost:3306/prod (root)";
        accessor.databaseProductName = "MySQL";
        accessor.databaseVersion = "8.4.0";
        accessor.activeSchemaName = "prod";

        AgentContext context = new DefaultContextProvider(accessor).collect();

        assertEquals("localhost:3306/prod (root)", context.connection().label());
        assertEquals("MySQL", context.connection().databaseProductName());
        assertEquals("8.4.0", context.connection().databaseVersion());
        assertEquals("prod", context.connection().schema());
    }

    @Test
    void collectMontaMetadataContextDoSchemaCacheado() {
        FakeAccessor accessor = new FakeAccessor();
        accessor.cachedSchema = new SchemaInfo("prod",
                List.of(new TableInfo("orders", List.of()), new TableInfo("customers", List.of())),
                List.of(new TableInfo("orders_view", List.of())),
                List.of(), List.of(), List.of());

        AgentContext context = new DefaultContextProvider(accessor).collect();

        assertEquals(2, context.metadata().tableCount());
        assertEquals(1, context.metadata().viewCount());
        assertEquals(List.of("orders", "customers"), context.metadata().tableNames());
    }

    @Test
    void collectSemSchemaCacheadoDevolveMetadataVazio() {
        AgentContext context = new DefaultContextProvider(new FakeAccessor()).collect();
        assertEquals(MetadataContext.EMPTY, context.metadata());
    }

    @Test
    void collectTruncaSqlMuitoLongoDoEditor() {
        FakeAccessor accessor = new FakeAccessor();
        accessor.currentEditorSql = "a".repeat(5000);
        accessor.hasEditorSelection = true;

        AgentContext context = new DefaultContextProvider(accessor).collect();

        assertTrue(context.editor().sql().length() < 5000);
        assertTrue(context.editor().sql().endsWith("(truncado)"));
        assertTrue(context.editor().hasSelection());
    }

    @Test
    void collectMontaExecutionContextDaUltimaExecucaoComErro() {
        FakeAccessor accessor = new FakeAccessor();
        accessor.lastExecution = Optional.of(new ExecutionHistoryStore.Entry(
                "id1", "SELECT 1", "conn", "prod", 123L, 42L, false, "erro de sintaxe"));

        AgentContext context = new DefaultContextProvider(accessor).collect();

        assertEquals("SELECT 1", context.execution().lastSql());
        assertFalse(context.execution().lastSuccess());
        assertEquals("erro de sintaxe", context.execution().lastErrorMessage());
        assertEquals(42L, context.execution().lastDurationMs());
    }

    @Test
    void collectExecucaoComSucessoNaoTrazMensagemDeErro() {
        FakeAccessor accessor = new FakeAccessor();
        accessor.lastExecution = Optional.of(new ExecutionHistoryStore.Entry(
                "id1", "SELECT 1", "conn", "prod", 123L, 10L, true, "1 linha(s) afetada(s)"));

        AgentContext context = new DefaultContextProvider(accessor).collect();

        assertTrue(context.execution().lastSuccess());
        assertNull(context.execution().lastErrorMessage());
    }

    @Test
    void collectSemExecucaoDevolveExecutionContextVazio() {
        AgentContext context = new DefaultContextProvider(new FakeAccessor()).collect();
        assertEquals(ExecutionContext.EMPTY, context.execution());
    }

    private static final class FakeAccessor implements IdeStateAccessor {
        SchemaInfo cachedSchema;
        String activeSchemaName;
        String connectionLabel;
        String databaseProductName;
        String databaseVersion;
        String currentEditorSql;
        boolean hasEditorSelection;
        Optional<ExecutionHistoryStore.Entry> lastExecution = Optional.empty();

        @Override
        public ConnectionManager connectionManager() {
            return null;
        }

        @Override
        public MetadataService metadataService() {
            return null;
        }

        @Override
        public SchemaInfo cachedSchema() {
            return cachedSchema;
        }

        @Override
        public String activeSchemaName() {
            return activeSchemaName;
        }

        @Override
        public String connectionLabel() {
            return connectionLabel;
        }

        @Override
        public String databaseProductName() {
            return databaseProductName;
        }

        @Override
        public String databaseVersion() {
            return databaseVersion;
        }

        @Override
        public String currentEditorSql() {
            return currentEditorSql;
        }

        @Override
        public boolean hasEditorSelection() {
            return hasEditorSelection;
        }

        @Override
        public Optional<ExecutionHistoryStore.Entry> lastExecution() {
            return lastExecution;
        }
    }
}
