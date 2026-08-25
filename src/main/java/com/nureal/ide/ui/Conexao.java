package com.nureal.ide.ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.nureal.ide.modulos.conexoes.dominio.contratos.ConexaoAtivaPort;
import com.nureal.ide.modulos.conexoes.dominio.entidades.ConnectionProfile;
import com.nureal.ide.modulos.metadados.dominio.entidades.SchemaInfo;
import com.nureal.ide.modulos.historico.infraestrutura.SessionStore;

import java.awt.Component;
import javax.swing.JComponent;
import javax.swing.JTabbedPane;

/**
 * Conexao (workspace) de uma conexao: sua sessao JDBC, esquema e abas de SQL
 * proprias.
 */
final class Conexao {
	final String name; // nome da conexao (ou SCRATCH)
	final ConnectionProfile profile; // null para a conexao sem conexao
	final ConexaoAtivaPort mgr; // gerenciador JDBC proprio
	SchemaInfo schema; // esquema carregado (ou null)
	List<String> schemaList; // lista de esquemas (schema em branco)
	/**
	 * TODOS os esquemas ja carregados nesta conexao durante a sessao (nao so
	 * o {@link #schema} "corrente") — pedido explicito do usuario: rodar uma
	 * consulta cruzando schemas (ex.: {@code SELECT * FROM db1.t1 JOIN
	 * db2.t2}) nao deveria exigir "escolher um esquema" primeiro, e o
	 * autocomplete deveria sugerir tabelas de QUALQUER esquema ja visitado
	 * nesta conexao, nao so do que esta selecionado no momento. Acumulado
	 * (nunca substituido) por {@link #rememberSchema} sempre que um esquema e
	 * carregado (abrir na arvore, trocar de esquema, ou a aba pedir um
	 * diferente do corrente ao executar) — ver {@code MainWindow#openSchema}/
	 * {@code #switchCatalogThenRun}/{@code #activateWorkspace}. Nunca
	 * misturado entre CONEXOES diferentes: cada {@code Conexao} tem o seu.
	 */
	final Map<String, SchemaInfo> loadedSchemas = new LinkedHashMap<>();
	List<SessionStore.Tab> tabs = new ArrayList<>();
	int selectedTab = 0;
	/**
	 * Ultimos resultados de cada aba de SQL desta conexao, indexados
	 * pelo ID ESTAVEL da aba ({@code SessionStore.Tab#id} / {@code
	 * SqlEditorPane#tabId()}) — nao pela instancia de SqlEditorPane nem
	 * pela POSICAO: ao trocar de conexao e voltar, {@code
	 * rebuildEditorTabs} cria instancias NOVAS de SqlEditorPane a partir
	 * do texto salvo em {@code tabs}, entao a instancia antiga (chave
	 * usada em {@code MainWindow#resultsByTab} enquanto esta conexao
	 * estava ativa) nao serve mais de chave; e a POSICAO tambem nao e
	 * confiavel, pois fechar/reabrir abas muda a posicao sem mudar a
	 * identidade da aba. O id, por ser gerado uma unica vez quando a aba
	 * e criada e persistido com ela, e a unica chave estavel.
	 * Preenchido em {@code saveActiveTabs} (ao SAIR desta conexao) e
	 * consumido em {@code rebuildEditorTabs} (ao VOLTAR pra ela).
	 */
	Map<String, List<QueryResult>> tabResults = new HashMap<>();

	/**
	 * Abas de terminal SQL VIVAS desta conexao (nao a representacao salva
	 * {@link #tabs}, mas o {@code JTabbedPane} de verdade com as instancias
	 * de {@code SqlEditorPane} ja abertas) — construido uma UNICA vez, na
	 * primeira ativacao desta conexao (ver {@code MainWindow#activateWorkspace}),
	 * e nunca mais destruido enquanto a conexao continuar aberta: e o que
	 * permite trocar de aba de conexao e voltar sem perder o estado dos
	 * terminais (texto digitado, resultados, conexao JDBC dedicada de cada
	 * um) — pedido explicito do usuario ("abas de conexao... dentro dessa
	 * aba de conexao posso ter varias abas de terminais"). {@code null}
	 * ate a primeira ativacao.
	 */
	JTabbedPane ownEditorTabs;
	/** A aba "+" (nova query) desta conexao — ver {@link #ownEditorTabs}. */
	Component ownPlusTab;
	/** Painel de nivel superior desta conexao dentro da tira de abas de conexao (ver {@code MainWindow#connectionTabs}). */
	JComponent ownPanel;

	/**
	 * Instante (epoch millis) da ultima execucao de verdade nesta conexao —
	 * usado so pelo keep-alive (ver {@code MainWindow#pingKeepAlive}) pra
	 * decidir se a conexao esta ociosa ha tempo suficiente pra merecer um
	 * ping. Comeca no momento da criacao (equivalente a "acabou de conectar,
	 * ainda nao esta ociosa").
	 */
	private long lastActivityMillis = System.currentTimeMillis();

	Conexao(String name, ConnectionProfile profile, ConexaoAtivaPort mgr) {
		this.name = name;
		this.profile = profile;
		this.mgr = mgr;
	}

	String name() {
		return name;
	}

	ConnectionProfile profile() {
		return profile;
	}

	ConexaoAtivaPort mgr() {
		return mgr;
	}

	SchemaInfo schema() {
		return schema;
	}

	/** Define o esquema CORRENTE e acumula em {@link #loadedSchemas} (nunca esquece um esquema ja visitado). */
	void setSchema(SchemaInfo schema) {
		this.schema = schema;
		if (schema != null) {
			loadedSchemas.put(schema.name(), schema);
		}
	}

	List<String> schemaList() {
		return schemaList;
	}

	void setSchemaList(List<String> schemaList) {
		this.schemaList = schemaList;
	}

	List<SessionStore.Tab> tabs() {
		return tabs;
	}

	int selectedTab() {
		return selectedTab;
	}

	void setSelectedTab(int selectedTab) {
		this.selectedTab = selectedTab;
	}

	Map<String, List<QueryResult>> tabResults() {
		return tabResults;
	}

	long lastActivityMillis() {
		return lastActivityMillis;
	}

	void setLastActivityMillis(long millis) {
		this.lastActivityMillis = millis;
	}
}
