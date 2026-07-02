package com.nureal.ide.ui;

import com.formdev.flatlaf.FlatClientProperties;
import com.nureal.ide.core.connection.ConnectionProfile;
import com.nureal.ide.core.connection.ConnectionStore;

import javax.swing.BorderFactory;
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
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.Graphics2D;
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
     * sem a segunda linha "usuario@host:porta/schema". Usada por
     * {@code MainWindow#refreshDynamicSizing}/{@code #buildLeftSide} como base
     * do zoom, em vez de um numero magico repetido em dois lugares.
     */
    static final int DEFAULT_ROW_HEIGHT = 34;

	private final ConnectionStore store;
    private final Consumer<ConnectionProfile> connectAction;
    private final Consumer<ConnectionProfile> disconnectAction;
    private final DefaultListModel<ConnectionProfile> model = new DefaultListModel<>();
    private final JList<ConnectionProfile> list = new JList<>(model);
    private final JTextField search = new JTextField();
    private final Set<String> connectedNames = new HashSet<>();
    private List<ConnectionProfile> all = new ArrayList<>();
    private String connectingName;

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

    private JComponent buildHeader() {
        JLabel title = new JLabel("CONEXOES");
        title.putClientProperty("FlatLaf.styleClass", "small");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 11f));
        title.setForeground(new java.awt.Color(0x6B7280));

        JButton novo = new JButton("Nova");
        novo.setIcon(Icons.get(IconType.NEW, 13, new Color(0x334155)));
        novo.setToolTipText("Nova conexao");
        novo.addActionListener(e -> onNew());
        novo.setIconTextGap(6);
        novo.setMargin(new Insets(4, 10, 4, 10));
        novo.setFont(novo.getFont().deriveFont(12f));
        // Botao secundario com contorno (nao preenchido) — mesma linguagem de
        // arco discreto usada na barra de ferramentas do editor (ver
        // MainWindow#buildToolbar), so que aqui em versao "outline", ja que
        // esta ao lado do titulo da secao, nao de uma acao primaria.
        novo.putClientProperty("JButton.buttonType", "roundRect");
        novo.putClientProperty(FlatClientProperties.STYLE, "arc: 8; borderWidth: 1");

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
        list.setCellRenderer(new ConnectionRenderer());
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
        return sp;
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
        JMenuItem connect = new JMenuItem("Conectar");
        connect.setEnabled(!connected);
        connect.addActionListener(a -> connectSelected());
        JMenuItem disconnect = new JMenuItem("Desconectar");
        disconnect.setEnabled(connected);
        disconnect.addActionListener(a -> disconnectSelected());
        JMenuItem edit = new JMenuItem("Editar...");
        edit.setIcon(Icons.get(IconType.EDIT, 15, new Color(0x334155)));
        edit.addActionListener(a -> onEdit());
        JMenuItem delete = new JMenuItem("Excluir");
        delete.setIcon(Icons.get(IconType.DELETE, 15, new Color(0x334155)));
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
            if (value instanceof ConnectionProfile p) {
                Color dotColor;
                if (connectedNames.contains(p.name())) {
                    dotColor = new Color(0x059669);
                } else if (p.name().equals(connectingName)) {
                    dotColor = new Color(0xF59E0B);
                } else {
                    dotColor = new Color(0xC4C9D1);
                }
                setIcon(statusDot(dotColor));
                setText(p.name());
                setFont(getFont().deriveFont(connectedNames.contains(p.name()) ? Font.BOLD : Font.PLAIN));
                setToolTipText(p.user() + "@" + p.host() + ":" + p.port() + "/" + p.schema());
            }
            return this;
        }
    }
}
