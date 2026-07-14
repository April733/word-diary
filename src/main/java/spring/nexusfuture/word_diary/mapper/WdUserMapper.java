package spring.nexusfuture.word_diary.mapper;

import org.apache.ibatis.annotations.*;
import spring.nexusfuture.word_diary.entity.WdUser;

@Mapper
public interface WdUserMapper {

    @Insert("INSERT INTO wd_user(username, nickname, password, email, phone, is_member, member_tier, create_time, update_time) " +
            "VALUES(#{username}, #{nickname}, #{password}, #{email}, #{phone}, #{isMember}, #{memberTier}, NOW(), NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(WdUser user);

    @Select("SELECT * FROM wd_user WHERE username = #{username} AND is_deleted = 0")
    WdUser selectByUsername(String username);

    @Select("SELECT * FROM wd_user WHERE phone = #{phone} AND is_deleted = 0 LIMIT 1")
    WdUser selectByPhone(String phone);

    @Select("SELECT * FROM wd_user WHERE id = #{id} AND is_deleted = 0")
    WdUser selectById(Long id);

    @Update("UPDATE wd_user SET preferred_locale = #{locale}, update_time = NOW() WHERE id = #{userId}")
    int updateLocale(@Param("userId") Long userId, @Param("locale") String locale);

    @Update("UPDATE wd_user SET password = #{password}, update_time = NOW() WHERE id = #{userId}")
    int updatePassword(@Param("userId") Long userId, @Param("password") String password);
}
