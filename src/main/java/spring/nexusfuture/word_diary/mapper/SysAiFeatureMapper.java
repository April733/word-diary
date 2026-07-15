package spring.nexusfuture.word_diary.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import spring.nexusfuture.word_diary.entity.SysAiFeature;

import java.util.List;

@Mapper
public interface SysAiFeatureMapper {

    @Select("SELECT * FROM sys_ai_feature WHERE status = 1 AND is_deleted = 0 ORDER BY sort_num ASC, id ASC")
    List<SysAiFeature> selectActiveList();

    @Select("SELECT * FROM sys_ai_feature WHERE feature_key = #{featureKey} AND status = 1 AND is_deleted = 0 LIMIT 1")
    SysAiFeature selectByFeatureKey(@Param("featureKey") String featureKey);
}
