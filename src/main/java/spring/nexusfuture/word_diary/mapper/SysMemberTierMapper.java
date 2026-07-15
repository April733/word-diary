package spring.nexusfuture.word_diary.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import spring.nexusfuture.word_diary.entity.SysMemberTier;

import java.util.List;

@Mapper
public interface SysMemberTierMapper {

    @Select("SELECT * FROM sys_member_tier WHERE tier = #{tier} AND is_deleted = 0")
    SysMemberTier selectByTier(Integer tier);

    @Select("SELECT * FROM sys_member_tier WHERE is_deleted = 0 ORDER BY tier ASC")
    List<SysMemberTier> selectAll();
}
