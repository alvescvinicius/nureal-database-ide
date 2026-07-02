package com.nureal.ide.ui;

import javax.swing.JTextField;
import javax.swing.UIManager;

/**
 * "Rotulo" de texto selecionavel/copiavel (Ctrl+C) — um {@link JTextField}
 * NAO editavel disfarcado de {@code JLabel} (sem borda, sem fundo, cor de
 * texto padrao), para qualquer lugar que so precisava exibir um valor
 * (antes, um {@code JLabel} comum, sem selecao de texto). Pedido explicito
 * do usuario: "qualquer texto aqui dentro pode ser selecionado e dado
 * Ctrl+C, ate nas propriedades" — usado por
 * {@code MainWindow#showObjectProperties} (titulo/subtitulo do dialogo de
 * propriedades) e por {@link ColumnMetadataPopup} (dialogo "Informacoes da
 * coluna", onde esse truque nasceu — ver seu javadoc para o motivo de nao
 * usar isto no popup de HOVER, que continua um JLabel comum de proposito).
 */
final class SelectableLabel {

    private SelectableLabel() {
    }

    /**
     * Um JTextField com aparencia de JLabel, mas selecionavel/copiavel com
     * Ctrl+C. Sem "FlatClientProperties.STYLE: focusWidth" de proposito —
     * essa chave de estilo nao existe para JTextField nesta versao do
     * FlatLaf (so para botoes) e derrubava a aplicacao com
     * {@code UnknownStyleException} ao abrir o dialogo de Propriedades. Sem
     * borda (jah removida abaixo) nao ha o que o focusWidth precisaria
     * reservar espaco mesmo, entao nao faz falta.
     */
    static JTextField of(String value) {
        JTextField field = new JTextField(value);
        field.setEditable(false);
        field.setBorder(null);
        field.setOpaque(false);
        field.setForeground(UIManager.getColor("Label.foreground"));
        return field;
    }
}
