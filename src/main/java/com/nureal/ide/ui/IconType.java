package com.nureal.ide.ui;

/**
 * Papel semantico de um icone: define a cor PADRAO aplicada por
 * {@link IconTheme#colorFor(IconType)}, seguindo a regra da marca (verde so
 * para acoes positivas, amarelo so para estados, vermelho so para erro; o
 * resto e neutro/estrutural).
 */
enum IconRole { NEUTRAL, POSITIVE, WARNING, NEGATIVE }

/**
 * Catalogo de todos os icones da aplicacao, por CONCEITO — nunca por forma
 * especifica desenhada. Cada valor e renderizado por {@link Icons#get(IconType)}
 * (e variantes com tamanho/cor customizados) a partir de primitivas
 * geometricas simples (ver Icons.java). O codigo cliente nunca mais chama um
 * metodo de desenho especifico (como o antigo {@code Icons.play()}); sempre
 * pede o CONCEITO: {@code Icons.get(IconType.RUN)}.
 */
enum IconType {

    // Acoes de arquivo/edicao genericas
    NEW(IconRole.NEUTRAL),
    OPEN(IconRole.NEUTRAL),
    SAVE(IconRole.NEUTRAL),
    EDIT(IconRole.NEUTRAL),
    DELETE(IconRole.NEGATIVE),
    COPY(IconRole.NEUTRAL),
    PASTE(IconRole.NEUTRAL),
    CLOSE(IconRole.NEUTRAL),
    FAVORITE(IconRole.NEUTRAL),

    // Execucao de SQL
    RUN(IconRole.POSITIVE),
    STOP(IconRole.NEUTRAL),
    FORMAT(IconRole.NEUTRAL),
    SEARCH(IconRole.NEUTRAL),
    FILTER(IconRole.NEUTRAL),

    // Objetos de banco de dados (arvore de objetos)
    DATABASE(IconRole.NEUTRAL),
    SCHEMA(IconRole.NEUTRAL),
    TABLE(IconRole.NEUTRAL),
    VIEW(IconRole.NEUTRAL),
    FUNCTION(IconRole.NEUTRAL),
    PROCEDURE(IconRole.NEUTRAL),
    TRIGGER(IconRole.NEUTRAL),
    COLUMN(IconRole.NEUTRAL),
    INDEX(IconRole.NEUTRAL),
    PRIMARY_KEY(IconRole.NEUTRAL),
    FOREIGN_KEY(IconRole.NEUTRAL),

    // Dados / IO
    EXPORT(IconRole.NEUTRAL),

    // Sistema / feedback
    SETTINGS(IconRole.NEUTRAL),
    HELP(IconRole.NEUTRAL),
    INFO(IconRole.NEUTRAL),
    WARNING(IconRole.WARNING),
    SUCCESS(IconRole.POSITIVE),
    ERROR(IconRole.NEGATIVE),
    REFRESH(IconRole.NEUTRAL),

    // Conexao
    CONNECTION(IconRole.POSITIVE),
    DISCONNECT(IconRole.NEGATIVE),
    STATUS_DOT(IconRole.NEUTRAL), // cor sempre informada pelo chamador (vermelho/amarelo/verde dinamico)

    // Layout / navegacao da janela
    PANEL_LEFT(IconRole.NEUTRAL),
    PANEL_BOTTOM(IconRole.NEUTRAL),
    CHEVRON_LEFT(IconRole.NEUTRAL),
    CHEVRON_RIGHT(IconRole.NEUTRAL),
    THEME_LIGHT(IconRole.NEUTRAL),
    THEME_DARK(IconRole.NEUTRAL);

    private final IconRole role;

    IconType(IconRole role) {
        this.role = role;
    }

    IconRole role() {
        return role;
    }
}
