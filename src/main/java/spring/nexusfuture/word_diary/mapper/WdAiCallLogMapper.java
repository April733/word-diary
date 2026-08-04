package spring.nexusfuture.word_diary.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import spring.nexusfuture.word_diary.dto.response.AdminAiCallDetailVO;
import spring.nexusfuture.word_diary.dto.response.AdminAiCallListItemVO;
import spring.nexusfuture.word_diary.entity.WdAiCallLog;

import java.util.List;
import java.util.Map;

@Mapper
public interface WdAiCallLogMapper {

    @Insert("INSERT INTO wd_ai_call_log(user_id, model_name, call_scene, input_text, input_char_count, input_word_count, " +
            "assembled_prompt_chars, prompt_tokens, completion_tokens, reasoning_tokens, content_tokens, total_tokens, estimated_cost, " +
            "system_prompt, user_prompt, response_data, duration_ms, create_time, update_time) " +
            "VALUES(#{userId}, #{modelName}, #{callScene}, #{inputText}, #{inputCharCount}, #{inputWordCount}, " +
            "#{assembledPromptChars}, #{promptTokens}, #{completionTokens}, #{reasoningTokens}, #{contentTokens}, #{totalTokens}, #{estimatedCost}, " +
            "#{systemPrompt}, #{userPrompt}, #{responseData}, #{durationMs}, NOW(), NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(WdAiCallLog log);

    @Select("SELECT * FROM wd_ai_call_log WHERE user_id = #{userId} AND is_deleted = 0 ORDER BY create_time DESC")
    List<WdAiCallLog> selectByUserId(Long userId);

    @Select("SELECT DATE(create_time) AS day, COUNT(*) AS calls, " +
            "COALESCE(SUM(input_word_count),0) AS input_words, " +
            "COALESCE(SUM(input_char_count),0) AS input_chars, " +
            "COALESCE(SUM(assembled_prompt_chars),0) AS assembled_chars, " +
            "COALESCE(SUM(prompt_tokens),0) AS prompt_tokens, " +
            "COALESCE(SUM(completion_tokens),0) AS completion_tokens, " +
            "COALESCE(SUM(reasoning_tokens),0) AS reasoning_tokens, " +
            "COALESCE(SUM(content_tokens),0) AS content_tokens, " +
            "COALESCE(SUM(total_tokens),0) AS total_tokens, " +
            "COALESCE(SUM(estimated_cost),0) AS estimated_cost " +
            "FROM wd_ai_call_log WHERE is_deleted = 0 " +
            "GROUP BY DATE(create_time) ORDER BY day DESC")
    List<Map<String, Object>> selectDailyCostSummary();

    @Select("""
            <script>
            SELECT COUNT(*) FROM wd_ai_call_log l
            WHERE l.is_deleted = 0
            <if test="userId != null">AND l.user_id = #{userId}</if>
            <if test="callScene != null and callScene != ''">AND l.call_scene = #{callScene}</if>
            <if test="modelName != null and modelName != ''">AND l.model_name = #{modelName}</if>
            <if test="fromDay != null and fromDay != ''">AND DATE(l.create_time) &gt;= #{fromDay}</if>
            <if test="toDay != null and toDay != ''">AND DATE(l.create_time) &lt;= #{toDay}</if>
            </script>
            """)
    long countAdminCalls(@Param("userId") Long userId,
                         @Param("callScene") String callScene,
                         @Param("modelName") String modelName,
                         @Param("fromDay") String fromDay,
                         @Param("toDay") String toDay);

    @Select("""
            <script>
            SELECT l.id, l.user_id AS userId, u.username AS username, l.model_name AS modelName,
                   l.call_scene AS callScene, l.input_char_count AS inputCharCount,
                   l.input_word_count AS inputWordCount, l.prompt_tokens AS promptTokens,
                   l.completion_tokens AS completionTokens, l.reasoning_tokens AS reasoningTokens,
                   l.content_tokens AS contentTokens, l.total_tokens AS totalTokens,
                   l.estimated_cost AS estimatedCost, l.duration_ms AS durationMs,
                   l.create_time AS createTime
            FROM wd_ai_call_log l
            LEFT JOIN wd_user u ON u.id = l.user_id
            WHERE l.is_deleted = 0
            <if test="userId != null">AND l.user_id = #{userId}</if>
            <if test="callScene != null and callScene != ''">AND l.call_scene = #{callScene}</if>
            <if test="modelName != null and modelName != ''">AND l.model_name = #{modelName}</if>
            <if test="fromDay != null and fromDay != ''">AND DATE(l.create_time) &gt;= #{fromDay}</if>
            <if test="toDay != null and toDay != ''">AND DATE(l.create_time) &lt;= #{toDay}</if>
            ORDER BY l.create_time DESC
            LIMIT #{offset}, #{size}
            </script>
            """)
    List<AdminAiCallListItemVO> selectAdminCalls(@Param("userId") Long userId,
                                                 @Param("callScene") String callScene,
                                                 @Param("modelName") String modelName,
                                                 @Param("fromDay") String fromDay,
                                                 @Param("toDay") String toDay,
                                                 @Param("offset") int offset,
                                                 @Param("size") int size);

    @Select("""
            SELECT l.id, l.user_id AS userId, u.username AS username, l.model_name AS modelName,
                   l.call_scene AS callScene, l.input_text AS inputText,
                   l.input_char_count AS inputCharCount, l.input_word_count AS inputWordCount,
                   l.assembled_prompt_chars AS assembledPromptChars,
                   l.prompt_tokens AS promptTokens, l.completion_tokens AS completionTokens,
                   l.reasoning_tokens AS reasoningTokens, l.content_tokens AS contentTokens,
                   l.total_tokens AS totalTokens, l.estimated_cost AS estimatedCost,
                   l.system_prompt AS systemPrompt, l.user_prompt AS userPrompt,
                   l.response_data AS responseData, l.duration_ms AS durationMs,
                   l.create_time AS createTime
            FROM wd_ai_call_log l
            LEFT JOIN wd_user u ON u.id = l.user_id
            WHERE l.id = #{id} AND l.is_deleted = 0
            """)
    AdminAiCallDetailVO selectAdminCallDetail(Long id);

    @Select("""
            SELECT
              COALESCE(SUM(CASE WHEN DATE(create_time) = CURDATE() THEN estimated_cost ELSE 0 END), 0) AS todayCost,
              COALESCE(SUM(CASE WHEN DATE(create_time) = CURDATE() THEN 1 ELSE 0 END), 0) AS todayCalls,
              COALESCE(SUM(CASE WHEN DATE(create_time) = CURDATE() THEN total_tokens ELSE 0 END), 0) AS todayTokens,
              COALESCE(SUM(CASE WHEN DATE_FORMAT(create_time, '%Y-%m') = DATE_FORMAT(CURDATE(), '%Y-%m')
                                THEN estimated_cost ELSE 0 END), 0) AS monthCost,
              COALESCE(SUM(CASE WHEN DATE_FORMAT(create_time, '%Y-%m') = DATE_FORMAT(CURDATE(), '%Y-%m')
                                THEN 1 ELSE 0 END), 0) AS monthCalls,
              COALESCE(SUM(estimated_cost), 0) AS totalCost,
              COUNT(*) AS totalCalls
            FROM wd_ai_call_log
            WHERE is_deleted = 0
            """)
    Map<String, Object> selectCostKpis();

    @Select("""
            SELECT call_scene AS name, COUNT(*) AS calls,
                   COALESCE(SUM(total_tokens),0) AS total_tokens,
                   COALESCE(SUM(estimated_cost),0) AS estimated_cost
            FROM wd_ai_call_log
            WHERE is_deleted = 0
            GROUP BY call_scene
            ORDER BY estimated_cost DESC
            """)
    List<Map<String, Object>> selectCostByScene();

    @Select("""
            SELECT model_name AS name, COUNT(*) AS calls,
                   COALESCE(SUM(total_tokens),0) AS total_tokens,
                   COALESCE(SUM(estimated_cost),0) AS estimated_cost,
                   ROUND(AVG(duration_ms)) AS avg_duration_ms,
                   MIN(duration_ms) AS min_duration_ms,
                   MAX(duration_ms) AS max_duration_ms
            FROM wd_ai_call_log
            WHERE is_deleted = 0
            GROUP BY model_name
            ORDER BY estimated_cost DESC
            """)
    List<Map<String, Object>> selectCostByModel();

    @Select("""
            SELECT DATE(create_time) AS day, COUNT(*) AS calls,
                   COALESCE(SUM(total_tokens),0) AS total_tokens,
                   COALESCE(SUM(estimated_cost),0) AS estimated_cost
            FROM wd_ai_call_log
            WHERE is_deleted = 0 AND create_time >= DATE_SUB(CURDATE(), INTERVAL #{days} DAY)
            GROUP BY DATE(create_time)
            ORDER BY day ASC
            """)
    List<Map<String, Object>> selectDailyCostRecent(int days);

    @Select("""
            SELECT l.user_id AS userId, u.username AS username, COUNT(*) AS calls,
                   COALESCE(SUM(l.total_tokens),0) AS total_tokens,
                   COALESCE(SUM(l.estimated_cost),0) AS estimated_cost
            FROM wd_ai_call_log l
            LEFT JOIN wd_user u ON u.id = l.user_id
            WHERE l.is_deleted = 0
            GROUP BY l.user_id, u.username
            ORDER BY estimated_cost DESC
            LIMIT #{limit}
            """)
    List<Map<String, Object>> selectCostTopUsers(int limit);

    @Update("""
            UPDATE wd_ai_call_log l
            LEFT JOIN sys_ai_model m
              ON m.model_key = l.model_name AND m.is_deleted = 0 AND m.is_enabled = 1
            LEFT JOIN sys_ai_scene ds
              ON ds.scene_key = 'default' AND ds.is_deleted = 0 AND ds.is_enabled = 1
            LEFT JOIN sys_ai_model d
              ON d.model_key = ds.model_key AND d.is_deleted = 0 AND d.is_enabled = 1
            SET l.estimated_cost = ROUND(
              COALESCE(l.prompt_tokens, 0) / 1000000 * COALESCE(COALESCE(m.input_price_per_million, d.input_price_per_million), 0)
            + COALESCE(l.content_tokens, l.completion_tokens, 0) / 1000000 * COALESCE(COALESCE(m.output_price_per_million, d.output_price_per_million), 0)
            , 8),
            l.update_time = NOW()
            WHERE l.is_deleted = 0
            """)
    int recalculateEstimatedCosts();
}
