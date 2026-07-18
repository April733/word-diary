package spring.nexusfuture.word_diary.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import spring.nexusfuture.word_diary.common.AiFeatureI18nSupport;
import spring.nexusfuture.word_diary.common.BizException;
import spring.nexusfuture.word_diary.common.CollectJsonSupport;
import spring.nexusfuture.word_diary.common.R;
import spring.nexusfuture.word_diary.config.AiPromptBuilder;
import spring.nexusfuture.word_diary.dto.AnnotationReq;
import spring.nexusfuture.word_diary.dto.PdfMarkupReq;
import spring.nexusfuture.word_diary.entity.WdPdfMarkup;
import spring.nexusfuture.word_diary.service.PdfMarkupService;
import spring.nexusfuture.word_diary.dto.BookCollectReq;
import spring.nexusfuture.word_diary.dto.BookCollectResultVO;
import spring.nexusfuture.word_diary.dto.BookNotebookLinkVO;
import spring.nexusfuture.word_diary.dto.CollectEntryDTO;
import spring.nexusfuture.word_diary.entity.WdAiMessage;
import spring.nexusfuture.word_diary.entity.WdAiSession;
import spring.nexusfuture.word_diary.entity.WdAnnotation;
import spring.nexusfuture.word_diary.entity.WdBook;
import spring.nexusfuture.word_diary.entity.WdReaderSettings;
import spring.nexusfuture.word_diary.service.AiService;
import spring.nexusfuture.word_diary.service.AiSessionService;
import spring.nexusfuture.word_diary.service.AnnotationService;
import spring.nexusfuture.word_diary.service.BookCollectService;
import spring.nexusfuture.word_diary.service.BookNotebookLinkService;
import spring.nexusfuture.word_diary.service.BookService;
import spring.nexusfuture.word_diary.service.ReaderSettingsService;

import spring.nexusfuture.word_diary.entity.SysAiFeature;
import spring.nexusfuture.word_diary.service.AiFeatureService;
import spring.nexusfuture.word_diary.service.CosService;
import spring.nexusfuture.word_diary.service.FeedbackService;
import spring.nexusfuture.word_diary.service.MailService;
import spring.nexusfuture.word_diary.dto.AiFeaturePreferenceVO;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@RestController
@RequestMapping("/api/books")
@RequiredArgsConstructor
public class BookController {

    private final BookService bookService;
    private final AnnotationService annotationService;
    private final ReaderSettingsService readerSettingsService;
    private final AiService aiService;
    private final AiFeatureService aiFeatureService;
    private final AiSessionService aiSessionService;
    private final CosService cosService;
    private final AiPromptBuilder promptBuilder;
    private final MailService mailService;
    private final FeedbackService feedbackService;
    private final BookNotebookLinkService notebookLinkService;
    private final BookCollectService bookCollectService;
    private final PdfMarkupService pdfMarkupService;
    private final ObjectMapper objectMapper;
    private final ExecutorService sseExecutor = Executors.newCachedThreadPool();

    /** 上传书籍 */
    @PostMapping("/upload")
    public R<WdBook> upload(@RequestAttribute Long userId,
                            @RequestParam("file") MultipartFile file) {
        return R.ok(bookService.uploadBook(userId, file));
    }

    /** 书架列表 */
    @GetMapping
    public R<List<WdBook>> list(@RequestAttribute Long userId) {
        return R.ok(bookService.listBooks(userId));
    }

    /** 书籍详情 */
    @GetMapping("/{bookId}")
    public R<WdBook> detail(@PathVariable Long bookId) {
        return R.ok(bookService.getBook(bookId));
    }

    /** 删除书籍 */
    @DeleteMapping("/{bookId}")
    public R<Void> delete(@RequestAttribute Long userId,
                          @PathVariable Long bookId) {
        bookService.deleteBook(bookId, userId);
        return R.ok();
    }

    /** 更新阅读进度 */
    @PostMapping("/{bookId}/progress")
    public R<Void> updateProgress(@PathVariable Long bookId,
                                  @RequestBody Map<String, Object> body) {
        Integer chapterIndex = body.get("chapterIndex") != null
                ? ((Number) body.get("chapterIndex")).intValue() : 0;
        String cfi = (String) body.getOrDefault("cfi", "");
        BigDecimal percent = body.get("percent") != null
                ? BigDecimal.valueOf(((Number) body.get("percent")).doubleValue()) : BigDecimal.ZERO;
        bookService.updateProgress(bookId, chapterIndex, cfi, percent);
        return R.ok();
    }

    /** 获取书籍文件（后端代理 COS 下载，返回文件流给前端） */
    @GetMapping("/{bookId}/file")
    public void getFile(@RequestAttribute Long userId,
                        @PathVariable Long bookId,
                        jakarta.servlet.http.HttpServletResponse response) throws IOException {
        WdBook book = bookService.getBook(bookId);
        if (!userId.equals(book.getUserId())) {
            response.sendError(403, "无权访问");
            return;
        }
        String cosKey = book.getFilePath();
        log.info("getFile: bookId={}, cosKey={}", bookId, cosKey);

        String contentType;
        switch (book.getFileType()) {
            case "pdf": contentType = "application/pdf"; break;
            case "txt": contentType = "text/plain"; break;
            default: contentType = "application/epub+zip";
        }

        try (java.io.InputStream in = cosService.download(cosKey);
             java.io.OutputStream os = response.getOutputStream()) {
            response.setContentType(contentType);
            long fileSize = cosService.getFileSize(cosKey);
            if (fileSize > 0) {
                response.setContentLengthLong(fileSize);
            }
            // 大文件流式转发，避免 nginx/客户端过早断开
            response.setHeader("Cache-Control", "private, max-age=60");
            byte[] buffer = new byte[64 * 1024];
            int bytesRead;
            long total = 0;
            while ((bytesRead = in.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
                total += bytesRead;
            }
            os.flush();
            log.info("getFile: 文件传输完成, bookId={}, 共 {} bytes", bookId, total);
        } catch (Exception e) {
            log.error("getFile: 下载失败, bookId={}", bookId, e);
            if (!response.isCommitted()) {
                response.sendError(500, "无法获取文件");
            }
        }
    }

    /** 大文件直链：返回 COS 预签名 URL，浏览器/PDF.js 直连拉取，避免经后端中转超时 */
    @GetMapping("/{bookId}/file-url")
    public R<Map<String, String>> getFileUrl(@RequestAttribute Long userId, @PathVariable Long bookId) {
        try {
            WdBook book = bookService.getBook(bookId);
            if (!userId.equals(book.getUserId())) {
                throw new BizException(403, "无权访问");
            }
            if (book.getFilePath() == null || book.getFilePath().isBlank()) {
                throw new BizException("书籍文件路径为空");
            }
            String url = cosService.generatePresignedUrl(book.getFilePath());
            Map<String, String> body = new java.util.HashMap<>();
            body.put("url", url);
            body.put("fileType", book.getFileType() != null ? book.getFileType() : "");
            return R.ok(body);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("getFileUrl 失败 bookId={}", bookId, e);
            throw new BizException("获取文件链接失败: " + e.getMessage());
        }
    }

    // ========== 笔记相关 ==========

    /** 新增笔记 */
    @PostMapping("/{bookId}/annotations")
    public R<WdAnnotation> addAnnotation(@RequestAttribute Long userId,
                                         @PathVariable Long bookId,
                                         @RequestBody AnnotationReq req) {
        WdAnnotation annotation = WdAnnotation.builder()
                .bookId(bookId)
                .chapterIndex(req.getChapterIndex() != null ? req.getChapterIndex() : 0)
                .cfiStart(req.getCfiStart())
                .cfiEnd(req.getCfiEnd())
                .textContent(req.getTextContent())
                .note(req.getNote())
                .color(req.getColor())
                .build();
        return R.ok(annotationService.addAnnotation(userId, annotation));
    }

    /** 更新笔记 */
    @PutMapping("/{bookId}/annotations/{id}")
    public R<WdAnnotation> updateAnnotation(@RequestAttribute Long userId,
                                            @PathVariable Long bookId,
                                            @PathVariable Long id,
                                            @RequestBody AnnotationReq req) {
        WdAnnotation annotation = WdAnnotation.builder()
                .id(id)
                .note(req.getNote())
                .color(req.getColor())
                .build();
        return R.ok(annotationService.updateAnnotation(userId, annotation));
    }

    /** 删除笔记 */
    @DeleteMapping("/{bookId}/annotations/{id}")
    public R<Void> deleteAnnotation(@RequestAttribute Long userId,
                                    @PathVariable Long bookId,
                                    @PathVariable Long id) {
        annotationService.deleteAnnotation(id, userId);
        return R.ok();
    }

    /** 获取某本书全部笔记 */
    @GetMapping("/{bookId}/annotations")
    public R<List<WdAnnotation>> listAnnotations(@RequestAttribute Long userId,
                                                  @PathVariable Long bookId) {
        return R.ok(annotationService.listByBook(bookId, userId));
    }

    /** 获取某章节笔记 */
    @GetMapping("/{bookId}/annotations/chapter/{chapterIndex}")
    public R<List<WdAnnotation>> listChapterAnnotations(@RequestAttribute Long userId,
                                                         @PathVariable Long bookId,
                                                         @PathVariable Integer chapterIndex) {
        return R.ok(annotationService.listByChapter(bookId, userId, chapterIndex));
    }

    // ========== PDF 批注（Canvas 覆盖层，与 EPUB CFI 笔记分离）==========

    @GetMapping("/{bookId}/pdf-markups")
    public R<List<WdPdfMarkup>> listPdfMarkups(@RequestAttribute Long userId, @PathVariable Long bookId) {
        return R.ok(pdfMarkupService.listByBook(userId, bookId));
    }

    @GetMapping("/{bookId}/pdf-markups/page/{pageNum}")
    public R<List<WdPdfMarkup>> listPdfMarkupsByPage(@RequestAttribute Long userId,
                                                     @PathVariable Long bookId,
                                                     @PathVariable Integer pageNum) {
        return R.ok(pdfMarkupService.listByPage(userId, bookId, pageNum));
    }

    @PostMapping("/{bookId}/pdf-markups")
    public R<WdPdfMarkup> addPdfMarkup(@RequestAttribute Long userId,
                                       @PathVariable Long bookId,
                                       @RequestBody PdfMarkupReq req) {
        WdPdfMarkup markup = WdPdfMarkup.builder()
                .pageNum(req.getPageNum())
                .markupType(req.getMarkupType())
                .geometryJson(req.getGeometryJson())
                .color(req.getColor())
                .noteText(req.getNoteText())
                .build();
        return R.ok(pdfMarkupService.add(userId, bookId, markup));
    }

    @PutMapping("/{bookId}/pdf-markups/{id}")
    public R<WdPdfMarkup> updatePdfMarkup(@RequestAttribute Long userId,
                                          @PathVariable Long bookId,
                                          @PathVariable Long id,
                                          @RequestBody PdfMarkupReq req) {
        WdPdfMarkup markup = WdPdfMarkup.builder()
                .geometryJson(req.getGeometryJson())
                .color(req.getColor())
                .noteText(req.getNoteText())
                .build();
        return R.ok(pdfMarkupService.update(userId, bookId, id, markup));
    }

    @DeleteMapping("/{bookId}/pdf-markups/{id}")
    public R<Void> deletePdfMarkup(@RequestAttribute Long userId,
                                   @PathVariable Long bookId,
                                   @PathVariable Long id) {
        pdfMarkupService.delete(userId, bookId, id);
        return R.ok();
    }

    // ========== 阅读设置 ==========

    /** 获取用户阅读设置 */
    @GetMapping("/reader-settings")
    public R<WdReaderSettings> getReaderSettings(@RequestAttribute Long userId) {
        return R.ok(readerSettingsService.getSettings(userId));
    }

    /** 保存用户阅读设置 */
    @PutMapping("/reader-settings")
    public R<WdReaderSettings> saveReaderSettings(@RequestAttribute Long userId,
                                                   @RequestBody WdReaderSettings settings) {
        return R.ok(readerSettingsService.saveSettings(userId, settings));
    }

    /** 问答注入模型的最近消息条数：3 轮 ≈ 解析1+2+3（每轮 user+assistant） */
    private static final int AI_CHAT_HISTORY_LIMIT = 6;

    /**
     * AI 流式对话。
     * <ul>
     *   <li>解析：写入 session 便于回查；调用模型时不带历史（每次独立）</li>
     *   <li>问答：写入 session，并把最近若干条（含解析）注入模型</li>
     * </ul>
     */
    @PostMapping(value = "/ai-chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter aiChat(@RequestAttribute Long userId,
                             @RequestBody Map<String, Object> body) {
        final long t0 = System.currentTimeMillis();
        String question = (String) body.get("question");
        String context = (String) body.getOrDefault("context", "");
        String locale = AiFeatureI18nSupport.normalizeLocale(
                (String) body.getOrDefault("locale", "zh-CN"));
        Long bookId = body.get("bookId") != null
                ? Long.valueOf(body.get("bookId").toString()) : null;
        String featureKey = (String) body.get("featureKey");
        @SuppressWarnings("unchecked")
        List<String> featureKeys = (List<String>) body.get("featureKeys");
        List<String> targetWords = CollectJsonSupport.normalizeTargetWords(body.get("targetWords"));
        Long sessionId = body.get("sessionId") != null
                ? Long.valueOf(body.get("sessionId").toString()) : null;

        final boolean isAnalyze = (featureKeys != null && !featureKeys.isEmpty())
                || (featureKey != null && !featureKey.isBlank());
        final boolean freeChat = !isAnalyze;
        final String requestKind = isAnalyze ? "解析" : "问答";

        // ★ 重要指标①：后台收到请求
        log.info("========== [耗时] ①收到{}请求 userId={} bookId={} features={} contextLen={} targets={} ==========",
                requestKind, userId, bookId,
                featureKeys != null && !featureKeys.isEmpty() ? featureKeys : featureKey,
                context != null ? context.length() : 0,
                targetWords);

        List<Message> historyMessages = Collections.emptyList();
        Long parentMessageId = null;
        String savedFeatureKey = featureKeys != null && !featureKeys.isEmpty()
                ? String.join(",", featureKeys) : featureKey;

        // 解析与问答都写入同一 session（便于回查）；仅问答把历史塞给模型
        long tSession = System.currentTimeMillis();
        if (sessionId == null) {
            WdAiSession session = aiSessionService.getOrCreateSession(userId, bookId);
            sessionId = session.getId();
        }
        log.info("[ai-chat] 会话就绪 sessionId={}, kind={}, 耗时={}ms",
                sessionId, requestKind, System.currentTimeMillis() - tSession);

        if (freeChat) {
            long tHistory = System.currentTimeMillis();
            historyMessages = buildChatHistory(sessionId, AI_CHAT_HISTORY_LIMIT);
            log.info("[ai-chat] 问答加载历史 historyMsgs={}, 耗时={}ms",
                    historyMessages.size(), System.currentTimeMillis() - tHistory);
        } else {
            log.info("[ai-chat] 解析模式：落库但不注入历史");
        }

        String userMessageContent;
        if (featureKeys != null && !featureKeys.isEmpty()) {
            userMessageContent = "【多选功能】" + context;
        } else if (isAnalyze) {
            userMessageContent = question != null ? question : ("【" + featureKey + "】" + context);
        } else {
            userMessageContent = question;
        }
        long tSaveUser = System.currentTimeMillis();
        WdAiMessage savedUserMsg = aiSessionService.saveMessage(
                sessionId, null, "user", userMessageContent, savedFeatureKey);
        parentMessageId = savedUserMsg.getId();
        log.info("[ai-chat] 保存用户消息 msgId={}, 耗时={}ms", parentMessageId, System.currentTimeMillis() - tSaveUser);

        long tTitle = System.currentTimeMillis();
        List<WdAiMessage> allMessages = aiSessionService.getMessages(sessionId);
        if (allMessages.size() == 1) {
            aiSessionService.updateSessionTitle(sessionId,
                    question != null ? question : (context != null ? context : ""));
            log.info("[ai-chat] 更新会话标题, 耗时={}ms", System.currentTimeMillis() - tTitle);
        }

        // 延长超时到 5 分钟，避免长回复被截断
        SseEmitter emitter = new SseEmitter(300_000L);

        long tPrompt = System.currentTimeMillis();
        String systemPrompt;
        String userPrompt;

        if (featureKeys != null && !featureKeys.isEmpty()) {
            // 多选功能模式（解析）
            systemPrompt = promptBuilder.buildMultiFeaturePrompt(locale, featureKeys, context);
            userPrompt = promptBuilder.buildMultiFeatureUserPrompt(locale, featureKeys, context);
        } else if (featureKey != null && !featureKey.isEmpty()) {
            // 单功能模式（解析）
            systemPrompt = promptBuilder.buildFeaturePrompt(locale, featureKey, context);
            userPrompt = promptBuilder.buildFeatureUserPrompt(locale, featureKey, context);
        } else {
            // 普通对话模式（问答，带记忆）
            systemPrompt = promptBuilder.buildReadingAssistantPrompt(locale, context);
            userPrompt = promptBuilder.buildReadingAssistantUserPrompt(locale, question);
        }

        final boolean collectEnabled = CollectJsonSupport.shouldCollect(featureKeys, featureKey);
        final boolean phoneticEnabled = CollectJsonSupport.shouldRequireWordPhonetics(featureKeys, featureKey);
        if (featureKeys != null && !featureKeys.isEmpty() || (featureKey != null && !featureKey.isBlank())) {
            systemPrompt = systemPrompt + CollectJsonSupport.featureScopeInstruction(locale, featureKeys, featureKey);
        }
        if (phoneticEnabled) {
            if (!targetWords.isEmpty()) {
                systemPrompt = systemPrompt + CollectJsonSupport.phoneticFormatInstruction(locale);
            } else {
                systemPrompt = systemPrompt + CollectJsonSupport.phoneticDisplayInstruction(locale);
            }
        }
        if (!targetWords.isEmpty()) {
            systemPrompt = systemPrompt + CollectJsonSupport.targetWordsInstruction(locale, targetWords);
        }
        if (collectEnabled) {
            systemPrompt = systemPrompt + CollectJsonSupport.collectInstruction(locale);
        }
        // 范围约束仅用于自由问答；解析选中文本本身即语言学习任务，不必重复长段约束
        if (freeChat) {
            systemPrompt = systemPrompt + promptBuilder.languageLearningScopeInstruction(locale);
        }
        log.info("[ai-chat] 拼装提示词完成 sysChars={}, userChars={}, collect={}, phonetic={}, history={}, 耗时={}ms",
                systemPrompt != null ? systemPrompt.length() : 0,
                userPrompt != null ? userPrompt.length() : 0,
                collectEnabled, phoneticEnabled, historyMessages.size(),
                System.currentTimeMillis() - tPrompt);

        final String finalSystemPrompt = systemPrompt;
        final String finalUserPrompt = userPrompt;
        final List<Message> finalHistory = historyMessages;

        final Long finalSessionId = sessionId;
        final Long finalParentMessageId = parentMessageId;
        final String finalFeatureKey = savedFeatureKey;
        final boolean persistSession = true;
        final StringBuilder fullResponseText = new StringBuilder();

        // 原始输入：优先选中上下文，否则用提问（用于成本字数统计）
        final String rawInput = (context != null && !context.isBlank()) ? context
                : (question != null ? question : "");
        final String callScene = isAnalyze ? "feature" : "reading_chat";

        final long prepMs = System.currentTimeMillis() - t0;
        log.info("[ai-chat] 同步准备完成（即将异步调模型）prepMs={}, scene={}, persistSession={}",
                prepMs, callScene, persistSession);

        // 与单词流式解析一致：异步订阅，避免阻塞 MVC 线程导致 SSE 缓冲
        final long asyncSubmitAt = System.currentTimeMillis();
        sseExecutor.execute(() -> {
            final long tAsyncStart = System.currentTimeMillis();
            log.info("[ai-chat] 异步线程开始 queueWaitMs={}", tAsyncStart - asyncSubmitAt);
            final long[] firstChunkAt = {0L};
            final int[] chunkCount = {0};
            try {
                long tSubscribe = System.currentTimeMillis();
                aiService.chatStream(userId, callScene, rawInput,
                                finalSystemPrompt, finalUserPrompt, finalHistory)
                        .subscribe(
                                content -> {
                                    if (firstChunkAt[0] == 0L && content != null && !content.isEmpty()) {
                                        firstChunkAt[0] = System.currentTimeMillis();
                                        // ★ 重要指标②：AI 流式首字到达（相对①收到请求）
                                        log.info("========== [耗时] ②AI流式首字到达 kind={} 距收到请求={}ms prepMs={} queueWaitMs={} 首字预览={} ==========",
                                                requestKind,
                                                firstChunkAt[0] - t0,
                                                prepMs,
                                                tAsyncStart - asyncSubmitAt,
                                                content.length() > 20 ? content.substring(0, 20) + "…" : content);
                                    }
                                    chunkCount[0]++;
                                    synchronized (emitter) {
                                        try {
                                            String prevFull = fullResponseText.toString();
                                            fullResponseText.append(content);
                                            String displayChunk = content;
                                            if (collectEnabled) {
                                                String prevVisible = CollectJsonSupport.stripForDisplay(prevFull);
                                                String visible = CollectJsonSupport.stripForDisplay(fullResponseText.toString());
                                                displayChunk = visible.length() > prevVisible.length()
                                                        ? visible.substring(prevVisible.length())
                                                        : "";
                                            }
                                            if (!displayChunk.isEmpty()) {
                                                emitter.send(SseEmitter.event()
                                                        .name("chunk")
                                                        .data(displayChunk, MediaType.TEXT_PLAIN));
                                            }
                                        } catch (IOException e) {
                                            emitter.completeWithError(e);
                                        }
                                    }
                                },
                                error -> {
                                    log.error("[ai-chat] 流式失败 totalMs={}, chunks={}, err={}",
                                            System.currentTimeMillis() - t0, chunkCount[0], error.getMessage(), error);
                                    String partialResponse = fullResponseText.toString();
                                    if (persistSession && finalSessionId != null && !partialResponse.isEmpty()) {
                                        try {
                                            aiSessionService.saveMessage(finalSessionId, finalParentMessageId,
                                                    "assistant", partialResponse, finalFeatureKey);
                                        } catch (Exception ignored) {}
                                    }
                                    synchronized (emitter) {
                                        try {
                                            emitter.send(SseEmitter.event()
                                                    .name("error")
                                                    .data("AI 请求失败: " + error.getMessage(), MediaType.TEXT_PLAIN));
                                        } catch (IOException ex) { /* ignore */ }
                                        emitter.complete();
                                    }
                                },
                                () -> {
                                    long tDone = System.currentTimeMillis();
                                    long streamMs = tDone - tAsyncStart;
                                    long ttfb = firstChunkAt[0] > 0 ? firstChunkAt[0] - tAsyncStart : -1;
                                    long genAfterFirst = firstChunkAt[0] > 0 ? tDone - firstChunkAt[0] : streamMs;
                                    log.info("[ai-chat] 模型流结束 streamMs={}, TTFB={}ms, afterFirstChunk={}ms, chunks={}, respChars={}",
                                            streamMs, ttfb, genAfterFirst, chunkCount[0], fullResponseText.length());
                                    synchronized (emitter) {
                                        try {
                                            String fullResponse = fullResponseText.toString();
                                            if (persistSession && finalSessionId != null && !fullResponse.isEmpty()) {
                                                long tPersist = System.currentTimeMillis();
                                                aiSessionService.saveMessage(finalSessionId, finalParentMessageId,
                                                        "assistant", fullResponse, finalFeatureKey);
                                                log.info("[ai-chat] 保存助手消息 耗时={}ms", System.currentTimeMillis() - tPersist);
                                            }
                                            if (collectEnabled) {
                                                long tCollect = System.currentTimeMillis();
                                                List<CollectEntryDTO> entries = CollectJsonSupport.parseEntriesEnriched(fullResponse, objectMapper);
                                                log.info("[ai-chat] 解析 COLLECT_JSON entries={}, 耗时={}ms",
                                                        entries.size(), System.currentTimeMillis() - tCollect);
                                                if (!entries.isEmpty()) {
                                                    emitter.send(SseEmitter.event()
                                                            .name("collect-entries")
                                                            .data(objectMapper.writeValueAsString(Map.of("entries", entries)), MediaType.APPLICATION_JSON));
                                                }
                                            }
                                            String donePayload = persistSession && finalSessionId != null
                                                    ? "{\"sessionId\":" + finalSessionId + "}"
                                                    : "{\"sessionId\":null}";
                                            emitter.send(SseEmitter.event()
                                                    .name("done")
                                                    .data(donePayload, MediaType.APPLICATION_JSON));
                                            log.info("[ai-chat] 全链路完成 totalMs={}, prepMs={}, streamMs={}, TTFB={}ms",
                                                    System.currentTimeMillis() - t0, prepMs, streamMs, ttfb);
                                        } catch (IOException e) { /* ignore */ }
                                        emitter.complete();
                                    }
                                }
                        );
                log.info("[ai-chat] subscribe 已挂上 耗时={}ms (不含流式等待)", System.currentTimeMillis() - tSubscribe);
            } catch (Exception e) {
                log.error("[ai-chat] subscribe 失败 totalMs={}", System.currentTimeMillis() - t0, e);
                synchronized (emitter) {
                    try {
                        emitter.send(SseEmitter.event()
                                .name("error")
                                .data("AI 请求失败: " + e.getMessage(), MediaType.TEXT_PLAIN));
                    } catch (IOException ignored) {}
                    emitter.completeWithError(e);
                }
            }
        });

        log.info("[ai-chat] 已提交异步任务并返回 SSE emitter, syncReturnMs={}", System.currentTimeMillis() - t0);
        return emitter;
    }

    /**
     * 将会话最近消息转为模型历史（时间正序）。
     * 含解析记录（问答需要解析1+2+3作上下文）；assistant 去掉 COLLECT_JSON。
     */
    private List<Message> buildChatHistory(Long sessionId, int limit) {
        List<WdAiMessage> recent = aiSessionService.getRecentMessages(sessionId, limit);
        if (recent == null || recent.isEmpty()) {
            return Collections.emptyList();
        }
        Collections.reverse(recent);
        List<Message> out = new ArrayList<>(recent.size());
        for (WdAiMessage msg : recent) {
            if (msg == null || msg.getContent() == null || msg.getContent().isBlank()) {
                continue;
            }
            String role = msg.getRole() != null ? msg.getRole().trim().toLowerCase() : "";
            if ("user".equals(role)) {
                out.add(new UserMessage(msg.getContent()));
            } else if ("assistant".equals(role)) {
                String content = CollectJsonSupport.stripForDisplay(msg.getContent());
                if (!content.isBlank()) {
                    out.add(new AssistantMessage(content));
                }
            }
        }
        return out;
    }

    @GetMapping("/{bookId}/notebook-links")
    public R<List<BookNotebookLinkVO>> listNotebookLinks(@RequestAttribute Long userId, @PathVariable Long bookId) {
        return R.ok(notebookLinkService.listLinks(userId, bookId));
    }

    @PostMapping("/{bookId}/words/collect")
    public R<BookCollectResultVO> collectWords(@RequestAttribute Long userId, @PathVariable Long bookId,
                                               @RequestBody BookCollectReq req) {
        return R.ok(bookCollectService.collect(userId, bookId, req));
    }

    /** 获取或创建会话 */
    @PostMapping("/ai-sessions")
    public R<WdAiSession> createSession(@RequestAttribute Long userId,
                                         @RequestBody Map<String, Object> body) {
        Long bookId = body.get("bookId") != null
                ? Long.valueOf(body.get("bookId").toString()) : null;
        return R.ok(aiSessionService.getOrCreateSession(userId, bookId));
    }

    /** 获取某书的所有会话列表 */
    @GetMapping("/ai-sessions")
    public R<List<WdAiSession>> listSessions(@RequestAttribute Long userId,
                                              @RequestParam(required = false) Long bookId) {
        return R.ok(aiSessionService.listSessions(userId, bookId));
    }

    /** 获取某个会话的所有消息 */
    @GetMapping("/ai-sessions/{sessionId}/messages")
    public R<List<WdAiMessage>> getMessages(@RequestAttribute Long userId,
                                             @PathVariable Long sessionId) {
        return R.ok(aiSessionService.getMessages(sessionId));
    }

    /** 分页获取会话消息（从旧到新），用于前端滚动加载历史 */
    @GetMapping("/ai-sessions/{sessionId}/messages/paged")
    public R<Map<String, Object>> getMessagesPaged(@RequestAttribute Long userId,
                                                    @PathVariable Long sessionId,
                                                    @RequestParam(defaultValue = "0") int offset,
                                                    @RequestParam(defaultValue = "10") int limit) {
        List<WdAiMessage> messages = aiSessionService.getMessagesPaged(sessionId, offset, limit);
        int total = aiSessionService.countMessages(sessionId);
        Map<String, Object> result = new java.util.HashMap<>();
        result.put("messages", messages);
        result.put("total", total);
        result.put("offset", offset);
        result.put("limit", limit);
        result.put("hasMore", (offset + limit) < total);
        return R.ok(result);
    }

    /** 重命名会话 */
    @PutMapping("/ai-sessions/{sessionId}")
    public R<Void> renameSession(@RequestAttribute Long userId,
                                  @PathVariable Long sessionId,
                                  @RequestBody Map<String, Object> body) {
        Object titleObj = body != null ? body.get("title") : null;
        String title = titleObj != null ? titleObj.toString() : null;
        aiSessionService.renameSession(sessionId, userId, title);
        return R.ok();
    }

    /** 删除会话 */
    @DeleteMapping("/ai-sessions/{sessionId}")
    public R<Void> deleteSession(@RequestAttribute Long userId,
                                  @PathVariable Long sessionId) {
        aiSessionService.deleteSession(sessionId, userId);
        return R.ok();
    }

    /** 获取 AI 阅读功能列表（含用户偏好） */
    @GetMapping("/ai-features")
    public R<List<SysAiFeature>> listAiFeatures(
            @RequestParam(value = "locale", required = false, defaultValue = "zh-CN") String locale) {
        return R.ok(aiFeatureService.listActiveFeatures(locale));
    }

    /** 获取用户 AI 功能偏好（全量功能 + 用户勾选的功能key） */
    @GetMapping("/ai-features/preferences")
    public R<AiFeaturePreferenceVO> getAiFeaturePreferences(
            @RequestAttribute Long userId,
            @RequestParam(value = "locale", required = false, defaultValue = "zh-CN") String locale) {
        return R.ok(aiFeatureService.getPreferences(userId, locale));
    }

    /** 保存用户 AI 功能偏好（含排序） */
    @PutMapping("/ai-features/preferences")
    public R<Void> saveAiFeaturePreferences(@RequestAttribute Long userId,
                                            @RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<String> featureKeys = (List<String>) body.get("featureKeys");
        @SuppressWarnings("unchecked")
        List<String> featureOrder = (List<String>) body.get("featureOrder");
        aiFeatureService.savePreferences(userId, featureKeys, featureOrder);
        return R.ok();
    }

    /** 用户提交问题反馈 */
    @PostMapping("/report-issue")
    public R<?> reportIssue(@RequestAttribute Long userId,
                            @RequestBody Map<String, String> body) {
        String username = body.getOrDefault("username", "用户" + userId);
        String reporterEmail = body.getOrDefault("reporterEmail", "");
        String subject = body.getOrDefault("subject", "");
        String description = body.getOrDefault("description", "");
        String errorDetail = body.getOrDefault("errorDetail", "");
        feedbackService.saveFeedback(userId, username, reporterEmail, subject, description, errorDetail);
        return R.ok();
    }
}
