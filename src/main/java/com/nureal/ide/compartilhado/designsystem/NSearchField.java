package com.nureal.ide.compartilhado.designsystem;

import com.formdev.flatlaf.FlatClientProperties;

import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * Campo de busca do Nureal Design System — MESMO padrao ja usado (mas
 * copiado em pelo menos 9 lugares: {@code ConnectionsPanel},
 * {@code MainWindow}, {@code ResultGrid}, {@code HistoryPanel},
 * {@code SavedQueriesPanel}, {@code ErDiagramWindow},
 * {@code EventsReplicationDialog}, {@code ServerStatusDialog}): placeholder +
 * botao de limpar do proprio FlatLaf, mais o boilerplate de
 * {@link DocumentListener} (3 metodos identicos chamando o mesmo filtro) que
 * se repetia em cada tela.
 */
public final class NSearchField extends JTextField {

    private static final long serialVersionUID = 1L;

    public NSearchField(String placeholder) {
        putClientProperty("JTextField.placeholderText", placeholder);
        putClientProperty("JTextField.showClearButton", true);
        putClientProperty(FlatClientProperties.STYLE, "arc: 8");
    }

    /** Chama {@code onChange} a cada mudanca de texto (digitar, colar, limpar) — sem repetir os 3 metodos do {@link DocumentListener}. */
    public NSearchField onTextChange(Runnable onChange) {
        getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                onChange.run();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                onChange.run();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                onChange.run();
            }
        });
        return this;
    }
}
