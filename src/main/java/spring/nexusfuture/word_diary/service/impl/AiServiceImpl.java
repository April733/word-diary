package spring.nexusfuture.word_diary.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import spring.nexusfuture.word_diary.common.BizException;
import spring.nexusfuture.word_diary.common.TextMetrics;
import spring.nexusfuture.word_diary.common.TraceIdSupport;
import spring.nexusfuture.word_diary.common.SysConfigKeys;
import spring.nexusfuture.word_diary.common.WordPosParser;
import spring.nexusfuture.word_diary.config.AiChatClientRouter;
import spring.nexusfuture.word_diary.config.AiPromptBuilder;
import spring.nexusfuture.word_diary.config.AiSceneChatOptionsSupport;
import spring.nexusfuture.word_diary.dto.response.AiParseResult;
import spring.nexusfuture.word_diary.dto.ResolvedAiEndpoint;
import spring.nexusfuture.word_diary.entity.WdAiCallLog;
import spring.nexusfuture.word_diary.mapper.WdAiCallLogMapper;
import spring.nexusfuture.word_diary.service.AiModelService;
import spring.nexusfuture.word_diary.service.AiRegionResolver;
import spring.nexusfuture.word_diary.service.AiService;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.web.client.ResourceAccessException;

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
    private final AiRegionResolver aiRegionResolver;

    public AiServiceImpl(AiChatClientRouter chatClientRouter, ObjectMapper objectMapper,
                         WdAiCallLogMapper aiCallLogMapper, AiPromptBuilder promptBuilder,
                         AiModelService aiModelService, AiRegionResolver aiRegionResolver) {
        this.chatClientRouter = chatClientRouter;
        this.objectMapper = objectMapper;
        this.aiCallLogMapper = aiCallLogMapper;
        this.promptBuilder = promptBuilder;
        this.aiModelService = aiModelService;
        this.aiRegionResolver = aiRegionResolver;
    }

    @Override
    public List<AiParseResult> parseWords(Long userId, List<String> words) {
        long startTime = System.currentTimeMillis();
        String traceId = TraceIdSupport.getOrCreate();
        String systemPrompt = "";
        String userPrompt = "";
        String inputText = words == null ? "" : String.join(", ", words);
        String usedModelKey = null;
        try {
            log.info("[ai-latency][{}] parseWords start userId={}, words={}", traceId, userId, words != null ? words.size() : 0);
            long tPromptStart = System.currentTimeMillis();
            String wordsJson = words.stream()
                    .map(w -> "\"" + w + "\"")
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("[]");
            String wordsList = "[" + wordsJson + "]";

            systemPrompt = promptBuilder.buildWordParsePrompt(userId);
            userPrompt = promptBuilder.buildWordParseUserPrompt(userId, wordsList);
            log.info("[ai-latency][{}] parseWords promptBuilt promptMs={}, sysChars={}, userChars={}",
                    traceId, System.currentTimeMillis() - tPromptStart,
                    systemPrompt.length(), userPrompt.length());

            long tResolveStart = System.currentTimeMillis();
            SceneCall call = resolveCall(userId, SCENE_WORD_PARSE);
            AiSceneChatOptionsSupport.bindExtraBody(call.params());
            usedModelKey = call.modelKey();
            log.info("[ai-latency][{}] parseWords routeResolved resolveMs={}, model={}, provider={}",
                    traceId, System.currentTimeMillis() - tResolveStart, usedModelKey, call.provider());
            long tProviderStart = System.currentTimeMillis();
            ChatResponse chatResponse = call.client().prompt()
                    .options(call.options())
                    .system(systemPrompt)
                    .user(userPrompt)
                    .call()
                    .chatResponse();
            int durationMs = (int) (System.currentTimeMillis() - startTime);
            log.info("[ai-latency][{}] parseWords providerReturned providerMs={}, totalMs={}",
                    traceId, System.currentTimeMillis() - tProviderStart, durationMs);

            Generation generation = chatResponse.getResult();
            String content = generation.getOutput().getText();

            saveCallLog(userId, SCENE_WORD_PARSE, inputText, systemPrompt, userPrompt, content,
                    chatResponse, usedModelKey, durationMs, traceId);

            String json = extractJsonArray(content);
            long tParseStart = System.currentTimeMillis();
            try {
                List<AiParseResult> results = objectMapper.readValue(json, new TypeReference<List<AiParseResult>>() {});
                canonicalizeParseResults(results);
                log.info("[ai-latency][{}] parseWords done parseMs={}, totalMs={}, results={}",
                        traceId, System.currentTimeMillis() - tParseStart,
                        System.currentTimeMillis() - startTime, results != null ? results.size() : 0);
                return results;
            } catch (Exception e) {
                log.error("AI返回JSON解析失败, 原始内容: {}", content, e);
                throw new RuntimeException("AI 返回数据解析失败，请重试", e);
            }
        } catch (Exception e) {
            int durationMs = (int) (System.currentTimeMillis() - startTime);
            log.error("[ai-latency][{}] parseWords failed totalMs={}, err={}", traceId, durationMs, e.getMessage(), e);
            try {
                saveCallLog(userId, SCENE_WORD_PARSE, inputText, systemPrompt, userPrompt,
                        e.getMessage(), null, usedModelKey, durationMs, traceId);
            } catch (Exception ignored) {}
            throw e;
        } finally {
            AiSceneChatOptionsSupport.clearExtraBody();
        }
    }

    @Override
    public Flux<String> parseWordsStream(Long userId, List<String> words) {
        long startTime = System.currentTimeMillis();
        String traceId = TraceIdSupport.getOrCreate();
        String systemPrompt = promptBuilder.buildWordParsePrompt(userId);
        String wordsJson = words.stream()
                .map(w -> "\"" + w + "\"")
                .reduce((a, b) -> a + ", " + b)
                .orElse("[]");
        String wordsList = "[" + wordsJson + "]";
        String userPrompt = promptBuilder.buildWordParseUserPrompt(userId, wordsList);
        String inputText = words == null ? "" : String.join(", ", words);

        log.info("[ai-latency][{}] parseWordsStream start userId={}, words={}, sysChars={}, userChars={}",
                traceId, userId, words, systemPrompt.length(), userPrompt.length());

        StringBuilder fullResponseText = new StringBuilder();
        final ChatResponse[] lastWithUsage = {null};
        SceneCall call = resolveCall(userId, SCENE_WORD_PARSE);
        AiSceneChatOptionsSupport.bindExtraBody(call.params());
        String usedModelKey = call.modelKey();
        // chatResponse + streamUsage：末包带 usage，流式结束后写入 token
        return call.client().prompt()
                .options(call.options())
                .system(systemPrompt)
                .user(userPrompt)
                .stream()
                .chatResponse()
                .doOnNext(cr -> {
                    if (hasTokenUsage(cr)) {
                        lastWithUsage[0] = cr;
                    }
                    String text = extractDeltaText(cr);
                    if (!text.isEmpty()) {
                        fullResponseText.append(text);
                    }
                })
                .map(this::extractDeltaText)
                .filter(text -> !text.isEmpty())
                .doOnError(e -> {
                    int durationMs = (int) (System.currentTimeMillis() - startTime);
                    log.error("[ai-latency][{}] parseWordsStream failed totalMs={}, err={}",
                            traceId, durationMs, e.getMessage(), e);
                    try {
                        saveCallLog(userId, SCENE_WORD_PARSE, inputText, systemPrompt, userPrompt,
                                e.getMessage(), lastWithUsage[0], usedModelKey, durationMs, traceId);
                    } catch (Exception ignored) {}
                })
                .doOnComplete(() -> {
                    int durationMs = (int) (System.currentTimeMillis() - startTime);
                    String fullText = fullResponseText.toString();
                    log.info("[ai-latency][{}] parseWordsStream done totalMs={}, respChars={}, hasUsage={}",
                            traceId, durationMs, fullText.length(), lastWithUsage[0] != null);
                    saveCallLog(userId, SCENE_WORD_PARSE, inputText, systemPrompt, userPrompt,
                            fullText, lastWithUsage[0], usedModelKey, durationMs, traceId);
                })
                .doFinally(sig -> AiSceneChatOptionsSupport.clearExtraBody());
    }

    @Override
    public Flux<String> chatStream(Long userId, String systemPrompt, String userPrompt) {
        return chatStream(userId, SCENE_READING_CHAT, null, systemPrompt, userPrompt, null);
    }

    @Override
    public Flux<String> chatStream(Long userId, String callScene, String inputText,
                                   String systemPrompt, String userPrompt) {
        return chatStream(userId, callScene, inputText, systemPrompt, userPrompt, null);
    }

    @Override
    public Flux<String> chatStream(Long userId, String callScene, String inputText,
                                   String systemPrompt, String userPrompt,
                                   List<Message> history) {
        long startTime = System.currentTimeMillis();
        String traceId = TraceIdSupport.getOrCreate();
        String scene = (callScene == null || callScene.isBlank()) ? SCENE_READING_CHAT : callScene;
        String rawInput = inputText != null ? inputText : "";
        List<Message> historyMessages = history == null ? Collections.emptyList() : history;
        log.info("[ai-stream][{}] 开始 userId={}, scene={}, inputWords={}, sysChars={}, userChars={}, historyMsgs={}",
                traceId, userId, scene, TextMetrics.wordCount(rawInput),
                systemPrompt != null ? systemPrompt.length() : 0,
                userPrompt != null ? userPrompt.length() : 0,
                historyMessages.size());

        StringBuilder fullResponseText = new StringBuilder();
        final ChatResponse[] lastWithUsage = {null};
        final long[] providerRequestAt = {0L};
        final long[] firstChunkAt = {0L};

        long tResolve = System.currentTimeMillis();
        SceneCall call = resolveCall(userId, scene);
        AiSceneChatOptionsSupport.bindExtraBody(call.params());
        String usedModelKey = call.modelKey();
        log.info("[ai-stream][{}] resolveCall 耗时={}ms model={} provider={} 就绪",
                traceId, System.currentTimeMillis() - tResolve, usedModelKey, call.provider());

        // chatResponse：增量仍按 delta 推前端；streamUsage 末包带回 usage，完成后落库
        var promptSpec = call.client().prompt()
                .options(call.options())
                .system(systemPrompt);
        if (!historyMessages.isEmpty()) {
            promptSpec = promptSpec.messages(historyMessages);
        }
        return promptSpec
                .user(userPrompt)
                .stream()
                .chatResponse()
                .doOnSubscribe(s -> {
                    providerRequestAt[0] = System.currentTimeMillis();
                    // ★ 重要指标③起点：真正向大模型发起 stream
                    log.info("========== [耗时][{}] ③已请求大模型 scene={} model={} history={} model就绪耗时={}ms (等待首token…) ==========",
                            traceId, scene, usedModelKey, historyMessages.size(), providerRequestAt[0] - startTime);
                })
                .doOnNext(cr -> {
                    if (hasTokenUsage(cr)) {
                        lastWithUsage[0] = cr;
                    }
                    String text = extractDeltaText(cr);
                    if (text.isEmpty()) {
                        return;
                    }
                    if (firstChunkAt[0] == 0L) {
                        firstChunkAt[0] = System.currentTimeMillis();
                        long sinceProvider = providerRequestAt[0] > 0
                                ? firstChunkAt[0] - providerRequestAt[0]
                                : firstChunkAt[0] - startTime;
                        // ★ 重要指标③结果：请求大模型 → 首 token
                        log.info("========== [耗时][{}] ③请求大模型→首token={}ms (chatStream总准备={}ms) 预览={} ==========",
                                traceId, sinceProvider,
                                firstChunkAt[0] - startTime,
                                text.length() > 20 ? text.substring(0, 20) + "…" : text);
                    }
                    fullResponseText.append(text);
                })
                .map(this::extractDeltaText)
                .filter(text -> !text.isEmpty())
                .doOnError(e -> {
                    int durationMs = (int) (System.currentTimeMillis() - startTime);
                    log.error("[ai-stream][{}] 失败 耗时={}ms, 错误={}", traceId, durationMs, e.getMessage(), e);
                    try {
                        saveCallLog(userId, scene, rawInput, systemPrompt, userPrompt,
                                e.getMessage(), lastWithUsage[0], usedModelKey, durationMs, traceId);
                    } catch (Exception ignored) {}
                })
                .doOnComplete(() -> {
                    int durationMs = (int) (System.currentTimeMillis() - startTime);
                    long ttfbProvider = (firstChunkAt[0] > 0 && providerRequestAt[0] > 0)
                            ? firstChunkAt[0] - providerRequestAt[0] : -1;
                    log.info("[ai-stream][{}] 完成 total={}ms, 请求大模型→首token={}ms, afterFirst={}ms, 响应长度={}, hasUsage={}",
                            traceId, durationMs, ttfbProvider,
                            firstChunkAt[0] > 0 ? durationMs - (firstChunkAt[0] - startTime) : durationMs,
                            fullResponseText.length(), lastWithUsage[0] != null);
                    saveCallLog(userId, scene, rawInput, systemPrompt, userPrompt,
                            fullResponseText.toString(), lastWithUsage[0], usedModelKey, durationMs, traceId);
                })
                .doFinally(sig -> AiSceneChatOptionsSupport.clearExtraBody());
    }

    @Override
    public String chatOnce(Long userId, String callScene, String inputText,
                           String systemPrompt, String userPrompt) {
        return chatOnce(userId, callScene, inputText, systemPrompt, userPrompt, null);
    }

    @Override
    public String chatOnce(Long userId, String callScene, String inputText,
                           String systemPrompt, String userPrompt,
                           List<org.springframework.ai.content.Media> media) {
        long startTime = System.currentTimeMillis();
        String traceId = TraceIdSupport.getOrCreate();
        String scene = (callScene == null || callScene.isBlank()) ? SCENE_READING_CHAT : callScene;
        String rawInput = inputText != null ? inputText : "";
        String usedModelKey = null;
        String usedProvider = null;
        boolean wantMedia = media != null && !media.isEmpty();
        try {
            log.info("[ai-latency][{}] chatOnce start userId={}, scene={}, inputWords={}, media={}, sysChars={}, userChars={}",
                    traceId, userId, scene, TextMetrics.wordCount(rawInput), wantMedia ? media.size() : 0,
                    systemPrompt != null ? systemPrompt.length() : 0,
                    userPrompt != null ? userPrompt.length() : 0);
            long tResolveStart = System.currentTimeMillis();
            SceneCall call = resolveCall(userId, scene);
            usedModelKey = call.modelKey();
            usedProvider = call.provider();
            log.info("[ai-latency][{}] chatOnce routeResolved resolveMs={}, model={}, provider={}",
                    traceId, System.currentTimeMillis() - tResolveStart, usedModelKey, usedProvider);
            boolean useMedia = wantMedia && providerSupportsVision(usedProvider, usedModelKey);
            if (wantMedia && !useMedia) {
                log.warn("[ai-once] 当前线路不支持识图，改为纯文本 scene={} provider={} model={}",
                        scene, usedProvider, usedModelKey);
            }
            String effectiveUser = userPrompt != null ? userPrompt : "";
            if (wantMedia && !useMedia) {
                effectiveUser = effectiveUser + VISION_FALLBACK_NOTE;
            }
            try {
                return invokeOnce(call, systemPrompt, effectiveUser, useMedia ? media : null,
                        userId, scene, rawInput, startTime, traceId);
            } catch (Exception first) {
                if (useMedia && isLikelyVisionUnsupported(first)) {
                    log.warn("[ai-once] 识图调用失败，降级纯文本 scene={} err={}", scene, first.getMessage());
                    return invokeOnce(call, systemPrompt, (userPrompt != null ? userPrompt : "") + VISION_FALLBACK_NOTE,
                            null, userId, scene, rawInput, startTime, traceId);
                }
                throw first;
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            int durationMs = (int) (System.currentTimeMillis() - startTime);
            log.error("[ai-once] 失败 scene={}, media={}, 耗时={}ms, err={}",
                    scene, wantMedia ? media.size() : 0, durationMs, e.getMessage(), e);
            try {
                saveCallLog(userId, scene, rawInput, systemPrompt, userPrompt,
                        e.getMessage(), null, usedModelKey, durationMs, traceId);
            } catch (Exception ignored) {
            }
            throw wrapAiCallException(e);
        }
    }

    private static final String VISION_FALLBACK_NOTE = """


            [系统说明] 当前 AI 线路不支持图片输入，题目截图未能送达模型。
            请主要依据作文正文评分，并在 summary 中注明「未能核对照片题目」。
            """;

    private String invokeOnce(SceneCall call, String systemPrompt, String userPrompt,
                              List<org.springframework.ai.content.Media> media,
                              Long userId, String scene, String rawInput, long startTime, String traceId) {
        AiSceneChatOptionsSupport.bindExtraBody(call.params());
        try {
            var promptSpec = call.client().prompt()
                    .options(call.options())
                    .system(systemPrompt != null ? systemPrompt : "");
            ChatResponse chatResponse;
            long tProviderStart = System.currentTimeMillis();
            if (media != null && !media.isEmpty()) {
                org.springframework.ai.chat.messages.UserMessage userMessage =
                        new org.springframework.ai.chat.messages.UserMessage(
                                userPrompt != null ? userPrompt : "", media);
                chatResponse = promptSpec.messages(userMessage).call().chatResponse();
            } else {
                chatResponse = promptSpec
                        .user(userPrompt != null ? userPrompt : "")
                        .call()
                        .chatResponse();
            }
            int durationMs = (int) (System.currentTimeMillis() - startTime);
            long providerMs = System.currentTimeMillis() - tProviderStart;
            String content = chatResponse.getResult().getOutput().getText();
            log.info("[ai-latency][{}] chatOnce providerReturned scene={}, model={}, providerMs={}, totalMs={}, respChars={}",
                    traceId, scene, call.modelKey(), providerMs, durationMs, content != null ? content.length() : 0);
            saveCallLog(userId, scene, rawInput, systemPrompt, userPrompt, content,
                    chatResponse, call.modelKey(), durationMs, traceId);
            return content != null ? content : "";
        } finally {
            AiSceneChatOptionsSupport.clearExtraBody();
        }
    }

    /** DeepSeek 等官方 API 目前多为纯文本；带图请求易 Broken pipe / 参数拒绝 */
    private boolean providerSupportsVision(String provider, String modelKey) {
        String p = provider != null ? provider.toLowerCase(Locale.ROOT) : "";
        String m = modelKey != null ? modelKey.toLowerCase(Locale.ROOT) : "";
        if (p.contains("deepseek") || m.contains("deepseek")) {
            return false;
        }
        // 明确含 vl / vision 的模型键视为支持
        if (m.contains("vl") || m.contains("vision") || m.contains("gpt-4o") || m.contains("gemini")) {
            return true;
        }
        // 其它线路保守尝试；失败再降级
        return true;
    }

    private boolean isLikelyVisionUnsupported(Throwable e) {
        String msg = rootMessage(e).toLowerCase(Locale.ROOT);
        return msg.contains("broken pipe")
                || msg.contains("image_url")
                || msg.contains("image")
                || msg.contains("vision")
                || msg.contains("multimodal")
                || msg.contains("unknown variant")
                || e instanceof ResourceAccessException;
    }

    private BizException wrapAiCallException(Throwable e) {
        String msg = rootMessage(e);
        if (msg.toLowerCase(Locale.ROOT).contains("broken pipe")
                || e instanceof ResourceAccessException) {
            return new BizException("AI 线路调用失败（可能不支持识图或网络中断），请稍后重试或切换线路");
        }
        return new BizException("AI 调用失败，请稍后重试");
    }

    private String rootMessage(Throwable e) {
        Throwable cur = e;
        String last = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
            if (cur.getMessage() != null && !cur.getMessage().isBlank()) {
                last = cur.getMessage();
            }
        }
        return last;
    }

    private record SceneCall(ChatClient client, OpenAiChatOptions options, String modelKey, String provider,
                             spring.nexusfuture.word_diary.dto.AiSceneCallParams params) {}

    /** 场景 + 用户地区 → DB 模型 + provider → ChatClient */
    private SceneCall resolveCall(Long userId, String sceneKey) {
        String region = aiRegionResolver.resolveEffectiveRegion(userId);
        ResolvedAiEndpoint endpoint = aiModelService.requireEndpointForScene(sceneKey, region);
        var params = endpoint.paramsOrEmpty();
        log.info("AI 场景={} region={} 使用模型={} provider={} temp={} thinking={}",
                sceneKey, region, endpoint.modelKey(), endpoint.provider(),
                params.temperature(), params.thinkingMode());
        return new SceneCall(
                chatClientRouter.require(endpoint.provider()),
                AiSceneChatOptionsSupport.buildOptions(endpoint, true),
                endpoint.modelKey(),
                endpoint.provider(),
                params
        );
    }

    private void saveCallLog(Long userId, String callScene, String inputText,
                             String systemPrompt, String userPrompt, String responseData,
                             ChatResponse chatResponse, String usedModelKey, int durationMs) {
        saveCallLog(userId, callScene, inputText, systemPrompt, userPrompt,
                responseData, chatResponse, usedModelKey, durationMs, null);
    }

    private void saveCallLog(Long userId, String callScene, String inputText,
                             String systemPrompt, String userPrompt, String responseData,
                             ChatResponse chatResponse, String usedModelKey, int durationMs, String traceId) {
        try {
            long tSaveStart = System.currentTimeMillis();
            String safeInput = inputText != null ? inputText : "";
            String sys = systemPrompt != null ? systemPrompt : "";
            String usr = userPrompt != null ? userPrompt : "";
            int assembledChars = TextMetrics.charCount(sys) + TextMetrics.charCount(usr);

            // 流式必须以 resolveCall 得到的 modelKey 为准，
            // 不能回退 default 场景模型（否则看板会把 qwen 误记成 deepseek）
            String modelName = resolveLoggedModelName(chatResponse, usedModelKey);
            int promptTokens = chatResponse != null ? getPromptTokens(chatResponse) : 0;
            int completionTokens = chatResponse != null ? getCompletionTokens(chatResponse) : 0;
            int reasoningTokens = chatResponse != null ? getReasoningTokens(chatResponse) : 0;
            int contentTokens = chatResponse != null ? getContentTokens(chatResponse) : 0;
            int totalTokens = chatResponse != null ? getTotalTokens(chatResponse) : 0;
            BigDecimal estimatedCost = aiModelService.estimateCost(modelName, promptTokens, contentTokens);
            String responseDataToStore = resolveResponseDataForLog(callScene, responseData, chatResponse);

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
                    .responseData(responseDataToStore)
                    .promptTokens(promptTokens)
                    .completionTokens(completionTokens)
                    .reasoningTokens(reasoningTokens)
                    .contentTokens(contentTokens)
                    .totalTokens(totalTokens)
                    .estimatedCost(estimatedCost)
                    .durationMs(durationMs)
                    .build();
            long tInsertStart = System.currentTimeMillis();
            aiCallLogMapper.insert(callLog);
            long insertMs = System.currentTimeMillis() - tInsertStart;
            long saveMs = System.currentTimeMillis() - tSaveStart;
            if (promptTokens == 0 && completionTokens == 0 && responseData != null && !responseData.isBlank()) {
                log.warn("AI调用记录 token 仍为 0（供应商可能未回 usage）, id={}, scene={}, model={}, responseChars={}",
                        callLog.getId(), callLog.getCallScene(), callLog.getModelName(),
                        TextMetrics.charCount(responseData));
            }
            log.info("AI调用记录已保存, id={}, scene={}, model={}, inputWords={}, assembledChars={}, tokens in={}/out={}(reason={}/content={}), cost={}, {}ms",
                    callLog.getId(), callLog.getCallScene(), callLog.getModelName(),
                    callLog.getInputWordCount(), callLog.getAssembledPromptChars(),
                    callLog.getPromptTokens(), callLog.getCompletionTokens(),
                    callLog.getReasoningTokens(), callLog.getContentTokens(),
                    callLog.getEstimatedCost(), durationMs);
            log.info("[ai-latency][{}] callLogSaved id={}, scene={}, model={}, insertMs={}, saveTotalMs={}",
                    traceId != null ? traceId : "-", callLog.getId(), callLog.getCallScene(), callLog.getModelName(),
                    insertMs, saveMs);
        } catch (Exception e) {
            log.warn("保存AI调用记录失败: {}", e.getMessage());
        }
    }

    private String resolveResponseDataForLog(String callScene, String responseData, ChatResponse chatResponse) {
        if (!SysConfigKeys.AI_SCENE_TRANSLATION_GENERATE.equals(callScene)) {
            return responseData;
        }
        if (chatResponse == null || chatResponse.getResult() == null) {
            return responseData;
        }
        try {
            return objectMapper.writeValueAsString(chatResponse.getResult());
        } catch (Exception e) {
            log.warn("translation_generate 序列化 chatResponse.getResult() 失败: {}", e.getMessage());
            return responseData;
        }
    }



    /** 优先用请求时解析到的模型；同步响应元数据仅作补充 */
    private String resolveLoggedModelName(ChatResponse chatResponse, String usedModelKey) {
        if (usedModelKey != null && !usedModelKey.isBlank()) {
            return usedModelKey.trim();
        }
        if (chatResponse != null) {
            try {
                if (chatResponse.getMetadata() != null
                        && chatResponse.getMetadata().get("model") != null) {
                    String fromMeta = chatResponse.getMetadata().get("model").toString();
                    if (fromMeta != null && !fromMeta.isBlank()) {
                        return fromMeta.trim();
                    }
                }
            } catch (Exception ignored) {
            }
        }
        try {
            return aiModelService.requireActiveModelKey();
        } catch (Exception e) {
            return "";
        }
    }

    private String extractDeltaText(ChatResponse chatResponse) {
        try {
            if (chatResponse == null || chatResponse.getResult() == null
                    || chatResponse.getResult().getOutput() == null) {
                return "";
            }
            String text = chatResponse.getResult().getOutput().getText();
            return text != null ? text : "";
        } catch (Exception e) {
            return "";
        }
    }

    private boolean hasTokenUsage(ChatResponse chatResponse) {
        org.springframework.ai.chat.metadata.Usage usage = extractUsage(chatResponse);
        return nullToZero(usage.getPromptTokens()) > 0
                || nullToZero(usage.getCompletionTokens()) > 0
                || nullToZero(usage.getTotalTokens()) > 0;
    }

    private Integer getPromptTokens(ChatResponse chatResponse) {
        return nullToZero(extractUsage(chatResponse).getPromptTokens());
    }

    private Integer getCompletionTokens(ChatResponse chatResponse) {
        return nullToZero(extractUsage(chatResponse).getCompletionTokens());
    }

    private Integer getReasoningTokens(ChatResponse chatResponse) {
        if (chatResponse == null) {
            return 0;
        }
        // 方式1：反射调用 Usage.getReasoningTokens()（Spring AI 1.0.0-M8+ 支持）
        try {
            java.lang.reflect.Method m = extractUsage(chatResponse).getClass()
                    .getMethod("getReasoningTokens");
            Object r = m.invoke(extractUsage(chatResponse));
            if (r instanceof Integer) {
                return (Integer) r;
            }
            if (r instanceof Number) {
                return ((Number) r).intValue();
            }
        } catch (Exception ignored) {
        }
        // 方式2：从 provider metadata 提取
        try {
            if (chatResponse.getResult() != null && chatResponse.getResult().getOutput() != null
                    && chatResponse.getResult().getOutput().getMetadata() != null) {
                Object rt = chatResponse.getResult().getOutput().getMetadata().get("reasoning_tokens");
                if (rt instanceof Number) {
                    return ((Number) rt).intValue();
                }
            }
            if (chatResponse.getMetadata() != null && chatResponse.getMetadata().getUsage() != null) {
                var meta = chatResponse.getMetadata();
                java.lang.reflect.Method getMap = meta.getClass().getMethod("getExtraMetadata");
                Object extra = getMap.invoke(meta);
                if (extra instanceof java.util.Map) {
                    Object rt = ((java.util.Map<?, ?>) extra).get("reasoning_tokens");
                    if (rt instanceof Number) {
                        return ((Number) rt).intValue();
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return 0;
    }

    private Integer getContentTokens(ChatResponse chatResponse) {
        int completion = getCompletionTokens(chatResponse);
        int reasoning = getReasoningTokens(chatResponse);
        return Math.max(completion - reasoning, 0);
    }

    private Integer getTotalTokens(ChatResponse chatResponse) {
        int total = nullToZero(extractUsage(chatResponse).getTotalTokens());
        if (total > 0) {
            return total;
        }
        return getPromptTokens(chatResponse) + getCompletionTokens(chatResponse);
    }

    private static int nullToZero(Integer value) {
        return value != null ? value : 0;
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

    private static void canonicalizeParseResults(List<AiParseResult> results) {
        if (results == null) {
            return;
        }
        for (AiParseResult r : results) {
            if (r != null) {
                r.setPartSpeechMean(WordPosParser.canonicalize(r.getPartSpeechMean()));
            }
        }
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
