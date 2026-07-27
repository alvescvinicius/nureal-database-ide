package com.nureal.ide.ui;
import com.nureal.ide.compartilhado.designsystem.Buttons;
import com.nureal.ide.compartilhado.designsystem.Typography;

import com.nureal.ide.modulos.metadados.dominio.entidades.SchemaForeignKey;
import com.nureal.ide.modulos.metadados.dominio.entidades.TableInfo;
import com.nureal.ide.compartilhado.designsystem.NSearchField;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Window;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Janela do Diagrama ER (entidade-relacionamento) de um schema inteiro: uma
 * caixa por tabela (nome + colunas, PK/FK destacadas) e uma linha por chave
 * estrangeira ligando a coluna de origem a referenciada — o recurso "mais
 * citado como faltando" em qualquer comparacao com Workbench/DBeaver (ver
 * {@code GAP_ANALYSIS_DBA_DEV.md}, fase 3). Diferente do
 * {@link FkInspectorWindow} (uma FK de cada vez, dados da linha referenciada),
 * este mostra a ESTRUTURA das relacoes do schema todo, sem executar nenhuma
 * consulta nos dados das tabelas.
 *
 * NAO-MODAL (mesma familia de {@link ProcessListDialog}/{@link ServerStatusDialog}):
 * um diagrama de referencia so e util se o usuario puder continuar
 * trabalhando (escrevendo SQL, navegando a arvore) enquanto o consulta.
 *
 * Todo o desenho/interacao (arrastar caixa, pan, zoom) mora em
 * {@link ErDiagramCanvas} — esta classe so monta a moldura (JDialog +
 * barra de ferramentas: zoom, ajustar, reorganizar, buscar, exportar PNG).
 */
final class ErDiagramWindow {

    private ErDiagramWindow() {
    }

    static void open(Component parent, String schemaName, List<TableInfo> tables,
            List<SchemaForeignKey> foreignKeys, Map<String, Set<String>> primaryKeysByTable) {
        new Session(parent, schemaName, tables, foreignKeys, primaryKeysByTable).show();
    }

    private static final class Session {
        private final Window owner;
        private final String schemaName;
        private final ErDiagramCanvas canvas;
        private JDialog dialog;
        private JLabel zoomLabel;

        Session(Component parent, String schemaName, List<TableInfo> tables,
                List<SchemaForeignKey> foreignKeys, Map<String, Set<String>> primaryKeysByTable) {
            this.owner = (DialogUtil.owner(parent) instanceof Window w) ? w : null;
            this.schemaName = schemaName;
            this.canvas = new ErDiagramCanvas(tables, foreignKeys, primaryKeysByTable);
        }

        void show() {
            dialog = new JDialog(owner, "Diagrama ER — " + schemaName, JDialog.ModalityType.MODELESS);
            dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
            dialog.setLayout(new BorderLayout());
            dialog.add(buildToolbar(), BorderLayout.NORTH);
            dialog.add(canvas, BorderLayout.CENTER);
            canvas.onScaleChange(scale -> zoomLabel.setText(Math.round(scale * 100) + "%"));
            dialog.setSize(1100, 720);
            dialog.setLocationRelativeTo(owner);
            dialog.setVisible(true);
        }

        private JComponent buildToolbar() {
            NSearchField search = new NSearchField("Buscar tabela...");
            search.setColumns(16);
            search.onTextChange(() -> canvas.highlight(search.getText()));

            JLabel count = new JLabel("  " + canvas.tableCount() + " tabela(s), "
                    + canvas.relationshipCount() + " relacionamento(s)");
            Typography.tertiary(count);

            JButton zoomOut = new JButton("−");
            JButton zoomIn = new JButton("+");
            JButton fit = new JButton("Ajustar a janela");
            JButton reorganize = new JButton("Reorganizar");
            JButton exportPng = new JButton("Exportar PNG...");
            zoomLabel = new JLabel("100%");
            Typography.tertiary(zoomLabel);
            for (JButton b : new JButton[] { zoomOut, zoomIn, fit, reorganize, exportPng }) {
                Buttons.styleSecondary(b);
            }
            zoomOut.addActionListener(a -> canvas.zoomBy(1 / 1.2));
            zoomIn.addActionListener(a -> canvas.zoomBy(1.2));
            fit.addActionListener(a -> canvas.fitToView());
            reorganize.addActionListener(a -> {
                canvas.autoLayout();
                canvas.fitToView();
            });
            exportPng.addActionListener(a -> exportPng());

            JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 6));
            left.add(search);
            left.add(count);
            JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 6));
            right.add(zoomOut);
            right.add(zoomLabel);
            right.add(zoomIn);
            right.add(fit);
            right.add(reorganize);
            right.add(exportPng);

            JPanel bar = new JPanel(new BorderLayout());
            bar.add(left, BorderLayout.WEST);
            bar.add(right, BorderLayout.EAST);
            bar.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 4));
            return bar;
        }

        /**
         * Exporta o diagrama INTEIRO (nao so o que esta recortado na janela
         * agora) como PNG — mesma paridade de descoberta ja aplicada ao
         * "Exportar CSV" do botao principal da grade (ver
         * {@code MainWindow#exportResultCsv}, fase 3 do GAP_ANALYSIS).
         */
        private void exportPng() {
            JFileChooser fc = new JFileChooser();
            fc.setDialogTitle("Exportar diagrama como PNG");
            fc.setSelectedFile(new File("diagrama-" + schemaName + ".png"));
            fc.setFileFilter(new FileNameExtensionFilter("Imagem PNG (*.png)", "png"));
            if (fc.showSaveDialog(dialog) != JFileChooser.APPROVE_OPTION) {
                return;
            }
            File file = fc.getSelectedFile();
            if (!file.getName().toLowerCase(Locale.ROOT).endsWith(".png")) {
                file = new File(file.getParentFile(), file.getName() + ".png");
            }
            try {
                ImageIO.write(canvas.renderFullImage(), "png", file);
                JOptionPane.showMessageDialog(dialog, "Diagrama exportado para \"" + file.getName() + "\".",
                        "Exportar PNG", JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(dialog, "Falha ao exportar PNG:\n" + ex.getMessage(),
                        "Exportar PNG", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
}
