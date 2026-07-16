package spring.nexusfuture.word_diary.service;

import spring.nexusfuture.word_diary.dto.*;

public interface StudySessionService {
    StudyTodayVO getToday(Long userId);
    StudySessionVO openSession(Long userId);
    StudyAnswerVO submitAnswer(Long userId, StudyAnswerReq req);
    StudySettingsVO getSettings(Long userId);
    void updateSettings(Long userId, StudySettingsReq req);
}
