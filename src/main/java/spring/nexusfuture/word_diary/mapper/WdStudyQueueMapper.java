package spring.nexusfuture.word_diary.mapper;

import org.apache.ibatis.annotations.*;
import spring.nexusfuture.word_diary.entity.WdStudyQueue;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface WdStudyQueueMapper {

    @Insert("INSERT INTO wd_study_queue(user_id, study_date, plan_id, queue_type, phase, sort_order, state, " +
            "insert_reason, create_time, update_time) " +
            "VALUES(#{userId}, #{studyDate}, #{planId}, #{queueType}, #{phase}, #{sortOrder}, #{state}, " +
            "#{insertReason}, NOW(), NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(WdStudyQueue item);

    @Select("SELECT * FROM wd_study_queue WHERE id = #{id}")
    WdStudyQueue selectById(Long id);

    @Select("SELECT * FROM wd_study_queue WHERE user_id = #{userId} AND study_date = #{studyDate} " +
            "ORDER BY sort_order ASC, id ASC")
    List<WdStudyQueue> selectByUserAndDate(@Param("userId") Long userId, @Param("studyDate") LocalDate studyDate);

    @Select("SELECT * FROM wd_study_queue WHERE user_id = #{userId} AND study_date = #{studyDate} " +
            "AND state = 0 AND phase = #{phase} ORDER BY sort_order ASC, id ASC LIMIT 1")
    WdStudyQueue selectNextPendingByPhase(@Param("userId") Long userId, @Param("studyDate") LocalDate studyDate,
                                          @Param("phase") String phase);

    @Select("SELECT * FROM wd_study_queue WHERE user_id = #{userId} AND study_date = #{studyDate} " +
            "AND state = 0 ORDER BY sort_order ASC, id ASC LIMIT 1")
    WdStudyQueue selectNextPending(@Param("userId") Long userId, @Param("studyDate") LocalDate studyDate);

    @Select("SELECT COUNT(*) FROM wd_study_queue WHERE user_id = #{userId} AND study_date = #{studyDate} " +
            "AND state = 0 AND phase = #{phase}")
    int countPendingByPhase(@Param("userId") Long userId, @Param("studyDate") LocalDate studyDate,
                            @Param("phase") String phase);

    @Select("SELECT * FROM wd_study_queue WHERE user_id = #{userId} AND study_date = #{studyDate} " +
            "AND phase = #{phase} ORDER BY state ASC, sort_order ASC, id ASC")
    List<WdStudyQueue> selectByUserDateAndPhase(@Param("userId") Long userId,
                                                @Param("studyDate") LocalDate studyDate,
                                                @Param("phase") String phase);

    @Select("SELECT COALESCE(MAX(sort_order), 0) FROM wd_study_queue WHERE user_id = #{userId} AND study_date = #{studyDate}")
    int maxSortOrder(@Param("userId") Long userId, @Param("studyDate") LocalDate studyDate);

    @Update("UPDATE wd_study_queue SET state = #{state}, update_time = NOW() WHERE id = #{id}")
    int updateState(@Param("id") Long id, @Param("state") int state);

    @Delete("DELETE FROM wd_study_queue WHERE id = #{id}")
    int deleteById(Long id);

    @Delete("DELETE q FROM wd_study_queue q " +
            "INNER JOIN wd_learn_plan p ON p.id = q.plan_id " +
            "WHERE p.notebook_id = #{notebookId} AND q.state = 0")
    int deletePendingByNotebookId(@Param("notebookId") Long notebookId);

    /** 关闭背诵时：清掉该本在队列中的全部条目（含已完成遍次），避免补第二遍把词再加回来 */
    @Delete("DELETE q FROM wd_study_queue q " +
            "INNER JOIN wd_learn_plan p ON p.id = q.plan_id " +
            "WHERE p.notebook_id = #{notebookId}")
    int deleteAllByNotebookId(@Param("notebookId") Long notebookId);

    @Delete("DELETE q FROM wd_study_queue q " +
            "INNER JOIN wd_learn_plan p ON p.id = q.plan_id " +
            "WHERE p.word_id = #{wordId} AND q.state = 0")
    int deletePendingByWordId(@Param("wordId") Long wordId);

    /** 清掉指定学习日里来自「未开启背诵」单词本的待学项 */
    @Delete("DELETE q FROM wd_study_queue q " +
            "INNER JOIN wd_learn_plan p ON p.id = q.plan_id " +
            "INNER JOIN wd_word_book b ON b.id = p.notebook_id " +
            "WHERE q.user_id = #{userId} AND q.study_date = #{studyDate} " +
            "AND q.state = 0 AND b.status = 0 AND b.is_deleted = 0")
    int deletePendingFromDisabledNotebooks(@Param("userId") Long userId,
                                           @Param("studyDate") LocalDate studyDate);

    @Delete("DELETE FROM wd_study_queue WHERE user_id = #{userId} AND study_date = #{studyDate}")
    int deleteByUserAndDate(@Param("userId") Long userId, @Param("studyDate") LocalDate studyDate);

    @Select("SELECT COUNT(*) FROM wd_study_queue WHERE user_id = #{userId} AND study_date = #{studyDate}")
    int countByUserAndDate(@Param("userId") Long userId, @Param("studyDate") LocalDate studyDate);

    @Select("SELECT COUNT(*) FROM wd_study_queue WHERE user_id = #{userId} AND study_date = #{studyDate} " +
            "AND state = 0")
    int countAllPending(@Param("userId") Long userId, @Param("studyDate") LocalDate studyDate);

    @Select("SELECT COUNT(*) FROM wd_study_queue WHERE user_id = #{userId} AND study_date = #{studyDate} " +
            "AND queue_type = #{queueType} AND state = 1")
    int countDoneByType(@Param("userId") Long userId, @Param("studyDate") LocalDate studyDate,
                        @Param("queueType") String queueType);

    @Select("SELECT COUNT(*) FROM wd_study_queue WHERE user_id = #{userId} AND plan_id = #{planId} AND state = 1 " +
            "AND update_time >= #{windowStart} AND update_time < #{windowEnd}")
    int countDoneByPlan(@Param("userId") Long userId,
                        @Param("planId") Long planId,
                        @Param("windowStart") java.time.LocalDateTime windowStart,
                        @Param("windowEnd") java.time.LocalDateTime windowEnd);

    /**
     * 当前学习日时间窗内，至少完成 minPasses 遍的单词。
     * 用 update_time 落在 [windowStart, windowEnd)，避免 0～7 点睡前记录混入 7 点后的「今日」。
     */
    @Select("SELECT p.word_id FROM wd_study_queue q " +
            "INNER JOIN wd_learn_plan p ON p.id = q.plan_id " +
            "WHERE q.user_id = #{userId} AND q.state = 1 " +
            "AND q.update_time >= #{windowStart} AND q.update_time < #{windowEnd} " +
            "GROUP BY p.word_id HAVING COUNT(*) >= #{minPasses}")
    List<Long> selectWordIdsWithMinDonePasses(@Param("userId") Long userId,
                                              @Param("windowStart") java.time.LocalDateTime windowStart,
                                              @Param("windowEnd") java.time.LocalDateTime windowEnd,
                                              @Param("minPasses") int minPasses);

    /** 某阶段在学习日时间窗内已完成至少 minPasses 遍的单词数 */
    @Select("SELECT COUNT(*) FROM (" +
            "  SELECT p.word_id FROM wd_study_queue q " +
            "  INNER JOIN wd_learn_plan p ON p.id = q.plan_id " +
            "  WHERE q.user_id = #{userId} AND q.state = 1 AND q.phase = #{phase} " +
            "    AND q.update_time >= #{windowStart} AND q.update_time < #{windowEnd} " +
            "  GROUP BY p.word_id HAVING COUNT(*) >= #{minPasses}" +
            ") t")
    int countWordsWithMinDonePassesByPhase(@Param("userId") Long userId,
                                           @Param("phase") String phase,
                                           @Param("windowStart") java.time.LocalDateTime windowStart,
                                           @Param("windowEnd") java.time.LocalDateTime windowEnd,
                                           @Param("minPasses") int minPasses);
}
