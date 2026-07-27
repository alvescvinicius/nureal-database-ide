package com.nureal.ide.ui;
import com.nureal.ide.compartilhado.designsystem.Buttons;
import com.nureal.ide.compartilhado.designsystem.Typography;
import com.nureal.ide.compartilhado.designsystem.GridTheme;

import com.nureal.ide.core.json.JsonParser;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.TreeSelectionModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Window;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * "EXPLAIN visual" — decodifica {@code EXPLAIN FORMAT=JSON <consulta>} (ver
 * {@code MainWindow#onExplain}) e mostra o plano como uma ARVORE navegavel
 * (JTree) em vez do JSON cru ou de uma tabela plana — fase 4 do
 * GAP_ANALYSIS_DBA_DEV.md: "descobrir por que uma consulta esta lenta sem
 * decifrar um JSON gigante a mao". Acesso de leitura de qualquer no mostra
 * seus atributos (custo, linhas examinadas, indice usado etc.) no painel a
 * direita; tabelas com {@code access_type=ALL} (table scan completo — o
 * sinal classico de "falta indice") ficam destacadas em amarelo (alerta de
 * performance, nao erro — ver Design System, secao 15) na propria arvore.
 *
 * NAO tenta modelar o schema OFICIAL do {@code EXPLAIN FORMAT=JSON} do MySQL
 * (que varia entre 5.7/8.0 e entre versoes de patch) — em vez disso caminha o
 * JSON de forma GENERICA (ver {@link #buildNode}): qualquer mapa cujos
 * valores sejam TODOS escalares vira "atributos" do no atual; qualquer mapa
 * ou lista de mapas aninhado vira um no FILHO. Isso deixa a arvore util e
 * robusta a variacoes de schema, ao custo de nao ter rotulos 100% "bonitos"
 * para todo tipo de no (so os mais comuns — tabela, bloco de consulta,
 * nested loop etc. — tem tradução dedicada, ver {@link #humanize}).
 */
final class ExplainDialog {

    private ExplainDialog() {
    }

    static void open(Component parent, String sql, String explainJson) {
        new Session(parent, sql, explainJson).show();
    }

    /** Rotulo + atributos (escalares) de um no da arvore — {@link #toString()} e o que o JTree exibe. */
    private static final class NodeInfo {
        final String label;
        final Map<String, Object> attributes;
        final boolean fullScan;

        NodeInfo(String label, Map<String, Object> attributes) {
            this.label = label;
            this.attributes = attributes;
            this.fullScan = "ALL".equalsIgnoreCase(String.valueOf(attributes.get("access_type")));
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private static final Map<String, String> KNOWN_LABELS = Map.ofEntries(
            Map.entry("query_block", "Bloco de consulta"),
            Map.entry("nested_loop", "Loop aninhado (nested loop join)"),
            Map.entry("grouping_operation", "Agrupamento (GROUP BY)"),
            Map.entry("ordering_operation", "Ordenacao (ORDER BY)"),
            Map.entry("duplicates_removal", "Remocao de duplicados (DISTINCT)"),
            Map.entry("materialized_from_subquery", "Subconsulta materializada"),
            Map.entry("union_result", "Uniao (UNION)"),
            Map.entry("attached_subqueries", "Subconsultas correlacionadas"),
            Map.entry("optimized_away_subqueries", "Subconsultas eliminadas pelo otimizador"),
            Map.entry("buffer_result", "Buffer de resultado"),
            Map.entry("table", "Tabela"));

    private static String humanize(String key) {
        if (key == null) {
            return "";
        }
        String known = KNOWN_LABELS.get(key);
        return known != null ? known : key.replace('_', ' ');
    }

    private static boolean isPureScalarMap(Map<?, ?> map) {
        for (Object v : map.values()) {
            if (v instanceof Map<?, ?> || v instanceof List<?>) {
                return false;
            }
        }
        return true;
    }

    private static boolean isScalarList(List<?> list) {
        for (Object v : list) {
            if (v instanceof Map<?, ?> || v instanceof List<?>) {
                return false;
            }
        }
        return true;
    }

    private static String joinScalars(List<?> list) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(list.get(i));
        }
        return sb.toString();
    }

    private static String deriveLabel(String keyName, Map<String, Object> attrs) {
        if (attrs.containsKey("table_name")) {
            StringBuilder sb = new StringBuilder(String.valueOf(attrs.get("table_name")));
            if (attrs.containsKey("access_type")) {
                sb.append("  [").append(attrs.get("access_type")).append("]");
            }
            if (attrs.containsKey("rows_examined_per_scan")) {
                sb.append("  ~").append(trimNumber(attrs.get("rows_examined_per_scan"))).append(" linha(s)");
            }
            if (attrs.containsKey("filtered")) {
                sb.append(", ").append(trimNumber(attrs.get("filtered"))).append("% filtradas");
            }
            return sb.toString();
        }
        if ("query_block".equals(keyName) && attrs.containsKey("select_id")) {
            return "Bloco de consulta #" + trimNumber(attrs.get("select_id"));
        }
        return keyName == null ? "Plano de execucao" : humanize(keyName);
    }

    private static String trimNumber(Object value) {
        if (value instanceof Double d) {
            if (d == Math.floor(d) && !d.isInfinite()) {
                return String.valueOf(d.longValue());
            }
            return String.valueOf(d);
        }
        return String.valueOf(value);
    }

    /**
     * Caminha um valor decodificado por {@link JsonParser} e monta o no da
     * arvore correspondente (ver javadoc da classe para a estrategia geral).
     */
    @SuppressWarnings("unchecked")
    private static DefaultMutableTreeNode buildNode(String keyName, Object value) {
        if (value instanceof Map<?, ?> rawMap) {
            Map<String, Object> map = (Map<String, Object>) rawMap;
            Map<String, Object> attrs = new LinkedHashMap<>();
            List<DefaultMutableTreeNode> children = new ArrayList<>();
            for (Map.Entry<String, Object> e : map.entrySet()) {
                String k = e.getKey();
                Object v = e.getValue();
                if (v instanceof Map<?, ?> m2) {
                    if (isPureScalarMap(m2)) {
                        for (Map.Entry<?, ?> e2 : m2.entrySet()) {
                            attrs.put(k + "." + e2.getKey(), e2.getValue());
                        }
                    } else {
                        children.add(buildNode(k, v));
                    }
                } else if (v instanceof List<?> list) {
                    if (list.isEmpty()) {
                        continue;
                    }
                    if (isScalarList(list)) {
                        attrs.put(k, joinScalars(list));
                    } else {
                        children.add(buildNode(k, v));
                    }
                } else {
                    attrs.put(k, v);
                }
            }
            DefaultMutableTreeNode node = new DefaultMutableTreeNode(new NodeInfo(deriveLabel(keyName, attrs), attrs));
            for (DefaultMutableTreeNode c : children) {
                node.add(c);
            }
            return node;
        }
        if (value instanceof List<?> list) {
            DefaultMutableTreeNode node = new DefaultMutableTreeNode(new NodeInfo(humanize(keyName), Map.of()));
            int i = 0;
            for (Object item : list) {
                node.add(buildNode(keyName + " #" + (++i), item));
            }
            return node;
        }
        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put(keyName, value);
        return new DefaultMutableTreeNode(new NodeInfo(humanize(keyName) + ": " + value, attrs));
    }

    private static final class Session {
        private final Window owner;
        private final String sql;
        private final String explainJson;
        private JDialog dialog;
        private JTextArea details;

        Session(Component parent, String sql, String explainJson) {
            this.owner = (DialogUtil.owner(parent) instanceof Window w) ? w : null;
            this.sql = sql;
            this.explainJson = explainJson;
        }

        void show() {
            dialog = new JDialog(owner, "Plano de execucao (EXPLAIN)", JDialog.ModalityType.MODELESS);
            dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
            dialog.setLayout(new BorderLayout());

            Object root;
            try {
                root = JsonParser.parse(explainJson);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(owner, "Nao foi possivel interpretar o plano (JSON invalido):\n"
                        + ex.getMessage(), "Plano de execucao", JOptionPane.ERROR_MESSAGE);
                return;
            }
            DefaultMutableTreeNode rootNode = buildNode(null, root);
            JTree tree = new JTree(rootNode);
            tree.setRootVisible(true);
            tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
            tree.setCellRenderer(new DefaultTreeCellRenderer() {
                private static final long serialVersionUID = 1L;

                @Override
                public Component getTreeCellRendererComponent(JTree t, Object value, boolean sel, boolean expanded,
                        boolean leaf, int row, boolean hasFocus) {
                    Component c = super.getTreeCellRendererComponent(t, value, sel, expanded, leaf, row, hasFocus);
                    if (value instanceof DefaultMutableTreeNode node && node.getUserObject() instanceof NodeInfo info
                            && info.fullScan && !sel) {
                        // Amber (GridTheme.HEADER_HIGHLIGHT_BORDER), nao vermelho
                        // (COLOR_LOGIC_FALSE): um table scan completo e um
                        // ALERTA de performance, nao um ERRO — o Design System
                        // reserva vermelho estritamente para erro/exclusao (ver
                        // DESIGN_SYSTEM.md, secao 15).
                        setForeground(GridTheme.HEADER_HIGHLIGHT_BORDER);
                    }
                    return c;
                }
            });
            for (int i = 0; i < tree.getRowCount(); i++) {
                tree.expandRow(i);
            }
            tree.addTreeSelectionListener(e -> {
                Object comp = tree.getLastSelectedPathComponent();
                if (comp instanceof DefaultMutableTreeNode node && node.getUserObject() instanceof NodeInfo info) {
                    showDetails(info);
                }
            });

            details = new JTextArea();
            details.setEditable(false);
            details.setFont(SqlEditorPane.monospaceFont(12));
            details.setLineWrap(true);
            details.setWrapStyleWord(true);
            details.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));

            JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                    new JScrollPane(tree), new JScrollPane(details));
            split.setResizeWeight(0.55);
            split.setDividerLocation(520);

            dialog.add(buildHeader(), BorderLayout.NORTH);
            dialog.add(split, BorderLayout.CENTER);

            // Seleciona a raiz de saida (mostra pelo menos algo no painel de
            // detalhes antes de qualquer clique do usuario).
            if (rootNode.getUserObject() instanceof NodeInfo rootInfo) {
                showDetails(rootInfo);
            }

            dialog.setSize(1000, 650);
            dialog.setLocationRelativeTo(owner);
            dialog.setVisible(true);
        }

        private void showDetails(NodeInfo info) {
            if (info.attributes.isEmpty()) {
                details.setText("(sem atributos proprios — veja os nos filhos)");
                return;
            }
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, Object> e : info.attributes.entrySet()) {
                sb.append(e.getKey()).append(": ").append(e.getValue()).append('\n');
            }
            details.setText(sb.toString());
            details.setCaretPosition(0);
        }

        private JComponent buildHeader() {
            JLabel sqlLabel = new JLabel("  " + clip(sql, 140));
            sqlLabel.setToolTipText(sql);
            Typography.tertiary(sqlLabel);

            JLabel legend = new JLabel("Amarelo = table scan completo (ALL) — considere um indice.");
            Typography.tertiary(legend);

            JButton rawJson = new JButton("Ver JSON bruto");
            Buttons.styleSecondary(rawJson);
            rawJson.addActionListener(a -> showRawJson());

            JPanel top = new JPanel(new BorderLayout());
            top.setBorder(BorderFactory.createEmptyBorder(8, 4, 6, 8));
            JPanel left = new JPanel(new BorderLayout());
            left.add(sqlLabel, BorderLayout.NORTH);
            left.add(legend, BorderLayout.SOUTH);
            top.add(left, BorderLayout.CENTER);
            top.add(rawJson, BorderLayout.EAST);
            return top;
        }

        private void showRawJson() {
            JTextArea area = new JTextArea(explainJson, 24, 70);
            area.setEditable(false);
            area.setFont(SqlEditorPane.monospaceFont(12));
            area.setLineWrap(false);
            JScrollPane scroll = new JScrollPane(area);
            scroll.setPreferredSize(new Dimension(760, 480));
            JOptionPane.showMessageDialog(dialog, scroll, "EXPLAIN FORMAT=JSON — bruto",
                    JOptionPane.PLAIN_MESSAGE);
        }

        private static String clip(String text, int max) {
            String oneLine = text.replaceAll("\\s+", " ").trim();
            return oneLine.length() <= max ? oneLine : oneLine.substring(0, max - 3) + "...";
        }
    }
}
