package com.nureal.ide.ui;

import com.nureal.ide.core.connection.ConnectionProfile;
import com.nureal.ide.core.connection.ConnectionStore;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.Box;
import javax.swing.DefaultListModel;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagLayout;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Painel lateral com as conexoes salvas, no estilo de cartoes.
 * Duplo-clique (ou "Conectar" no menu de contexto) conecta. "Nova" cadastra.
 */
public class ConnectionsPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    /**
     * Altura padrao (nao-escalada) do cartao de conexao — reduzida de 54 para
     * 34 quando o cartao passou a mostrar so o nome (ver {@link ConnectionRenderer}),
     * de 34 para 26 (com uma unica linha de texto, 34 ficava bem mais alto que
     * a arvore de Objetos), e agora de 26 para 24: desde que a arvore de
     * Objetos ganhou icone de tipo em toda linha abrivel (Rodada 2, ver
     * {@code ObjectTreeCellRenderer#typeIcon}), as duas listas passaram a
     * carregar a MESMA composicao "icone pequeno + texto", entao nao havia
     * mais motivo pra manter alturas diferentes so por seguranca. Este valor
     * agora e a UNICA fonte de verdade tambem para a arvore de Objetos (ver
     * {@code MainWindow#buildObjectBrowser}/{@code #refreshDynamicSizing}) —
     * as duas compartilham a mesma constante em vez de dois numeros magicos
     * proximos, mas nao identicos, em arquivos diferentes.
     */
    static final int DEFAULT_ROW_HEIGHT = 24;

    private final ConnectionStore store;
    private final Consumer<ConnectionProfile> connectAction;
    private final Consumer<ConnectionProfile> disconnectAction;
    private final DefaultListModel<ConnectionProfile> model = new DefaultListModel<>();
    private final JList<ConnectionProfile> list = new JList<>(model);
    private final JTextField search = new JTextField();
    /**
     * Alterna entre a lista e o estado vazio (ver {@link #buildEmptyState()})
     * — mesma receita (CardLayout + JLabel de icone/titulo/subtitulo) que
     * {@code MainWindow#buildEmptyState}/{@code #resultsCards} ja usa pro
     * painel de Resultados, pra esta lista nao ser a unica area do app sem
     * nenhuma pista visual quando fica sem nada pra mostrar.
     */
    private final JPanel listCards = new JPanel(new CardLayout());
    private JLabel emptyTitle;
    private JLabel emptySub;
    private final Set<String> connectedNames = new HashSet<>();
    private List<ConnectionProfile> all = new ArrayList<>();
    private String connectingName;
    /** Qual conexao e a workspace ATIVA agora (aba do editor visivel no momento) —
     * ver {@link #setActiveName}. Diferente de "conectada": varias podem estar
     * conectadas ao mesmo tempo, mas so uma tem suas abas na tela. */
    private String activeName;

    public ConnectionsPanel(ConnectionStore store, Consumer<ConnectionProfile> connectAction,
            Consumer<ConnectionProfile> disconnectAction) {
        super(new BorderLayout(0, 8));
        this.store = store;
        this.connectAction = connectAction;
        this.disconnectAction = disconnectAction;
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        add(buildHeader(), BorderLayout.NORTH);
        add(buildList(), BorderLayout.CENTER);

        reload();
    }

    /**
     * Sem isto, a cor de SELECAO da lista ficava CONGELADA na paleta de
     * quando o painel foi construido (chamado uma unica vez, em
     * {@link #buildList}) — alternar claro/escuro nao reflete sozinho em
     * {@code list.setSelectionBackground/Foreground} porque um {@code Color}
     * java e um valor imutavel copiado na hora da chamada, nao uma
     * referencia "viva" a {@link GridTheme}. Mesma familia de bug ja
     * corrigida em {@code ResultGrid}/{@code SqlEditorPane}/{@code FkInspectorWindow}
     * — faltava aqui (e em {@code HistoryPanel}/{@code SavedQueriesPanel},
     * ver la). Guard contra {@code null}: o PRIMEIRO {@code updateUI()}
     * deste painel e disparado pelo proprio {@code super(new BorderLayout(...))}
     * do construtor, ANTES de {@link #list} ser de fato inicializado.
     */
    @Override
    public void updateUI() {
        super.updateUI();
        if (list != null) {
            list.setSelectionBackground(GridTheme.SELECTION_BACKGROUND);
            list.setSelectionForeground(GridTheme.SELECTION_FOREGROUND);
        }
    }

    private JComponent buildHeader() {
        // Ver Typography#sectionHeader: MESMA receita de "OBJETOS" (MainWindow),
        // "HISTORICO" e "QUERIES SALVAS" — ponto unico, sem copia colada.
        JLabel title = Typography.sectionHeader("CONEXOES");

        // Botao SO DE ICONE (nao mais texto "Nova" com contorno) — antes este
        // era o UNICO cabecalho de painel lateral com um idioma de botao
        // diferente do painel de Objetos (que ja usa icones-so no cabecalho,
        // ver MainWindow#buildObjectBrowserPanel/createSchemaButton). Spec de
        // padronizacao visual: "todos os paineis devem possuir cabecalhos
        // iguais" — mesmo icone (IconType.NEW), mesmo tamanho (13) e mesma
        // cor (GridTheme.MUTED_TEXT) que o botao equivalente do painel de
        // Objetos, mesmo estilo (Buttons#styleIconButton).
        // Buttons.iconButton (nao mais "new JButton(Icons.get(...))" solto):
        // o icone se refaz sozinho a cada troca de tema — antes ficava
        // congelado na cor MUTED_TEXT do tema em que a janela abriu (mesmo
        // bug sistemico corrigido no botao equivalente do painel de Objetos,
        // ver javadoc de Buttons#iconButton).
        JButton novo = Buttons.iconButton(IconType.NEW, 13, () -> GridTheme.MUTED_TEXT);
        novo.setToolTipText("Nova conexao");
        novo.addActionListener(e -> onNew());

        JPanel titleRow = new JPanel(new BorderLayout());
        titleRow.setOpaque(false);
        titleRow.add(title, BorderLayout.WEST);
        titleRow.add(novo, BorderLayout.EAST);

        // Busca por nome/host/schema — pedido explicito do usuario, que tem
        // ~15 conexoes salvas na empresa e precisava de um jeito rapido de
        // achar a certa em vez de rolar a lista inteira (mesmo padrao visual
        // do campo de busca do SavedQueriesPanel).
        search.putClientProperty("JTextField.placeholderText", "Buscar por nome, host ou schema...");
        search.putClientProperty("JTextField.showClearButton", true);
        search.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                applyFilter();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                applyFilter();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                applyFilter();
            }
        });

        JPanel header = new JPanel(new BorderLayout(0, 6));
        header.setOpaque(false);
        header.add(titleRow, BorderLayout.NORTH);
        header.add(search, BorderLayout.SOUTH);
        return header;
    }

    private JComponent buildList() {
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setFixedCellHeight(DEFAULT_ROW_HEIGHT);
        // Sem isto, a linha selecionada usava o default do FlatLaf.properties
        // (List.selectionBackground = accent verde SOLIDO, #059669) — bem
        // mais "gritante" que o cinza neutro que a arvore de Objetos e a
        // grade de resultados usam pra selecao (ver ObjectTreeCellRenderer,
        // GridTheme.SELECTION_BACKGROUND), quebrando a consistencia visual
        // entre as duas listas/arvores do app (pedido da revisao: "mesmos
        // estados de... selecao"). Mesmo tom neutro da grade em vez de um
        // terceiro cinza proprio so pra esta lista.
        list.setSelectionBackground(GridTheme.SELECTION_BACKGROUND);
        list.setSelectionForeground(GridTheme.SELECTION_FOREGROUND);
        list.setCellRenderer(new ConnectionRenderer());
        TreeHoverTracker.installOnList(list);
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    connectSelected();
                }
            }

            @Override
            public void mousePressed(MouseEvent e) {
                maybeMenu(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                maybeMenu(e);
            }
        });

        JScrollPane sp = new JScrollPane(list);
        sp.setBorder(BorderFactory.createEmptyBorder());

        listCards.add(sp, "list");
        listCards.add(buildEmptyState(), "empty");
        return listCards;
    }

    /**
     * Estado vazio da lista: nenhuma conexao cadastrada ainda, OU a busca nao
     * encontrou nada — texto varia conforme o caso (ver {@link #updateEmptyState}).
     * Mesma composicao (icone 40px reativo ao tema + titulo + subtitulo) que
     * {@code MainWindow#buildEmptyState} usa pro painel de Resultados, pra
     * manter um UNICO "idioma" de estado vazio em toda a IDE.
     */
    private JComponent buildEmptyState() {
        JLabel icon = new JLabel();
        Buttons.bindThemedIcon(icon, IconType.CONNECTION, 40, () -> GridTheme.MUTED_TEXT);
        icon.setAlignmentX(Component.CENTER_ALIGNMENT);

        emptyTitle = new JLabel();
        emptyTitle.setFont(emptyTitle.getFont().deriveFont(13f));
        Typography.primary(emptyTitle);
        emptyTitle.setAlignmentX(Component.CENTER_ALIGNMENT);

        emptySub = new JLabel();
        Typography.tertiary(emptySub);
        emptySub.setAlignmentX(Component.CENTER_ALIGNMENT);

        JPanel box = new JPanel();
        box.setOpaque(false);
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        box.add(icon);
        box.add(Box.createVerticalStrut(10));
        box.add(emptyTitle);
        box.add(Box.createVerticalStrut(2));
        box.add(emptySub);

        JPanel center = new JPanel(new GridBagLayout());
        center.setOpaque(false);
        center.add(box);
        return center;
    }

    /**
     * Mostra a lista ou o estado vazio (ver {@link #listCards}), com o texto
     * certo pro caso: nenhuma conexao cadastrada (lista de {@link #all} vazia
     * de verdade) e diferente de busca sem resultado (ha conexoes, mas
     * nenhuma bate com {@link #search}) — cada um pede uma acao diferente do
     * usuario (cadastrar vs. limpar o filtro).
     */
    private void updateEmptyState(boolean empty) {
        ((CardLayout) listCards.getLayout()).show(listCards, empty ? "empty" : "list");
        if (!empty || emptyTitle == null) {
            return;
        }
        String query = search.getText() == null ? "" : search.getText().trim();
        if (all.isEmpty()) {
            emptyTitle.setText("Nenhuma conexao cadastrada");
            emptySub.setText("Clique em + para criar a primeira");
        } else {
            emptyTitle.setText("Nenhuma conexao encontrada");
            emptySub.setText(query.isEmpty() ? "Tente outro termo de busca" : "Nada bate com \"" + query + "\"");
        }
    }

    private void maybeMenu(MouseEvent e) {
        if (!e.isPopupTrigger()) {
            return;
        }
        int idx = list.locationToIndex(e.getPoint());
        if (idx >= 0) {
            list.setSelectedIndex(idx);
        }
        ConnectionProfile selected = list.getSelectedValue();
        boolean connected = selected != null && connectedNames.contains(selected.name());

        JPopupMenu menu = new JPopupMenu();
        // Os 4 itens deste menu agora seguem a MESMA receita (icone 15px,
        // GridTheme.HEADER_FOREGROUND) — antes so "Editar.../Excluir" tinham
        // icone, "Conectar/Desconectar" ficavam so texto no MESMO menu,
        // inconsistencia interna (nao uma questao de outro painel).
        JMenuItem connect = new JMenuItem("Conectar");
        connect.setIcon(Icons.get(IconType.CONNECTION, 15, GridTheme.HEADER_FOREGROUND));
        connect.setEnabled(!connected);
        connect.addActionListener(a -> connectSelected());
        JMenuItem disconnect = new JMenuItem("Desconectar");
        disconnect.setIcon(Icons.get(IconType.DISCONNECT, 15, GridTheme.HEADER_FOREGROUND));
        disconnect.setEnabled(connected);
        disconnect.addActionListener(a -> disconnectSelected());
        JMenuItem edit = new JMenuItem("Editar...");
        edit.setIcon(Icons.get(IconType.EDIT, 15, GridTheme.HEADER_FOREGROUND));
        edit.addActionListener(a -> onEdit());
        JMenuItem delete = new JMenuItem("Excluir");
        delete.setIcon(Icons.get(IconType.DELETE, 15, GridTheme.HEADER_FOREGROUND));
        delete.addActionListener(a -> onDelete());
        menu.add(connect);
        menu.add(disconnect);
        menu.addSeparator();
        menu.add(edit);
        menu.add(delete);
        menu.show(list, e.getX(), e.getY());
    }

    /** Recarrega a lista a partir do arquivo. */
    public void reload() {
        try {
            all = new ArrayList<>(store.load());
        } catch (IOException e) {
            all = new ArrayList<>();
            // Centraliza na JANELA (nao neste painel, que fica na lateral) —
            // ver DialogUtil.
            JOptionPane.showMessageDialog(DialogUtil.owner(this),
                    "Nao foi possivel ler as conexoes:\n" + e.getMessage(),
                    "Conexoes", JOptionPane.WARNING_MESSAGE);
        }
        applyFilter();
    }

    /**
     * Paleta fixa de cores usada para diferenciar visualmente cada CONEXAO
     * (nao o tema claro/escuro) — usada no dot da lista (ver
     * {@link ConnectionRenderer}), no dot de cada aba do editor (ver
     * {@code MainWindow#updateWorkspaceContextBar}) e na faixa do inspetor de
     * FK, pra dar a MESMA identidade visual em toda a IDE.
     * <p>
     * DE PROPOSITO sem vermelho, laranja ou amarelo: essas cores ja tem
     * significado de STATUS nesta mesma IDE (amarelo/laranja = "conectando",
     * vermelho = erro/perigo) — usa-las tambem como identidade de conexao
     * fazia uma conexao perfeitamente conectada "parecer" com problema/
     * desconectada so pela cor calhar de cair no vermelho/laranja (bug visual
     * relatado pelo usuario). Paleta toda em tons frios (azul/roxo/rosa/teal/
     * indigo/ciano/fucsia/ardosia), sem nenhuma dessas 3 cores de status.
     */
    private static final Color[] WORKSPACE_PALETTE = {
            new Color(0x2563EB), // azul
            new Color(0x0891B2), // ciano
            new Color(0x0D9488), // teal
            new Color(0x4F46E5), // indigo
            new Color(0x7C3AED), // roxo
            new Color(0xC026D3), // fucsia
            new Color(0xDB2777), // rosa
            new Color(0x64748B), // ardosia (azul-acinzentado neutro)
    };

    /**
     * Cor de identidade da conexao {@code name} — pacote-privado (nao mais
     * private): {@code MainWindow} tambem usa (ver
     * {@code MainWindow#colorForWorkspace}, que so delega pra ca).
     * <p>
     * Indice pela POSICAO da conexao na ordem de cadastro ({@link #all} —
     * NAO a lista exibida, que reordena as conectadas pra cima em
     * {@link #applyFilter}), nao mais pelo hash do nome: hash podia (e
     * acontecia na pratica) atribuir a MESMA cor a duas conexoes diferentes
     * abertas ao mesmo tempo, confundindo a identidade visual (bug relatado
     * pelo usuario). Por posicao de cadastro, cada uma das primeiras 8
     * conexoes cadastradas ganha uma cor diferente garantida; so se repete a
     * partir da 9a conexao cadastrada.
     */
    Color colorForWorkspace(String name) {
        if (name == null || name.isBlank()) {
            // Cinza neutro do tema (nao um literal proprio) — antes ficava
            // preso no tom claro em qualquer modo, GridTheme.MUTED_TEXT
            // acompanha claro/escuro (ver GridTheme#applyPalette).
            return GridTheme.MUTED_TEXT;
        }
        int idx = -1;
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).name().equals(name)) {
                idx = i;
                break;
            }
        }
        // Fallback (conexao nao encontrada em "all" — ex.: ja foi excluida,
        // mas uma aba antiga ainda guarda o nome): hash do nome, melhor que
        // nao ter cor nenhuma, mesmo sem a garantia de unicidade de acima.
        if (idx < 0) {
            idx = Math.floorMod(name.hashCode(), WORKSPACE_PALETTE.length);
        } else {
            idx = idx % WORKSPACE_PALETTE.length;
        }
        return WORKSPACE_PALETTE[idx];
    }

    private void persist() {
        try {
            store.save(all);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(DialogUtil.owner(this),
                    "Nao foi possivel salvar as conexoes:\n" + e.getMessage(),
                    "Conexoes", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Filtra {@link #all} pela busca (nome/host/schema) e reordena com as
     * conectadas SEMPRE primeiro — pedido explicito do usuario, que tem
     * varias conexoes salvas e quer achar/ver as ativas rapido, sem depender
     * da ordem em que foram cadastradas. Preserva a selecao atual quando ela
     * continua visivel apos o filtro.
     */
    private void applyFilter() {
        ConnectionProfile previouslySelected = list.getSelectedValue();
        String f = search.getText() == null ? "" : search.getText().trim().toLowerCase(Locale.ROOT);
        List<ConnectionProfile> filtered = new ArrayList<>();
        for (ConnectionProfile p : all) {
            if (f.isEmpty() || matches(p, f)) {
                filtered.add(p);
            }
        }
        filtered.sort(Comparator.comparing((ConnectionProfile p) -> !connectedNames.contains(p.name())));
        model.clear();
        for (ConnectionProfile p : filtered) {
            model.addElement(p);
        }
        updateEmptyState(filtered.isEmpty());
        if (previouslySelected != null) {
            list.setSelectedValue(previouslySelected, false);
        }
    }

    private static boolean matches(ConnectionProfile p, String f) {
        return p.name().toLowerCase(Locale.ROOT).contains(f)
                || p.host().toLowerCase(Locale.ROOT).contains(f)
                || p.schema().toLowerCase(Locale.ROOT).contains(f);
    }

    private void onNew() {
        ConnectionProfile created = ConnectionEditDialog.show(this, null, name -> nameTaken(name, null));
        if (created != null) {
            all.add(created);
            persist();
            applyFilter();
            list.setSelectedValue(created, true);
        }
    }

    private void onEdit() {
        ConnectionProfile selected = list.getSelectedValue();
        if (selected == null) {
            return;
        }
        ConnectionProfile edited = ConnectionEditDialog.show(this, selected, name -> nameTaken(name, selected));
        if (edited != null) {
            int idx = all.indexOf(selected);
            if (idx >= 0) {
                all.set(idx, edited);
            }
            persist();
            applyFilter();
            list.setSelectedValue(edited, true);
        }
    }

    /**
     * {@code true} se alguma conexao (diferente de {@code excluding}, usado
     * ao editar para nao acusar o proprio registro) ja usa {@code name} —
     * comparacao sem diferenciar maiusculas/minusculas, pois o nome e o que
     * identifica a conexao na UI (lista, indicador de "conectado" etc.).
     */
    private boolean nameTaken(String name, ConnectionProfile excluding) {
        for (ConnectionProfile p : all) {
            if (p == excluding) {
                continue;
            }
            if (p.name().equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    private void onDelete() {
        ConnectionProfile p = list.getSelectedValue();
        if (p == null) {
            return;
        }
        int ok = JOptionPane.showConfirmDialog(DialogUtil.owner(this),
                "Excluir a conexao \"" + p.name() + "\"?",
                "Excluir conexao", JOptionPane.YES_NO_OPTION);
        if (ok == JOptionPane.YES_OPTION) {
            all.remove(p);
            persist();
            applyFilter();
        }
    }

    private void connectSelected() {
        ConnectionProfile p = list.getSelectedValue();
        if (p != null) {
            connectAction.accept(p);
        }
    }

    private void disconnectSelected() {
        ConnectionProfile p = list.getSelectedValue();
        if (p != null) {
            disconnectAction.accept(p);
        }
    }

    /** Ajusta a altura de cada cartao da lista (usado pelo zoom/modo compacto). */
    public void setRowHeight(int height) {
        list.setFixedCellHeight(height);
        list.revalidate();
        list.repaint();
    }

    /**
     * Define o conjunto de conexoes atualmente conectadas (bolinha verde) —
     * tambem reordena a lista para trazer as conectadas para o topo (ver
     * {@link #applyFilter()}).
     */
    public void setConnectedNames(Set<String> names) {
        connectedNames.clear();
        if (names != null) {
            connectedNames.addAll(names);
        }
        connectingName = null;
        applyFilter();
    }

    /** Marca qual conexao esta conectando (bolinha ambar). */
    public void setConnecting(ConnectionProfile profile) {
        this.connectingName = (profile == null) ? null : profile.name();
        list.repaint();
    }

    /**
     * Marca qual conexao e a workspace ATIVA agora — a que "dono" das abas
     * visiveis no editor neste exato momento (ver {@code MainWindow#activateWorkspace}).
     * So ELA ganha a tarja de identidade (cor propria, ver {@link ConnectionRenderer}):
     * com varias conexoes conectadas ao mesmo tempo, e a unica forma de saber,
     * so olhando a lista, qual delas e a que esta na tela agora (as outras
     * continuam com o dot verde normal de "conectada", sem a tarja).
     * {@code name == null} = nenhuma (rascunho sem conexao ativo).
     */
    public void setActiveName(String name) {
        this.activeName = name;
        list.repaint();
    }

    /**
     * Pequeno circulo de status (verde = conectado, ambar = conectando, cinza
     * = desconectado). Visibilidade de pacote (nao private): reaproveitado
     * por {@link ObjectTreeCellRenderer} para a MESMA bolinha na raiz da
     * arvore de objetos (schema), garantindo o mesmo indicador visual da
     * conexao em dois lugares diferentes da UI.
     */
    static Icon statusDot(Color color) {
        return new Icon() {
            @Override
            public int getIconWidth() {
                return 10;
            }

            @Override
            public int getIconHeight() {
                return 10;
            }

            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(color);
                g2.fillOval(x + 1, y + 1, 8, 8);
                g2.dispose();
            }
        };
    }

    /**
     * Renderiza cada conexao como uma linha compacta: status + so o nome
     * dado pelo usuario (sem a segunda linha "usuario@host:porta/schema") —
     * pedido explicito de quem tem muitas conexoes salvas (~15 na empresa) e
     * queria ver mais linhas de uma vez sem rolar. O destino completo continua
     * disponivel via tooltip, para quem precisar conferir sem abrir "Editar".
     */
    private final class ConnectionRenderer extends javax.swing.DefaultListCellRenderer {
        private static final long serialVersionUID = 1L;

        @Override
        public Component getListCellRendererComponent(
                JList<?> list, Object value, int index,
                boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            setHorizontalAlignment(SwingConstants.LEFT);
            setBorder(BorderFactory.createEmptyBorder(4, 12, 4, 12));
            setIconTextGap(10);
            // Hover (linha sob o mouse, sem selecionar) — mesmo destaque suave
            // da arvore de Objetos e da grade de resultados (ver
            // TreeHoverTracker/GridTheme.HOVER_BACKGROUND); faltava aqui antes.
            // Selecao sempre tem prioridade visual: so pinta hover quando a
            // linha NAO esta selecionada.
            if (!isSelected && index == TreeHoverTracker.hoverRow(list)) {
                setOpaque(true);
                setBackground(GridTheme.HOVER_BACKGROUND);
            }
            if (value instanceof ConnectionProfile p) {
                // Cor do dot: status de conexao (conectado/conectando/ocioso) —
                // sinal PRINCIPAL, nao muda com a identidade do workspace.
                // A tarja lateral (cor propria da conexao, ver
                // #colorForWorkspace) so aparece na conexao ATIVA
                // (a dona das abas visiveis no editor agora, ver setActiveName) —
                // com varias conectadas ao mesmo tempo, e o unico jeito de saber
                // qual delas e a que esta na tela sem clicar em cada uma.
                Color dotColor;
                boolean connected = connectedNames.contains(p.name());
                boolean active = p.name().equals(activeName);
                if (connected) {
                    // Mesmo verde de MainWindow#setConnectedState (ACCENT da
                    // marca) — antes um literal PROPRIO (0x059669, que so por
                    // coincidencia ja era EXATAMENTE o mesmo valor de ACCENT).
                    dotColor = MainWindow.ACCENT;
                } else if (p.name().equals(connectingName)) {
                    // Mesmo ambar de MainWindow#setConnectingState.
                    dotColor = GridTheme.HEADER_HIGHLIGHT_BORDER;
                } else {
                    dotColor = new Color(0xC4C9D1);
                }
                setIcon(statusDot(dotColor));
                setBorder(active
                        ? BorderFactory.createCompoundBorder(
                                BorderFactory.createMatteBorder(0, 3, 0, 0, colorForWorkspace(p.name())),
                                BorderFactory.createEmptyBorder(4, 9, 4, 12))
                        : BorderFactory.createEmptyBorder(4, 12, 4, 12));
                // A tarja lateral colorida (acima) e o UNICO sinal hoje de
                // qual conexao esta ativa — quem nao distingue bem cor (ou so
                // olhou rapido) nao teria como saber sem ela. Sufixo textual
                // simples como segundo sinal, independente de cor (pedido da
                // revisao: "identificar... sem depender apenas de cores").
                setText(active ? p.name() + "  ·  atual" : p.name());
                setFont(getFont().deriveFont(connectedNames.contains(p.name()) ? Font.BOLD : Font.PLAIN));
                setToolTipText(p.user() + "@" + p.host() + ":" + p.port() + "/" + p.schema());
            }
            return this;
        }
    }
}
