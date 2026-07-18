package spring.nexusfuture.word_diary.controller;

import org.springframework.web.bind.annotation.*;
import spring.nexusfuture.word_diary.common.R;
import spring.nexusfuture.word_diary.dto.*;
import spring.nexusfuture.word_diary.entity.WdLearnPlan;
import spring.nexusfuture.word_diary.entity.WdWord;
import spring.nexusfuture.word_diary.mapper.WdLearnPlanMapper;
import spring.nexusfuture.word_diary.mapper.WdWordMapper;
import spring.nexusfuture.word_diary.service.StudyService;
import spring.nexusfuture.word_diary.service.StudySessionService;
import spring.nexusfuture.word_diary.service.StudyBrowseService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/study")
public class StudyController {

    private final StudyService studyService;
    private final StudySessionService sessionService;
    private final StudyBrowseService browseService;
    private final WdLearnPlanMapper planMapper;
    private final WdWordMapper wordMapper;

    public StudyController(StudyService studyService, StudySessionService sessionService,
                           StudyBrowseService browseService,
                           WdLearnPlanMapper planMapper, WdWordMapper wordMapper) {
        this.studyService = studyService;
        this.sessionService = sessionService;
        this.browseService = browseService;
        this.planMapper = planMapper;
        this.wordMapper = wordMapper;
    }

    @GetMapping("/today")
    public R<StudyTodayVO> getToday(@RequestAttribute Long userId) {
        return R.ok(sessionService.getToday(userId));
    }

    @PostMapping("/session/open")
    public R<StudySessionVO> openSession(@RequestAttribute Long userId) {
        return R.ok(sessionService.openSession(userId));
    }

    @GetMapping("/session/queue")
    public R<List<StudyTaskVO>> sessionQueue(@RequestAttribute Long userId,
                                             @RequestParam(required = false) String phase) {
        return R.ok(sessionService.listTodayQueue(userId, phase));
    }

    @PostMapping("/answer")
    public R<StudyAnswerVO> submitAnswer(@RequestAttribute Long userId, @RequestBody StudyAnswerReq req) {
        return R.ok(sessionService.submitAnswer(userId, req));
    }

    @GetMapping("/settings")
    public R<StudySettingsVO> getSettings(@RequestAttribute Long userId) {
        return R.ok(sessionService.getSettings(userId));
    }

    @PutMapping("/settings")
    public R<Void> updateSettings(@RequestAttribute Long userId, @RequestBody StudySettingsReq req) {
        sessionService.updateSettings(userId, req);
        return R.ok();
    }

    @GetMapping("/browse/words")
    public R<StudyBrowsePageVO> browseWords(@RequestAttribute Long userId,
                                            @RequestParam(required = false) Long notebookId,
                                            @RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "20") int pageSize) {
        return R.ok(browseService.listWords(userId, notebookId, page, pageSize));
    }

    @PostMapping("/browse/answer")
    public R<Void> browseAnswer(@RequestAttribute Long userId, @RequestBody StudyBrowseAnswerReq req) {
        browseService.submitAnswer(userId, req);
        return R.ok();
    }

    /** @deprecated 使用 /session/open */
    @GetMapping("/new-words")
    public R<List<StudyTaskVO>> getNewWords(@RequestAttribute Long userId) {
        return R.ok(studyService.getNewWords(userId));
    }

    /** @deprecated 使用 /session/open */
    @GetMapping("/today-review")
    public R<List<StudyTaskVO>> getTodayReview(@RequestAttribute Long userId) {
        return R.ok(studyService.getTodayReview(userId));
    }

    @GetMapping("/today-completed")
    public R<List<StudyTaskVO>> getTodayCompleted(@RequestAttribute Long userId) {
        return R.ok(studyService.getTodayCompleted(userId));
    }

    /** @deprecated 使用 /answer */
    @PostMapping("/first-learn/{planId}")
    public R<Void> firstLearn(@RequestAttribute Long userId, @PathVariable Long planId, @RequestBody StudyReq req) {
        studyService.firstLearn(userId, planId, req.getResult());
        return R.ok();
    }

    /** @deprecated 使用 /answer */
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

    @GetMapping("/debug/plans")
    public R<List<Map<String, Object>>> debugPlans(@RequestAttribute Long userId) {
        List<WdLearnPlan> plans = planMapper.selectAllByUserId(userId);
        List<Map<String, Object>> result = new java.util.ArrayList<>();
        for (WdLearnPlan p : plans) {
            WdWord w = wordMapper.selectById(p.getWordId());
            Map<String, Object> m = new HashMap<>();
            m.put("planId", p.getId());
            m.put("wordId", p.getWordId());
            m.put("notebookId", p.getNotebookId());
            m.put("learnStatus", p.getLearnStatus());
            m.put("currentLevel", p.getCurrentLevel());
            m.put("isDeleted", p.getIsDeleted());
            m.put("wordProto", w != null ? w.getWordProto() : "<not-found>");
            m.put("wordNotebookId", w != null ? w.getNotebookId() : null);
            result.add(m);
        }
        return R.ok(result);
    }
}
