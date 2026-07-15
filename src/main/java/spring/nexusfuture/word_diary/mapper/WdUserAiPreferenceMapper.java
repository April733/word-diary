package spring.nexusfuture.word_diary.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import spring.nexusfuture.word_diary.entity.WdUserAiPreference;

@Mapper
public interface WdUserAiPreferenceMapper {

    @Select("SELECT * FROM wd_user_ai_preference WHERE user_id = #{userId} LIMIT 1")
    WdUserAiPreference selectByUserId(@Param("userId") Long userId);

    @Insert("INSERT INTO wd_user_ai_preference (user_id, feature_keys, feature_order) VALUES (#{userId}, #{featureKeys}, #{featureOrder})")
    int insert(WdUserAiPreference preference);

    @Update("UPDATE wd_user_ai_preference SET feature_keys = #{featureKeys}, feature_order = #{featureOrder} WHERE user_id = #{userId}")
    int updateByUserId(@Param("userId") Long userId, @Param("featureKeys") String featureKeys, @Param("featureOrder") String featureOrder);
}
