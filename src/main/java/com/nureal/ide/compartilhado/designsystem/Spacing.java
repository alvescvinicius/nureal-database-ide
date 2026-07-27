package com.nureal.ide.compartilhado.designsystem;

/**
 * Escala UNICA de espacamento da aplicacao — pedida explicitamente na
 * revisao de refinamento visual premium ("adotar uma escala unica... 4, 8,
 * 12, 16, 24, 32px... eliminar espacamentos aleatorios"). Antes deste
 * arquivo, cada painel/dialogo escolhia seus proprios numeros "no olho"
 * (margens de 3, 5, 6, 10, 14... espalhadas por dezenas de chamadas de
 * {@code BorderFactory.createEmptyBorder}/{@code Insets}), do mesmo jeito
 * que {@link Typography} centralizou peso/cor de texto e {@link GridTheme}
 * centralizou cor.
 *
 * Mesma logica de {@link Typography}: existir aqui nao apaga automaticamente
 * todo numero antigo do app (uma varredura de 100% dos arquivos de uma vez
 * seria arriscado demais para uma mudanca "so visual") — qualquer tela NOVA,
 * ou qualquer tela revisada a partir de agora, deve usar estas constantes em
 * vez de inventar um numero proprio. Ver DESIGN_SYSTEM.md, secao de
 * espacamento, para o status de adocao por area do app.
 *
 * <p>Nomes curtos de proposito (chamados o tempo todo em construcao de UI):
 * <ul>
 *   <li>{@link #XS} (4px) — espacamento minimo, entre elementos MUITO
 *       relacionados (ex.: icone e o texto colado a ele).</li>
 *   <li>{@link #SM} (8px) — o "passo" mais comum do app: padding interno de
 *       botao/campo, gap entre controles de uma mesma barra.</li>
 *   <li>{@link #MD} (12px) — agrupamento de secoes dentro do mesmo painel.</li>
 *   <li>{@link #LG} (16px) — separacao entre blocos/paineis distintos.</li>
 *   <li>{@link #XL} (24px) — margens externas de dialogo/janela.</li>
 *   <li>{@link #XXL} (32px) — respiro grande, estados vazios/telas de
 *       destaque.</li>
 * </ul>
 */
public final class Spacing {

    private Spacing() {
    }

    public static final int XS = 4;
    public static final int SM = 8;
    public static final int MD = 12;
    public static final int LG = 16;
    public static final int XL = 24;
    public static final int XXL = 32;
}
