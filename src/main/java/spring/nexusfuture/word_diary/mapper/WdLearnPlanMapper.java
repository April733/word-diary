package spring.nexusfuture.word_diary.mapper;

import org.apache.ibatis.annotations.*;
import spring.nexusfuture.word_diary.entity.WdLearnPlan;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface WdLearnPlanMapper {

    @Insert("INSERT INTO wd_learn_plan(user_id, notebook_id, word_id, learn_status, current_level, " +
            "first_study_time, next_review_time, review_times, interval_days, ease_factor, rep_count, " +
            "lapse_count, priority_score, create_time, update_time) " +
            "VALUES(#{userId}, #{notebookId}, #{wordId}, #{learnStatus}, #{currentLevel}, " +
            "#{firstStudyTime}, #{nextReviewTime}, #{reviewTimes}, " +
            "COALESCE(#{intervalDays}, 0), COALESCE(#{easeFactor}, 2.50), COALESCE(#{repCount}, 0), " +
            "COALESCE(#{lapseCount}, 0), COALESCE(#{priorityScore}, 0), NOW(), NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(WdLearnPlan plan);

    @Select("SELECT * FROM wd_learn_plan WHERE id = #{id} AND is_deleted = 0")
    WdLearnPlan selectById(Long id);

    @Select("SELECT * FROM wd_learn_plan WHERE user_id = #{userId} AND word_id = #{wordId} AND is_deleted = 0")
    WdLearnPlan selectByUserIdAndWordId(@Param("userId") Long userId, @Param("wordId") Long wordId);

    @Select("SELECT p.* FROM wd_learn_plan p " +
            "INNER JOIN wd_word w ON w.id = p.word_id AND w.is_deleted = 0 " +
            "INNER JOIN wd_word_book b ON b.id = p.notebook_id AND b.is_deleted = 0 AND b.status = 1 " +
            "WHERE p.user_id = #{userId} AND p.learn_status = 0 AND p.is_deleted = 0")
    List<WdLearnPlan> selectNewWords(Long userId);

    @Select("SELECT p.* FROM wd_learn_plan p " +
            "INNER JOIN wd_word w ON w.id = p.word_id AND w.is_deleted = 0 " +
            "INNER JOIN wd_word_book b ON b.id = p.notebook_id AND b.is_deleted = 0 AND b.status = 1 " +
            "WHERE p.user_id = #{userId} AND p.learn_status IN (1, 2) " +
            "AND (DATE(p.next_review_time) < CURDATE() " +
            "OR (DATE(p.next_review_time) = CURDATE() AND HOUR(NOW()) >= #{unlockHour})) " +
            "AND p.is_deleted = 0 ORDER BY p.priority_score DESC, p.next_review_time ASC")
    List<WdLearnPlan> selectReviewTasks(@Param("userId") Long userId, @Param("unlockHour") int unlockHour);

    @Select("SELECT p.* FROM wd_learn_plan p " +
            "INNER JOIN wd_word w ON w.id = p.word_id AND w.is_deleted = 0 " +
            "INNER JOIN wd_word_book b ON b.id = p.notebook_id AND b.is_deleted = 0 AND b.status = 1 " +
            "WHERE p.user_id = #{userId} AND p.learn_status = 0 " +
            "AND p.scheduled_new_date IS NULL AND p.is_deleted = 0 ORDER BY p.id ASC")
    List<WdLearnPlan> selectUnscheduledNew(Long userId);

    /** 今日应学新词：含排期为今天，以及未学完的逾期（scheduled_new_date &lt; today） */
    @Select("SELECT p.* FROM wd_learn_plan p " +
            "INNER JOIN wd_word w ON w.id = p.word_id AND w.is_deleted = 0 " +
            "INNER JOIN wd_word_book b ON b.id = p.notebook_id AND b.is_deleted = 0 AND b.status = 1 " +
            "WHERE p.user_id = #{userId} AND p.learn_status = 0 " +
            "AND p.scheduled_new_date IS NOT NULL AND p.scheduled_new_date <= #{today} " +
            "AND p.is_deleted = 0 ORDER BY p.scheduled_new_date ASC, p.new_alloc_seq ASC, p.id ASC")
    List<WdLearnPlan> selectTodayNewWords(@Param("userId") Long userId, @Param("today") LocalDate today);

    @Update("UPDATE wd_learn_plan SET learn_status = #{learnStatus}, current_level = #{currentLevel}, " +
            "first_study_time = #{firstStudyTime}, next_review_time = #{nextReviewTime}, " +
            "review_times = #{reviewTimes}, interval_days = #{intervalDays}, ease_factor = #{easeFactor}, " +
            "rep_count = #{repCount}, lapse_count = #{lapseCount}, last_feedback = #{lastFeedback}, " +
            "last_study_at = #{lastStudyAt}, priority_score = #{priorityScore}, " +
            "scheduled_new_date = #{scheduledNewDate}, new_alloc_seq = #{newAllocSeq}, update_time = NOW() " +
            "WHERE id = #{id}")
    int updatePlan(WdLearnPlan plan);

    @Update("UPDATE wd_learn_plan SET scheduled_new_date = #{scheduledNewDate}, new_alloc_seq = #{newAllocSeq}, " +
            "update_time = NOW() WHERE id = #{id}")
    int updateSchedule(WdLearnPlan plan);

    @Select("<script>SELECT COUNT(*) FROM wd_learn_plan p " +
            "INNER JOIN wd_word w ON w.id = p.word_id AND w.is_deleted = 0 " +
            "INNER JOIN wd_word_book b ON b.id = p.notebook_id AND b.is_deleted = 0 " +
            "WHERE p.user_id = #{userId} AND p.learn_status IN (1, 2, 3) AND p.is_deleted = 0" +
            "<if test='notebookId != null'> AND p.notebook_id = #{notebookId}</if></script>")
    int countBrowseWords(@Param("userId") Long userId, @Param("notebookId") Long notebookId);

    @Select("<script>SELECT p.* FROM wd_learn_plan p " +
            "INNER JOIN wd_word w ON w.id = p.word_id AND w.is_deleted = 0 " +
            "INNER JOIN wd_word_book b ON b.id = p.notebook_id AND b.is_deleted = 0 " +
            "WHERE p.user_id = #{userId} AND p.learn_status IN (1, 2, 3) AND p.is_deleted = 0" +
            "<if test='notebookId != null'> AND p.notebook_id = #{notebookId}</if> " +
            "ORDER BY COALESCE(p.last_study_at, p.update_time) DESC, p.id DESC LIMIT #{limit} OFFSET #{offset}</script>")
    List<WdLearnPlan> selectBrowseWords(@Param("userId") Long userId, @Param("notebookId") Long notebookId,
                                        @Param("limit") int limit, @Param("offset") int offset);

    @Update("UPDATE wd_learn_plan SET learn_status = 0, update_time = NOW() " +
            "WHERE user_id = #{userId} AND learn_status = 4 AND is_deleted = 0")
    int migrateForgottenToNotLearned(Long userId);

    @Select("SELECT * FROM wd_learn_plan WHERE user_id = #{userId} AND notebook_id = #{notebookId} AND is_deleted = 0")
    List<WdLearnPlan> selectByUserIdAndNotebookId(@Param("userId") Long userId, @Param("notebookId") Long notebookId);

    @Select("SELECT p.* FROM wd_learn_plan p " +
            "INNER JOIN wd_word w ON w.id = p.word_id AND w.is_deleted = 0 " +
            "INNER JOIN wd_word_book b ON b.id = p.notebook_id AND b.is_deleted = 0 " +
            "WHERE p.user_id = #{userId} AND p.is_deleted = 0")
    List<WdLearnPlan> selectAllByUserId(Long userId);

    @Update("UPDATE wd_learn_plan SET is_deleted = 1, update_time = NOW() WHERE word_id = #{wordId} AND is_deleted = 0")
    int deleteByWordId(Long wordId);

    @Update("UPDATE wd_learn_plan SET is_deleted = 0, update_time = NOW() WHERE word_id = #{wordId} AND is_deleted = 1")
    int restoreByWordId(Long wordId);

    @Update("UPDATE wd_learn_plan SET is_deleted = 1, update_time = NOW() WHERE notebook_id = #{notebookId} AND is_deleted = 0")
    int softDeleteByNotebookId(Long notebookId);

    /** 关闭单词本参与学习时，清掉未学新词的排期，重新开启后再重新分配 */
    @Update("UPDATE wd_learn_plan SET scheduled_new_date = NULL, new_alloc_seq = NULL, update_time = NOW() " +
            "WHERE notebook_id = #{notebookId} AND learn_status = 0 AND is_deleted = 0")
    int clearNewScheduleByNotebookId(Long notebookId);

    @Delete("DELETE p FROM wd_learn_plan p LEFT JOIN wd_word w ON w.id = p.word_id WHERE w.id IS NULL")
    int hardDeleteOrphanPlans();
}
