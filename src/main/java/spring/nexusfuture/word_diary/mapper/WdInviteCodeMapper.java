package spring.nexusfuture.word_diary.mapper;

import org.apache.ibatis.annotations.*;
import spring.nexusfuture.word_diary.entity.WdInviteCode;

@Mapper
public interface WdInviteCodeMapper {

    @Insert("INSERT INTO wd_invite_code(seq_no, invite_code, member_tier, create_time, update_time) " +
            "VALUES(#{seqNo}, #{inviteCode}, #{memberTier}, NOW(), NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(WdInviteCode inviteCode);

    @Select("SELECT * FROM wd_invite_code WHERE invite_code = #{code} AND is_deleted = 0")
    WdInviteCode selectByCode(String code);

    @Update("UPDATE wd_invite_code SET used_by = #{userId}, used_time = NOW(), update_time = NOW() " +
            "WHERE invite_code = #{code} AND used_by IS NULL AND is_deleted = 0")
    int useInviteCode(@Param("code") String code, @Param("userId") Long userId);
}
