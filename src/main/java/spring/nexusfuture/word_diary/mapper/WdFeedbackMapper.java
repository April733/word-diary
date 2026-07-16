package spring.nexusfuture.word_diary.mapper;

import org.apache.ibatis.annotations.*;
import spring.nexusfuture.word_diary.entity.WdFeedback;

import java.util.List;

@Mapper
public interface WdFeedbackMapper {

    @Insert("INSERT INTO wd_feedback(user_id, username, reporter_email, subject, description, error_detail, status, create_time) " +
            "VALUES(#{userId}, #{username}, #{reporterEmail}, #{subject}, #{description}, #{errorDetail}, 0, NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(WdFeedback feedback);

    @Select("SELECT * FROM wd_feedback WHERE is_deleted = 0 ORDER BY create_time DESC")
    List<WdFeedback> selectAll();

    @Select("SELECT * FROM wd_feedback WHERE user_id = #{userId} AND is_deleted = 0 ORDER BY create_time DESC")
    List<WdFeedback> selectByUserId(@Param("userId") Long userId);

    @Select("SELECT * FROM wd_feedback WHERE status = #{status} AND is_deleted = 0 ORDER BY create_time DESC")
    List<WdFeedback> selectByStatus(@Param("status") Integer status);

    @Update("UPDATE wd_feedback SET status = #{status} WHERE id = #{id} AND is_deleted = 0")
    int updateStatus(@Param("id") Long id, @Param("status") Integer status);

    @Update("UPDATE wd_feedback SET is_deleted = 1 WHERE id = #{id}")
    int softDelete(@Param("id") Long id);
}