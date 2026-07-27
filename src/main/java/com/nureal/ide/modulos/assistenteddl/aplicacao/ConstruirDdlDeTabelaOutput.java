package com.nureal.ide.modulos.assistenteddl.aplicacao;

import java.util.List;

/**
 * Saida do caso de uso "construir DDL de tabela": ou a lista de instrucoes
 * DDL prontas (sucesso), ou um erro de validacao tipado com a mensagem ja
 * pronta para mostrar ao usuario — substitui a antiga
 * {@code SqlBuilderValidationException} lancada por
 * {@code DdlAssistantDialog.buildStatements()} (ver
 * .specs/07-modulo-assistente-ddl.md, regra 1: validacao de formulario e
 * erro de negocio esperado, nao excecao).
 */
public record ConstruirDdlDeTabelaOutput(List<String> statements, ErroDeValidacao erro, String mensagemErro) {

    /**
     * {@code NOME_INVALIDO}/{@code NOME_DUPLICADO}/{@code SEM_COLUNAS}: os
     * 3 casos de validacao do modo "criar tabela" (ver spec). {@code
     * DADOS_INCOMPLETOS}: erro de linha da grade (coluna/FK sem nome,
     * identificador invalido, coluna repetida) detectado por quem monta o
     * formulario ANTES de chamar este caso de uso — nao existe validacao de
     * linha aqui porque o Handler nunca ve os componentes Swing, so o
     * resultado ja coletado.
     */
    public enum ErroDeValidacao { NOME_INVALIDO, NOME_DUPLICADO, SEM_COLUNAS, DADOS_INCOMPLETOS }

    public static ConstruirDdlDeTabelaOutput sucesso(List<String> statements) {
        return new ConstruirDdlDeTabelaOutput(statements, null, null);
    }

    public static ConstruirDdlDeTabelaOutput erro(ErroDeValidacao erro, String mensagem) {
        return new ConstruirDdlDeTabelaOutput(List.of(), erro, mensagem);
    }

    public boolean sucesso() {
        return erro == null;
    }
}
