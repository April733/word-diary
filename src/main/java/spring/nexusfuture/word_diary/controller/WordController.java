package spring.nexusfuture.word_diary.controller;

import org.springframework.web.bind.annotation.*;
import spring.nexusfuture.word_diary.common.R;
import spring.nexusfuture.word_diary.dto.WordAddReq;
import spring.nexusfuture.word_diary.dto.WordVO;
import spring.nexusfuture.word_diary.service.WordService;
import java.util.List;

@RestController
@RequestMapping("/api/notebooks/{notebookId}/words")
public class WordController {

    private final WordService wordService;

    public WordController(WordService wordService) {
        this.wordService = wordService;
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

    @DeleteMapping("/{wordId}")
    public R<Void> delete(@RequestAttribute Long userId, @PathVariable Long notebookId, @PathVariable Long wordId) {
        wordService.delete(userId, wordId);
        return R.ok();
    }

    @PutMapping("/{wordId}")
    public R<Void> update(@RequestAttribute Long userId, @PathVariable Long notebookId, @PathVariable Long wordId, @RequestBody WordAddReq req) {
        wordService.updateWord(userId, wordId, req);
        return R.ok();
    }
}
