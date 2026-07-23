package spring.nexusfuture.word_diary.service;

import spring.nexusfuture.word_diary.entity.WdAiMessage;
import spring.nexusfuture.word_diary.entity.WdAiSession;

import java.util.List;

/** AI 阅读助手会话与消息持久化。 */
public interface AiSessionService {

    /**
     * 获取或创建会话：用户在该书下 24h 内有最近会话则复用，否则新建。
     *
     * @param userId 当前用户 ID
     * @param bookId 书籍 ID
     * @return 会话实体
     */
    WdAiSession getOrCreateSession(Long userId, Long bookId);

    /**
     * 获取某本书下的全部 AI 会话列表（按最近活跃排序）。
     *
     * @param userId 当前用户 ID
     * @param bookId 书籍 ID
     * @return 会话列表
     */
    List<WdAiSession> listSessions(Long userId, Long bookId);

    /**
     * 获取会话的全部消息（从旧到新）。
     *
     * @param sessionId 会话 ID
     * @return 消息列表
     */
    List<WdAiMessage> getMessages(Long sessionId);

    /**
     * 分页获取会话消息（从旧到新），用于前端滚动加载历史。
     *
     * @param sessionId 会话 ID
     * @param offset 偏移量（从 0 起）
     * @param limit 每页条数
     * @return 消息列表
     */
    List<WdAiMessage> getMessagesPaged(Long sessionId, int offset, int limit);

    /**
     * 获取会话消息总数。
     *
     * @param sessionId 会话 ID
     * @return 消息条数
     */
    int countMessages(Long sessionId);

    /**
     * 获取会话最近 N 条消息，用于拼入 AI 多轮上下文。
     *
     * @param sessionId 会话 ID
     * @param limit 最多条数
     * @return 最近消息列表（时间正序）
     */
    List<WdAiMessage> getRecentMessages(Long sessionId, int limit);

    /**
     * 保存一条会话消息。
     *
     * @param sessionId 会话 ID
     * @param parentId 关联的问题 ID（assistant 指向对应 user 消息的 id，user 传 null）
     * @param role 角色 user|assistant|system
     * @param content 消息正文
     * @param featureKey 触发的 AI 功能 key，可为空
     * @return 持久化后的消息实体
     */
    WdAiMessage saveMessage(Long sessionId, Long parentId, String role, String content, String featureKey);

    /**
     * 更新会话标题：取第一条用户消息截断；仅当标题仍为默认「新对话」时生效。
     *
     * @param sessionId 会话 ID
     * @param firstUserMessage 首条用户消息全文
     */
    void updateSessionTitle(Long sessionId, String firstUserMessage);

    /**
     * 用户自定义重命名会话（校验归属）。
     *
     * @param sessionId 会话 ID
     * @param userId 当前用户 ID
     * @param title 新标题
     */
    void renameSession(Long sessionId, Long userId, String title);

    /**
     * 二次编辑：更新助手消息正文（校验会话归属）。
     *
     * @param messageId 消息 ID
     * @param userId 当前用户 ID
     * @param content 新正文
     */
    void updateMessageContent(Long messageId, Long userId, String content);

    /**
     * 软删除会话（校验归属）。
     *
     * @param sessionId 会话 ID
     * @param userId 当前用户 ID
     */
    void deleteSession(Long sessionId, Long userId);
}
