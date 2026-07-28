package spring.nexusfuture.word_diary.service.impl;

import org.springframework.stereotype.Service;
import spring.nexusfuture.word_diary.common.WordStudyStatus;
import spring.nexusfuture.word_diary.dto.response.StatsVO;
import spring.nexusfuture.word_diary.dto.response.StudyTaskVO;
import spring.nexusfuture.word_diary.entity.*;
import spring.nexusfuture.word_diary.mapper.*;
import spring.nexusfuture.word_diary.service.StudyCalendarService;
import spring.nexusfuture.word_diary.service.StudyDailyPassStore;
import spring.nexusfuture.word_diary.service.StudyNewWordPoolService;
import spring.nexusfuture.word_diary.service.StudyReviewPoolService;
import spring.nexusfuture.word_diary.service.StudyService;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class StudyServiceImpl implements StudyService {

    private final WdWordMapper wordMapper;
    private final WdWordBookMapper bookMapper;
    private final StudyDailyPassStore passStore;
    private final StudyCalendarService calendarService;
    private final StudyReviewPoolService reviewPoolService;
    private final StudyNewWordPoolService newWordPoolService;

    public StudyServiceImpl(WdWordMapper wordMapper, WdWordBookMapper bookMapper,
                            StudyDailyPassStore passStore, StudyCalendarService calendarService,
                            StudyReviewPoolService reviewPoolService,
                            StudyNewWordPoolService newWordPoolService) {
        this.wordMapper = wordMapper;
        this.bookMapper = bookMapper;
        this.passStore = passStore;
        this.calendarService = calendarService;
        this.reviewPoolService = reviewPoolService;
        this.newWordPoolService = newWordPoolService;
    }

    @Override
    public List<StudyTaskVO> getTodayCompleted(Long userId) {
        LocalDate today = calendarService.currentStudyDate(userId);
        Set<Long> completedWordIds = passStore.allCompletedWordIds(userId, today, 2);
        if (completedWordIds.isEmpty()) {
            return List.of();
        }
        return wordMapper.selectAllByUserId(userId).stream()
                .filter(w -> w.getId() != null && completedWordIds.contains(w.getId()))
                .map(this::toTaskVO)
                .collect(Collectors.toList());
    }

    @Override
    public StatsVO getStats(Long userId) {
        List<WdWord> inPlan = wordMapper.selectAllInStudyPlan(userId);
        LocalDate studyDate = calendarService.currentStudyDate(userId);
        List<WdWord> reviewTasks = wordMapper.selectReviewTasks(userId, studyDate);
        int reviewPending = reviewPoolService.listPending(userId, studyDate).size();
        int newPending = newWordPoolService.listPending(userId, studyDate).size();
        boolean reviewPhaseDone = reviewPending == 0;

        long pending = inPlan.stream().filter(w -> codeOf(w) == 0).count();
        int masteredCode = WordStudyStatus.MASTERED.getCode();
        long learning = inPlan.stream().filter(w -> {
            int s = codeOf(w);
            return s != 0 && s != masteredCode;
        }).count();
        long mastered = inPlan.stream().filter(w -> codeOf(w) == masteredCode).count();

        return StatsVO.builder()
                .totalWords(inPlan.size())
                .newWords(pending)
                .reviewingWords(learning)
                .masteredWords(mastered)
                .pausedWords(wordMapper.countPausedByUserId(userId))
                .todayReviewCount(reviewTasks.size())
                .todayReviewPending(reviewPending)
                .todayNewPending(newPending)
                .reviewPhaseDone(reviewPhaseDone)
                .build();
    }

    @Override
    public List<StudyTaskVO> listPlanWords(Long userId, String status) {
        String key = status == null || status.isBlank() ? "all" : status.trim().toLowerCase();
        return wordMapper.selectAllInStudyPlan(userId).stream()
                .filter(w -> matchPlanFilter(codeOf(w), key))
                .sorted((a, b) -> {
                    int cmp = Integer.compare(statusSortKey(codeOf(a)), statusSortKey(codeOf(b)));
                    if (cmp != 0) return cmp;
                    return Long.compare(b.getId(), a.getId());
                })
                .map(this::toTaskVO)
                .collect(Collectors.toList());
    }

    private static int codeOf(WdWord word) {
        return word.getStatus() == null ? 0 : word.getStatus();
    }

    private static boolean matchPlanFilter(int status, String key) {
        int mastered = WordStudyStatus.MASTERED.getCode();
        return switch (key) {
            case "pending" -> status == 0;
            case "learning" -> status != 0 && status != mastered;
            case "mastered" -> status == mastered;
            default -> true;
        };
    }

    private static int statusSortKey(int status) {
        if (status == 0) return 0;
        if (status == WordStudyStatus.MASTERED.getCode()) return 2;
        return 1;
    }

    private StudyTaskVO toTaskVO(WdWord word) {
        WdWordBook book = bookMapper.selectById(word.getNotebookId());
        return StudyTaskVO.builder()
                .wordId(word.getId())
                .wordProto(word.getWordProto() != null ? word.getWordProto() : "")
                .usPhonetic(word.getUsPhonetic() != null ? word.getUsPhonetic() : "")
                .ukPhonetic(word.getUkPhonetic() != null ? word.getUkPhonetic() : "")
                .partSpeechMean(word.getPartSpeechMean() != null ? word.getPartSpeechMean() : "")
                .sentenceExample(word.getSentenceExample() != null ? word.getSentenceExample() : "")
                .rootAffix(word.getRootAffix() != null ? word.getRootAffix() : "")
                .learnStatus(word.getStatus())
                .statusData(word.getStatusData())
                .notebookName(book != null ? book.getBookName() : "")
                .build();
    }

    @Override
    public List<Map<String, Object>> debugWordSrs(Long userId) {
        List<WdWord> words = wordMapper.selectAllByUserId(userId);
        List<Map<String, Object>> result = new java.util.ArrayList<>();
        for (WdWord w : words) {
            Map<String, Object> m = new java.util.HashMap<>();
            m.put("wordId", w.getId());
            m.put("notebookId", w.getNotebookId());
            m.put("learnStatus", w.getStatus());
            m.put("statusData", w.getStatusData());
            m.put("isDeleted", w.getIsDeleted());
            m.put("wordProto", w.getWordProto() != null ? w.getWordProto() : "<not-found>");
            result.add(m);
        }
        return result;
    }
}
