package com.nureal.ide.modulos.dialeto.infraestrutura;

import com.nureal.ide.modulos.dialeto.dominio.contratos.DatabaseDialect;
import com.nureal.ide.modulos.dialeto.dominio.entidades.ProviderType;

import java.util.EnumMap;
import java.util.Map;

/**
 * Unico ponto que sabe "qual {@link DatabaseDialect} usar para qual
 * {@link ProviderType}" — substitui {@code new MySqlDialect()} espalhado
 * pelo app (achado numa auditoria de arquitetura: existia em
 * {@code ComposicaoRaiz} E em {@code ConnectionEditDialog}, dois pontos
 * independentes que precisariam mudar em sincronia toda vez que um driver
 * novo entrasse). Registrado uma vez no {@code ComposicaoRaiz}; qualquer
 * consumidor que precisar de um dialect por {@link ProviderType} pede a
 * ESTE objeto, nunca instancia um driver concreto diretamente.
 * <p>
 * MySQL, PostgreSQL e SQLite tem driver de verdade hoje (Oracle continua so
 * como ponto de extensao nomeado, ver {@link ProviderType}) — {@link
 * #driverFor} lanca para qualquer {@link ProviderType} ainda nao
 * registrado, em vez de devolver {@code null} (erro aparece na hora, no
 * lugar certo, nao como um {@code NullPointerException} generico mais
 * adiante).
 */
public final class DriverRegistry {

    private final Map<ProviderType, DatabaseDialect> drivers = new EnumMap<>(ProviderType.class);

    public DriverRegistry() {
        register(ProviderType.MYSQL, new MySqlDialect());
        register(ProviderType.POSTGRESQL, new PostgresDialect());
        register(ProviderType.SQLITE, new SqliteDialect());
    }

    /** Ponto de extensao: um driver novo (Postgres, Oracle, SQLite...) so precisa se registrar aqui — nenhum outro modulo muda. */
    public void register(ProviderType provider, DatabaseDialect driver) {
        drivers.put(provider, driver);
    }

    public DatabaseDialect driverFor(ProviderType provider) {
        DatabaseDialect driver = drivers.get(provider);
        if (driver == null) {
            throw new IllegalArgumentException("Nenhum driver registrado para " + provider);
        }
        return driver;
    }

    /** {@code true} se ja existe um driver registrado para {@code provider} — usado por UI que precisar listar/filtrar bancos disponiveis. */
    public boolean isSupported(ProviderType provider) {
        return drivers.containsKey(provider);
    }
}
