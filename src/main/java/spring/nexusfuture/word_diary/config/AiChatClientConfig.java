package spring.nexusfuture.word_diary.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import spring.nexusfuture.word_diary.common.BizException;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Configuration
@EnableConfigurationProperties(AiProviderProperties.class)
public class AiChatClientConfig {

    @Bean
    public AiChatClientRouter aiChatClientRouter(AiProviderProperties properties) {
        Map<String, ChatClient> clients = new LinkedHashMap<>();
        for (Map.Entry<String, AiProviderProperties.Provider> e : properties.getProviders().entrySet()) {
            String provider = e.getKey().trim().toLowerCase();
            AiProviderProperties.Provider cfg = e.getValue();
            if (cfg == null || isBlank(cfg.getApiKey()) || isBlank(cfg.getBaseUrl())) {
                log.warn("跳过 AI 供应商 {}：api-key 或 base-url 未配置", provider);
                continue;
            }
            String baseUrl = OpenAiBaseUrlEnvironmentPostProcessor.normalizeBaseUrl(cfg.getBaseUrl().trim());
            OpenAiApi api = OpenAiApi.builder()
                    .baseUrl(baseUrl)
                    .apiKey(cfg.getApiKey().trim())
                    .build();
            OpenAiChatModel chatModel = OpenAiChatModel.builder()
                    .openAiApi(api)
                    .defaultOptions(OpenAiChatOptions.builder().build())
                    .build();
            clients.put(provider, ChatClient.builder(chatModel).build());
            log.info("已注册 AI 供应商 ChatClient: provider={}, baseUrl={}", provider, baseUrl);
        }
        if (clients.isEmpty()) {
            throw new IllegalStateException("app.ai.providers 未配置任何可用供应商（需要 api-key + base-url）");
        }
        return new AiChatClientRouter(clients);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    /** 按 sys_ai_model.provider 选择 ChatClient */
    public static final class AiChatClientRouter {
        private final Map<String, ChatClient> clients;

        public AiChatClientRouter(Map<String, ChatClient> clients) {
            this.clients = clients;
        }

        public ChatClient require(String provider) {
            if (provider == null || provider.isBlank()) {
                throw new BizException("模型未配置 provider");
            }
            String key = provider.trim().toLowerCase();
            ChatClient client = clients.get(key);
            if (client == null) {
                throw new BizException("未配置 AI 供应商接入点: " + key
                        + "（请在 application.yml 的 app.ai.providers." + key + " 填写 api-key/base-url）");
            }
            return client;
        }

        public boolean hasProvider(String provider) {
            return provider != null && clients.containsKey(provider.trim().toLowerCase());
        }
    }
}
