package com.nureal.ide.modulos.iachat.aplicacao;

import com.nureal.ide.modulos.iachat.dominio.entidades.AgentContext;
import com.nureal.ide.modulos.iachat.dominio.entidades.ConnectionContext;
import com.nureal.ide.modulos.iachat.dominio.entidades.EditorContext;
import com.nureal.ide.modulos.iachat.dominio.entidades.ExecutionContext;
import com.nureal.ide.modulos.iachat.dominio.entidades.MetadataContext;
import com.nureal.ide.modulos.iachat.dominio.contratos.Specialist;
import com.nureal.ide.modulos.iachat.infraestrutura.specialist.SpecialistRegistry;

/**
 * Monta o system prompt final: base + fragmento do Database Specialist
 * (resolvido automaticamente pelo tipo de banco conectado, usuario nunca
 * escolhe) + secoes do {@link AgentContext} (conexao, metadados, editor,
 * ultima execucao). O Provider nunca monta prompt — isso e responsabilidade
 * exclusiva da IDE (ver a especificacao "AI Provider Architecture").
 */
public final class PromptComposer {

    private static final String BASE_PROMPT =
            "Voce e o assistente de IA integrado a Nureal Database IDE, uma IDE para bancos de dados. "
                    + "Responda em portugues do Brasil, de forma direta e tecnica. "
                    + "Quando precisar de informacoes reais do banco conectado (tabelas, colunas, indices, "
                    + "chaves estrangeiras), use as tools disponiveis em vez de adivinhar. "
                    + "Para perguntas quantitativas/analiticas sobre os DADOS (contagens, agregacoes, filtros, "
                    + "comparacoes) que list_tables/describe_table (so metadados) nao respondem, prefira "
                    + "execute_sql para trazer os numeros reais em vez de estimar. "
                    + "Sempre que sugerir SQL, coloque em um bloco de codigo ```sql. Quando fizer sentido "
                    + "destacar uma dica ou um aviso importante (ex.: risco de uma operacao destrutiva), use "
                    + "um bloco de citacao no formato \"> [!TIP]\" ou \"> [!WARNING]\" seguido do texto nas "
                    + "linhas seguintes comecando com \">\", em vez de misturar isso no meio do paragrafo.";

    private PromptComposer() {
    }

    public static String compose(AgentContext context) {
        StringBuilder sb = new StringBuilder(BASE_PROMPT);

        SpecialistRegistry.resolve(context.connection().databaseProductName())
                .ifPresent(specialist -> appendSpecialist(sb, specialist));

        appendConnection(sb, context.connection());
        appendMetadata(sb, context.metadata());
        appendEditor(sb, context.editor());
        appendExecution(sb, context.execution());
        return sb.toString();
    }

    private static void appendSpecialist(StringBuilder sb, Specialist specialist) {
        sb.append("\n\n--- ").append(specialist.displayName()).append(" ---\n")
                .append(specialist.systemPromptFragment());
    }

    private static void appendConnection(StringBuilder sb, ConnectionContext connection) {
        if (connection.label() != null) {
            sb.append("\n\nConexao ativa: ").append(connection.label());
        }
        if (connection.databaseProductName() != null) {
            sb.append("\nBanco: ").append(connection.databaseProductName());
            if (connection.databaseVersion() != null) {
                sb.append(' ').append(connection.databaseVersion());
            }
        }
        if (connection.schema() != null) {
            sb.append("\nSchema ativo: ").append(connection.schema());
        }
    }

    private static void appendMetadata(StringBuilder sb, MetadataContext metadata) {
        if (metadata.tableCount() == 0) {
            return;
        }
        sb.append("\nSchema tem ").append(metadata.tableCount()).append(" tabela(s)");
        if (metadata.viewCount() > 0) {
            sb.append(" e ").append(metadata.viewCount()).append(" view(s)");
        }
        sb.append('.');
    }

    private static void appendEditor(StringBuilder sb, EditorContext editor) {
        if (editor.sql() == null || editor.sql().isBlank()) {
            return;
        }
        sb.append("\n\n").append(editor.hasSelection() ? "SQL selecionado no editor:" : "SQL atual no editor:")
                .append('\n').append(editor.sql());
    }

    private static void appendExecution(StringBuilder sb, ExecutionContext execution) {
        if (execution.lastSql() == null) {
            return;
        }
        sb.append("\n\nUltima execucao (").append(execution.lastSuccess() ? "sucesso" : "erro").append("): ")
                .append(execution.lastSql());
        if (!execution.lastSuccess() && execution.lastErrorMessage() != null) {
            sb.append("\nErro: ").append(execution.lastErrorMessage());
        }
    }
}
