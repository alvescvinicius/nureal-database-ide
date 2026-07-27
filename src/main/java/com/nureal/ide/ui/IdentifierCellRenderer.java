package com.nureal.ide.ui;
import com.nureal.ide.compartilhado.designsystem.GridTheme;

import java.awt.Color;

import javax.swing.SwingConstants;

/**
 * Colunas identificadoras (id, *_id, uuid/guid): laranja forte.
 *
 * Alinhamento depende do TIPO REAL do valor (o que o JDBC devolveu), nao do
 * GRUPO nem da aparencia do texto: um ID genuinamente numerico (a coluna e
 * INT/BIGINT/... de verdade, e o driver devolve um {@link Number}) alinha a
 * direita, como qualquer numero — qualquer outra coisa (String, mesmo que
 * seja so digitos como "3" ou "00042", UUID, etc.) alinha a ESQUERDA, como
 * texto.
 *
 * Importante nao decidir isso pelo "formato" do texto (ex.: "parece um
 * numero curto" vs "parece um hash longo"): uma coluna VARCHAR cujas
 * primeiras linhas so tem digitos (ex.: {@code client_order_id} com valores
 * "3", "42"...) NAO e numerica so porque o conteudo de hoje parece um
 * numero — o BANCO diz que e texto, entao o alinhamento/estilo tem que
 * seguir o texto, nao adivinhar pelo valor. Foi exatamente esse o bug
 * relatado: uma coluna VARCHAR alinhada/parecendo numero so pq os valores
 * de exemplo eram digitos curtos.
 */
final class IdentifierCellRenderer extends AbstractTypedCellRenderer {

    private static final long serialVersionUID = 1L;

    @Override
    int alignment(Object value) {
        return isOpaqueIdentifier(value) ? SwingConstants.LEFT : SwingConstants.RIGHT;
    }

    @Override
    Color colorFor(Object value) {
        return GridTheme.COLOR_IDENTIFIER;
    }

    /** Opaco (alinha a esquerda, como texto) = qualquer coisa que NAO seja um numero de verdade. */
    private static boolean isOpaqueIdentifier(Object value) {
        return !(value instanceof Number);
    }
}
