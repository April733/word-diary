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

    @Select("SELECT * FROM sys_ai_model WHERE model_key = #{modelKey} AND is_deleted = 0 LIMIT 1")
    SysAiModel selectByModelKey(String modelKey);

    @Select("SELECT * FROM sys_ai_model WHERE model_key = #{modelKey} AND is_deleted = 0 AND is_enabled = 1 LIMIT 1")
    SysAiModel selectEnabledByModelKey(String modelKey);

    @Insert("INSERT INTO sys_ai_model(model_key, display_name, provider, billing_unit, currency, " +
            "input_price_per_million, output_price_per_million, sort_num, is_enabled, remark, create_time, update_time) " +
            "VALUES(#{modelKey}, #{displayName}, #{provider}, #{billingUnit}, #{currency}, " +
            "#{inputPricePerMillion}, #{outputPricePerMillion}, #{sortNum}, #{isEnabled}, #{remark}, NOW(), NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(SysAiModel model);

    @Update("UPDATE sys_ai_model SET model_key=#{modelKey}, display_name=#{displayName}, provider=#{provider}, " +
            "billing_unit=#{billingUnit}, currency=#{currency}, input_price_per_million=#{inputPricePerMillion}, " +
            "output_price_per_million=#{outputPricePerMillion}, sort_num=#{sortNum}, is_enabled=#{isEnabled}, " +
            "remark=#{remark}, update_time=NOW() WHERE id=#{id} AND is_deleted=0")
    int update(SysAiModel model);

    @Update("UPDATE sys_ai_model SET is_deleted = 1, update_time = NOW() WHERE id = #{id}")
    int softDelete(Long id);

    @Select("SELECT COUNT(*) FROM sys_ai_model WHERE provider = #{provider} AND is_deleted = 0")
    int countByProvider(String provider);
}
