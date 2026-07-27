package com.nureal.ide.modulos.iachat.dominio.contratos;
import com.nureal.ide.modulos.iachat.aplicacao.DefaultContextProvider;
import com.nureal.ide.modulos.iachat.dominio.entidades.AgentContext;

import java.util.Optional;

import com.nureal.ide.modulos.conexoes.dominio.contratos.ConexaoAtivaPort;
import com.nureal.ide.modulos.metadados.dominio.contratos.MetadataRepository;
import com.nureal.ide.modulos.historico.infraestrutura.ExecutionHistoryStore;
import com.nureal.ide.modulos.metadados.dominio.entidades.SchemaInfo;

/**
 * Ponte, sem depender de Swing, entre o modulo de IA e o estado ao vivo da
 * IDE (conexao ativa, schema cacheado, editor atual, ultima execucao). A
 * unica implementacao concreta ({@code modulos.iachat.apresentacao.IdeContextAccessor}) le direto do
 * {@code MainWindow} — este pacote ({@code modulos.iachat}) nunca importa
 * {@code javax.swing.*} nem {@code com.nureal.ide.ui.*}, so esta interface.
 *
 * Serve de base tanto para {@link DefaultContextProvider} (monta o
 * {@link AgentContext} do prompt) quanto para as Tools que precisam de
 * acesso real a conexao/metadados (ver {@code modulos.iachat.tool}).
 */
public interface IdeStateAccessor {

    /** Conexao ativa, ou {@code null} se nenhuma aba estiver conectada. */
    ConexaoAtivaPort connectionManager();

    MetadataRepository metadataService();

    /** Ultimo schema carregado no cache da aba ativa, ou {@code null} se nunca carregado. */
    SchemaInfo cachedSchema();

    /** Nome do schema/banco ativo, ou {@code null}. */
    String activeSchemaName();

    /** Rotulo da conexao ativa sem senha (ex.: "host:3306/schema (user)"), ou {@code null}. */
    String connectionLabel();

    /** Nome do produto do banco (ex.: "MySQL"), via {@code Connection#getMetaData()}, ou {@code null}. Usado pra resolver o Database Specialist. */
    String databaseProductName();

    /** Versao do banco (ex.: "8.4.0"), via {@code Connection#getMetaData()}, ou {@code null}. */
    String databaseVersion();

    /** SQL selecionado (ou o conteudo completo da aba, se nada selecionado) no editor ativo, ou {@code null}. */
    String currentEditorSql();

    /** {@code true} se {@link #currentEditorSql()} veio de uma selecao (nao do conteudo inteiro da aba). */
    boolean hasEditorSelection();

    /** Ultima execucao registrada para a conexao ativa (qualquer aba), se houver. */
    Optional<ExecutionHistoryStore.Entry> lastExecution();
}
