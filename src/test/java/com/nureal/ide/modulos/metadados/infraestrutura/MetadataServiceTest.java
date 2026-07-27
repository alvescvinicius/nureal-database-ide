package com.nureal.ide.modulos.metadados.infraestrutura;
import com.nureal.ide.modulos.metadados.dominio.contratos.MetadataRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.nureal.ide.modulos.dialeto.infraestrutura.MySqlDialect;

/**
 * Caracteriza {@link MetadataService} apos ele passar a implementar
 * {@link MetadataRepository} (ver .specs/04-modulo-dialeto-e-metadados.md,
 * regra 2) — cobre so {@code listSchemas}, o metodo mais simples, com um
 * fake de {@link Connection}/{@link PreparedStatement}/{@link ResultSet} via
 * {@link Proxy}; os demais metodos (leitura completa de schema/tabela) fazem
 * multiplas consultas encadeadas e ficam para uma suite de integracao contra
 * MySQL real (ver .specs/16-estrategia-de-testes.md).
 */
class MetadataServiceTest {

	@Test
	void listSchemasDevolveOsNomesNaOrdemDoResultSet() throws Exception {
		MetadataRepository repository = new MetadataService(new MySqlDialect());
		Connection conn = fakeConnection(List.of("app_dev", "app_prod"));

		List<String> schemas = repository.listSchemas(conn);

		assertEquals(List.of("app_dev", "app_prod"), schemas);
	}

	private static Connection fakeConnection(List<String> schemaNames) {
		InvocationHandler handler = (proxy, method, args) -> {
			if ("prepareStatement".equals(method.getName())) {
				return fakeStatement(schemaNames);
			}
			throw new UnsupportedOperationException(method.getName() + " nao usado por este fake");
		};
		return (Connection) Proxy.newProxyInstance(MetadataServiceTest.class.getClassLoader(),
				new Class<?>[] { Connection.class }, handler);
	}

	private static PreparedStatement fakeStatement(List<String> schemaNames) {
		InvocationHandler handler = (proxy, method, args) -> {
			switch (method.getName()) {
			case "executeQuery":
				return fakeResultSet(schemaNames);
			case "close":
				return null;
			default:
				throw new UnsupportedOperationException(method.getName() + " nao usado por este fake");
			}
		};
		return (PreparedStatement) Proxy.newProxyInstance(MetadataServiceTest.class.getClassLoader(),
				new Class<?>[] { PreparedStatement.class }, handler);
	}

	private static ResultSet fakeResultSet(List<String> schemaNames) {
		Deque<String> pending = new ArrayDeque<>(schemaNames);
		String[] current = { null };
		InvocationHandler handler = (proxy, method, args) -> {
			switch (method.getName()) {
			case "next":
				if (pending.isEmpty()) {
					return false;
				}
				current[0] = pending.poll();
				return true;
			case "getString":
				return current[0];
			case "close":
				return null;
			default:
				throw new UnsupportedOperationException(method.getName() + " nao usado por este fake");
			}
		};
		return (ResultSet) Proxy.newProxyInstance(MetadataServiceTest.class.getClassLoader(),
				new Class<?>[] { ResultSet.class }, handler);
	}
}
