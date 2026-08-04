package com.nureal.ide.ui;
import com.nureal.ide.compartilhado.designsystem.IconType;
import com.nureal.ide.compartilhado.designsystem.Icons;
import com.nureal.ide.compartilhado.designsystem.Buttons;
import com.nureal.ide.compartilhado.designsystem.Typography;
import com.nureal.ide.compartilhado.designsystem.GridTheme;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.FlowLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
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
	/**
	 * Seta de "gerenciar conexoes" (estilo dropdown, mesmo icone ja usado em
	 * {@code MainWindow#addRunFormatExplainButtons} pro menu de opcoes de
	 * formatacao) — SEMPRE visivel, ao contrario de {@link #switchButton}
	 * (que so aparece com 2+ conexoes conectadas). Abre a lista completa de
	 * conexoes salvas (ver {@link #setOnManageConnections}), que deixou de
	 * ficar sempre visivel na sidebar (pedido explicito do protótipo:
	 * "card reduzido para apenas Ambiente/Schema/Banco").
	 */
	private final JButton manageButton;
	private final JPanel switchRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));

	private State state = State.DISCONNECTED;
	private String lastName = "Sem conexao";
	private String lastHost = " ";
	private String lastEngine = " ";

	private List<ActiveConnection> activeConnections = List.of();
	private String activeConnectionName;
	private Consumer<String> onSwitchRequested = name -> { };
	private Runnable onManageConnections = () -> { };
	/** {@code true} enquanto o mouse esta em cima do card (ou de qualquer filho dele) — ver {@link #fillColor()}. */
	private boolean hovering;

	ConnectionStatusCard() {
		super(NAccent.NEUTRAL, null);
		statusLabel.setFont(statusLabel.getFont().deriveFont(java.awt.Font.BOLD, 11f));

		switchButton = Buttons.iconButton(IconType.SWAP, 13, () -> GridTheme.MUTED_TEXT);
		switchButton.setToolTipText("Trocar conexao ativa");
		switchButton.addActionListener(e -> showSwitchMenu());

		manageButton = new JButton(new com.formdev.flatlaf.icons.FlatMenuArrowIcon());
		manageButton.setToolTipText("Conexoes salvas");
		manageButton.addActionListener(e -> onManageConnections.run());
		Buttons.styleIconButton(manageButton);

		switchRow.setOpaque(false);
		switchRow.add(manageButton);

		JPanel nameRow = new JPanel(new BorderLayout());
		nameRow.setOpaque(false);
		nameRow.add(nameLabel, BorderLayout.WEST);
		nameRow.add(switchRow, BorderLayout.EAST);
		// O CARD INTEIRO (nao so a linha do nome) abre "Conexoes salvas" —
		// pedido explicito do usuario apos achar a setinha pouco funcional/
		// dificil de acertar, e depois relatar que nem a linha do nome
		// reagia ao passar o mouse (provavelmente porque so tentou em cima
		// de host/engine/status, que nao faziam parte do alvo). Cobrir TODO
		// o card, nao so um pedaco especifico, elimina essa ambiguidade — o
		// unico ponto que NAO deve disparar isto e switchRow (setinha +
		// botao de trocar), que continua capturando o PROPRIO clique
		// primeiro (Swing entrega ao filho mais especifico sob o cursor,
		// nunca aos dois ao mesmo tempo). O listener vai em CADA componente
		// visivel do card (nao so no painel externo): eventos de mouse em
		// Swing nao "borbulham" sozinhos do filho pro pai (diferente do
		// DOM) — um clique/hover em cima do TEXTO de qualquer rotulo vai
		// direto pro rotulo, nunca alcançaria um listener so no card.
		MouseAdapter openConnections = new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				onManageConnections.run();
			}
		};
		for (java.awt.Component c : new java.awt.Component[] {
				this, nameRow, nameLabel, hostLabel, engineLabel, statusLabel }) {
			c.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			c.addMouseListener(openConnections);
			if (c instanceof JLabel jl) {
				jl.setToolTipText("Conexoes salvas");
			}
		}
		// Fundo destacado (GridTheme.HOVER_BACKGROUND) enquanto o mouse esta
		// em cima do card — pedido explicito do usuario: so o cursor virar
		// maozinha nao deixava claro que tinha uma acao ali ("faltou uma
		// sombra de selecao"). SO no CARD (nao em cada filho tambem, como o
		// listener de clique acima): getMousePosition() de um Container ja
		// retorna nao-nulo quando o mouse esta sobre um FILHO dele (ver
		// javadoc de Component#getMousePosition), entao o mouseExited do
		// card (disparado ao mover pra cima de um filho, que "rouba" o
		// evento) so desliga o hover quando o mouse de fato SAIU do card
		// inteiro — sem isto, o fundo "piscaria" toda vez que o mouse
		// cruzasse a borda de um rotulo interno.
		addMouseListener(new MouseAdapter() {
			@Override
			public void mouseEntered(MouseEvent e) {
				hovering = true;
				repaint();
			}

			@Override
			public void mouseExited(MouseEvent e) {
				if (getMousePosition() == null) {
					hovering = false;
					repaint();
				}
			}
		});

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

	/** Chamado quando o usuario clica na seta de "Conexoes salvas" (ver {@link #manageButton}). */
	void setOnManageConnections(Runnable callback) {
		this.onManageConnections = (callback != null) ? callback : () -> { };
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
		switchRow.add(manageButton);
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
		// Typography.primary/tertiary reaplicados AQUI (nao mais so uma vez
		// no construtor): nameLabel/hostLabel/engineLabel ficavam com a cor
		// do tema em que o card foi CONSTRUIDO (sempre o escuro, ver
		// App#main) gravada pra sempre — so o statusLabel (linha abaixo,
		// "Conectado"/"Desconectado") ja lia a cor certa a cada render()
		// porque a sua vinha de uma variavel LOCAL (color, calculada aqui
		// mesmo), nao de uma chamada unica no construtor. render() roda a
		// cada troca de estado E a partir de refreshTheme() (chamado por
		// MainWindow#toggleTheme), entao reaplicar aqui cobre os dois casos
		// de uma vez — bug relatado pelo usuario ("continuo nao enxergando
		// nada aqui" no tema claro, com captura mostrando nome/host/engine
		// quase invisiveis contra o fundo claro do card).
		Typography.primary(nameLabel);
		Typography.tertiary(hostLabel);
		Typography.tertiary(engineLabel);
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

	/** Fundo do card: destacado (hover) enquanto o mouse esta em cima — ver {@link #hovering}. */
	@Override
	protected Color fillColor() {
		return hovering ? GridTheme.HOVER_BACKGROUND : super.fillColor();
	}
}
