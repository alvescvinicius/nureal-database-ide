package com.nureal.ide.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

import org.junit.jupiter.api.Test;

import com.nureal.ide.modulos.dialeto.dominio.contratos.DatabaseDialect;
import com.nureal.ide.modulos.dialeto.infraestrutura.MySqlDialect;

/**
 * Testes de caracterizacao do motor de persistencia de edicoes de grade,
 * escritos ANTES de qualquer nova extracao (ver
 * .specs/16-estrategia-de-testes.md) — {@link SqlGridPersistenceEngine} foi
 * extraida de {@link GridEditController} sem mudanca de comportamento, e
 * estes testes fixam esse comportamento usando fakes de {@link Connection}/
 * {@link PreparedStatement}/{@link ResultSet} via {@link Proxy}, sem
 * depender de um MySQL real.
 */
class SqlGridPersistenceEngineTest {

	private final DatabaseDialect dialect = new MySqlDialect();

	@Test
	void insereLinhaNovaEDevolveChaveGeradaQuandoPkNaoFoiPreenchidaPeloUsuario() throws Exception {
		ResultTableModel model = model("id", "nome");
		model.addRow(new Object[] { null, "Ana" });
		EditableTarget target = new EditableTarget("clientes", List.of(0), List.of(0, 1));

		List<String> executedSql = new ArrayList<>();
		Connection conn = fakeConnection(executedSql, new LinkedHashMap<>(), 42, false);

		SqlGridPersistenceEngine.Resultado r = SqlGridPersistenceEngine.apply(conn, dialect, target, model,
				Set.of(), Set.of(0), Map.of(), new HashMap<>());

		assertEquals(1, r.inserted());
		assertEquals(0, r.updated());
		assertEquals(0, r.deleted());
		assertEquals(Map.of(0, 42), r.generatedKeys());
		assertEquals(1, executedSql.size());
		assertTrue(executedSql.get(0).startsWith("INSERT INTO `clientes` (`nome`)"),
				"a PK vazia nao deveria entrar na lista de colunas do INSERT: " + executedSql.get(0));
	}

	@Test
	void naoBuscaChaveGeradaQuandoUsuarioJaPreencheuAPk() throws Exception {
		ResultTableModel model = model("id", "nome");
		model.addRow(new Object[] { 99, "Bia" });
		EditableTarget target = new EditableTarget("clientes", List.of(0), List.of(0, 1));

		Connection conn = fakeConnection(new ArrayList<>(), new LinkedHashMap<>(), 999, false);

		SqlGridPersistenceEngine.Resultado r = SqlGridPersistenceEngine.apply(conn, dialect, target, model,
				Set.of(), Set.of(0), Map.of(), new HashMap<>());

		assertEquals(1, r.inserted());
		assertTrue(r.generatedKeys().isEmpty(),
				"PK ja preenchida pelo usuario nao deveria gerar entrada em generatedKeys()");
	}

	@Test
	void excluiLinhaExistenteUsandoValorOriginalDaPkComoAncoraDoWhere() throws Exception {
		ResultTableModel model = model("id", "nome");
		model.addRow(new Object[] { 7, "Bia" });
		EditableTarget target = new EditableTarget("clientes", List.of(0), List.of(0, 1));
		Map<Integer, Object[]> originalRow = new HashMap<>();
		originalRow.put(0, new Object[] { 7, "Bia" });

		List<String> executedSql = new ArrayList<>();
		Map<Integer, Object> boundParams = new LinkedHashMap<>();
		Connection conn = fakeConnection(executedSql, boundParams, null, false);

		SqlGridPersistenceEngine.Resultado r = SqlGridPersistenceEngine.apply(conn, dialect, target, model,
				Set.of(0), Set.of(), Map.of(), originalRow);

		assertEquals(0, r.inserted());
		assertEquals(0, r.updated());
		assertEquals(1, r.deleted());
		assertEquals("DELETE FROM `clientes` WHERE `id` = ?", executedSql.get(0));
		assertEquals(7, boundParams.get(1));
	}

	@Test
	void erroDuranteAExecucaoDesfazATransacaoInteiraERelancaAExcecao() {
		ResultTableModel model = model("id", "nome");
		model.addRow(new Object[] { 1, "Carla" });
		EditableTarget target = new EditableTarget("clientes", List.of(0), List.of(0, 1));
		Map<Integer, Object[]> originalRow = new HashMap<>();
		originalRow.put(0, new Object[] { 1, "Carla" });
		Map<Integer, Set<Integer>> dirty = Map.of(0, Set.of(1));

		List<Boolean> rolledBack = new ArrayList<>();
		Connection conn = fakeConnection(new ArrayList<>(), new LinkedHashMap<>(), null, true, rolledBack);

		assertThrows(SQLException.class, () -> SqlGridPersistenceEngine.apply(conn, dialect, target, model,
				Set.of(), Set.of(), dirty, originalRow));

		assertEquals(List.of(true), rolledBack);
	}

	// ---------- fixtures ----------

	private static ResultTableModel model(String... columnNames) {
		Vector<String> names = new Vector<>(Arrays.asList(columnNames));
		Class<?>[] types = new Class<?>[columnNames.length];
		Arrays.fill(types, Object.class);
		String[] sourceTables = new String[columnNames.length];
		String[] realColumnNames = columnNames.clone();
		String[] sqlTypeNames = new String[columnNames.length];
		return new ResultTableModel(names, types, sourceTables, realColumnNames, sqlTypeNames);
	}

	// ---------- fakes minimos via Proxy (sem depender de banco real) ----------

	private Connection fakeConnection(List<String> executedSql, Map<Integer, Object> boundParams,
			Integer generatedKey, boolean failExecuteUpdate) {
		return fakeConnection(executedSql, boundParams, generatedKey, failExecuteUpdate, new ArrayList<>());
	}

	private Connection fakeConnection(List<String> executedSql, Map<Integer, Object> boundParams,
			Integer generatedKey, boolean failExecuteUpdate, List<Boolean> rolledBack) {
		boolean[] autoCommit = { true };
		InvocationHandler handler = (proxy, method, args) -> {
			switch (method.getName()) {
			case "getAutoCommit":
				return autoCommit[0];
			case "setAutoCommit":
				autoCommit[0] = (boolean) args[0];
				return null;
			case "commit":
				return null;
			case "rollback":
				rolledBack.add(true);
				return null;
			case "prepareStatement":
				executedSql.add((String) args[0]);
				return fakePreparedStatement(boundParams, generatedKey, failExecuteUpdate);
			default:
				throw new UnsupportedOperationException(method.getName() + " nao usado por este fake");
			}
		};
		return (Connection) Proxy.newProxyInstance(SqlGridPersistenceEngineTest.class.getClassLoader(),
				new Class<?>[] { Connection.class }, handler);
	}

	private PreparedStatement fakePreparedStatement(Map<Integer, Object> boundParams, Integer generatedKey,
			boolean failExecuteUpdate) {
		InvocationHandler handler = (proxy, method, args) -> {
			switch (method.getName()) {
			case "setObject":
				boundParams.put((Integer) args[0], args[1]);
				return null;
			case "executeUpdate":
				if (failExecuteUpdate) {
					throw new SQLException("falha simulada no executeUpdate");
				}
				return 1;
			case "getGeneratedKeys":
				return fakeGeneratedKeysResultSet(generatedKey);
			case "close":
				return null;
			default:
				throw new UnsupportedOperationException(method.getName() + " nao usado por este fake");
			}
		};
		return (PreparedStatement) Proxy.newProxyInstance(SqlGridPersistenceEngineTest.class.getClassLoader(),
				new Class<?>[] { PreparedStatement.class }, handler);
	}

	private ResultSet fakeGeneratedKeysResultSet(Integer generatedKey) {
		boolean[] consumed = { false };
		InvocationHandler handler = (proxy, method, args) -> {
			switch (method.getName()) {
			case "next":
				if (generatedKey == null || consumed[0]) {
					return false;
				}
				consumed[0] = true;
				return true;
			case "getObject":
				return generatedKey;
			case "close":
				return null;
			default:
				throw new UnsupportedOperationException(method.getName() + " nao usado por este fake");
			}
		};
		return (ResultSet) Proxy.newProxyInstance(SqlGridPersistenceEngineTest.class.getClassLoader(),
				new Class<?>[] { ResultSet.class }, handler);
	}
}
