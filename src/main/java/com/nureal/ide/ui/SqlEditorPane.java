package com.nureal.ide.ui;

import com.nureal.ide.core.autocomplete.SqlCompletionProvider;
import com.nureal.ide.core.format.SqlFormatter;
import org.fife.ui.autocomplete.AutoCompletion;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rtextarea.RTextScrollPane;
import org.fife.ui.rtextarea.SearchContext;
import org.fife.ui.rtextarea.SearchEngine;
import org.fife.ui.rtextarea.SearchResult;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.KeyStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.font.TextAttribute;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Uma aba de edicao SQL: editor com syntax highlighting e autocomplete.
 * Cada aba tem seu proprio editor, compartilhando o mesmo provider de sugestoes.
 */
public class SqlEditorPane extends JPanel {

    private static final long serialVersionUID = 1L;
	private final RSyntaxTextArea textArea;
    private final SqlFormatter formatter = new SqlFormatter();

    private final SearchContext searchContext = new SearchContext();
    private JPanel findBar;
    private JTextField findField;
    private JTextField replaceField;
    private JToggleButton matchCaseBtn;
    private JToggleButton wholeWordBtn;
    private JLabel findStatus;

    public SqlEditorPane(SqlCompletionProvider provider, Runnable onRun) {
        super(new BorderLayout());

        textArea = new RSyntaxTextArea(20, 80);
        textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_SQL);
        textArea.setCodeFoldingEnabled(true);
        textArea.setTabSize(2);
        textArea.setText("");
        textArea.setFont(pickEditorFont(16));
        textArea.setAntiAliasingEnabled(true);
        textArea.setFractionalFontMetricsEnabled(true);
        textArea.setPaintTabLines(true);
        textArea.setHighlightCurrentLine(true);
        // Realces translucidos (verde da marca) para um visual mais suave
        textArea.setCurrentLineHighlightColor(new Color(0x16, 0xA3, 0x4A, 22));
        textArea.setSelectionColor(new Color(0x16, 0xA3, 0x4A, 70));
        textArea.setMarkAllHighlightColor(new Color(0x22, 0xC5, 0x5E, 90));

        AutoCompletion ac = new AutoCompletion(provider);
        ac.setAutoActivationEnabled(true);
        ac.setAutoActivationDelay(200);
        // Mesmo com UMA unica sugestao, mostra o popup em vez de inserir
        // automaticamente: a insercao so ocorre quando o usuario escolhe
        // (clique, Enter ou Ctrl+Espaco), para nao atrapalhar a digitacao.
        ac.setAutoCompleteSingleChoices(false);
        ac.install(textArea);

        // Executa: Ctrl+Enter (preferido) e F5
        textArea.getActionMap().put("run-sql", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                onRun.run();
            }
        });
        textArea.getInputMap().put(
                KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.CTRL_DOWN_MASK), "run-sql");
        textArea.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0), "run-sql");

        // Ctrl+Shift+F formata (beautifier)
        textArea.getInputMap().put(KeyStroke.getKeyStroke("control shift F"), "format-sql");
        textArea.getActionMap().put("format-sql", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                formatText();
            }
        });

        // Ctrl+F / Ctrl+H abrem a barra de localizar e substituir
        AbstractAction showFind = new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                showFindBar();
            }
        };
        textArea.getInputMap().put(KeyStroke.getKeyStroke("control F"), "show-find");
        textArea.getInputMap().put(KeyStroke.getKeyStroke("control H"), "show-find");
        textArea.getActionMap().put("show-find", showFind);

        // Caixa: Ctrl+U / Ctrl+Shift+U -> MAIUSCULAS ; Ctrl+L / Ctrl+Shift+L -> minusculas
        AbstractAction toUpper = new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                changeCase(true);
            }
        };
        AbstractAction toLower = new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                changeCase(false);
            }
        };
        textArea.getActionMap().put("to-upper", toUpper);
        textArea.getActionMap().put("to-lower", toLower);
        textArea.getInputMap().put(KeyStroke.getKeyStroke("control U"), "to-upper");
        textArea.getInputMap().put(KeyStroke.getKeyStroke("control shift U"), "to-upper");
        textArea.getInputMap().put(KeyStroke.getKeyStroke("control L"), "to-lower");
        textArea.getInputMap().put(KeyStroke.getKeyStroke("control shift L"), "to-lower");

        RTextScrollPane scroll = new RTextScrollPane(textArea);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        add(scroll, BorderLayout.CENTER);
        add(buildFindBar(), BorderLayout.SOUTH);
    }

    public RSyntaxTextArea textArea() {
        return textArea;
    }

    /** Usa a selecao, se houver; caso contrario, o texto inteiro. */
    public String currentSql() {
        String selected = textArea.getSelectedText();
        String sql = (selected != null && !selected.isBlank()) ? selected : textArea.getText();
        return sql.trim();
    }

    /**
     * Formata o SQL (beautifier). Se houver selecao, formata apenas a selecao;
     * caso contrario, formata o texto inteiro da aba.
     */
    public void formatText() {
        String selected = textArea.getSelectedText();
        if (selected != null && !selected.isBlank()) {
            textArea.replaceSelection(formatter.format(selected));
            return;
        }
        String all = textArea.getText();
        if (all == null || all.isBlank()) {
            return;
        }
        int caret = textArea.getCaretPosition();
        String formatted = formatter.format(all);
        textArea.setText(formatted);
        textArea.setCaretPosition(Math.min(caret, formatted.length()));
    }

    // ---------- Localizar / Substituir ----------

    private JComponent buildFindBar() {
        findField = new JTextField(22);
        replaceField = new JTextField(22);
        findStatus = new JLabel();
        findStatus.setForeground(new Color(0x6B7280));

        matchCaseBtn = new JToggleButton("Aa");
        matchCaseBtn.setToolTipText("Diferenciar maiusculas/minusculas");
        wholeWordBtn = new JToggleButton("W");
        wholeWordBtn.setToolTipText("Palavra inteira");

        Color iconColor = new Color(0x6B7280);
        JButton prev = new JButton(Icons.chevron(12, iconColor, true));
        prev.setToolTipText("Anterior (Shift+Enter)");
        prev.addActionListener(e -> findPrevious());
        JButton next = new JButton(Icons.chevron(12, iconColor, false));
        next.setToolTipText("Proximo (Enter)");
        next.addActionListener(e -> findNext());
        JButton replaceOne = new JButton("Substituir");
        replaceOne.addActionListener(e -> replaceOne());
        JButton replaceAll = new JButton("Substituir tudo");
        replaceAll.addActionListener(e -> replaceAll());
        JButton close = new JButton(Icons.close(12, iconColor));
        close.setToolTipText("Fechar (Esc)");
        close.addActionListener(e -> hideFindBar());

        findField.addActionListener(e -> findNext());
        replaceField.addActionListener(e -> replaceOne());
        bindKey(findField, "shift ENTER", this::findPrevious);
        bindKey(findField, "ESCAPE", this::hideFindBar);
        bindKey(replaceField, "ESCAPE", this::hideFindBar);

        JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        row1.add(new JLabel("Localizar:"));
        row1.add(findField);
        row1.add(prev);
        row1.add(next);
        row1.add(matchCaseBtn);
        row1.add(wholeWordBtn);
        row1.add(findStatus);

        JPanel row2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        row2.add(new JLabel("Substituir:"));
        row2.add(replaceField);
        row2.add(replaceOne);
        row2.add(replaceAll);
        row2.add(close);

        findBar = new JPanel();
        findBar.setLayout(new BoxLayout(findBar, BoxLayout.Y_AXIS));
        findBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(0xE5E7EB)),
                BorderFactory.createEmptyBorder(2, 6, 2, 6)));
        findBar.add(row1);
        findBar.add(row2);
        findBar.setVisible(false);
        return findBar;
    }

    private static void bindKey(JComponent c, String keyStroke, Runnable action) {
        c.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(keyStroke), keyStroke);
        c.getActionMap().put(keyStroke, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                action.run();
            }
        });
    }

    private void showFindBar() {
        String sel = textArea.getSelectedText();
        if (sel != null && !sel.isEmpty() && !sel.contains("\n")) {
            findField.setText(sel);
        }
        findBar.setVisible(true);
        revalidate();
        findField.requestFocusInWindow();
        findField.selectAll();
    }

    private void hideFindBar() {
        findBar.setVisible(false);
        clearMarks();
        revalidate();
        textArea.requestFocusInWindow();
    }

    /** Limpa o destaque de "marcar todos" sem mexer no texto. */
    private void clearMarks() {
        SearchEngine.markAll(textArea, new SearchContext());
    }

    private void configureSearch(boolean forward) {
        searchContext.setSearchFor(findField.getText());
        searchContext.setReplaceWith(replaceField.getText());
        searchContext.setMatchCase(matchCaseBtn.isSelected());
        searchContext.setWholeWord(wholeWordBtn.isSelected());
        searchContext.setRegularExpression(false);
        searchContext.setSearchForward(forward);
        searchContext.setMarkAll(true);
    }

    private void findNext() {
        if (findField.getText().isEmpty()) {
            return;
        }
        configureSearch(true);
        SearchResult result = SearchEngine.find(textArea, searchContext);
        if (!result.wasFound()) {
            // wrap: recomeca do inicio
            textArea.setCaretPosition(0);
            result = SearchEngine.find(textArea, searchContext);
        }
        updateStatus(result);
    }

    private void findPrevious() {
        if (findField.getText().isEmpty()) {
            return;
        }
        configureSearch(false);
        SearchResult result = SearchEngine.find(textArea, searchContext);
        if (!result.wasFound()) {
            // wrap: recomeca do fim
            textArea.setCaretPosition(textArea.getDocument().getLength());
            result = SearchEngine.find(textArea, searchContext);
        }
        updateStatus(result);
    }

    private void replaceOne() {
        if (findField.getText().isEmpty()) {
            return;
        }
        configureSearch(true);
        SearchResult result = SearchEngine.replace(textArea, searchContext);
        if (!result.wasFound()) {
            textArea.setCaretPosition(0);
            result = SearchEngine.replace(textArea, searchContext);
        }
        updateStatus(result);
    }

    private void replaceAll() {
        if (findField.getText().isEmpty()) {
            return;
        }
        configureSearch(true);
        SearchResult result = SearchEngine.replaceAll(textArea, searchContext);
        findStatus.setText(result.getCount() + " substituicao(oes)");
    }

    private void updateStatus(SearchResult result) {
        int marked = result.getMarkedCount();
        if (!result.wasFound() && marked == 0) {
            findStatus.setText("Nenhum resultado");
        } else {
            findStatus.setText(marked + " ocorrencia(s)");
        }
    }

    // ---------- Fonte e caixa ----------

    /**
     * Escolhe a fonte monoespacada mais moderna disponivel e aplica um peso
     * SEMIBOLD (sintetico) por cima, deixando o texto mais encorpado e legivel
     * em codigos grandes — independente de existir uma variante "Medium" no SO.
     */
    private static Font pickEditorFont(int size) {
        // Familias que ja sao "encorpadas" (peso medio/semibold) — se instaladas,
        // usamos direto, sem aplicar peso sintetico por cima.
        String[] heavy = {
                "JetBrains Mono Medium", "JetBrainsMono Medium",
                "Cascadia Code SemiBold", "Cascadia Mono SemiBold",
                "Fira Code Medium", "Source Code Pro Medium"};
        String[] regular = {
                "JetBrains Mono", "Cascadia Code", "Cascadia Mono", "Fira Code",
                "Source Code Pro", "Consolas", "DejaVu Sans Mono", "Menlo",
                "Monaco", "Liberation Mono", "Courier New"};
        Set<String> available = new HashSet<>(Arrays.asList(GraphicsEnvironment
                .getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));

        for (String family : heavy) {
            if (available.contains(family)) {
                return new Font(family, Font.PLAIN, size);
            }
        }
        Font base = new Font(Font.MONOSPACED, Font.PLAIN, size);
        for (String family : regular) {
            if (available.contains(family)) {
                base = new Font(family, Font.PLAIN, size);
                break;
            }
        }
        // peso semibold sintetico: deixa qualquer fonte mais encorpada e legivel
        return base.deriveFont(Map.of(TextAttribute.WEIGHT, TextAttribute.WEIGHT_SEMIBOLD));
    }

    /**
     * Converte para MAIUSCULAS (upper=true) ou minusculas a selecao atual; se
     * nao houver selecao, usa a palavra sob o cursor.
     */
    private void changeCase(boolean upper) {
        int start = textArea.getSelectionStart();
        int end = textArea.getSelectionEnd();
        String text = textArea.getText();
        if (start == end) {
            int caret = textArea.getCaretPosition();
            int s = caret;
            int e = caret;
            while (s > 0 && isWordChar(text.charAt(s - 1))) {
                s--;
            }
            while (e < text.length() && isWordChar(text.charAt(e))) {
                e++;
            }
            if (s == e) {
                return;
            }
            start = s;
            end = e;
        }
        String selected = text.substring(start, end);
        String replaced = upper
                ? selected.toUpperCase(Locale.ROOT)
                : selected.toLowerCase(Locale.ROOT);
        if (replaced.equals(selected)) {
            return;
        }
        textArea.setSelectionStart(start);
        textArea.setSelectionEnd(end);
        textArea.replaceSelection(replaced);
        textArea.setSelectionStart(start);
        textArea.setSelectionEnd(start + replaced.length());
    }

    private static boolean isWordChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }
}
