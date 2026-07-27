package com.nureal.ide.modulos.conexoes.infraestrutura;
import com.nureal.ide.modulos.conexoes.dominio.contratos.ConexaoAtivaPort;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.junit.jupiter.api.Test;

import com.nureal.ide.modulos.dialeto.dominio.contratos.DatabaseDialect;
import com.nureal.ide.modulos.dialeto.infraestrutura.MySqlDialect;

/**
 * Caracteriza {@link ConnectionManager} apos ele passar a implementar
 * {@link ConexaoAtivaPort} (ver .specs/03-modulo-conexoes-e-seguranca.md,
 * regra 2) — cobre apenas o estado inicial/de fronteira, ja que abrir uma
 * conexao de verdade exige um MySQL real (ver .specs/16-estrategia-de-testes.md:
 * testes de infraestrutura contra banco real ficam para uma suite separada).
 */
class ConnectionManagerTest {

	@Test
	void comecaDesconectadoESemPerfil() {
		ConexaoAtivaPort port = new ConnectionManager(new MySqlDialect());

		assertFalse(port.isConnected());
		assertNull(port.getConnection());
		assertNull(port.profile());
	}

	@Test
	void exposOMesmoDialetoRecebidoNoConstrutor() {
		DatabaseDialect dialect = new MySqlDialect();
		ConexaoAtivaPort port = new ConnectionManager(dialect);

		assertSame(dialect, port.dialect());
	}

	@Test
	void fecharSemNuncaTerAbertoNaoLancaExcecao() {
		ConexaoAtivaPort port = new ConnectionManager(new MySqlDialect());

		assertDoesNotThrow(port::close);
		assertFalse(port.isConnected());
	}
}
