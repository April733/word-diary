package spring.nexusfuture.word_diary.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
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

    /** 获取书籍文件（后端代理 COS 下载，返回文件流给前端 epub.js） */
    @GetMapping("/{bookId}/file")
    public void getFile(@PathVariable Long bookId,
                        jakarta.servlet.http.HttpServletResponse response) throws IOException {
        WdBook book = bookService.getBook(bookId);
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
            byte[] buffer = new byte[8192];
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
            response.sendError(500, "无法获取文件");
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

    /** AI 流式对话（支持会话记忆） */
    @PostMapping(value = "/ai-chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter aiChat(@RequestAttribute Long userId,
                             @RequestBody Map<String, Object> body) {
        String question = (String) body.get("question");
        String context = (String) body.getOrDefault("context", "");
        String locale = (String) body.getOrDefault("locale", "zh-CN");
        Long bookId = body.get("bookId") != null
                ? Long.valueOf(body.get("bookId").toString()) : null;
        String featureKey = (String) body.get("featureKey");
        @SuppressWarnings("unchecked")
        List<String> featureKeys = (List<String>) body.get("featureKeys");
        Long sessionId = body.get("sessionId") != null
                ? Long.valueOf(body.get("sessionId").toString()) : null;

        // 获取或创建会话
        if (sessionId == null) {
            WdAiSession session = aiSessionService.getOrCreateSession(userId, bookId);
            sessionId = session.getId();
        }

        // 判断用户输入
        String userMessageContent;
        if (featureKeys != null && !featureKeys.isEmpty()) {
            userMessageContent = "【多选功能】" + context;
        } else if (featureKey != null && !featureKey.isEmpty()) {
            userMessageContent = question;
        } else {
            userMessageContent = question;
        }

        // 保存用户消息
        String savedFeatureKey = featureKeys != null && !featureKeys.isEmpty()
                ? String.join(",", featureKeys) : featureKey;
        WdAiMessage savedUserMsg = aiSessionService.saveMessage(sessionId, null, "user", userMessageContent, savedFeatureKey);
        final Long parentMessageId = savedUserMsg.getId();

        // 如果是会话第一条消息，更新标题
        List<WdAiMessage> allMessages = aiSessionService.getMessages(sessionId);
        if (allMessages.size() == 1) {
            aiSessionService.updateSessionTitle(sessionId,
                    question != null ? question : (context != null ? context : ""));
        }

        // 获取最近历史消息拼入 system prompt
        List<WdAiMessage> historyMessages = aiSessionService.getRecentMessages(sessionId, 5);
        String historyContext = buildHistoryContext(historyMessages);

        // 延长超时到 5 分钟，避免长回复被截断
        SseEmitter emitter = new SseEmitter(300_000L);

        String systemPrompt;
        String userPrompt;

        if (featureKeys != null && !featureKeys.isEmpty()) {
            // 多选功能模式
            systemPrompt = promptBuilder.buildMultiFeaturePrompt(locale, featureKeys, context) + historyContext;
            userPrompt = promptBuilder.buildMultiFeatureUserPrompt(locale, featureKeys, context);
        } else if (featureKey != null && !featureKey.isEmpty()) {
            // 单功能模式
            systemPrompt = promptBuilder.buildFeaturePrompt(locale, featureKey, context) + historyContext;
            userPrompt = promptBuilder.buildFeatureUserPrompt(locale, featureKey, context);
        } else {
            // 普通对话模式
            systemPrompt = promptBuilder.buildReadingAssistantPrompt(locale, context) + historyContext;
            userPrompt = promptBuilder.buildReadingAssistantUserPrompt(locale, question);
        }

        final boolean collectEnabled = CollectJsonSupport.shouldCollect(featureKeys, featureKey);
        if (collectEnabled) {
            systemPrompt = systemPrompt + CollectJsonSupport.collectInstruction();
        }
        final String finalSystemPrompt = systemPrompt;
        final String finalUserPrompt = userPrompt;

        final Long finalSessionId = sessionId;
        final String finalFeatureKey = savedFeatureKey;
        final StringBuilder fullResponseText = new StringBuilder();

        // 原始输入：优先选中上下文，否则用提问（用于成本字数统计）
        final String rawInput = (context != null && !context.isBlank()) ? context
                : (question != null ? question : "");
        final String callScene = (featureKeys != null && !featureKeys.isEmpty()) || (featureKey != null && !featureKey.isEmpty())
                ? "feature"
                : "reading_chat";

        // 与单词流式解析一致：异步订阅，避免阻塞 MVC 线程导致 SSE 缓冲
        sseExecutor.execute(() -> {
            try {
                aiService.chatStream(userId, callScene, rawInput, finalSystemPrompt, finalUserPrompt)
                        .subscribe(
                                content -> {
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
                                    log.error("AI chat error", error);
                                    String partialResponse = fullResponseText.toString();
                                    if (!partialResponse.isEmpty()) {
                                        try {
                                            aiSessionService.saveMessage(finalSessionId, parentMessageId, "assistant", partialResponse, finalFeatureKey);
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
                                    synchronized (emitter) {
                                        try {
                                            String fullResponse = fullResponseText.toString();
                                            if (!fullResponse.isEmpty()) {
                                                aiSessionService.saveMessage(finalSessionId, parentMessageId, "assistant", fullResponse, finalFeatureKey);
                                            }
                                            if (collectEnabled) {
                                                List<CollectEntryDTO> entries = CollectJsonSupport.parseEntries(fullResponse, objectMapper);
                                                if (!entries.isEmpty()) {
                                                    emitter.send(SseEmitter.event()
                                                            .name("collect-entries")
                                                            .data(objectMapper.writeValueAsString(Map.of("entries", entries)), MediaType.APPLICATION_JSON));
                                                }
                                            }
                                            emitter.send(SseEmitter.event()
                                                    .name("done")
                                                    .data("{\"sessionId\":" + finalSessionId + "}", MediaType.APPLICATION_JSON));
                                        } catch (IOException e) { /* ignore */ }
                                        emitter.complete();
                                    }
                                }
                        );
            } catch (Exception e) {
                log.error("AI chat subscribe failed", e);
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

        return emitter;
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

    /** 拼接历史消息为 AI 上下文 */
    private String buildHistoryContext(List<WdAiMessage> historyMessages) {
        if (historyMessages == null || historyMessages.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("\n\n--- 对话历史 ---\n");
        for (WdAiMessage msg : historyMessages) {
            String roleLabel = "user".equals(msg.getRole()) ? "用户" : "AI";
            // 历史拼进 prompt 时去掉收录 JSON，避免污染上下文
            sb.append(roleLabel).append(": ")
                    .append(CollectJsonSupport.stripForDisplay(msg.getContent())).append("\n");
        }
        sb.append("--- 对话历史结束 ---\n");
        sb.append("请基于以上对话历史，结合用户最新的问题给出回答。");
        return sb.toString();
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

    /** 删除会话 */
    @DeleteMapping("/ai-sessions/{sessionId}")
    public R<Void> deleteSession(@RequestAttribute Long userId,
                                  @PathVariable Long sessionId) {
        aiSessionService.deleteSession(sessionId, userId);
        return R.ok();
    }

    /** 获取 AI 阅读功能列表（含用户偏好） */
    @GetMapping("/ai-features")
    public R<List<SysAiFeature>> listAiFeatures() {
        return R.ok(aiFeatureService.listActiveFeatures());
    }

    /** 获取用户 AI 功能偏好（全量功能 + 用户勾选的功能key） */
    @GetMapping("/ai-features/preferences")
    public R<AiFeaturePreferenceVO> getAiFeaturePreferences(@RequestAttribute Long userId) {
        return R.ok(aiFeatureService.getPreferences(userId));
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
