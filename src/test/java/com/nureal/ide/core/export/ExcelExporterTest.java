package com.nureal.ide.core.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Caracteriza {@link ExcelExporter} apos {@link ExcelExporter.TableSheet}
 * passar a depender de {@link TabelaExportavel} em vez de
 * {@code javax.swing.table.TableModel} (ver
 * .specs/09-modulo-backup-exportacao.md, regra 1) — nenhuma mudanca de
 * formato de saida (.xlsx) e esperada, apenas de contrato de entrada.
 */
class ExcelExporterTest {

	@Test
	void exportaCabecalhoEValoresDeUmaTabelaSimples(@TempDir Path dir) throws Exception {
		TabelaExportavel tabela = fakeTable(
				new String[] { "id", "nome" },
				new Object[][] { { 1, "Ana" }, { 2, "Bia" } });
		File file = dir.resolve("saida.xlsx").toFile();

		ExcelExporter.export(List.of(new ExcelExporter.TableSheet("Clientes", tabela)), file);

		try (XSSFWorkbook wb = new XSSFWorkbook(file)) {
			Sheet sheet = wb.getSheet("Clientes");
			Row header = sheet.getRow(0);
			assertEquals("id", header.getCell(0).getStringCellValue());
			assertEquals("nome", header.getCell(1).getStringCellValue());

			Row row1 = sheet.getRow(1);
			assertEquals(1.0, row1.getCell(0).getNumericCellValue());
			assertEquals("Ana", row1.getCell(1).getStringCellValue());

			Row row2 = sheet.getRow(2);
			assertEquals(2.0, row2.getCell(0).getNumericCellValue());
			assertEquals("Bia", row2.getCell(1).getStringCellValue());
		}
	}

	@Test
	void celulaNulaFicaVaziaNaPlanilha(@TempDir Path dir) throws Exception {
		TabelaExportavel tabela = fakeTable(
				new String[] { "id", "nome" },
				new Object[][] { { 1, null } });
		File file = dir.resolve("saida-nula.xlsx").toFile();

		ExcelExporter.export(List.of(new ExcelExporter.TableSheet("T", tabela)), file);

		try (XSSFWorkbook wb = new XSSFWorkbook(file)) {
			Row row = wb.getSheet("T").getRow(1);
			Cell cell = row.getCell(1);
			assertTrue(cell == null || cell.getCellType() == CellType.BLANK,
					"celula deveria ficar vazia (nula ou em branco) para um valor nulo");
		}
	}

	@Test
	void criaUmaAbaVaziaQuandoNaoHaNenhumaTabela(@TempDir Path dir) throws Exception {
		File file = dir.resolve("vazio.xlsx").toFile();

		ExcelExporter.export(List.of(), file);

		try (XSSFWorkbook wb = new XSSFWorkbook(file)) {
			assertEquals(1, wb.getNumberOfSheets());
			assertEquals("Vazio", wb.getSheetName(0));
		}
	}

	private static TabelaExportavel fakeTable(String[] columns, Object[][] rows) {
		return new TabelaExportavel() {
			@Override
			public int linhas() {
				return rows.length;
			}

			@Override
			public int colunas() {
				return columns.length;
			}

			@Override
			public String nomeColuna(int coluna) {
				return columns[coluna];
			}

			@Override
			public Object valor(int linha, int coluna) {
				return rows[linha][coluna];
			}
		};
	}
}
