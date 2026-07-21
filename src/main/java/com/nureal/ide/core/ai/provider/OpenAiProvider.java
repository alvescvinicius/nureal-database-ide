package com.nureal.ide.core.ai.provider;

import java.time.Duration;

/** OpenAI — Chat Completions API (ver {@link OpenAiCompatibleProvider}). */
public final class OpenAiProvider extends OpenAiCompatibleProvider {

    private static final String BASE_URL = "https://api.openai.com/v1";

    public OpenAiProvider(String apiKey, Duration timeout) {
        super(apiKey, BASE_URL, timeout);
    }

    @Override
    protected String providerName() {
        return "OpenAI";
    }
}
