package com.nureal.ide.ui;
import com.nureal.ide.compartilhado.designsystem.IconType;
import com.nureal.ide.compartilhado.designsystem.Buttons;
import com.nureal.ide.compartilhado.designsystem.GridTheme;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.sql.Blob;
import java.sql.Clob;

import com.nureal.ide.core.log.AppLogger;
import com.nureal.ide.core.sql.SqlTypeKind;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
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

    /**
     * Atalho para abrir a partir de uma celula da grade (ver
     * {@code SelectionManager}/{@code ResultContextMenu}): resolve o tipo SQL
     * real da coluna (para colorir o valor com a MESMA cor semantica da
     * grade, ver {@link GridTheme#colorFor}) a partir do {@link ResultTableModel}
     * da propria tabela, quando disponivel.
     */
    static void show(JTable table, int viewColumn, Object rawValue) {
        String columnName = (viewColumn >= 0) ? table.getColumnName(viewColumn) : "";
        String sqlType = null;
        if (viewColumn >= 0 && table.getModel() instanceof ResultTableModel model) {
            sqlType = model.sqlType(table.convertColumnIndexToModel(viewColumn));
        }
        show(table, columnName, sqlType, rawValue);
    }

    /** @deprecated use {@link #show(JTable, int, Object)} quando houver uma grade — assim o valor ganha a cor do tipo real. */
    @Deprecated
    static void show(Component parent, String columnName, Object rawValue) {
        show(parent, columnName, null, rawValue);
    }

    static void show(Component parent, String columnName, String sqlType, Object rawValue) {
        java.awt.Window owner = SwingUtilities.getWindowAncestor(parent);
        JDialog dialog = new JDialog(owner, "Conteudo completo: " + columnName, JDialog.ModalityType.MODELESS);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        JTextArea area = new JTextArea();
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        // Mesma fonte monoespacada do editor SQL principal (ver
        // SqlEditorPane#monospaceFont) — antes era Font.MONOSPACED generico,
        // uma fonte DIFERENTE da usada no resto do app pra qualquer texto em
        // largura fixa (inconsistencia de tipografia).
        area.setFont(SqlEditorPane.monospaceFont(12));
        area.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        // Cor do texto = cor semantica do TIPO real da coluna (ver
        // GridTheme#colorFor) — mesma identidade visual da grade, pedido do
        // "Sistema Semantico de Cores por Tipo de Dado". Sem tipo conhecido
        // (chamador antigo, ou coluna computada sem metadado), fica no
        // foreground padrao do tema em vez de adivinhar.
        if (sqlType != null) {
            area.setForeground(GridTheme.colorFor(SqlTypeKind.classify(sqlType)));
        }

        // Dialogo MODELESS (fica aberto enquanto o usuario mexe no resto do
        // app) — Buttons.bindThemedIcon evita que o icone fique congelado se
        // o tema for alternado com este dialogo ainda aberto (mesmo bug
        // sistemico corrigido no resto do app, ver Buttons#bindThemedIcon).
        JButton copy = new JButton("Copiar");
        Buttons.bindThemedIcon(copy, IconType.COPY, 13, () -> GridTheme.MUTED_TEXT);
        copy.addActionListener(e -> copyToClipboard(area.getText()));
        copy.setIconTextGap(6);
        // Mesmo estilo "outline" dos botoes secundarios do resto do app (ver
        // Buttons#styleSecondary) — antes reimplementava o mesmo trecho na
        // mao, com uma margem (4,12,4,12) que ja tinha divergido da margem
        // canonica (4,10,4,10) usada em todo outro botao secundario do app.
        Buttons.styleSecondary(copy);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
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
