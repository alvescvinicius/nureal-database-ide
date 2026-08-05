package com.nureal.ide.ui;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.swing.table.DefaultTableModel;

import com.nureal.ide.core.log.AppLogger;

/**
 * Motor de execucao de um lote de instrucoes SQL contra uma conexao JDBC:
 * roda cada instrucao em sequencia, para na primeira que der erro, e monta o
 * {@link QueryResult} de cada uma (grade paginada ou contagem de linhas
 * afetadas). Tambem concentra o mapeamento JDBC -&gt; {@link ResultTableModel}
 * (cabecalhos, tipos, metadados de coluna) e a paginacao sob demanda via
 * {@link ResultCursor}.
 *
 * <p>Extraido de {@code MainWindow} (ver
 * {@code .specs/06-modulo-execucao-e-edicao-de-grade.md}): era o unico
 * trecho da janela principal que falava JDBC cru diretamente. Nenhuma
 * mudanca de comportamento em relacao ao codigo original — apenas
 * isolamento numa classe propria, sem estado de instancia, para poder ser
 * testado e reaproveitado sem depender de {@code MainWindow}.
 */
final class SqlExecutionEngine {

	static final int PAGE_SIZE = 200;

	private SqlExecutionEngine() {
	}

	/**
	 * Roda cada instrucao em sequencia (chamado a partir da thread de fundo de
	 * um {@code SwingWorker}) — para na primeira que der erro. {@code
	 * onStatementChange} e chamado com o {@link Statement} recem-criado antes
	 * de executa-lo (permite cancelamento — ver
	 * {@code ResultsAreaController#cancelExecution}) e de novo com
	 * {@code null} assim que a instrucao termina ou falha.
	 */
	static List<QueryResult> executeStatements(Connection conn, List<String> statements,
			Supplier<Boolean> isCancelled, Consumer<Statement> onStatementChange) {
		List<QueryResult> results = new ArrayList<>();
		for (int i = 0; i < statements.size(); i++) {
			if (isCancelled.get()) {
				break;
			}
			String sql = statements.get(i);
			int n = i + 1;
			// Kind calculado ANTES de rodar (so olha o texto): usado tanto no
			// titulo de sucesso quanto no de erro, pra uma instrucao que falha
			// ainda mostrar um nome reconhecivel (ex.: "DELETE log_acesso" em
			// vermelho) em vez do generico "Erro N" sempre que possivel.
			SqlStatementLabel.Kind kind = SqlStatementLabel.kindOf(sql);
			long t0 = System.nanoTime();
			Statement st = null;
			try {
				st = conn.createStatement();
				// cursor do servidor: busca em lotes do tamanho da pagina
				st.setFetchSize(PAGE_SIZE);
				onStatementChange.accept(st);
				boolean hasResultSet = st.execute(sql);
				long execMs = (System.nanoTime() - t0) / 1_000_000L;
				results.add(buildStatementResult(st, sql, n, kind, hasResultSet, execMs));
			} catch (SQLException ex) {
				if (st != null) {
					try {
						st.close();
					} catch (SQLException ignore) {
						// ignora
					}
				}
				long execMs = (System.nanoTime() - t0) / 1_000_000L;
				String title = SqlStatementLabel.title(sql, kind, "Erro " + n);
				results.add(QueryResult.message(title, sql, "Erro: " + ex.getMessage(), true, execMs));
				break;
			} finally {
				onStatementChange.accept(null);
			}
		}
		return results;
	}

	/** Monta o {@link QueryResult} de UMA instrucao ja executada com sucesso (grade de linhas OU contagem afetada). */
	private static QueryResult buildStatementResult(Statement st, String sql, int n, SqlStatementLabel.Kind kind,
			boolean hasResultSet, long execMs) throws SQLException {
		if (hasResultSet) {
			ResultSet rs = st.getResultSet();
			// try/catch explicito (nao so confiar no fechamento em cascata
			// de rs quando o CHAMADOR fecha st no catch dele, ver
			// #executeStatements): se createModel/appendPage lancar no meio
			// da leitura, rs nunca era fechado por ESTE metodo — funcionava
			// na pratica com drivers JDBC que fecham ResultSets abertos ao
			// fechar o Statement pai (MySQL Connector/J faz isso), mas nao e
			// garantido pela API, e nenhum ResultCursor seria criado pra
			// rastrear esse rs em openCursors — achado numa auditoria
			// pedida pelo usuario.
			try {
				ResultTableModel model = createModel(rs);
				long t1 = System.nanoTime();
				int read = appendPage(model, rs, PAGE_SIZE);
				long fetchMs = (System.nanoTime() - t1) / 1_000_000L;
				boolean hasMore = read == PAGE_SIZE;
				ResultCursor cursor = null;
				if (hasMore) {
					cursor = new ResultCursor(st, rs);
				} else {
					rs.close();
					st.close();
				}
				String title = SqlStatementLabel.title(sql, kind, "Resultado " + n);
				return QueryResult.grid(title, sql, model, execMs, fetchMs, cursor);
			} catch (SQLException | RuntimeException ex) {
				try {
					rs.close();
				} catch (SQLException ignore) {
					// ignora
				}
				throw ex;
			}
		}
		int updated = st.getUpdateCount();
		st.close();
		String title = SqlStatementLabel.title(sql, kind, "Comando " + n);
		return QueryResult.message(title, sql, updated + " linha(s) afetada(s)", false, execMs);
	}

	/**
	 * Cria o modelo (cabecalhos + tipos + origem real + tipo SQL de cada coluna)
	 * para uma consulta. Visibilidade de pacote: reaproveitado por
	 * {@link FkInspectorWindow} para montar a grade do Inspetor Flutuante de FK
	 * com o MESMO caminho usado pelas abas de resultado normais.
	 */
	static ResultTableModel createModel(ResultSet rs) throws SQLException {
		ResultSetMetaData md = rs.getMetaData();
		int cols = md.getColumnCount();
		Vector<String> names = new Vector<>();
		Class<?>[] types = new Class<?>[cols];
		String[] sourceTables = new String[cols];
		String[] realColumnNames = new String[cols];
		String[] sqlTypeNames = new String[cols];
		ResultTableModel.ColumnJdbcMeta[] jdbcMeta = new ResultTableModel.ColumnJdbcMeta[cols];
		for (int i = 1; i <= cols; i++) {
			names.add(md.getColumnLabel(i));
			types[i - 1] = resolveColumnClass(md, i);
			// Tabela/coluna "reais" de origem (quando o driver informa) — usadas
			// so para casar a coluna do resultado com FKs do schema (indicador
			// no cabecalho). Podem vir vazias para expressoes/funcoes/JOINs
			// complexos; nesse caso simplesmente nao mostramos o indicador.
			try {
				sourceTables[i - 1] = md.getTableName(i);
				realColumnNames[i - 1] = md.getColumnName(i);
			} catch (SQLException ignore) {
				sourceTables[i - 1] = null;
				realColumnNames[i - 1] = null;
			}
			// Nome do tipo SQL real (ex.: "VARCHAR", "BIGINT", "JSON", "TIMESTAMP")
			// — usado pelo RendererFactory para colorir/alinhar por tipo.
			try {
				sqlTypeNames[i - 1] = md.getColumnTypeName(i);
			} catch (SQLException ignore) {
				sqlTypeNames[i - 1] = null;
			}
			jdbcMeta[i - 1] = readJdbcMeta(md, i);
		}
		return new ResultTableModel(names, types, sourceTables, realColumnNames, sqlTypeNames, jdbcMeta);
	}

	/**
	 * Le os metadados de coluna que o driver ja entrega junto com o
	 * ResultSetMetaData (sem nenhuma consulta extra ao banco): nulabilidade,
	 * precisao, escala, tamanho de exibicao e auto-increment. Cada chamada e
	 * protegida individualmente porque alguns drivers/tipos lancam SQLException
	 * para campos que nao fazem sentido (ex.: escala de uma coluna texto) em vez de
	 * simplesmente devolver 0.
	 */
	private static ResultTableModel.ColumnJdbcMeta readJdbcMeta(ResultSetMetaData md, int col) {
		boolean nullable = true;
		int precision = 0;
		int scale = 0;
		int displaySize = 0;
		boolean autoIncrement = false;
		try {
			nullable = md.isNullable(col) != ResultSetMetaData.columnNoNulls;
		} catch (SQLException ignore) {
			// mantem o padrao (nullable)
		}
		try {
			precision = md.getPrecision(col);
		} catch (SQLException ignore) {
			// mantem 0
		}
		try {
			scale = md.getScale(col);
		} catch (SQLException ignore) {
			// mantem 0
		}
		try {
			displaySize = md.getColumnDisplaySize(col);
		} catch (SQLException ignore) {
			// mantem 0
		}
		try {
			autoIncrement = md.isAutoIncrement(col);
		} catch (SQLException ignore) {
			// mantem false
		}
		return new ResultTableModel.ColumnJdbcMeta(nullable, precision, scale, displaySize, autoIncrement);
	}

	private static Class<?> resolveColumnClass(ResultSetMetaData md, int col) {
		try {
			return Class.forName(md.getColumnClassName(col));
		} catch (Exception ex) {
			AppLogger.fine("Nao foi possivel resolver a classe da coluna via metadata; usando Object", ex);
			return Object.class;
		}
	}

	/**
	 * Anexa ate {@code max} linhas do ResultSet ao modelo; retorna quantas leu.
	 * Visibilidade de pacote: tambem usado por {@link FkInspectorWindow}.
	 */
	static int appendPage(DefaultTableModel model, ResultSet rs, int max) throws SQLException {
		int cols = model.getColumnCount();
		int read = 0;
		while (read < max && rs.next()) {
			Vector<Object> row = new Vector<>(cols);
			for (int i = 1; i <= cols; i++) {
				row.add(rs.getObject(i));
			}
			model.addRow(row);
			read++;
		}
		return read;
	}

	/** Cursor aberto (Statement + ResultSet) para paginacao sob demanda. */
	static final class ResultCursor {
		final Statement st;
		final ResultSet rs;
		boolean exhausted;

		ResultCursor(Statement st, ResultSet rs) {
			this.st = st;
			this.rs = rs;
		}

		void close() {
			try {
				rs.close();
			} catch (SQLException ignore) {
				// ignora
			}
			try {
				st.close();
			} catch (SQLException ignore) {
				// ignora
			}
		}
	}
}
