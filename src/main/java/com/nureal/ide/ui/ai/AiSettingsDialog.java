package com.nureal.ide.ui.ai;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Window;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Vector;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingWorker;

import com.nureal.ide.core.ai.config.AiPreferences;
import com.nureal.ide.core.ai.provider.OllamaProvider;
import com.nureal.ide.core.log.AppLogger;

/**
 * Configuracao de IA (base URL do Ollama, modelo, temperatura, timeout,
 * streaming) — mesmo padrao "painel + JOptionPane.showConfirmDialog" ja
 * usado por outros ajustes simples do {@code MainWindow} (ex.:
 * {@code chooseEditorFont}), em vez de uma janela dedicada, ja que e um
 * formulario pequeno e de uso ocasional.
 */
public final class AiSettingsDialog {

    private AiSettingsDialog() {
    }

    public static void open(Window owner, AiPreferences preferences, Runnable onSaved) {
        AiPreferences.State current;
        try {
            current = preferences.load();
        } catch (IOException e) {
            AppLogger.warning("Falha ao carregar configuracao de IA", e);
            current = AiPreferences.State.defaults();
        }

        JCheckBox embeddedCheckbox = new JCheckBox("Usar o Ollama embutido da IDE (recomendado)");
        embeddedCheckbox.setSelected(current.embeddedMode());

        JTextField baseUrlField = new JTextField(current.embeddedMode() ? AiPreferences.DEFAULT_BASE_URL : current.baseUrl());
        baseUrlField.setEnabled(!current.embeddedMode());
        embeddedCheckbox.addActionListener(e -> {
            boolean embedded = embeddedCheckbox.isSelected();
            baseUrlField.setEnabled(!embedded);
            if (embedded) {
                baseUrlField.setText(AiPreferences.DEFAULT_BASE_URL);
            }
        });

        JComboBox<String> modelCombo = new JComboBox<>();
        modelCombo.setEditable(true);
        if (!current.model().isBlank()) {
            modelCombo.addItem(current.model());
            modelCombo.setSelectedItem(current.model());
        }
        JButton refreshButton = new JButton("Listar modelos");
        JLabel modelsStatus = new JLabel(" ");

        refreshButton.addActionListener(e -> {
            refreshButton.setEnabled(false);
            modelsStatus.setText("Consultando o Ollama...");
            String baseUrl = baseUrlField.getText().isBlank() ? AiPreferences.DEFAULT_BASE_URL
                    : baseUrlField.getText().strip();
            new SwingWorker<List<String>, Void>() {
                @Override
                protected List<String> doInBackground() {
                    return new OllamaProvider(baseUrl, Duration.ofSeconds(10)).listModels();
                }

                @Override
                protected void done() {
                    refreshButton.setEnabled(true);
                    try {
                        List<String> models = get();
                        Object selected = modelCombo.getSelectedItem();
                        modelCombo.setModel(new javax.swing.DefaultComboBoxModel<>(new Vector<>(models)));
                        if (selected != null && models.contains(selected)) {
                            modelCombo.setSelectedItem(selected);
                        }
                        modelsStatus.setText(models.isEmpty() ? "Nenhum modelo instalado (\"ollama pull <modelo>\")"
                                : models.size() + " modelo(s) encontrado(s).");
                    } catch (Exception ex) {
                        modelsStatus.setText("Falha ao consultar o Ollama: " + rootMessage(ex));
                    }
                }
            }.execute();
        });

        JSpinner temperatureSpinner = new JSpinner(
                new SpinnerNumberModel(current.temperature(), 0.0, 2.0, 0.1));
        JSpinner timeoutSpinner = new JSpinner(
                new SpinnerNumberModel(current.timeoutSeconds(), 5, 600, 5));
        JCheckBox streamingCheckbox = new JCheckBox("Streaming (mostrar a resposta enquanto e gerada)");
        streamingCheckbox.setSelected(current.streamingEnabled());

        JPanel modelRow = new JPanel(new BorderLayout(6, 0));
        modelRow.add(modelCombo, BorderLayout.CENTER);
        modelRow.add(refreshButton, BorderLayout.EAST);

        JPanel form = new JPanel(new GridLayout(0, 1, 4, 4));
        form.add(embeddedCheckbox);
        form.add(new JLabel("Base URL do Ollama (só usada com o embutido desligado):"));
        form.add(baseUrlField);
        form.add(new JLabel("Modelo:"));
        form.add(modelRow);
        form.add(modelsStatus);
        form.add(new JLabel("Temperatura (0 = mais previsivel, 2 = mais criativo):"));
        form.add(temperatureSpinner);
        form.add(new JLabel("Timeout (segundos):"));
        form.add(timeoutSpinner);
        form.add(streamingCheckbox);

        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        panel.add(form, BorderLayout.CENTER);
        panel.setPreferredSize(new Dimension(440, 350));

        int result = JOptionPane.showConfirmDialog(owner, panel, "Configuracao de IA (Ollama)",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) {
            return;
        }

        String model = modelCombo.getEditor().getItem() == null ? "" : modelCombo.getEditor().getItem().toString().strip();
        AiPreferences.State newState = new AiPreferences.State(
                baseUrlField.getText().isBlank() ? AiPreferences.DEFAULT_BASE_URL : baseUrlField.getText().strip(),
                model,
                (double) temperatureSpinner.getValue(),
                (int) timeoutSpinner.getValue(),
                streamingCheckbox.isSelected(),
                embeddedCheckbox.isSelected());
        try {
            preferences.save(newState);
            onSaved.run();
        } catch (IOException e) {
            AppLogger.warning("Falha ao salvar configuracao de IA", e);
            JOptionPane.showMessageDialog(owner, "Nao foi possivel salvar a configuracao de IA:\n" + e.getMessage(),
                    "Configuracao de IA", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * {@code get()} do SwingWorker embrulha em {@code ExecutionException} o
     * que {@code doInBackground()} lancou — aqui e sempre um
     * {@code ProviderException}, que ja tem mensagem amigavel pronta (ver
     * {@code OllamaProvider}). So desembrulha UM nivel: nao continuar
     * descendo a cadeia de causas, senao a mensagem amigavel do
     * ProviderException e ignorada em favor da causa tecnica raiz dele
     * (ex.: "ClosedChannelException" sem texto nenhum).
     */
    private static String rootMessage(Throwable t) {
        Throwable cause = (t instanceof java.util.concurrent.ExecutionException && t.getCause() != null)
                ? t.getCause() : t;
        String msg = cause.getMessage();
        return msg == null ? cause.getClass().getSimpleName() : msg;
    }
}
