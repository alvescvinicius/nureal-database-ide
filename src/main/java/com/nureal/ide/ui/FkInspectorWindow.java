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

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.filechooser.FileNameExtensionFilter;

import com.formdev.flatlaf.FlatLaf;

import com.nureal.ide.core.connection.ConexaoAtivaPort;
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
    static void open(Component ownerComponent, ConexaoAtivaPort connectionManager, String schema,
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

        FilterBar filterBar = buildFilterBar(refCols, localValues);

        JPanel center = new JPanel(new BorderLayout());
        JLabel status = new JLabel(" ");
        JPanel south = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 3));
        south.setOpaque(true);
        south.add(status);

        // Cores/bordas de filterBar/south/labels dependem do tema (GridTheme
        // e FlatLaf.isLafDark()) mas sao setadas com setBackground/setForeground/
        // setBorder — uma vez CHAMADAS, esses valores ficam CONGELADOS no
        // componente (Swing nunca reaplica sozinho um valor explicito, so o
        // que ainda e UIResource/nao-setado — mesma familia de bug ja vista e
        // corrigida em ResultGrid/SqlEditorPane/GridTheme). Centraliza a
        // logica aqui pra poder chamar de novo sempre que o L&F mudar (ver o
        // updateUI() do dialogo abaixo) — sem isto, o inspetor so refletia o
        // tema atualizado depois de FECHADO E REABERTO (bug relatado pelo
        // usuario: "eu preciso fechar e abrir de novo... queria que fosse
        // automatico").
        Runnable applyChrome = () -> applyChrome(filterBar.panel(), filterBar.filterLabel(), south, status);
        applyChrome.run();

        JDialog dialog = buildDialog(owner, table, applyChrome);
        List<JTextField> valueFields = filterBar.valueFields();

        Runnable runQuery = () -> runQuery(dialog, connectionManager, dialect, schema, metadataCache, scale, table,
                refCols, valueFields, center, status);

        filterBar.search().addActionListener(a -> runQuery.run());
        filterBar.clear().addActionListener(a -> {
            for (JTextField f : valueFields) {
                f.setText("");
            }
            runQuery.run();
        });
        for (JTextField f : valueFields) {
            f.addActionListener(a -> runQuery.run());
        }

        dialog.add(filterBar.panel(), BorderLayout.NORTH);
        dialog.add(center, BorderLayout.CENTER);
        dialog.add(south, BorderLayout.SOUTH);
        dialog.setSize(760, 480);
        dialog.setLocationRelativeTo(owner);
        dialog.setResizable(true);
        dialog.setVisible(true); // MODELESS: nao bloqueia, retorna na hora
        runQuery.run(); // consulta inicial, ja com o filtro pre-preenchido
    }

    private record FilterBar(JPanel panel, JLabel filterLabel, List<JTextField> valueFields, JButton search,
            JButton clear) {
    }

    /** Barra "coluna = valor" no topo, uma caixa por coluna referenciada, com "Buscar"/"Limpar". */
    private static FilterBar buildFilterBar(List<String> refCols, List<Object> localValues) {
        List<JTextField> valueFields = new ArrayList<>();
        JPanel filterBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 6));
        filterBar.setOpaque(true);
        JLabel filterLabel = new JLabel("Filtro:");
        filterBar.add(filterLabel);
        for (int i = 0; i < refCols.size(); i++) {
            Object val = (i < localValues.size()) ? localValues.get(i) : null;
            JLabel colLabel = new JLabel(refCols.get(i) + " =");
            filterBar.add(colLabel);
            JTextField field = new JTextField(val == null ? "" : val.toString(), 12);
            // Mesmo botao "limpar" (x) dos campos de busca do resto do app
            // (ver ConnectionsPanel/HistoryPanel/SavedQueriesPanel) — antes
            // este era o unico campo de filtro sem essa affordance.
            field.putClientProperty("JTextField.showClearButton", true);
            valueFields.add(field);
            filterBar.add(field);
        }
        JButton search = new JButton("Buscar");
        JButton clear = new JButton("Limpar (ver todos)");
        for (JButton btn : new JButton[] { search, clear }) {
            Buttons.styleSecondary(btn);
        }
        filterBar.add(search);
        filterBar.add(clear);
        return new FilterBar(filterBar, filterLabel, valueFields, search, clear);
    }

    /**
     * Cores/bordas de filterBar/south/labels dependem do tema (GridTheme e
     * FlatLaf.isLafDark()) mas sao setadas com setBackground/setForeground/
     * setBorder — uma vez CHAMADAS, esses valores ficam CONGELADOS no
     * componente (Swing nunca reaplica sozinho um valor explicito, so o que
     * ainda e UIResource/nao-setado — mesma familia de bug ja vista e
     * corrigida em ResultGrid/SqlEditorPane/GridTheme). Chamado de novo
     * sempre que o L&F mudar (ver {@link #buildDialog}) — sem isto, o
     * inspetor so refletia o tema atualizado depois de FECHADO E REABERTO
     * (bug relatado pelo usuario: "eu preciso fechar e abrir de novo...
     * queria que fosse automatico").
     */
    private static void applyChrome(JPanel filterBar, JLabel filterLabel, JPanel south, JLabel status) {
        // Mesmo fundo "barra de contexto" do breadcrumb do editor SQL (ver
        // SqlEditorPane#buildBreadcrumbBar).
        filterBar.setBackground(FlatLaf.isLafDark() ? new Color(0x1A, 0x1B, 0x1E) : new Color(0xEC, 0xEE, 0xF1));
        filterBar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, GridTheme.HEADER_BORDER));
        Typography.tertiary(filterLabel);
        south.setBackground(GridTheme.HEADER_BACKGROUND);
        south.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, GridTheme.HEADER_BORDER),
                BorderFactory.createEmptyBorder(0, 4, 0, 4)));
        Typography.tertiary(status);
    }

    /**
     * JDialog NAO e um JComponent (nao tem updateUI() proprio pra
     * sobrescrever — so o JRootPane dele tem). createRootPane() e o ponto de
     * extensao padrao do Swing pra isto: chamado UMA vez, na construcao do
     * proprio JDialog, e o JRootPane devolvido AQUI e quem recebe o
     * updateUI() em cascata do FlatLaf.updateUI() (que percorre a arvore de
     * componentes de toda janela aberta) — reaplica {@code applyChrome}
     * sempre que o L&F mudar em qualquer janela aberta (ver
     * FlatLaf.updateUI() em MainWindow#toggleTheme).
     */
    private static JDialog buildDialog(Window owner, String table, Runnable applyChrome) {
        JDialog dialog = new JDialog(owner, "Inspetor: " + table, Dialog.ModalityType.MODELESS) {
            private static final long serialVersionUID = 1L;

            @Override
            protected JRootPane createRootPane() {
                return new JRootPane() {
                    private static final long serialVersionUID = 1L;

                    @Override
                    public void updateUI() {
                        super.updateUI();
                        applyChrome.run();
                    }
                };
            }
        };
        dialog.setLayout(new BorderLayout());
        return dialog;
    }

    /** Roda (em background) o SELECT atual — com os filtros preenchidos ou sem nenhum — e reconstroi a grade. */
    private static void runQuery(JDialog dialog, ConexaoAtivaPort connectionManager, DatabaseDialect dialect,
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
                        ResultTableModel model = SqlExecutionEngine.createModel(rs);
                        SqlExecutionEngine.appendPage(model, rs, LIMIT);
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
