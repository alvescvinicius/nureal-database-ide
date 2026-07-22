package com.nureal.ide.ui;

import java.util.regex.Pattern;

/**
 * Validacao de nome de identificador SQL (tabela/view/trigger/procedure/
 * function) usada pelos assistentes de DDL guiados — mesma regra em
 * {@link DdlAssistantDialog}, {@link ViewBuilderDialog},
 * {@link TriggerBuilderDialog} e {@link RoutineBuilderDialog}: letras,
 * numeros e {@code _}/{@code $}, sem comecar com numero. Antes cada um
 * declarava seu proprio {@code Pattern} identico (SPEC-0008 Etapa 2);
 * unificado aqui.
 */
final class SqlIdentifiers {

	private static final Pattern IDENTIFIER = Pattern.compile("^[A-Za-z_][A-Za-z0-9_$]*$");

	private SqlIdentifiers() {
	}

	static boolean isValid(String name) {
		return IDENTIFIER.matcher(name).matches();
	}
}
