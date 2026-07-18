package spring.nexusfuture.word_diary.service;

import spring.nexusfuture.word_diary.dto.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

public interface StudySessionService {
    StudyTodayVO getToday(Long userId);
    StudySessionVO openSession(Long userId);
    StudyAnswerVO submitAnswer(Long userId, StudyAnswerReq req);
    StudySettingsVO getSettings(Long userId);
    void updateSettings(Long userId, StudySettingsReq req);
    /** 今日队列词表（phase=review|new，空则全部） */
    java.util.List<StudyTaskVO> listTodayQueue(Long userId, String phase);
    /** 当前学习日（按用户复习开放时刻切分，默认早7点→次日早7点） */
    LocalDate currentStudyDate(Long userId);
    /** 当前学习日开始时刻（含） */
    LocalDateTime currentStudyDayStart(Long userId);
    /** 当前学习日结束时刻（不含） */
    LocalDateTime currentStudyDayEnd(Long userId);
}
