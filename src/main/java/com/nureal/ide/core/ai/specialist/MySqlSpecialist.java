package com.nureal.ide.core.ai.specialist;

/** Unico specialist do MVP (a IDE so suporta MySQL de ponta a ponta ainda — ver README). */
public final class MySqlSpecialist implements Specialist {

    @Override
    public String id() {
        return "mysql";
    }

    @Override
    public String displayName() {
        return "MySQL Specialist";
    }

    @Override
    public String systemPromptFragment() {
        return """
                Voce esta especializado em MySQL. Considere:
                - LIMIT/OFFSET para paginacao (nao TOP nem FETCH FIRST)
                - Identificadores entre backticks (`tabela`, `coluna`), nunca aspas duplas
                - TINYINT(1) costuma representar booleano; ENUM e JSON sao tipos nativos
                - Engine padrao InnoDB (transacional); MyISAM ainda aparece em bancos legados
                - AUTO_INCREMENT para chaves geradas (nao SEQUENCE)
                - information_schema para consultas de metadados quando as tools nao bastarem
                """;
    }
}
