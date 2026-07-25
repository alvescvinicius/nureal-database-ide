package com.nureal.ide.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import javax.swing.table.DefaultTableModel;

import org.junit.jupiter.api.Test;

/**
 * Testes de caracterizacao do motor de execucao de lote de instrucoes SQL,
 * escritos ANTES de qualquer nova extracao (ver
 * .specs/16-estrategia-de-testes.md) para servir de rede de seguranca —
 * {@link SqlExecutionEngine} foi extraida de {@code MainWindow} sem mudanca
 * de comportamento, e estes testes fixam esse comportamento.
 *
 * <p>Como {@link Connection}/{@link Statement}/{@link ResultSet} sao
 * interfaces JDBC, os testes abaixo usam fakes minimos via
 * {@link Proxy} em vez de subir um banco real — exatamente o ganho de
 * testabilidade que motivou a extracao (ver
 * .specs/06-modulo-execucao-e-edicao-de-grade.md).
 */
class SqlExecutionEngineTest {

	@Test
	void appendPageParaNoLimiteSolicitadoMesmoComMaisLinhasDisponiveis() throws Exception {
		DefaultTableModel model = new DefaultTableModel(new Object[] { "id" }, 0);
		ResultSet rs = fakeResultSet(List.of(row(1), row(2), row(3)));

		int read = SqlExecutionEngine.appendPage(model, rs, 2);

		assertEquals(2, read);
		assertEquals(2, model.getRowCount());
		assertEquals(1, model.getValueAt(0, 0));
		assertEquals(2, model.getValueAt(1, 0));
	}

	@Test
	void appendPageParaAntesDoLimiteQuandoResultSetAcaba() throws Exception {
		DefaultTableModel model = new DefaultTableModel(new Object[] { "id" }, 0);
		ResultSet rs = fakeResultSet(List.<Object[]>of(row(1)));

		int read = SqlExecutionEngine.appendPage(model, rs, 200);

		assertEquals(1, read);
		assertEquals(1, model.getRowCount());
	}

	@Test
	void executeStatementsNaoExecutaNadaQuandoJaCanceladoNoInicio() {
		List<QueryResult> results = SqlExecutionEngine.executeStatements(
				fakeConnection(new ArrayDeque<>()), List.of("SELECT 1"), () -> true, st -> {
				});

		assertTrue(results.isEmpty());
	}

	@Test
	void executeStatementsParaNaPrimeiraInstrucaoComErroSemRodarAsSeguintes() {
		Deque<Object> statementScripts = new ArrayDeque<>(
				List.of(updateCountScript(1), errorScript("erro de sintaxe perto de 'X'"), updateCountScript(1)));

		List<QueryResult> results = SqlExecutionEngine.executeStatements(
				fakeConnection(statementScripts),
				List.of("UPDATE a SET x=1", "ISTO NAO E SQL VALIDO", "UPDATE c SET x=1"),
				() -> false, st -> {
				});

		assertEquals(2, results.size());
		assertFalse(results.get(0).error());
		assertEquals("1 linha(s) afetada(s)", results.get(0).message());
		assertTrue(results.get(1).error());
		assertTrue(results.get(1).message().contains("erro de sintaxe perto de 'X'"),
				"mensagem de erro deveria conter o texto original da excecao SQL: " + results.get(1).message());
	}

	@Test
	void executeStatementsNotificaOStatementEmExecucaoParaPermitirCancelamento() {
		Deque<Object> statementScripts = new ArrayDeque<>(List.of(updateCountScript(0)));
		StringBuilder notified = new StringBuilder();

		SqlExecutionEngine.executeStatements(fakeConnection(statementScripts), List.of("DELETE FROM x"), () -> false,
				st -> notified.append(st == null ? "null" : "statement").append(';'));

		// chamado uma vez com o Statement em execucao e, no finally, uma vez com null
		assertEquals("statement;null;", notified.toString());
	}

	// ---------- fakes minimos via Proxy (sem depender de banco real) ----------

	private static Object[] row(Object... values) {
		return values;
	}

	private static ResultSet fakeResultSet(List<Object[]> rows) {
		Deque<Object[]> pending = new ArrayDeque<>(rows);
		Object[] current = { null };
		InvocationHandler handler = (proxy, method, args) -> {
			switch (method.getName()) {
			case "next":
				if (pending.isEmpty()) {
					return false;
				}
				current[0] = pending.poll();
				return true;
			case "getObject":
				int col = (int) args[0];
				return ((Object[]) current[0])[col - 1];
			default:
				throw new UnsupportedOperationException(method.getName() + " nao usado por este fake");
			}
		};
		return (ResultSet) Proxy.newProxyInstance(SqlExecutionEngineTest.class.getClassLoader(),
				new Class<?>[] { ResultSet.class }, handler);
	}

	/** "Script" de uma instrucao: ou termina com sucesso (contagem de linhas) ou lanca SQLException. */
	private static Object updateCountScript(int updateCount) {
		return updateCount;
	}

	private static Object errorScript(String message) {
		return new SQLException(message);
	}

	private static Connection fakeConnection(Deque<Object> statementScripts) {
		InvocationHandler handler = (proxy, method, args) -> {
			if ("createStatement".equals(method.getName())) {
				return fakeStatement(statementScripts.poll());
			}
			throw new UnsupportedOperationException(method.getName() + " nao usado por este fake");
		};
		return (Connection) Proxy.newProxyInstance(SqlExecutionEngineTest.class.getClassLoader(),
				new Class<?>[] { Connection.class }, handler);
	}

	private static Statement fakeStatement(Object script) {
		InvocationHandler handler = (proxy, method, args) -> {
			switch (method.getName()) {
			case "setFetchSize":
			case "close":
				return null;
			case "execute":
				if (script instanceof SQLException ex) {
					throw ex;
				}
				return false; // nunca ha ResultSet nestes testes (so contagem de update)
			case "getUpdateCount":
				return (int) script;
			default:
				throw new UnsupportedOperationException(method.getName() + " nao usado por este fake");
			}
		};
		return (Statement) Proxy.newProxyInstance(SqlExecutionEngineTest.class.getClassLoader(),
				new Class<?>[] { Statement.class }, handler);
	}
}
