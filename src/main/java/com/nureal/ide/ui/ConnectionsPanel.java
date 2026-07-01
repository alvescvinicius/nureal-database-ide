package com.nureal.ide.ui;

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
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Painel lateral com as conexoes salvas, no estilo de cartoes.
 * Duplo-clique (ou "Conectar" no menu de contexto) conecta. "Nova" cadastra.
 */
public class ConnectionsPanel extends JPanel {

    private static final long serialVersionUID = 1L;
	private final ConnectionStore store;
    private final Consumer<ConnectionProfile> connectAction;
    private final DefaultListModel<ConnectionProfile> model = new DefaultListModel<>();
    private final JList<ConnectionProfile> list = new JList<>(model);
    private final Set<String> connectedNames = new HashSet<>();
    private String connectingName;

    public ConnectionsPanel(ConnectionStore store, Consumer<ConnectionProfile> connectAction) {
        super(new BorderLayout(0, 8));
        this.store = store;
        this.connectAction = connectAction;
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
        novo.setIcon(Icons.get(IconType.NEW, 15, new Color(0x334155)));
        novo.addActionListener(k -> onNew());

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.add(title, BorderLayout.WEST);
        header.add(novo, BorderLayout.EAST);
        return header;
    }

    private JComponent buildList() {
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setFixedCellHeight(54);
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
        JPopupMenu menu = new JPopupMenu();
        JMenuItem connect = new JMenuItem("Conectar");
        connect.addActionListener(k -> connectSelected());
        JMenuItem edit = new JMenuItem("Editar...");
        edit.setIcon(Icons.get(IconType.EDIT, 15, new Color(0x334155)));
        edit.addActionListener(k -> onEdit());
        JMenuItem delete = new JMenuItem("Excluir");
        delete.setIcon(Icons.get(IconType.DELETE, 15, new Color(0x334155)));
        delete.addActionListener(k -> onDelete());
        menu.add(connect);
        menu.addSeparator();
        menu.add(edit);
        menu.add(delete);
        menu.show(list, e.getX(), e.getY());
    }

    /** Recarrega a lista a partir do arquivo. */
    public void reload() {
        model.clear();
        try {
            List<ConnectionProfile> saved = store.load();
            for (ConnectionProfile p : saved) {
                model.addElement(p);
            }
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this,
                    "Nao foi possivel ler as conexoes:\n" + e.getMessage(),
                    "Conexoes", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void persist() {
        try {
            store.save(java.util.Collections.list(model.elements()));
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this,
                    "Nao foi possivel salvar as conexoes:\n" + e.getMessage(),
                    "Conexoes", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void onNew() {
        ConnectionProfile created = ConnectionEditDialog.show(this, null);
        if (created != null) {
            model.addElement(created);
            persist();
            list.setSelectedValue(created, true);
        }
    }

    private void onEdit() {
        int idx = list.getSelectedIndex();
        if (idx < 0) {
            return;
        }
        ConnectionProfile edited = ConnectionEditDialog.show(this, model.get(idx));
        if (edited != null) {
            model.set(idx, edited);
            persist();
        }
    }

    private void onDelete() {
        int idx = list.getSelectedIndex();
        if (idx < 0) {
            return;
        }
        ConnectionProfile p = model.get(idx);
        int ok = JOptionPane.showConfirmDialog(this,
                "Excluir a conexao \"" + p.name() + "\"?",
                "Excluir conexao", JOptionPane.YES_NO_OPTION);
        if (ok == JOptionPane.YES_OPTION) {
            model.remove(idx);
            persist();
        }
    }

    private void connectSelected() {
        ConnectionProfile p = list.getSelectedValue();
        if (p != null) {
            connectAction.accept(p);
        }
    }

    /** Ajusta a altura de cada cartao da lista (usado pelo zoom/modo compacto). */
    public void setRowHeight(int height) {
        list.setFixedCellHeight(height);
        list.revalidate();
        list.repaint();
    }

    /** Define o conjunto de conexoes atualmente conectadas (bolinha verde). */
    public void setConnectedNames(Set<String> names) {
        connectedNames.clear();
        if (names != null) {
            connectedNames.addAll(names);
        }
        connectingName = null;
        list.repaint();
    }

    /** Marca qual conexao esta conectando (bolinha ambar). */
    public void setConnecting(ConnectionProfile profile) {
        this.connectingName = (profile == null) ? null : profile.name();
        list.repaint();
    }

    /** Pequeno circulo de status (verde = conectado, cinza = desconectado). */
    private static Icon statusDot(Color color) {
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

    /** Renderiza cada conexao como um cartao: status + nome em destaque + destino. */
    private final class ConnectionRenderer extends javax.swing.DefaultListCellRenderer {
        private static final long serialVersionUID = 1L;

		@Override
        public Component getListCellRendererComponent(
                JList<?> list, Object value, int index,
                boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            setHorizontalAlignment(SwingConstants.LEFT);
            setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));
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
                String sub = p.user() + "@" + p.host() + ":" + p.port() + "/" + p.schema();
                String subColor = isSelected ? "#E5F5EC" : "#6B7280";
                String family = getFont().getFamily();
                setText("<html><div style='font-family:" + family + ";line-height:1.5'><b>"
                        + escape(p.name())
                        + "</b><br><span style='color:" + subColor + ";font-size:10px'>"
                        + escape(sub) + "</span></div></html>");
            }
            return this;
        }

        private static String escape(String s) {
            return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        }
    }
}
