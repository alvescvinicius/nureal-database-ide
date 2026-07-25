package com.nureal.ide.compartilhado.persistencia;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Caracteriza {@link ArquivoChaveValorUtil}, extraido das cinco stores de
 * arquivo plano que reimplementavam esta mesma logica de forma identica
 * (ver .specs/08-modulo-historico-consultas-sessao.md, regra 1).
 */
class ArquivoChaveValorUtilTest {

	@Test
	void decodeDesfazExatamenteOQueEncodeFez() {
		String original = "SELECT * FROM clientes\nWHERE id = 1; -- linha com quebra";

		String codificado = ArquivoChaveValorUtil.encode(original);
		String decodificado = ArquivoChaveValorUtil.decode(codificado);

		assertEquals(original, decodificado);
	}

	@Test
	void encodeNuncaProduzQuebraDeLinha() {
		String comQuebras = "linha 1\nlinha 2\r\nlinha 3";

		String codificado = ArquivoChaveValorUtil.encode(comQuebras);

		assertEquals(-1, codificado.indexOf('\n'), "Base64 nao deveria conter quebra de linha");
		assertEquals(-1, codificado.indexOf('\r'), "Base64 nao deveria conter retorno de carro");
	}

	@Test
	void decodeDevolveStringVaziaParaEntradaMalformada() {
		assertEquals("", ArquivoChaveValorUtil.decode("nao e base64 valido!!!"));
	}

	@Test
	void parseLongDevolveZeroParaEntradaInvalidaOuAusente() {
		assertEquals(0L, ArquivoChaveValorUtil.parseLong("abc"));
		assertEquals(0L, ArquivoChaveValorUtil.parseLong(""));
		assertEquals(42L, ArquivoChaveValorUtil.parseLong("42"));
	}

	@Test
	void parseIntDevolveZeroParaEntradaInvalidaOuAusente() {
		assertEquals(0, ArquivoChaveValorUtil.parseInt("abc"));
		assertEquals(0, ArquivoChaveValorUtil.parseInt(""));
		assertEquals(7, ArquivoChaveValorUtil.parseInt("7"));
	}
}
