package com.nureal.ide.ui;
import com.nureal.ide.compartilhado.designsystem.IconType;
import com.nureal.ide.compartilhado.designsystem.Icons;
import com.nureal.ide.compartilhado.designsystem.Buttons;
import com.nureal.ide.compartilhado.designsystem.Typography;
import com.nureal.ide.compartilhado.designsystem.GridTheme;
import com.nureal.ide.compartilhado.designsystem.Spacing;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;

import com.nureal.ide.compartilhado.designsystem.NAccent;
import com.nureal.ide.compartilhado.designsystem.NBadge;
import com.nureal.ide.compartilhado.designsystem.NTheme;

/**
 * Indicador "Conexao Ativa" — pilula arredondada mostrando SO o nome da
 * conexao ativa (status por cor do dot, ver {@link #render}) — pedido
 * explicito do usuario apos um bug relatado com captura de tela ("coloque
 * apenas o nome da conexao mesmo... aproveite ao maximo o espaco pra evitar
 * nomes cortados... sem interferir no botao de adicionar conexao, que ta
 * bugado ficando sobreposta"): host/porta/usuario/engine SAIRAM da pilula
 * (ficavam grandes demais pra coluna estreita da sidebar, forcando o layout
 * a quebrar) — quem quiser esses detalhes abre o dropdown "Conexoes salvas"
 * que a pilula ja abre ao clicar.
 * <p>
 * Ja foi um CARD vertical na sidebar (SPEC-0007 "Sidebar Workspace"), depois
 * um card mais rico com selos, depois uma barra esticada pela largura
 * inteira da janela, depois um pill centralizado numa linha propria por
 * cima de tudo, depois embutida dentro da barra de acoes do editor (ao lado
 * do "Salvar") — e agora de volta ao topo FIXO da coluna da sidebar (ver
 * {@code MainWindow#buildLeftSide}/{@code #buildConnectionBar}), acima da
 * busca unificada e das abas Objetos/SQL/Salvas/Historico. Decisao final do
 * usuario: "dois cabecalhos independentes" — esta pilula alinhada acima de
 * "Objetos" (coluna da sidebar) e "Executar" alinhado ao inicio das abas do
 * editor (coluna do editor), cada um o topo da PROPRIA coluna, sem precisar
 * sincronizar com a posicao do divisor arrastavel entre elas. Continua sem
 * inventar dado nenhum (ex.: uma referencia visual sugeriu em algum momento
 * um selo de charset, que o app nao consulta em lugar nenhum hoje — ficou de
 * fora, ver {@link #render}).
 */
final class ConnectionStatusCard extends JPanel {

	private static final long serialVersionUID = 1L;

	private enum State {
		DISCONNECTED, CONNECTING, CONNECTED
	}

	/** Uma conexao ativa (conectada agora), pronta pra exibir no dropdown de troca rapida — ver {@link #setActiveConnections}. */
	record ActiveConnection(String name, String label) {
	}

	private final JLabel dotIcon = new JLabel();
	private final JLabel engineIcon = new JLabel();
	private final JLabel nameLabel = new JLabel();
	/** Painel clicavel (icones + nome + seta) que abre "Conexoes salvas" — fundo destacado no hover, ver construtor. */
	private final JPanel clickArea;

	/**
	 * Botao "trocar conexao ativa" (icone ⇄ + texto) + selo com a contagem de
	 * conexoes conectadas AGORA (nao a lista completa de conexoes salvas) —
	 * pedido explicito do usuario: focar outra conexao ja conectada, SEM
	 * desconectar as demais. So aparece com 2+ conexoes conectadas
	 * simultaneamente (com 0 ou 1, nao ha nada pra trocar).
	 */
	private final JButton switchButton;
	private final JPanel switchRow = new JPanel(new FlowLayout(FlowLayout.LEFT, Spacing.XS, 0));
	/** "+ Nova conexao", sempre visivel — ver {@link #setOnNewConnection}. */
	private final JButton newConnectionButton;
	/** Seta "▾" no fim do {@link #clickArea} — so o icone, nao um botao clicavel separado (o painel inteiro ja e clicavel, ver {@link #openConnections}). */
	private final JLabel chevron = new JLabel();

	private State state = State.DISCONNECTED;
	private String lastName = "Sem conexao";

	private List<ActiveConnection> activeConnections = List.of();
	private String activeConnectionName;
	private Consumer<String> onSwitchRequested = name -> { };
	private Runnable onManageConnections = () -> { };
	private Runnable onNewConnection = () -> { };

	/**
	 * Fundo elevado ({@link NTheme#surfaceBackground()}) da pilula, recalculado
	 * em {@link #updateUI()} — mesma familia de cuidado ja documentada em
	 * {@link com.nureal.ide.compartilhado.designsystem.NCard} pra nao "queimar"
	 * a cor do tema em que o app foi ABERTO.
	 */
	private Color fill = NTheme.surfaceBackground();

	@Override
	public void updateUI() {
		super.updateUI();
		fill = NTheme.surfaceBackground();
	}

	@Override
	protected void paintComponent(Graphics g) {
		Graphics2D g2 = (Graphics2D) g.create();
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2.setColor(fill);
		g2.fillRoundRect(0, 0, getWidth(), getHeight(), NTheme.CARD_ARC, NTheme.CARD_ARC);
		g2.setColor(GridTheme.HEADER_BORDER);
		g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, NTheme.CARD_ARC, NTheme.CARD_ARC);
		g2.dispose();
		super.paintComponent(g);
	}

	ConnectionStatusCard() {
		super(new BorderLayout());
		setOpaque(false);
		setBorder(BorderFactory.createEmptyBorder(Spacing.XS, Spacing.SM, Spacing.XS, Spacing.SM));

		nameLabel.setFont(nameLabel.getFont().deriveFont(java.awt.Font.BOLD, 12f));

		JPanel iconsCol = new JPanel(new FlowLayout(FlowLayout.LEFT, Spacing.XS, 0));
		iconsCol.setOpaque(false);
		iconsCol.add(dotIcon);
		iconsCol.add(engineIcon);

		Buttons.bindThemedIcon(chevron, IconType.CHEVRON_RIGHT, 10, () -> GridTheme.MUTED_TEXT);

		// BorderLayout (nao mais FlowLayout): FlowLayout SEMPRE da a cada
		// filho a largura preferida INTEIRA, entao um nome de conexao longo
		// nunca encolhia — ou vazava por cima do botao "+ Nova conexao" (a
		// area clicavel toda simplesmente ficava mais larga que a coluna da
		// sidebar), ou empurrava a coluna inteira mais larga, desalinhando o
		// resto da janela (os dois bugs relatados pelo usuario com captura
		// de tela). Aqui SO {@link #nameLabel} fica no CENTER — a UNICA
		// regiao que o BorderLayout deixa encolher abaixo do preferido — e
		// o JLabel do Swing ja trunca com "..." sozinho quando isso acontece
		// (comportamento nativo de SwingUtilities#layoutCompoundLabel, sem
		// precisar de nenhum limite de caracteres calculado na mao). Os
		// icones (WEST) e a seta (EAST, dentro do proprio clickArea) ficam
		// com largura FIXA, sempre visiveis por inteiro — e o
		// {@link #switchRow} (fora do clickArea, EAST do painel inteiro)
		// nunca e espremido, entao o botao "+" nunca mais fica sobreposto.
		clickArea = new JPanel(new BorderLayout(Spacing.SM, 0));
		clickArea.setOpaque(false);
		clickArea.setBorder(BorderFactory.createEmptyBorder(2, Spacing.SM, 2, Spacing.SM));
		clickArea.add(iconsCol, BorderLayout.WEST);
		clickArea.add(nameLabel, BorderLayout.CENTER);
		clickArea.add(chevron, BorderLayout.EAST);

		switchButton = new JButton("Trocar");
		Buttons.bindThemedIcon(switchButton, IconType.SWAP, 13, () -> GridTheme.MUTED_TEXT);
		switchButton.setIconTextGap(4);
		Buttons.styleSecondary(switchButton);
		switchButton.setToolTipText("Trocar conexao ativa");
		switchButton.addActionListener(e -> showSwitchMenu());

		// SO icone (nao mais "+ Nova conexao" com texto): pedido explicito do
		// usuario apos o bug acima ("reduzido a um icone de adicionar e
		// sempre visivel") — um botao so-de-icone tem largura fixa pequena,
		// entao mesmo textos de nome/host mais longos no futuro nao tem
		// como "engolir" o espaco dele de novo.
		newConnectionButton = Buttons.iconButton(IconType.NEW, 15, () -> GridTheme.BRAND_GREEN);
		newConnectionButton.setToolTipText("Nova conexao");
		newConnectionButton.addActionListener(e -> onNewConnection.run());

		switchRow.setOpaque(false);
		// Populado por #updateSwitchRow (chamado no fim do construtor), nao
		// aqui — evita adicionar switchButton/newConnectionButton so pra
		// serem removidos/reordenados na primeira chamada.

		// clickArea no CENTER (nao mais WEST): CENTER e a UNICA regiao que o
		// BorderLayout deixa encolher — e o encolhimento cascateia pro
		// nameLabel LA DENTRO (que tambem esta no CENTER do clickArea, ver
		// acima). switchRow continua EAST, largura fixa, protegida.
		add(clickArea, BorderLayout.CENTER);
		add(switchRow, BorderLayout.EAST);

		// O clickArea INTEIRO (icones + textos + seta) abre "Conexoes
		// salvas" — mesmo cuidado ja aplicado quando isto era um card na
		// sidebar: o listener vai em CADA componente visivel dele (nao so
		// no painel), porque eventos de mouse em Swing nao "borbulham"
		// sozinhos do filho pro pai.
		MouseAdapter openConnections = new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				onManageConnections.run();
			}
		};
		for (Component c : new Component[] { clickArea, iconsCol, dotIcon, engineIcon, nameLabel, chevron }) {
			c.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			c.addMouseListener(openConnections);
			if (c instanceof JLabel jl) {
				jl.setToolTipText("Conexoes salvas");
			}
		}
		// Fundo destacado (GridTheme.HOVER_BACKGROUND) so no clickArea
		// (nao na barra inteira) enquanto o mouse esta em cima — mesmo
		// pedido explicito de quando isto era um card ("faltou uma sombra
		// de selecao"). getMousePosition() (nao e.getOppositeComponent()):
		// retorna nao-nulo quando o mouse esta sobre um FILHO do clickArea
		// tambem, entao o mouseExited (disparado ao mover pra cima de um
		// filho, que "rouba" o evento) so desliga o hover quando o mouse de
		// fato SAIU da area inteira — sem isto, o fundo "piscaria" toda vez
		// que o mouse cruzasse a borda de um rotulo interno.
		clickArea.setOpaque(false);
		clickArea.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseEntered(MouseEvent e) {
				clickArea.setOpaque(true);
				clickArea.setBackground(GridTheme.HOVER_BACKGROUND);
				clickArea.repaint();
			}

			@Override
			public void mouseExited(MouseEvent e) {
				if (clickArea.getMousePosition() == null) {
					clickArea.setOpaque(false);
					clickArea.repaint();
				}
			}
		});

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

	/** Chamado quando o usuario clica no {@link #clickArea} (icones/nome/seta) — abre "Conexoes salvas". */
	void setOnManageConnections(Runnable callback) {
		this.onManageConnections = (callback != null) ? callback : () -> { };
	}

	/** Chamado quando o usuario clica em "+ Nova conexao". */
	void setOnNewConnection(Runnable callback) {
		this.onNewConnection = (callback != null) ? callback : () -> { };
	}

	/** Componente contra o qual o dropdown "Conexoes salvas" deve se ancorar (posicao/largura) — {@code this} mesmo, ja que a pilula E este painel. */
	JComponent popupAnchor() {
		return this;
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
		// removeAll (nao so remove(switchButton)): sem isto, um selo
		// (NBadge) novo se somava a cada chamada em vez de substituir o
		// anterior — a MESMA contagem de conexoes conectadas acumulava
		// varios selos identicos se #updateSwitchRow rodasse de novo com o
		// numero ja em 2+ (ex.: troca de tema com 2+ conexoes conectadas).
		switchRow.removeAll();
		switchButton.setVisible(activeConnections.size() > 1);
		if (activeConnections.size() > 1) {
			switchRow.add(switchButton);
			switchRow.add(new NBadge(String.valueOf(activeConnections.size()), NAccent.NEUTRAL));
		}
		switchRow.add(newConnectionButton);
		switchRow.revalidate();
		switchRow.repaint();
	}

	void showDisconnected() {
		state = State.DISCONNECTED;
		lastName = "Sem conexao";
		render();
	}

	void showConnecting(String name) {
		state = State.CONNECTING;
		lastName = name;
		render();
	}

	/** So o NOME da conexao (host/porta/usuario/engine ficaram de fora da pilula — ver javadoc de {@link #render}). */
	void showConnected(String name) {
		state = State.CONNECTED;
		lastName = name;
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
		// Typography.primary reaplicado AQUI (nao mais so uma vez no
		// construtor): nameLabel ficava com a cor do tema em que a barra
		// foi CONSTRUIDA (sempre o escuro, ver App#main) gravada pra sempre
		// — mesma familia de bug ja corrigida varias vezes nesta base.
		// render() roda a cada troca de estado E a partir de
		// refreshTheme() (chamado por MainWindow#toggleTheme), entao
		// reaplicar aqui cobre os dois casos de uma vez.
		Typography.primary(nameLabel);
		Color color = switch (state) {
		case DISCONNECTED -> GridTheme.COLOR_LOGIC_FALSE;
		case CONNECTING -> GridTheme.HEADER_HIGHLIGHT_BORDER;
		case CONNECTED -> GridTheme.COLOR_LOGIC_TRUE;
		};
		dotIcon.setIcon(Icons.get(IconType.STATUS_DOT, 8, color));
		Buttons.bindThemedIcon(engineIcon, IconType.DATABASE, 14, () -> GridTheme.MUTED_TEXT);

		// SO o nome da conexao (nao mais usuario/host/engine juntos) —
		// pedido explicito do usuario: "coloque apenas o nome da conexao
		// mesmo". nameLabel esta no CENTER de um BorderLayout (ver
		// construtor) — o proprio Swing trunca com "..." sozinho quando o
		// espaco disponivel e menor que o texto (SwingUtilities#
		// layoutCompoundLabel), aproveitando o MAXIMO de espaco disponivel
		// antes de abreviar, em vez de um limite fixo de caracteres. O
		// nome COMPLETO (nunca truncado) fica no tooltip, pra quando
		// precisar conferir sem abrir o dropdown.
		String name = switch (state) {
		case DISCONNECTED -> "Nenhuma conexao";
		case CONNECTING, CONNECTED -> lastName;
		};
		nameLabel.setText(name);
		nameLabel.setToolTipText((state == State.CONNECTED || state == State.CONNECTING) ? lastName
				: "Conexoes salvas");
	}
}
