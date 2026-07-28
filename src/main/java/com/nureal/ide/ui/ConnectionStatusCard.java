package com.nureal.ide.ui;
import com.nureal.ide.compartilhado.designsystem.IconType;
import com.nureal.ide.compartilhado.designsystem.Icons;
import com.nureal.ide.compartilhado.designsystem.Buttons;
import com.nureal.ide.compartilhado.designsystem.Typography;
import com.nureal.ide.compartilhado.designsystem.GridTheme;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.util.List;
import java.util.function.Consumer;

import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;

import com.nureal.ide.compartilhado.designsystem.NAccent;
import com.nureal.ide.compartilhado.designsystem.NBadge;
import com.nureal.ide.compartilhado.designsystem.NCard;

/**
 * Card "Conexao Ativa" da sidebar (SPEC-0007 "Sidebar Workspace"): unico
 * lugar do app mostrando nome/host/engine/status da conexao ativa — sempre
 * visivel, independente de qual secao do rail esta selecionada (nunca
 * desaparece ao trocar de Conexoes/Objetos/Consultas/Historico).
 * <p>
 * Substitui o antigo par {@code connStatusLabel}/{@code connProgress} do
 * rodape (removido, ver {@code MainWindow#buildFooter}): a MESMA informacao
 * que antes ficava numa barra fixa no fundo da janela ("Conectado: x"),
 * proibida pela spec ("nunca mostrar Conectado na barra inferior").
 */
final class ConnectionStatusCard extends NCard {

	private static final long serialVersionUID = 1L;

	private enum State {
		DISCONNECTED, CONNECTING, CONNECTED
	}

	/** Uma conexao ativa (conectada agora), pronta pra exibir no dropdown de troca rapida — ver {@link #setActiveConnections}. */
	record ActiveConnection(String name, String label) {
	}

	private final JLabel nameLabel = new JLabel();
	private final JLabel hostLabel = new JLabel();
	private final JLabel engineLabel = new JLabel();
	private final JLabel statusLabel = new JLabel();

	/**
	 * Botao "trocar conexao ativa" (icone ⇄) + selo com a contagem de
	 * conexoes conectadas AGORA (nao a lista completa de conexoes salvas) —
	 * pedido explicito do usuario: focar outra conexao ja conectada, SEM
	 * desconectar as demais. So aparece com 2+ conexoes conectadas
	 * simultaneamente (com 0 ou 1, nao ha nada pra trocar).
	 */
	private final JButton switchButton;
	private final JPanel switchRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));

	private State state = State.DISCONNECTED;
	private String lastName = "Sem conexao";
	private String lastHost = " ";
	private String lastEngine = " ";

	private List<ActiveConnection> activeConnections = List.of();
	private String activeConnectionName;
	private Consumer<String> onSwitchRequested = name -> { };

	ConnectionStatusCard() {
		super(NAccent.NEUTRAL, null);
		Typography.primary(nameLabel);
		Typography.tertiary(hostLabel);
		Typography.tertiary(engineLabel);
		statusLabel.setFont(statusLabel.getFont().deriveFont(java.awt.Font.BOLD, 11f));

		switchButton = Buttons.iconButton(IconType.SWAP, 13, () -> GridTheme.MUTED_TEXT);
		switchButton.setToolTipText("Trocar conexao ativa");
		switchButton.addActionListener(e -> showSwitchMenu());
		switchRow.setOpaque(false);

		JPanel nameRow = new JPanel(new BorderLayout());
		nameRow.setOpaque(false);
		nameRow.add(nameLabel, BorderLayout.WEST);
		nameRow.add(switchRow, BorderLayout.EAST);

		addContent(nameRow);
		addContent(hostLabel);
		addContent(engineLabel);
		addContent(statusLabel);

		updateSwitchRow();
		render();
	}

	/**
	 * Conexoes conectadas AGORA (chamado sempre que alguma conecta/desconecta
	 * — ver {@code MainWindow#refreshConnectionIndicators}) e qual delas esta
	 * em foco. {@code connections} ja vem com o rotulo pronto (nome/host) —
	 * este card nunca le {@code ConnectionProfile}/{@code ConnectionManager}
	 * diretamente, so exibe o que {@code MainWindow} ja resolveu.
	 */
	void setActiveConnections(List<ActiveConnection> connections, String activeName) {
		this.activeConnections = connections;
		this.activeConnectionName = activeName;
		updateSwitchRow();
	}

	/** Chamado quando o usuario escolhe uma conexao diferente no dropdown — recebe o NOME da conexao escolhida. */
	void setOnSwitchRequested(Consumer<String> callback) {
		this.onSwitchRequested = (callback != null) ? callback : name -> { };
	}

	/**
	 * Marcador da conexao ATIVA via {@link JCheckBoxMenuItem} nativo (FlatLaf),
	 * nao mais um caractere Unicode "✓ " colado no texto — mesmo motivo ja
	 * documentado em {@code MainWindow#presetItem}: o caractere nao existe em
	 * algumas fontes e aparecia como um quadrado vazio ("tofu") no lugar do
	 * check, relatado pelo usuario em varios menus (este, Zoom e Espacamento
	 * de linhas — ver os mesmos ajustes em {@code MainWindow#buildLayoutMenu}).
	 */
	private void showSwitchMenu() {
		JPopupMenu menu = new JPopupMenu();
		for (ActiveConnection c : activeConnections) {
			boolean isActive = c.name().equals(activeConnectionName);
			JCheckBoxMenuItem item = new JCheckBoxMenuItem(c.label(), isActive);
			if (isActive) {
				item.setEnabled(false);
			} else {
				item.addActionListener(e -> onSwitchRequested.accept(c.name()));
			}
			menu.add(item);
		}
		menu.show(switchButton, 0, switchButton.getHeight());
	}

	/**
	 * Reconstroi o botao+selo: o selo ({@link NBadge}) fixa a cor de fundo/
	 * texto na CONSTRUCAO (sem setter proprio pra atualizar depois), entao
	 * precisa ser recriado aqui (nao so ter o texto trocado) sempre que a
	 * contagem OU o tema mudar — mesma familia de cuidado do resto desta
	 * classe (ver {@link #refreshTheme()}).
	 */
	private void updateSwitchRow() {
		switchRow.removeAll();
		switchRow.add(switchButton);
		switchButton.setVisible(activeConnections.size() > 1);
		if (activeConnections.size() > 1) {
			switchRow.add(new NBadge(String.valueOf(activeConnections.size()), NAccent.NEUTRAL));
		}
		switchRow.revalidate();
		switchRow.repaint();
	}

	void showDisconnected() {
		state = State.DISCONNECTED;
		lastName = "Sem conexao";
		lastHost = " ";
		lastEngine = " ";
		render();
	}

	void showConnecting(String name) {
		state = State.CONNECTING;
		lastName = name;
		lastHost = " ";
		lastEngine = " ";
		render();
	}

	void showConnected(String name, String host, String engine) {
		state = State.CONNECTED;
		lastName = name;
		lastHost = (host != null) ? host : " ";
		lastEngine = (engine != null) ? engine : " ";
		render();
	}

	/**
	 * Reaplica as cores derivadas do tema (dot + status): {@link GridTheme}
	 * so tem os valores NOVOS depois que {@code MainWindow#toggleTheme}
	 * troca a paleta — mesmo padrao ja usado por
	 * {@code SqlEditorPane#refreshTheme}/{@code ChatWindow#refreshTheme}.
	 */
	void refreshTheme() {
		render();
		updateSwitchRow();
	}

	private void render() {
		Color color = switch (state) {
		case DISCONNECTED -> GridTheme.COLOR_LOGIC_FALSE;
		case CONNECTING -> GridTheme.HEADER_HIGHLIGHT_BORDER;
		case CONNECTED -> GridTheme.COLOR_LOGIC_TRUE;
		};
		String statusText = switch (state) {
		case DISCONNECTED -> "Desconectado";
		case CONNECTING -> "Conectando...";
		case CONNECTED -> "Conectado";
		};
		nameLabel.setIcon(Icons.get(IconType.STATUS_DOT, 8, color));
		nameLabel.setText(lastName);
		hostLabel.setText(lastHost);
		engineLabel.setText(lastEngine);
		statusLabel.setForeground(color);
		statusLabel.setText(statusText);
	}
}
