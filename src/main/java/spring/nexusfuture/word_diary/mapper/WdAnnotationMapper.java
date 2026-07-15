package spring.nexusfuture.word_diary.mapper;

import org.apache.ibatis.annotations.*;
import spring.nexusfuture.word_diary.entity.WdAnnotation;

import java.util.List;

@Mapper
public interface WdAnnotationMapper {

    @Insert("INSERT INTO wd_annotation(book_id, user_id, chapter_index, cfi_start, cfi_end, " +
            "text_content, note, color, create_by, create_time, update_by, update_time, is_deleted) " +
            "VALUES(#{bookId}, #{userId}, #{chapterIndex}, #{cfiStart}, #{cfiEnd}, " +
            "#{textContent}, #{note}, #{color}, #{createBy}, #{createTime}, #{updateBy}, #{updateTime}, #{isDeleted})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(WdAnnotation annotation);

    @Update("UPDATE wd_annotation SET note=#{note}, color=#{color}, update_time=NOW() " +
            "WHERE id=#{id} AND user_id=#{userId} AND is_deleted=0")
    int updateById(WdAnnotation annotation);

    @Update("UPDATE wd_annotation SET is_deleted=1, update_time=NOW() WHERE id=#{id} AND user_id=#{userId}")
    int deleteById(@Param("id") Long id, @Param("userId") Long userId);

    @Select("SELECT * FROM wd_annotation WHERE id=#{id} AND is_deleted=0")
    WdAnnotation selectById(@Param("id") Long id);

    @Select("SELECT * FROM wd_annotation WHERE book_id=#{bookId} AND user_id=#{userId} AND is_deleted=0 " +
            "ORDER BY chapter_index, id")
    List<WdAnnotation> selectByBookId(@Param("bookId") Long bookId, @Param("userId") Long userId);

    @Select("SELECT * FROM wd_annotation WHERE book_id=#{bookId} AND user_id=#{userId} " +
            "AND chapter_index=#{chapterIndex} AND is_deleted=0 ORDER BY id")
    List<WdAnnotation> selectByChapter(@Param("bookId") Long bookId,
                                       @Param("userId") Long userId,
                                       @Param("chapterIndex") Integer chapterIndex);
}
