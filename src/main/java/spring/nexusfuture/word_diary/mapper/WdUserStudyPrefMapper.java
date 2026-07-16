package spring.nexusfuture.word_diary.mapper;

import org.apache.ibatis.annotations.*;
import spring.nexusfuture.word_diary.entity.WdUserStudyPref;

@Mapper
public interface WdUserStudyPrefMapper {

    @Select("SELECT * FROM wd_user_study_pref WHERE user_id = #{userId}")
    WdUserStudyPref selectByUserId(Long userId);

    @Insert("INSERT INTO wd_user_study_pref(user_id, daily_new_limit, review_unlock_hour, auto_spread_new, timezone, create_time, update_time) " +
            "VALUES(#{userId}, #{dailyNewLimit}, #{reviewUnlockHour}, #{autoSpreadNew}, #{timezone}, NOW(), NOW())")
    int insert(WdUserStudyPref pref);

    @Update("UPDATE wd_user_study_pref SET daily_new_limit = #{dailyNewLimit}, review_unlock_hour = #{reviewUnlockHour}, " +
            "auto_spread_new = #{autoSpreadNew}, timezone = #{timezone}, update_time = NOW() WHERE user_id = #{userId}")
    int update(WdUserStudyPref pref);
}
