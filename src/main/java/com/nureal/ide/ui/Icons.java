package com.nureal.ide.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import javax.swing.Icon;

import com.formdev.flatlaf.extras.FlatSVGIcon;

/**
 * Biblioteca de icones da Nureal Database IDE — design system minimalista
 * (grid 20x20, stroke 2px, raio de canto 3px; ver {@link IconTheme}).
 *
 * Todo icone e identificado por CONCEITO, nunca por desenho:
 * <pre>    Icons.get(IconType.RUN)</pre>
 * Internamente, cada {@link IconType} e renderizado a partir de um numero
 * pequeno de PRIMITIVAS geometricas reutilizaveis (quadrado, retangulo,
 * retangulo arredondado, linha, circulo, ponto, chevron, seta, triangulo,
 * barra, + e -). Nenhum icone desenha um objeto realista (sem cilindros, sem
 * pastas detalhadas, sem chaves ilustradas, sem oculos/olho, sem impressora,
 * sem planilha do Excel) — tudo e forma geometrica pura, monocromatica.
 *
 * Os icones sao vetoriais (Java2D), entao funcionam em qualquer tamanho/DPI:
 * nenhuma dimensao e fixa, tudo e proporcional ao {@code size} pedido.
 */
final class Icons {

    private Icons() {
    }

    // ====================================================================
    // API publica — SEMPRE pelo conceito (IconType), nunca por nome de forma.
    // ====================================================================

    /** Icone no tamanho e cor padrao do design system para este conceito. */
    static Icon get(IconType type) {
        return get(type, IconTheme.DEFAULT_SIZE, IconTheme.colorFor(type));
    }

    /** Icone no tamanho pedido, com a cor padrao do design system para este conceito. */
    static Icon get(IconType type, int size) {
        return get(type, size, IconTheme.colorFor(type));
    }

    /** Icone totalmente customizado (tamanho e cor explicitos — ex.: status dinamico). */
    static Icon get(IconType type, int size, Color color) {
    	// Intercepta o REFRESH para usar o novo arquivo SVG
        if (type == IconType.REFRESH) {
            FlatSVGIcon svgIcon = new FlatSVGIcon("com/nureal/ide/icon/refresh.svg", size, size);
            
            // Aplica a cor dinâmica recebida por parâmetro (ex: a cor MUTED que você definiu)
            svgIcon.setColorFilter(new FlatSVGIcon.ColorFilter(k -> color));
            return svgIcon;
        }

        // Comportamento original para todos os outros ícones da aplicação
        Glyph glyph = REGISTRY.getOrDefault(type, Icons::drawFallback);
        return new VectorIcon(size, color, glyph);
    }

    // ====================================================================
    // Logo da marca (icone da janela/taskbar) — identidade visual fixa da
    // Nureal, fora do catalogo de icones de UI (IconType).
    // ====================================================================

    /** Imagens do logo da Nureal (varios tamanhos) para o icone da janela/taskbar. */
    static List<Image> brandImages() {
        // Aponta para o novo arquivo dentro do seu diretório de resources mapeado
        FlatSVGIcon svgIcon = new FlatSVGIcon("com/nureal/ide/icon/nureal-logo-n.svg");
        
        // Abordagem clássica e compatível com todas as versões do FlatLaf
        int[] sizes = {16, 20, 24, 32, 48, 64, 128, 256};
        List<Image> images = new ArrayList<>();
        for (int s : sizes) {
            // .derive(w, h) altera o tamanho do ícone vetorialmente
            // .getImage() extrai a java.awt.Image pura dele
            images.add(svgIcon.derive(s, s).getImage());
        }
        return images;
    }
    /** Logo "N" branco sobre quadrado verde institucional arredondado, no tamanho dado. */
    static BufferedImage brandImage(int s) {
        BufferedImage img = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
                RenderingHints.VALUE_STROKE_PURE);
        double pad = s * 0.06;
        double r = s * 0.22;
        g.setColor(IconTheme.GREEN);
        g.fill(new RoundRectangle2D.Double(pad, pad, s - 2 * pad, s - 2 * pad, r, r));
        double dot = s * 0.10;
        g.setColor(IconTheme.GREEN.darker());
        g.fill(new RoundRectangle2D.Double(pad * 1.6, s - pad * 1.6 - dot, dot, dot, dot * 0.25, dot * 0.25));
        g.setColor(IconTheme.YELLOW);
        g.fill(new RoundRectangle2D.Double(
                pad * 1.6 + dot * 1.3, s - pad * 1.6 - dot, dot, dot, dot * 0.25, dot * 0.25));
        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke((float) (s * 0.13),
                BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        double x0 = s * 0.32;
        double x1 = s * 0.68;
        double y0 = s * 0.28;
        double y1 = s * 0.62;
        Path2D n = new Path2D.Double();
        n.moveTo(x0, y1);
        n.lineTo(x0, y0);
        n.lineTo(x1, y1);
        n.lineTo(x1, y0);
        g.draw(n);
        g.dispose();
        return img;
    }

    // ====================================================================
    // Catalogo: cada IconType -> uma composicao de primitivas (Glyph).
    // ====================================================================

    @FunctionalInterface
    private interface Glyph {
        /** Desenha o icone num canvas logico 0..s x 0..s (s = tamanho pedido). */
        void paint(Graphics2D g2, double s);
    }

    private enum Dir { UP, DOWN, LEFT, RIGHT }

    private static final Map<IconType, Glyph> REGISTRY = buildRegistry();

    private static Map<IconType, Glyph> buildRegistry() {
        Map<IconType, Glyph> m = new EnumMap<>(IconType.class);

        // ---------- Arquivo / edicao genericos ----------
        m.put(IconType.NEW, (g2, s) -> plus(g2, s * 0.5, s * 0.5, s * 0.5));
        m.put(IconType.OPEN, (g2, s) -> {
            roundedRect(g2, s * 0.18, s * 0.18, s * 0.64, s * 0.64, IconTheme.cornerRadius(s), false);
            chevron(g2, s * 0.52, s * 0.5, s * 0.22, Dir.RIGHT);
        });
        m.put(IconType.SAVE, (g2, s) -> {
            roundedRect(g2, s * 0.18, s * 0.16, s * 0.64, s * 0.68, IconTheme.cornerRadius(s), false);
            bar(g2, s * 0.30, s * 0.60, s * 0.40, s * 0.10);
        });
        m.put(IconType.EDIT, (g2, s) -> {
            AffineTransform old = g2.getTransform();
            g2.translate(s * 0.5, s * 0.5);
            g2.rotate(Math.toRadians(45));
            double len = s * 0.52;
            line(g2, -len / 2, 0, len / 2 - s * 0.10, 0);
            triangle(g2, len / 2 - s * 0.05, 0, s * 0.16, Dir.RIGHT, true);
            g2.setTransform(old);
        });
        m.put(IconType.DELETE, (g2, s) -> crossMark(g2, s * 0.5, s * 0.5, s * 0.36));
        m.put(IconType.COPY, (g2, s) -> {
            double r = IconTheme.cornerRadius(s);
            roundedRect(g2, s * 0.20, s * 0.20, s * 0.46, s * 0.46, r, false);
            roundedRect(g2, s * 0.34, s * 0.34, s * 0.46, s * 0.46, r, false);
        });
        m.put(IconType.PASTE, (g2, s) -> {
            double r = IconTheme.cornerRadius(s);
            roundedRect(g2, s * 0.24, s * 0.26, s * 0.52, s * 0.58, r, false);
            bar(g2, s * 0.40, s * 0.16, s * 0.20, s * 0.14);
        });
        m.put(IconType.CLOSE, (g2, s) -> crossMark(g2, s * 0.5, s * 0.5, s * 0.32));
        m.put(IconType.FAVORITE, (g2, s) -> g2.draw(star(s * 0.5, s * 0.5, s * 0.32, s * 0.14)));

        // ---------- Execucao de SQL ----------
        m.put(IconType.RUN, (g2, s) -> triangle(g2, s * 0.46, s * 0.5, s * 0.56, Dir.RIGHT, true));
        m.put(IconType.STOP, (g2, s) ->
                roundedRect(g2, s * 0.28, s * 0.28, s * 0.44, s * 0.44, IconTheme.cornerRadius(s) * 0.6, true));
        m.put(IconType.FORMAT, (g2, s) -> {
            line(g2, s * 0.22, s * 0.32, s * 0.78, s * 0.32);
            line(g2, s * 0.22, s * 0.50, s * 0.78, s * 0.50);
            line(g2, s * 0.22, s * 0.68, s * 0.78, s * 0.68);
        });
        m.put(IconType.SEARCH, (g2, s) -> circle(g2, s * 0.5, s * 0.5, s * 0.28, false));
        m.put(IconType.FILTER, (g2, s) -> triangle(g2, s * 0.5, s * 0.5, s * 0.50, Dir.DOWN, false));

        // ---------- Objetos de banco de dados ----------
        m.put(IconType.DATABASE, (g2, s) -> {
            square(g2, s * 0.20, s * 0.20, s * 0.60, false);
            bar(g2, s * 0.32, s * 0.42, s * 0.36, s * 0.08);
            bar(g2, s * 0.32, s * 0.58, s * 0.36, s * 0.08);
        });
        m.put(IconType.SCHEMA, (g2, s) -> {
            Stroke old = g2.getStroke();
            float dash = (float) (s * 0.09);
            g2.setStroke(new BasicStroke(IconTheme.strokeWidth(s), BasicStroke.CAP_ROUND,
                    BasicStroke.JOIN_ROUND, 10f, new float[] {dash, dash}, 0f));
            circle(g2, s * 0.5, s * 0.5, s * 0.30, false);
            g2.setStroke(old);
        });
        m.put(IconType.TABLE, (g2, s) -> {
            square(g2, s * 0.20, s * 0.20, s * 0.60, false);
            line(g2, s * 0.5, s * 0.20, s * 0.5, s * 0.80);
            line(g2, s * 0.20, s * 0.5, s * 0.80, s * 0.5);
        });
        m.put(IconType.VIEW, (g2, s) -> {
            rect(g2, s * 0.18, s * 0.26, s * 0.64, s * 0.48, false);
            line(g2, s * 0.18, s * 0.42, s * 0.82, s * 0.42);
        });
        m.put(IconType.FUNCTION, (g2, s) -> centeredGlyph(g2, s, "f", Font.ITALIC));
        m.put(IconType.PROCEDURE, (g2, s) -> centeredGlyph(g2, s, "{ }", Font.PLAIN));
        m.put(IconType.TRIGGER, (g2, s) -> g2.draw(diamond(s * 0.5, s * 0.5, s * 0.30)));
        m.put(IconType.COLUMN, (g2, s) -> bar(g2, s * 0.46, s * 0.18, s * 0.08, s * 0.64));
        m.put(IconType.INDEX, (g2, s) -> {
            line(g2, s * 0.22, s * 0.32, s * 0.78, s * 0.32);
            line(g2, s * 0.22, s * 0.50, s * 0.62, s * 0.50);
            line(g2, s * 0.22, s * 0.68, s * 0.70, s * 0.68);
        });
        m.put(IconType.PRIMARY_KEY, (g2, s) -> circle(g2, s * 0.5, s * 0.5, s * 0.26, true));
        m.put(IconType.FOREIGN_KEY, (g2, s) -> circle(g2, s * 0.5, s * 0.5, s * 0.26, false));

        // ---------- Dados / IO ----------
        m.put(IconType.EXPORT, (g2, s) -> arrow(g2, s * 0.5, s * 0.5, s * 0.56, Dir.DOWN));

        // ---------- Sistema / feedback ----------
        m.put(IconType.SETTINGS, (g2, s) -> {
            circle(g2, s * 0.5, s * 0.5, s * 0.22, false);
            double inner = s * 0.24;
            double outer = s * 0.32;
            line(g2, s * 0.5, inner, s * 0.5, outer);
            line(g2, s * 0.5, s - inner, s * 0.5, s - outer);
            line(g2, inner, s * 0.5, outer, s * 0.5);
            line(g2, s - inner, s * 0.5, s - outer, s * 0.5);
        });
        m.put(IconType.HELP, (g2, s) -> {
            circle(g2, s * 0.5, s * 0.5, s * 0.32, false);
            centeredGlyph(g2, s, "?", Font.PLAIN);
        });
        m.put(IconType.INFO, (g2, s) -> {
            circle(g2, s * 0.5, s * 0.5, s * 0.32, false);
            dot(g2, s * 0.5, s * 0.36, s * 0.035);
            bar(g2, s * 0.47, s * 0.46, s * 0.06, s * 0.20);
        });
        m.put(IconType.WARNING, (g2, s) -> {
            triangle(g2, s * 0.5, s * 0.52, s * 0.60, Dir.UP, false);
            bar(g2, s * 0.47, s * 0.38, s * 0.06, s * 0.20);
            dot(g2, s * 0.5, s * 0.66, s * 0.035);
        });
        m.put(IconType.SUCCESS, (g2, s) -> {
            circle(g2, s * 0.5, s * 0.5, s * 0.32, false);
            line(g2, s * 0.36, s * 0.50, s * 0.46, s * 0.60);
            line(g2, s * 0.46, s * 0.60, s * 0.64, s * 0.38);
        });
        m.put(IconType.ERROR, (g2, s) -> {
            circle(g2, s * 0.5, s * 0.5, s * 0.32, false);
            crossMark(g2, s * 0.5, s * 0.5, s * 0.20);
        });
        m.put(IconType.REFRESH, Icons::drawRefresh);

        // ---------- Conexao ----------
        m.put(IconType.CONNECTION, (g2, s) -> {
            dot(g2, s * 0.28, s * 0.5, s * 0.07);
            dot(g2, s * 0.72, s * 0.5, s * 0.07);
            line(g2, s * 0.35, s * 0.5, s * 0.65, s * 0.5);
        });
        m.put(IconType.DISCONNECT, (g2, s) -> {
            dot(g2, s * 0.28, s * 0.5, s * 0.07);
            dot(g2, s * 0.72, s * 0.5, s * 0.07);
            line(g2, s * 0.35, s * 0.5, s * 0.44, s * 0.5);
            line(g2, s * 0.56, s * 0.5, s * 0.65, s * 0.5);
        });
        m.put(IconType.STATUS_DOT, (g2, s) -> dot(g2, s * 0.5, s * 0.5, s * 0.38));

        // ---------- Layout / navegacao da janela ----------
        m.put(IconType.PANEL_LEFT, (g2, s) -> {
            rect(g2, s * 0.18, s * 0.18, s * 0.64, s * 0.64, false);
            bar(g2, s * 0.18, s * 0.18, s * 0.22, s * 0.64);
        });
        m.put(IconType.PANEL_BOTTOM, (g2, s) -> {
            rect(g2, s * 0.18, s * 0.18, s * 0.64, s * 0.64, false);
            bar(g2, s * 0.18, s * 0.58, s * 0.64, s * 0.24);
        });
        m.put(IconType.CHEVRON_LEFT, (g2, s) -> chevron(g2, s * 0.5, s * 0.5, s * 0.34, Dir.LEFT));
        m.put(IconType.CHEVRON_RIGHT, (g2, s) -> chevron(g2, s * 0.5, s * 0.5, s * 0.34, Dir.RIGHT));
        m.put(IconType.THEME_LIGHT, (g2, s) -> circle(g2, s * 0.5, s * 0.5, s * 0.30, false));
        m.put(IconType.THEME_DARK, (g2, s) -> circle(g2, s * 0.5, s * 0.5, s * 0.30, true));

        return m;
    }

    /** Usado apenas se um IconType ficar sem entrada no catalogo (nao deveria acontecer). */
    private static void drawFallback(Graphics2D g2, double s) {
        square(g2, s * 0.30, s * 0.30, s * 0.40, false);
    }

    /** Seta circular de atualizar/recarregar: arco (reducao do circulo) + ponta triangular. */
    private static void drawRefresh(Graphics2D g2, double s) {
        double cx = s * 0.5;
        double cy = s * 0.5;
        double r = s * 0.30;
        Arc2D arc = new Arc2D.Double(cx - r, cy - r, r * 2, r * 2, 35, 270, Arc2D.OPEN);
        g2.draw(arc);
        double endAngle = Math.toRadians(35);
        double ex = cx + r * Math.cos(endAngle);
        double ey = cy - r * Math.sin(endAngle);
        double tangent = endAngle - Math.PI / 2;
        double ah = s * 0.16;
        Path2D head = new Path2D.Double();
        head.moveTo(ex, ey);
        head.lineTo(ex - ah * Math.cos(tangent - 0.5), ey + ah * Math.sin(tangent - 0.5));
        head.lineTo(ex - ah * Math.cos(tangent + 0.5), ey + ah * Math.sin(tangent + 0.5));
        head.closePath();
        g2.fill(head);
    }

    // ====================================================================
    // Primitivas geometricas — TODO icone e composto exclusivamente a
    // partir destas (nunca desenha um objeto/silhueta realista).
    // ====================================================================

    private static void square(Graphics2D g2, double x, double y, double w, boolean filled) {
        rect(g2, x, y, w, w, filled);
    }

    private static void rect(Graphics2D g2, double x, double y, double w, double h, boolean filled) {
        Rectangle2D r = new Rectangle2D.Double(x, y, w, h);
        if (filled) {
            g2.fill(r);
        } else {
            g2.draw(r);
        }
    }

    private static void roundedRect(Graphics2D g2, double x, double y, double w, double h,
            double radius, boolean filled) {
        RoundRectangle2D r = new RoundRectangle2D.Double(x, y, w, h, radius, radius);
        if (filled) {
            g2.fill(r);
        } else {
            g2.draw(r);
        }
    }

    private static void line(Graphics2D g2, double x1, double y1, double x2, double y2) {
        g2.draw(new Line2D.Double(x1, y1, x2, y2));
    }

    private static void circle(Graphics2D g2, double cx, double cy, double r, boolean filled) {
        Ellipse2D e = new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2);
        if (filled) {
            g2.fill(e);
        } else {
            g2.draw(e);
        }
    }

    private static void dot(Graphics2D g2, double cx, double cy, double r) {
        g2.fill(new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2));
    }

    /** "V" apontando na direcao dada, centralizado em (cx,cy) — usado isolado ou como ponta de seta. */
    private static void chevron(Graphics2D g2, double cx, double cy, double size, Dir dir) {
        double h = size / 2;
        Path2D p = new Path2D.Double();
        switch (dir) {
            case LEFT -> {
                p.moveTo(cx + h, cy - h);
                p.lineTo(cx - h, cy);
                p.lineTo(cx + h, cy + h);
            }
            case RIGHT -> {
                p.moveTo(cx - h, cy - h);
                p.lineTo(cx + h, cy);
                p.lineTo(cx - h, cy + h);
            }
            case UP -> {
                p.moveTo(cx - h, cy + h);
                p.lineTo(cx, cy - h);
                p.lineTo(cx + h, cy + h);
            }
            case DOWN -> {
                p.moveTo(cx - h, cy - h);
                p.lineTo(cx, cy + h);
                p.lineTo(cx + h, cy - h);
            }
        }
        g2.draw(p);
    }

    private static void triangle(Graphics2D g2, double cx, double cy, double size, Dir dir, boolean filled) {
        double h = size / 2;
        Path2D p = new Path2D.Double();
        switch (dir) {
            case RIGHT -> {
                p.moveTo(cx - h * 0.6, cy - h);
                p.lineTo(cx - h * 0.6, cy + h);
                p.lineTo(cx + h * 0.8, cy);
            }
            case LEFT -> {
                p.moveTo(cx + h * 0.6, cy - h);
                p.lineTo(cx + h * 0.6, cy + h);
                p.lineTo(cx - h * 0.8, cy);
            }
            case DOWN -> {
                p.moveTo(cx - h, cy - h * 0.6);
                p.lineTo(cx + h, cy - h * 0.6);
                p.lineTo(cx, cy + h * 0.8);
            }
            case UP -> {
                p.moveTo(cx - h, cy + h * 0.6);
                p.lineTo(cx + h, cy + h * 0.6);
                p.lineTo(cx, cy - h * 0.8);
            }
        }
        p.closePath();
        if (filled) {
            g2.fill(p);
        } else {
            g2.draw(p);
        }
    }

    private static void bar(Graphics2D g2, double x, double y, double w, double h) {
        g2.fill(new Rectangle2D.Double(x, y, w, h));
    }

    private static void plus(Graphics2D g2, double cx, double cy, double size) {
        double h = size / 2;
        line(g2, cx - h, cy, cx + h, cy);
        line(g2, cx, cy - h, cx, cy + h);
    }

    /** Linha + ponta em chevron na direcao dada — composicao padrao de "seta". */
    private static void arrow(Graphics2D g2, double cx, double cy, double size, Dir dir) {
        double half = size / 2;
        double head = size * 0.55;
        double headHalf = head / 2;
        switch (dir) {
            case DOWN -> {
                line(g2, cx, cy - half, cx, cy + half - headHalf * 0.7);
                chevron(g2, cx, cy + half - headHalf, head, Dir.DOWN);
            }
            case UP -> {
                line(g2, cx, cy + half, cx, cy - half + headHalf * 0.7);
                chevron(g2, cx, cy - half + headHalf, head, Dir.UP);
            }
            case RIGHT -> {
                line(g2, cx - half, cy, cx + half - headHalf * 0.7, cy);
                chevron(g2, cx + half - headHalf, cy, head, Dir.RIGHT);
            }
            case LEFT -> {
                line(g2, cx + half, cy, cx - half + headHalf * 0.7, cy);
                chevron(g2, cx - half + headHalf, cy, head, Dir.LEFT);
            }
        }
    }

    /** "X" simples (usado por CLOSE e DELETE) — duas linhas cruzadas. */
    private static void crossMark(Graphics2D g2, double cx, double cy, double size) {
        double h = size / 2;
        line(g2, cx - h, cy - h, cx + h, cy + h);
        line(g2, cx - h, cy + h, cx + h, cy - h);
    }

    /** Losango (quadrado girado 45 graus), usado por TRIGGER. */
    private static Path2D diamond(double cx, double cy, double h) {
        Path2D p = new Path2D.Double();
        p.moveTo(cx, cy - h);
        p.lineTo(cx + h, cy);
        p.lineTo(cx, cy + h);
        p.lineTo(cx - h, cy);
        p.closePath();
        return p;
    }

    /** Estrela outline (5 pontas), usada por FAVORITE — unica forma fora da lista basica de
     *  primitivas, mantida pois o conceito "Favorito" foi explicitamente definido como estrela. */
    private static Path2D star(double cx, double cy, double rOuter, double rInner) {
        Path2D p = new Path2D.Double();
        for (int i = 0; i < 10; i++) {
            double r = (i % 2 == 0) ? rOuter : rInner;
            double angle = Math.PI / 2 + i * Math.PI / 5;
            double x = cx + r * Math.cos(angle);
            double y = cy - r * Math.sin(angle);
            if (i == 0) {
                p.moveTo(x, y);
            } else {
                p.lineTo(x, y);
            }
        }
        p.closePath();
        return p;
    }

    /** Glifo de texto curto centralizado (usado por FUNCTION "f", PROCEDURE "{ }", HELP "?"). */
    private static void centeredGlyph(Graphics2D g2, double s, String text, int style) {
        Font f = new Font(Font.SANS_SERIF, style, (int) Math.round(s * 0.50));
        g2.setFont(f);
        FontMetrics fm = g2.getFontMetrics();
        int tw = fm.stringWidth(text);
        g2.drawString(text, (float) ((s - tw) / 2), (float) ((s + fm.getAscent() * 0.62) / 2));
    }

    // ====================================================================
    // Implementacao de javax.swing.Icon — uma unica classe generica que
    // delega o desenho ao Glyph do IconType pedido.
    // ====================================================================

    private static final class VectorIcon implements Icon {
        private final int size;
        private final Color color;
        private final Glyph glyph;

        VectorIcon(int size, Color color, Glyph glyph) {
            this.size = size;
            this.color = color;
            this.glyph = glyph;
        }

        @Override
        public int getIconWidth() {
            return size;
        }

        @Override
        public int getIconHeight() {
            return size;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            g2.setStroke(IconTheme.stroke(size));
            g2.translate(x, y);
            glyph.paint(g2, size);
            g2.dispose();
        }
    }
}
