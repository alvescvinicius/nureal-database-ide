package com.nureal.ide.ui;

import java.awt.Color;

import javax.swing.JLabel;

import com.nureal.ide.ui.components.NAccent;
import com.nureal.ide.ui.components.NCard;

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

	private final JLabel nameLabel = new JLabel();
	private final JLabel hostLabel = new JLabel();
	private final JLabel engineLabel = new JLabel();
	private final JLabel statusLabel = new JLabel();

	private State state = State.DISCONNECTED;
	private String lastName = "Sem conexao";
	private String lastHost = " ";
	private String lastEngine = " ";

	ConnectionStatusCard() {
		super(NAccent.NEUTRAL, null);
		Typography.primary(nameLabel);
		Typography.tertiary(hostLabel);
		Typography.tertiary(engineLabel);
		statusLabel.setFont(statusLabel.getFont().deriveFont(java.awt.Font.BOLD, 11f));

		addContent(nameLabel);
		addContent(hostLabel);
		addContent(engineLabel);
		addContent(statusLabel);

		render();
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
