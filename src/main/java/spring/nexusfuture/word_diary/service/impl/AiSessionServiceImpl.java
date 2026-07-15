package spring.nexusfuture.word_diary.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import spring.nexusfuture.word_diary.entity.WdAiMessage;
import spring.nexusfuture.word_diary.entity.WdAiSession;
import spring.nexusfuture.word_diary.mapper.WdAiMessageMapper;
import spring.nexusfuture.word_diary.mapper.WdAiSessionMapper;
import spring.nexusfuture.word_diary.service.AiSessionService;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiSessionServiceImpl implements AiSessionService {

    private final WdAiSessionMapper sessionMapper;
    private final WdAiMessageMapper messageMapper;

    /** 复用会话的时间窗口（小时） */
    private static final int SESSION_REUSE_HOURS = 24;

    @Override
    @Transactional
    public WdAiSession getOrCreateSession(Long userId, Long bookId) {
        List<WdAiSession> sessions = sessionMapper.selectByUserAndBook(userId, bookId);
        LocalDateTime threshold = LocalDateTime.now().minus(SESSION_REUSE_HOURS, ChronoUnit.HOURS);

        // 查找24h内活跃的会话，复用
        for (WdAiSession session : sessions) {
            if (session.getUpdateTime() != null && session.getUpdateTime().isAfter(threshold)) {
                sessionMapper.touch(session.getId());
                return session;
            }
        }

        // 创建新会话
        WdAiSession newSession = WdAiSession.builder()
                .userId(userId)
                .bookId(bookId)
                .title("新对话")
                .build();
        sessionMapper.insert(newSession);
        log.info("创建新AI会话: id={}, userId={}, bookId={}", newSession.getId(), userId, bookId);
        return newSession;
    }

    @Override
    public List<WdAiSession> listSessions(Long userId, Long bookId) {
        return sessionMapper.selectByUserAndBook(userId, bookId);
    }

    @Override
    public List<WdAiMessage> getMessages(Long sessionId) {
        return messageMapper.selectBySessionId(sessionId);
    }

    @Override
    public List<WdAiMessage> getMessagesPaged(Long sessionId, int offset, int limit) {
        return messageMapper.selectBySessionIdPaged(sessionId, offset, limit);
    }

    @Override
    public int countMessages(Long sessionId) {
        return messageMapper.countBySessionId(sessionId);
    }

    @Override
    public List<WdAiMessage> getRecentMessages(Long sessionId, int limit) {
        return messageMapper.selectRecentBySessionId(sessionId, limit);
    }

    @Override
    @Transactional
    public WdAiMessage saveMessage(Long sessionId, Long parentId, String role, String content, String featureKey) {
        WdAiMessage message = WdAiMessage.builder()
                .sessionId(sessionId)
                .parentId(parentId)
                .role(role)
                .content(content)
                .featureKey(featureKey)
                .build();
        messageMapper.insert(message);

        // 更新会话时间
        sessionMapper.touch(sessionId);

        return message;
    }

    @Override
    public void updateSessionTitle(Long sessionId, String firstUserMessage) {
        if (firstUserMessage == null || firstUserMessage.isEmpty()) return;
        // 截取前30个字符作为标题
        String title = firstUserMessage.length() > 30
                ? firstUserMessage.substring(0, 30) + "..."
                : firstUserMessage;
        sessionMapper.updateTitle(sessionId, title);
    }

    @Override
    @Transactional
    public void deleteSession(Long sessionId, Long userId) {
        sessionMapper.deleteById(sessionId, userId);
        messageMapper.softDeleteBySessionId(sessionId);
    }
}
