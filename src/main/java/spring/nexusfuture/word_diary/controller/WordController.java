package spring.nexusfuture.word_diary.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import spring.nexusfuture.word_diary.common.R;
import spring.nexusfuture.word_diary.dto.AiParseReq;
import spring.nexusfuture.word_diary.dto.AiParseResult;
import spring.nexusfuture.word_diary.dto.BatchAddReq;
import spring.nexusfuture.word_diary.dto.WordAddReq;
import spring.nexusfuture.word_diary.dto.WordVO;
import spring.nexusfuture.word_diary.service.AiService;
import spring.nexusfuture.word_diary.service.WordService;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j

@RestController
@RequestMapping("/api/notebooks/{notebookId}/words")
public class WordController {

    private final WordService wordService;
    private final AiService aiService;
    private final ExecutorService sseExecutor = Executors.newCachedThreadPool();

    public WordController(WordService wordService, AiService aiService) {
        this.wordService = wordService;
        this.aiService = aiService;
    }

    @PostMapping
    public R<Void> add(@RequestAttribute Long userId, @PathVariable Long notebookId, @RequestBody WordAddReq req) {
        wordService.addWord(userId, notebookId, req);
        return R.ok();
    }

    @GetMapping
    public R<List<WordVO>> list(@RequestAttribute Long userId, @PathVariable Long notebookId) {
        return R.ok(wordService.listByNotebook(userId, notebookId));
    }

    /** 垃圾桶（软删后保留 7 天） */
    @GetMapping("/trash")
    public R<List<WordVO>> trash(@RequestAttribute Long userId, @PathVariable Long notebookId) {
        return R.ok(wordService.listTrash(userId, notebookId));
    }

    @DeleteMapping("/{wordId}")
    public R<Void> delete(@RequestAttribute Long userId, @PathVariable Long notebookId, @PathVariable Long wordId) {
        wordService.delete(userId, wordId);
        return R.ok();
    }

    @PostMapping("/{wordId}/restore")
    public R<Void> restore(@RequestAttribute Long userId, @PathVariable Long notebookId, @PathVariable Long wordId) {
        wordService.restore(userId, notebookId, wordId);
        return R.ok();
    }

    @PutMapping("/{wordId}")
    public R<Void> update(@RequestAttribute Long userId, @PathVariable Long notebookId, @PathVariable Long wordId, @RequestBody WordAddReq req) {
        wordService.updateWord(userId, wordId, req);
        return R.ok();
    }

    @PostMapping("/ai-parse")
    public R<List<AiParseResult>> aiParse(@RequestAttribute Long userId,
                                          @PathVariable Long notebookId, @RequestBody AiParseReq req) {
        if (req.getWords() == null || req.getWords().size() > 20) {
            return R.fail("单次最多生成 20 个单词");
        }
        List<AiParseResult> results = aiService.parseWords(userId, req.getWords());
        return R.ok(results);
    }

    @PostMapping(value = "/ai-parse-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter aiParseStream(@RequestAttribute Long userId,
                                    @PathVariable Long notebookId, @RequestBody AiParseReq req) {
        if (req.getWords() == null || req.getWords().size() > 20) {
            SseEmitter emitter = new SseEmitter(0L);
            emitter.completeWithError(new RuntimeException("单次最多生成 20 个单词"));
            return emitter;
        }
        // 服务端二次校验：必须是英文
        for (String word : req.getWords()) {
            if (word == null || !word.matches("[A-Za-z\\s\\-'.]+")) {
                SseEmitter emitter = new SseEmitter(0L);
                emitter.completeWithError(new RuntimeException("包含非英文内容: " + word));
                return emitter;
            }
        }

        SseEmitter emitter = new SseEmitter(120000L);
        sseExecutor.execute(() -> {
            try {
                aiService.parseWordsStream(userId, req.getWords())
                        .subscribe(
                                chunk -> {
                                    try {
                                        log.info("[SSE-OUT] 转发chunk, len={}, content: {}", chunk.length(), chunk);
                                        emitter.send(SseEmitter.event()
                                                .name("chunk")
                                                .data(chunk));
                                    } catch (Exception e) {
                                        emitter.completeWithError(e);
                                    }
                                },
                                error -> {
                                    try {
                                        log.info("[SSE-OUT] 转发 error: {}", error.getMessage());
                                        emitter.send(SseEmitter.event()
                                                .name("error")
                                                .data(error.getMessage()));
                                    } catch (Exception ignored) {}
                                    emitter.completeWithError(error);
                                },
                                () -> {
                                    try {
                                        log.info("[SSE-OUT] 转发 done");
                                        emitter.send(SseEmitter.event()
                                                .name("done")
                                                .data("completed"));
                                    } catch (Exception ignored) {}
                                    emitter.complete();
                                }
                        );
            } catch (Exception e) {
                try {
                    emitter.send(SseEmitter.event()
                            .name("error")
                            .data(e.getMessage()));
                } catch (Exception ignored) {}
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    @PostMapping("/batch-add")
    public R<Void> batchAdd(@RequestAttribute Long userId,
                            @PathVariable Long notebookId, @RequestBody BatchAddReq req) {
        if (req.getWords() == null || req.getWords().size() > 50) {
            return R.fail("单次最多导入 50 个单词");
        }
        wordService.batchAddWords(userId, notebookId, req.getWords());
        return R.ok();
    }

    @PostMapping("/ai-fill-gaps")
    public R<Map<String, Integer>> fillGaps(@RequestAttribute Long userId,
                                            @PathVariable Long notebookId,
                                            @RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Number> ids = (List<Number>) body.get("wordIds");
        List<Long> wordIds = ids != null ? ids.stream().map(Number::longValue).toList() : List.of();
        int updated = wordService.fillGaps(userId, notebookId, wordIds);
        return R.ok(Map.of("updated", updated));
    }
}
