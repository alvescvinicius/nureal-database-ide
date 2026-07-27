package com.nureal.ide.ui;
import com.nureal.ide.compartilhado.designsystem.GridTheme;

import com.nureal.ide.modulos.metadados.dominio.entidades.ColumnInfo;
import com.nureal.ide.modulos.metadados.dominio.entidades.SchemaForeignKey;
import com.nureal.ide.modulos.metadados.dominio.entidades.TableInfo;

import javax.swing.JPanel;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Canvas do Diagrama ER: uma caixa por tabela (nome + colunas, PK/FK
 * destacadas) e uma curva por chave estrangeira ligando a coluna de origem a
 * referenciada. Arrastar/zoom/pan sao geridos aqui, sem {@link javax.swing.JScrollPane}
 * — mais simples de acertar (uma unica transformacao {@code translate+scale}
 * de "espaco de diagrama" para "espaco de tela", ao inves de reconciliar
 * scroll de viewport COM escala) e e o mesmo modelo de interacao de
 * ferramentas de quadro branco/diagrama (arrastar o fundo pan, roda do mouse
 * zoom). E o PRIMEIRO componente de canvas customizado do app (nenhum outro
 * lugar desenha um grafo com Graphics2D) — ver {@code ErDiagramWindow} para a
 * janela nao-modal que hospeda isto.
 */
final class ErDiagramCanvas extends JPanel {

    private static final long serialVersionUID = 1L;

    private static final int TITLE_HEIGHT = 26;
    private static final int ROW_HEIGHT = 18;
    private static final int PADDING = 10;
    private static final int MIN_WIDTH = 170;
    private static final int MAX_WIDTH = 320;
    private static final double MIN_SCALE = 0.15;
    private static final double MAX_SCALE = 3.0;

    private final Font titleFont = SqlEditorPane.monospaceFont(13).deriveFont(Font.BOLD);
    private final Font rowFont = SqlEditorPane.monospaceFont(12);

    private final List<Node> nodes = new ArrayList<>();
    private final List<Edge> edges = new ArrayList<>();

    private double scale = 1.0;
    private double panX;
    private double panY;
    private String highlightText = "";
    private boolean fittedOnce;

    private Node dragNode;
    private double dragOffsetX;
    private double dragOffsetY;
    private java.awt.Point lastPanPoint;
    private boolean panning;

    private Consumer<Double> scaleListener;

    /**
     * Uma coluna dentro de uma caixa — {@code pk}/{@code fk} decidem o
     * destaque (ver {@link #drawNode}); {@code fk} e marcada DEPOIS da
     * construcao dos nos, ao processar {@code foreignKeys} (uma coluna so e
     * "FK" se aparecer como origem de alguma {@link SchemaForeignKey}).
     */
    private static final class Col {
        final String name;
        final String type;
        boolean pk;
        boolean fk;

        Col(String name, String type) {
            this.name = name;
            this.type = type;
        }
    }

    /** Uma caixa (tabela) do diagrama — posicao/tamanho sao MUTAVEIS (arrastar, layout automatico). */
    private static final class Node {
        final String table;
        final List<Col> columns;
        double x;
        double y;
        double width;
        double height;

        Node(String table, List<Col> columns) {
            this.table = table;
            this.columns = columns;
        }
    }

    /**
     * Uma linha do diagrama (uma chave estrangeira). {@code fromRow}/{@code
     * toRow} sao o INDICE da coluna correspondente dentro do no (para ancorar
     * a linha na altura exata da coluna, nao no meio da caixa) — {@code -1}
     * quando a coluna nao foi encontrada (fallback: ancora na barra de
     * titulo), o que so deveria acontecer se os metadados estiverem
     * inconsistentes entre {@code tables} e {@code foreignKeys}.
     */
    private static final class Edge {
        final Node from;
        final int fromRow;
        final Node to;
        final int toRow;

        Edge(Node from, int fromRow, Node to, int toRow) {
            this.from = from;
            this.fromRow = fromRow;
            this.to = to;
            this.toRow = toRow;
        }
    }

    ErDiagramCanvas(List<TableInfo> tables, List<SchemaForeignKey> foreignKeys,
            Map<String, Set<String>> primaryKeysByTable) {
        setFocusable(true);
        Map<String, Node> byName = new HashMap<>();
        for (TableInfo t : tables) {
            Set<String> pks = primaryKeysByTable.getOrDefault(t.name(), Set.of());
            List<Col> cols = new ArrayList<>();
            for (ColumnInfo c : t.columns()) {
                Col col = new Col(c.name(), c.type());
                col.pk = pks.contains(c.name());
                cols.add(col);
            }
            Node node = new Node(t.name(), cols);
            nodes.add(node);
            byName.put(t.name(), node);
        }
        for (SchemaForeignKey fk : foreignKeys) {
            Node from = byName.get(fk.fromTable());
            Node to = byName.get(fk.toTable());
            // Tabela referenciada FORA da lista carregada (outro schema, ou
            // uma FK "orfa" apontando pra algo que nao existe mais) — melhor
            // ignorar essa relacao em silencio do que quebrar o diagrama
            // inteiro por uma constraint incomum.
            if (from == null || to == null) {
                continue;
            }
            int n = Math.min(fk.fromColumns().size(), fk.toColumns().size());
            for (int i = 0; i < n; i++) {
                int fromRow = indexOfColumn(from, fk.fromColumns().get(i));
                int toRow = indexOfColumn(to, fk.toColumns().get(i));
                if (fromRow >= 0) {
                    from.columns.get(fromRow).fk = true;
                }
                edges.add(new Edge(from, fromRow, to, toRow));
            }
        }
        computeSizes();
        autoLayout();
        installInteraction();
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                // So no PRIMEIRO redimensionamento real (a janela ainda tem
                // width/height=0 durante a construcao) — chamar fitToView()
                // antes disso daria uma escala degenerada (divisao por ~0).
                if (!fittedOnce && getWidth() > 0 && getHeight() > 0) {
                    fittedOnce = true;
                    fitToView();
                }
            }
        });
    }

    private static int indexOfColumn(Node node, String columnName) {
        if (columnName == null) {
            return -1;
        }
        for (int i = 0; i < node.columns.size(); i++) {
            if (node.columns.get(i).name.equalsIgnoreCase(columnName)) {
                return i;
            }
        }
        return -1;
    }

    /** Mede o texto de cada caixa (nome + "coluna : tipo") uma unica vez, via uma imagem temporaria (nao depende do componente ja estar visivel/realizado). */
    private void computeSizes() {
        BufferedImage tmp = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = tmp.createGraphics();
        FontMetrics titleFm = g2.getFontMetrics(titleFont);
        FontMetrics rowFm = g2.getFontMetrics(rowFont);
        for (Node n : nodes) {
            int width = Math.max(MIN_WIDTH, titleFm.stringWidth(n.table) + PADDING * 2);
            for (Col c : n.columns) {
                String text = c.name + " : " + c.type;
                width = Math.max(width, rowFm.stringWidth(text) + PADDING * 2 + 26);
            }
            n.width = Math.min(width, MAX_WIDTH);
            n.height = TITLE_HEIGHT + Math.max(1, n.columns.size()) * ROW_HEIGHT + PADDING / 2.0;
        }
        g2.dispose();
    }

    int tableCount() {
        return nodes.size();
    }

    int relationshipCount() {
        return edges.size();
    }

    void onScaleChange(Consumer<Double> listener) {
        this.scaleListener = listener;
    }

    private void notifyScale() {
        if (scaleListener != null) {
            scaleListener.accept(scale);
        }
    }

    /**
     * Rearranja todas as caixas numa grade simples (ordem em que vieram do
     * schema), descartando qualquer posicao arrastada manualmente — chamado
     * na construcao e pelo botao "Reorganizar". Nao tenta nenhum algoritmo de
     * grafo mais esperto (ex.: minimizar cruzamento de linhas): uma grade
     * previsivel, combinada com as linhas curvas (ver {@link #drawEdge}), ja
     * deixa o diagrama legivel para o numero de tabelas tipico desta IDE; um
     * layout de grafo real fica para uma proxima versao se o volume de
     * tabelas exigir.
     */
    void autoLayout() {
        int n = nodes.size();
        if (n == 0) {
            return;
        }
        int cols = (int) Math.ceil(Math.sqrt(n));
        double maxW = 0;
        double maxH = 0;
        for (Node node : nodes) {
            maxW = Math.max(maxW, node.width);
            maxH = Math.max(maxH, node.height);
        }
        int gapX = 60;
        int gapY = 50;
        int i = 0;
        for (Node node : nodes) {
            int col = i % cols;
            int row = i / cols;
            node.x = gapX + col * (maxW + gapX);
            node.y = gapY + row * (maxH + gapY);
            i++;
        }
        repaint();
    }

    /** Ajusta zoom/posicao para todas as caixas caberem na area visivel atual. */
    void fitToView() {
        Rectangle2D b = computeBounds();
        double vw = Math.max(getWidth(), 200);
        double vh = Math.max(getHeight(), 200);
        double margin = 40;
        double sx = (vw - margin * 2) / b.getWidth();
        double sy = (vh - margin * 2) / b.getHeight();
        scale = clamp(Math.min(sx, sy), MIN_SCALE, MAX_SCALE);
        panX = margin - b.getX() * scale;
        panY = margin - b.getY() * scale;
        repaint();
        notifyScale();
    }

    void zoomBy(double factor) {
        zoomAround(new java.awt.Point(Math.max(getWidth(), 1) / 2, Math.max(getHeight(), 1) / 2), factor);
    }

    private void zoomAround(java.awt.Point screenPoint, double factor) {
        double newScale = clamp(scale * factor, MIN_SCALE, MAX_SCALE);
        double diagX = (screenPoint.x - panX) / scale;
        double diagY = (screenPoint.y - panY) / scale;
        panX = screenPoint.x - diagX * newScale;
        panY = screenPoint.y - diagY * newScale;
        scale = newScale;
        repaint();
        notifyScale();
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    void highlight(String text) {
        this.highlightText = (text == null) ? "" : text.trim().toLowerCase(Locale.ROOT);
        repaint();
    }

    private boolean matches(Node n) {
        return highlightText.isEmpty() || n.table.toLowerCase(Locale.ROOT).contains(highlightText);
    }

    private Rectangle2D computeBounds() {
        if (nodes.isEmpty()) {
            return new Rectangle2D.Double(0, 0, 400, 300);
        }
        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        for (Node n : nodes) {
            minX = Math.min(minX, n.x);
            minY = Math.min(minY, n.y);
            maxX = Math.max(maxX, n.x + n.width);
            maxY = Math.max(maxY, n.y + n.height);
        }
        return new Rectangle2D.Double(minX, minY, maxX - minX, maxY - minY);
    }

    private Point2D toDiagram(java.awt.Point p) {
        return new Point2D.Double((p.x - panX) / scale, (p.y - panY) / scale);
    }

    private Node hitTest(Point2D d) {
        for (int i = nodes.size() - 1; i >= 0; i--) {
            Node n = nodes.get(i);
            if (d.getX() >= n.x && d.getX() <= n.x + n.width && d.getY() >= n.y && d.getY() <= n.y + n.height) {
                return n;
            }
        }
        return null;
    }

    private void installInteraction() {
        MouseAdapter adapter = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                requestFocusInWindow();
                Point2D d = toDiagram(e.getPoint());
                Node hit = hitTest(d);
                if (hit != null) {
                    dragNode = hit;
                    dragOffsetX = d.getX() - hit.x;
                    dragOffsetY = d.getY() - hit.y;
                    // Traz pro topo da pilha de desenho (fica por cima das
                    // demais enquanto e arrastada) — mesma logica de qualquer
                    // editor de diagrama.
                    nodes.remove(hit);
                    nodes.add(hit);
                } else {
                    panning = true;
                    lastPanPoint = e.getPoint();
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                dragNode = null;
                panning = false;
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (dragNode != null) {
                    Point2D d = toDiagram(e.getPoint());
                    dragNode.x = d.getX() - dragOffsetX;
                    dragNode.y = d.getY() - dragOffsetY;
                    repaint();
                } else if (panning && lastPanPoint != null) {
                    panX += e.getX() - lastPanPoint.x;
                    panY += e.getY() - lastPanPoint.y;
                    lastPanPoint = e.getPoint();
                    repaint();
                }
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                Node hit = hitTest(toDiagram(e.getPoint()));
                setCursor(Cursor.getPredefinedCursor(hit != null ? Cursor.MOVE_CURSOR : Cursor.DEFAULT_CURSOR));
            }
        };
        addMouseListener(adapter);
        addMouseMotionListener(adapter);
        addMouseWheelListener(e -> {
            double factor = e.getPreciseWheelRotation() < 0 ? 1.12 : 1 / 1.12;
            zoomAround(e.getPoint(), factor);
        });
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.translate(panX, panY);
        g2.scale(scale, scale);
        paintDiagram(g2);
        g2.dispose();
    }

    /**
     * Desenha o diagrama inteiro (linhas por baixo, caixas por cima) no
     * {@link Graphics2D} JA posicionado/escalado pelo chamador — reaproveitado
     * tanto por {@link #paintComponent} (a visualizacao ao vivo, com
     * pan/zoom/realce de busca) quanto por {@link #renderFullImage} (export
     * PNG, sempre em escala 1:1 e sem realce/esmaecimento — "o diagrama
     * inteiro", nao o que esta recortado na janela agora).
     */
    private void paintDiagram(Graphics2D g2) {
        boolean anyHighlightActive = !highlightText.isEmpty();
        for (Edge e : edges) {
            boolean dim = anyHighlightActive && !matches(e.from) && !matches(e.to);
            drawEdge(g2, e, dim);
        }
        for (Node n : nodes) {
            boolean dim = anyHighlightActive && !matches(n);
            drawNode(g2, n, dim);
        }
    }

    private void drawNode(Graphics2D g2, Node n, boolean dim) {
        Composite old = g2.getComposite();
        if (dim) {
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.30f));
        }
        int x = (int) Math.round(n.x);
        int y = (int) Math.round(n.y);
        int w = (int) Math.round(n.width);
        int h = (int) Math.round(n.height);

        g2.setColor(GridTheme.ZEBRA_EVEN);
        g2.fillRoundRect(x, y, w, h, 10, 10);
        boolean matchedBySearch = !highlightText.isEmpty() && matches(n);
        g2.setColor(matchedBySearch ? GridTheme.HEADER_HIGHLIGHT_BORDER : GridTheme.HEADER_BORDER);
        g2.setStroke(new BasicStroke(matchedBySearch ? 2f : 1f));
        g2.drawRoundRect(x, y, w, h, 10, 10);

        g2.setFont(titleFont);
        g2.setColor(GridTheme.HEADER_FOREGROUND);
        g2.drawString(clip(n.table, g2.getFontMetrics(), w - PADDING * 2), x + PADDING, y + 18);
        g2.setColor(GridTheme.GRID_LINE);
        g2.drawLine(x + 1, y + TITLE_HEIGHT, x + w - 1, y + TITLE_HEIGHT);

        g2.setFont(rowFont);
        FontMetrics rowFm = g2.getFontMetrics();
        int rowY = y + TITLE_HEIGHT + ROW_HEIGHT - 5;
        for (Col c : n.columns) {
            String marker = c.pk ? "PK " : (c.fk ? "FK " : "");
            g2.setColor(c.pk ? GridTheme.COLOR_PRIMARY_KEY : (c.fk ? GridTheme.COLOR_FOREIGN_KEY : GridTheme.COLOR_TEXTUAL));
            String text = marker + c.name + " : " + c.type;
            g2.drawString(clip(text, rowFm, w - PADDING * 2), x + PADDING, rowY);
            rowY += ROW_HEIGHT;
        }
        g2.setComposite(old);
    }

    private static String clip(String text, FontMetrics fm, int maxWidth) {
        if (fm.stringWidth(text) <= maxWidth) {
            return text;
        }
        String ellipsis = "...";
        int ellipsisWidth = fm.stringWidth(ellipsis);
        StringBuilder sb = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (fm.stringWidth(sb.toString() + c) + ellipsisWidth > maxWidth) {
                break;
            }
            sb.append(c);
        }
        return sb + ellipsis;
    }

    private void drawEdge(Graphics2D g2, Edge e, boolean dim) {
        Composite old = g2.getComposite();
        if (dim) {
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.18f));
        }
        double fromCenterX = e.from.x + e.from.width / 2;
        double toCenterX = e.to.x + e.to.width / 2;
        boolean toIsRight = toCenterX >= fromCenterX;
        double fy = (e.fromRow >= 0)
                ? e.from.y + TITLE_HEIGHT + (e.fromRow + 0.5) * ROW_HEIGHT
                : e.from.y + TITLE_HEIGHT / 2.0;
        double ty = (e.toRow >= 0)
                ? e.to.y + TITLE_HEIGHT + (e.toRow + 0.5) * ROW_HEIGHT
                : e.to.y + TITLE_HEIGHT / 2.0;
        double fx = toIsRight ? e.from.x + e.from.width : e.from.x;
        double tx = toIsRight ? e.to.x : e.to.x + e.to.width;

        double ctrl = Math.max(30, Math.abs(tx - fx) * 0.35);
        double c1x = fx + (toIsRight ? ctrl : -ctrl);
        double c2x = tx + (toIsRight ? -ctrl : ctrl);

        Path2D path = new Path2D.Double();
        path.moveTo(fx, fy);
        path.curveTo(c1x, fy, c2x, ty, tx, ty);
        g2.setColor(GridTheme.COLOR_FOREIGN_KEY);
        g2.setStroke(new BasicStroke(1.3f));
        g2.draw(path);

        // Seta simples apontando para a tabela REFERENCIADA (lado "um" da
        // relacao 1:N) — direcao aproximada pela tangente final da curva
        // (ponto de controle mais proximo do destino), suficiente pra indicar
        // o sentido sem precisar resolver a derivada exata da bezier.
        double angle = Math.atan2(ty - fy, tx - c2x);
        drawArrowHead(g2, tx, ty, angle);
        g2.setComposite(old);
    }

    private void drawArrowHead(Graphics2D g2, double tx, double ty, double angle) {
        double size = 7;
        Path2D arrow = new Path2D.Double();
        arrow.moveTo(tx, ty);
        arrow.lineTo(tx - size * Math.cos(angle - Math.PI / 7), ty - size * Math.sin(angle - Math.PI / 7));
        arrow.lineTo(tx - size * Math.cos(angle + Math.PI / 7), ty - size * Math.sin(angle + Math.PI / 7));
        arrow.closePath();
        g2.fill(arrow);
    }

    /**
     * Renderiza o diagrama INTEIRO (nao so o que esta visivel na janela
     * agora) numa imagem nova, em escala 1:1, sem esmaecimento de busca —
     * usada pelo "Exportar PNG..." (ver {@code ErDiagramWindow}). Reaproveita
     * {@link #paintDiagram}, so que com uma transformacao propria (traduzida
     * para a origem do retangulo delimitador, sem pan/zoom da tela).
     */
    BufferedImage renderFullImage() {
        Rectangle2D b = computeBounds();
        int margin = 40;
        int w = Math.max(1, (int) Math.ceil(b.getWidth()) + margin * 2);
        int h = Math.max(1, (int) Math.ceil(b.getHeight()) + margin * 2);
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        Color bg = getBackground() != null ? getBackground() : Color.WHITE;
        g2.setColor(bg);
        g2.fillRect(0, 0, w, h);
        g2.translate(margin - b.getX(), margin - b.getY());
        String savedHighlight = highlightText;
        highlightText = ""; // export sempre "sem busca" — todas as tabelas em destaque total
        paintDiagram(g2);
        highlightText = savedHighlight;
        g2.dispose();
        return img;
    }
}
