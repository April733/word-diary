package spring.nexusfuture.word_diary.mapper;

import org.apache.ibatis.annotations.*;
import spring.nexusfuture.word_diary.entity.WdPdfMarkup;

import java.util.List;

@Mapper
public interface WdPdfMarkupMapper {

    @Insert("INSERT INTO wd_pdf_markup(book_id, user_id, page_num, markup_type, geometry_json, color, note_text, " +
            "create_by, create_time, update_by, update_time, is_deleted) " +
            "VALUES(#{bookId}, #{userId}, #{pageNum}, #{markupType}, #{geometryJson}, #{color}, #{noteText}, " +
            "#{createBy}, #{createTime}, #{updateBy}, #{updateTime}, #{isDeleted})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(WdPdfMarkup markup);

    @Update("UPDATE wd_pdf_markup SET geometry_json=#{geometryJson}, color=#{color}, note_text=#{noteText}, " +
            "update_by=#{updateBy}, update_time=NOW() WHERE id=#{id} AND user_id=#{userId} AND is_deleted=0")
    int updateById(WdPdfMarkup markup);

    @Update("UPDATE wd_pdf_markup SET is_deleted=1, update_time=NOW() WHERE id=#{id} AND user_id=#{userId}")
    int softDelete(@Param("id") Long id, @Param("userId") Long userId);

    @Select("SELECT * FROM wd_pdf_markup WHERE id=#{id} AND is_deleted=0")
    WdPdfMarkup selectById(Long id);

    @Select("SELECT * FROM wd_pdf_markup WHERE book_id=#{bookId} AND user_id=#{userId} AND is_deleted=0 " +
            "ORDER BY page_num ASC, id ASC")
    List<WdPdfMarkup> selectByBook(@Param("bookId") Long bookId, @Param("userId") Long userId);

    @Select("SELECT * FROM wd_pdf_markup WHERE book_id=#{bookId} AND user_id=#{userId} AND page_num=#{pageNum} " +
            "AND is_deleted=0 ORDER BY id ASC")
    List<WdPdfMarkup> selectByPage(@Param("bookId") Long bookId, @Param("userId") Long userId,
                                   @Param("pageNum") Integer pageNum);
}
