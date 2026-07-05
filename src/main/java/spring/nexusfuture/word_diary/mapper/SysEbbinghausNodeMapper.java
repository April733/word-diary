package spring.nexusfuture.word_diary.mapper;

import org.apache.ibatis.annotations.*;
import spring.nexusfuture.word_diary.entity.SysEbbinghausNode;
import java.util.List;

@Mapper
public interface SysEbbinghausNodeMapper {

    @Select("SELECT * FROM sys_ebbinghaus_node WHERE is_deleted = 0 ORDER BY sort ASC")
    List<SysEbbinghausNode> selectAll();

    @Select("SELECT * FROM sys_ebbinghaus_node WHERE id = #{id} AND is_deleted = 0")
    SysEbbinghausNode selectById(Integer id);

    @Select("SELECT * FROM sys_ebbinghaus_node WHERE is_deleted = 0 ORDER BY sort ASC LIMIT 1")
    SysEbbinghausNode selectFirstLevel();
}
