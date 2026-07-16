package spring.nexusfuture.word_diary.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 多供应商接入点（仅 api-key / base-url，不含模型名）。
 * 模型由 sys_ai_scene + sys_ai_model 决定。
 */
@ConfigurationProperties(prefix = "app.ai")
public class AiProviderProperties {

    private Map<String, Provider> providers = new LinkedHashMap<>();

    public Map<String, Provider> getProviders() {
        return providers;
    }

    public void setProviders(Map<String, Provider> providers) {
        this.providers = providers != null ? providers : new LinkedHashMap<>();
    }

    public static class Provider {
        private String apiKey = "";
        private String baseUrl = "";

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }
    }
}
