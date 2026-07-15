package spring.nexusfuture.word_diary.mapper;

import org.apache.ibatis.annotations.*;
import spring.nexusfuture.word_diary.entity.WdBook;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface WdBookMapper {

    @Insert("INSERT INTO wd_book(user_id, title, author, cover_url, file_path, file_type, file_size, " +
            "total_chapters, current_chapter, current_cfi, progress_percent, last_read_at, " +
            "create_by, create_time, update_by, update_time, is_deleted) " +
            "VALUES(#{userId}, #{title}, #{author}, #{coverUrl}, #{filePath}, #{fileType}, #{fileSize}, " +
            "#{totalChapters}, #{currentChapter}, #{currentCfi}, #{progressPercent}, #{lastReadAt}, " +
            "#{createBy}, #{createTime}, #{updateBy}, #{updateTime}, #{isDeleted})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(WdBook book);

    @Update("UPDATE wd_book SET title=#{title}, author=#{author}, cover_url=#{coverUrl}, " +
            "total_chapters=#{totalChapters}, update_time=NOW() " +
            "WHERE id=#{id} AND user_id=#{userId} AND is_deleted=0")
    int updateById(WdBook book);

    @Update("UPDATE wd_book SET is_deleted=1, update_time=NOW() WHERE id=#{id} AND user_id=#{userId}")
    int deleteById(@Param("id") Long id, @Param("userId") Long userId);

    @Select("SELECT * FROM wd_book WHERE id=#{id} AND is_deleted=0")
    WdBook selectById(@Param("id") Long id);

    @Select("SELECT * FROM wd_book WHERE user_id=#{userId} AND is_deleted=0 ORDER BY last_read_at DESC")
    List<WdBook> selectByUserId(@Param("userId") Long userId);

    @Update("UPDATE wd_book SET current_chapter=#{currentChapter}, current_cfi=#{currentCfi}, " +
            "progress_percent=#{progressPercent}, last_read_at=#{lastReadAt}, update_time=NOW() " +
            "WHERE id=#{id} AND is_deleted=0")
    int updateProgress(@Param("id") Long id,
                       @Param("currentChapter") Integer currentChapter,
                       @Param("currentCfi") String currentCfi,
                       @Param("progressPercent") BigDecimal progressPercent,
                       @Param("lastReadAt") LocalDateTime lastReadAt);
}
