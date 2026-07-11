package com.nureal.ide.ui;

import java.awt.Color;
import java.awt.Insets;

import javax.swing.JButton;

import com.formdev.flatlaf.FlatClientProperties;

/**
 * Unico ponto de estilo para os DOIS "papeis" de botao que o app usa em
 * qualquer dialogo/barra secundaria — mesmo raio, espacamento e
 * comportamento em qualquer lugar que apareçam (pedido da revisao visual:
 * "todos os botoes devem seguir o mesmo padrao"). Antes de existir esta
 * classe, o MESMO bloco de 3-4 linhas (roundRect + arc:8;borderWidth:1 +
 * margem, ou ACCENT solido + arc:8 sem borda) estava duplicado em
 * {@code DdlAssistantDialog}, {@code FkInspectorWindow} e
 * {@code CellContentViewer} — qualquer ajuste futuro (raio, espacamento)
 * exigiria lembrar de mudar em todos os lugares ao mesmo tempo.
 * <p>
 * Nao cobre botoes SO DE ICONE (toolbar/breadcrumb, estilo
 * {@code "toolBarButton"}) nem o botao "Executar" verde da barra de
 * ferramentas principal (estilos proprios, ver
 * {@code MainWindow#styleRunButton}) — so a acao SECUNDARIA (contorno) e a
 * acao PRIMARIA com texto (preenchida na cor da marca) de dialogos e barras
 * de acao como {@link ResultStatusBar}.
 */
final class Buttons {

    private Buttons() {
    }

    /** Botao secundario: contorno fino, cantos arredondados, sem preenchimento — a maioria dos botoes do app. */
    static void styleSecondary(JButton button) {
        button.putClientProperty("JButton.buttonType", "roundRect");
        button.putClientProperty(FlatClientProperties.STYLE, "arc: 8; borderWidth: 1");
        button.setMargin(new Insets(4, 10, 4, 10));
    }

    /** Botao primario: preenchido na cor da marca — SO a acao principal/de confirmacao de um dialogo ou barra. */
    static void stylePrimary(JButton button) {
        button.setBackground(MainWindow.ACCENT);
        button.setForeground(Color.WHITE);
        button.setMargin(new Insets(6, 14, 6, 14));
        button.putClientProperty(FlatClientProperties.STYLE, "arc: 8; focusWidth: 0; innerFocusWidth: 0; borderWidth: 0");
    }
}
