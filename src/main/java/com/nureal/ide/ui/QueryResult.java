package com.nureal.ide.ui;

import javax.swing.table.DefaultTableModel;

/**
 * Resultado de um statement: grade (model != null) ou mensagem (update/erro).
 */
record QueryResult(String title, String sql, DefaultTableModel model, String message, boolean error,
		long execMs, long fetchMs, MainWindow.ResultCursor cursor) {
	static QueryResult grid(String title, String sql, DefaultTableModel model, long execMs, long fetchMs,
			MainWindow.ResultCursor cursor) {
		return new QueryResult(title, sql, model, null, false, execMs, fetchMs, cursor);
	}

	static QueryResult message(String title, String sql, String message, boolean error, long execMs) {
		return new QueryResult(title, sql, null, message, error, execMs, 0L, null);
	}
}
