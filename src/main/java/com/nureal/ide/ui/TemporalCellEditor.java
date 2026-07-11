package com.nureal.ide.ui;

import java.awt.Component;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.EventObject;

import javax.swing.AbstractCellEditor;
import javax.swing.BorderFactory;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.table.TableCellEditor;

/**
 * Editor de celula para colunas TEMPORAIS (DATE/TIME/TIMESTAMP/...).
 *
 * O editor generico padrao do proprio JTable (usado quando nenhum editor e
 * registrado explicitamente para a coluna) tem dois problemas graves aqui:
 * (1) mostra {@code value.toString()} pra editar — formato ISO
 * ("2026-07-11 00:00:00.0") — mas a grade EXIBE em "dd/MM/yyyy HH:mm:ss.SSS"
 * (ver {@link TemporalCellRenderer}); o usuario ve um formato e o editor
 * espera outro, digita o que ve e o valor fica incoerente ou a edicao falha;
 * (2) para reconstruir o valor editado, usa REFLEXAO pra achar um construtor
 * {@code (String)} na classe do valor original — funciona por acaso para
 * {@code java.sql.Date}/{@code java.sql.Timestamp} (tem construtor
 * descontinuado que aceita String), mas {@code java.time.LocalDate}/
 * {@code LocalDateTime} NAO TEM CONSTRUTOR NENHUM (sao criados por metodos
 * estaticos como {@code LocalDate.parse}) — a reflexao lanca excecao, o
 * editor generico so pinta a borda vermelha e devolve {@code false} em
 * silencio, sem qualquer pista do motivo pro usuario. Resultado pratico: em
 * bases onde o driver JDBC devolve tipos {@code java.time.*} para colunas de
 * data, a edicao NUNCA consegue ser confirmada.
 *
 * Este editor resolve os dois pontos: mostra/aceita o MESMO formato exibido
 * pela grade (com uma folga: hora/segundos/milissegundos sao opcionais, e o
 * formato ISO "yyyy-MM-dd[ HH:mm:ss]" tambem e aceito) e converte o texto de
 * volta para a MESMA classe Java que a coluna ja usava — nunca String —
 * consultando {@link ResultTableModel#getColumnClass} (a classe DECLARADA da
 * coluna, disponivel mesmo quando o valor atual da celula e {@code null},
 * como numa linha nova recem-criada).
 *
 * Texto que nao bate com nenhum formato aceito NUNCA vira um valor de tipo
 * errado no modelo: a borda fica vermelha, um tooltip explica o formato
 * esperado, e {@link #stopCellEditing()} devolve {@code false} — a edicao
 * continua aberta (o usuario corrige o texto ou teclas Esc pra cancelar,
 * igual ao editor generico padrao do Swing se comporta pra outros tipos).
 */
final class TemporalCellEditor extends AbstractCellEditor implements TableCellEditor {

    private static final long serialVersionUID = 1L;

    private static final DateTimeFormatter DATE_OUT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATETIME_OUT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss.SSS");

    // Formatos aceitos na ENTRADA do mais completo ao mais tolerante — o
    // usuario nao deveria ser obrigado a digitar milissegundos so pra
    // corrigir o dia, por exemplo. yyyy-MM-dd tambem aceito por ser o
    // formato "natural" de quem esta acostumado a digitar SQL direto.
    private static final DateTimeFormatter[] DATETIME_IN = {
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss.SSS"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
    };
    private static final DateTimeFormatter[] DATE_IN = {
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
    };

    private final JTextField field = new JTextField();

    /** Classe DECLARADA da coluna sendo editada agora — ver {@link #getTableCellEditorComponent}. */
    private Class<?> targetClass = Object.class;
    /** Resultado do parse bem-sucedido em {@link #stopCellEditing()} — o que {@link #getCellEditorValue()} devolve. */
    private Object parsedValue;

    TemporalCellEditor() {
        field.setHorizontalAlignment(SwingConstants.CENTER);
        field.addActionListener(e -> stopCellEditing()); // Enter confirma, igual a qualquer editor de texto do Swing
    }

    /** So inicia a edicao no DUPLO-clique (ou teclado/F2) — mesmo padrao do editor generico do JTable (clickCountToStart = 2). */
    @Override
    public boolean isCellEditable(EventObject e) {
        if (e instanceof java.awt.event.MouseEvent me) {
            return me.getClickCount() >= 2;
        }
        return true;
    }

    @Override
    public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
        field.setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 6));
        field.setToolTipText(null);
        // Limpa o contorno de erro de uma edicao anterior invalida — sem
        // isto o campo ficava "vermelho" pra sempre na proxima vez que
        // qualquer celula desta coluna fosse editada (mesma instancia
        // reaproveitada, ver o campo `field`).
        field.putClientProperty("JComponent.outline", null);
        // Le as cores da TABELA (nao os defaults estaticos do L&F do proprio
        // JTextField) toda vez que a edicao comeca, igual o DefaultCellEditor
        // padrao do Swing ja faz para os outros tipos de coluna — sem isto,
        // este campo (uma UNICA instancia reaproveitada por toda coluna
        // temporal, ver o campo `field`) podia ficar preso na aparencia clara
        // do L&F mesmo com o app inteiro no tema escuro (bug relatado: "caixa
        // branca" ao editar uma celula).
        field.setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());
        field.setForeground(isSelected ? table.getSelectionForeground() : table.getForeground());
        int modelColumn = table.convertColumnIndexToModel(column);
        targetClass = table.getModel().getColumnClass(modelColumn);
        field.setText(value == null ? "" : formatForEdit(value));
        return field;
    }

    @Override
    public Object getCellEditorValue() {
        return parsedValue;
    }

    @Override
    public boolean stopCellEditing() {
        String text = field.getText().trim();
        if (text.isEmpty()) {
            parsedValue = null;
            return super.stopCellEditing();
        }
        Object value = tryParse(text, targetClass);
        if (value == null) {
            // Contorno de erro NATIVO do FlatLaf ("JComponent.outline" =
            // "error"), nao mais uma LineBorder vermelha desenhada na mao —
            // mesmo mecanismo agora usado em ConnectionEditDialog para o
            // campo de nome duplicado, e reativo ao tema sozinho (o antigo
            // Color.RED fixo nao acompanhava claro/escuro).
            field.putClientProperty("JComponent.outline", "error");
            field.setToolTipText("Data invalida — use " + expectedFormatHint(targetClass) + " (ou Esc para cancelar)");
            return false;
        }
        parsedValue = value;
        return super.stopCellEditing();
    }

    private static String formatForEdit(Object value) {
        if (value instanceof Timestamp ts) {
            return ts.toLocalDateTime().format(DATETIME_OUT);
        }
        if (value instanceof java.sql.Date d) {
            return d.toLocalDate().format(DATE_OUT);
        }
        if (value instanceof LocalDateTime ldt) {
            return ldt.format(DATETIME_OUT);
        }
        if (value instanceof LocalDate ld) {
            return ld.format(DATE_OUT);
        }
        return value.toString();
    }

    /** Converte o texto digitado para a MESMA classe que a coluna ja usa — nunca String. {@code null} = invalido. */
    private static Object tryParse(String text, Class<?> targetClass) {
        if (targetClass == Timestamp.class) {
            LocalDateTime ldt = parseDateTime(text);
            return ldt == null ? null : Timestamp.valueOf(ldt);
        }
        if (targetClass == java.sql.Date.class) {
            LocalDate ld = parseDate(text);
            return ld == null ? null : java.sql.Date.valueOf(ld);
        }
        if (targetClass == LocalDateTime.class) {
            return parseDateTime(text);
        }
        if (targetClass == LocalDate.class) {
            return parseDate(text);
        }
        // Classe declarada desconhecida/generica: tenta data+hora primeiro, depois so data.
        LocalDateTime ldt = parseDateTime(text);
        if (ldt != null) {
            return ldt;
        }
        return parseDate(text);
    }

    private static LocalDateTime parseDateTime(String text) {
        for (DateTimeFormatter f : DATETIME_IN) {
            try {
                return LocalDateTime.parse(text, f);
            } catch (DateTimeParseException ignore) {
                // tenta o proximo formato
            }
        }
        // Usuario digitou so a data numa coluna de data+hora: hora vira meia-noite.
        LocalDate d = parseDate(text);
        return d == null ? null : d.atStartOfDay();
    }

    private static LocalDate parseDate(String text) {
        for (DateTimeFormatter f : DATE_IN) {
            try {
                return LocalDate.parse(text, f);
            } catch (DateTimeParseException ignore) {
                // tenta o proximo formato
            }
        }
        return null;
    }

    private static String expectedFormatHint(Class<?> targetClass) {
        boolean withTime = targetClass == Timestamp.class || targetClass == LocalDateTime.class;
        return withTime ? "dd/MM/yyyy HH:mm:ss" : "dd/MM/yyyy";
    }
}
