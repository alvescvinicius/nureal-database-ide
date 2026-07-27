package com.nureal.ide.modulos.iachat.infraestrutura.specialist;
import com.nureal.ide.modulos.iachat.dominio.contratos.Specialist;
import com.nureal.ide.modulos.iachat.aplicacao.PromptComposer;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Resolve o {@link Specialist} certo a partir do nome do produto do banco
 * (mesma fonte que {@code SessionInitializer} ja usa:
 * {@code Connection#getMetaData().getDatabaseProductName()}). Sem banco
 * conhecido (nenhuma conexao ativa, ou tipo ainda sem specialist), devolve
 * vazio — o {@code PromptComposer} simplesmente nao acrescenta fragmento
 * nenhum nesse caso, nunca falha.
 */
public final class SpecialistRegistry {

    private static final Map<String, Specialist> BY_PRODUCT_NAME = Map.of("mysql", new MySqlSpecialist());

    private SpecialistRegistry() {
    }

    public static Optional<Specialist> resolve(String databaseProductName) {
        if (databaseProductName == null || databaseProductName.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(BY_PRODUCT_NAME.get(databaseProductName.trim().toLowerCase(Locale.ROOT)));
    }
}
