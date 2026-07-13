package com.nureal.ide.ui;

import com.nureal.ide.core.sql.SqlTypeKind;

import java.time.temporal.Temporal;
import java.util.Locale;

import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;

/**
 * Escolhe e instala o {@link TableCellRenderer} de cada coluna da grade de
 * resultados, classificando-a por {@link SqlTypeKind} (o NOME DO TIPO SQL
 * real — {@link ResultTableModel#sqlType(int)} — com fallback pela classe
 * Java quando o tipo nao e conhecido), mais uma heuristica de NOME so para
 * identificadores (id/*_id/uuid).
 *
 * Esta e a UNICA classe que decide "qual grupo visual uma coluna pertence" —
 * ver o "Sistema Semantico de Cores por Tipo de Dado" em DESIGN_SYSTEM.md: a
 * cor de cada {@link Group} vem de {@link GridTheme#colorFor(SqlTypeKind)}
 * (ou, para {@link Group#IDENTIFIER}, de {@link GridTheme#COLOR_IDENTIFIER} —
 * a UNICA excecao deliberada ao sistema, mantida a pedido do usuario).
 *
 * IMPORTANTE: o grupo e decidido SEMPRE pelo TIPO DE DADO real da coluna
 * (tipo SQL ou, na falta dele, a classe Java) — NUNCA pela APARENCIA do
 * valor (ex.: uma coluna VARCHAR contendo so digitos continua TEXTUAL; um
 * INT com valores 0/1 continua INTEGER, nao BOOLEAN). A UNICA excecao onde o
 * NOME de uma coluna entra na conta e a heuristica de identificador
 * ("id"/"*_id"/uuid, ver {@link #isGenericIdName}/{@link #isUuidName}) —
 * mantida deliberadamente (ver javadoc de {@link GridTheme#COLOR_IDENTIFIER}).
 *
 * A classificacao acontece UMA VEZ POR COLUNA, quando a grade e montada
 * ({@link #installOn}) — nao a cada celula pintada — e os renderers em si
 * sao instancias UNICAS e sem estado (compartilhadas por todas as colunas do
 * mesmo grupo, em todas as grades da aplicacao), nunca recriadas.
 */
final class RendererFactory {

    /**
     * Chave de client property (ver {@code JComponent#putClientProperty}) sob
     * a qual {@link ResultGrid} guarda o {@link ColumnMetadataResolver} desta
     * tabela especifica — {@link AbstractTypedCellRenderer} le esta property
     * a cada celula pintada pra saber se a coluna e chave primaria/estrangeira
     * de verdade (metadados do banco), sem precisar que os renderers
     * (instancias UNICAS e compartilhadas entre todas as grades) guardem
     * nenhum estado por tabela.
     */
    static final String KEY_METADATA_RESOLVER = "nureal.columnMetadataResolver";

    // Rodada 2 do "Sistema Semantico de Cores": todos os supliers abaixo
    // (exceto IDENTIFIER, excecao deliberada) chamam GridTheme.colorFor(kind)
    // — a MESMA fonte usada por CellContentViewer/ColumnMetadataPopup/etc —
    // em vez de ler campos como GridTheme.COLOR_INTEGER/COLOR_TEXTUAL
    // diretamente. ANTES desta correcao, cada supplier lia um campo
    // "editor-only" hardcoded (ex.: () -> GridTheme.COLOR_INTEGER): esses
    // campos continuam existindo, mas so pro SqlEditorPane (syntax
    // highlight) — mudar colorFor() sozinho NUNCA mudava a cor da grade,
    // porque estes renderers simplesmente nao passavam por ele. Bug relatado
    // pelo usuario ("os numericos continuam azuis e as string verdes"): a
    // Rodada 2 tinha mudado colorFor() mas esquecido de reconectar estes 7
    // supliers a ele.
    private static final IdentifierCellRenderer IDENTIFIER = new IdentifierCellRenderer();
    private static final NumberCellRenderer INTEGER_RENDERER =
            new NumberCellRenderer(() -> GridTheme.colorFor(SqlTypeKind.INTEGER));
    private static final NumberCellRenderer DECIMAL_RENDERER =
            new NumberCellRenderer(() -> GridTheme.colorFor(SqlTypeKind.DECIMAL));
    private static final TemporalCellRenderer DATE_RENDERER =
            new TemporalCellRenderer(() -> GridTheme.colorFor(SqlTypeKind.DATE), TemporalCellRenderer.Kind.DATE);
    private static final TemporalCellRenderer TIME_RENDERER =
            new TemporalCellRenderer(() -> GridTheme.colorFor(SqlTypeKind.TIME), TemporalCellRenderer.Kind.TIME);
    private static final TemporalCellRenderer DATETIME_RENDERER = new TemporalCellRenderer(
            () -> GridTheme.colorFor(SqlTypeKind.DATETIME), TemporalCellRenderer.Kind.DATETIME);
    private static final BooleanCellRenderer BOOLEAN_RENDERER = new BooleanCellRenderer();
    private static final PlainTypedCellRenderer UUID_RENDERER =
            new PlainTypedCellRenderer(() -> GridTheme.colorFor(SqlTypeKind.UUID));
    private static final PlainTypedCellRenderer JSON_RENDERER =
            new PlainTypedCellRenderer(() -> GridTheme.colorFor(SqlTypeKind.JSON));
    private static final PlainTypedCellRenderer XML_RENDERER =
            new PlainTypedCellRenderer(() -> GridTheme.colorFor(SqlTypeKind.XML));
    private static final BinaryCellRenderer BINARY_RENDERER = new BinaryCellRenderer();
    private static final PlainTypedCellRenderer ENUM_RENDERER =
            new PlainTypedCellRenderer(() -> GridTheme.colorFor(SqlTypeKind.ENUM));
    private static final PlainTypedCellRenderer TEXTUAL_RENDERER =
            new PlainTypedCellRenderer(() -> GridTheme.colorFor(SqlTypeKind.TEXTUAL));

    private RendererFactory() {
    }

    enum Group { IDENTIFIER, INTEGER, DECIMAL, DATE, TIME, DATETIME, BOOLEAN, UUID, JSON, XML, BINARY, ENUM, TEXTUAL }

    /** Classifica e instala o renderer (e, para colunas temporais, o editor) de cada coluna de {@code table}. */
    static void installOn(javax.swing.JTable table, ResultTableModel model) {
        for (int c = 0; c < table.getColumnModel().getColumnCount(); c++) {
            TableColumn column = table.getColumnModel().getColumn(c);
            int modelColumn = column.getModelIndex();
            Group group = classify(model.sqlType(modelColumn), model.getColumnClass(modelColumn),
                    model.getColumnName(modelColumn));
            column.setCellRenderer(rendererFor(group));
            if (group == Group.DATE || group == Group.TIME || group == Group.DATETIME) {
                // Precisa de um editor PROPRIO (ver TemporalCellEditor): o
                // editor generico padrao do JTable mostra/espera um formato
                // diferente do que a grade exibe e nao consegue reconstruir
                // tipos java.time.* (LocalDate/LocalDateTime nao tem
                // construtor(String)) — edicao de data falhava em silencio.
                // Instancia NOVA por coluna: guarda estado por edicao em
                // andamento (classe alvo, valor parseado), nao pode ser
                // compartilhada entre colunas/grades.
                column.setCellEditor(new TemporalCellEditor());
            }
        }
    }

    static TableCellRenderer rendererFor(Group group) {
        return switch (group) {
            case IDENTIFIER -> IDENTIFIER;
            case INTEGER -> INTEGER_RENDERER;
            case DECIMAL -> DECIMAL_RENDERER;
            case DATE -> DATE_RENDERER;
            case TIME -> TIME_RENDERER;
            case DATETIME -> DATETIME_RENDERER;
            case BOOLEAN -> BOOLEAN_RENDERER;
            case UUID -> UUID_RENDERER;
            case JSON -> JSON_RENDERER;
            case XML -> XML_RENDERER;
            case BINARY -> BINARY_RENDERER;
            case ENUM -> ENUM_RENDERER;
            case TEXTUAL -> TEXTUAL_RENDERER;
        };
    }

    /**
     * Classifica a coluna em um dos grupos visuais. Prioridade: tipos
     * inequivocos pelo NOME DO TIPO SQL primeiro (enum/data/hora/data-hora/
     * json/xml/binario/boolean); depois o nome da coluna para identificadores
     * (excecao deliberada, ver javadoc da classe); por fim inteiro/decimal
     * pelo tipo SQL, com fallback pela classe Java quando o tipo SQL nao
     * estiver disponivel.
     */
    static Group classify(String sqlType, Class<?> cls, String columnName) {
        String name = columnName == null ? "" : columnName.toLowerCase(Locale.ROOT);
        boolean typeKnown = sqlType != null && !sqlType.isBlank();
        SqlTypeKind kind = SqlTypeKind.classify(sqlType);

        if (typeKnown) {
            switch (kind) {
                case ENUM -> {
                    return Group.ENUM;
                }
                case DATE -> {
                    return Group.DATE;
                }
                case TIME -> {
                    return Group.TIME;
                }
                case DATETIME -> {
                    return Group.DATETIME;
                }
                case JSON -> {
                    return Group.JSON;
                }
                case XML -> {
                    return Group.XML;
                }
                case BINARY -> {
                    return Group.BINARY;
                }
                case BOOLEAN -> {
                    return Group.BOOLEAN;
                }
                default -> {
                    // segue abaixo (identificador/inteiro/decimal/textual)
                }
            }
        }

        boolean integerType = typeKnown && kind == SqlTypeKind.INTEGER;
        boolean decimalType = typeKnown && kind == SqlTypeKind.DECIMAL;
        boolean numericClass = Number.class.isAssignableFrom(cls);

        // "uuid"/"guid" no nome: sinal inequivoco de identificador opaco
        // independente do tipo fisico de armazenamento (muitos bancos
        // guardam UUID como CHAR/VARCHAR mesmo) — EXCECAO deliberada mantida
        // a pedido do usuario (ver GridTheme#COLOR_IDENTIFIER): prioridade
        // MAIOR que o tipo real UUID abaixo.
        if (isUuidName(name)) {
            return Group.IDENTIFIER;
        }
        // Tipo real UUID (ex.: Postgres) mas nome que NAO parece identificador
        // (ja descartado acima): agora sim usa a cor do TIPO.
        if (typeKnown && kind == SqlTypeKind.UUID) {
            return Group.UUID;
        }
        // "id"/"*_id" no nome: SO conta como identificador de verdade quando
        // o valor tambem E numerico (autoincrement/chave de verdade). Uma
        // coluna "*_id" armazenada como TEXTO PURO (VARCHAR/CHAR/TEXT) e sem
        // cara de UUID e, na pratica, quase sempre um identificador de
        // NEGOCIO/externo (ex.: client_order_id copiado de outro sistema) —
        // nao uma chave desta tabela — entao cai no fluxo normal abaixo e
        // termina classificada como TEXTUAL, igual qualquer outra coluna de
        // texto. Pedido explicito do usuario: uma "*_id" que e texto e nem
        // faz parte da chave da tabela deve ser tratada como texto normal,
        // nao ganhar o destaque/alinhamento de identificador so pelo nome.
        if (isGenericIdName(name) && (integerType || decimalType || numericClass)) {
            return Group.IDENTIFIER;
        }
        if (integerType) {
            return Group.INTEGER;
        }
        if (decimalType) {
            return Group.DECIMAL;
        }
        // Fallback pela classe Java quando o nome do tipo SQL nao ajudou.
        if (numericClass) {
            return isIntegralClass(cls) ? Group.INTEGER : Group.DECIMAL;
        }
        if (isTemporalClass(cls)) {
            if (cls == java.sql.Time.class || cls == java.time.LocalTime.class) {
                return Group.TIME;
            }
            if (cls == java.sql.Date.class || cls == java.time.LocalDate.class) {
                return Group.DATE;
            }
            return Group.DATETIME;
        }
        if (cls == Boolean.class) {
            return Group.BOOLEAN;
        }
        return Group.TEXTUAL;
    }

    private static boolean isIntegralClass(Class<?> cls) {
        return cls == Integer.class || cls == Long.class || cls == Short.class || cls == Byte.class
                || java.math.BigInteger.class.isAssignableFrom(cls);
    }

    private static boolean isGenericIdName(String lowerCaseName) {
        return lowerCaseName.equals("id") || lowerCaseName.endsWith("_id");
    }

    private static boolean isUuidName(String lowerCaseName) {
        return lowerCaseName.contains("uuid") || lowerCaseName.contains("guid");
    }

    private static boolean isTemporalClass(Class<?> cls) {
        return java.util.Date.class.isAssignableFrom(cls) || Temporal.class.isAssignableFrom(cls);
    }
}
