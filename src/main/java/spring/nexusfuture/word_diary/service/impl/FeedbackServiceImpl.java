package spring.nexusfuture.word_diary.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import spring.nexusfuture.word_diary.entity.WdFeedback;
import spring.nexusfuture.word_diary.mapper.WdFeedbackMapper;
import spring.nexusfuture.word_diary.service.FeedbackService;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FeedbackServiceImpl implements FeedbackService {

    private final WdFeedbackMapper feedbackMapper;

    @Override
    @Transactional
    public WdFeedback saveFeedback(Long userId, String username, String reporterEmail, String subject, String description, String errorDetail) {
        WdFeedback feedback = WdFeedback.builder()
                .userId(userId)
                .username(username)
                .reporterEmail(reporterEmail)
                .subject(subject)
                .description(description)
                .errorDetail(errorDetail)
                .status(0)
                .isDeleted(0)
                .build();
        feedbackMapper.insert(feedback);
        return feedback;
    }

    @Override
    public List<WdFeedback> listAll() {
        return feedbackMapper.selectAll();
    }

    @Override
    public List<WdFeedback> listByUserId(Long userId) {
        return feedbackMapper.selectByUserId(userId);
    }

    @Override
    public List<WdFeedback> listByStatus(Integer status) {
        return feedbackMapper.selectByStatus(status);
    }

    @Override
    @Transactional
    public void updateStatus(Long id, Integer status) {
        feedbackMapper.updateStatus(id, status);
    }

    @Override
    @Transactional
    public void deleteFeedback(Long id) {
        feedbackMapper.softDelete(id);
    }
}