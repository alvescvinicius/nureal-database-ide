package com.nureal.ide.compartilhado.designsystem;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

import java.awt.GridBagLayout;

/**
 * "Estado vazio" padrao do NDS: icone 40px reativo ao tema + titulo (peso
 * {@link Typography#primary}) + subtitulo (peso {@link Typography#tertiary}),
 * empilhados e centralizados — usado sempre que uma lista/painel nao tem
 * nada pra mostrar (nenhuma conexao salva, nenhum historico, nenhuma query
 * salva, nenhum resultado ainda).
 * <p>
 * Extraido de 4 copias praticamente identicas encontradas numa auditoria
 * pedida pelo usuario ({@code ConnectionsPanel}, {@code HistoryPanel},
 * {@code SavedQueriesPanel}, {@code ResultsAreaController}) — as copias ja
 * tinham divergido entre si (tamanho de icone 46 vs 40px, espacamento 12/4
 * vs 10/2, fonte do titulo 14f vs 13f, e uma delas esquecia
 * {@code setOpaque(false)} no painel central, sujeito a mostrar um retangulo
 * de fundo fora do tema). Ponto unico agora — qualquer ajuste futuro (ex.:
 * espacamento) se aplica aos 4 lugares de uma vez.
 * <p>
 * {@link #titleLabel()}/{@link #subtitleLabel()} expostos pra quem precisa
 * TROCAR o texto depois de construido (ex.: alternar entre "nada cadastrado
 * ainda" e "nada bate com a busca") — os dois usam
 * {@link Typography#selfStyling} por baixo, entao continuam com a cor certa
 * sozinhos numa troca de tema, sem exigir nenhum cuidado extra de quem usa.
 */
public final class NEmptyState extends JPanel {

    private static final long serialVersionUID = 1L;

    private final JLabel titleLabel;
    private final JLabel subtitleLabel;
    /** Slot opcional pra uma acao (ex.: "+ Criar nova conexao") — ver {@link #addAction}. Vazio por padrao, sem ocupar espaco visivel alem do respiro fixo abaixo do subtitulo. */
    private final JPanel actionSlot = new JPanel();

    public NEmptyState(IconType icon, String title, String subtitle) {
        super(new GridBagLayout());
        setOpaque(false);

        JLabel iconLabel = new JLabel();
        Buttons.bindThemedIcon(iconLabel, icon, 40, () -> GridTheme.MUTED_TEXT);
        iconLabel.setAlignmentX(CENTER_ALIGNMENT);

        titleLabel = Typography.selfStyling(title, label -> {
            label.setFont(label.getFont().deriveFont(13f));
            Typography.primary(label);
        });
        titleLabel.setAlignmentX(CENTER_ALIGNMENT);

        subtitleLabel = Typography.selfStyling(subtitle, Typography::tertiary);
        subtitleLabel.setAlignmentX(CENTER_ALIGNMENT);

        actionSlot.setOpaque(false);
        actionSlot.setLayout(new BoxLayout(actionSlot, BoxLayout.Y_AXIS));
        actionSlot.setAlignmentX(CENTER_ALIGNMENT);

        JPanel box = new JPanel();
        box.setOpaque(false);
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        box.add(iconLabel);
        box.add(Box.createVerticalStrut(10));
        box.add(titleLabel);
        box.add(Box.createVerticalStrut(2));
        box.add(subtitleLabel);
        box.add(Box.createVerticalStrut(12));
        box.add(actionSlot);

        add(box);
    }

    /**
     * Acrescenta uma acao (tipicamente um {@link javax.swing.JButton}) logo
     * abaixo do subtitulo — ex.: "+ Criar nova conexao" no estado vazio de
     * {@code ConnectionsPanel}. Opcional: os outros 3 consumidores deste
     * componente (Historico/Consultas Salvas/Resultados) nao chamam isto,
     * fica so o respiro fixo de 12px sem nenhum botao.
     */
    public void addAction(JComponent action) {
        action.setAlignmentX(CENTER_ALIGNMENT);
        actionSlot.add(action);
        actionSlot.revalidate();
    }

    /** Rotulo do titulo — mutavel via {@code setText} pra estados vazios que mudam de mensagem em tempo de execucao (ex.: "sem itens" vs "busca sem resultado"). */
    public JLabel titleLabel() {
        return titleLabel;
    }

    /** Rotulo do subtitulo — ver {@link #titleLabel()}. */
    public JLabel subtitleLabel() {
        return subtitleLabel;
    }

    /** Conveniencia para quando o chamador so precisa do componente pronto, sem guardar referencia (ex.: estado vazio ESTATICO, texto nunca muda). */
    public static JComponent of(IconType icon, String title, String subtitle) {
        return new NEmptyState(icon, title, subtitle);
    }
}
