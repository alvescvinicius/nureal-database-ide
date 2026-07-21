package com.nureal.ide.core.ai.specialist;

/**
 * Especialista de um tipo de banco de dados — conhecimento/regras que o
 * {@code PromptComposer} acrescenta ao system prompt automaticamente, sem o
 * usuario escolher nada (o Agent resolve pelo tipo do banco conectado, ver
 * {@link SpecialistRegistry}). Mesma filosofia de extensao de
 * {@code core.dialect.DatabaseDialect}: uma implementacao por tipo de banco,
 * sem alterar o resto do app pra adicionar a proxima.
 */
public interface Specialist {

    /** Identificador estavel (ex.: "mysql") — nunca muda entre versoes, usado so internamente. */
    String id();

    /** Nome amigavel (ex.: "MySQL Specialist"). */
    String displayName();

    /** Conhecimento/regras especificas deste banco, acrescentado ao system prompt. */
    String systemPromptFragment();
}
