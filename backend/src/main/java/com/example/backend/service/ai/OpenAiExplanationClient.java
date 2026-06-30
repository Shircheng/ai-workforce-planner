package com.example.backend.service.ai;

import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;

@Service
public class OpenAiExplanationClient {

    private static final String OPENAI_BASE_URL = "https://api.openai.com/v1";
    private static final String DEFAULT_MODEL = "gpt-4.1-mini";

    private final RestClient restClient;
    private final String apiKey;
    private final String model;
    private final boolean enabled;

    @Autowired
    public OpenAiExplanationClient(
            Environment environment,
            @Value("${ai.openai.enabled:true}") boolean enabled,
            @Value("${ai.openai.model:gpt-4.1-mini}") String model,
            @Value("${ai.openai.timeout-seconds:20}") long timeoutSeconds
    ) {
        this(
                RestClient.builder(),
                resolveApiKey(environment),
                resolveModel(environment, model),
                enabled,
                Duration.ofSeconds(timeoutSeconds <= 0 ? 20 : timeoutSeconds)
        );
    }

    OpenAiExplanationClient(
            RestClient.Builder restClientBuilder,
            String apiKey,
            String model,
            boolean enabled,
            Duration timeout
    ) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(timeout);

        this.restClient = restClientBuilder
                .baseUrl(OPENAI_BASE_URL)
                .requestFactory(requestFactory)
                .build();
        this.apiKey = apiKey;
        this.model = StringUtils.hasText(model) ? model.trim() : DEFAULT_MODEL;
        this.enabled = enabled;
    }

    public OpenAiExplanationResult generateJson(String systemPrompt, String userPrompt) {
        if (!enabled) {
            throw new OpenAiExplanationException("OpenAI explanations are disabled.");
        }
        if (!StringUtils.hasText(apiKey)) {
            throw new OpenAiExplanationException("OPENAI_API_KEY is not configured.");
        }

        Map<String, Object> requestBody = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)
                ),
                "temperature", 0.2,
                "response_format", Map.of("type", "json_object")
        );

        try {
            JsonNode response = restClient.post()
                    .uri("/chat/completions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(JsonNode.class);

            String content = response == null
                    ? null
                    : response.path("choices").path(0).path("message").path("content").asText(null);
            if (!StringUtils.hasText(content)) {
                throw new OpenAiExplanationException("OpenAI response did not include explanation content.");
            }
            return new OpenAiExplanationResult(content, model);
        } catch (RestClientResponseException ex) {
            throw new OpenAiExplanationException(
                    "OpenAI API error: HTTP " + ex.getStatusCode().value(),
                    ex
            );
        } catch (RestClientException ex) {
            throw new OpenAiExplanationException("OpenAI API request failed.", ex);
        }
    }

    public String getConfiguredModel() {
        return model;
    }

    private static String resolveModel(Environment environment, String configuredModel) {
        String environmentValue = environment == null ? null : environment.getProperty("OPENAI_MODEL");
        if (StringUtils.hasText(environmentValue)) {
            return environmentValue.trim();
        }
        String systemValue = System.getenv("OPENAI_MODEL");
        if (StringUtils.hasText(systemValue)) {
            return systemValue.trim();
        }
        String dotenvValue = dotenvValue("OPENAI_MODEL");
        if (StringUtils.hasText(dotenvValue)) {
            return dotenvValue.trim();
        }
        return configuredModel;
    }

    private static String resolveApiKey(Environment environment) {
        String value = environment == null ? null : environment.getProperty("OPENAI_API_KEY");
        if (StringUtils.hasText(value)) {
            return value.trim();
        }
        String systemValue = System.getenv("OPENAI_API_KEY");
        if (StringUtils.hasText(systemValue)) {
            return systemValue.trim();
        }
        return dotenvValue("OPENAI_API_KEY");
    }

    private static String dotenvValue(String key) {
        for (Path path : List.of(Path.of(".env"), Path.of("backend", ".env"))) {
            String value = dotenvValue(path, key);
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private static String dotenvValue(Path path, String key) {
        if (!Files.isRegularFile(path)) {
            return null;
        }
        try {
            for (String line : Files.readAllLines(path)) {
                String trimmed = line.trim();
                if (!StringUtils.hasText(trimmed) || trimmed.startsWith("#")) {
                    continue;
                }
                int separator = trimmed.indexOf('=');
                if (separator <= 0) {
                    continue;
                }
                String name = trimmed.substring(0, separator).trim();
                if (!key.equals(name)) {
                    continue;
                }
                String value = trimmed.substring(separator + 1).trim();
                if ((value.startsWith("\"") && value.endsWith("\""))
                        || (value.startsWith("'") && value.endsWith("'"))) {
                    value = value.substring(1, value.length() - 1);
                }
                return value;
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }

    public record OpenAiExplanationResult(String content, String modelUsed) {
    }

    public static class OpenAiExplanationException extends RuntimeException {

        public OpenAiExplanationException(String message) {
            super(message);
        }

        public OpenAiExplanationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
