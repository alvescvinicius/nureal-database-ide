package com.nureal.ide;

import java.awt.AWTEvent;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.Toolkit;
import java.awt.event.MouseEvent;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import javax.swing.AbstractButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rsyntaxtextarea.folding.FoldParserManager;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;
import com.nureal.ide.core.log.AppLogger;
import com.nureal.ide.ui.MainWindow;
import com.nureal.ide.ui.SqlFoldParser;

/**
 * Ponto de entrada da Nureal Database IDE.
 * Aplica o tema FlatLaf (ESCURO por padrao — pedido explicito do usuario;
 * era claro por padrao antes) com as customizacoes da marca e uma fonte de
 * interface moderna.
 */
public class App {

    public static void main(String[] args) {
        AppLogger.init();

        // carrega as customizacoes em resources/com/nureal/ide/theme/FlatLaf.properties
        FlatLaf.registerCustomDefaultsSource("com.nureal.ide.theme");

        // Fonte de interface moderna (definida ANTES do setup para ser aplicada)
        UIManager.put("defaultFont", pickUiFont(12));
        FlatDarkLaf.setup();

        // Barra de titulo customizada (icone da marca + min/max/fechar no
        // mesmo estilo do resto da UI, claro/escuro seguindo o tema) em vez
        // da barra padrao do Windows — pedido explicito do usuario, visto
        // num mockup. TEM que ser chamado ANTES de qualquer JFrame/JDialog
        // ser construido (Swing so aplica a decoracao customizada em janelas
        // criadas DEPOIS desta flag ser ligada). O FlatLaf cuida sozinho de
        // arrastar/redimensionar/maximizar em duplo-clique/encaixe (Aero
        // Snap no Windows) — nao precisamos implementar nada disso na mao.
        JFrame.setDefaultLookAndFeelDecorated(true);
        JDialog.setDefaultLookAndFeelDecorated(true);

        // Folding (expandir/recolher) para o editor SQL
        FoldParserManager.get().addFoldParserMapping(
                SyntaxConstants.SYNTAX_STYLE_SQL, new SqlFoldParser());

        // Maozinha (cursor de clique) ao passar o mouse sobre qualquer botao/icone
        // clicavel do app, sem precisar setCursor(...) em cada botao individualmente.
        installHandCursorOnButtons();

        SwingUtilities.invokeLater(() -> new MainWindow().setVisible(true));
    }

    /**
     * Registra um listener global de mouse que troca o cursor para "maozinha"
     * sempre que o ponteiro entra em qualquer {@link AbstractButton} habilitado
     * (JButton, JToggleButton, itens de menu, checkboxes, etc.) em qualquer
     * janela/dialogo do app, e devolve ao cursor padrao ao sair. Evita ter que
     * chamar setCursor(...) manualmente em cada botao/icone espalhado pelo
     * codigo (toolbar, cabecalhos de painel, barra de status dos resultados
     * etc.), inclusive nos criados no futuro.
     */
    private static void installHandCursorOnButtons() {
        Cursor hand = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR);
        Cursor normal = Cursor.getDefaultCursor();
        Toolkit.getDefaultToolkit().addAWTEventListener(event -> {
            if (!(event instanceof MouseEvent me)) return;
            Component c = me.getComponent();
            if (!(c instanceof AbstractButton b)) return;
            if (me.getID() == MouseEvent.MOUSE_ENTERED) {
                if (b.isEnabled()) {
                    c.setCursor(hand);
                }
            } else if (me.getID() == MouseEvent.MOUSE_EXITED) {
                c.setCursor(normal);
            }
        }, AWTEvent.MOUSE_EVENT_MASK);
    }

    /** Primeira fonte de interface moderna disponivel no sistema. */
    private static Font pickUiFont(int size) {
        String[] preferred = {
                "Segoe UI Variable Text", "Segoe UI", "Inter", "Roboto",
                "Noto Sans", "SF Pro Text", "Liberation Sans", "DejaVu Sans"};
        Set<String> available = new HashSet<>(Arrays.asList(GraphicsEnvironment
                .getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
        for (String family : preferred) {
            if (available.contains(family)) {
                return new Font(family, Font.PLAIN, size);
            }
        }
        return new Font(Font.SANS_SERIF, Font.PLAIN, size);
    }
}
