package spring.nexusfuture.word_diary.mapper;

import org.apache.ibatis.annotations.*;
import spring.nexusfuture.word_diary.entity.WdWord;
import java.util.List;

@Mapper
public interface WdWordMapper {

    @Insert("INSERT INTO wd_word(notebook_id, word_proto, us_phonetic, uk_phonetic, part_speech_mean, " +
            "sentence_example, root_affix, is_public, user_id, create_time, update_time) " +
            "VALUES(#{notebookId}, #{wordProto}, #{usPhonetic}, #{ukPhonetic}, #{partSpeechMean}, " +
            "#{sentenceExample}, #{rootAffix}, #{isPublic}, #{userId}, NOW(), NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(WdWord word);

    @Select("SELECT * FROM wd_word WHERE notebook_id = #{notebookId} AND is_deleted = 0")
    List<WdWord> selectByNotebookId(Long notebookId);

    @Select("SELECT * FROM wd_word WHERE id = #{id} AND is_deleted = 0")
    WdWord selectById(Long id);

    @Delete("UPDATE wd_word SET is_deleted = 1, update_time = NOW() WHERE id = #{id}")
    int deleteById(Long id);
}
