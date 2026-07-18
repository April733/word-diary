package spring.nexusfuture.word_diary.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import spring.nexusfuture.word_diary.common.BizException;
import spring.nexusfuture.word_diary.common.LearnStatus;
import spring.nexusfuture.word_diary.common.StudyAlgorithm;
import spring.nexusfuture.word_diary.common.StudyFeedback;
import spring.nexusfuture.word_diary.dto.*;
import spring.nexusfuture.word_diary.entity.*;
import spring.nexusfuture.word_diary.mapper.*;
import spring.nexusfuture.word_diary.service.StudyService;
import spring.nexusfuture.word_diary.service.StudySessionService;
import spring.nexusfuture.word_diary.service.SysBaseConfigService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class StudyServiceImpl implements StudyService {

    private final WdLearnPlanMapper planMapper;
    private final WdLearnRecordMapper recordMapper;
    private final WdWordMapper wordMapper;
    private final WdWordBookMapper bookMapper;
    private final WdStudyQueueMapper queueMapper;
    private final SysBaseConfigService configService;
    private final StudySessionService sessionService;

    public StudyServiceImpl(WdLearnPlanMapper planMapper, WdLearnRecordMapper recordMapper,
                            WdWordMapper wordMapper, WdWordBookMapper bookMapper,
                            WdStudyQueueMapper queueMapper,
                            SysBaseConfigService configService, StudySessionService sessionService) {
        this.planMapper = planMapper;
        this.recordMapper = recordMapper;
        this.wordMapper = wordMapper;
        this.bookMapper = bookMapper;
        this.queueMapper = queueMapper;
        this.configService = configService;
        this.sessionService = sessionService;
    }

    @Override
    public List<StudyTaskVO> getNewWords(Long userId) {
        planMapper.migrateForgottenToNotLearned(userId);
        List<WdLearnPlan> plans = planMapper.selectNewWords(userId);
        return dedupByWordId(plans).stream().map(this::toTaskVO).collect(Collectors.toList());
    }

    @Override
    public List<StudyTaskVO> getTodayReview(Long userId) {
        List<WdLearnPlan> plans = planMapper.selectReviewTasks(userId, configService.getReviewUnlockHour());
        List<Long> todayCompletedWordIds = queueMapper.selectWordIdsWithMinDonePasses(
                userId, LocalDate.now(), 2);
        return dedupByWordId(plans).stream()
                .filter(p -> !todayCompletedWordIds.contains(p.getWordId()))
                .map(this::toTaskVO)
                .collect(Collectors.toList());
    }

    @Override
    public List<StudyTaskVO> getTodayCompleted(Long userId) {
        // 一天内至少背完 2 遍才算已完成
        List<Long> todayWordIds = queueMapper.selectWordIdsWithMinDonePasses(userId, LocalDate.now(), 2);
        if (todayWordIds.isEmpty()) {
            return List.of();
        }
        List<WdLearnPlan> allPlans = planMapper.selectAllByUserId(userId);
        return allPlans.stream()
                .filter(p -> todayWordIds.contains(p.getWordId()))
                .collect(Collectors.toMap(WdLearnPlan::getWordId, p -> p, (a, b) -> a, LinkedHashMap::new))
                .values().stream()
                .map(this::toTaskVO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void firstLearn(Long userId, Long planId, Integer result) {
        WdLearnPlan plan = planMapper.selectById(planId);
        if (plan == null || !plan.getUserId().equals(userId)) {
            throw new BizException("学习计划不存在");
        }
        if (plan.getLearnStatus() != LearnStatus.NOT_LEARNED) {
            throw new BizException("当前单词状态不是待初学");
        }

        int feedback = mapLegacyResult(result);
        LocalDate today = LocalDate.now();
        StudyAlgorithm.applyFeedback(plan, feedback, today, true);
        planMapper.updatePlan(plan);
        saveRecord(userId, plan, LearnStatus.STUDY_TYPE_FIRST, feedback);
    }

    @Override
    @Transactional
    public void review(Long userId, Long planId, Integer result) {
        WdLearnPlan plan = planMapper.selectById(planId);
        if (plan == null || !plan.getUserId().equals(userId)) {
            throw new BizException("学习计划不存在");
        }
        if (plan.getLearnStatus() != LearnStatus.FIRST_LEARNED && plan.getLearnStatus() != LearnStatus.REVIEWING) {
            throw new BizException("当前单词状态不可复习");
        }

        int feedback = mapLegacyResult(result);
        LocalDate today = LocalDate.now();
        StudyAlgorithm.applyFeedback(plan, feedback, today, false);
        if (feedback == StudyFeedback.FORGET) {
            plan.setScheduledNewDate(null);
        }
        planMapper.updatePlan(plan);
        saveRecord(userId, plan, LearnStatus.STUDY_TYPE_REVIEW, feedback);
    }

    @Override
    public List<StudyRecordVO> getRecords(Long userId) {
        List<WdLearnRecord> records = recordMapper.selectByUserId(userId);
        return records.stream().map(r -> {
            // 词/本可能已软删，学习记录仍应展示当时词形
            WdWord word = wordMapper.selectByIdAny(r.getWordId());
            WdWordBook book = bookMapper.selectByIdAny(r.getNotebookId());
            String proto = word != null && word.getWordProto() != null && !word.getWordProto().isBlank()
                    ? word.getWordProto() : "未知";
            if (word != null && word.getIsDeleted() != null && word.getIsDeleted() == 1
                    && !"未知".equals(proto)) {
                proto = proto + "（已删除）";
            }
            String notebookName = book != null && book.getBookName() != null && !book.getBookName().isBlank()
                    ? book.getBookName() : "未知";
            if (book != null && book.getIsDeleted() != null && book.getIsDeleted() == 1
                    && !"未知".equals(notebookName)) {
                notebookName = notebookName + "（已删除）";
            }
            return StudyRecordVO.builder()
                    .id(r.getId())
                    .wordProto(proto)
                    .studyType(r.getStudyType())
                    .result(r.getResult())
                    .feedback(r.getFeedback())
                    .studyMode(r.getStudyMode())
                    .studyTime(r.getStudyTime())
                    .notebookName(notebookName)
                    .build();
        }).collect(Collectors.toList());
    }

    @Override
    public StatsVO getStats(Long userId) {
        planMapper.migrateForgottenToNotLearned(userId);
        List<WdLearnPlan> allPlans = planMapper.selectAllByUserId(userId);
        List<WdLearnPlan> reviewTasks = planMapper.selectReviewTasks(userId, configService.getReviewUnlockHour());
        StudyTodayVO today = sessionService.getToday(userId);

        return StatsVO.builder()
                .totalWords(allPlans.size())
                .newWords(allPlans.stream().filter(p -> p.getLearnStatus() == LearnStatus.NOT_LEARNED).count())
                .reviewingWords(allPlans.stream()
                        .filter(p -> p.getLearnStatus() == LearnStatus.FIRST_LEARNED
                                || p.getLearnStatus() == LearnStatus.REVIEWING).count())
                .masteredWords(allPlans.stream().filter(p -> p.getLearnStatus() == LearnStatus.MASTERED).count())
                .todayReviewCount(reviewTasks.size())
                .todayReviewPending(today.getReviewPending())
                .todayNewPending(today.getNewPending())
                .reviewPhaseDone(today.isReviewPhaseDone())
                .build();
    }

    private int mapLegacyResult(Integer result) {
        if (result == null || result == LearnStatus.RESULT_FORGET) {
            return StudyFeedback.FORGET;
        }
        return StudyFeedback.KNOW;
    }

    private void saveRecord(Long userId, WdLearnPlan plan, Integer studyType, int feedback) {
        int legacyResult = feedback == StudyFeedback.KNOW ? LearnStatus.RESULT_REMEMBER : LearnStatus.RESULT_FORGET;
        WdLearnRecord record = WdLearnRecord.builder()
                .userId(userId)
                .notebookId(plan.getNotebookId())
                .wordId(plan.getWordId())
                .studyType(studyType)
                .result(legacyResult)
                .feedback(feedback)
                .studyMode(LearnStatus.STUDY_MODE_DAILY)
                .affectsSchedule(1)
                .studyTime(LocalDateTime.now())
                .build();
        recordMapper.insert(record);
    }

    private List<WdLearnPlan> dedupByWordId(List<WdLearnPlan> plans) {
        return plans.stream()
                .collect(Collectors.toMap(WdLearnPlan::getWordId, p -> p,
                        (a, b) -> a.getId() < b.getId() ? a : b,
                        LinkedHashMap::new))
                .values().stream()
                .collect(Collectors.toList());
    }

    private StudyTaskVO toTaskVO(WdLearnPlan plan) {
        WdWord word = wordMapper.selectById(plan.getWordId());
        WdWordBook book = bookMapper.selectById(plan.getNotebookId());
        return StudyTaskVO.builder()
                .planId(plan.getId())
                .wordId(plan.getWordId())
                .wordProto(word != null ? word.getWordProto() : "")
                .usPhonetic(word != null ? word.getUsPhonetic() : "")
                .ukPhonetic(word != null ? word.getUkPhonetic() : "")
                .partSpeechMean(word != null ? word.getPartSpeechMean() : "")
                .sentenceExample(word != null ? word.getSentenceExample() : "")
                .rootAffix(word != null ? word.getRootAffix() : "")
                .learnStatus(plan.getLearnStatus())
                .currentLevel(plan.getCurrentLevel())
                .reviewTimes(plan.getReviewTimes())
                .nextReviewTime(plan.getNextReviewTime())
                .notebookName(book != null ? book.getBookName() : "")
                .build();
    }
}
