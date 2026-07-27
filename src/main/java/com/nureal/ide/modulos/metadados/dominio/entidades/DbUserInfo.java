package com.nureal.ide.modulos.metadados.dominio.entidades;
import com.nureal.ide.modulos.dialeto.dominio.contratos.DatabaseDialect;

/**
 * Um usuario do SERVIDOR de banco (nao um usuario da aplicacao — a Nureal
 * Database IDE nao tem login proprio, e uma ferramenta desktop single-user;
 * isto representa uma linha de {@code mysql.user}). A identidade real de um
 * usuario MySQL e o PAR {@code user}+{@code host} (o mesmo nome de usuario
 * pode existir varias vezes, uma por host de origem permitido) — por isso os
 * dois campos juntos, nunca so o nome, em qualquer lugar que precise
 * referenciar um usuario especifico (ver {@code DatabaseDialect#dropUserStatement}
 * e afins).
 */
public record DbUserInfo(String user, String host, boolean accountLocked, boolean passwordExpired) {

    /** Rotulo padrao "user@host" usado nas listas/dialogos. */
    public String label() {
        return user + "@" + host;
    }
}
