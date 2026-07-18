package spring.nexusfuture.word_diary.service;

import spring.nexusfuture.word_diary.entity.WdAiMessage;
import spring.nexusfuture.word_diary.entity.WdAiSession;

import java.util.List;

public interface AiSessionService {

    /**
     * 获取或创建会话。如果用户在该书下已有最近会话（24h内），返回它；否则创建新会话。
     */
    WdAiSession getOrCreateSession(Long userId, Long bookId);

    /**
     * 获取某书的所有会话列表
     */
    List<WdAiSession> listSessions(Long userId, Long bookId);

    /**
     * 获取会话的所有消息
     */
    List<WdAiMessage> getMessages(Long sessionId);

    /**
     * 分页获取会话消息（从旧到新排序），用于前端滚动加载历史记录
     */
    List<WdAiMessage> getMessagesPaged(Long sessionId, int offset, int limit);

    /**
     * 获取会话消息总数
     */
    int countMessages(Long sessionId);

    /**
     * 获取会话最近 N 条消息（用于拼入 AI 上下文）
     */
    List<WdAiMessage> getRecentMessages(Long sessionId, int limit);

    /**
     * 保存一条消息
     * @param parentId 关联的问题ID（assistant消息指向对应user消息的id，user消息传null）
     */
    WdAiMessage saveMessage(Long sessionId, Long parentId, String role, String content, String featureKey);

    /**
     * 更新会话标题（取第一条用户消息截断；仅当标题仍为默认「新对话」时）
     */
    void updateSessionTitle(Long sessionId, String firstUserMessage);

    /**
     * 用户自定义重命名会话
     */
    void renameSession(Long sessionId, Long userId, String title);

    /**
     * 删除会话（软删除）
     */
    void deleteSession(Long sessionId, Long userId);
}
