package spring.nexusfuture.word_diary.controller;

import org.springframework.web.bind.annotation.*;
import spring.nexusfuture.word_diary.common.BizException;
import spring.nexusfuture.word_diary.common.R;
import spring.nexusfuture.word_diary.dto.NotebookStudyEnabledReq;
import spring.nexusfuture.word_diary.dto.WordBookAddReq;
import spring.nexusfuture.word_diary.dto.WordBookVO;
import spring.nexusfuture.word_diary.service.WordBookService;

import java.util.List;

@RestController
@RequestMapping("/api/notebooks")
public class WordBookController {

    private final WordBookService bookService;

    public WordBookController(WordBookService bookService) {
        this.bookService = bookService;
    }

    @PostMapping
    public R<WordBookVO> create(@RequestAttribute Long userId, @RequestBody WordBookAddReq req) {
        return R.ok(bookService.create(userId, req));
    }

    @GetMapping
    public R<List<WordBookVO>> list(@RequestAttribute Long userId) {
        return R.ok(bookService.listByUser(userId));
    }

    @PutMapping("/{bookId}")
    public R<Void> update(@RequestAttribute Long userId, @PathVariable Long bookId, @RequestBody WordBookAddReq req) {
        bookService.update(userId, bookId, req);
        return R.ok();
    }

    @PutMapping("/{bookId}/study-enabled")
    public R<Void> setStudyEnabled(@RequestAttribute Long userId, @PathVariable Long bookId,
                                   @RequestBody NotebookStudyEnabledReq req) {
        if (req == null || req.getEnabled() == null) {
            throw new BizException("请指定 enabled");
        }
        bookService.setStudyEnabled(userId, bookId, req.getEnabled());
        return R.ok();
    }

    @DeleteMapping("/{bookId}")
    public R<Void> delete(@RequestAttribute Long userId, @PathVariable Long bookId) {
        bookService.delete(userId, bookId);
        return R.ok();
    }
}
