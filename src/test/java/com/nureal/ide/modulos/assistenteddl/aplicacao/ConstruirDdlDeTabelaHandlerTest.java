package com.nureal.ide.modulos.assistenteddl.aplicacao;

import com.nureal.ide.modulos.assistenteddl.aplicacao.ConstruirDdlDeTabelaOutput.ErroDeValidacao;
import com.nureal.ide.modulos.dialeto.infraestrutura.MySqlDialect;
import com.nureal.ide.modulos.metadados.dominio.entidades.NewColumnSpec;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConstruirDdlDeTabelaHandlerTest {

    private final ConstruirDdlDeTabelaHandler handler = new ConstruirDdlDeTabelaHandler(new MySqlDialect());

    private static ConstruirDdlDeTabelaInput criarTabela(String tableName, boolean jaExiste,
            List<NewColumnSpec> colunas) {
        return new ConstruirDdlDeTabelaInput(false, null, tableName, jaExiste, "",
                colunas, List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    }

    @Test
    void nomeVazioRetornaErroNomeInvalido() {
        ConstruirDdlDeTabelaOutput output = handler.executar(criarTabela("", false, List.of()));

        assertFalse(output.sucesso());
        assertEquals(ErroDeValidacao.NOME_INVALIDO, output.erro());
        assertEquals("Informe o nome da tabela.", output.mensagemErro());
    }

    @Test
    void nomeComCaracterInvalidoRetornaErroNomeInvalido() {
        ConstruirDdlDeTabelaOutput output = handler.executar(criarTabela("1tabela", false, List.of()));

        assertFalse(output.sucesso());
        assertEquals(ErroDeValidacao.NOME_INVALIDO, output.erro());
    }

    @Test
    void nomeJaExistenteRetornaErroNomeDuplicado() {
        List<NewColumnSpec> colunas = List.of(new NewColumnSpec("id", "INT", "", false, true, true, "", ""));
        ConstruirDdlDeTabelaOutput output = handler.executar(criarTabela("usuarios", true, colunas));

        assertFalse(output.sucesso());
        assertEquals(ErroDeValidacao.NOME_DUPLICADO, output.erro());
        assertTrue(output.mensagemErro().contains("usuarios"));
    }

    @Test
    void semColunasRetornaErroSemColunas() {
        ConstruirDdlDeTabelaOutput output = handler.executar(criarTabela("usuarios", false, List.of()));

        assertFalse(output.sucesso());
        assertEquals(ErroDeValidacao.SEM_COLUNAS, output.erro());
    }

    @Test
    void entradaValidaGeraCreateTableComSucesso() {
        List<NewColumnSpec> colunas = List.of(new NewColumnSpec("id", "INT", "", false, true, true, "", ""));
        ConstruirDdlDeTabelaOutput output = handler.executar(criarTabela("usuarios", false, colunas));

        assertTrue(output.sucesso());
        assertEquals(1, output.statements().size());
        assertTrue(output.statements().get(0).toUpperCase().contains("CREATE TABLE"));
        assertTrue(output.statements().get(0).contains("usuarios"));
    }

    @Test
    void modoAlterarIgnoraValidacaoDeNomeEDelegaAoDialeto() {
        List<NewColumnSpec> novasColunas = List.of(new NewColumnSpec("nome", "VARCHAR", "255", true, false, false, "", ""));
        ConstruirDdlDeTabelaInput input = new ConstruirDdlDeTabelaInput(true, "usuarios", null, false, null,
                novasColunas, List.of(), List.of(), List.of(), List.of(), List.of(), List.of());

        ConstruirDdlDeTabelaOutput output = handler.executar(input);

        assertTrue(output.sucesso());
        assertEquals(1, output.statements().size());
        assertTrue(output.statements().get(0).toUpperCase().contains("ALTER TABLE"));
    }
}
