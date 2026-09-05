package com.nureal.ide.modulos.dialeto.dominio.entidades;

/**
 * Qual SGBD uma conexao/driver representa — chave usada por
 * {@code DriverRegistry} para resolver o {@code DatabaseDialect} certo.
 * Hoje so {@link #MYSQL} tem implementacao (ver
 * {@code modulos.dialeto.infraestrutura.MySqlDialect}); os demais existem
 * aqui como pontos de extensao ja nomeados, sem nenhuma implementacao
 * ainda (ver .specs — evolucao multi-banco).
 */
public enum ProviderType {
    MYSQL,
    POSTGRESQL,
    ORACLE,
    SQLITE
}
