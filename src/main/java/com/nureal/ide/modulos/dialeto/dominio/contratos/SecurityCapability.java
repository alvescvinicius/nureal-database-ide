package com.nureal.ide.modulos.dialeto.dominio.contratos;

import java.util.List;

/**
 * Administracao do SERVIDOR (usuarios, privilegios, roles) — ver
 * {@code com.nureal.ide.ui.UserManagementDialog}/{@code UserRolesTab}.
 * Capacidade OPCIONAL de {@link DatabaseDialect} (ver
 * {@link DatabaseDialect#security()}): o modelo de privilegios muda muito
 * de banco para banco (Postgres usa {@code pg_roles}/GRANT com sintaxe
 * propria, bem diferente do {@code GRANT ... ON ... TO} do MySQL) — um
 * driver que ainda nao implementar isso simplesmente devolve
 * {@code Optional.empty()} em {@code security()}, e a UI trata a ausencia
 * (por ora, "recurso nao disponivel para este banco" — esconder o menu
 * inteiro fica para quando essa gating existir na UI).
 */
public interface SecurityCapability {

    /**
     * Lista os usuarios do SERVIDOR (nao do schema) — normalmente le uma
     * tabela de catalogo que exige privilegio administrativo para consultar
     * (ex.: {@code mysql.user}); o chamador deve tratar falha de permissao
     * como "conexao sem privilegio para administrar usuarios", nao como bug.
     * Retorna USER, HOST, ACCOUNT_LOCKED ('Y'/'N') e PASSWORD_EXPIRED
     * ('Y'/'N'), nesta ordem, sem parametros.
     */
    String listUsersQuery();

    /** {@code SHOW GRANTS} (ou equivalente) para um usuario+host especifico — saida crua, uma linha por GRANT. */
    String showGrantsQuery(String user, String host);

    /**
     * Cria um usuario novo. {@code expireNow} forca a troca de senha no
     * proximo login (politica comum ao entregar uma credencial nova a
     * alguem).
     */
    String createUserStatement(String user, String host, String password, boolean expireNow);

    /** Remove um usuario (e todos os privilegios/roles concedidos a ele junto). */
    String dropUserStatement(String user, String host);

    /** Troca a senha de um usuario existente. */
    String setPasswordStatement(String user, String host, String newPassword);

    /** Bloqueia/desbloqueia a conta (login recusado enquanto bloqueada, sem apagar usuario/privilegios). */
    String lockUserStatement(String user, String host, boolean lock);

    /** Forca a expiracao da senha atual agora (usuario precisa trocar no proximo login). */
    String expirePasswordStatement(String user, String host);

    /**
     * Monta o alvo de um GRANT/REVOKE ({@code *.*}, {@code `schema`.*},
     * {@code `schema`.`tabela`} ou {@code `schema`.`tabela` (`col1`, `col2`)})
     * a partir do nivel escolhido na UI — {@code schema}/{@code table} nulos
     * conforme o nivel (ambos nulos = global; so {@code table} nulo = schema
     * inteiro); {@code columns} vazio/nulo = privilegio na tabela inteira.
     */
    String privilegeTarget(String schema, String table, List<String> columns);

    /**
     * Concede privilegios a um usuario sobre o alvo montado por
     * {@link #privilegeTarget}. {@code withGrantOption} equivale a
     * {@code WITH GRANT OPTION} (o usuario passa a poder repassar esses
     * mesmos privilegios a outros).
     */
    String grantStatement(List<String> privileges, String target, String user, String host, boolean withGrantOption);

    /** Revoga privilegios de um usuario sobre o alvo montado por {@link #privilegeTarget}. */
    String revokeStatement(List<String> privileges, String target, String user, String host);

    /**
     * Privilegios validos SO no nivel global ({@code *.*}) — ex.:
     * administrar o servidor, criar outros usuarios, replicacao. Nao fazem
     * sentido presos a um schema/tabela especifico.
     */
    List<String> globalPrivilegeNames();

    /** Privilegios validos em nivel de schema ou tabela (o grosso do dia a dia: SELECT, INSERT, UPDATE, DELETE...). */
    List<String> objectPrivilegeNames();

    /** Subconjunto de {@link #objectPrivilegeNames()} que o banco aceita conceder POR COLUNA. */
    List<String> columnPrivilegeNames();

    /**
     * Cria uma role (MySQL 8+; conceito ausente em versoes antigas — a
     * execucao falha com uma mensagem clara do proprio servidor, que e
     * suficiente aqui). Uma role e um "pacote" de privilegios que pode ser
     * concedido a varios usuarios de uma vez, em vez de repetir o mesmo
     * conjunto de GRANTs usuario por usuario.
     */
    String createRoleStatement(String role);

    /** Remove uma role (usuarios que a tinham atribuida perdem os privilegios dela). */
    String dropRoleStatement(String role);

    /** Atribui uma role existente a um usuario (o usuario so GANHA os privilegios dela ao ativa-la — ver {@link #setDefaultRoleStatement}). */
    String grantRoleStatement(String role, String user, String host);

    /** Remove uma role de um usuario. */
    String revokeRoleStatement(String role, String user, String host);

    /**
     * Torna uma role ATIVA por padrao no login (sem isto, {@code GRANT role
     * TO user} so deixa a role DISPONIVEL — o usuario precisaria rodar
     * {@code SET ROLE} a cada sessao para os privilegios dela valerem).
     */
    String setDefaultRoleStatement(String role, String user, String host);

    /**
     * Roles conhecidas no servidor — melhor esforco: lista nomes distintos
     * que ja foram concedidos a pelo menos um usuario (nao ha, no MySQL, uma
     * marca confiavel e portatil entre versoes 8.x que diga "esta linha de
     * mysql.user e uma role e nao um usuario comum" fora dessa relacao).
     * Roles criadas mas ainda NUNCA atribuidas a ninguem nao aparecem aqui —
     * limitacao aceita, documentada na propria UI (ver UserManagementDialog).
     */
    String knownRolesQuery();
}
