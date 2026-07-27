package com.nureal.ide.modulos.conexoes.dominio.contratos;
import com.nureal.ide.modulos.conexoes.infraestrutura.ConnectionManager;
import com.nureal.ide.modulos.conexoes.dominio.entidades.ConnectionProfile;

import java.sql.Connection;
import java.sql.SQLException;

import com.nureal.ide.modulos.dialeto.dominio.contratos.DatabaseDialect;

/**
 * O que o resto do aplicativo enxerga de uma conexao aberta: abrir, obter o
 * {@link Connection} JDBC ativo, saber se esta conectado, consultar o
 * dialeto/perfil em uso, e fechar. Extraido de {@link ConnectionManager}
 * (ver .specs/03-modulo-conexoes-e-seguranca.md, regra 2) para que outros
 * modulos (execucao de consulta, edicao de grade, assistente de DDL,
 * historico, chat de IA) dependam deste contrato, nao da implementacao
 * concreta.
 *
 * <p>Nesta primeira etapa da migracao, {@link ConnectionManager} passa a
 * implementar esta interface, mas os consumidores existentes (MainWindow,
 * ResultGrid, TableMetadataCache, ColumnMetadataResolver, FkInspectorWindow,
 * IdeStateAccessor/IdeContextAccessor, DescribeTableTool, ExecuteSqlTool,
 * Conexao, ConnectionEditDialog) ainda declaram o tipo concreto
 * {@code ConnectionManager} — sao mais de dez pontos de uso, e trocar todos
 * de uma vez sem poder rodar a IDE de verdade para validar seria um risco
 * desproporcional ao ganho imediato. A migracao desses consumidores para
 * declarar {@code ConexaoAtivaPort} em vez de {@code ConnectionManager} fica
 * para uma proxima etapa, um consumidor por vez.
 */
public interface ConexaoAtivaPort extends AutoCloseable {

    /** Abre uma conexao nova, fechando a anterior se existir. */
    void open(ConnectionProfile profile) throws SQLException;

    /** Conexao JDBC atualmente aberta, ou {@code null} se nao conectado. */
    Connection getConnection();

    boolean isConnected();

    DatabaseDialect dialect();

    /** Perfil usado na ultima chamada a {@link #open}, ou {@code null} se nunca conectado. */
    ConnectionProfile profile();

    @Override
    void close();
}
