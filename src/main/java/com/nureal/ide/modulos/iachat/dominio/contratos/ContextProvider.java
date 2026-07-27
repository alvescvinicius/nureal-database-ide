package com.nureal.ide.modulos.iachat.dominio.contratos;
import com.nureal.ide.modulos.iachat.dominio.entidades.AgentContext;

/**
 * Transforma o estado atual da IDE em {@link AgentContext} consumivel pelo
 * Agent. Somente leitura, sem dependencia de UI (ver
 * {@code docs/041-Context-Overview.md}).
 */
public interface ContextProvider {

    AgentContext collect();
}
