package com.nureal.ide.ui;

import java.util.Vector;

import javax.swing.table.DefaultTableModel;

import com.nureal.ide.modulos.backupexportacao.dominio.contratos.TabelaExportavel;

/**
 * Modelo de tabela usado pela grade de resultados — somente leitura por
 * padrao, mas pode virar editavel (ver {@link GridEditController} /
 * {@link #setEditController}) quando o resultado vem de um SELECT simples de
 * uma unica tabela com chave primaria conhecida.
 *
 * Alem dos dados propriamente ditos, mantem os metadados de cada coluna
 * disponiveis SEM round-trip extra ao banco — tudo que
 * {@link java.sql.ResultSetMetaData} ja entrega no momento da consulta:
 * tipo Java, tabela/coluna fisica de origem (para casar com chaves
 * estrangeiras do schema), nome do tipo SQL (para o renderer colorir e
 * alinhar por tipo) e agora tambem nullable/precisao/escala/tamanho/auto
 * increment ({@link ColumnJdbcMeta}), usados pelo popup de metadados da
 * coluna e pelo menu "Informacoes da coluna".
 *
 * Metadados que EXIGEM consulta ao schema (chave primaria, chaves
 * estrangeiras, indices, comentario) NAO ficam aqui — ficam em
 * {@link TableMetadataCache} / {@link ColumnMetadataResolver}, carregados
 * sob demanda em segundo plano, pois dependem da tabela fisica de origem
 * (nem sempre conhecida para colunas calculadas/JOINs complexos).
 *
 * Unica classe com este nome no pacote: ela costumava existir tanto aqui
 * quanto como classe aninhada em {@code MainWindow}, com o mesmo nome mas
 * TIPOS DIFERENTES (bug corrigido) — qualquer referencia nao qualificada a
 * "ResultTableModel" dentro de MainWindow.java resolvia para a classe
 * aninhada, entao um {@code instanceof ResultTableModel} feito por outra
 * classe do pacote (ex.: o renderer) nunca era verdadeiro em tempo de
 * execucao. Agora existe uma unica classe, top-level, usada por todos.
 */
public final class ResultTableModel extends DefaultTableModel implements TabelaExportavel {

    private static final long serialVersionUID = 1L;

    private final transient Class<?>[] columnTypes;
    private final transient String[] sourceTables;
    private final transient String[] realColumnNames;
    private final transient String[] sqlTypeNames;
    private final transient ColumnJdbcMeta[] jdbcMeta;

    /**
     * Controlador de edicao (ver {@link GridEditController}), instalado por
     * {@link ResultGrid} logo apos criar o modelo. {@code null} (ou com
     * {@code isEditable() == false}) mantem a grade somente-leitura, como
     * sempre foi — {@link #isCellEditable} delega para ele em vez de um
     * {@code return false} fixo.
     */
    private transient GridEditController editController;

    /**
     * Metadados de coluna disponiveis diretamente pelo JDBC no momento da
     * consulta (sem custo extra): nulabilidade, precisao, escala, tamanho de
     * exibicao e auto-increment. Ver {@link java.sql.ResultSetMetaData}.
     */
    public record ColumnJdbcMeta(boolean nullable, int precision, int scale,
                                  int displaySize, boolean autoIncrement) {
        public static final ColumnJdbcMeta UNKNOWN = new ColumnJdbcMeta(true, 0, 0, 0, false);
    }

    public ResultTableModel(
            Vector<String> columnNames,
            Class<?>[] columnTypes,
            String[] sourceTables,
            String[] realColumnNames,
            String[] sqlTypeNames) {
        this(columnNames, columnTypes, sourceTables, realColumnNames, sqlTypeNames, null);
    }

    public ResultTableModel(
            Vector<String> columnNames,
            Class<?>[] columnTypes,
            String[] sourceTables,
            String[] realColumnNames,
            String[] sqlTypeNames,
            ColumnJdbcMeta[] jdbcMeta) {

        super(columnNames, 0);

        this.columnTypes = columnTypes;
        this.sourceTables = sourceTables;
        this.realColumnNames = realColumnNames;
        this.sqlTypeNames = sqlTypeNames;
        this.jdbcMeta = (jdbcMeta != null) ? jdbcMeta : new ColumnJdbcMeta[columnTypes.length];
    }

    void setEditController(GridEditController editController) {
        this.editController = editController;
    }

    /**
     * Insere varias linhas de uma vez, disparando UM UNICO evento de mudanca
     * ({@code fireTableRowsInserted}) para o lote inteiro, em vez de um
     * evento por linha (o que chamar {@link #addRow(java.util.Vector)} em
     * loop faz por baixo dos panos — cada chamada dispara o proprio evento).
     * Usado pela paginacao/"carregar tudo" de resultados grandes (ver
     * {@code ResultsAreaController#loadPage}/{@code #loadAll}): inserir
     * centenas ou milhares de linhas uma a uma deixava a grade visivelmente
     * travada durante o carregamento, ja que cada evento aciona uma
     * revalidacao/repintura da {@code JTable}. Sem efeito se {@code rows}
     * estiver vazio (nenhum evento disparado a toa).
     */
    @SuppressWarnings("unchecked")
    public void addRows(java.util.List<Vector<Object>> rows) {
        if (rows.isEmpty()) {
            return;
        }
        int firstRow = getRowCount();
        for (Vector<Object> row : rows) {
            dataVector.addElement(row);
        }
        fireTableRowsInserted(firstRow, getRowCount() - 1);
    }

    GridEditController editController() {
        return editController;
    }

    @Override
    public boolean isCellEditable(int row, int column) {
        return editController != null && editController.isCellEditable(row, column);
    }

    @Override
    public Class<?> getColumnClass(int column) {
        if (column >= 0
                && column < columnTypes.length
                && columnTypes[column] != null) {
            return columnTypes[column];
        }

        return Object.class;
    }

    /**
     * Retorna a tabela física de origem da coluna.
     */
    public String sourceTable(int column) {
        if (column < 0 || column >= sourceTables.length) {
            return null;
        }

        return sourceTables[column];
    }

    // ---------- TabelaExportavel (ver modulos.backupexportacao.infraestrutura.ExcelExporter) ----------

    @Override
    public int linhas() {
        return getRowCount();
    }

    @Override
    public int colunas() {
        return getColumnCount();
    }

    @Override
    public String nomeColuna(int coluna) {
        return getColumnName(coluna);
    }

    @Override
    public Object valor(int linha, int coluna) {
        return getValueAt(linha, coluna);
    }

    /**
     * Retorna o nome físico da coluna (sem alias).
     */
    public String realColumnName(int column) {
        if (column < 0 || column >= realColumnNames.length) {
            return null;
        }

        return realColumnNames[column];
    }

    /**
     * Retorna o nome do tipo SQL (VARCHAR, BIGINT, JSON, TIMESTAMP, etc.).
     */
    public String sqlType(int column) {
        if (column < 0
                || column >= sqlTypeNames.length
                || sqlTypeNames[column] == null) {
            return "";
        }

        return sqlTypeNames[column];
    }

    /** Metadados JDBC (nullable/precisao/escala/tamanho/auto-increment) da coluna. */
    public ColumnJdbcMeta jdbcMeta(int column) {
        if (column < 0 || column >= jdbcMeta.length || jdbcMeta[column] == null) {
            return ColumnJdbcMeta.UNKNOWN;
        }
        return jdbcMeta[column];
    }
}
