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

    @Select("SELECT * FROM wd_word WHERE notebook_id = #{notebookId} AND is_deleted = 0")
    List<WdWord> selectByNotebookId(Long notebookId);

    @Select("SELECT * FROM wd_word WHERE id = #{id} AND is_deleted = 0")
    WdWord selectById(Long id);

    @Update("UPDATE wd_word SET word_proto = #{wordProto}, us_phonetic = #{usPhonetic}, " +
            "uk_phonetic = #{ukPhonetic}, part_speech_mean = #{partSpeechMean}, " +
            "sentence_example = #{sentenceExample}, root_affix = #{rootAffix}, " +
            "entry_type = #{entryType}, source_snippet = #{sourceSnippet}, source_location = #{sourceLocation}, " +
            "update_time = NOW() WHERE id = #{id} AND is_deleted = 0")
    int update(WdWord word);

    @Select("SELECT * FROM wd_word WHERE notebook_id = #{notebookId} AND LOWER(word_proto) = LOWER(#{wordProto}) AND is_deleted = 0 LIMIT 1")
    WdWord selectByNotebookAndWordProto(@Param("notebookId") Long notebookId, @Param("wordProto") String wordProto);

    @Delete("UPDATE wd_word SET is_deleted = 1, update_time = NOW() WHERE id = #{id}")
    int deleteById(Long id);

    @Update("UPDATE wd_word SET is_deleted = 1, update_time = NOW() WHERE notebook_id = #{notebookId} AND is_deleted = 0")
    int softDeleteByNotebookId(Long notebookId);
}
