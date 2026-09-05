package com.nureal.ide.modulos.populador.dominio;

import com.nureal.ide.modulos.metadados.dominio.entidades.ColumnDetail;

import java.util.Locale;
import java.util.Random;

/**
 * Detecta o {@link GeneratorKind} mais plausivel para uma coluna (pelo NOME
 * primeiro, tipo SQL depois) e gera o valor de fato. Colunas FK/AUTO_INCREMENT
 * nunca passam por aqui — ver {@code GerarLinhasFakeHandler} (FK vem de
 * amostra da tabela pai, AUTO_INCREMENT o proprio banco gera).
 */
public final class FakeDataGenerator {

    private FakeDataGenerator() {
    }

    /**
     * Primeiro heuristica de NOME de coluna (mais especifica, ganha sempre
     * que bater), depois fallback por TIPO SQL. Ordem das checagens de nome
     * importa: "email" antes de "nome" (uma coluna "nome_email" nunca
     * existe de verdade, mas a ordem evita qualquer ambiguidade futura).
     */
    public static GeneratorKind detectar(ColumnDetail coluna) {
        String nome = coluna.name().toLowerCase(Locale.ROOT);
        if (nome.contains("email")) {
            return GeneratorKind.EMAIL;
        }
        if (nome.contains("cpf")) {
            return GeneratorKind.CPF;
        }
        if (nome.contains("telefone") || nome.contains("celular") || nome.contains("fone")) {
            return GeneratorKind.TELEFONE;
        }
        if (nome.contains("cep")) {
            return GeneratorKind.CEP;
        }
        if (nome.contains("cidade")) {
            return GeneratorKind.CIDADE;
        }
        if (nome.contains("endereco") || nome.contains("logradouro") || nome.equals("rua") || nome.contains("_rua")) {
            return GeneratorKind.ENDERECO;
        }
        if (nome.contains("nascimento")) {
            return GeneratorKind.DATA_NASCIMENTO;
        }
        if (nome.contains("nome")) {
            return GeneratorKind.NOME;
        }
        if (nome.contains("ativo") || nome.contains("status") || nome.startsWith("is_") || nome.startsWith("fl_")) {
            return GeneratorKind.BOOLEANO;
        }
        return detectarPorTipo(TipoColuna.parse(coluna.type()));
    }

    private static GeneratorKind detectarPorTipo(TipoColuna tipo) {
        return switch (tipo.base()) {
        case "TINYINT" -> (tipo.tamanho() != null && tipo.tamanho() == 1) ? GeneratorKind.BOOLEANO
                : GeneratorKind.NUMERO_INTEIRO;
        case "BOOLEAN", "BOOL" -> GeneratorKind.BOOLEANO;
        case "SMALLINT", "MEDIUMINT", "INT", "INTEGER", "BIGINT" -> GeneratorKind.NUMERO_INTEIRO;
        case "DECIMAL", "NUMERIC", "FLOAT", "DOUBLE" -> GeneratorKind.NUMERO_DECIMAL;
        case "DATE" -> GeneratorKind.DATA;
        case "DATETIME", "TIMESTAMP" -> GeneratorKind.DATA_HORA;
        default -> GeneratorKind.TEXTO;
        };
    }

    public static Object gerar(GeneratorKind kind, ColumnDetail coluna, Random rnd) {
        return kind.gerar(TipoColuna.parse(coluna.type()), rnd);
    }
}
