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
import spring.nexusfuture.word_diary.common.TextMetrics;
import spring.nexusfuture.word_diary.common.SysConfigKeys;
import spring.nexusfuture.word_diary.config.AiChatClientConfig.AiChatClientRouter;
import spring.nexusfuture.word_diary.config.AiPromptBuilder;
import spring.nexusfuture.word_diary.dto.AiParseResult;
import spring.nexusfuture.word_diary.dto.ResolvedAiEndpoint;
import spring.nexusfuture.word_diary.entity.WdAiCallLog;
import spring.nexusfuture.word_diary.mapper.WdAiCallLogMapper;
import spring.nexusfuture.word_diary.service.AiModelService;
import spring.nexusfuture.word_diary.service.AiService;

import java.math.BigDecimal;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class AiServiceImpl implements AiService {

    public static final String SCENE_WORD_PARSE = SysConfigKeys.AI_SCENE_WORD_PARSE;
    public static final String SCENE_READING_CHAT = SysConfigKeys.AI_SCENE_READING_CHAT;

    private final AiChatClientRouter chatClientRouter;
    private final ObjectMapper objectMapper;
    private final WdAiCallLogMapper aiCallLogMapper;
    private final AiPromptBuilder promptBuilder;
    private final AiModelService aiModelService;

    public AiServiceImpl(AiChatClientRouter chatClientRouter, ObjectMapper objectMapper,
                         WdAiCallLogMapper aiCallLogMapper, AiPromptBuilder promptBuilder,
                         AiModelService aiModelService) {
        this.chatClientRouter = chatClientRouter;
        this.objectMapper = objectMapper;
        this.aiCallLogMapper = aiCallLogMapper;
        this.promptBuilder = promptBuilder;
        this.aiModelService = aiModelService;
    }

    @Override
    public List<AiParseResult> parseWords(Long userId, List<String> words) {
        long startTime = System.currentTimeMillis();
        String systemPrompt = "";
        String userPrompt = "";
        String inputText = words == null ? "" : String.join(", ", words);
        try {
            String wordsJson = words.stream()
                    .map(w -> "\"" + w + "\"")
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("[]");
            String wordsList = "[" + wordsJson + "]";

            systemPrompt = promptBuilder.buildWordParsePrompt(userId);
            userPrompt = promptBuilder.buildWordParseUserPrompt(userId, wordsList);

            SceneCall call = resolveCall(SCENE_WORD_PARSE);
            ChatResponse chatResponse = call.client().prompt()
                    .options(call.options())
                    .system(systemPrompt)
                    .user(userPrompt)
                    .call()
                    .chatResponse();
            int durationMs = (int) (System.currentTimeMillis() - startTime);

            Generation generation = chatResponse.getResult();
            String content = generation.getOutput().getText();

            saveCallLog(userId, SCENE_WORD_PARSE, inputText, systemPrompt, userPrompt, content, chatResponse, durationMs);

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
            try {
                saveCallLog(userId, SCENE_WORD_PARSE, inputText, systemPrompt, userPrompt, e.getMessage(), null, durationMs);
            } catch (Exception ignored) {}
            throw e;
        }
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
        String inputText = words == null ? "" : String.join(", ", words);

        log.info("开始AI流式调用, userId={}, words={}", userId, words);

        StringBuilder fullResponseText = new StringBuilder();
        SceneCall call = resolveCall(SCENE_WORD_PARSE);
        return call.client().prompt()
                .options(call.options())
                .system(systemPrompt)
                .user(userPrompt)
                .stream()
                .content()
                .filter(text -> text != null && !text.isEmpty())
                .doOnNext(fullResponseText::append)
                .doOnError(e -> {
                    int durationMs = (int) (System.currentTimeMillis() - startTime);
                    log.error("AI流式调用失败, 耗时: {}ms, 错误: {}", durationMs, e.getMessage(), e);
                    try {
                        saveCallLog(userId, SCENE_WORD_PARSE, inputText, systemPrompt, userPrompt,
                                e.getMessage(), null, durationMs);
                    } catch (Exception ignored) {}
                })
                .doOnComplete(() -> {
                    int durationMs = (int) (System.currentTimeMillis() - startTime);
                    String fullText = fullResponseText.toString();
                    log.info("AI流式调用完成, 耗时: {}ms, 响应长度={}", durationMs, fullText.length());
                    saveCallLog(userId, SCENE_WORD_PARSE, inputText, systemPrompt, userPrompt,
                            fullText, null, durationMs);
                });
    }

    @Override
    public Flux<String> chatStream(Long userId, String systemPrompt, String userPrompt) {
        return chatStream(userId, SCENE_READING_CHAT, null, systemPrompt, userPrompt);
    }

    @Override
    public Flux<String> chatStream(Long userId, String callScene, String inputText,
                                   String systemPrompt, String userPrompt) {
        long startTime = System.currentTimeMillis();
        String scene = (callScene == null || callScene.isBlank()) ? SCENE_READING_CHAT : callScene;
        String rawInput = inputText != null ? inputText : "";
        log.info("AI阅读助手流式对话开始, userId={}, scene={}, inputWords={}",
                userId, scene, TextMetrics.wordCount(rawInput));

        StringBuilder fullResponseText = new StringBuilder();

        SceneCall call = resolveCall(scene);
        // 使用 content() 拿增量 token，避免 chatResponse 在部分供应商下整段回传
        return call.client().prompt()
                .options(call.options())
                .system(systemPrompt)
                .user(userPrompt)
                .stream()
                .content()
                .filter(text -> text != null && !text.isEmpty())
                .doOnNext(fullResponseText::append)
                .doOnError(e -> {
                    int durationMs = (int) (System.currentTimeMillis() - startTime);
                    log.error("AI阅读助手流式调用失败, 耗时: {}ms, 错误: {}", durationMs, e.getMessage(), e);
                    try {
                        saveCallLog(userId, scene, rawInput, systemPrompt, userPrompt,
                                e.getMessage(), null, durationMs);
                    } catch (Exception ignored) {}
                })
                .doOnComplete(() -> {
                    int durationMs = (int) (System.currentTimeMillis() - startTime);
                    log.info("AI阅读助手流式调用完成, 耗时: {}ms, 响应总长度={}",
                            durationMs, fullResponseText.length());
                    saveCallLog(userId, scene, rawInput, systemPrompt, userPrompt,
                            fullResponseText.toString(), null, durationMs);
                });
    }

    private record SceneCall(ChatClient client, OpenAiChatOptions options) {}

    /** 场景 → DB 模型 + provider → 对应供应商 ChatClient */
    private SceneCall resolveCall(String sceneKey) {
        ResolvedAiEndpoint endpoint = aiModelService.requireEndpointForScene(sceneKey);
        log.info("AI 场景={} 使用模型={} provider={}", sceneKey, endpoint.modelKey(), endpoint.provider());
        return new SceneCall(
                chatClientRouter.require(endpoint.provider()),
                OpenAiChatOptions.builder()
                        .model(endpoint.modelKey())
                        // 部分兼容网关不支持 stream_options，开启会导致整段缓冲、前端感觉“流式失效”
                        .streamUsage(false)
                        .build()
        );
    }

    private void saveCallLog(Long userId, String callScene, String inputText,
                             String systemPrompt, String userPrompt, String responseData,
                             ChatResponse chatResponse, int durationMs) {
        try {
            String safeInput = inputText != null ? inputText : "";
            String sys = systemPrompt != null ? systemPrompt : "";
            String usr = userPrompt != null ? userPrompt : "";
            int assembledChars = TextMetrics.charCount(sys) + TextMetrics.charCount(usr);

            String modelName = chatResponse != null ? getModelName(chatResponse) : resolveFallbackModelName();
            int promptTokens = chatResponse != null ? getPromptTokens(chatResponse) : 0;
            int completionTokens = chatResponse != null ? getCompletionTokens(chatResponse) : 0;
            int totalTokens = chatResponse != null ? getTotalTokens(chatResponse) : 0;
            BigDecimal estimatedCost = aiModelService.estimateCost(modelName, promptTokens, completionTokens);

            WdAiCallLog callLog = WdAiCallLog.builder()
                    .userId(userId)
                    .modelName(modelName)
                    .callScene(callScene != null ? callScene : "")
                    .inputText(safeInput)
                    .inputCharCount(TextMetrics.charCount(safeInput))
                    .inputWordCount(TextMetrics.wordCount(safeInput))
                    .assembledPromptChars(assembledChars)
                    .systemPrompt(sys)
                    .userPrompt(usr)
                    .responseData(responseData)
                    .promptTokens(promptTokens)
                    .completionTokens(completionTokens)
                    .totalTokens(totalTokens)
                    .estimatedCost(estimatedCost)
                    .durationMs(durationMs)
                    .build();
            aiCallLogMapper.insert(callLog);
            log.info("AI调用记录已保存, id={}, scene={}, model={}, inputWords={}, assembledChars={}, tokens in/out={}/{}, cost={}, {}ms",
                    callLog.getId(), callLog.getCallScene(), callLog.getModelName(),
                    callLog.getInputWordCount(), callLog.getAssembledPromptChars(),
                    callLog.getPromptTokens(), callLog.getCompletionTokens(),
                    callLog.getEstimatedCost(), durationMs);
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
        return resolveFallbackModelName();
    }

    private String resolveFallbackModelName() {
        try {
            return aiModelService.requireActiveModelKey();
        } catch (Exception e) {
            return "";
        }
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
        Pattern pattern = Pattern.compile("\\[\\s*\\{.*?}\\s*]", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return matcher.group(0);
        }
        return text.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();
    }
}
