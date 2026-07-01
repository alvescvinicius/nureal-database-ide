package com.nureal.ide;

import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rsyntaxtextarea.folding.FoldParserManager;

import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.nureal.ide.ui.MainWindow;
import com.nureal.ide.ui.SqlFoldParser;

/**
 * Ponto de entrada da Nureal Database IDE.
 * Aplica o tema FlatLaf (claro por padrao) com as customizacoes da marca e
 * uma fonte de interface moderna.
 */
public class App {

    public static void main(String[] args) {
        // carrega as customizacoes em resources/com/nureal/ide/theme/FlatLaf.properties
        FlatLaf.registerCustomDefaultsSource("com.nureal.ide.theme");

        // Fonte de interface moderna (definida ANTES do setup para ser aplicada)
        UIManager.put("defaultFont", pickUiFont(12));
        FlatLightLaf.setup();

        // Folding (expandir/recolher) para o editor SQL
        FoldParserManager.get().addFoldParserMapping(
                SyntaxConstants.SYNTAX_STYLE_SQL, new SqlFoldParser());

        SwingUtilities.invokeLater(() -> new MainWindow().setVisible(true));
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
