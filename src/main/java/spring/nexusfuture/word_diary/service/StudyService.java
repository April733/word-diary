package spring.nexusfuture.word_diary.service;

import spring.nexusfuture.word_diary.dto.StatsVO;
import spring.nexusfuture.word_diary.dto.StudyRecordVO;
import spring.nexusfuture.word_diary.dto.StudyTaskVO;
import java.util.List;

public interface StudyService {
    List<StudyTaskVO> getNewWords(Long userId);
    List<StudyTaskVO> getTodayReview(Long userId);
    void firstLearn(Long userId, Long planId, Integer result);
    void review(Long userId, Long planId, Integer result);
    List<StudyRecordVO> getRecords(Long userId);
    StatsVO getStats(Long userId);
}
