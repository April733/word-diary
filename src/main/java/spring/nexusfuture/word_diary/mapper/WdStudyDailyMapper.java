package spring.nexusfuture.word_diary.mapper;

import org.apache.ibatis.annotations.*;
import spring.nexusfuture.word_diary.entity.WdStudyDaily;

import java.time.LocalDate;

@Mapper
public interface WdStudyDailyMapper {

    @Select("SELECT * FROM wd_study_daily WHERE user_id = #{userId} AND study_date = #{studyDate}")
    WdStudyDaily selectByUserAndDate(@Param("userId") Long userId, @Param("studyDate") LocalDate studyDate);

    @Insert("INSERT INTO wd_study_daily(user_id, study_date, review_total, review_done, new_total, new_done, " +
            "review_phase_done, status, create_time, update_time) " +
            "VALUES(#{userId}, #{studyDate}, #{reviewTotal}, #{reviewDone}, #{newTotal}, #{newDone}, " +
            "#{reviewPhaseDone}, #{status}, NOW(), NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(WdStudyDaily daily);

    @Update("UPDATE wd_study_daily SET review_total = #{reviewTotal}, review_done = #{reviewDone}, " +
            "new_total = #{newTotal}, new_done = #{newDone}, review_phase_done = #{reviewPhaseDone}, " +
            "status = #{status}, update_time = NOW() WHERE id = #{id}")
    int update(WdStudyDaily daily);
}
