package spring.nexusfuture.word_diary.service;

import spring.nexusfuture.word_diary.entity.WdFeedback;

import java.util.List;

public interface FeedbackService {

    WdFeedback saveFeedback(Long userId, String username, String reporterEmail, String subject, String description, String errorDetail);

    List<WdFeedback> listAll();

    List<WdFeedback> listByUserId(Long userId);

    List<WdFeedback> listByStatus(Integer status);

    void updateStatus(Long id, Integer status);

    void deleteFeedback(Long id);
}