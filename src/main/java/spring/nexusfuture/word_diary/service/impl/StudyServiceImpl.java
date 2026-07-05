package spring.nexusfuture.word_diary.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import spring.nexusfuture.word_diary.common.BizException;
import spring.nexusfuture.word_diary.common.LearnStatus;
import spring.nexusfuture.word_diary.dto.StatsVO;
import spring.nexusfuture.word_diary.dto.StudyRecordVO;
import spring.nexusfuture.word_diary.dto.StudyTaskVO;
import spring.nexusfuture.word_diary.entity.*;
import spring.nexusfuture.word_diary.mapper.*;
import spring.nexusfuture.word_diary.service.StudyService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class StudyServiceImpl implements StudyService {

    private final WdLearnPlanMapper planMapper;
    private final WdLearnRecordMapper recordMapper;
    private final WdWordMapper wordMapper;
    private final WdWordBookMapper bookMapper;
    private final SysEbbinghausNodeMapper ebbinghausMapper;

    public StudyServiceImpl(WdLearnPlanMapper planMapper, WdLearnRecordMapper recordMapper,
                            WdWordMapper wordMapper, WdWordBookMapper bookMapper,
                            SysEbbinghausNodeMapper ebbinghausMapper) {
        this.planMapper = planMapper;
        this.recordMapper = recordMapper;
        this.wordMapper = wordMapper;
        this.bookMapper = bookMapper;
        this.ebbinghausMapper = ebbinghausMapper;
    }

    @Override
    public List<StudyTaskVO> getNewWords(Long userId) {
        List<WdLearnPlan> plans = planMapper.selectNewWords(userId);
        return plans.stream().map(this::toTaskVO).collect(Collectors.toList());
    }

    @Override
    public List<StudyTaskVO> getTodayReview(Long userId) {
        List<WdLearnPlan> plans = planMapper.selectReviewTasks(userId, LocalDateTime.now());
        return plans.stream().map(this::toTaskVO).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void firstLearn(Long userId, Long planId, Integer result) {
        WdLearnPlan plan = planMapper.selectById(planId);
        if (plan == null || !plan.getUserId().equals(userId)) {
            throw new BizException("学习计划不存在");
        }
        if (plan.getLearnStatus() != LearnStatus.NOT_LEARNED && plan.getLearnStatus() != LearnStatus.FORGOTTEN) {
            throw new BizException("当前单词状态不是待初学");
        }

        LocalDateTime now = LocalDateTime.now();

        if (result == LearnStatus.RESULT_REMEMBER) {
            // Remembered: status -> FIRST_LEARNED, level -> 1
            plan.setLearnStatus(LearnStatus.FIRST_LEARNED);
            plan.setCurrentLevel(1);
            plan.setFirstStudyTime(plan.getFirstStudyTime() != null ? plan.getFirstStudyTime() : now);
            plan.setReviewTimes(plan.getReviewTimes() + 1);

            // Calculate next review time
            List<SysEbbinghausNode> nodes = ebbinghausMapper.selectAll();
            if (nodes.size() > 1) {
                SysEbbinghausNode nextNode = nodes.get(1); // level 1 -> index 1
                plan.setNextReviewTime(plan.getFirstStudyTime().plusDays(nextNode.getDayInterval()));
            }
        } else {
            // Forgot: status stays 0, wait for retry
            plan.setLearnStatus(LearnStatus.NOT_LEARNED);
            plan.setCurrentLevel(0);
            plan.setNextReviewTime(null);
        }
        planMapper.updatePlan(plan);

        // Record study behavior
        saveRecord(userId, plan, LearnStatus.STUDY_TYPE_FIRST, result, now);
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

        LocalDateTime now = LocalDateTime.now();

        if (result == LearnStatus.RESULT_REMEMBER) {
            plan.setReviewTimes(plan.getReviewTimes() + 1);
            int nextLevel = plan.getCurrentLevel() + 1;

            List<SysEbbinghausNode> nodes = ebbinghausMapper.selectAll();
            int maxLevel = nodes.size() - 1; // max level = max index

            if (nextLevel > maxLevel) {
                // Mastered!
                plan.setLearnStatus(LearnStatus.MASTERED);
                plan.setCurrentLevel(nextLevel);
                plan.setNextReviewTime(null);
            } else {
                plan.setLearnStatus(LearnStatus.REVIEWING);
                plan.setCurrentLevel(nextLevel);
                SysEbbinghausNode nextNode = nodes.get(nextLevel);
                plan.setNextReviewTime(plan.getFirstStudyTime().plusDays(nextNode.getDayInterval()));
            }
        } else {
            // Forgot -> reset to status 4 (FORGOTTEN), level 0
            plan.setLearnStatus(LearnStatus.FORGOTTEN);
            plan.setCurrentLevel(0);
            plan.setNextReviewTime(null);
            // firstStudyTime and reviewTimes are preserved for statistics
        }
        planMapper.updatePlan(plan);

        saveRecord(userId, plan, LearnStatus.STUDY_TYPE_REVIEW, result, now);
    }

    @Override
    public List<StudyRecordVO> getRecords(Long userId) {
        List<WdLearnRecord> records = recordMapper.selectByUserId(userId);
        return records.stream().map(r -> {
            WdWord word = wordMapper.selectById(r.getWordId());
            WdWordBook book = bookMapper.selectById(r.getNotebookId());
            return StudyRecordVO.builder()
                    .id(r.getId())
                    .wordProto(word != null ? word.getWordProto() : "未知")
                    .studyType(r.getStudyType())
                    .result(r.getResult())
                    .studyTime(r.getStudyTime())
                    .notebookName(book != null ? book.getBookName() : "未知")
                    .build();
        }).collect(Collectors.toList());
    }

    @Override
    public StatsVO getStats(Long userId) {
        List<WdLearnPlan> allPlans = planMapper.selectAllByUserId(userId);
        List<WdLearnPlan> reviewTasks = planMapper.selectReviewTasks(userId, LocalDateTime.now());

        return StatsVO.builder()
                .totalWords(allPlans.size())
                .newWords(allPlans.stream().filter(p -> p.getLearnStatus() == LearnStatus.NOT_LEARNED).count())
                .reviewingWords(allPlans.stream()
                        .filter(p -> p.getLearnStatus() == LearnStatus.FIRST_LEARNED
                                || p.getLearnStatus() == LearnStatus.REVIEWING).count())
                .masteredWords(allPlans.stream().filter(p -> p.getLearnStatus() == LearnStatus.MASTERED).count())
                .todayReviewCount(reviewTasks.size())
                .build();
    }

    private void saveRecord(Long userId, WdLearnPlan plan, Integer studyType, Integer result, LocalDateTime time) {
        WdLearnRecord record = WdLearnRecord.builder()
                .userId(userId)
                .notebookId(plan.getNotebookId())
                .wordId(plan.getWordId())
                .studyType(studyType)
                .result(result)
                .studyTime(time)
                .build();
        recordMapper.insert(record);
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
