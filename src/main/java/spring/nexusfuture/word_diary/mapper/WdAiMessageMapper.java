package spring.nexusfuture.word_diary.mapper;

import org.apache.ibatis.annotations.*;
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
}
