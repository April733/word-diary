package spring.nexusfuture.word_diary.mapper;

import org.apache.ibatis.annotations.*;
import spring.nexusfuture.word_diary.entity.WdUserBenefit;

import java.util.List;

@Mapper
public interface WdUserBenefitMapper {

    @Insert("INSERT INTO wd_user_benefit(user_id, tier_snapshot, max_bookshelves, max_wordbooks, " +
            "max_words_per_book, daily_import_limit, daily_ai_reading_limit, is_ai_enabled, " +
            "channel, channel_ref_id, acquired_at, expired_at, create_time, update_time) " +
            "VALUES(#{userId}, #{tierSnapshot}, #{maxBookshelves}, #{maxWordbooks}, " +
            "#{maxWordsPerBook}, #{dailyImportLimit}, #{dailyAiReadingLimit}, #{isAiEnabled}, " +
            "#{channel}, #{channelRefId}, #{acquiredAt}, #{expiredAt}, NOW(), NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(WdUserBenefit benefit);

    /** 查询用户当前有效的权益（未过期） */
    @Select("SELECT * FROM wd_user_benefit WHERE user_id = #{userId} AND is_deleted = 0 " +
            "AND (expired_at IS NULL OR expired_at > NOW()) ORDER BY acquired_at DESC")
    List<WdUserBenefit> selectActiveByUserId(Long userId);

    /** 查询用户最新一条权益记录 */
    @Select("SELECT * FROM wd_user_benefit WHERE user_id = #{userId} AND is_deleted = 0 " +
            "ORDER BY acquired_at DESC LIMIT 1")
    WdUserBenefit selectLatestByUserId(Long userId);

    /** 查询用户所有权益记录 */
    @Select("SELECT * FROM wd_user_benefit WHERE user_id = #{userId} AND is_deleted = 0 " +
            "ORDER BY acquired_at DESC")
    List<WdUserBenefit> selectAllByUserId(Long userId);
}
