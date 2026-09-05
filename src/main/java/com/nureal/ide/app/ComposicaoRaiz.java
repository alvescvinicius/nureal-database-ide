package com.nureal.ide.app;

import com.nureal.ide.core.format.FormatPreferences;
import com.nureal.ide.modulos.atualizacao.dominio.contratos.RepositorioDeReleasesPort;
import com.nureal.ide.modulos.atualizacao.infraestrutura.UpdateChecker;
import com.nureal.ide.modulos.conexoes.dominio.contratos.ConexaoAtivaPort;
import com.nureal.ide.modulos.conexoes.dominio.contratos.ConnectionRepository;
import com.nureal.ide.modulos.conexoes.infraestrutura.ConnectionManager;
import com.nureal.ide.modulos.conexoes.infraestrutura.ConnectionStore;
import com.nureal.ide.modulos.dialeto.dominio.contratos.DatabaseDialect;
import com.nureal.ide.modulos.dialeto.dominio.entidades.ProviderType;
import com.nureal.ide.modulos.dialeto.infraestrutura.DriverRegistry;
import com.nureal.ide.modulos.historico.infraestrutura.ExecutionHistoryStore;
import com.nureal.ide.modulos.historico.infraestrutura.SavedQueryStore;
import com.nureal.ide.modulos.historico.infraestrutura.SessionStore;
import com.nureal.ide.modulos.metadados.infraestrutura.MetadataCache;
import com.nureal.ide.modulos.autocomplete.infraestrutura.SqlCompletionProviderRSyntax;
import com.nureal.ide.ui.TableMetadataCache;

/**
 * Composition root da aplicacao (ver
 * .specs/13-composition-root-e-bootstrap.md): unico lugar autorizado a
 * instanciar as implementacoes concretas de infraestrutura dos modulos —
 * {@code dialeto}, {@code conexoes}, {@code metadados},
 * {@code historico}, {@code atualizacao}. Construido uma unica vez em
 * {@link com.nureal.ide.App#main}, antes de {@code MainWindow} existir, e
 * passado a ela via construtor.
 *
 * <p>Nao inclui o grafo de objetos do modulo {@code ia-chat} — esse
 * continua sendo montado lazily dentro de {@code MainWindow.openAiChat()}
 * porque depende de preferencias que mudam em tempo de execucao (troca de
 * modelo/provider), ao contrario dos objetos aqui, que existem uma unica
 * vez durante toda a vida do processo (ver lacuna 1 registrada em
 * .specs/11-modulo-ia-chat.md).
 *
 * <p>Nao inclui, tambem, os {@code ConnectionManager} de cada conexao
 * individual do usuario (um por {@code Conexao}/workspace, criados sob
 * demanda ao conectar) — apenas o {@code bootstrapConnectionManager}, usado
 * unicamente antes da primeira conexao real existir (ver javadoc do campo
 * em {@code MainWindow}).
 */
public final class ComposicaoRaiz {

	// Registry (nao mais "new MySqlDialect()" direto aqui): unico ponto que
	// sabe montar um DatabaseDialect por ProviderType — ver javadoc de
	// DriverRegistry. So MySQL esta registrado hoje (mesmo comportamento de
	// antes, zero mudanca visivel), mas um driver novo (Postgres/Oracle/
	// SQLite) so precisa se registrar AQUI, sem tocar em mais nenhum lugar
	// do app.
	private final DriverRegistry driverRegistry = new DriverRegistry();
	private final DatabaseDialect dialect = driverRegistry.driverFor(ProviderType.MYSQL);
	private final ConexaoAtivaPort bootstrapConnectionManager = new ConnectionManager(dialect);
	private final MetadataCache metadataCache = new MetadataCache();
	// Sem MetadataRepository fixo: cada chamada resolve o DatabaseDialect da
	// CONEXAO ativa no momento (ver MainWindow#metadataService), nao um
	// dialeto global preso a MYSQL — do contrario, toda leitura de metadados
	// (inclusive ao ABRIR uma conexao SQLite/Postgres) rodaria a consulta do
	// MySQL contra o banco errado (bug real: "no such table:
	// information_schema.COLUMNS" ao conectar num arquivo SQLite, porque
	// este campo ainda usava sempre o MySqlDialect fixo aqui).
	private final TableMetadataCache tableMetadataCache = new TableMetadataCache();
	private final SqlCompletionProviderRSyntax completionProvider = new SqlCompletionProviderRSyntax(dialect.keywords());
	private final ConnectionRepository connectionStore = new ConnectionStore();
	private final SessionStore sessionStore = new SessionStore();
	private final SavedQueryStore savedQueryStore = new SavedQueryStore();
	private final ExecutionHistoryStore historyStore = new ExecutionHistoryStore();
	private final RepositorioDeReleasesPort releasesRepository = new UpdateChecker();
	private final FormatPreferences formatPrefsStore = new FormatPreferences();

	public DatabaseDialect dialect() {
		return dialect;
	}

	public DriverRegistry driverRegistry() {
		return driverRegistry;
	}

	public ConexaoAtivaPort bootstrapConnectionManager() {
		return bootstrapConnectionManager;
	}

	public MetadataCache metadataCache() {
		return metadataCache;
	}

	public TableMetadataCache tableMetadataCache() {
		return tableMetadataCache;
	}

	public SqlCompletionProviderRSyntax completionProvider() {
		return completionProvider;
	}

	public ConnectionRepository connectionStore() {
		return connectionStore;
	}

	public SessionStore sessionStore() {
		return sessionStore;
	}

	public SavedQueryStore savedQueryStore() {
		return savedQueryStore;
	}

	public ExecutionHistoryStore historyStore() {
		return historyStore;
	}

	public RepositorioDeReleasesPort releasesRepository() {
		return releasesRepository;
	}

	public FormatPreferences formatPrefsStore() {
		return formatPrefsStore;
	}
}
