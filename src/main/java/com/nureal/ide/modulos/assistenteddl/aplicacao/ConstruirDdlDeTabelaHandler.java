package com.nureal.ide.modulos.assistenteddl.aplicacao;

import com.nureal.ide.compartilhado.validacao.SqlIdentifiers;
import com.nureal.ide.modulos.assistenteddl.aplicacao.ConstruirDdlDeTabelaOutput.ErroDeValidacao;
import com.nureal.ide.modulos.dialeto.dominio.contratos.DatabaseDialect;
import com.nureal.ide.modulos.metadados.dominio.entidades.NewTableSpec;

import java.util.ArrayList;
import java.util.List;

/**
 * Orquestra a montagem do DDL (CREATE TABLE ou ALTER TABLE) a partir do que
 * ja foi coletado do formulario do assistente — delega toda a geracao de SQL
 * a {@link DatabaseDialect}, igual o {@code DdlAssistantDialog.buildStatements()}
 * de onde esta logica veio (ver .specs/07-modulo-assistente-ddl.md).
 */
public final class ConstruirDdlDeTabelaHandler {

    private final DatabaseDialect dialect;

    public ConstruirDdlDeTabelaHandler(DatabaseDialect dialect) {
        this.dialect = dialect;
    }

    public ConstruirDdlDeTabelaOutput executar(ConstruirDdlDeTabelaInput input) {
        if (input.alterMode()) {
            // Modify/drop ANTES de add: assim, remover uma FK/indice e criar
            // outro no lugar (mesmas colunas) na mesma execucao funciona sem
            // colisao de nome.
            List<String> statements = new ArrayList<>();
            statements.addAll(dialect.alterTableModifyStatements(input.alterTableName(), input.modifiedColumns(),
                    input.droppedColumns(), input.droppedForeignKeys(), input.droppedIndexes()));
            statements.addAll(dialect.alterTableAddStatements(input.alterTableName(), input.newColumns(),
                    input.foreignKeys(), input.indexes()));
            return ConstruirDdlDeTabelaOutput.sucesso(statements);
        }

        String tableName = input.tableName();
        if (tableName == null || tableName.isEmpty()) {
            return ConstruirDdlDeTabelaOutput.erro(ErroDeValidacao.NOME_INVALIDO, "Informe o nome da tabela.");
        }
        if (!SqlIdentifiers.isValid(tableName)) {
            return ConstruirDdlDeTabelaOutput.erro(ErroDeValidacao.NOME_INVALIDO,
                    "Nome de tabela invalido: \"" + tableName + "\". Use letras, numeros e _ (nao pode comecar com numero).");
        }
        if (input.tableNameJaExiste()) {
            return ConstruirDdlDeTabelaOutput.erro(ErroDeValidacao.NOME_DUPLICADO,
                    "Ja existe uma tabela chamada \"" + tableName + "\".");
        }
        if (input.newColumns().isEmpty()) {
            return ConstruirDdlDeTabelaOutput.erro(ErroDeValidacao.SEM_COLUNAS, "Adicione pelo menos uma coluna.");
        }
        NewTableSpec spec = new NewTableSpec(tableName, input.newColumns(), input.comment(),
                input.foreignKeys(), input.indexes());
        return ConstruirDdlDeTabelaOutput.sucesso(List.of(dialect.createTableStatement(spec)));
    }
}
