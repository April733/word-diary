package spring.nexusfuture.word_diary.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OpenAiBaseUrlEnvironmentPostProcessorTest {

    @Test
    void normalizeBaseUrl_stripsTrailingV1() {
        assertEquals("https://dashscope.aliyuncs.com/compatible-mode",
                OpenAiBaseUrlEnvironmentPostProcessor.normalizeBaseUrl(
                        "https://dashscope.aliyuncs.com/compatible-mode/v1"));
        assertEquals("https://dashscope.aliyuncs.com/compatible-mode",
                OpenAiBaseUrlEnvironmentPostProcessor.normalizeBaseUrl(
                        "https://dashscope.aliyuncs.com/compatible-mode/v1/"));
    }

    @Test
    void normalizeBaseUrl_leavesCorrectUrls() {
        assertEquals("https://api.deepseek.com",
                OpenAiBaseUrlEnvironmentPostProcessor.normalizeBaseUrl("https://api.deepseek.com"));
        assertEquals("https://dashscope.aliyuncs.com/compatible-mode",
                OpenAiBaseUrlEnvironmentPostProcessor.normalizeBaseUrl(
                        "https://dashscope.aliyuncs.com/compatible-mode"));
        assertEquals("https://api-inference.modelscope.cn",
                OpenAiBaseUrlEnvironmentPostProcessor.normalizeBaseUrl(
                        "https://api-inference.modelscope.cn/v1"));
    }
}
