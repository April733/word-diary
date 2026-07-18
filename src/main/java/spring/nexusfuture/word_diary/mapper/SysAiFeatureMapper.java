package spring.nexusfuture.word_diary.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import spring.nexusfuture.word_diary.entity.SysAiFeature;

import java.util.List;

@Mapper
public interface SysAiFeatureMapper {

    @Select("SELECT * FROM sys_ai_feature WHERE status = 1 AND is_deleted = 0 ORDER BY sort_num ASC, id ASC")
    List<SysAiFeature> selectActiveList();

    @Select("SELECT * FROM sys_ai_feature WHERE is_deleted = 0 ORDER BY sort_num ASC, id ASC")
    List<SysAiFeature> selectAll();

    @Select("SELECT * FROM sys_ai_feature WHERE id = #{id} AND is_deleted = 0")
    SysAiFeature selectById(Long id);

    @Select("SELECT * FROM sys_ai_feature WHERE feature_key = #{featureKey} AND status = 1 AND is_deleted = 0 LIMIT 1")
    SysAiFeature selectByFeatureKey(@Param("featureKey") String featureKey);

    @Select("SELECT * FROM sys_ai_feature WHERE feature_key = #{featureKey} AND is_deleted = 0 LIMIT 1")
    SysAiFeature selectByFeatureKeyAny(@Param("featureKey") String featureKey);

    @Select("SELECT * FROM sys_ai_feature WHERE is_deleted = 0 AND (name_i18n IS NULL OR name_i18n = '' OR name_i18n = '{}') ORDER BY id ASC")
    List<SysAiFeature> selectMissingI18n();

    @Insert("INSERT INTO sys_ai_feature(feature_key, feature_name, description, name_i18n, description_i18n, prompt, sort_num, status, create_time, update_time) " +
            "VALUES(#{featureKey}, #{featureName}, #{description}, #{nameI18n}, #{descriptionI18n}, #{prompt}, #{sortNum}, #{status}, NOW(), NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(SysAiFeature feature);

    @Update("UPDATE sys_ai_feature SET feature_key=#{featureKey}, feature_name=#{featureName}, description=#{description}, " +
            "name_i18n=#{nameI18n}, description_i18n=#{descriptionI18n}, " +
            "prompt=#{prompt}, sort_num=#{sortNum}, status=#{status}, update_time=NOW() " +
            "WHERE id=#{id} AND is_deleted=0")
    int update(SysAiFeature feature);

    @Update("UPDATE sys_ai_feature SET name_i18n=#{nameI18n}, description_i18n=#{descriptionI18n}, update_time=NOW() " +
            "WHERE id=#{id} AND is_deleted=0")
    int updateI18n(SysAiFeature feature);

    @Update("UPDATE sys_ai_feature SET is_deleted=1, update_time=NOW() WHERE id=#{id}")
    int softDelete(Long id);
}
