package com.nureal.ide.core.ai.specialist;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SpecialistRegistryTest {

    @Test
    void resolveMySqlIndependenteDeCaixa() {
        assertInstanceOf(MySqlSpecialist.class, SpecialistRegistry.resolve("MySQL").orElseThrow());
        assertInstanceOf(MySqlSpecialist.class, SpecialistRegistry.resolve("mysql").orElseThrow());
        assertInstanceOf(MySqlSpecialist.class, SpecialistRegistry.resolve("  MySQL  ").orElseThrow());
    }

    @Test
    void resolveVazioParaBancoDesconhecido() {
        assertTrue(SpecialistRegistry.resolve("Oracle").isEmpty());
        assertTrue(SpecialistRegistry.resolve("PostgreSQL").isEmpty());
    }

    @Test
    void resolveVazioParaNuloOuVazio() {
        assertTrue(SpecialistRegistry.resolve(null).isEmpty());
        assertTrue(SpecialistRegistry.resolve("").isEmpty());
        assertTrue(SpecialistRegistry.resolve("   ").isEmpty());
    }
}
