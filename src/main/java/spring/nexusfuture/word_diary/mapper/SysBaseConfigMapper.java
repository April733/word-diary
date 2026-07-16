package spring.nexusfuture.word_diary.mapper;

import org.apache.ibatis.annotations.*;
import spring.nexusfuture.word_diary.entity.SysBaseConfig;

import java.util.List;

@Mapper
public interface SysBaseConfigMapper {

    @Select("SELECT * FROM sys_base_config WHERE config_key = #{key} AND is_deleted = 0 AND is_enabled = 1 LIMIT 1")
    SysBaseConfig selectByKey(String key);

    @Select("SELECT * FROM sys_base_config WHERE is_deleted = 0 ORDER BY category, sort_num, id")
    List<SysBaseConfig> selectAll();

    @Select("SELECT * FROM sys_base_config WHERE category = #{category} AND is_deleted = 0 AND is_enabled = 1 " +
            "ORDER BY sort_num, id")
    List<SysBaseConfig> selectByCategory(String category);

    @Update("UPDATE sys_base_config SET config_value = #{configValue}, update_time = NOW() " +
            "WHERE config_key = #{configKey} AND is_deleted = 0")
    int updateValue(@Param("configKey") String configKey, @Param("configValue") String configValue);
}
