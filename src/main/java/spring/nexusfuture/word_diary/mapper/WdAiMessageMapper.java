package spring.nexusfuture.word_diary.mapper;

import org.apache.ibatis.annotations.*;
import spring.nexusfuture.word_diary.dto.response.AdminAiMessageDetailVO;
import spring.nexusfuture.word_diary.dto.response.AdminAiMessageListItemVO;
import spring.nexusfuture.word_diary.entity.WdAiMessage;

import java.util.List;

@Mapper
public interface WdAiMessageMapper {

    @Insert("INSERT INTO wd_ai_message(session_id, parent_id, role, content, feature_key, create_time) " +
            "VALUES(#{sessionId}, #{parentId}, #{role}, #{content}, #{featureKey}, NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(WdAiMessage message);

    /** 更新消息内容（用于将 assistant 回答回填到已存在的消息记录） */
    @Update("UPDATE wd_ai_message SET content = #{content} WHERE id = #{id} AND is_deleted = 0")
    int updateContent(@Param("id") Long id, @Param("content") String content);

    @Select("SELECT * FROM wd_ai_message WHERE id = #{id} AND is_deleted = 0")
    WdAiMessage selectById(@Param("id") Long id);

    @Select("SELECT * FROM wd_ai_message WHERE session_id = #{sessionId} AND is_deleted = 0 ORDER BY id ASC")
    List<WdAiMessage> selectBySessionId(@Param("sessionId") Long sessionId);

    /** 分页查询会话消息，按 id DESC 倒序，用于前端加载更多历史记录 */
    @Select("SELECT * FROM wd_ai_message WHERE session_id = #{sessionId} AND is_deleted = 0 " +
            "ORDER BY id DESC LIMIT #{offset}, #{limit}")
    List<WdAiMessage> selectBySessionIdPaged(@Param("sessionId") Long sessionId,
                                              @Param("offset") int offset,
                                              @Param("limit") int limit);

    /** 查询会话消息总数 */
    @Select("SELECT COUNT(*) FROM wd_ai_message WHERE session_id = #{sessionId} AND is_deleted = 0")
    int countBySessionId(@Param("sessionId") Long sessionId);

    /** 获取会话最近 N 条消息，用于拼入 AI 上下文 */
    @Select("SELECT * FROM wd_ai_message WHERE session_id = #{sessionId} AND is_deleted = 0 " +
            "ORDER BY id DESC LIMIT #{limit}")
    List<WdAiMessage> selectRecentBySessionId(@Param("sessionId") Long sessionId, @Param("limit") int limit);

    /** 软删除会话所有消息 */
    @Update("UPDATE wd_ai_message SET is_deleted = 1 WHERE session_id = #{sessionId}")
    int softDeleteBySessionId(@Param("sessionId") Long sessionId);

    /** 清理超过保留天数的旧消息 */
    @Delete("DELETE FROM wd_ai_message WHERE create_time < DATE_SUB(NOW(), INTERVAL #{days} DAY)")
    int deleteExpiredMessages(@Param("days") int days);

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM wd_ai_message m
            INNER JOIN wd_ai_session s ON s.id = m.session_id AND s.is_deleted = 0
            WHERE m.is_deleted = 0
            <if test="sessionId != null">AND m.session_id = #{sessionId}</if>
            <if test="userId != null">AND s.user_id = #{userId}</if>
            </script>
            """)
    long countAdminMessages(@Param("sessionId") Long sessionId, @Param("userId") Long userId);

    @Select("""
            <script>
            SELECT m.id, m.session_id AS sessionId, s.user_id AS userId, u.username AS username,
                   s.book_id AS bookId, s.title AS sessionTitle, m.parent_id AS parentId,
                   m.role, LEFT(m.content, 200) AS contentPreview, m.feature_key AS featureKey,
                   m.create_time AS createTime
            FROM wd_ai_message m
            INNER JOIN wd_ai_session s ON s.id = m.session_id AND s.is_deleted = 0
            LEFT JOIN wd_user u ON u.id = s.user_id
            WHERE m.is_deleted = 0
            <if test="sessionId != null">AND m.session_id = #{sessionId}</if>
            <if test="userId != null">AND s.user_id = #{userId}</if>
            ORDER BY m.create_time DESC
            LIMIT #{offset}, #{size}
            </script>
            """)
    List<AdminAiMessageListItemVO> selectAdminMessages(@Param("sessionId") Long sessionId,
                                                       @Param("userId") Long userId,
                                                       @Param("offset") int offset,
                                                       @Param("size") int size);

    @Select("""
            SELECT m.id, m.session_id AS sessionId, s.user_id AS userId, u.username AS username,
                   s.book_id AS bookId, s.title AS sessionTitle, m.parent_id AS parentId,
                   m.role, m.content, m.feature_key AS featureKey, m.create_time AS createTime
            FROM wd_ai_message m
            INNER JOIN wd_ai_session s ON s.id = m.session_id AND s.is_deleted = 0
            LEFT JOIN wd_user u ON u.id = s.user_id
            WHERE m.id = #{id} AND m.is_deleted = 0
            """)
    AdminAiMessageDetailVO selectAdminMessageDetail(Long id);
}
