package spring.nexusfuture.word_diary.mapper;

import org.apache.ibatis.annotations.*;
import spring.nexusfuture.word_diary.entity.SysAiModel;

import java.util.List;

@Mapper
public interface SysAiModelMapper {

    @Select("SELECT * FROM sys_ai_model WHERE is_deleted = 0 ORDER BY sort_num ASC, id ASC")
    List<SysAiModel> selectAll();

    @Select("SELECT * FROM sys_ai_model WHERE id = #{id} AND is_deleted = 0")
    SysAiModel selectById(Long id);

    @Select("SELECT * FROM sys_ai_model WHERE scene_key = #{sceneKey} AND is_deleted = 0 AND is_enabled = 1 LIMIT 1")
    SysAiModel selectEnabledBySceneKey(String sceneKey);

    @Select("SELECT * FROM sys_ai_model WHERE scene_key = #{sceneKey} AND is_deleted = 0 LIMIT 1")
    SysAiModel selectBySceneKey(String sceneKey);

    /** 按模型名取一条定价（同模型多场景时取 sort_num 最小） */
    @Select("SELECT * FROM sys_ai_model WHERE model_key = #{modelKey} AND is_deleted = 0 AND is_enabled = 1 " +
            "ORDER BY sort_num ASC, id ASC LIMIT 1")
    SysAiModel selectByModelKey(String modelKey);

    @Select("SELECT * FROM sys_ai_model WHERE scene_key = 'default' AND is_enabled = 1 AND is_deleted = 0 LIMIT 1")
    SysAiModel selectDefault();

    @Insert("INSERT INTO sys_ai_model(scene_key, scene_name, model_key, display_name, provider, currency, " +
            "input_price_per_million, output_price_per_million, sort_num, is_enabled, remark, create_time, update_time) " +
            "VALUES(#{sceneKey}, #{sceneName}, #{modelKey}, #{displayName}, #{provider}, #{currency}, " +
            "#{inputPricePerMillion}, #{outputPricePerMillion}, #{sortNum}, #{isEnabled}, #{remark}, NOW(), NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(SysAiModel model);

    @Update("UPDATE sys_ai_model SET scene_key=#{sceneKey}, scene_name=#{sceneName}, model_key=#{modelKey}, " +
            "display_name=#{displayName}, provider=#{provider}, currency=#{currency}, " +
            "input_price_per_million=#{inputPricePerMillion}, output_price_per_million=#{outputPricePerMillion}, " +
            "sort_num=#{sortNum}, is_enabled=#{isEnabled}, remark=#{remark}, update_time=NOW() " +
            "WHERE id=#{id} AND is_deleted=0")
    int update(SysAiModel model);

    @Update("UPDATE sys_ai_model SET model_key=#{modelKey}, display_name=#{displayName}, provider=#{provider}, " +
            "currency=#{currency}, input_price_per_million=#{inputPricePerMillion}, " +
            "output_price_per_million=#{outputPricePerMillion}, remark=#{remark}, update_time=NOW() " +
            "WHERE scene_key=#{sceneKey} AND is_deleted=0")
    int updateModelBySceneKey(SysAiModel model);

    @Update("UPDATE sys_ai_model SET is_deleted = 1, update_time = NOW() WHERE id = #{id}")
    int softDelete(Long id);
}
