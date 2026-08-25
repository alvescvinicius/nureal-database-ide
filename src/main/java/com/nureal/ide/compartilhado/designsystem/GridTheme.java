package com.nureal.ide.compartilhado.designsystem;

import com.nureal.ide.core.sql.SqlTypeKind;

import java.awt.Color;

/**
 * Paleta de cores da grade de resultados — ponto UNICO de verdade para todas
 * as cores usadas por {@link AbstractTypedCellRenderer} e suas subclasses,
 * {@link ColumnHeaderRenderer}, {@link ResultGrid}, {@link RowNumberGutter},
 * {@link ColumnMetadataPopup}, {@link SqlEditorPane} (syntax highlight),
 * {@link CellContentViewer} e o autocomplete.
 *
 * Antes desta classe, as mesmas cores (ex.: a borda {@code 0xCBD5E1} do
 * cabecalho e do canto do gutter) estavam definidas em duplicidade em varios
 * arquivos — bastava mudar uma paleta para os locais ficarem inconsistentes
 * entre si. Qualquer ajuste futuro de tema (ex.: um modo escuro dedicado a
 * grade) muda em um unico lugar.
 *
 * <p>
 * ESTE E EXATAMENTE ESSE AJUSTE: os campos eram {@code static final}, ou
 * seja, a grade de resultados sempre pintava com a paleta CLARA, mesmo com o
 * app inteiro no tema escuro (FlatDarkLaf) — cabecalho, zebra e gutter
 * apareciam como uma caixa branca "fora do tema" no meio de uma janela
 * escura, e o renderer textual chegava a pintar o texto de toda celula
 * textual em {@code 0x263238} (quase preto) por cima de um fundo escuro,
 * ilegivel. Os campos agora sao mutaveis; {@link #applyPalette(boolean)}
 * troca todos de uma vez para a variante clara ou escura, chamado no
 * arranque (tema claro, o padrao do app) e de novo em
 * {@code MainWindow#toggleTheme} sempre que o usuario alterna o tema.
 *
 * <p>
 * <b>Sistema semantico de cores por tipo de dado</b> (ver DESIGN_SYSTEM.md,
 * secao "Rodada 2 — quase monocromatico"): {@link #colorFor(SqlTypeKind)}
 * abaixo e o UNICO lugar que decide qual {@link Color} cada categoria usa
 * na EXIBICAO DE DADOS (grade de resultados, autocomplete, tooltips,
 * visualizador de celula, metadados) — nenhum outro componente de dados pode
 * ter sua propria cor "por tipo". Pedido explicito do usuario (revisao desta
 * regra, depois de ver a 1a versao "colorida demais" em uso real): a grade
 * representa DADOS, nao CODIGO — cor so deve aparecer quando tem SIGNIFICADO
 * ESTRUTURAL (chave primaria/estrangeira, data, JSON/XML, booleano, nulo,
 * edicao pendente, erro); todo o resto (texto comum, numeros, enum, uuid,
 * binario, hora) usa a cor PADRAO do tema (branco no escuro / preto no
 * claro) — ver {@link #COLOR_DEFAULT_TEXT}. O EDITOR SQL e diferente de
 * proposito: la a cor ajuda a ESCREVER codigo, entao mantem um destaque mais
 * rico (numeros/strings/booleano continuam cada um com sua propria cor) — o
 * editor le os campos {@link #COLOR_INTEGER}/{@link #COLOR_DECIMAL}/
 * {@link #COLOR_TEXTUAL}/{@link #COLOR_BOOLEAN} DIRETAMENTE (ver
 * {@code SqlEditorPane#applySemanticSyntaxColors}), nunca por
 * {@link #colorFor(SqlTypeKind)} — por isso estes 2 caminhos podem divergir
 * sem duplicar logica: mudar a cor de exibicao de dados aqui em
 * {@link #colorFor(SqlTypeKind)} NUNCA afeta o editor, e vice-versa.
 */
public final class GridTheme {

    private GridTheme() {
    }

    // ---------- Cores por tipo de dado (renderers da grade + editor + autocomplete + tooltips) ----------
    /** Palavras-chave SQL (SELECT/FROM/WHERE/...) — usado pelo editor (syntax highlight), nao pela grade. */
    public static Color COLOR_KEYWORD;
    /** Nomes de tabela/view/procedure/function/trigger — neutro, para nao competir visualmente com as palavras-chave. */
    public static Color COLOR_OBJECT_NAME;
    /** Nomes de coluna — mesmo tom neutro de {@link #COLOR_OBJECT_NAME} (ver DESIGN_SYSTEM.md). */
    public static Color COLOR_COLUMN_NAME;

    /**
     * Cores EDITOR-ONLY (ver javadoc da classe): {@link #COLOR_INTEGER}/
     * {@link #COLOR_DECIMAL}/{@link #COLOR_TEXTUAL}/{@link #COLOR_ENUM}/
     * {@link #COLOR_UUID}/{@link #COLOR_BINARY}/{@link #COLOR_TIME} continuam
     * existindo com os MESMOS valores de antes (o editor SQL le
     * {@link #COLOR_INTEGER}/{@link #COLOR_DECIMAL}/{@link #COLOR_TEXTUAL}
     * diretamente), mas {@link #colorFor(SqlTypeKind)} — usado pela grade e
     * pelos demais visualizadores de DADOS — nao aponta mais pra elas: essas
     * 7 categorias viram {@link #COLOR_DEFAULT_TEXT} na exibicao de dados
     * (pedido explicito do usuario, ver javadoc da classe).
     */
    public static Color COLOR_INTEGER;
    public static Color COLOR_DECIMAL;
    public static Color COLOR_TEXTUAL;
    public static Color COLOR_ENUM;
    public static Color COLOR_DATE;
    public static Color COLOR_TIME;
    public static Color COLOR_DATETIME;
    /** Cor do LITERAL booleano no editor SQL (TRUE/FALSE, um unico tom) — a grade usa {@link #COLOR_BOOLEAN_TRUE}/{@link #COLOR_BOOLEAN_FALSE} (por VALOR) em vez desta. */
    public static Color COLOR_BOOLEAN;
    public static Color COLOR_UUID;
    public static Color COLOR_JSON;
    public static Color COLOR_XML;
    public static Color COLOR_BINARY;
    public static Color COLOR_NULL;

    /**
     * Cor PADRAO de texto na exibicao de DADOS (grade, autocomplete,
     * tooltips, visualizador de celula, metadados) — branco no tema escuro,
     * preto no tema claro. Pedido explicito do usuario: "a maioria dos dados
     * exibidos e texto e numeros; colorir tudo gera fadiga visual... eu nao
     * coloriria tipos numericos [nem] strings... assim, quando uma data
     * aparecer em roxo ou um JSON em ciano, o destaque sera imediato". Usada
     * por {@link #colorFor(SqlTypeKind)} para INTEGER/DECIMAL/TEXTUAL/ENUM/
     * UUID/BINARY/TIME (nenhuma dessas 7 categorias tem "significado
     * estrutural" — sao so o CONTEUDO normal de uma tabela).
     */
    public static Color COLOR_DEFAULT_TEXT;

    /**
     * Identificadores (id/*_id/uuid pelo NOME da coluna, sem FK/PK real nos
     * metadados) — EXCECAO deliberada ao sistema de "cor so pelo tipo real":
     * pedido explicito do usuario para manter o destaque de identificador que
     * ja existia (laranja). Ver Rodada de "Sistema Semantico de Cores" em
     * DESIGN_SYSTEM.md para a decisao explicita. NAO e mais usada para FK de
     * verdade (metadados confirmados) — essa e {@link #COLOR_FOREIGN_KEY}
     * agora (Rodada 2), uma cor PROPRIA (amarelo) distinta do dourado da PK.
     */
    public static Color COLOR_IDENTIFIER;

    // ---------- Destaque de chave (grid) — ver AbstractTypedCellRenderer ----------
    // Sobrepoe a cor do TIPO da coluna (nao mexe no alinhamento nem na logica
    // de RendererFactory) quando a coluna e, de fato, chave primaria ou
    // estrangeira da tabela de origem — metadados REAIS do banco (via
    // ColumnMetadataResolver), nao heuristica de nome. PK = dourado; FK =
    // amarelo (COLOR_FOREIGN_KEY, cor PROPRIA desde a Rodada 2 — antes
    // reaproveitava o laranja de COLOR_IDENTIFIER; pedido explicito do
    // usuario para distinguir PK de FK visualmente, "mantem relacao visual
    // com PK sem confundir").
    public static Color COLOR_PRIMARY_KEY;
    public static Color COLOR_FOREIGN_KEY;

    /**
     * Cor do VALOR booleano na grade (Rodada 2: volta a ser por VALOR, nao
     * por TIPO — reverte a decisao da Rodada 1 apos revisao do usuario em
     * uso real). TRUE = azul ("associado a estado ativo/ligado"); FALSE =
     * laranja ("chama atencao sem parecer erro" — pedido explicito do
     * usuario para EVITAR vermelho puro aqui, reservado exclusivamente para
     * erros/valores invalidos). Ver {@link BooleanCellRenderer#colorFor}.
     */
    public static Color COLOR_BOOLEAN_TRUE;
    public static Color COLOR_BOOLEAN_FALSE;

    // ---------- Status geral positivo/negativo (NAO e cor-por-tipo/valor) ----------
    // Usado por indicadores de STATUS espalhados pelo app que nao tem nada a
    // ver com o tipo/valor de uma coluna: o "dot" de conectado/desconectado
    // (MainWindow), sucesso/falha no historico de execucao (HistoryPanel), o
    // aviso vermelho de acao destrutiva do Backup/Restore. Deliberadamente
    // SEPARADOS de COLOR_BOOLEAN_TRUE/FALSE (que sao sobre VALOR de celula,
    // nao sobre status do app), mesmo que as cores acabem parecidas — os dois
    // pares evoluem de forma independente.
    public static Color COLOR_LOGIC_TRUE;
    public static Color COLOR_LOGIC_FALSE;

    // ---------- Selecao / celula ativa ----------
    public static Color SELECTION_BACKGROUND;
    public static Color SELECTION_FOREGROUND;
    public static Color ACTIVE_CELL_BORDER;

    // ---------- Zebra / hover (linhas nao selecionadas) ----------
    public static Color ZEBRA_EVEN;
    public static Color ZEBRA_ODD;
    public static Color HOVER_BACKGROUND;

    // ---------- Edicao na grade (ver GridEditController) ----------
    /** Celula com valor editado, ainda nao salvo. */
    public static Color EDIT_DIRTY_CELL;
    /** Linha inteira nova (inserida, ainda nao salva). */
    public static Color EDIT_NEW_ROW;
    /** Linha marcada para exclusao ao salvar. */
    public static Color EDIT_DELETED_ROW;

    // ---------- Grid lines ----------
    public static Color GRID_LINE;

    // ---------- Cabecalho ----------
    public static Color HEADER_BACKGROUND;
    public static Color HEADER_FOREGROUND;
    public static Color HEADER_BORDER;
    public static Color SORT_INDICATOR_ACTIVE;
    public static Color SORT_INDICATOR_INACTIVE;
    /** Cabecalho da coluna atualmente "encontrada" (ver ResultGrid#highlightSelectedColumn). */
    public static Color HEADER_HIGHLIGHT_BACKGROUND;
    public static Color HEADER_HIGHLIGHT_BORDER;

    // ---------- Coluna de numeracao (gutter) ----------
    public static Color GUTTER_BACKGROUND;
    public static Color GUTTER_FOREGROUND;

    // ---------- Texto auxiliar (labels de metadados, filtro, etc.) ----------
    public static Color MUTED_TEXT;

    // ---------- Destaque semantico de card/painel (Nureal Design System — NCard/ui.components) ----------
    // Categorias que nem a grade nem o editor precisavam antes do NDS (cards
    // do chat de IA: dica/aviso/erro/SQL/tool) — publico porque ui.components
    // (pacote FILHO, nunca o contrario) consome direto, mesma fonte unica de
    // verdade que o resto da paleta.
    public static Color ACCENT_INFO;
    public static Color ACCENT_WARNING;
    public static Color ACCENT_ERROR;
    public static Color ACCENT_SQL;
    public static Color ACCENT_TOOL;

    /**
     * Verde da marca — MESMO valor em qualquer tema (claro/escuro), ao
     * contrario dos {@code ACCENT_*} acima (que trocam por
     * {@link #applyPalette}). Fonte unica de verdade movida para ca (era
     * {@code MainWindow.ACCENT}) na migracao deste componente para
     * {@code compartilhado.designsystem}: um componente de design system
     * nunca pode depender de {@code MainWindow} (pacote pai da aplicacao),
     * so o contrario. {@code MainWindow.ACCENT} continua existindo, so que
     * delegando pra ca, para nao quebrar os demais consumidores ja
     * existentes (ver {@code MainWindow}).
     */
    public static final Color BRAND_GREEN = new Color(0x1E9147);

    // Tema ESCURO agora e o padrao do app (ver App#main) — a paleta inicial
    // da grade precisa combinar, senao a primeira janela pintaria com cores
    // claras por baixo do FlatDarkLaf ja ativo ate o primeiro toggleTheme().
    static {
        applyPalette(true);
    }

    /**
     * Troca TODA a paleta de uma vez para a variante clara ou escura —
     * chamado no arranque e sempre que {@code MainWindow#toggleTheme} alterna
     * o Look and Feel (FlatLightLaf/FlatDarkLaf). Grades ja construidas (ver
     * {@code ResultGrid}) leem estes campos a cada pintura de celula, entao
     * passam a refletir a nova paleta assim que repintarem; a UNICA excecao
     * ja corrigida era {@code AbstractTypedCellRenderer#COLOR_NULL}, que
     * copiava o valor uma unica vez — ver o javadoc de la.
     */
    public static void applyPalette(boolean dark) {
        if (dark) {
            applyDarkPalette();
        } else {
            applyLightPalette();
        }
    }

    private static void applyDarkPalette() {
        // ---- Sistema semantico de cores por tipo de dado (ver DESIGN_SYSTEM.md) ----
            // Paleta ESTIMADA a partir da descricao do usuario (nomes de cor,
            // nao hex exatos) — mesmo criterio ja usado para o verde da marca
            // (ver Rodada de brand color): ajustavel depois se surgir um valor
            // oficial, sempre neste UNICO lugar.
            COLOR_KEYWORD = new Color(0x56, 0x9C, 0xD6); // azul institucional (mesmo tom do editor VS Code Dark+)
            COLOR_OBJECT_NAME = new Color(0xE8, 0xEA, 0xED); // branco/neutro — nao compete com palavras-chave
            COLOR_COLUMN_NAME = new Color(0xD7, 0xDE, 0xE3); // idem, leve diferenca de legibilidade

            // ---- Cores EDITOR-ONLY (inalteradas desde a Rodada 1 — o editor
            // continua com destaque rico; so a grade/exibicao de dados muda
            // na Rodada 2, ver colorFor(SqlTypeKind) mais abaixo) ----
            COLOR_INTEGER = new Color(0x81, 0xD4, 0xFA); // azul claro
            COLOR_DECIMAL = new Color(0x29, 0xB6, 0xF6); // azul vibrante (mais saturado que INTEGER)
            COLOR_TEXTUAL = new Color(0x81, 0xC7, 0x84); // verde
            COLOR_ENUM = new Color(0xC8, 0xE6, 0xC9); // verde claro
            COLOR_TIME = new Color(0xFF, 0xE0, 0x82); // amarelo
            COLOR_BOOLEAN = new Color(0xCE, 0x93, 0xD8); // roxo (literal TRUE/FALSE no editor)
            COLOR_UUID = new Color(0x9F, 0xA8, 0xDA); // azul arroxeado
            COLOR_BINARY = new Color(0x90, 0xA4, 0xAE); // cinza azulado

            // ---- Cores com significado ESTRUTURAL (usadas por colorFor —
            // grade/autocomplete/tooltips/metadados) ----
            COLOR_DATE = new Color(0xB3, 0x9D, 0xDB); // roxo suave (DATE/DATETIME/TIMESTAMP unificados)
            COLOR_DATETIME = COLOR_DATE;
            COLOR_JSON = new Color(0x4F, 0xB3, 0xBF); // ciano/azul-petroleo discreto
            COLOR_XML = new Color(0x80, 0xCB, 0xC4); // verde-agua discreto
            COLOR_NULL = new Color(0x8A, 0x97, 0xA0); // cinza (+ italico, ver AbstractTypedCellRenderer) — ja estava bom

            // Cor PADRAO de texto na exibicao de dados (ver javadoc do campo)
            // — mesmo tom neutro ja usado por COLOR_OBJECT_NAME.
            COLOR_DEFAULT_TEXT = new Color(0xE8, 0xEA, 0xED);

            // Identificador por NOME (sem FK/PK real) — excecao deliberada,
            // ver javadoc de COLOR_IDENTIFIER.
            COLOR_IDENTIFIER = new Color(0xFF, 0xB7, 0x4D); // laranja
            COLOR_PRIMARY_KEY = new Color(0xFF, 0xD5, 0x4F); // dourado
            COLOR_FOREIGN_KEY = new Color(0xFF, 0xEE, 0x58); // amarelo — distinto do dourado da PK

            // Boolean da grade: por VALOR (Rodada 2) — ver javadoc dos campos.
            COLOR_BOOLEAN_TRUE = new Color(0x64, 0xB5, 0xF6); // azul
            COLOR_BOOLEAN_FALSE = new Color(0xFF, 0x98, 0x00); // laranja (nunca vermelho puro) — tom distinto de COLOR_IDENTIFIER

            COLOR_LOGIC_TRUE = new Color(0x66, 0xBB, 0x6A);
            COLOR_LOGIC_FALSE = new Color(0xE5, 0x73, 0x73);

            SELECTION_BACKGROUND = new Color(0x3E, 0x4A, 0x52);
            SELECTION_FOREGROUND = new Color(0xEC, 0xEF, 0xF1);
            ACTIVE_CELL_BORDER = new Color(0x90, 0xA4, 0xAE);

            ZEBRA_EVEN = new Color(0x2B, 0x2D, 0x30);
            ZEBRA_ODD = new Color(0x31, 0x34, 0x38);
            HOVER_BACKGROUND = new Color(0x3A, 0x3F, 0x44);

            EDIT_DIRTY_CELL = new Color(0x4A, 0x3B, 0x14);
            EDIT_NEW_ROW = new Color(0x1B, 0x43, 0x32);
            EDIT_DELETED_ROW = new Color(0x4A, 0x1F, 0x1F);

            GRID_LINE = new Color(0x3A, 0x3F, 0x44);

            HEADER_BACKGROUND = new Color(0x24, 0x26, 0x29);
            HEADER_FOREGROUND = new Color(0xCB, 0xD5, 0xE1);
            HEADER_BORDER = new Color(0x44, 0x48, 0x4D);
            SORT_INDICATOR_ACTIVE = new Color(0x34, 0xD3, 0x99);
            SORT_INDICATOR_INACTIVE = new Color(0x5B, 0x64, 0x70);
            HEADER_HIGHLIGHT_BACKGROUND = new Color(0x58, 0x4B, 0x14);
            HEADER_HIGHLIGHT_BORDER = new Color(0xCA, 0x8A, 0x04);

            GUTTER_BACKGROUND = new Color(0x24, 0x26, 0x29);
            GUTTER_FOREGROUND = new Color(0x8B, 0x95, 0xA1);

            MUTED_TEXT = new Color(0x9A, 0xA3, 0xAF);

            ACCENT_INFO = new Color(0x60, 0xA5, 0xFA);
            ACCENT_WARNING = new Color(0xF5, 0xC8, 0x42);
            ACCENT_ERROR = new Color(0xF8, 0x71, 0x71);
            ACCENT_SQL = new Color(0x2D, 0xD4, 0xBF);
            ACCENT_TOOL = new Color(0xA7, 0x8B, 0xFA);
    }

    private static void applyLightPalette() {
        // ---- Sistema semantico de cores por tipo de dado (tema claro) ----
            COLOR_KEYWORD = new Color(0x15, 0x65, 0xC0); // azul institucional
            COLOR_OBJECT_NAME = new Color(0x1B, 0x1F, 0x23); // preto/neutro
            COLOR_COLUMN_NAME = new Color(0x26, 0x32, 0x38);

            // ---- Cores EDITOR-ONLY (inalteradas — ver bloco escuro acima) ----
            COLOR_INTEGER = new Color(0x03, 0x9B, 0xE5); // azul claro
            COLOR_DECIMAL = new Color(0x01, 0x57, 0x9B); // azul vibrante (mais saturado/escuro que INTEGER)
            COLOR_TEXTUAL = new Color(0x2E, 0x7D, 0x32); // verde
            COLOR_ENUM = new Color(0x66, 0xBB, 0x6A); // verde claro
            COLOR_TIME = new Color(0xF9, 0xA8, 0x25); // amarelo
            COLOR_BOOLEAN = new Color(0x7B, 0x1F, 0xA2); // roxo (literal TRUE/FALSE no editor)
            COLOR_UUID = new Color(0x3F, 0x51, 0xB5); // azul arroxeado
            COLOR_BINARY = new Color(0x45, 0x5A, 0x64); // cinza azulado

            // ---- Cores com significado ESTRUTURAL (colorFor — grade/etc.) ----
            COLOR_DATE = new Color(0x7E, 0x57, 0xC2); // roxo suave (DATE/DATETIME/TIMESTAMP unificados)
            COLOR_DATETIME = COLOR_DATE;
            COLOR_JSON = new Color(0x00, 0x69, 0x78); // ciano/azul-petroleo discreto
            COLOR_XML = new Color(0x00, 0x79, 0x6B); // verde-agua discreto
            COLOR_NULL = new Color(0x90, 0xA4, 0xAE); // cinza (+ italico) — ja estava bom

            COLOR_DEFAULT_TEXT = new Color(0x1B, 0x1F, 0x23); // mesmo tom neutro de COLOR_OBJECT_NAME

            COLOR_IDENTIFIER = new Color(0xE6, 0x51, 0x00); // excecao mantida, ver javadoc
            COLOR_PRIMARY_KEY = new Color(0xB8, 0x86, 0x0B); // dourado
            COLOR_FOREIGN_KEY = new Color(0xF9, 0xA8, 0x25); // amarelo — distinto do dourado da PK

            COLOR_BOOLEAN_TRUE = new Color(0x15, 0x65, 0xC0); // azul
            COLOR_BOOLEAN_FALSE = new Color(0xEF, 0x6C, 0x00); // laranja (nunca vermelho puro) — tom distinto de COLOR_IDENTIFIER

            COLOR_LOGIC_TRUE = new Color(0x2E, 0x7D, 0x32);
            COLOR_LOGIC_FALSE = new Color(0xC6, 0x28, 0x28);

            // Era 0xB0BEC5 (Blue Grey 200) — cinza escuro demais, cansava a
            // vista em uso prolongado (pedido explicito do usuario). Mesma
            // familia de cor (Blue Grey), so um tom bem mais claro (100), pra
            // continuar dando contraste claro contra zebra/hover sem pesar na
            // leitura.
            SELECTION_BACKGROUND = new Color(0xCF, 0xD8, 0xDC);
            SELECTION_FOREGROUND = new Color(0x26, 0x32, 0x38);
            ACTIVE_CELL_BORDER = new Color(0x37, 0x47, 0x4F);

            // Branco puro pra zebra "par" agora (era 0xFCFDFE): redesenho "novo
            // e leve" (Fase 6) achatou as superficies de conteudo pra branco em
            // toda a IDE (ver FlatLaf.properties) — a zebra ODD e quem carrega
            // o contraste sutil sozinha, um tom bem mais proximo do branco que
            // antes (era 0xF7F8FA) pra nao reintroduzir a "camada de cinza" que
            // este redesenho removeu de todo o resto da UI.
            ZEBRA_EVEN = new Color(0xFF, 0xFF, 0xFF);
            ZEBRA_ODD = new Color(0xFA, 0xFB, 0xFC);
            HOVER_BACKGROUND = new Color(0xF1, 0xF4, 0xF6);

            EDIT_DIRTY_CELL = new Color(0xFE, 0xF3, 0xC7);
            EDIT_NEW_ROW = new Color(0xDC, 0xFC, 0xE7);
            EDIT_DELETED_ROW = new Color(0xFE, 0xE2, 0xE2);

            // Linha de grade quase invisivel (era 0xEDEFF2) — a leitura de
            // "linha" vem mais da zebra do que de um traco solido agora.
            GRID_LINE = new Color(0xF2, 0xF4, 0xF6);

            // Cabecalho com peso TIPOGRAFICO (ver HEADER_FOREGROUND em negrito
            // via Typography), nao mais um bloco de fundo cinza distinto do
            // resto da grade (era 0xF1F3F5) — so uma borda inferior fina
            // (HEADER_BORDER, bem mais clara que antes) separa cabecalho de
            // dados.
            HEADER_BACKGROUND = new Color(0xFA, 0xFB, 0xFC);
            HEADER_FOREGROUND = new Color(0x33, 0x41, 0x55);
            HEADER_BORDER = new Color(0xE4, 0xE7, 0xEB);
            SORT_INDICATOR_ACTIVE = new Color(0x05, 0x96, 0x69);
            SORT_INDICATOR_INACTIVE = new Color(0xB0, 0xB8, 0xC1);
            HEADER_HIGHLIGHT_BACKGROUND = new Color(0xFE, 0xF0, 0x8A);
            HEADER_HIGHLIGHT_BORDER = new Color(0xCA, 0x8A, 0x04);

            GUTTER_BACKGROUND = new Color(0xFA, 0xFB, 0xFC);
            GUTTER_FOREGROUND = new Color(0x9A, 0xA3, 0xAF);

            MUTED_TEXT = new Color(0x6B, 0x72, 0x80);

            ACCENT_INFO = new Color(0x25, 0x63, 0xEB);
            ACCENT_WARNING = new Color(0xB3, 0x86, 0x00);
            ACCENT_ERROR = new Color(0xE5, 0x48, 0x4D);
            ACCENT_SQL = new Color(0x0F, 0x76, 0x6E);
            ACCENT_TOOL = new Color(0x7C, 0x3A, 0xED);
    }

    /**
     * Cor da categoria semantica {@code kind} NA EXIBICAO DE DADOS (grade,
     * autocomplete, tooltip, metadados, visualizador de celula) — ver javadoc
     * da classe para a distincao com o editor SQL (que NAO chama isto).
     * Rodada 2 ("quase monocromatico"): so categorias com significado
     * ESTRUTURAL ganham cor propria aqui (DATE/DATETIME, JSON, XML); todo o
     * resto (numero, texto, enum, uuid, binario, hora) usa
     * {@link #COLOR_DEFAULT_TEXT} — pedido explicito do usuario. BOOLEAN
     * tambem cai em {@link #COLOR_DEFAULT_TEXT} aqui porque a grade NUNCA usa
     * este metodo para colorir uma celula booleana de verdade (isso e por
     * VALOR, nao por tipo — ver {@link BooleanCellRenderer#colorFor}); este
     * caso so e alcancado quando algum componente mostra a palavra "BOOLEAN"
     * como TEXTO (ex.: coluna "Tipo" de uma grade de metadados), onde nao ha
     * valor nenhum para colorir por TRUE/FALSE.
     */
    public static Color colorFor(SqlTypeKind kind) {
        return switch (kind) {
            case DATE, DATETIME -> COLOR_DATE;
            case JSON -> COLOR_JSON;
            case XML -> COLOR_XML;
            case INTEGER, DECIMAL, TEXTUAL, ENUM, UUID, BINARY, TIME, BOOLEAN -> COLOR_DEFAULT_TEXT;
        };
    }

}
