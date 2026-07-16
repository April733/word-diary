package spring.nexusfuture.word_diary.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.HashMap;
import java.util.Map;

/**
 * Spring AI OpenAI 客户端会在 base-url 后自动追加 /v1/chat/completions。
 * 若 base-url 已含 /v1（如阿里云 compatible-mode），会导致 /v1/v1/... 404。
 */
public class OpenAiBaseUrlEnvironmentPostProcessor implements EnvironmentPostProcessor {

    private static final String[] KEYS = {
            "spring.ai.openai.base-url",
            "spring.ai.openai.chat.base-url"
    };

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Map<String, Object> overrides = new HashMap<>();
        for (String key : KEYS) {
            String value = environment.getProperty(key);
            String normalized = normalizeBaseUrl(value);
            if (normalized != null && !normalized.equals(value)) {
                overrides.put(key, normalized);
            }
        }
        if (!overrides.isEmpty()) {
            environment.getPropertySources().addFirst(
                    new MapPropertySource("openAiBaseUrlNormalized", overrides));
        }
    }

    static String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return baseUrl;
        }
        String trimmed = baseUrl.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        if (trimmed.endsWith("/v1")) {
            return trimmed.substring(0, trimmed.length() - 3);
        }
        return trimmed;
    }
}
