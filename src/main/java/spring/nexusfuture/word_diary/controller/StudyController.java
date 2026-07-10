package spring.nexusfuture.word_diary.controller;

import org.springframework.web.bind.annotation.*;
import spring.nexusfuture.word_diary.common.R;
import spring.nexusfuture.word_diary.dto.*;
import spring.nexusfuture.word_diary.entity.WdLearnPlan;
import spring.nexusfuture.word_diary.entity.WdWord;
import spring.nexusfuture.word_diary.mapper.WdLearnPlanMapper;
import spring.nexusfuture.word_diary.mapper.WdWordMapper;
import spring.nexusfuture.word_diary.service.StudyService;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

@RestController
@RequestMapping("/api/study")
public class StudyController {

    private final StudyService studyService;
    private final WdLearnPlanMapper planMapper;
    private final WdWordMapper wordMapper;

    public StudyController(StudyService studyService, WdLearnPlanMapper planMapper, WdWordMapper wordMapper) {
        this.studyService = studyService;
        this.planMapper = planMapper;
        this.wordMapper = wordMapper;
    }

    @GetMapping("/new-words")
    public R<List<StudyTaskVO>> getNewWords(@RequestAttribute Long userId) {
        return R.ok(studyService.getNewWords(userId));
    }

    @GetMapping("/today-review")
    public R<List<StudyTaskVO>> getTodayReview(@RequestAttribute Long userId) {
        return R.ok(studyService.getTodayReview(userId));
    }

    @GetMapping("/today-completed")
    public R<List<StudyTaskVO>> getTodayCompleted(@RequestAttribute Long userId) {
        return R.ok(studyService.getTodayCompleted(userId));
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

    /**
     * 临时诊断接口：返回用户所有 plan 行 + 关联的 word 详情
     * 用于排查 new-words 接口去重问题
     */
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
