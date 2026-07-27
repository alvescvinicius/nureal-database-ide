package com.nureal.ide.modulos.conexoes.dominio.contratos;
import com.nureal.ide.modulos.conexoes.infraestrutura.ConnectionStore;
import com.nureal.ide.modulos.conexoes.dominio.entidades.ConnectionProfile;

import java.io.IOException;
import java.util.List;

/**
 * Contrato de persistencia dos perfis de conexao do usuario — extraido de
 * {@link ConnectionStore} (ver .specs/03-modulo-conexoes-e-seguranca.md,
 * regra 1) para que nenhum consumidor futuro precise depender da forma de
 * armazenamento concreta (hoje um arquivo cifrado local; poderia, no
 * futuro, ser outra coisa sem exigir mudanca de quem usa este contrato).
 */
public interface ConnectionRepository {

    /** Le as conexoes salvas. Vazio se nao houver nenhuma ainda. */
    List<ConnectionProfile> load() throws IOException;

    /** Grava todas as conexoes, substituindo o conteudo salvo anteriormente. */
    void save(List<ConnectionProfile> connections) throws IOException;
}
