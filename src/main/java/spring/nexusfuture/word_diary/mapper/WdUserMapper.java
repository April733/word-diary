package spring.nexusfuture.word_diary.mapper;

import org.apache.ibatis.annotations.*;
import spring.nexusfuture.word_diary.dto.response.AdminUserListItemVO;
import spring.nexusfuture.word_diary.entity.WdUser;

import java.util.List;

@Mapper
public interface WdUserMapper {

    @Insert("INSERT INTO wd_user(username, nickname, password, email, phone, role, create_time, update_time) " +
            "VALUES(#{username}, #{nickname}, #{password}, #{email}, #{phone}, COALESCE(#{role}, 'user'), NOW(), NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(WdUser user);

    @Select("SELECT * FROM wd_user WHERE username = #{username} AND is_deleted = 0")
    WdUser selectByUsername(String username);

    @Select("SELECT * FROM wd_user WHERE phone = #{phone} AND is_deleted = 0 LIMIT 1")
    WdUser selectByPhone(String phone);

    @Select("SELECT * FROM wd_user WHERE email = #{email} AND is_deleted = 0 LIMIT 1")
    WdUser selectByEmail(String email);

    @Select("SELECT * FROM wd_user WHERE id = #{id} AND is_deleted = 0")
    WdUser selectById(Long id);

    @Select("SELECT * FROM wd_user WHERE id = #{id}")
    WdUser selectByIdIncludeDeleted(Long id);

    @Update("UPDATE wd_user SET preferred_locale = #{locale}, update_time = NOW() WHERE id = #{userId}")
    int updateLocale(@Param("userId") Long userId, @Param("locale") String locale);

    @Update("UPDATE wd_user SET preferred_ai_region = #{region}, update_time = NOW() WHERE id = #{userId}")
    int updateAiRegion(@Param("userId") Long userId, @Param("region") String region);

    @Update("UPDATE wd_user SET password = #{password}, update_time = NOW() WHERE id = #{userId}")
    int updatePassword(@Param("userId") Long userId, @Param("password") String password);

    @Update("UPDATE wd_user SET role = #{role}, update_by = #{updateBy}, update_time = NOW() WHERE id = #{userId}")
    int updateRole(@Param("userId") Long userId, @Param("role") String role, @Param("updateBy") Long updateBy);

    @Update("UPDATE wd_user SET is_deleted = #{isDeleted}, update_by = #{updateBy}, update_time = NOW() WHERE id = #{userId}")
    int updateDeleted(@Param("userId") Long userId, @Param("isDeleted") Integer isDeleted, @Param("updateBy") Long updateBy);

    @Select("SELECT COUNT(*) FROM wd_user WHERE role = 'admin' AND is_deleted = 0")
    long countActiveAdmins();

    @Select("""
            <script>
            SELECT COUNT(*) FROM wd_user
            WHERE 1 = 1
            <if test="includeDeleted == null or includeDeleted == false">AND is_deleted = 0</if>
            <if test="role != null and role != ''">AND role = #{role}</if>
            <if test="keyword != null and keyword != ''">
              AND (
                username LIKE CONCAT('%', #{keyword}, '%')
                OR nickname LIKE CONCAT('%', #{keyword}, '%')
                OR email LIKE CONCAT('%', #{keyword}, '%')
                OR phone LIKE CONCAT('%', #{keyword}, '%')
              )
            </if>
            </script>
            """)
    long countAdminUsers(@Param("keyword") String keyword,
                         @Param("role") String role,
                         @Param("includeDeleted") Boolean includeDeleted);

    @Select("""
            <script>
            SELECT u.id, u.username, u.nickname, u.email, u.phone, u.role,
                   u.preferred_locale AS preferredLocale,
                   u.preferred_ai_region AS preferredAiRegion,
                   u.create_time AS createTime, u.update_time AS updateTime,
                   u.is_deleted AS isDeleted,
                   p.level_name AS proficiencyLevel
            FROM wd_user u
            LEFT JOIN wd_user_proficiency p ON p.user_id = u.id
            WHERE 1 = 1
            <if test="includeDeleted == null or includeDeleted == false">AND u.is_deleted = 0</if>
            <if test="role != null and role != ''">AND u.role = #{role}</if>
            <if test="keyword != null and keyword != ''">
              AND (
                u.username LIKE CONCAT('%', #{keyword}, '%')
                OR u.nickname LIKE CONCAT('%', #{keyword}, '%')
                OR u.email LIKE CONCAT('%', #{keyword}, '%')
                OR u.phone LIKE CONCAT('%', #{keyword}, '%')
              )
            </if>
            ORDER BY u.id DESC
            LIMIT #{offset}, #{size}
            </script>
            """)
    List<AdminUserListItemVO> selectAdminUsers(@Param("keyword") String keyword,
                                               @Param("role") String role,
                                               @Param("includeDeleted") Boolean includeDeleted,
                                               @Param("offset") int offset,
                                               @Param("size") int size);
}
