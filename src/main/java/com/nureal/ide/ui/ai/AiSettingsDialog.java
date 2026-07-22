package com.nureal.ide.ui.ai;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Window;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.util.List;
import java.util.Vector;

import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingWorker;

import com.nureal.ide.core.ai.config.AiCredentialsStore;
import com.nureal.ide.core.ai.config.AiPreferences;
import com.nureal.ide.core.ai.provider.ClaudeProvider;
import com.nureal.ide.core.ai.provider.GeminiProvider;
import com.nureal.ide.core.ai.provider.LLMProvider;
import com.nureal.ide.core.ai.provider.OllamaProvider;
import com.nureal.ide.core.ai.provider.OpenAiProvider;
import com.nureal.ide.core.ai.provider.OpenRouterProvider;
import com.nureal.ide.core.ai.provider.ProviderType;
import com.nureal.ide.core.log.AppLogger;

/**
 * Configuracao de IA (provider, credenciais, modelo, temperatura, timeout,
 * streaming) — mesmo padrao "painel + JOptionPane.showConfirmDialog" ja
 * usado por outros ajustes simples do {@code MainWindow} (ex.:
 * {@code chooseEditorFont}), em vez de uma janela dedicada, ja que e um
 * formulario pequeno e de uso ocasional.
 */
public final class AiSettingsDialog {

    private static final String CARD_OLLAMA = "ollama";
    private static final String CARD_CLOUD = "cloud";

    private AiSettingsDialog() {
    }

    public static void open(Window owner, AiPreferences preferences, AiCredentialsStore credentials,
            Runnable onSaved) {
        AiPreferences.State current;
        try {
            current = preferences.load();
        } catch (IOException e) {
            AppLogger.warning("Falha ao carregar configuracao de IA", e);
            current = AiPreferences.State.defaults();
        }

        JComboBox<ProviderType> providerCombo = new JComboBox<>(ProviderType.values());
        providerCombo.setSelectedItem(current.provider());
        providerCombo.setRenderer(new DefaultListCellRenderer() {
            private static final long serialVersionUID = 1L;

            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                    boolean isSelected, boolean cellHasFocus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected,
                        cellHasFocus);
                if (value instanceof ProviderType type) {
                    label.setText(type.displayName());
                }
                return label;
            }
        });

        JTextField baseUrlField = new JTextField(current.baseUrl());
        JPasswordField apiKeyField = new JPasswordField();
        loadApiKeyIntoField(credentials, current.provider(), apiKeyField);

        CardLayout cards = new CardLayout();
        JPanel credentialsCards = new JPanel(cards);
        JPanel ollamaCard = new JPanel(new BorderLayout());
        ollamaCard.add(baseUrlField, BorderLayout.CENTER);
        JPanel cloudCard = new JPanel(new BorderLayout());
        cloudCard.add(apiKeyField, BorderLayout.CENTER);
        credentialsCards.add(ollamaCard, CARD_OLLAMA);
        credentialsCards.add(cloudCard, CARD_CLOUD);

        JLabel credentialsLabel = new JLabel();

        JComboBox<String> modelCombo = new JComboBox<>();
        modelCombo.setEditable(true);
        if (!current.model().isBlank()) {
            modelCombo.addItem(current.model());
            modelCombo.setSelectedItem(current.model());
        }
        JButton refreshButton = new JButton("Listar modelos");
        JLabel modelsStatus = new JLabel(" ");

        wireProviderChange(providerCombo, cards, credentialsCards, credentialsLabel, apiKeyField, modelCombo,
                modelsStatus, credentials);
        wireRefreshModels(refreshButton, providerCombo, baseUrlField, apiKeyField, modelCombo, modelsStatus);

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
        form.add(new JLabel("Provider:"));
        form.add(providerCombo);
        form.add(credentialsLabel);
        form.add(credentialsCards);
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
        panel.setPreferredSize(new Dimension(440, 400));

        int result = JOptionPane.showConfirmDialog(owner, panel, "Configuracao de IA",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) {
            return;
        }
        saveState(owner, preferences, credentials, onSaved, providerCombo, baseUrlField, modelCombo,
                temperatureSpinner, timeoutSpinner, streamingCheckbox, apiKeyField);
    }

    /** "Provider" muda -> troca o card de credenciais (API key vs Base URL) e limpa a lista de modelos ja consultada. */
    private static void wireProviderChange(JComboBox<ProviderType> providerCombo, CardLayout cards,
            JPanel credentialsCards, JLabel credentialsLabel, JPasswordField apiKeyField,
            JComboBox<String> modelCombo, JLabel modelsStatus, AiCredentialsStore credentials) {
        Runnable updateCardForProvider = () -> {
            ProviderType selected = (ProviderType) providerCombo.getSelectedItem();
            if (selected.requiresApiKey()) {
                cards.show(credentialsCards, CARD_CLOUD);
                credentialsLabel.setText("API Key de " + selected.displayName() + ":");
            } else {
                cards.show(credentialsCards, CARD_OLLAMA);
                credentialsLabel.setText("Base URL do Ollama:");
            }
        };
        updateCardForProvider.run();
        providerCombo.addActionListener(e -> {
            updateCardForProvider.run();
            ProviderType selected = (ProviderType) providerCombo.getSelectedItem();
            loadApiKeyIntoField(credentials, selected, apiKeyField);
            modelCombo.setModel(new DefaultComboBoxModel<>());
            modelsStatus.setText(" ");
        });
    }

    /** Botao "Listar modelos": consulta o provider com os valores AINDA NAO salvos do formulario. */
    private static void wireRefreshModels(JButton refreshButton, JComboBox<ProviderType> providerCombo,
            JTextField baseUrlField, JPasswordField apiKeyField, JComboBox<String> modelCombo,
            JLabel modelsStatus) {
        refreshButton.addActionListener(e -> {
            refreshButton.setEnabled(false);
            ProviderType selected = (ProviderType) providerCombo.getSelectedItem();
            modelsStatus.setText("Consultando " + selected.displayName() + "...");
            String baseUrl = baseUrlField.getText().isBlank() ? AiPreferences.DEFAULT_BASE_URL
                    : baseUrlField.getText().strip();
            String apiKey = new String(apiKeyField.getPassword()).strip();
            new SwingWorker<List<String>, Void>() {
                @Override
                protected List<String> doInBackground() {
                    return buildPreviewProvider(selected, baseUrl, apiKey).listModels();
                }

                @Override
                protected void done() {
                    refreshButton.setEnabled(true);
                    try {
                        List<String> models = get();
                        Object selectedModel = modelCombo.getSelectedItem();
                        modelCombo.setModel(new DefaultComboBoxModel<>(new Vector<>(models)));
                        if (selectedModel != null && models.contains(selectedModel)) {
                            modelCombo.setSelectedItem(selectedModel);
                        }
                        modelsStatus.setText(models.isEmpty() ? "Nenhum modelo disponivel."
                                : models.size() + " modelo(s) encontrado(s).");
                    } catch (Exception ex) {
                        modelsStatus.setText("Falha ao consultar " + selected.displayName() + ": " + rootMessage(ex));
                    }
                }
            }.execute();
        });
    }

    /** Le os campos do formulario ja confirmado, monta o {@link AiPreferences.State} e salva (preferencias + API key). */
    private static void saveState(Window owner, AiPreferences preferences, AiCredentialsStore credentials,
            Runnable onSaved, JComboBox<ProviderType> providerCombo, JTextField baseUrlField,
            JComboBox<String> modelCombo, JSpinner temperatureSpinner, JSpinner timeoutSpinner,
            JCheckBox streamingCheckbox, JPasswordField apiKeyField) {
        ProviderType selectedProvider = (ProviderType) providerCombo.getSelectedItem();
        String model = modelCombo.getEditor().getItem() == null ? "" : modelCombo.getEditor().getItem().toString().strip();
        AiPreferences.State newState = new AiPreferences.State(
                selectedProvider,
                baseUrlField.getText().isBlank() ? AiPreferences.DEFAULT_BASE_URL : baseUrlField.getText().strip(),
                model,
                (double) temperatureSpinner.getValue(),
                (int) timeoutSpinner.getValue(),
                streamingCheckbox.isSelected());
        try {
            preferences.save(newState);
            if (selectedProvider.requiresApiKey()) {
                credentials.saveApiKey(selectedProvider, new String(apiKeyField.getPassword()).strip());
            }
            onSaved.run();
        } catch (IOException | GeneralSecurityException e) {
            AppLogger.warning("Falha ao salvar configuracao de IA", e);
            JOptionPane.showMessageDialog(owner, "Nao foi possivel salvar a configuracao de IA:\n" + e.getMessage(),
                    "Configuracao de IA", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static void loadApiKeyIntoField(AiCredentialsStore credentials, ProviderType provider,
            JPasswordField field) {
        if (!provider.requiresApiKey()) {
            field.setText("");
            return;
        }
        try {
            field.setText(credentials.loadApiKey(provider).orElse(""));
        } catch (GeneralSecurityException | IOException e) {
            AppLogger.warning("Falha ao carregar API key de " + provider.displayName(), e);
            field.setText("");
        }
    }

    /** Provider "de teste" com os valores AINDA NAO salvos do formulario — usado so pelo botao "Listar modelos". */
    private static LLMProvider buildPreviewProvider(ProviderType type, String baseUrl, String apiKey) {
        Duration timeout = Duration.ofSeconds(15);
        return switch (type) {
            case OLLAMA -> new OllamaProvider(baseUrl, timeout);
            case CLAUDE -> new ClaudeProvider(apiKey, timeout);
            case OPENAI -> new OpenAiProvider(apiKey, timeout);
            case GEMINI -> new GeminiProvider(apiKey, timeout);
            case OPENROUTER -> new OpenRouterProvider(apiKey, timeout);
        };
    }

    /**
     * {@code get()} do SwingWorker embrulha em {@code ExecutionException} o
     * que {@code doInBackground()} lancou — aqui e sempre um
     * {@code ProviderException}, que ja tem mensagem amigavel pronta. So
     * desembrulha UM nivel: nao continuar descendo a cadeia de causas,
     * senao a mensagem amigavel do ProviderException e ignorada em favor
     * da causa tecnica raiz dele (ex.: "ClosedChannelException" sem texto
     * nenhum).
     */
    private static String rootMessage(Throwable t) {
        Throwable cause = (t instanceof java.util.concurrent.ExecutionException && t.getCause() != null)
                ? t.getCause() : t;
        String msg = cause.getMessage();
        return msg == null ? cause.getClass().getSimpleName() : msg;
    }
}
