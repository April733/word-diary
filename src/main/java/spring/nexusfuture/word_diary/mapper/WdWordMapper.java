package spring.nexusfuture.word_diary.mapper;

import org.apache.ibatis.annotations.*;
import spring.nexusfuture.word_diary.entity.WdWord;
import java.util.List;

@Mapper
public interface WdWordMapper {

    @Insert("INSERT INTO wd_word(notebook_id, word_proto, us_phonetic, uk_phonetic, part_speech_mean, " +
            "sentence_example, root_affix, entry_type, source_book_id, source_snippet, source_location, " +
            "is_public, user_id, create_time, update_time) " +
            "VALUES(#{notebookId}, #{wordProto}, #{usPhonetic}, #{ukPhonetic}, #{partSpeechMean}, " +
            "#{sentenceExample}, #{rootAffix}, COALESCE(#{entryType}, 'word'), #{sourceBookId}, #{sourceSnippet}, #{sourceLocation}, " +
            "#{isPublic}, #{userId}, NOW(), NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(WdWord word);

    @Select("SELECT * FROM wd_word WHERE notebook_id = #{notebookId} AND is_deleted = 0 ORDER BY id DESC")
    List<WdWord> selectByNotebookId(Long notebookId);

    @Select("SELECT * FROM wd_word WHERE id = #{id} AND is_deleted = 0")
    WdWord selectById(Long id);

    @Select("SELECT * FROM wd_word WHERE id = #{id}")
    WdWord selectByIdAny(Long id);

    @Update("UPDATE wd_word SET word_proto = #{wordProto}, us_phonetic = #{usPhonetic}, " +
            "uk_phonetic = #{ukPhonetic}, part_speech_mean = #{partSpeechMean}, " +
            "sentence_example = #{sentenceExample}, root_affix = #{rootAffix}, " +
            "entry_type = #{entryType}, source_book_id = #{sourceBookId}, " +
            "source_snippet = #{sourceSnippet}, source_location = #{sourceLocation}, " +
            "update_time = NOW() WHERE id = #{id} AND is_deleted = 0")
    int update(WdWord word);

    @Select("SELECT * FROM wd_word WHERE notebook_id = #{notebookId} AND LOWER(word_proto) = LOWER(#{wordProto}) AND is_deleted = 0 LIMIT 1")
    WdWord selectByNotebookAndWordProto(@Param("notebookId") Long notebookId, @Param("wordProto") String wordProto);

    /** 移入垃圾桶（软删，保留 7 天） */
    @Update("UPDATE wd_word SET is_deleted = 1, deleted_at = NOW(), update_time = NOW() WHERE id = #{id} AND is_deleted = 0")
    int deleteById(Long id);

    @Update("UPDATE wd_word SET is_deleted = 1, deleted_at = NOW(), update_time = NOW() WHERE notebook_id = #{notebookId} AND is_deleted = 0")
    int softDeleteByNotebookId(Long notebookId);

    @Select("SELECT * FROM wd_word WHERE notebook_id = #{notebookId} AND is_deleted = 1 " +
            "AND deleted_at IS NOT NULL AND deleted_at >= DATE_SUB(NOW(), INTERVAL 7 DAY) " +
            "ORDER BY deleted_at DESC")
    List<WdWord> selectTrashByNotebookId(Long notebookId);

    @Update("UPDATE wd_word SET is_deleted = 0, deleted_at = NULL, update_time = NOW() " +
            "WHERE id = #{id} AND is_deleted = 1")
    int restoreById(Long id);

    /** 物理删除已过期的垃圾桶词条 */
    @Delete("DELETE FROM wd_word WHERE is_deleted = 1 AND deleted_at IS NOT NULL " +
            "AND deleted_at < DATE_SUB(NOW(), INTERVAL 7 DAY)")
    int hardDeleteExpiredTrash();

    @Select("SELECT COUNT(*) FROM wd_word WHERE notebook_id = #{notebookId} AND is_deleted = 1 " +
            "AND deleted_at IS NOT NULL AND deleted_at >= DATE_SUB(NOW(), INTERVAL 7 DAY)")
    int countTrashByNotebookId(Long notebookId);
}
