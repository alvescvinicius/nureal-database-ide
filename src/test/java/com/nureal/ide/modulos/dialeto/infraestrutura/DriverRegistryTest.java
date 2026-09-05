package com.nureal.ide.modulos.dialeto.infraestrutura;

import com.nureal.ide.modulos.dialeto.dominio.contratos.DatabaseDialect;
import com.nureal.ide.modulos.dialeto.dominio.entidades.ProviderType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DriverRegistryTest {

    @Test
    void mysqlJaVemRegistradoPorPadrao() {
        DriverRegistry registry = new DriverRegistry();

        assertTrue(registry.isSupported(ProviderType.MYSQL));
        assertEquals("mysql", registry.driverFor(ProviderType.MYSQL).id());
    }

    @Test
    void postgresqlJaVemRegistradoPorPadrao() {
        DriverRegistry registry = new DriverRegistry();

        assertTrue(registry.isSupported(ProviderType.POSTGRESQL));
        assertEquals("postgresql", registry.driverFor(ProviderType.POSTGRESQL).id());
    }

    @Test
    void sqliteJaVemRegistradoPorPadrao() {
        DriverRegistry registry = new DriverRegistry();

        assertTrue(registry.isSupported(ProviderType.SQLITE));
        assertEquals("sqlite", registry.driverFor(ProviderType.SQLITE).id());
    }

    @Test
    void providerAindaSemDriverNaoEstaSuportado() {
        DriverRegistry registry = new DriverRegistry();

        assertFalse(registry.isSupported(ProviderType.ORACLE));
    }

    @Test
    void resolverProviderSemDriverLancaExcecaoClara() {
        DriverRegistry registry = new DriverRegistry();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> registry.driverFor(ProviderType.ORACLE));
        assertTrue(ex.getMessage().contains("ORACLE"));
    }

    @Test
    void novoDriverPodeSerRegistradoSemMexerNoResto() {
        DriverRegistry registry = new DriverRegistry();
        DatabaseDialect fake = registry.driverFor(ProviderType.MYSQL); // reaproveita qualquer instancia real so pra testar o registro

        registry.register(ProviderType.ORACLE, fake);

        assertTrue(registry.isSupported(ProviderType.ORACLE));
        assertEquals(fake, registry.driverFor(ProviderType.ORACLE));
    }
}
