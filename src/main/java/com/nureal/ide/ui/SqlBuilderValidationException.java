package com.nureal.ide.ui;

/**
 * Erro de validacao de formulario dos assistentes de DDL guiados
 * ({@link DdlAssistantDialog}, {@link ViewBuilderDialog},
 * {@link TriggerBuilderDialog}, {@link RoutineBuilderDialog}) — mensagem
 * pronta para mostrar ao usuario (num {@code JOptionPane} ou na propria
 * pre-visualizacao do DDL), nunca um erro de programacao.
 * <p>
 * Antes, cada um dos 4 assistentes declarava sua PROPRIA copia identica
 * desta classe (SPEC-0008 Etapa 2: "ValidationException 100% igual nos 4
 * arquivos") — unificada aqui, cada assistente so lanca/captura este tipo em
 * vez de ter a sua.
 */
final class SqlBuilderValidationException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	SqlBuilderValidationException(String message) {
		super(message);
	}
}
