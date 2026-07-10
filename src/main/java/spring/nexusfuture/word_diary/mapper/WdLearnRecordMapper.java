package spring.nexusfuture.word_diary.mapper;

import org.apache.ibatis.annotations.*;
import spring.nexusfuture.word_diary.entity.WdLearnRecord;
import java.util.List;

@Mapper
public interface WdLearnRecordMapper {

    @Insert("INSERT INTO wd_learn_record(user_id, notebook_id, word_id, study_type, result, study_time, " +
            "create_time, update_time) VALUES(#{userId}, #{notebookId}, #{wordId}, #{studyType}, #{result}, " +
            "#{studyTime}, NOW(), NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(WdLearnRecord record);

    @Select("SELECT * FROM wd_learn_record WHERE user_id = #{userId} AND is_deleted = 0 ORDER BY study_time DESC")
    List<WdLearnRecord> selectByUserId(Long userId);

    @Select("SELECT * FROM wd_learn_record WHERE user_id = #{userId} AND word_id = #{wordId} AND is_deleted = 0 " +
            "ORDER BY study_time DESC")
    List<WdLearnRecord> selectByUserIdAndWordId(@Param("userId") Long userId, @Param("wordId") Long wordId);

    // Today's studied word IDs (distinct, to avoid duplicates from multiple study actions)
    @Select("SELECT DISTINCT word_id FROM wd_learn_record WHERE user_id = #{userId} " +
            "AND DATE(study_time) = CURDATE() AND is_deleted = 0")
    List<Long> selectTodayStudiedWordIds(Long userId);

    // Today's completed (remembered) word IDs
    @Select("SELECT DISTINCT word_id FROM wd_learn_record WHERE user_id = #{userId} " +
            "AND DATE(study_time) = CURDATE() AND result = 1 AND is_deleted = 0")
    List<Long> selectTodayCompletedWordIds(Long userId);
}
