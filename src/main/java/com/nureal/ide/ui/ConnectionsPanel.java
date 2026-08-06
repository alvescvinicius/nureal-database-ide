package com.nureal.ide.ui;
import com.nureal.ide.compartilhado.designsystem.IconType;
import com.nureal.ide.compartilhado.designsystem.Icons;
import com.nureal.ide.compartilhado.designsystem.Buttons;
import com.nureal.ide.compartilhado.designsystem.Typography;
import com.nureal.ide.compartilhado.designsystem.GridTheme;
import com.nureal.ide.compartilhado.designsystem.NAccent;
import com.nureal.ide.compartilhado.designsystem.NBadge;
import com.nureal.ide.compartilhado.designsystem.NEmptyState;
import com.nureal.ide.compartilhado.designsystem.Spacing;

import com.nureal.ide.modulos.conexoes.dominio.entidades.ConnectionProfile;
import com.nureal.ide.modulos.conexoes.dominio.contratos.ConnectionRepository;
import com.nureal.ide.compartilhado.designsystem.NSearchField;

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
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.FlowLayout;
import java.awt.Window;
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


    private final ConnectionRepository store;
    private final Consumer<ConnectionProfile> connectAction;
    private final Consumer<ConnectionProfile> disconnectAction;
    private final DefaultListModel<ConnectionProfile> model = new DefaultListModel<>();
    private final JList<ConnectionProfile> list = new JList<>(model);
    private final NSearchField search = new NSearchField("Buscar por nome, host ou schema...");
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
    /** Botao "+ Criar nova conexao" do estado vazio — visivel so quando NAO ha nenhuma conexao cadastrada (ver {@link #updateEmptyState}); escondido quando o estado vazio e de BUSCA sem resultado (criar uma conexao nao ajudaria ali). */
    private JButton emptyCreateButton;
    private final Set<String> connectedNames = new HashSet<>();
    private List<ConnectionProfile> all = new ArrayList<>();
    private String connectingName;
    /** Qual conexao e a workspace ATIVA agora (aba do editor visivel no momento) —
     * ver {@link #setActiveName}. Diferente de "conectada": varias podem estar
     * conectadas ao mesmo tempo, mas so uma tem suas abas na tela. */
    private String activeName;
    /**
     * Janela usada como dono dos dialogos deste painel (Nova/Editar/Excluir)
     * quando informada — ver {@link #setOwnerWindow}/{@link #dialogOwner}.
     * Sem isto, {@code DialogUtil.owner(this)} so resolve corretamente
     * enquanto este painel estiver DENTRO de alguma janela (ex.: o popup de
     * "Conexoes salvas" quando aberto) — um atalho que abre "Nova conexao"
     * SEM passar por esse popup primeiro (ver {@code MainWindow}, botao
     * "+ Nova conexao" da barra de conexao) precisa de um dono valido mesmo
     * com o painel momentaneamente sem pai nenhum.
     */
    private Window ownerOverride;

    public ConnectionsPanel(ConnectionRepository store, Consumer<ConnectionProfile> connectAction,
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

    /**
     * Busca + "Nova conexao" numa linha so — sem o titulo "CONEXOES" de
     * antes: agora que este painel abre a partir da barra de conexao no
     * topo da janela (ver {@code MainWindow#buildConnectionBar}), o
     * contexto ja esta claro sem repetir o titulo (visual alinhado a uma
     * referencia trazida pelo usuario). Botao com TEXTO "Nova conexao" (nao
     * mais so icone) — mais descobrivel como acao principal deste dropdown.
     */
    private JComponent buildHeader() {
        JButton novo = new JButton("Nova conexao");
        // Contorno (Buttons.styleSecondary), nao preenchido: o botao verde
        // SOLIDO principal desta tela e o da barra de conexao no topo (ver
        // MainWindow#buildConnectionBar) — este aqui, dentro do dropdown, e
        // uma acao secundaria/de apoio, mesma hierarquia visual do resto do
        // app (Executar em destaque, Formatar/Explicar em contorno).
        Buttons.bindThemedIcon(novo, IconType.NEW, 13, () -> GridTheme.BRAND_GREEN);
        novo.setIconTextGap(6);
        Buttons.styleSecondary(novo);
        novo.setToolTipText("Nova conexao");
        novo.addActionListener(e -> onNew());

        // Busca por nome/host/schema — pedido explicito do usuario, que tem
        // ~15 conexoes salvas na empresa e precisava de um jeito rapido de
        // achar a certa em vez de rolar a lista inteira (mesmo padrao visual
        // do campo de busca do SavedQueriesPanel).
        search.onTextChange(this::applyFilter);

        JPanel header = new JPanel(new BorderLayout(Spacing.SM, 0));
        header.setOpaque(false);
        header.add(search, BorderLayout.CENTER);
        header.add(novo, BorderLayout.EAST);
        return header;
    }

    private JComponent buildList() {
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        // SEM altura fixa (nao mais list.setFixedCellHeight(24)):
        // o cartao de 2 linhas do ConnectionRenderer (nome + usuario@host:porta,
        // mais icones) precisa de mais altura que a linha unica de antes —
        // uma altura FIXA cortaria a segunda linha. Sem fixedCellHeight, o
        // JList mede a altura de cada linha pelo PROPRIO preferredSize do
        // renderer, que já reflete zoom (fonte escala com o resto do app via
        // UIManager, ver MainWindow#applyZoomFont) sem precisar de um numero
        // calculado a parte — ver {@link #setRowHeight}.
        // Sem isto, JList usa o default de 8 linhas "fantasma" pra calcular a
        // altura PREFERIDA (Scrollable#getPreferredScrollableViewportSize),
        // mesmo com a lista vazia ou com so 1-2 itens de verdade — na sidebar
        // unificada (Fase 3 do AI-CHAT-MASTER-PLAN.md), onde este painel fica
        // embutido numa coluna com varias secoes, isso sobrava uma caixa cinza
        // vazia enorme. 3 linhas visiveis por padrao (mesmo valor de
        // SavedQueriesPanel/HistoryPanel, reduzido de 5 na revisao de UX —
        // a maioria dos usuarios tem so 1-3 conexoes cadastradas, e 5 sobrava
        // "espaco fantasma" visivel mesmo com poucas); quem tiver mais rola
        // dentro do proprio painel (ver MainWindow#capMaxHeight).
        list.setVisibleRowCount(3);
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
                    return;
                }
                // Icone "..." de cada linha (ver ConnectionRenderer): abre o
                // MESMO menu do clique direito, so que com um alvo visivel e
                // descobrivel — pedido explicito do usuario ("verifique uma
                // forma boa para o funcionamento e design" apos uma
                // referencia visual mostrando esse icone em cada linha).
                if (e.getClickCount() == 1 && isMenuIconClick(e)) {
                    showRowMenu(e.getPoint());
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
        // NEmptyState (design system, ver seu javadoc): ponto UNICO da
        // receita "icone + titulo + subtitulo" — antes esta era uma de 4
        // copias praticamente identicas (achado numa auditoria pedida pelo
        // usuario) ja com metricas divergentes entre si.
        NEmptyState state = new NEmptyState(IconType.CONNECTION, "", "");
        emptyTitle = state.titleLabel();
        emptySub = state.subtitleLabel();

        // CTA visivel so quando NAO ha NENHUMA conexao cadastrada (ver
        // #updateEmptyState) — visual trazido pelo usuario ("+ Criar nova
        // conexao"). Mesma acao do botao "Nova conexao" do cabecalho
        // ({@link #onNew()}), nenhuma logica nova.
        emptyCreateButton = new JButton("Criar nova conexao");
        Buttons.bindThemedIcon(emptyCreateButton, IconType.NEW, 14, () -> Color.WHITE);
        emptyCreateButton.setIconTextGap(6);
        Buttons.stylePrimary(emptyCreateButton);
        emptyCreateButton.addActionListener(e -> onNew());
        state.addAction(emptyCreateButton);
        return state;
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
        boolean nothingCreatedYet = all.isEmpty();
        if (nothingCreatedYet) {
            emptyTitle.setText("Nenhuma conexao cadastrada");
            emptySub.setText("Crie sua primeira conexao para comecar");
        } else {
            emptyTitle.setText("Nenhuma conexao encontrada");
            emptySub.setText(query.isEmpty() ? "Tente outro termo de busca" : "Nada bate com \"" + query + "\"");
        }
        if (emptyCreateButton != null) {
            emptyCreateButton.setVisible(nothingCreatedYet);
        }
    }

    private void maybeMenu(MouseEvent e) {
        if (!e.isPopupTrigger()) {
            return;
        }
        showRowMenu(e.getPoint());
    }

    /**
     * Zona clicavel do icone "..." de cada linha (ver {@link ConnectionRenderer}):
     * faixa fixa na ponta direita da celula, mesma largura reservada no
     * renderer ({@link ConnectionRenderer#MENU_ICON_ZONE_WIDTH}) — mesma
     * receita ja usada em {@code ResultTableHeader#filterIconAtPoint} pro
     * icone de funil do cabecalho da grade.
     */
    private boolean isMenuIconClick(MouseEvent e) {
        int idx = list.locationToIndex(e.getPoint());
        if (idx < 0) {
            return false;
        }
        java.awt.Rectangle cell = list.getCellBounds(idx, idx);
        if (cell == null) {
            return false;
        }
        int zoneStart = cell.x + cell.width - ConnectionRenderer.MENU_ICON_ZONE_WIDTH;
        return e.getX() >= zoneStart;
    }

    /** Menu de contexto (Conectar/Desconectar/Editar/Excluir) da linha sob {@code point} — usado tanto pelo clique direito ({@link #maybeMenu}) quanto pelo icone "..." visivel em cada linha ({@link #isMenuIconClick}). */
    private void showRowMenu(java.awt.Point point) {
        int idx = list.locationToIndex(point);
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
        menu.show(list, point.x, point.y);
    }

    /** Ver javadoc de {@link #ownerOverride}. */
    void setOwnerWindow(Window w) {
        this.ownerOverride = w;
    }

    /** Janela/componente usado como dono de dialogos abertos por este painel — ver {@link #ownerOverride}. */
    private Component dialogOwner() {
        return (ownerOverride != null) ? ownerOverride : DialogUtil.owner(this);
    }

    /**
     * Abre "Nova conexao" de fora (ver {@code MainWindow}, botao "+ Nova
     * conexao" da barra de conexao no topo da janela) — MESMA logica do
     * botao "+" do cabecalho deste painel ({@link #onNew()}), so exposta
     * como ponto de entrada publico pra nao duplicar a criacao/validacao/
     * persistencia de uma nova conexao em dois lugares.
     */
    public void createNewConnection() {
        onNew();
    }

    /** Recarrega a lista a partir do arquivo. */
    public void reload() {
        try {
            all = new ArrayList<>(store.load());
        } catch (IOException e) {
            all = new ArrayList<>();
            // Centraliza na JANELA (nao neste painel, que fica na lateral) —
            // ver DialogUtil.
            JOptionPane.showMessageDialog(dialogOwner(),
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

    /**
     * Mistura {@code accent} em {@code base} com peso {@code weight}
     * (0..1) — usada pelo tingimento de fundo da linha ativa
     * ({@link ConnectionRenderer}). Calcula a cor SOLIDA final em vez de
     * usar um {@code Color} com alfa: o resultado nao depende de o que foi
     * pintado embaixo antes (ordem de pintura do {@code JList}), sempre o
     * mesmo tom previsivel em claro ou escuro.
     */
    private static Color tint(Color base, Color accent, float weight) {
        int r = Math.round(base.getRed() * (1 - weight) + accent.getRed() * weight);
        int g = Math.round(base.getGreen() * (1 - weight) + accent.getGreen() * weight);
        int b = Math.round(base.getBlue() * (1 - weight) + accent.getBlue() * weight);
        return new Color(r, g, b);
    }

    private void persist() {
        try {
            store.save(all);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(dialogOwner(),
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
        ConnectionProfile created = ConnectionEditDialog.show(dialogOwner(), null, name -> nameTaken(name, null));
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
        ConnectionProfile edited = ConnectionEditDialog.show(dialogOwner(), selected, name -> nameTaken(name, selected));
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
        // WARNING_MESSAGE (nao mais o icone de pergunta padrao): mesmo
        // icone que ObjectDdlActions ja usa pra confirmar exclusao de view/
        // trigger/rotina — os dois sao acoes IRREVERSIVEIS, entao devem
        // parecer igualmente alarmantes (inconsistencia encontrada numa
        // auditoria pedida pelo usuario: excluir uma conexao parecia mais
        // "neutro" que excluir um objeto do banco).
        int ok = JOptionPane.showConfirmDialog(dialogOwner(),
                "Excluir a conexao \"" + p.name() + "\"?",
                "Excluir conexao", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
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

    /**
     * Chamado pelo zoom/modo compacto (ver {@code MainWindow#refreshDynamicSizing})
     * pra manter a densidade das listas do app em sincronia — DELIBERADAMENTE
     * NAO forca mais uma altura fixa por linha aqui: o cartao de 2 linhas do
     * {@link ConnectionRenderer} (nome + usuario@host:porta, mais icones)
     * precisa de mais altura que a linha unica que este numero foi pensado
     * pra descrever, e a fonte ja escala sozinha com o zoom (ver
     * MainWindow#applyZoomFont), entao o JList mede a altura certa por conta
     * propria a partir do preferredSize do renderer. Mantido como metodo (em
     * vez de remover) pra nao quebrar o chamador existente.
     */
    public void setRowHeight(int height) {
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
     * = desconectado), 10px. Visibilidade de pacote (nao private):
     * reaproveitado por {@link ObjectTreeCellRenderer} para a MESMA bolinha
     * na raiz da arvore de objetos (schema), garantindo o mesmo indicador
     * visual da conexao em dois lugares diferentes da UI.
     * <p>
     * Delega pro MESMO {@code IconType.STATUS_DOT} do design system que
     * {@code ConnectionStatusCard}/{@code HistoryPanel} ja usam (em vez de
     * pintar um oval a mao aqui) — antes existiam DUAS implementacoes
     * independentes do mesmo conceito visual (uma pintada a mao aqui, outra
     * via {@code Icons.get} nos outros dois lugares), achado numa auditoria
     * pedida pelo usuario. Mantido como metodo/assinatura proprios (em vez
     * de trocar os 4 chamadores pra {@code Icons.get} direto) so pra nao
     * mexer em call sites que ja funcionam — a UNICA pintura de verdade
     * agora mora em {@link Icons}.
     */
    static Icon statusDot(Color color) {
        return Icons.get(IconType.STATUS_DOT, 10, color);
    }

    /**
     * Renderiza cada conexao como um cartao de 2 linhas (nome em destaque +
     * "usuario@host:porta" discreto), com icone de status, icone de banco,
     * selo "ATUAL" e um icone "..." pra abrir o mesmo menu do clique direito
     * — visual alinhado a uma referencia trazida pelo usuario. NAO e mais um
     * {@code JLabel} ({@code DefaultListCellRenderer}), e sim um
     * {@code JPanel} com sub-componentes proprios: um label so aceita UMA
     * linha/icone, e o cartao da referencia precisa de duas linhas de texto
     * mais 3 icones.
     */
    private final class ConnectionRenderer extends JPanel implements javax.swing.ListCellRenderer<ConnectionProfile> {
        private static final long serialVersionUID = 1L;

        /** Largura da faixa clicavel do icone "..." (ver {@link ConnectionsPanel#isMenuIconClick}). */
        static final int MENU_ICON_ZONE_WIDTH = 30;

        private final JLabel dot = new JLabel();
        private final JLabel dbIcon = new JLabel();
        private final JLabel nameLabel = new JLabel();
        private final JLabel subLabel = new JLabel();
        private final JLabel menuIcon = new JLabel();
        private final JPanel trailing = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));

        ConnectionRenderer() {
            super(new BorderLayout(8, 0));
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 8));

            JPanel leadingIcons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
            leadingIcons.setOpaque(false);
            leadingIcons.add(dot);
            leadingIcons.add(dbIcon);

            JPanel textStack = new JPanel();
            textStack.setOpaque(false);
            textStack.setLayout(new BoxLayout(textStack, BoxLayout.Y_AXIS));
            nameLabel.setAlignmentX(LEFT_ALIGNMENT);
            subLabel.setAlignmentX(LEFT_ALIGNMENT);
            subLabel.setFont(subLabel.getFont().deriveFont(10f));
            textStack.add(nameLabel);
            textStack.add(subLabel);

            trailing.setOpaque(false);
            Buttons.bindThemedIcon(menuIcon, IconType.MORE, 16, () -> GridTheme.MUTED_TEXT);
            menuIcon.setToolTipText("Conectar, editar ou excluir");

            add(leadingIcons, BorderLayout.WEST);
            add(textStack, BorderLayout.CENTER);
            add(trailing, BorderLayout.EAST);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends ConnectionProfile> list, ConnectionProfile p,
                int index, boolean isSelected, boolean cellHasFocus) {
            setOpaque(false);
            setBackground(null);
            // Hover (linha sob o mouse, sem selecionar) — mesmo destaque suave
            // da arvore de Objetos e da grade de resultados (ver
            // TreeHoverTracker/GridTheme.HOVER_BACKGROUND). Selecao sempre
            // tem prioridade visual: so pinta hover quando a linha NAO esta
            // selecionada.
            boolean hovered = !isSelected && index == TreeHoverTracker.hoverRow(list);
            if (isSelected) {
                setOpaque(true);
                setBackground(list.getSelectionBackground());
            } else if (hovered) {
                setOpaque(true);
                setBackground(GridTheme.HOVER_BACKGROUND);
            }

            // Cor do dot: status de conexao (conectado/conectando/ocioso) —
            // sinal PRINCIPAL, nao muda com a identidade do workspace. A
            // tarja lateral (cor propria da conexao, ver #colorForWorkspace)
            // so aparece na conexao ATIVA (a dona das abas visiveis no
            // editor agora, ver setActiveName) — com varias conectadas ao
            // mesmo tempo, e o unico jeito de saber qual delas esta na tela
            // sem clicar em cada uma.
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
            dot.setIcon(statusDot(dotColor));
            Buttons.bindThemedIcon(dbIcon, IconType.DATABASE, 14, () -> GridTheme.MUTED_TEXT);

            setBorder(active
                    ? BorderFactory.createCompoundBorder(
                            BorderFactory.createMatteBorder(0, 3, 0, 0, colorForWorkspace(p.name())),
                            BorderFactory.createEmptyBorder(6, 9, 6, 8))
                    : BorderFactory.createEmptyBorder(6, 12, 6, 8));
            // Tarja lateral (acima) sozinha e sutil demais pra "achar" a
            // workspace ativa numa lista de ~15 conexoes so de bater o
            // olho (precisa reparar na borda fina de 3px). Um tingimento
            // MUITO leve (12%) da mesma cor de identidade no fundo inteiro
            // da linha da o mesmo sinal com muito mais area — so quando
            // nem selecionada nem em hover, que continuam com prioridade
            // visual maior (ver acima).
            if (active && !isSelected && !hovered) {
                setOpaque(true);
                setBackground(tint(list.getBackground(), colorForWorkspace(p.name()), 0.12f));
            }

            nameLabel.setForeground(isSelected ? list.getSelectionForeground() : GridTheme.HEADER_FOREGROUND);
            nameLabel.setFont(nameLabel.getFont().deriveFont(connected ? Font.BOLD : Font.PLAIN, 12f));
            nameLabel.setText(p.name());
            subLabel.setForeground(isSelected ? list.getSelectionForeground() : GridTheme.MUTED_TEXT);
            subLabel.setText(p.user() + "@" + p.host() + ":" + p.port());

            // "ATUAL" (selo, nao mais um sufixo de texto colado no nome) —
            // o UNICO sinal antes disto era a tarja lateral colorida; um
            // segundo sinal independente de cor continua valendo (pedido da
            // revisao: "identificar... sem depender apenas de cores"), so
            // que agora como selo visivel (mesma linguagem do NBadge usado
            // no resto do app) em vez de "  ·  atual" grudado no nome.
            trailing.removeAll();
            if (active) {
                trailing.add(new NBadge("ATUAL", NAccent.NEUTRAL));
            }
            trailing.add(menuIcon);

            setToolTipText(p.user() + "@" + p.host() + ":" + p.port() + "/" + p.schema());
            return this;
        }
    }
}
