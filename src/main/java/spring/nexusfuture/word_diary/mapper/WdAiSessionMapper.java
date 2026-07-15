package spring.nexusfuture.word_diary.mapper;

import org.apache.ibatis.annotations.*;
import spring.nexusfuture.word_diary.entity.WdAiMessage;
import spring.nexusfuture.word_diary.entity.WdAiSession;

import java.util.List;

@Mapper
public interface WdAiSessionMapper {

    @Insert("INSERT INTO wd_ai_session(user_id, book_id, title, create_time, update_time) " +
            "VALUES(#{userId}, #{bookId}, #{title}, NOW(), NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(WdAiSession session);

    @Select("SELECT * FROM wd_ai_session WHERE id = #{id} AND is_deleted = 0")
    WdAiSession selectById(@Param("id") Long id);

    @Select("SELECT * FROM wd_ai_session WHERE user_id = #{userId} AND book_id = #{bookId} AND is_deleted = 0 " +
            "ORDER BY update_time DESC")
    List<WdAiSession> selectByUserAndBook(@Param("userId") Long userId, @Param("bookId") Long bookId);

    @Update("UPDATE wd_ai_session SET title = #{title}, update_time = NOW() WHERE id = #{id} AND is_deleted = 0")
    int updateTitle(@Param("id") Long id, @Param("title") String title);

    @Update("UPDATE wd_ai_session SET update_time = NOW() WHERE id = #{id} AND is_deleted = 0")
    int touch(@Param("id") Long id);

    @Update("UPDATE wd_ai_session SET is_deleted = 1, update_time = NOW() WHERE id = #{id} AND user_id = #{userId}")
    int deleteById(@Param("id") Long id, @Param("userId") Long userId);
}
