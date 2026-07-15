package spring.nexusfuture.word_diary.mapper;

import org.apache.ibatis.annotations.*;
import spring.nexusfuture.word_diary.entity.WdReaderSettings;

import java.math.BigDecimal;

@Mapper
public interface WdReaderSettingsMapper {

    @Select("SELECT * FROM wd_reader_settings WHERE user_id=#{userId} AND is_deleted=0")
    WdReaderSettings selectByUserId(@Param("userId") Long userId);

    @Insert("INSERT INTO wd_reader_settings(user_id, font_size, line_height, background_color, text_color, flow, " +
            "create_by, create_time, update_by, update_time, is_deleted) " +
            "VALUES(#{userId}, #{fontSize}, #{lineHeight}, #{backgroundColor}, #{textColor}, #{flow}, " +
            "#{createBy}, #{createTime}, #{updateBy}, #{updateTime}, #{isDeleted})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(WdReaderSettings settings);

    @Update("UPDATE wd_reader_settings SET font_size=#{fontSize}, line_height=#{lineHeight}, " +
            "background_color=#{backgroundColor}, text_color=#{textColor}, flow=#{flow}, " +
            "update_by=#{updateBy}, update_time=NOW() " +
            "WHERE user_id=#{userId} AND is_deleted=0")
    int updateByUserId(WdReaderSettings settings);
}
