package com.nureal.ide.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.sql.Blob;
import java.sql.Clob;

import com.nureal.ide.core.log.AppLogger;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.WindowConstants;

/**
 * Visualizador de conteudo completo de uma celula — item "Ver conteudo
 * completo" do {@link ResultContextMenu}. Ponto de extensao arquitetural
 * pedido para a grade: hoje mostra texto simples (com previa formatada para
 * JSON) e um resumo de tamanho para BLOB, mas o metodo {@link #show} e o
 * unico lugar que precisa mudar para, no futuro, trocar por um visualizador
 * mais rico (arvore JSON navegavel, preview de imagem para BLOB, etc.) sem
 * tocar no menu de contexto ou nos renderers que o chamam.
 *
 * CLOB e lido por INTEIRO aqui (diferente do renderer, que so le uma previa
 * curta) porque abrir o visualizador e uma acao explicita e pouco frequente
 * do usuario — mas a leitura acontece em um {@link SwingWorker}, nunca na
 * EDT, para nao travar a interface com um CLOB grande.
 */
final class CellContentViewer {

    private CellContentViewer() {
    }

    static void show(Component parent, String columnName, Object rawValue) {
        java.awt.Window owner = SwingUtilities.getWindowAncestor(parent);
        JDialog dialog = new JDialog(owner, "Conteudo completo: " + columnName, JDialog.ModalityType.MODELESS);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        JTextArea area = new JTextArea();
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        area.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JButton copy = new JButton("Copiar");
        copy.addActionListener(e -> copyToClipboard(area.getText()));
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(copy);

        dialog.setLayout(new BorderLayout());
        dialog.add(new JScrollPane(area), BorderLayout.CENTER);
        dialog.add(buttons, BorderLayout.SOUTH);
        dialog.setSize(new Dimension(640, 480));
        // Centralizado na JANELA (owner), nao na celula que disparou o menu
        // "Ver conteudo completo" — senao o dialogo aparece perto daquela
        // celula especifica, em qualquer canto da grade/tela. Mesmo padrao de
        // ColumnMetadataPopup (ver DialogUtil para os JOptionPane).
        dialog.setLocationRelativeTo(owner);

        loadContent(rawValue, area, copy);
        dialog.setVisible(true);
    }

    private static void loadContent(Object rawValue, JTextArea area, JButton copy) {
        if (rawValue == null) {
            area.setText("");
            return;
        }
        if (rawValue instanceof Clob clob) {
            loadClobAsync(clob, area, copy);
            return;
        }
        if (rawValue instanceof Blob blob) {
            loadBlobSummaryAsync(blob, area);
            return;
        }
        if (rawValue instanceof byte[] bytes) {
            area.setText(hexPreview(bytes));
            return;
        }
        String text = rawValue.toString();
        area.setText(looksLikeJson(text) ? prettyPrintJson(text) : text);
        area.setCaretPosition(0);
    }

    private static void loadClobAsync(Clob clob, JTextArea area, JButton copy) {
        area.setText("Carregando...");
        copy.setEnabled(false);
        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                long length = clob.length();
                return clob.getSubString(1, (int) Math.min(length, Integer.MAX_VALUE));
            }

            @Override
            protected void done() {
                copy.setEnabled(true);
                try {
                    String text = get();
                    area.setText(looksLikeJson(text) ? prettyPrintJson(text) : text);
                    area.setCaretPosition(0);
                } catch (Exception ex) {
                    AppLogger.warning("Falha ao ler o CLOB", ex);
                    area.setText("Falha ao ler o CLOB: " + ex.getMessage());
                }
            }
        }.execute();
    }

    private static void loadBlobSummaryAsync(Blob blob, JTextArea area) {
        area.setText("Carregando...");
        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                long length = blob.length();
                int previewLen = (int) Math.min(length, 4096);
                byte[] preview = blob.getBytes(1, previewLen);
                return "BLOB — " + length + " byte(s)\n\n"
                        + "Primeiros " + previewLen + " byte(s) em hexadecimal:\n\n"
                        + hexPreview(preview);
            }

            @Override
            protected void done() {
                try {
                    area.setText(get());
                } catch (Exception ex) {
                    AppLogger.warning("Falha ao ler o BLOB", ex);
                    area.setText("Falha ao ler o BLOB: " + ex.getMessage());
                }
                area.setCaretPosition(0);
            }
        }.execute();
    }

    private static void copyToClipboard(String text) {
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
    }

    /** Preview hexadecimal simples (16 bytes por linha, estilo "hex dump"). */
    private static String hexPreview(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bytes.length; i += 16) {
            int end = Math.min(i + 16, bytes.length);
            for (int j = i; j < end; j++) {
                sb.append(String.format("%02X ", bytes[j]));
            }
            sb.append('\n');
        }
        if (bytes.length == 0) {
            sb.append("(vazio)");
        }
        return sb.toString();
    }

    private static boolean looksLikeJson(String text) {
        String t = text.trim();
        return (t.startsWith("{") && t.endsWith("}")) || (t.startsWith("[") && t.endsWith("]"));
    }

    /**
     * Indentacao ingenua de JSON (baseada em profundidade de chaves/colchetes,
     * ignorando o conteudo de strings) — so para leitura, NAO valida nem
     * reparsa o JSON. Suficiente para o visualizador; um parser de verdade
     * fica como melhoria futura caso vire uma arvore navegavel.
     */
    private static String prettyPrintJson(String json) {
        StringBuilder out = new StringBuilder(json.length() + 64);
        int indent = 0;
        boolean inString = false;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (inString) {
                out.append(c);
                if (c == '\\' && i + 1 < json.length()) {
                    out.append(json.charAt(++i));
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            switch (c) {
                case '"' -> {
                    inString = true;
                    out.append(c);
                }
                case '{', '[' -> {
                    out.append(c);
                    indent++;
                    out.append('\n').append("  ".repeat(indent));
                }
                case '}', ']' -> {
                    indent = Math.max(0, indent - 1);
                    out.append('\n').append("  ".repeat(indent)).append(c);
                }
                case ',' -> {
                    out.append(c).append('\n').append("  ".repeat(indent));
                }
                case ':' -> out.append(": ");
                default -> {
                    if (!Character.isWhitespace(c)) {
                        out.append(c);
                    }
                }
            }
        }
        return out.toString();
    }
}
