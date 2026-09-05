package com.nureal.ide.modulos.dialeto.infraestrutura;

import com.nureal.ide.modulos.dialeto.dominio.contratos.DatabaseDialect;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MySQL implementa as 3 capacidades OPCIONAIS de {@link DatabaseDialect}
 * (ver a quebra de {@code DatabaseDialect} em capacidades) — este teste
 * fixa esse comportamento: se algum dia {@code MySqlDialect} parar de
 * implementar uma delas (ou o metodo passar a devolver {@code
 * Optional.empty()} por engano), a suite acusa aqui, no ponto certo, em
 * vez de um menu sumindo silenciosamente na UI.
 */
class MySqlDialectCapabilitiesTest {

    private final DatabaseDialect dialect = new MySqlDialect();

    @Test
    void suportaSecurityCapability() {
        assertTrue(dialect.security().isPresent());
    }

    @Test
    void suportaAdminCapability() {
        assertTrue(dialect.admin().isPresent());
    }

    @Test
    void suportaReplicationCapability() {
        assertTrue(dialect.replication().isPresent());
    }
}
