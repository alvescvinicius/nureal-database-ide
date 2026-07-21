package com.nureal.ide.core.ai.context;

import com.nureal.ide.core.connection.ConnectionManager;
import com.nureal.ide.core.metadata.MetadataService;
import com.nureal.ide.core.metadata.model.SchemaInfo;

/**
 * Ponte, sem depender de Swing, entre o modulo de IA e o estado ao vivo da
 * IDE (conexao ativa, schema cacheado, editor atual). A unica implementacao
 * concreta ({@code ui.ai.IdeContextAccessor}) le direto do
 * {@code MainWindow} — este pacote ({@code core.ai}) nunca importa
 * {@code javax.swing.*} nem {@code com.nureal.ide.ui.*}, so esta interface.
 *
 * Serve de base tanto para {@link DefaultContextProvider} (monta o
 * {@link AgentContext} textual do prompt) quanto para as Tools que precisam
 * de acesso real a conexao/metadados (ver {@code core.ai.tool}).
 */
public interface IdeStateAccessor {

    /** Gerenciador da conexao ativa, ou {@code null} se nenhuma aba estiver conectada. */
    ConnectionManager connectionManager();

    MetadataService metadataService();

    /** Ultimo schema carregado no cache da aba ativa, ou {@code null} se nunca carregado. */
    SchemaInfo cachedSchema();

    /** Nome do schema/banco ativo, ou {@code null}. */
    String activeSchemaName();

    /** Rotulo da conexao ativa sem senha (ex.: "host:3306/schema (user)"), ou {@code null}. */
    String connectionLabel();

    /** SQL selecionado (ou o conteudo completo da aba, se nada selecionado) no editor ativo, ou {@code null}. */
    String currentEditorSql();
}
