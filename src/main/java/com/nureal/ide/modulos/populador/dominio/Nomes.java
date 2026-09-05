package com.nureal.ide.modulos.populador.dominio;

/** Listas fixas PT-BR usadas por {@link GeneratorKind} — sem dependencia externa (sem biblioteca tipo "Faker"). */
final class Nomes {

    private Nomes() {
    }

    static final String[] PRIMEIRO_NOME = { "Ana", "Bruno", "Carlos", "Daniela", "Eduardo", "Fernanda", "Gabriel",
            "Helena", "Igor", "Juliana", "Lucas", "Mariana", "Nicolas", "Otavio", "Patricia", "Rafael", "Sabrina",
            "Thiago", "Vanessa", "Wesley" };

    static final String[] SOBRENOME = { "Silva", "Santos", "Oliveira", "Souza", "Rodrigues", "Ferreira", "Almeida",
            "Pereira", "Lima", "Gomes", "Costa", "Ribeiro", "Martins", "Carvalho", "Rocha" };

    static final String[] DOMINIO_EMAIL = { "exemplo.com", "teste.com.br", "mail.com", "correio.com.br" };

    static final String[] CIDADE = { "Sao Paulo", "Rio de Janeiro", "Belo Horizonte", "Curitiba", "Porto Alegre",
            "Salvador", "Fortaleza", "Recife", "Brasilia", "Campinas" };

    static final String[] LOGRADOURO = { "Rua das Flores", "Av. Paulista", "Rua Sete de Setembro",
            "Av. Brasil", "Rua XV de Novembro", "Rua das Palmeiras", "Av. Central" };

    static final String[] PALAVRA = { "lorem", "ipsum", "teste", "exemplo", "dados", "registro", "amostra",
            "conteudo", "informacao", "valor" };
}
