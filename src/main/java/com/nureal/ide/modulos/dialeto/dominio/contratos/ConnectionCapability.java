package com.nureal.ide.modulos.dialeto.dominio.contratos;

import com.nureal.ide.modulos.conexoes.dominio.entidades.ConnectionProfile;

import java.util.List;

/**
 * O minimo que TODO driver precisa pra abrir e manter uma conexao — parte
 * OBRIGATORIA de {@link DatabaseDialect} (nao opcional como
 * {@link SecurityCapability}/{@link AdminCapability}/{@link ReplicationCapability}):
 * sem isto nao ha conexao nenhuma pra fazer qualquer outra coisa.
 */
public interface ConnectionCapability {

    /** Identificador curto, ex: "mysql". */
    String id();

    /** Classe do driver JDBC. */
    String driverClassName();

    /** Monta a URL JDBC a partir do perfil de conexao. */
    String buildJdbcUrl(ConnectionProfile profile);

    /**
     * Instrucoes SQL executadas uma vez, logo apos a conexao ser aberta, para
     * configurar a sessao (ex.: charset, fuso horario, modo estrito) — ver
     * {@code com.nureal.ide.modulos.conexoes.infraestrutura.SessionInitializer}. Lista vazia
     * quando o dialeto nao precisa de nenhuma configuracao de sessao.
     */
    List<String> sessionInitStatements();

    /**
     * Consulta minima e barata para "keep-alive" da conexao (um SELECT de
     * teste, sem tocar em nenhuma tabela real) — usada para manter a sessao
     * viva enquanto a IDE esta aberta e a conexao fica ociosa por um tempo
     * (ver "Manter conexao viva" no menu de layout, em MainWindow). Default
     * {@code "SELECT 1"}, valido na maioria dos bancos; dialetos que exigem
     * uma clausula FROM (ex.: Oracle, {@code "SELECT 1 FROM DUAL"}) devem
     * sobrescrever.
     */
    default String keepAliveQuery() {
        return "SELECT 1";
    }
}
