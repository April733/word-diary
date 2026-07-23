package spring.nexusfuture.word_diary.mapper;

import org.apache.ibatis.annotations.*;
import spring.nexusfuture.word_diary.entity.WdWord;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface WdWordMapper {

    @Insert("INSERT INTO wd_word(notebook_id, word_proto, us_phonetic, uk_phonetic, part_speech_mean, " +
            "sentence_example, root_affix, entry_type, source_book_id, source_snippet, source_location, " +
            "is_public, user_id, status, status_data, create_time, update_time) " +
            "VALUES(#{notebookId}, #{wordProto}, #{usPhonetic}, #{ukPhonetic}, #{partSpeechMean}, " +
            "#{sentenceExample}, #{rootAffix}, COALESCE(#{entryType}, 'word'), #{sourceBookId}, #{sourceSnippet}, #{sourceLocation}, " +
            "#{isPublic}, #{userId}, COALESCE(#{status}, 0), COALESCE(#{statusData}, NOW()), NOW(), NOW())")
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

    @Update("UPDATE wd_word SET status = #{status}, status_data = #{statusData}, update_time = NOW() " +
            "WHERE id = #{id} AND is_deleted = 0")
    int updateStudy(WdWord word);

    @Select("SELECT * FROM wd_word WHERE notebook_id = #{notebookId} AND LOWER(word_proto) = LOWER(#{wordProto}) AND is_deleted = 0 LIMIT 1")
    WdWord selectByNotebookAndWordProto(@Param("notebookId") Long notebookId, @Param("wordProto") String wordProto);

    /** 物理删除单词 */
    @Delete("DELETE FROM wd_word WHERE id = #{id}")
    int deleteById(Long id);

    @Update("UPDATE wd_word SET is_deleted = 1, update_time = NOW() WHERE notebook_id = #{notebookId} AND is_deleted = 0")
    int softDeleteByNotebookId(Long notebookId);

    @Select("SELECT w.* FROM wd_word w " +
            "INNER JOIN wd_word_book b ON b.id = w.notebook_id AND b.is_deleted = 0 AND b.status = 1 " +
            "WHERE w.user_id = #{userId} AND w.status = 0 AND w.is_deleted = 0 " +
            "ORDER BY w.id ASC LIMIT #{limit}")
    List<WdWord> selectNewWords(@Param("userId") Long userId, @Param("limit") int limit);

    /**
     * 到期复习：status NOT IN (0,7)，且「当前学习日 − 上次学习日」≥ 该阶段间隔。
     * {@code studyDate} 按每日刷新节点切日（默认 7:00），不是自然日 0:00。
     * MySQL：DATEDIFF(学习日, DATE(status_data))
     */
    @Select("SELECT w.* FROM wd_word w " +
            "INNER JOIN wd_word_book b ON b.id = w.notebook_id AND b.is_deleted = 0 AND b.status = 1 " +
            "WHERE w.user_id = #{userId} AND w.status NOT IN (0, 7) AND w.status_data IS NOT NULL " +
            "AND DATEDIFF(#{studyDate}, DATE(w.status_data)) >= CASE w.status " +
            "  WHEN 1 THEN 1 " +
            "  WHEN 2 THEN 1 " +
            "  WHEN 3 THEN 2 " +
            "  WHEN 4 THEN 4 " +
            "  WHEN 5 THEN 7 " +
            "  WHEN 6 THEN 15 " +
            "  ELSE 999 END " +
            "AND w.is_deleted = 0 ORDER BY w.status ASC, w.status_data ASC")
    List<WdWord> selectReviewTasks(@Param("userId") Long userId, @Param("studyDate") LocalDate studyDate);

    @Select("<script>SELECT COUNT(*) FROM wd_word w " +
            "INNER JOIN wd_word_book b ON b.id = w.notebook_id AND b.is_deleted = 0 " +
            "WHERE w.user_id = #{userId} AND w.status BETWEEN 1 AND 7 AND w.is_deleted = 0" +
            "<if test='notebookId != null'> AND w.notebook_id = #{notebookId}</if></script>")
    int countBrowseWords(@Param("userId") Long userId, @Param("notebookId") Long notebookId);

    @Select("<script>SELECT w.* FROM wd_word w " +
            "INNER JOIN wd_word_book b ON b.id = w.notebook_id AND b.is_deleted = 0 " +
            "WHERE w.user_id = #{userId} AND w.status BETWEEN 1 AND 7 AND w.is_deleted = 0" +
            "<if test='notebookId != null'> AND w.notebook_id = #{notebookId}</if> " +
            "ORDER BY COALESCE(w.status_data, w.update_time) DESC, w.id DESC LIMIT #{limit} OFFSET #{offset}</script>")
    List<WdWord> selectBrowseWords(@Param("userId") Long userId, @Param("notebookId") Long notebookId,
                                   @Param("limit") int limit, @Param("offset") int offset);

    @Select("SELECT w.* FROM wd_word w " +
            "INNER JOIN wd_word_book b ON b.id = w.notebook_id AND b.is_deleted = 0 " +
            "WHERE w.user_id = #{userId} AND w.is_deleted = 0")
    List<WdWord> selectAllByUserId(Long userId);

    /** 已开启「在背」的单词本中的全部单词 */
    @Select("SELECT w.* FROM wd_word w " +
            "INNER JOIN wd_word_book b ON b.id = w.notebook_id AND b.is_deleted = 0 AND b.status = 1 " +
            "WHERE w.user_id = #{userId} AND w.is_deleted = 0")
    List<WdWord> selectAllInStudyPlan(Long userId);

    /** 未开启「在背」的单词本中的词数（不计入每日计划） */
    @Select("SELECT COUNT(*) FROM wd_word w " +
            "INNER JOIN wd_word_book b ON b.id = w.notebook_id AND b.is_deleted = 0 AND b.status = 0 " +
            "WHERE w.user_id = #{userId} AND w.is_deleted = 0")
    int countPausedByUserId(@Param("userId") Long userId);
}
