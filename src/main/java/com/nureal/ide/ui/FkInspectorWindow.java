package com.nureal.ide.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.FlowLayout;
import java.awt.Window;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntUnaryOperator;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.filechooser.FileNameExtensionFilter;

import com.nureal.ide.core.connection.ConnectionManager;
import com.nureal.ide.core.dialect.DatabaseDialect;
import com.nureal.ide.core.export.ExcelExporter;
import com.nureal.ide.core.metadata.model.ForeignKeyInfo;

/**
 * Inspetor Flutuante de Chave Estrangeira: janela NAO-MODAL (o usuario nunca
 * precisa fecha-la para continuar trabalhando no editor/resultados de tras —
 * ver {@code Dialog.ModalityType.MODELESS}), que mostra os dados da tabela de
 * ORIGEM de uma FK (ex.: clicar com o botao direito no {@code 5} da coluna
 * {@code cliente_id} e escolher "Visualizar Origem" abre o registro do
 * cliente 5 direto de {@code clientes}), sem precisar abrir uma aba nova nem
 * perder o foco de onde o usuario estava.
 * <p>
 * Livre movimentacao/redimensionamento: e um {@link JDialog} comum (o proprio
 * SO cuida de arrastar/redimensionar/mover entre monitores, sem codigo extra
 * aqui). Cada clique em "Visualizar Origem" abre uma instancia NOVA e
 * independente — da pra ter varios inspetores abertos ao mesmo tempo,
 * inclusive um inspetor aberto A PARTIR de outro inspetor (a grade interna e
 * um {@link ResultGrid} de verdade, com o MESMO menu de contexto — clicar
 * numa FK dentro do inspetor abre mais um inspetor, recursivamente).
 * <p>
 * Filtro dinamico: a barra no topo mostra "coluna = valor" (o valor vindo da
 * celula clicada) numa caixa de texto por coluna referenciada — o usuario
 * pode apagar/trocar o valor ou limpar tudo (mostra os primeiros
 * {@value #LIMIT} registros da tabela, sem filtro) sem afetar a tela
 * principal da IDE.
 */
final class FkInspectorWindow {

    private static final int LIMIT = 200;

    private FkInspectorWindow() {
    }

    /**
     * @param ownerComponent   componente de onde a acao partiu — a janela dona de
     *                         verdade e resolvida a partir dele (ver {@link DialogUtil#owner};
     *                         o inspetor fica associado a ela, mas nao a bloqueia)
     * @param connectionManager conexao ativa de onde a FK foi clicada
     * @param schema           schema atual (para o cache de metadados da grade interna)
     * @param metadataCache    MESMO cache compartilhado do resto da sessao (ver {@code MainWindow})
     * @param scale            funcao de escala de UI (zoom), mesma do resto da janela
     * @param fk               a chave estrangeira clicada (tabela/colunas referenciadas)
     * @param localValues      valor de cada coluna LOCAL da FK nesta linha, na MESMA
     *                         ordem de {@code fk.columns()}/{@code fk.referencedColumns()}
     *                         (pode conter {@code null} para colunas que o SELECT nao trouxe)
     */
    static void open(Component ownerComponent, ConnectionManager connectionManager, String schema,
            TableMetadataCache metadataCache, IntUnaryOperator scale, ForeignKeyInfo fk, List<Object> localValues) {
        if (connectionManager == null || !connectionManager.isConnected()) {
            JOptionPane.showMessageDialog(ownerComponent, "Conexao fechada — nao e possivel abrir o inspetor.",
                    "Visualizar Origem", JOptionPane.WARNING_MESSAGE);
            return;
        }
        // DialogUtil.owner ja devolve a JANELA de nivel superior quando existe
        // (so cai de volta no proprio componente se, por algum motivo, ele
        // ainda nao estiver em nenhuma janela) — aqui precisamos de fato de
        // um Window para o construtor do JDialog abaixo.
        Window owner = (ownerComponent instanceof Window w) ? w
                : (ownerComponent == null ? null : javax.swing.SwingUtilities.getWindowAncestor(ownerComponent));
        DatabaseDialect dialect = connectionManager.dialect();
        String table = fk.referencedTable();
        List<String> refCols = fk.referencedColumns();

        JDialog dialog = new JDialog(owner, "Inspetor: " + table, Dialog.ModalityType.MODELESS);
        dialog.setLayout(new BorderLayout());

        List<JTextField> valueFields = new ArrayList<>();
        JPanel filterBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 6));
        JLabel filterLabel = new JLabel("Filtro:");
        filterLabel.setForeground(new Color(0x6B7280));
        filterBar.add(filterLabel);
        for (int i = 0; i < refCols.size(); i++) {
            Object val = (i < localValues.size()) ? localValues.get(i) : null;
            JLabel colLabel = new JLabel(refCols.get(i) + " =");
            filterBar.add(colLabel);
            JTextField field = new JTextField(val == null ? "" : val.toString(), 12);
            valueFields.add(field);
            filterBar.add(field);
        }
        JButton search = new JButton("Buscar");
        JButton clear = new JButton("Limpar (ver todos)");
        filterBar.add(search);
        filterBar.add(clear);

        JPanel center = new JPanel(new BorderLayout());
        JLabel status = new JLabel(" ");
        status.setForeground(new Color(0x6B7280));
        JPanel south = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 3));
        south.add(status);

        Runnable runQuery = () -> runQuery(dialog, connectionManager, dialect, schema, metadataCache, scale, table,
                refCols, valueFields, center, status);

        search.addActionListener(a -> runQuery.run());
        clear.addActionListener(a -> {
            for (JTextField f : valueFields) {
                f.setText("");
            }
            runQuery.run();
        });
        for (JTextField f : valueFields) {
            f.addActionListener(a -> runQuery.run());
        }

        dialog.add(filterBar, BorderLayout.NORTH);
        dialog.add(center, BorderLayout.CENTER);
        dialog.add(south, BorderLayout.SOUTH);
        dialog.setSize(760, 480);
        dialog.setLocationRelativeTo(owner);
        dialog.setResizable(true);
        dialog.setVisible(true); // MODELESS: nao bloqueia, retorna na hora
        runQuery.run(); // consulta inicial, ja com o filtro pre-preenchido
    }

    /** Roda (em background) o SELECT atual — com os filtros preenchidos ou sem nenhum — e reconstroi a grade. */
    private static void runQuery(JDialog dialog, ConnectionManager connectionManager, DatabaseDialect dialect,
            String schema, TableMetadataCache metadataCache, IntUnaryOperator scale, String table,
            List<String> refCols, List<JTextField> valueFields, JPanel center, JLabel status) {
        List<String> conditions = new ArrayList<>();
        List<String> values = new ArrayList<>();
        for (int i = 0; i < refCols.size(); i++) {
            String text = valueFields.get(i).getText().trim();
            if (!text.isEmpty()) {
                conditions.add(dialect.quoteIdentifier(refCols.get(i)) + " = ?");
                values.add(text);
            }
        }
        String sql = "SELECT * FROM " + dialect.quoteIdentifier(table)
                + (conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions))
                + " LIMIT " + LIMIT;

        status.setText(" Carregando...");
        new SwingWorker<ResultTableModel, Void>() {
            @Override
            protected ResultTableModel doInBackground() throws Exception {
                Connection conn = connectionManager.getConnection();
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    for (int i = 0; i < values.size(); i++) {
                        ps.setObject(i + 1, values.get(i));
                    }
                    try (ResultSet rs = ps.executeQuery()) {
                        ResultTableModel model = MainWindow.createModel(rs);
                        MainWindow.appendPage(model, rs, LIMIT);
                        return model;
                    }
                }
            }

            @Override
            protected void done() {
                try {
                    ResultTableModel model = get();
                    Runnable exportExcel = () -> exportSingleTable(dialog, model, table);
                    ResultGrid grid = new ResultGrid(model, connectionManager, schema, metadataCache, exportExcel,
                            scale);
                    center.removeAll();
                    center.add(grid.asComponent(), BorderLayout.CENTER);
                    center.revalidate();
                    center.repaint();
                    int rows = model.getRowCount();
                    status.setText(" " + rows + " linha(s)" + (rows >= LIMIT ? " (limite de " + LIMIT + " atingido)" : ""));
                } catch (Exception ex) {
                    Throwable cause = (ex.getCause() != null) ? ex.getCause() : ex;
                    status.setText(" Erro ao consultar: " + cause.getMessage());
                }
            }
        }.execute();
    }

    /** "Exportar Excel..." dentro do inspetor — exporta so o que esta na grade dele (1 aba). */
    private static void exportSingleTable(JDialog owner, ResultTableModel model, String sheetName) {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Exportar " + sheetName + " para Excel");
        fc.setSelectedFile(new java.io.File(sheetName + ".xlsx"));
        fc.setFileFilter(new FileNameExtensionFilter("Planilha Excel (*.xlsx)", "xlsx"));
        if (fc.showSaveDialog(owner) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        java.io.File file = fc.getSelectedFile();
        if (!file.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".xlsx")) {
            file = new java.io.File(file.getParentFile(), file.getName() + ".xlsx");
        }
        java.io.File target = file;
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                ExcelExporter.export(List.of(new ExcelExporter.TableSheet(sheetName, model)), target);
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(owner, "Falha ao exportar: " + ex.getMessage(), "Exportar",
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }
}
