package com.nureal.ide.ui;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.Locale;

import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JTable;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;

/**
 * "Clipboard" de UMA linha de coluna (Ctrl+C/Ctrl+V/"Duplicar") do assistente
 * de DDL ({@link DdlAssistantDialog}) — extraido do {@code Session} interno
 * (SPEC-0008 Etapa 3: arquivo tinha passado do limite de 1200 linhas que a
 * propria spec exige dividir). Le/copia uma linha de QUALQUER uma das duas
 * grades de coluna (a de "Colunas atuais", so-leitura, so no modo alterar; e
 * a de "Colunas novas", sempre editavel) e cola sempre como uma linha NOVA na
 * grade de colunas novas, com o Nome em branco — unico campo obrigatorio que
 * precisa ser diferente entre duas colunas.
 */
final class DdlColumnRowClipboard {

	/**
	 * Campos COMUNS de uma linha de coluna, capturados ao copiar. Nome NAO
	 * faz parte do snapshot de proposito: e o unico campo obrigatorio que
	 * precisa ser diferente em cada coluna, entao colar sempre deixa o Nome
	 * em branco, pronto para digitar, com todo o resto ja igual ao da linha
	 * copiada — pedido explicito do usuario ("copie uma linha e cole como
	 * uma nova mantendo os [valores] e mudando apenas o obrigatorio").
	 */
	record Snapshot(String type, String size, boolean nullable, boolean pk, boolean ai, String defaultValue,
			String comment) {
	}

	private final DefaultTableModel columnsModel;
	private final DefaultTableModel existingColumnsModel; // null no modo criar (sem grade "Colunas atuais")
	private final JTable newColumnsTable;
	private final boolean alterMode;

	private Snapshot copied;

	DdlColumnRowClipboard(DefaultTableModel columnsModel, DefaultTableModel existingColumnsModel,
			JTable newColumnsTable, boolean alterMode) {
		this.columnsModel = columnsModel;
		this.existingColumnsModel = existingColumnsModel;
		this.newColumnsTable = newColumnsTable;
		this.alterMode = alterMode;
	}

	/** Liga Ctrl+C, em qualquer uma das duas grades, a "copiar a linha selecionada". */
	void bindCopy(JTable table) {
		table.getInputMap(JComponent.WHEN_FOCUSED).put(
				KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.CTRL_DOWN_MASK), "ddl-copy-column-row");
		table.getActionMap().put("ddl-copy-column-row", new AbstractAction() {
			private static final long serialVersionUID = 1L;

			@Override
			public void actionPerformed(ActionEvent e) {
				if (table.isEditing()) {
					table.getCellEditor().stopCellEditing();
				}
				Snapshot snap = snapshot(table, table.getSelectedRow());
				if (snap != null) {
					copied = snap;
				}
			}
		});
	}

	/** Liga Ctrl+V, na grade de colunas NOVAS, a "colar a ultima linha copiada como uma coluna nova". */
	void bindPaste(JTable table) {
		table.getInputMap(JComponent.WHEN_FOCUSED).put(
				KeyStroke.getKeyStroke(KeyEvent.VK_V, InputEvent.CTRL_DOWN_MASK), "ddl-paste-column-row");
		table.getActionMap().put("ddl-paste-column-row", new AbstractAction() {
			private static final long serialVersionUID = 1L;

			@Override
			public void actionPerformed(ActionEvent e) {
				if (copied != null) {
					pasteAsNewColumn(copied);
				}
			}
		});
	}

	/** Copia a linha selecionada de {@code source} e ja cola direto como coluna nova — usado pelo botao "Duplicar". */
	void duplicate(JTable source) {
		Snapshot snap = snapshot(source, source.getSelectedRow());
		if (snap != null) {
			// Guarda tambem como ultima copia: um Ctrl+V logo depois repete a
			// mesma colagem, sem precisar copiar de novo.
			copied = snap;
			pasteAsNewColumn(snap);
		}
	}

	/**
	 * Le os campos comuns da linha {@code row} de {@code table} — reconhece
	 * se veio da grade "Colunas atuais" ({@code existingColumnsModel}:
	 * colunas Chave/Extra viram PK/AI por INFERENCIA, "PRI" e
	 * "auto_increment") ou da grade "Colunas novas" ({@code columnsModel}:
	 * PK/AI ja sao as proprias colunas). {@code null} se a linha nao existir
	 * ou a tabela nao for uma das duas conhecidas.
	 */
	private Snapshot snapshot(JTable table, int row) {
		if (row < 0 || row >= table.getRowCount()) {
			return null;
		}
		if (table.getModel() == existingColumnsModel) {
			String type = str(existingColumnsModel.getValueAt(row, 1));
			String size = str(existingColumnsModel.getValueAt(row, 2));
			boolean nullable = bool(existingColumnsModel.getValueAt(row, 3));
			String chave = str(existingColumnsModel.getValueAt(row, 4));
			String extra = str(existingColumnsModel.getValueAt(row, 5));
			String def = str(existingColumnsModel.getValueAt(row, 6));
			String comment = str(existingColumnsModel.getValueAt(row, 7));
			boolean pk = "PRI".equalsIgnoreCase(chave.trim());
			boolean ai = extra.toLowerCase(Locale.ROOT).contains("auto_increment");
			return new Snapshot(type, size, nullable, pk, ai, def, comment);
		}
		if (table.getModel() == columnsModel) {
			String type = str(columnsModel.getValueAt(row, 1));
			String size = str(columnsModel.getValueAt(row, 2));
			boolean nullable = bool(columnsModel.getValueAt(row, 3));
			boolean pk = bool(columnsModel.getValueAt(row, 4));
			boolean ai = bool(columnsModel.getValueAt(row, 5));
			String def = str(columnsModel.getValueAt(row, 6));
			String comment = str(columnsModel.getValueAt(row, 7));
			return new Snapshot(type, size, nullable, pk, ai, def, comment);
		}
		return null;
	}

	/**
	 * Acrescenta {@code snap} como uma linha NOVA em {@code columnsModel}
	 * (grade "Colunas novas a adicionar"), com o Nome em branco, e ja move
	 * selecao/edicao para a celula de Nome dessa linha, pronta pra digitar
	 * — o unico campo que o usuario ainda precisa preencher. No modo
	 * alterar, PK/AI sempre saem falsos: a propria grade ja nao oferece
	 * essas duas colunas para colunas novas em ALTER TABLE.
	 */
	private void pasteAsNewColumn(Snapshot snap) {
		boolean pk = !alterMode && snap.pk();
		boolean ai = !alterMode && snap.ai();
		columnsModel.addRow(new Object[] {
				"", snap.type(), snap.size(), snap.nullable(), pk, ai, snap.defaultValue(), snap.comment() });
		int newRow = columnsModel.getRowCount() - 1;
		SwingUtilities.invokeLater(() -> {
			newColumnsTable.requestFocusInWindow();
			newColumnsTable.setRowSelectionInterval(newRow, newRow);
			newColumnsTable.editCellAt(newRow, 0);
			Component editor = newColumnsTable.getEditorComponent();
			if (editor != null) {
				editor.requestFocusInWindow();
			}
		});
	}

	private static String str(Object v) {
		return v == null ? "" : v.toString();
	}

	private static boolean bool(Object v) {
		return v instanceof Boolean b && b;
	}
}
