package com.nureal.ide.modulos.populador.dominio;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Random;

/**
 * Um "gerador" de valor fake para uma coluna — cada constante sabe montar o
 * proprio valor plausivel. {@link FakeDataGenerator#detectar} escolhe qual
 * usar por padrao (nome da coluna, depois tipo SQL); o dialogo do Populador
 * deixa o usuario TROCAR essa escolha por coluna (ver
 * {@code TablePopulatorDialog}), por isso este e um enum publico, nao um
 * detalhe interno de {@link FakeDataGenerator}.
 */
public enum GeneratorKind {

    NOME {
        @Override
        Object gerar(TipoColuna tipo, Random rnd) {
            return Nomes.PRIMEIRO_NOME[rnd.nextInt(Nomes.PRIMEIRO_NOME.length)] + " "
                    + Nomes.SOBRENOME[rnd.nextInt(Nomes.SOBRENOME.length)];
        }
    },
    EMAIL {
        @Override
        Object gerar(TipoColuna tipo, Random rnd) {
            String nome = Nomes.PRIMEIRO_NOME[rnd.nextInt(Nomes.PRIMEIRO_NOME.length)].toLowerCase(java.util.Locale.ROOT);
            String dominio = Nomes.DOMINIO_EMAIL[rnd.nextInt(Nomes.DOMINIO_EMAIL.length)];
            return nome + rnd.nextInt(1000) + "@" + dominio;
        }
    },
    CPF {
        @Override
        Object gerar(TipoColuna tipo, Random rnd) {
            return Cpf.gerar(rnd);
        }
    },
    TELEFONE {
        @Override
        Object gerar(TipoColuna tipo, Random rnd) {
            return String.format("(%02d) 9%04d-%04d", 11 + rnd.nextInt(89), rnd.nextInt(10000), rnd.nextInt(10000));
        }
    },
    CEP {
        @Override
        Object gerar(TipoColuna tipo, Random rnd) {
            return String.format("%05d-%03d", rnd.nextInt(100000), rnd.nextInt(1000));
        }
    },
    CIDADE {
        @Override
        Object gerar(TipoColuna tipo, Random rnd) {
            return Nomes.CIDADE[rnd.nextInt(Nomes.CIDADE.length)];
        }
    },
    ENDERECO {
        @Override
        Object gerar(TipoColuna tipo, Random rnd) {
            return Nomes.LOGRADOURO[rnd.nextInt(Nomes.LOGRADOURO.length)] + ", " + (1 + rnd.nextInt(2000));
        }
    },
    DATA {
        @Override
        Object gerar(TipoColuna tipo, Random rnd) {
            return Date.valueOf(LocalDate.now().minusDays(rnd.nextInt(5 * 365)));
        }
    },
    DATA_HORA {
        @Override
        Object gerar(TipoColuna tipo, Random rnd) {
            return Timestamp.valueOf(LocalDateTime.now().minusMinutes(rnd.nextInt(5 * 365 * 24 * 60)));
        }
    },
    DATA_NASCIMENTO {
        @Override
        Object gerar(TipoColuna tipo, Random rnd) {
            // 18 a 80 anos atras — faixa plausivel pra "data de nascimento",
            // ao contrario de DATA (qualquer data recente).
            int dias = (18 * 365) + rnd.nextInt((80 - 18) * 365);
            return Date.valueOf(LocalDate.now().minusDays(dias));
        }
    },
    BOOLEANO {
        @Override
        Object gerar(TipoColuna tipo, Random rnd) {
            return rnd.nextBoolean() ? 1 : 0;
        }
    },
    NUMERO_INTEIRO {
        @Override
        Object gerar(TipoColuna tipo, Random rnd) {
            // Faixa conservadora por sub-tipo — nao usa o limite real da
            // coluna (ex.: BIGINT ate 2^63) para o dado continuar "legivel"
            // em telas/relatorios de teste, nao so tecnicamente valido.
            String base = tipo.base();
            int max = switch (base) {
            case "TINYINT" -> 120;
            case "SMALLINT" -> 30_000;
            default -> 1_000_000;
            };
            return 1 + rnd.nextInt(max);
        }
    },
    NUMERO_DECIMAL {
        @Override
        Object gerar(TipoColuna tipo, Random rnd) {
            int escala = tipo.escala() != null ? tipo.escala() : 2;
            int precisao = tipo.tamanho() != null ? tipo.tamanho() : 10;
            int parteInteira = Math.max(1, precisao - escala);
            double max = Math.pow(10, Math.min(parteInteira, 6)); // teto de 10^6, mesmo motivo de NUMERO_INTEIRO
            double valor = rnd.nextDouble() * max;
            return java.math.BigDecimal.valueOf(valor).setScale(escala, java.math.RoundingMode.HALF_UP);
        }
    },
    TEXTO {
        @Override
        Object gerar(TipoColuna tipo, Random rnd) {
            String texto = Nomes.PALAVRA[rnd.nextInt(Nomes.PALAVRA.length)] + " "
                    + Nomes.PALAVRA[rnd.nextInt(Nomes.PALAVRA.length)];
            int limite = tipo.tamanho() != null ? tipo.tamanho() : 255;
            return texto.length() > limite ? texto.substring(0, limite) : texto;
        }
    };

    abstract Object gerar(TipoColuna tipo, Random rnd);

    /** {@link #rotulo()} — usado pelo combo box/renderer padrao do Swing no dialogo do Populador (chama toString() sozinho). */
    @Override
    public String toString() {
        return rotulo();
    }

    /** Rotulo exibido no combo box do dialogo do Populador. */
    public String rotulo() {
        return switch (this) {
        case NOME -> "Nome";
        case EMAIL -> "E-mail";
        case CPF -> "CPF";
        case TELEFONE -> "Telefone";
        case CEP -> "CEP";
        case CIDADE -> "Cidade";
        case ENDERECO -> "Endereco";
        case DATA -> "Data";
        case DATA_HORA -> "Data e hora";
        case DATA_NASCIMENTO -> "Data de nascimento";
        case BOOLEANO -> "Booleano (0/1)";
        case NUMERO_INTEIRO -> "Numero inteiro";
        case NUMERO_DECIMAL -> "Numero decimal";
        case TEXTO -> "Texto generico";
        };
    }
}
