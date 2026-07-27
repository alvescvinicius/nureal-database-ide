package com.nureal.ide.modulos.conexoes.infraestrutura;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.nureal.ide.modulos.dialeto.dominio.contratos.DatabaseDialect;
import com.nureal.ide.modulos.dialeto.infraestrutura.MySqlDialect;

/**
 * Caracteriza {@link SessionInitializer} apos a correcao que fez a classe
 * parar de decidir por conta propria (switch no nome do produto) o que
 * rodar por banco, passando a executar exatamente o que o
 * {@link DatabaseDialect} da conexao declarar (ver
 * .specs/04-modulo-dialeto-e-metadados.md, regra 1).
 */
class SessionInitializerTest {

	@Test
	void naoExecutaNadaQuandoODialetoNaoDeclaraNenhumaInstrucao() throws Exception {
		List<String> executed = new ArrayList<>();
		Connection conn = fakeOpenConnection(executed);

		SessionInitializer.initialize(conn, new MySqlDialect());

		assertTrue(executed.isEmpty(), "MySqlDialect nao declara instrucoes de sessao hoje");
	}

	@Test
	void executaCadaInstrucaoDeclaradaPeloDialetoNaOrdem() throws Exception {
		List<String> executed = new ArrayList<>();
		Connection conn = fakeOpenConnection(executed);
		DatabaseDialect dialectComInstrucoes = fakeDialect(List.of("SET NAMES utf8mb4", "SET SESSION x=1"));

		SessionInitializer.initialize(conn, dialectComInstrucoes);

		assertEquals(List.of("SET NAMES utf8mb4", "SET SESSION x=1"), executed);
	}

	@Test
	void lancaExcecaoQuandoAConexaoEstaFechada() {
		Connection conn = fakeClosedConnection();

		assertThrows(SQLException.class, () -> SessionInitializer.initialize(conn, new MySqlDialect()));
	}

	private static Connection fakeOpenConnection(List<String> executed) {
		InvocationHandler handler = (proxy, method, args) -> {
			switch (method.getName()) {
			case "isClosed":
				return false;
			case "createStatement":
				return fakeStatement(executed);
			default:
				throw new UnsupportedOperationException(method.getName() + " nao usado por este fake");
			}
		};
		return (Connection) Proxy.newProxyInstance(SessionInitializerTest.class.getClassLoader(),
				new Class<?>[] { Connection.class }, handler);
	}

	private static Connection fakeClosedConnection() {
		InvocationHandler handler = (proxy, method, args) -> {
			if ("isClosed".equals(method.getName())) {
				return true;
			}
			throw new UnsupportedOperationException(method.getName() + " nao usado por este fake");
		};
		return (Connection) Proxy.newProxyInstance(SessionInitializerTest.class.getClassLoader(),
				new Class<?>[] { Connection.class }, handler);
	}

	private static Statement fakeStatement(List<String> executed) {
		InvocationHandler handler = (proxy, method, args) -> {
			switch (method.getName()) {
			case "execute":
				executed.add((String) args[0]);
				return false;
			case "close":
				return null;
			default:
				throw new UnsupportedOperationException(method.getName() + " nao usado por este fake");
			}
		};
		return (Statement) Proxy.newProxyInstance(SessionInitializerTest.class.getClassLoader(),
				new Class<?>[] { Statement.class }, handler);
	}

	private static DatabaseDialect fakeDialect(List<String> statements) {
		InvocationHandler handler = (proxy, method, args) -> {
			if ("sessionInitStatements".equals(method.getName())) {
				return statements;
			}
			throw new UnsupportedOperationException(method.getName() + " nao usado por este fake");
		};
		return (DatabaseDialect) Proxy.newProxyInstance(SessionInitializerTest.class.getClassLoader(),
				new Class<?>[] { DatabaseDialect.class }, handler);
	}
}
