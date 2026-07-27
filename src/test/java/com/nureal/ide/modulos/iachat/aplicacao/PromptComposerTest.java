package com.nureal.ide.modulos.iachat.aplicacao;
import com.nureal.ide.modulos.iachat.dominio.contratos.Specialist;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.nureal.ide.modulos.iachat.dominio.entidades.AgentContext;
import com.nureal.ide.modulos.iachat.dominio.entidades.ConnectionContext;
import com.nureal.ide.modulos.iachat.dominio.entidades.EditorContext;
import com.nureal.ide.modulos.iachat.dominio.entidades.ExecutionContext;
import com.nureal.ide.modulos.iachat.dominio.entidades.MetadataContext;

class PromptComposerTest {

    @Test
    void contextoVazioAindaGeraOPromptBase() {
        String prompt = PromptComposer.compose(AgentContext.EMPTY);
        assertTrue(prompt.contains("Nureal Database IDE"));
        assertFalse(prompt.contains("Conexao ativa"), "sem conexao, nao deveria mencionar conexao nenhuma");
    }

    @Test
    void bancoMysqlAcrescentaOFragmentoDoSpecialist() {
        AgentContext context = new AgentContext(
                new ConnectionContext("localhost:3306/prod (root)", "MySQL", "8.4.0", "prod"),
                MetadataContext.EMPTY, EditorContext.EMPTY, ExecutionContext.EMPTY);

        String prompt = PromptComposer.compose(context);

        assertTrue(prompt.contains("MySQL Specialist"));
        assertTrue(prompt.contains("backticks"));
        assertTrue(prompt.contains("Conexao ativa: localhost:3306/prod (root)"));
        assertTrue(prompt.contains("Banco: MySQL 8.4.0"));
        assertTrue(prompt.contains("Schema ativo: prod"));
    }

    @Test
    void bancoDesconhecidoNaoAcrescentaFragmentoDeSpecialist() {
        AgentContext context = new AgentContext(
                new ConnectionContext("host/db (user)", "Oracle", "19c", "db"),
                MetadataContext.EMPTY, EditorContext.EMPTY, ExecutionContext.EMPTY);

        String prompt = PromptComposer.compose(context);

        assertFalse(prompt.contains("Specialist"));
        assertTrue(prompt.contains("Banco: Oracle 19c"), "ainda deve mencionar o banco, so sem fragmento especializado");
    }

    @Test
    void metadataContextApareceQuandoHaTabelas() {
        AgentContext context = new AgentContext(ConnectionContext.EMPTY,
                new MetadataContext(12, 3, List.of("orders", "customers")), EditorContext.EMPTY, ExecutionContext.EMPTY);

        String prompt = PromptComposer.compose(context);

        assertTrue(prompt.contains("12 tabela(s)"));
        assertTrue(prompt.contains("3 view(s)"));
    }

    @Test
    void editorContextDistingueSelecaoDeAbaInteira() {
        AgentContext comSelecao = new AgentContext(ConnectionContext.EMPTY, MetadataContext.EMPTY,
                new EditorContext("SELECT 1", true), ExecutionContext.EMPTY);
        AgentContext semSelecao = new AgentContext(ConnectionContext.EMPTY, MetadataContext.EMPTY,
                new EditorContext("SELECT 1", false), ExecutionContext.EMPTY);

        assertTrue(PromptComposer.compose(comSelecao).contains("SQL selecionado no editor:"));
        assertTrue(PromptComposer.compose(semSelecao).contains("SQL atual no editor:"));
    }

    @Test
    void executionContextComErroIncluiAMensagem() {
        AgentContext context = new AgentContext(ConnectionContext.EMPTY, MetadataContext.EMPTY, EditorContext.EMPTY,
                new ExecutionContext("DELETE FROM orders", false, "constraint violada", 15));

        String prompt = PromptComposer.compose(context);

        assertTrue(prompt.contains("Ultima execucao (erro): DELETE FROM orders"));
        assertTrue(prompt.contains("Erro: constraint violada"));
    }
}
