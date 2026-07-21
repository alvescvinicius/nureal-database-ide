package com.nureal.ide.ui.components;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;

import com.nureal.ide.ui.Typography;

/**
 * Agrupamento de conteudo do Nureal Design System — titulo em versalete
 * (ver {@link Typography#sectionHeader}, MESMO estilo ja usado por
 * "CONEXOES"/"OBJETOS"/"HISTORICO"/"QUERIES SALVAS") seguido do conteudo,
 * sem borda nenhuma — a separacao entre secoes vem de ESPACO (ver
 * {@link NTheme#SPACE_MD}), nunca de uma linha divisoria (principio do
 * NDS: "divisorias deixam de ser linhas, passam a ser espaco").
 * <p>
 * Diferente de {@link NCard}: {@code NSection} nao tem fundo/cantos
 * arredondados proprios — e so um agrupamento logico dentro de um painel
 * ja existente (ex.: dividir a barra lateral em blocos), nao uma
 * superficie elevada.
 */
public final class NSection extends JPanel {

    private static final long serialVersionUID = 1L;

    public NSection(String title) {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setOpaque(false);
        setAlignmentX(LEFT_ALIGNMENT);
        if (title != null && !title.isBlank()) {
            JComponent header = Typography.sectionHeader(title);
            header.setAlignmentX(LEFT_ALIGNMENT);
            add(header);
            add(Box.createVerticalStrut(NTheme.SPACE_XS));
        }
    }

    public NSection addContent(JComponent content) {
        content.setAlignmentX(LEFT_ALIGNMENT);
        add(content);
        return this;
    }
}
