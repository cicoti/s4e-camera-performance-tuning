package com.s4etech.performance.v2.llm;

import java.time.Duration;
import java.util.Properties;

public class LocalLlmConfig {

    private static final String DEFAULT_ENDPOINT = "http://localhost:11434/api/generate";
    private static final String DEFAULT_MODEL = "llama3.1:8b";
    private static final int DEFAULT_TIMEOUT_SECONDS = 300;
    private static final int DEFAULT_MAX_TOKENS = 500;

    private final boolean enabled;
    private final String endpoint;
    private final String model;
    private final Duration timeout;
    private final int maxTokens;

    private LocalLlmConfig(boolean enabled, String endpoint, String model, Duration timeout, int maxTokens) {
        this.enabled = enabled;
        this.endpoint = endpoint;
        this.model = model;
        this.timeout = timeout;
        this.maxTokens = maxTokens;
    }

    public static LocalLlmConfig from(Properties properties) {
        boolean enabled = Boolean.parseBoolean(getValue(
                properties,
                "s4e.llm.enabled",
                "S4E_LLM_ENABLED",
                "llm.enabled",
                "false"
        ));

        String endpoint = getValue(
                properties,
                "s4e.llm.endpoint",
                "S4E_LLM_ENDPOINT",
                "llm.endpoint",
                DEFAULT_ENDPOINT
        );

        String model = getValue(
                properties,
                "s4e.llm.model",
                "S4E_LLM_MODEL",
                "llm.model",
                DEFAULT_MODEL
        );

        int timeoutSeconds = getIntValue(
                properties,
                "s4e.llm.timeoutSeconds",
                "S4E_LLM_TIMEOUT_SECONDS",
                "llm.timeoutSeconds",
                DEFAULT_TIMEOUT_SECONDS
        );

        int maxTokens = getIntValue(
                properties,
                "s4e.llm.maxTokens",
                "S4E_LLM_MAX_TOKENS",
                "llm.maxTokens",
                DEFAULT_MAX_TOKENS
        );

        return new LocalLlmConfig(
                enabled,
                endpoint,
                model,
                Duration.ofSeconds(timeoutSeconds),
                maxTokens
        );
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public String getModel() {
        return model;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    private static String getValue(
            Properties properties,
            String propertyName,
            String environmentName,
            String configName,
            String defaultValue) {

        String propertyValue = System.getProperty(propertyName);

        if (!isBlank(propertyValue)) {
            return propertyValue.trim();
        }

        String environmentValue = System.getenv(environmentName);

        if (!isBlank(environmentValue)) {
            return environmentValue.trim();
        }

        if (properties != null) {
            String configValue = properties.getProperty(configName);

            if (!isBlank(configValue)) {
                return configValue.trim();
            }
        }

        return defaultValue;
    }

    private static int getIntValue(
            Properties properties,
            String propertyName,
            String environmentName,
            String configName,
            int defaultValue) {

        String value = getValue(properties, propertyName, environmentName, configName, null);

        if (isBlank(value)) {
            return defaultValue;
        }

        try {
            int parsedValue = Integer.parseInt(value);

            if (parsedValue < 1) {
                return defaultValue;
            }

            return parsedValue;
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
