package spring.nexusfuture.word_diary.mapper;

import org.apache.ibatis.annotations.*;
import spring.nexusfuture.word_diary.entity.SysLearnStatus;
import java.util.List;

@Mapper
public interface SysLearnStatusMapper {

    @Select("SELECT * FROM sys_learn_status WHERE is_deleted = 0")
    List<SysLearnStatus> selectAll();

    @Select("SELECT * FROM sys_learn_status WHERE status = #{status} AND is_deleted = 0")
    SysLearnStatus selectByStatus(Integer status);
}
