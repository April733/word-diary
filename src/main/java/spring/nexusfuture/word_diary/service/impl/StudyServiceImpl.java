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
    private final StudySessionService sessionService;

    public StudyServiceImpl(WdLearnPlanMapper planMapper, WdLearnRecordMapper recordMapper,
                            WdWordMapper wordMapper, WdWordBookMapper bookMapper,
                            WdStudyQueueMapper queueMapper, StudySessionService sessionService) {
        this.planMapper = planMapper;
        this.recordMapper = recordMapper;
        this.wordMapper = wordMapper;
        this.bookMapper = bookMapper;
        this.queueMapper = queueMapper;
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
        LocalDate today = sessionService.currentStudyDate(userId);
        List<WdLearnPlan> plans = planMapper.selectReviewTasks(userId, today);
        List<Long> todayCompletedWordIds = queueMapper.selectWordIdsWithMinDonePasses(
                userId,
                sessionService.currentStudyDayStart(userId),
                sessionService.currentStudyDayEnd(userId),
                2);
        return dedupByWordId(plans).stream()
                .filter(p -> !todayCompletedWordIds.contains(p.getWordId()))
                .map(this::toTaskVO)
                .collect(Collectors.toList());
    }

    @Override
    public List<StudyTaskVO> getTodayCompleted(Long userId) {
        // 仅统计当前学习日时间窗内（默认今早7点～明早7点）至少背完 2 遍的词
        List<Long> todayWordIds = queueMapper.selectWordIdsWithMinDonePasses(
                userId,
                sessionService.currentStudyDayStart(userId),
                sessionService.currentStudyDayEnd(userId),
                2);
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
        LocalDate today = sessionService.currentStudyDate(userId);
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
        LocalDate today = sessionService.currentStudyDate(userId);
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
        // 全部在背：仅统计已开启参与每日学习的单词本
        List<WdLearnPlan> inPlan = planMapper.selectAllInStudyPlan(userId);
        LocalDate studyDate = sessionService.currentStudyDate(userId);
        List<WdLearnPlan> reviewTasks = planMapper.selectReviewTasks(userId, studyDate);
        StudyTodayVO today = sessionService.getToday(userId);

        long pending = inPlan.stream().filter(p -> p.getLearnStatus() == LearnStatus.NOT_LEARNED).count();
        long learning = inPlan.stream()
                .filter(p -> p.getLearnStatus() == LearnStatus.FIRST_LEARNED
                        || p.getLearnStatus() == LearnStatus.REVIEWING).count();
        long mastered = inPlan.stream().filter(p -> p.getLearnStatus() == LearnStatus.MASTERED).count();

        return StatsVO.builder()
                .totalWords(inPlan.size())
                .newWords(pending)
                .reviewingWords(learning)
                .masteredWords(mastered)
                .pausedWords(planMapper.countPausedByUserId(userId))
                .todayReviewCount(reviewTasks.size())
                .todayReviewPending(today.getReviewPending())
                .todayNewPending(today.getNewPending())
                .reviewPhaseDone(today.isReviewPhaseDone())
                .build();
    }

    @Override
    public List<StudyTaskVO> listPlanWords(Long userId, String status) {
        planMapper.migrateForgottenToNotLearned(userId);
        String key = status == null || status.isBlank() ? "all" : status.trim().toLowerCase();
        return dedupByWordId(planMapper.selectAllInStudyPlan(userId)).stream()
                .filter(p -> matchPlanStatus(p.getLearnStatus(), key))
                .sorted((a, b) -> {
                    int cmp = Integer.compare(
                            statusSortKey(a.getLearnStatus()),
                            statusSortKey(b.getLearnStatus()));
                    if (cmp != 0) return cmp;
                    return Long.compare(b.getId(), a.getId());
                })
                .map(this::toTaskVO)
                .collect(Collectors.toList());
    }

    private static boolean matchPlanStatus(Integer learnStatus, String key) {
        int s = learnStatus == null ? LearnStatus.NOT_LEARNED : learnStatus;
        return switch (key) {
            case "pending" -> s == LearnStatus.NOT_LEARNED;
            case "learning" -> s == LearnStatus.FIRST_LEARNED || s == LearnStatus.REVIEWING;
            case "mastered" -> s == LearnStatus.MASTERED;
            default -> s == LearnStatus.NOT_LEARNED
                    || s == LearnStatus.FIRST_LEARNED
                    || s == LearnStatus.REVIEWING
                    || s == LearnStatus.MASTERED;
        };
    }

    private static int statusSortKey(Integer learnStatus) {
        int s = learnStatus == null ? LearnStatus.NOT_LEARNED : learnStatus;
        if (s == LearnStatus.NOT_LEARNED) return 0;
        if (s == LearnStatus.FIRST_LEARNED || s == LearnStatus.REVIEWING) return 1;
        if (s == LearnStatus.MASTERED) return 2;
        return 9;
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
