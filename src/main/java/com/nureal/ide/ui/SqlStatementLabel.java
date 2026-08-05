package com.nureal.ide.ui;

import java.awt.Color;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.nureal.ide.compartilhado.designsystem.GridTheme;
import com.nureal.ide.compartilhado.designsystem.IconType;

/**
 * Deriva um nome de aba que reflete o MODELO MENTAL do usuario (ex.:
 * "categoria_produto", "UPDATE pedido") a partir do texto de uma instrucao
 * SQL ja executada, em vez do modelo interno da aplicacao ("Resultado 3") —
 * pedido explicito do usuario ("o usuario nunca pensa 'vou abrir o Resultado
 * 3'... ele pensa 'vou voltar na consulta de clientes'").
 * <p>
 * So reconhece os 4 verbos mais comuns (SELECT/INSERT/UPDATE/DELETE) + CREATE
 * TABLE — qualquer coisa fora desse conjunto (ALTER, DROP, scripts com
 * sintaxe incomum etc.) cai no titulo generico de sempre (ver o {@code
 * fallback} de {@link #title}), em vez de uma regex fragil tentando adivinhar
 * toda a gramatica SQL.
 */
final class SqlStatementLabel {

	enum Kind {
		SELECT, INSERT, UPDATE, DELETE, CREATE, OTHER
	}

	/** Tamanho maximo do NOME (tabela/objeto) antes de abreviar com "..." — ver exemplo do usuario ("UPDATE cobranca_cliente..."). */
	private static final int MAX_NAME_LEN = 18;

	private static final Pattern FROM = Pattern.compile("\\bFROM\\s+([\\w.\"`]+)", Pattern.CASE_INSENSITIVE);
	private static final Pattern INSERT_INTO = Pattern.compile("\\bINSERT\\s+(?:INTO\\s+)?([\\w.\"`]+)",
			Pattern.CASE_INSENSITIVE);
	private static final Pattern UPDATE_TARGET = Pattern.compile("\\bUPDATE\\s+([\\w.\"`]+)", Pattern.CASE_INSENSITIVE);
	private static final Pattern DELETE_TARGET = Pattern.compile("\\bDELETE\\s+(?:FROM\\s+)?([\\w.\"`]+)",
			Pattern.CASE_INSENSITIVE);
	private static final Pattern CREATE_TABLE = Pattern.compile(
			"\\bCREATE\\s+(?:OR\\s+REPLACE\\s+)?(?:TEMP(?:ORARY)?\\s+)?TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?([\\w.\"`]+)",
			Pattern.CASE_INSENSITIVE);
	/** Pula comentarios de linha/bloco antes do verbo — sem isto, um script comentado ("-- nota\nSELECT ...") sempre caia em OTHER. */
	private static final Pattern LEADING_COMMENTS = Pattern.compile("^(\\s*(--[^\n]*(\n|$)|/\\*.*?\\*/\\s*))+",
			Pattern.DOTALL);

	private SqlStatementLabel() {
	}

	static Kind kindOf(String sql) {
		String s = LEADING_COMMENTS.matcher(sql.strip()).replaceFirst("").stripLeading();
		String upper = s.toUpperCase(Locale.ROOT);
		if (upper.startsWith("SELECT") || upper.startsWith("WITH")) {
			return Kind.SELECT;
		}
		if (upper.startsWith("INSERT")) {
			return Kind.INSERT;
		}
		if (upper.startsWith("UPDATE")) {
			return Kind.UPDATE;
		}
		if (upper.startsWith("DELETE")) {
			return Kind.DELETE;
		}
		if (upper.startsWith("CREATE")) {
			return Kind.CREATE;
		}
		return Kind.OTHER;
	}

	/** Titulo pronto pra exibir na aba, ou {@code fallback} se nao reconhecer um alvo (tabela/objeto) claro. */
	static String title(String sql, Kind kind, String fallback) {
		String target = switch (kind) {
		case SELECT -> firstMatch(FROM, sql);
		case INSERT -> firstMatch(INSERT_INTO, sql);
		case UPDATE -> firstMatch(UPDATE_TARGET, sql);
		case DELETE -> firstMatch(DELETE_TARGET, sql);
		case CREATE -> firstMatch(CREATE_TABLE, sql);
		case OTHER -> null;
		};
		if (target == null) {
			return fallback;
		}
		String name = truncate(cleanIdentifier(target));
		return switch (kind) {
		case SELECT -> name;
		case CREATE -> "CREATE TABLE " + name;
		default -> kind.name() + " " + name;
		};
	}

	/** Icone de apoio por tipo de instrucao — ver legenda do usuario (SELECT/UPDATE/INSERT/DELETE/CREATE). */
	static IconType iconFor(Kind kind) {
		return switch (kind) {
		case UPDATE -> IconType.EDIT;
		case INSERT -> IconType.NEW;
		case DELETE -> IconType.DELETE;
		case SELECT, CREATE, OTHER -> IconType.TABLE;
		};
	}

	/** Cor de apoio por tipo de instrucao — mesma paleta semantica ja usada no resto do app (ver {@link GridTheme}). */
	static Color colorFor(Kind kind) {
		return switch (kind) {
		case SELECT -> GridTheme.ACCENT_INFO;
		case UPDATE -> GridTheme.ACCENT_WARNING;
		case INSERT -> GridTheme.BRAND_GREEN;
		case DELETE -> GridTheme.ACCENT_ERROR;
		case CREATE -> GridTheme.ACCENT_TOOL;
		case OTHER -> GridTheme.MUTED_TEXT;
		};
	}

	private static String firstMatch(Pattern p, String sql) {
		Matcher m = p.matcher(sql);
		return m.find() ? m.group(1) : null;
	}

	/** Remove aspas/crase e qualificador de esquema ("schema.tabela" -> "tabela") — so o nome que o usuario reconhece. */
	private static String cleanIdentifier(String raw) {
		String s = raw.replace("`", "").replace("\"", "");
		int dot = s.lastIndexOf('.');
		if (dot >= 0 && dot < s.length() - 1) {
			s = s.substring(dot + 1);
		}
		return s;
	}

	private static String truncate(String s) {
		return (s.length() <= MAX_NAME_LEN) ? s : s.substring(0, MAX_NAME_LEN) + "...";
	}
}
