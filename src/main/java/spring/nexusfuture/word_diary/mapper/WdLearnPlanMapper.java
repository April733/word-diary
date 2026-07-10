package spring.nexusfuture.word_diary.mapper;

import org.apache.ibatis.annotations.*;
import spring.nexusfuture.word_diary.entity.WdLearnPlan;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface WdLearnPlanMapper {

    @Insert("INSERT INTO wd_learn_plan(user_id, notebook_id, word_id, learn_status, current_level, " +
            "first_study_time, next_review_time, review_times, create_time, update_time) " +
            "VALUES(#{userId}, #{notebookId}, #{wordId}, #{learnStatus}, #{currentLevel}, " +
            "#{firstStudyTime}, #{nextReviewTime}, #{reviewTimes}, NOW(), NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(WdLearnPlan plan);

    @Select("SELECT * FROM wd_learn_plan WHERE id = #{id} AND is_deleted = 0")
    WdLearnPlan selectById(Long id);

    @Select("SELECT * FROM wd_learn_plan WHERE user_id = #{userId} AND word_id = #{wordId} AND is_deleted = 0")
    WdLearnPlan selectByUserIdAndWordId(@Param("userId") Long userId, @Param("wordId") Long wordId);

    // Today's new words to learn (status=0 not yet studied, status=4 forgotten and need relearn)
    @Select("SELECT * FROM wd_learn_plan WHERE user_id = #{userId} AND learn_status IN (0, 4) AND is_deleted = 0")
    List<WdLearnPlan> selectNewWords(Long userId);

    // Today's review tasks: next_review_time date < today, or date = today AND now >= 7:00
    // Ensures reviews only appear after 7am, regardless of when the word was learned yesterday
    @Select("SELECT * FROM wd_learn_plan WHERE user_id = #{userId} AND learn_status IN (1, 2) " +
            "AND (DATE(next_review_time) < CURDATE() " +
            "OR (DATE(next_review_time) = CURDATE() AND HOUR(NOW()) >= 7)) " +
            "AND is_deleted = 0 ORDER BY next_review_time ASC")
    List<WdLearnPlan> selectReviewTasks(Long userId);

    @Update("UPDATE wd_learn_plan SET learn_status = #{learnStatus}, current_level = #{currentLevel}, " +
            "first_study_time = #{firstStudyTime}, next_review_time = #{nextReviewTime}, " +
            "review_times = #{reviewTimes}, update_time = NOW() WHERE id = #{id}")
    int updatePlan(WdLearnPlan plan);

    @Select("SELECT * FROM wd_learn_plan WHERE user_id = #{userId} AND notebook_id = #{notebookId} AND is_deleted = 0")
    List<WdLearnPlan> selectByUserIdAndNotebookId(@Param("userId") Long userId, @Param("notebookId") Long notebookId);

    @Select("SELECT * FROM wd_learn_plan WHERE user_id = #{userId} AND is_deleted = 0")
    List<WdLearnPlan> selectAllByUserId(Long userId);

    @Update("UPDATE wd_learn_plan SET is_deleted = 1, update_time = NOW() WHERE word_id = #{wordId} AND is_deleted = 0")
    int deleteByWordId(Long wordId);
}
