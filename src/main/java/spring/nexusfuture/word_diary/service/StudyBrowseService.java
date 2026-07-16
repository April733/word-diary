package spring.nexusfuture.word_diary.service;

import spring.nexusfuture.word_diary.dto.StudyBrowseAnswerReq;
import spring.nexusfuture.word_diary.dto.StudyBrowsePageVO;

public interface StudyBrowseService {
    StudyBrowsePageVO listWords(Long userId, Long notebookId, int page, int pageSize);
    void submitAnswer(Long userId, StudyBrowseAnswerReq req);
}
