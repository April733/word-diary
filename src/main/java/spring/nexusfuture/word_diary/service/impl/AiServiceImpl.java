package spring.nexusfuture.word_diary.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import spring.nexusfuture.word_diary.config.AiPromptBuilder;
import spring.nexusfuture.word_diary.dto.AiParseResult;
import spring.nexusfuture.word_diary.entity.WdAiCallLog;
import spring.nexusfuture.word_diary.mapper.WdAiCallLogMapper;
import spring.nexusfuture.word_diary.service.AiService;

import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class AiServiceImpl implements AiService {

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;
    private final WdAiCallLogMapper aiCallLogMapper;
    private final AiPromptBuilder promptBuilder;

    public AiServiceImpl(ChatClient.Builder chatClientBuilder, ObjectMapper objectMapper,
                         WdAiCallLogMapper aiCallLogMapper, AiPromptBuilder promptBuilder) {
        this.chatClient = chatClientBuilder.build();
        this.objectMapper = objectMapper;
        this.aiCallLogMapper = aiCallLogMapper;
        this.promptBuilder = promptBuilder;
    }

    @Override
    public List<AiParseResult> parseWords(Long userId, List<String> words) {
        long startTime = System.currentTimeMillis();
        String systemPrompt = "";
        String userPrompt = "";
        try {
        String wordsJson = words.stream()
                .map(w -> "\"" + w + "\"")
                .reduce((a, b) -> a + ", " + b)
                .orElse("[]");
        String wordsList = "[" + wordsJson + "]";

        systemPrompt = promptBuilder.buildWordParsePrompt(userId);

        userPrompt = promptBuilder.buildWordParseUserPrompt(userId, wordsList);

        ChatResponse chatResponse = chatClient.prompt()
                .system(systemPrompt)
                .user(userPrompt)
                .call()
                .chatResponse();
        int durationMs = (int) (System.currentTimeMillis() - startTime);

        Generation generation = chatResponse.getResult();
        String content = generation.getOutput().getText();

        // 记录调用日志
        saveCallLog(userId, systemPrompt, userPrompt, content, chatResponse, durationMs);

        // Extract JSON array from response (handle markdown code blocks)
        String json = extractJsonArray(content);

        try {
            return objectMapper.readValue(json, new TypeReference<List<AiParseResult>>() {});
        } catch (Exception e) {
            log.error("AI返回JSON解析失败, 原始内容: {}", content, e);
            throw new RuntimeException("AI 返回数据解析失败，请重试", e);
        }

        } catch (Exception e) {
            int durationMs = (int) (System.currentTimeMillis() - startTime);
            log.error("AI调用失败, 耗时: {}ms, 错误: {}", durationMs, e.getMessage(), e);
            // 记录失败日志
            try {
                saveCallLog(userId, systemPrompt, userPrompt, e.getMessage(), null, durationMs);
            } catch (Exception ignored) {}
            throw e;
        }
    }

    private void saveCallLog(Long userId, String systemPrompt, String userPrompt, String responseData,
                             ChatResponse chatResponse, int durationMs) {
        try {
            WdAiCallLog callLog = WdAiCallLog.builder()
                    .userId(userId)
                    .modelName(chatResponse != null ? getModelName(chatResponse) : "unknown")
                    .systemPrompt(systemPrompt)
                    .userPrompt(userPrompt)
                    .responseData(responseData)
                    .promptTokens(chatResponse != null ? getPromptTokens(chatResponse) : 0)
                    .completionTokens(chatResponse != null ? getCompletionTokens(chatResponse) : 0)
                    .totalTokens(chatResponse != null ? getTotalTokens(chatResponse) : 0)
                    .durationMs(durationMs)
                    .build();
            aiCallLogMapper.insert(callLog);
            log.info("AI调用记录已保存, id={}, model={}, tokens={}/{}, {}ms",
                    callLog.getId(), callLog.getModelName(),
                    callLog.getPromptTokens(), callLog.getCompletionTokens(), durationMs);
        } catch (Exception e) {
            log.warn("保存AI调用记录失败: {}", e.getMessage());
        }
    }

    private String getModelName(ChatResponse chatResponse) {
        try {
            if (chatResponse.getMetadata() != null
                    && chatResponse.getMetadata().get("model") != null) {
                return chatResponse.getMetadata().get("model").toString();
            }
        } catch (Exception ignored) {
        }
        return "deepseek-v4-flash";
    }

    private Integer getPromptTokens(ChatResponse chatResponse) {
        return extractUsage(chatResponse).getPromptTokens();
    }

    private Integer getCompletionTokens(ChatResponse chatResponse) {
        return extractUsage(chatResponse).getCompletionTokens();
    }

    private Integer getTotalTokens(ChatResponse chatResponse) {
        return extractUsage(chatResponse).getTotalTokens();
    }

    private org.springframework.ai.chat.metadata.Usage extractUsage(ChatResponse chatResponse) {
        try {
            if (chatResponse != null && chatResponse.getMetadata() != null
                    && chatResponse.getMetadata().getUsage() != null) {
                return chatResponse.getMetadata().getUsage();
            }
        } catch (Exception e) {
            log.warn("提取 token usage 失败: {}", e.getMessage());
        }
        return new org.springframework.ai.chat.metadata.EmptyUsage();
    }

    private String extractJsonArray(String text) {
        // Try to find JSON array in the text, handling markdown ```json ... ``` blocks
        Pattern pattern = Pattern.compile("\\[\\s*\\{.*?}\\s*]", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return matcher.group(0);
        }
        // Fallback: remove markdown code fences
        String cleaned = text.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();
        return cleaned;
    }

    @Override
    public Flux<String> parseWordsStream(Long userId, List<String> words) {
        long startTime = System.currentTimeMillis();
        String systemPrompt = promptBuilder.buildWordParsePrompt(userId);
        String wordsJson = words.stream()
                .map(w -> "\"" + w + "\"")
                .reduce((a, b) -> a + ", " + b)
                .orElse("[]");
        String wordsList = "[" + wordsJson + "]";
        String userPrompt = promptBuilder.buildWordParseUserPrompt(userId, wordsList);

        log.info("开始AI流式调用, userId={}, words={}", userId, words);

        // 用 AtomicReference 收集最后一个 ChatResponse（包含 token usage 元数据）
        java.util.concurrent.atomic.AtomicReference<ChatResponse> lastResponse = new java.util.concurrent.atomic.AtomicReference<>();
        // 收集完整响应文本用于日志
        StringBuilder fullResponseText = new StringBuilder();

        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .streamUsage(true)
                .build();

        return chatClient.prompt()
                .options(options)
                .system(systemPrompt)
                .user(userPrompt)
                .stream()
                .chatResponse()
                .doOnNext(chatResponse -> {
                    lastResponse.set(chatResponse);
                })
                .<String>handle((chatResponse, sink) -> {
                    try {
                        Generation generation = chatResponse.getResult();
                        if (generation != null && generation.getOutput() != null) {
                            String text = generation.getOutput().getText();
                            if (text != null && !text.isEmpty()) {
                                fullResponseText.append(text);
                                log.info("[AI-STREAM] chunk(len={}): {}", text.length(), text);
                                sink.next(text);
                            }
                        }
                    } catch (Exception ex) {
                        log.warn("处理流式chunk异常: {}", ex.getMessage());
                    }
                })
                .doOnError(e -> {
                    int durationMs = (int) (System.currentTimeMillis() - startTime);
                    log.error("AI流式调用失败, 耗时: {}ms, 错误: {}", durationMs, e.getMessage(), e);
                    try {
                        saveCallLog(userId, systemPrompt, userPrompt, e.getMessage(), null, durationMs);
                    } catch (Exception ignored) {}
                })
                .doOnComplete(() -> {
                    int durationMs = (int) (System.currentTimeMillis() - startTime);
                    ChatResponse finalResponse = lastResponse.get();
                    String fullText = fullResponseText.toString();
                    log.info("AI流式调用完成, 耗时: {}ms, finalResponse={}, 完整响应总长度={}",
                            durationMs, finalResponse != null ? "present" : "null", fullText.length());
                    log.info("[AI-STREAM-FULL] 完整响应内容:\n{}", fullText);
                    // 从最终的 ChatResponse 中提取 token 信息保存
                    saveStreamCallLog(userId, systemPrompt, userPrompt, finalResponse, durationMs);
                });
    }

    private void saveStreamCallLog(Long userId, String systemPrompt, String userPrompt,
                                   ChatResponse chatResponse, int durationMs) {
        try {
            String responseData = chatResponse != null && chatResponse.getResult() != null
                    && chatResponse.getResult().getOutput() != null
                    ? chatResponse.getResult().getOutput().getText() : "";
            log.info("[DB-LOG] 保存到wd_ai_call_log, responseData长度={}, 内容: {}", responseData.length(), responseData);
            WdAiCallLog callLog = WdAiCallLog.builder()
                    .userId(userId)
                    .modelName(chatResponse != null ? getModelName(chatResponse) : "deepseek-v4-flash")
                    .systemPrompt(systemPrompt)
                    .userPrompt(userPrompt)
                    .responseData(responseData)
                    .promptTokens(chatResponse != null ? getPromptTokens(chatResponse) : 0)
                    .completionTokens(chatResponse != null ? getCompletionTokens(chatResponse) : 0)
                    .totalTokens(chatResponse != null ? getTotalTokens(chatResponse) : 0)
                    .durationMs(durationMs)
                    .build();
            aiCallLogMapper.insert(callLog);
            log.info("AI流式调用记录已保存, id={}, model={}, tokens={}/{}, {}ms",
                    callLog.getId(), callLog.getModelName(),
                    callLog.getPromptTokens(), callLog.getCompletionTokens(), durationMs);
        } catch (Exception e) {
            log.warn("保存AI流式调用记录失败: {}", e.getMessage());
        }
    }

    @Override
    public Flux<String> chatStream(Long userId, String systemPrompt, String userPrompt) {
        long startTime = System.currentTimeMillis();
        log.info("AI阅读助手流式对话开始, userId={}", userId);

        java.util.concurrent.atomic.AtomicReference<ChatResponse> lastResponse = new java.util.concurrent.atomic.AtomicReference<>();
        StringBuilder fullResponseText = new StringBuilder();

        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .streamUsage(true)
                .build();

        return chatClient.prompt()
                .options(options)
                .system(systemPrompt)
                .user(userPrompt)
                .stream()
                .chatResponse()
                .doOnNext(chatResponse -> {
                    lastResponse.set(chatResponse);
                })
                .<String>handle((chatResponse, sink) -> {
                    try {
                        Generation generation = chatResponse.getResult();
                        if (generation != null && generation.getOutput() != null) {
                            String text = generation.getOutput().getText();
                            if (text != null && !text.isEmpty()) {
                                fullResponseText.append(text);
                                sink.next(text);
                            }
                        }
                    } catch (Exception ex) {
                        log.warn("AI阅读助手处理流式chunk异常: {}", ex.getMessage());
                    }
                })
                .doOnError(e -> {
                    int durationMs = (int) (System.currentTimeMillis() - startTime);
                    log.error("AI阅读助手流式调用失败, 耗时: {}ms, 错误: {}", durationMs, e.getMessage(), e);
                    try {
                        saveCallLog(userId, systemPrompt, userPrompt, e.getMessage(), null, durationMs);
                    } catch (Exception ignored) {}
                })
                .doOnComplete(() -> {
                    int durationMs = (int) (System.currentTimeMillis() - startTime);
                    ChatResponse finalResponse = lastResponse.get();
                    log.info("AI阅读助手流式调用完成, 耗时: {}ms, 响应总长度={}",
                            durationMs, fullResponseText.length());
                    saveChatStreamCallLog(userId, systemPrompt, userPrompt, finalResponse, fullResponseText.toString(), durationMs);
                });
    }

    private void saveChatStreamCallLog(Long userId, String systemPrompt, String userPrompt,
                                       ChatResponse chatResponse, String fullResponseText, int durationMs) {
        try {
            log.info("[DB-LOG] 阅读助手保存到wd_ai_call_log, responseData长度={}", fullResponseText.length());
            WdAiCallLog callLog = WdAiCallLog.builder()
                    .userId(userId)
                    .modelName(chatResponse != null ? getModelName(chatResponse) : "deepseek-v4-flash")
                    .systemPrompt(systemPrompt)
                    .userPrompt(userPrompt)
                    .responseData(fullResponseText)
                    .promptTokens(chatResponse != null ? getPromptTokens(chatResponse) : 0)
                    .completionTokens(chatResponse != null ? getCompletionTokens(chatResponse) : 0)
                    .totalTokens(chatResponse != null ? getTotalTokens(chatResponse) : 0)
                    .durationMs(durationMs)
                    .build();
            aiCallLogMapper.insert(callLog);
            log.info("AI阅读助手调用记录已保存, id={}, model={}, tokens={}/{}, {}ms",
                    callLog.getId(), callLog.getModelName(),
                    callLog.getPromptTokens(), callLog.getCompletionTokens(), durationMs);
        } catch (Exception e) {
            log.warn("保存AI阅读助手调用记录失败: {}", e.getMessage());
        }
    }

}
