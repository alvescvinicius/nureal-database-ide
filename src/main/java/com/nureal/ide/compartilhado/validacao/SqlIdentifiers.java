package com.nureal.ide.compartilhado.validacao;

import java.util.regex.Pattern;

/**
 * Validacao de nome de identificador SQL (tabela/view/trigger/procedure/
 * function) usada pelos assistentes de DDL guiados — mesma regra em
 * {@code DdlAssistantDialog}, {@code ViewBuilderDialog},
 * {@code TriggerBuilderDialog} e {@code RoutineBuilderDialog} (e agora
 * tambem por {@code ConstruirDdlDeTabelaHandler}, em
 * {@code modulos.assistenteddl}): letras, numeros e {@code _}/{@code $}, sem
 * comecar com numero. Movida para {@code compartilhado} por ja ter, de
 * fato, 4+ usos identicos entre dialogs de camadas diferentes (SPEC-0008
 * Etapa 2 ja unificava isto num unico lugar dentro de {@code ui}; agora que
 * um desses usos passou a viver em {@code modulos.assistenteddl.aplicacao},
 * o utilitario precisa estar acessivel sem {@code aplicacao} depender de
 * {@code ui}).
 */
public final class SqlIdentifiers {

	private static final Pattern IDENTIFIER = Pattern.compile("^[A-Za-z_][A-Za-z0-9_$]*$");

	private SqlIdentifiers() {
	}

	public static boolean isValid(String name) {
		return IDENTIFIER.matcher(name).matches();
	}
}
