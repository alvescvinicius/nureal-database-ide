package com.nureal.ide.modulos.backupexportacao.dominio.contratos;
import com.nureal.ide.modulos.backupexportacao.infraestrutura.ExcelExporter;

/**
 * Fonte tabular minima que {@link ExcelExporter} sabe exportar — linhas,
 * colunas, nome de cada coluna e valor de cada celula. Extraida para
 * desacoplar {@code modulos.backupexportacao.infraestrutura} de {@code javax.swing.table.TableModel}
 * (ver .specs/09-modulo-backup-exportacao.md, regra 1): qualquer fonte
 * tabular pode implementar esta interface, nao apenas um modelo de tabela
 * Swing.
 */
public interface TabelaExportavel {

    int linhas();

    int colunas();

    String nomeColuna(int coluna);

    Object valor(int linha, int coluna);
}
