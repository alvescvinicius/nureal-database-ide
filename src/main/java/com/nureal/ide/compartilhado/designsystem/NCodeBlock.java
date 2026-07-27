package com.nureal.ide.compartilhado.designsystem;

import com.nureal.ide.compartilhado.designsystem.NButton.Kind;

import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JPanel;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rtextarea.RTextScrollPane;


/**
 * Bloco de codigo do Nureal Design System: {@link NCard} com cabecalho +
 * barra de acoes (sempre com "Copiar") + {@link RSyntaxTextArea}. Extraido
 * de {@code MessageRenderer.codeCard} (chat de IA) — primeiro consumidor,
 * mas nao acoplado a ele: quem constroi decide a coloracao/tokenizer via
 * {@link CodeStyler} (SQL usa {@code SqlEditorPane.styleAsReadOnlySql},
 * outros usam o tema generico do RSyntaxTextArea) — {@code ui.components}
 * nunca depende de uma tela especifica como {@code SqlEditorPane}.
 */
public final class NCodeBlock extends NCard {

    private static final long serialVersionUID = 1L;

    /** Aplica syntax highlight/tema a area de codigo — cada chamador decide COMO (SQL semantico, generico, etc.). */
    @FunctionalInterface
    public interface CodeStyler {
        void style(RSyntaxTextArea area);
    }

    private final RSyntaxTextArea area;
    private final JPanel actionsBar;

    public NCodeBlock(String code, NAccent accent, String header, CodeStyler styler) {
        super(accent, header);
        String trimmed = code.stripTrailing();

        area = new RSyntaxTextArea(trimmed);
        styler.style(area);
        area.setRows(Math.min(20, Math.max(1, trimmed.split("\n", -1).length)));
        area.setHighlightCurrentLine(false);

        RTextScrollPane scroll = new RTextScrollPane(area);
        scroll.setLineNumbersEnabled(false);
        scroll.setFoldIndicatorEnabled(false);
        scroll.setBackground(area.getBackground());
        scroll.getGutter().setBackground(area.getBackground());
        scroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, scroll.getPreferredSize().height));
        scroll.setAlignmentX(LEFT_ALIGNMENT);

        actionsBar = new JPanel();
        actionsBar.setLayout(new BoxLayout(actionsBar, BoxLayout.X_AXIS));
        actionsBar.setOpaque(false);
        actionsBar.setAlignmentX(LEFT_ALIGNMENT);
        actionsBar.add(Box.createHorizontalGlue());
        addAction("Copiar", () -> Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection(area.getText()), null));

        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setOpaque(false);
        body.setAlignmentX(LEFT_ALIGNMENT);
        body.add(actionsBar);
        body.add(Box.createVerticalStrut(NTheme.SPACE_XS));
        body.add(scroll);
        addContent(body);
    }

    /** Acrescenta uma acao a barra (ex.: "Executar", "Formatar") — sempre depois de "Copiar". */
    public NCodeBlock addAction(String label, Runnable action) {
        NButton button = new NButton(label, Kind.GHOST);
        button.addActionListener(e -> action.run());
        actionsBar.add(button);
        return this;
    }

    public String getCode() {
        return area.getText();
    }

    public void setCode(String code) {
        area.setText(code);
    }
}
