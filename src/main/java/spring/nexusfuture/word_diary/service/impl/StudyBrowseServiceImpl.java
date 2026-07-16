package spring.nexusfuture.word_diary.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import spring.nexusfuture.word_diary.common.BizException;
import spring.nexusfuture.word_diary.common.LearnStatus;
import spring.nexusfuture.word_diary.common.StudyFeedback;
import spring.nexusfuture.word_diary.dto.StudyBrowseAnswerReq;
import spring.nexusfuture.word_diary.dto.StudyBrowsePageVO;
import spring.nexusfuture.word_diary.dto.StudyTaskVO;
import spring.nexusfuture.word_diary.entity.WdLearnPlan;
import spring.nexusfuture.word_diary.entity.WdLearnRecord;
import spring.nexusfuture.word_diary.entity.WdWord;
import spring.nexusfuture.word_diary.entity.WdWordBook;
import spring.nexusfuture.word_diary.mapper.WdLearnPlanMapper;
import spring.nexusfuture.word_diary.mapper.WdLearnRecordMapper;
import spring.nexusfuture.word_diary.mapper.WdWordBookMapper;
import spring.nexusfuture.word_diary.mapper.WdWordMapper;
import spring.nexusfuture.word_diary.service.StudyBrowseService;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class StudyBrowseServiceImpl implements StudyBrowseService {

    private final WdLearnPlanMapper planMapper;
    private final WdLearnRecordMapper recordMapper;
    private final WdWordMapper wordMapper;
    private final WdWordBookMapper bookMapper;

    public StudyBrowseServiceImpl(WdLearnPlanMapper planMapper, WdLearnRecordMapper recordMapper,
                                  WdWordMapper wordMapper, WdWordBookMapper bookMapper) {
        this.planMapper = planMapper;
        this.recordMapper = recordMapper;
        this.wordMapper = wordMapper;
        this.bookMapper = bookMapper;
    }

    @Override
    public StudyBrowsePageVO listWords(Long userId, Long notebookId, int page, int pageSize) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(100, Math.max(1, pageSize));
        int offset = (safePage - 1) * safeSize;

        int total = planMapper.countBrowseWords(userId, notebookId);
        List<WdLearnPlan> plans = planMapper.selectBrowseWords(userId, notebookId, safeSize, offset);
        List<StudyTaskVO> items = dedupByWordId(plans).stream().map(this::toTaskVO).collect(Collectors.toList());

        return StudyBrowsePageVO.builder()
                .items(items)
                .total(total)
                .page(safePage)
                .pageSize(safeSize)
                .build();
    }

    @Override
    @Transactional
    public void submitAnswer(Long userId, StudyBrowseAnswerReq req) {
        if (req == null || req.getPlanId() == null || req.getFeedback() == null) {
            throw new BizException("参数不完整");
        }
        if (!StudyFeedback.isValid(req.getFeedback())) {
            throw new BizException("无效的反馈类型");
        }

        WdLearnPlan plan = planMapper.selectById(req.getPlanId());
        if (plan == null || !plan.getUserId().equals(userId)) {
            throw new BizException("学习计划不存在");
        }
        if (plan.getLearnStatus() != LearnStatus.FIRST_LEARNED
                && plan.getLearnStatus() != LearnStatus.REVIEWING
                && plan.getLearnStatus() != LearnStatus.MASTERED) {
            throw new BizException("该单词不可回顾");
        }

        int feedback = req.getFeedback();
        int legacyResult = feedback == StudyFeedback.KNOW ? LearnStatus.RESULT_REMEMBER : LearnStatus.RESULT_FORGET;
        WdLearnRecord record = WdLearnRecord.builder()
                .userId(userId)
                .notebookId(plan.getNotebookId())
                .wordId(plan.getWordId())
                .studyType(LearnStatus.STUDY_TYPE_BROWSE)
                .result(legacyResult)
                .feedback(feedback)
                .studyMode(LearnStatus.STUDY_MODE_BROWSE)
                .affectsSchedule(0)
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
