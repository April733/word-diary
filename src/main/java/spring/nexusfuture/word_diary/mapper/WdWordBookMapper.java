package spring.nexusfuture.word_diary.mapper;

import org.apache.ibatis.annotations.*;
import spring.nexusfuture.word_diary.entity.WdWordBook;
import java.util.List;

@Mapper
public interface WdWordBookMapper {

    @Insert("INSERT INTO wd_word_book(user_id, book_id, book_name, book_desc, sort_num, status, create_time, update_time) " +
            "VALUES(#{userId}, #{bookId}, #{bookName}, #{bookDesc}, #{sortNum}, #{status}, NOW(), NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(WdWordBook book);

    @Select("SELECT * FROM wd_word_book WHERE user_id = #{userId} AND is_deleted = 0 ORDER BY sort_num ASC")
    List<WdWordBook> selectByUserId(Long userId);

    @Select("SELECT * FROM wd_word_book WHERE user_id = #{userId} AND book_id = #{bookId} AND is_deleted = 0 " +
            "ORDER BY sort_num ASC, id ASC")
    List<WdWordBook> selectByBookId(@Param("userId") Long userId, @Param("bookId") Long bookId);

    @Select("SELECT * FROM wd_word_book WHERE id = #{id} AND is_deleted = 0")
    WdWordBook selectById(Long id);

    @Select("SELECT * FROM wd_word_book WHERE id = #{id}")
    WdWordBook selectByIdAny(Long id);

    @Update("UPDATE wd_word_book SET book_name = #{bookName}, book_desc = #{bookDesc}, " +
            "sort_num = #{sortNum}, update_time = NOW() WHERE id = #{id}")
    int update(WdWordBook book);

    /** status: 1参与每日学习 0不参与 */
    @Update("UPDATE wd_word_book SET status = #{status}, update_time = NOW() WHERE id = #{id}")
    int updateStatus(@Param("id") Long id, @Param("status") Integer status);

    @Update("UPDATE wd_word_book SET is_deleted = 1, update_time = NOW() WHERE id = #{id}")
    int deleteById(Long id);
}
