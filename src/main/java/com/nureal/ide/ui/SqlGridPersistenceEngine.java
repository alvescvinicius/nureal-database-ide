package com.nureal.ide.ui;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.nureal.ide.core.dialect.DatabaseDialect;
import com.nureal.ide.core.log.AppLogger;

/**
 * Motor de persistencia das edicoes pendentes de uma grade de resultados:
 * aplica exclusoes, atualizacoes e insercoes numa UNICA transacao (tudo ou
 * nada) via {@code PreparedStatement}, delegando a construcao do SQL ao
 * {@link DatabaseDialect}.
 *
 * <p>Extraido de {@link GridEditController} (ver
 * {@code .specs/06-modulo-execucao-e-edicao-de-grade.md}): {@code
 * GridEditController} continua dono do rastreio de celulas
 * sujas/novas/excluidas (estado de sessao de edicao, sem JDBC); esta classe
 * concentra apenas o trecho que fala com o banco, sem estado de instancia,
 * para poder ser testada com um {@link Connection} fake em vez de um MySQL
 * real. Nenhuma mudanca de comportamento em relacao ao codigo original.
 */
final class SqlGridPersistenceEngine {

	private SqlGridPersistenceEngine() {
	}

	/**
	 * Resultado da aplicacao: quantas linhas foram inseridas/atualizadas/
	 * excluidas de fato, e as chaves geradas por auto-increment (linha do
	 * MODELO -&gt; valor gerado) para as insercoes cuja PK nao veio preenchida
	 * pelo usuario — quem chama e responsavel por escrever esses valores de
	 * volta no modelo (na EDT, com o listener de dirty-tracking suprimido).
	 */
	record Resultado(int inserted, int updated, int deleted, Map<Integer, Object> generatedKeys) {
	}

	/**
	 * Aplica tudo que esta pendente numa UNICA transacao: exclusoes, depois
	 * atualizacoes, depois insercoes. Qualquer erro desfaz TUDO (rollback) e
	 * relanca a excecao.
	 */
	static Resultado apply(Connection conn, DatabaseDialect dialect, EditableTarget target, ResultTableModel model,
			Set<Integer> deletedRows, Set<Integer> newRows, Map<Integer, Set<Integer>> dirtyCells,
			Map<Integer, Object[]> originalRow) throws SQLException {
		boolean prevAutoCommit = conn.getAutoCommit();
		int inserted = 0;
		int updated = 0;
		int deleted = 0;
		Map<Integer, Object> generatedKeys = new HashMap<>();
		try {
			conn.setAutoCommit(false);

			for (int row : deletedRows) {
				if (newRows.contains(row)) {
					continue; // nunca chegou a existir no banco, nao ha o que excluir
				}
				executeDelete(conn, dialect, model, target, originalRow, row);
				deleted++;
			}

			for (Map.Entry<Integer, Set<Integer>> e : dirtyCells.entrySet()) {
				int row = e.getKey();
				if (newRows.contains(row) || deletedRows.contains(row) || e.getValue().isEmpty()) {
					continue;
				}
				executeUpdate(conn, dialect, model, target, originalRow, row, e.getValue());
				updated++;
			}

			for (int row : newRows) {
				if (deletedRows.contains(row)) {
					continue; // descartada antes de ser salva
				}
				if (executeInsert(conn, dialect, model, target, row, generatedKeys)) {
					inserted++;
				}
			}

			conn.commit();
		} catch (SQLException ex) {
			safeRollback(conn);
			throw ex;
		} finally {
			try {
				conn.setAutoCommit(prevAutoCommit);
			} catch (SQLException ignore) {
				// conexao pode ja ter caido; nada a fazer
			}
		}
		return new Resultado(inserted, updated, deleted, generatedKeys);
	}

	private static void safeRollback(Connection conn) {
		try {
			conn.rollback();
		} catch (SQLException ex) {
			AppLogger.warning("Falha ao desfazer (rollback) alteracoes da grade", ex);
		}
	}

	private static void executeDelete(Connection conn, DatabaseDialect dialect, ResultTableModel model,
			EditableTarget target, Map<Integer, Object[]> originalRow, int row) throws SQLException {
		String sql = "DELETE FROM " + dialect.quoteIdentifier(target.table()) + whereClause(dialect, target, model);
		try (PreparedStatement ps = conn.prepareStatement(sql)) {
			bindPk(ps, model, target, originalRow, row, 1);
			ps.executeUpdate();
		}
	}

	private static void executeUpdate(Connection conn, DatabaseDialect dialect, ResultTableModel model,
			EditableTarget target, Map<Integer, Object[]> originalRow, int row, Set<Integer> dirtyColumns)
			throws SQLException {
		List<Integer> cols = new ArrayList<>(dirtyColumns);
		StringBuilder sql = new StringBuilder("UPDATE ").append(dialect.quoteIdentifier(target.table())).append(" SET ");
		for (int i = 0; i < cols.size(); i++) {
			if (i > 0) {
				sql.append(", ");
			}
			sql.append(dialect.quoteIdentifier(model.realColumnName(cols.get(i)))).append(" = ?");
		}
		sql.append(whereClause(dialect, target, model));
		try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
			int idx = 1;
			for (int col : cols) {
				ps.setObject(idx++, model.getValueAt(row, col));
			}
			bindPk(ps, model, target, originalRow, row, idx);
			ps.executeUpdate();
		}
	}

	/**
	 * @return {@code true} se havia ao menos uma coluna preenchida (senao nao
	 *         ha o que inserir — linha nova deixada totalmente em branco e so
	 *         descartada em silencio).
	 */
	private static boolean executeInsert(Connection conn, DatabaseDialect dialect, ResultTableModel model,
			EditableTarget target, int row, Map<Integer, Object> generatedKeysOut) throws SQLException {
		List<Integer> cols = new ArrayList<>();
		for (int col : target.editableColumns()) {
			if (model.getValueAt(row, col) != null) {
				cols.add(col);
			}
		}
		if (cols.isEmpty()) {
			return false;
		}
		StringBuilder sql = new StringBuilder("INSERT INTO ").append(dialect.quoteIdentifier(target.table())).append(" (");
		for (int i = 0; i < cols.size(); i++) {
			if (i > 0) {
				sql.append(", ");
			}
			sql.append(dialect.quoteIdentifier(model.realColumnName(cols.get(i))));
		}
		sql.append(") VALUES (");
		for (int i = 0; i < cols.size(); i++) {
			sql.append(i > 0 ? ", ?" : "?");
		}
		sql.append(')');
		try (PreparedStatement ps = conn.prepareStatement(sql.toString(), PreparedStatement.RETURN_GENERATED_KEYS)) {
			for (int i = 0; i < cols.size(); i++) {
				ps.setObject(i + 1, model.getValueAt(row, cols.get(i)));
			}
			ps.executeUpdate();
			Object generated = fetchGeneratedKey(ps, model, target, row);
			if (generated != null) {
				generatedKeysOut.put(row, generated);
			}
		}
		return true;
	}

	/**
	 * Se a tabela tem uma UNICA coluna de PK e ela nao foi preenchida pelo
	 * usuario (auto_increment tipico), busca o valor gerado pelo banco.
	 * {@code null} quando nao ha chave gerada para devolver (PK composta, PK
	 * ja preenchida pelo usuario, ou coluna sem auto-increment).
	 */
	private static Object fetchGeneratedKey(PreparedStatement ps, ResultTableModel model, EditableTarget target,
			int row) {
		if (target.primaryKeyColumns().size() != 1) {
			return null;
		}
		int pkCol = target.primaryKeyColumns().get(0);
		if (model.getValueAt(row, pkCol) != null) {
			return null; // usuario ja preencheu a PK na mao
		}
		try (ResultSet keys = ps.getGeneratedKeys()) {
			if (keys.next()) {
				return keys.getObject(1);
			}
		} catch (SQLException ex) {
			AppLogger.fine("Sem chave gerada para a linha inserida (normal se a PK nao for auto_increment)", ex);
		}
		return null;
	}

	/**
	 * Colunas de chave primaria nunca sao NULL por definicao (e a propria
	 * definicao de PRIMARY KEY em SQL) — sempre "= ?" para cada uma, ligadas
	 * por AND.
	 */
	private static String whereClause(DatabaseDialect dialect, EditableTarget target, ResultTableModel model) {
		StringBuilder sb = new StringBuilder(" WHERE ");
		List<Integer> pkCols = target.primaryKeyColumns();
		for (int i = 0; i < pkCols.size(); i++) {
			if (i > 0) {
				sb.append(" AND ");
			}
			sb.append(dialect.quoteIdentifier(model.realColumnName(pkCols.get(i)))).append(" = ?");
		}
		return sb.toString();
	}

	private static void bindPk(PreparedStatement ps, ResultTableModel model, EditableTarget target,
			Map<Integer, Object[]> originalRow, int row, int startIndex) throws SQLException {
		Object[] full = originalRow.get(row);
		if (full == null) {
			int cols = model.getColumnCount();
			full = new Object[cols];
			for (int c = 0; c < cols; c++) {
				full[c] = model.getValueAt(row, c);
			}
			originalRow.put(row, full);
		}
		int idx = startIndex;
		for (int pkCol : target.primaryKeyColumns()) {
			ps.setObject(idx++, full[pkCol]);
		}
	}
}
