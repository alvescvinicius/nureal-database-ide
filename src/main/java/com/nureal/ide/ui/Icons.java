package com.nureal.ide.ui;

import javax.swing.Icon;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * Icones vetoriais desenhados com Java2D. Sao independentes da fonte (evitam o
 * problema de glifos que viram "quadradinhos" quando a fonte nao os possui) e
 * ficam nitidos em qualquer tamanho/DPI.
 */
final class Icons {

    private Icons() {
    }

    static Icon play(int size, Color color) {
        return new BaseIcon(size, color) {
            @Override
            void paint(Graphics2D g2, int s) {
                Path2D p = new Path2D.Double();
                p.moveTo(s * 0.30, s * 0.22);
                p.lineTo(s * 0.30, s * 0.78);
                p.lineTo(s * 0.78, s * 0.50);
                p.closePath();
                g2.fill(p);
            }
        };
    }

    static Icon moon(int size, Color color) {
        return new BaseIcon(size, color) {
            @Override
            void paint(Graphics2D g2, int s) {
                Area outer = new Area(new Ellipse2D.Double(s * 0.20, s * 0.16, s * 0.60, s * 0.68));
                Area cut = new Area(new Ellipse2D.Double(s * 0.36, s * 0.06, s * 0.62, s * 0.70));
                outer.subtract(cut);
                g2.fill(outer);
            }
        };
    }

    static Icon sun(int size, Color color) {
        return new BaseIcon(size, color) {
            @Override
            void paint(Graphics2D g2, int s) {
                double cx = s * 0.5;
                double cy = s * 0.5;
                double r = s * 0.18;
                g2.fill(new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r));
                g2.setStroke(new BasicStroke(Math.max(1f, s * 0.06f),
                        BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                double r1 = s * 0.30;
                double r2 = s * 0.42;
                for (int i = 0; i < 8; i++) {
                    double a = Math.PI * i / 4.0;
                    g2.draw(new Line2D.Double(
                            cx + r1 * Math.cos(a), cy + r1 * Math.sin(a),
                            cx + r2 * Math.cos(a), cy + r2 * Math.sin(a)));
                }
            }
        };
    }

    static Icon close(int size, Color color) {
        return new BaseIcon(size, color) {
            @Override
            void paint(Graphics2D g2, int s) {
                g2.setStroke(new BasicStroke(Math.max(1.4f, s * 0.10f),
                        BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.draw(new Line2D.Double(s * 0.28, s * 0.28, s * 0.72, s * 0.72));
                g2.draw(new Line2D.Double(s * 0.72, s * 0.28, s * 0.28, s * 0.72));
            }
        };
    }

    static Icon chevron(int size, Color color, boolean left) {
        return new BaseIcon(size, color) {
            @Override
            void paint(Graphics2D g2, int s) {
                g2.setStroke(new BasicStroke(Math.max(1.4f, s * 0.12f),
                        BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                Path2D p = new Path2D.Double();
                if (left) {
                    p.moveTo(s * 0.60, s * 0.25);
                    p.lineTo(s * 0.38, s * 0.50);
                    p.lineTo(s * 0.60, s * 0.75);
                } else {
                    p.moveTo(s * 0.40, s * 0.25);
                    p.lineTo(s * 0.62, s * 0.50);
                    p.lineTo(s * 0.40, s * 0.75);
                }
                g2.draw(p);
            }
        };
    }

    /** Icone de exportar (seta para baixo sobre uma bandeja). */
    static Icon export(int size, Color color) {
        return new BaseIcon(size, color) {
            @Override
            void paint(Graphics2D g2, int s) {
                g2.setStroke(new BasicStroke(Math.max(1.3f, s * 0.09f),
                        BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                // haste da seta
                g2.draw(new Line2D.Double(s * 0.50, s * 0.16, s * 0.50, s * 0.60));
                // ponta da seta
                Path2D head = new Path2D.Double();
                head.moveTo(s * 0.32, s * 0.44);
                head.lineTo(s * 0.50, s * 0.62);
                head.lineTo(s * 0.68, s * 0.44);
                g2.draw(head);
                // bandeja
                Path2D tray = new Path2D.Double();
                tray.moveTo(s * 0.24, s * 0.70);
                tray.lineTo(s * 0.24, s * 0.80);
                tray.lineTo(s * 0.76, s * 0.80);
                tray.lineTo(s * 0.76, s * 0.70);
                g2.draw(tray);
            }
        };
    }

    /** Icone de grade/tabela usado no estado vazio dos resultados. */
    static Icon grid(int size, Color color) {
        return new BaseIcon(size, color) {
            @Override
            void paint(Graphics2D g2, int s) {
                g2.setStroke(new BasicStroke(Math.max(1f, s * 0.035f)));
                double m = s * 0.16;
                double w = s - 2 * m;
                g2.draw(new RoundRectangle2D.Double(m, m, w, w, s * 0.10, s * 0.10));
                // cabecalho preenchido
                g2.fill(new Rectangle2D.Double(m, m, w, w * 0.22));
                // linhas internas
                for (int i = 1; i < 3; i++) {
                    double yy = m + w * (0.22 + 0.26 * i);
                    g2.draw(new Line2D.Double(m, yy, m + w, yy));
                }
                double xx = m + w * 0.5;
                g2.draw(new Line2D.Double(xx, m + w * 0.22, xx, m + w));
            }
        };
    }

    /** Imagens do logo da Nureal (varios tamanhos) para o icone da janela/taskbar. */
    static List<Image> brandImages() {
        int[] sizes = {16, 20, 24, 32, 48, 64, 128, 256};
        List<Image> images = new ArrayList<>();
        for (int s : sizes) {
            images.add(brandImage(s));
        }
        return images;
    }

    /** Logo "N" branco sobre quadrado esmeralda arredondado, no tamanho dado. */
    static BufferedImage brandImage(int s) {
        BufferedImage img = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
                RenderingHints.VALUE_STROKE_PURE);
        double pad = s * 0.06;
        double r = s * 0.22;
        g.setColor(new Color(0x059669));
        g.fill(new RoundRectangle2D.Double(pad, pad, s - 2 * pad, s - 2 * pad, r, r));
        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke((float) (s * 0.13),
                BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        double x0 = s * 0.32;
        double x1 = s * 0.68;
        double y0 = s * 0.31;
        double y1 = s * 0.69;
        Path2D n = new Path2D.Double();
        n.moveTo(x0, y1);
        n.lineTo(x0, y0);
        n.lineTo(x1, y1);
        n.lineTo(x1, y0);
        g.draw(n);
        g.dispose();
        return img;
    }

    private abstract static class BaseIcon implements Icon {
        private final int size;
        private final Color color;

        BaseIcon(int size, Color color) {
            this.size = size;
            this.color = color;
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
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            g2.translate(x, y);
            paint(g2, size);
            g2.dispose();
        }

        abstract void paint(Graphics2D g2, int size);
    }
}
