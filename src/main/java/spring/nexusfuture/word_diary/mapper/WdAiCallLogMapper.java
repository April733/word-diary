package spring.nexusfuture.word_diary.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import spring.nexusfuture.word_diary.entity.WdAiCallLog;

import java.util.List;
import java.util.Map;

@Mapper
public interface WdAiCallLogMapper {

    @Insert("INSERT INTO wd_ai_call_log(user_id, model_name, call_scene, input_text, input_char_count, input_word_count, " +
            "assembled_prompt_chars, prompt_tokens, completion_tokens, total_tokens, estimated_cost, " +
            "system_prompt, user_prompt, response_data, duration_ms, create_time, update_time) " +
            "VALUES(#{userId}, #{modelName}, #{callScene}, #{inputText}, #{inputCharCount}, #{inputWordCount}, " +
            "#{assembledPromptChars}, #{promptTokens}, #{completionTokens}, #{totalTokens}, #{estimatedCost}, " +
            "#{systemPrompt}, #{userPrompt}, #{responseData}, #{durationMs}, NOW(), NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(WdAiCallLog log);

    @Select("SELECT * FROM wd_ai_call_log WHERE user_id = #{userId} AND is_deleted = 0 ORDER BY create_time DESC")
    List<WdAiCallLog> selectByUserId(Long userId);

    /** 按日汇总：token + 已落库估算成本 */
    @Select("SELECT DATE(create_time) AS day, COUNT(*) AS calls, " +
            "COALESCE(SUM(input_word_count),0) AS input_words, " +
            "COALESCE(SUM(input_char_count),0) AS input_chars, " +
            "COALESCE(SUM(assembled_prompt_chars),0) AS assembled_chars, " +
            "COALESCE(SUM(prompt_tokens),0) AS prompt_tokens, " +
            "COALESCE(SUM(completion_tokens),0) AS completion_tokens, " +
            "COALESCE(SUM(total_tokens),0) AS total_tokens, " +
            "COALESCE(SUM(estimated_cost),0) AS estimated_cost " +
            "FROM wd_ai_call_log WHERE is_deleted = 0 " +
            "GROUP BY DATE(create_time) ORDER BY day DESC")
    List<Map<String, Object>> selectDailyCostSummary();

    /**
     * 按当前 sys_ai_model 单价重算历史成本。
     * 找不到精确 model_key 时回退到 is_default=1 的模型。
     */
    @Update("""
            UPDATE wd_ai_call_log l
            LEFT JOIN (
              SELECT m1.model_key,
                     m1.input_price_per_million,
                     m1.output_price_per_million
              FROM sys_ai_model m1
              INNER JOIN (
                SELECT model_key, MIN(id) AS min_id
                FROM sys_ai_model
                WHERE is_deleted = 0 AND is_enabled = 1
                GROUP BY model_key
              ) t ON t.min_id = m1.id
            ) m ON m.model_key = l.model_name
            LEFT JOIN sys_ai_model d ON d.scene_key = 'default' AND d.is_deleted = 0 AND d.is_enabled = 1
            SET l.estimated_cost = ROUND(
              COALESCE(l.prompt_tokens, 0) / 1000000 * COALESCE(COALESCE(m.input_price_per_million, d.input_price_per_million), 0)
            + COALESCE(l.completion_tokens, 0) / 1000000 * COALESCE(COALESCE(m.output_price_per_million, d.output_price_per_million), 0)
            , 8),
            l.update_time = NOW()
            WHERE l.is_deleted = 0
            """)
    int recalculateEstimatedCosts();
}
