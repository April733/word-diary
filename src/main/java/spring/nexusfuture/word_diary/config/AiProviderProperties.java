package spring.nexusfuture.word_diary.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * yml 引导用的供应商接入点（api-key / base-url）。
 * 运行时以 sys_ai_provider 为准，见 {@link AiProviderBootstrap}。
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
