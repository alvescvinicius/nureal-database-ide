package com.nureal.ide.ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.nureal.ide.core.connection.ConnectionManager;
import com.nureal.ide.core.connection.ConnectionProfile;
import com.nureal.ide.core.metadata.model.SchemaInfo;
import com.nureal.ide.core.session.SessionStore;

/**
 * Conexao (workspace) de uma conexao: sua sessao JDBC, esquema e abas de SQL
 * proprias.
 */
final class Conexao {
	final String name; // nome da conexao (ou SCRATCH)
	final ConnectionProfile profile; // null para a conexao sem conexao
	final ConnectionManager mgr; // gerenciador JDBC proprio
	SchemaInfo schema; // esquema carregado (ou null)
	List<String> schemaList; // lista de esquemas (schema em branco)
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

	Conexao(String name, ConnectionProfile profile, ConnectionManager mgr) {
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

	ConnectionManager mgr() {
		return mgr;
	}

	SchemaInfo schema() {
		return schema;
	}

	void setSchema(SchemaInfo schema) {
		this.schema = schema;
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
}
