package com.nureal.ide.compartilhado.designsystem;

import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Insets;
import java.util.function.Supplier;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;

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
 * Tambem cobre botoes SO DE ICONE (toolbar/breadcrumb, estilo
 * {@code "toolBarButton"} — ver {@link #styleIconButton}); a unica excecao
 * continua o botao "Executar" verde da barra de ferramentas principal
 * (estilo proprio, ver {@code MainWindow#styleRunButton}).
 */
public final class Buttons {

    private Buttons() {
    }

    /** Botao secundario: contorno fino, cantos arredondados, sem preenchimento — a maioria dos botoes do app. */
    public static void styleSecondary(JButton button) {
        button.putClientProperty("JButton.buttonType", "roundRect");
        button.putClientProperty(FlatClientProperties.STYLE, "arc: 8; borderWidth: 1");
        button.setMargin(new Insets(4, 10, 4, 10));
    }

    /** Botao primario: preenchido na cor da marca — SO a acao principal/de confirmacao de um dialogo ou barra. */
    public static void stylePrimary(JButton button) {
        button.setBackground(GridTheme.BRAND_GREEN);
        button.setForeground(Color.WHITE);
        button.setMargin(new Insets(6, 14, 6, 14));
        button.putClientProperty(FlatClientProperties.STYLE, "arc: 8; focusWidth: 0; innerFocusWidth: 0; borderWidth: 0");
    }

    /**
     * Botao SO DE ICONE (toolbar principal, cabecalho do painel de Objetos,
     * barra de resultados, linha de acoes do editor, barra de localizar) —
     * plano, sem preenchimento nem borda visivel, so o hover do FlatLaf. Ate
     * esta ficar centralizado aqui, cada uma das 5 barras que usam este
     * padrao tinha sua PROPRIA margem copiada/colada (5,5,5,5 / 4,4,4,4 /
     * 3,3,3,3 conforme o arquivo) — sem motivo funcional pra divergir, so
     * inconsistencia acumulada (spec de padronizacao visual: "mesmo
     * espacamento interno" em todo componente do mesmo tipo).
     */
    public static void styleIconButton(JButton button) {
        button.putClientProperty("JButton.buttonType", "toolBarButton");
        button.putClientProperty(FlatClientProperties.STYLE, "arc: 8");
        button.setMargin(new Insets(4, 4, 4, 4));
    }

    /**
     * Prende o ICONE de um botao (SO-icone ou com texto, ex.: "Salvar"/
     * "Historico") a paleta atual, reconstruindo-o a cada troca de tema —
     * bug sistemico encontrado na revisao de codigo: {@code Icons.get(type,
     * size, color)} "queima" a cor pedida DENTRO da imagem do icone no
     * momento em que e chamado (ver {@code Icons.VectorIcon}/
     * {@code FlatSVGIcon.ColorFilter}); um botao criado uma unica vez com
     * {@code new JButton(Icons.get(..., GridTheme.X))} ou
     * {@code button.setIcon(Icons.get(...))} fica com o icone CONGELADO na
     * paleta clara/escura de quando foi montado, porque nada volta a chamar
     * {@code Icons.get} depois disso — {@code MainWindow#toggleTheme} so
     * redesenha a CHROME padrao do FlatLaf (via {@code FlatLaf.updateUI()}),
     * nunca o bitmap de um icone ja atribuido. O sintoma so aparece depois do
     * PRIMEIRO alternar de tema (dai passar despercebido): o icone continua
     * com a cor do tema anterior, geralmente ilegivel ou de baixo contraste
     * contra o novo fundo.
     * <p>
     * Mecanismo: {@code JComponent#setUI} (chamado de dentro de
     * {@code updateUI()}) dispara um {@code PropertyChangeEvent} no nome
     * fixo {@code "UI"} SEMPRE que o Look and Feel e trocado/recarregado —
     * ouvir esse evento e mais simples e mais seguro que subclassificar
     * {@code JButton} so para sobrescrever {@code updateUI()} (evita todo o
     * problema de "campos ainda nulos no primeiro updateUI(), disparado de
     * dentro do proprio construtor da superclasse" que outros pontos do app
     * precisam contornar com guarda null, ver {@code ResultGrid}/
     * {@code ConnectionsPanel}). {@code colorSupplier} deve ler um campo
     * MUTAVEL como {@code GridTheme.MUTED_TEXT}, nunca capturar um
     * {@code Color} ja resolvido, senao a troca de tema continua invisivel
     * pra este botao.
     */
    public static void bindThemedIcon(JButton button, IconType type, int size, Supplier<Color> colorSupplier) {
        Runnable refreshIcon = () -> button.setIcon(Icons.get(type, size, colorSupplier.get()));
        refreshIcon.run();
        button.addPropertyChangeListener("UI", e -> refreshIcon.run());
    }

    /** Igual a {@link #bindThemedIcon}, para uma cor FIXA (nao reativa ao tema, ex.: {@link Color#WHITE}). */
    public static void bindThemedIcon(JButton button, IconType type, int size, Color fixedColor) {
        bindThemedIcon(button, type, size, () -> fixedColor);
    }

    /** Igual a {@link #bindThemedIcon(JButton, IconType, int, Supplier)}, para um {@code JLabel} (ex.: icone estatico de estado vazio/placeholder). */
    public static void bindThemedIcon(javax.swing.JLabel label, IconType type, int size, Supplier<Color> colorSupplier) {
        Runnable refreshIcon = () -> label.setIcon(Icons.get(type, size, colorSupplier.get()));
        refreshIcon.run();
        label.addPropertyChangeListener("UI", e -> refreshIcon.run());
    }

    /** Cria um botao SO DE ICONE (ver {@link #styleIconButton}) ja com o icone reativo ao tema (ver {@link #bindThemedIcon}). */
    public static JButton iconButton(IconType type, int size, Supplier<Color> colorSupplier) {
        JButton button = new JButton();
        styleIconButton(button);
        bindThemedIcon(button, type, size, colorSupplier);
        return button;
    }

    /** Igual ao outro {@link #iconButton}, para uma cor FIXA (nao reativa ao tema, ex.: {@link Color#WHITE}). */
    public static JButton iconButton(IconType type, int size, Color fixedColor) {
        return iconButton(type, size, () -> fixedColor);
    }

    /**
     * Rodape padrao dos assistentes de DDL guiados ({@link DdlAssistantDialog},
     * {@link ViewBuilderDialog}, {@link TriggerBuilderDialog},
     * {@link RoutineBuilderDialog}): "Fechar" (fecha {@code dialog}) + a acao
     * primaria do dialogo, no mesmo {@code FlowLayout.RIGHT} — SPEC-0008
     * Etapa 2 encontrou este bloco de ~12 linhas copiado identico nos 4
     * arquivos (so o texto do botao primario mudava). {@code primaryAction}
     * ja deve vir com texto e {@code ActionListener} prontos; este metodo so
     * aplica {@link #stylePrimary} e monta o painel.
     */
    public static JPanel dialogFooter(JDialog dialog, JButton primaryAction) {
        JButton close = new JButton("Fechar");
        close.addActionListener(a -> dialog.dispose());
        styleSecondary(close);
        stylePrimary(primaryAction);

        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(0, 10, 4, 10));
        panel.add(close);
        panel.add(primaryAction);
        return panel;
    }
}
