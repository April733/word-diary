package spring.nexusfuture.word_diary.controller;

import org.springframework.web.bind.annotation.*;
import spring.nexusfuture.word_diary.common.R;
import spring.nexusfuture.word_diary.dto.*;
import spring.nexusfuture.word_diary.service.StudyService;
import java.util.List;

@RestController
@RequestMapping("/api/study")
public class StudyController {

    private final StudyService studyService;

    public StudyController(StudyService studyService) {
        this.studyService = studyService;
    }

    @GetMapping("/new-words")
    public R<List<StudyTaskVO>> getNewWords(@RequestAttribute Long userId) {
        return R.ok(studyService.getNewWords(userId));
    }

    @GetMapping("/today-review")
    public R<List<StudyTaskVO>> getTodayReview(@RequestAttribute Long userId) {
        return R.ok(studyService.getTodayReview(userId));
    }

    @PostMapping("/first-learn/{planId}")
    public R<Void> firstLearn(@RequestAttribute Long userId, @PathVariable Long planId, @RequestBody StudyReq req) {
        studyService.firstLearn(userId, planId, req.getResult());
        return R.ok();
    }

    @PostMapping("/review/{planId}")
    public R<Void> review(@RequestAttribute Long userId, @PathVariable Long planId, @RequestBody StudyReq req) {
        studyService.review(userId, planId, req.getResult());
        return R.ok();
    }

    @GetMapping("/records")
    public R<List<StudyRecordVO>> getRecords(@RequestAttribute Long userId) {
        return R.ok(studyService.getRecords(userId));
    }

    @GetMapping("/stats")
    public R<StatsVO> getStats(@RequestAttribute Long userId) {
        return R.ok(studyService.getStats(userId));
    }
}
