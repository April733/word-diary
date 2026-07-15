package spring.nexusfuture.word_diary.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;
import spring.nexusfuture.word_diary.entity.WdAiCallLog;

import java.util.List;

@Mapper
public interface WdAiCallLogMapper {

    @Insert("INSERT INTO wd_ai_call_log(user_id, model_name, prompt_tokens, completion_tokens, total_tokens, " +
            "system_prompt, user_prompt, response_data, duration_ms, create_time, update_time) " +
            "VALUES(#{userId}, #{modelName}, #{promptTokens}, #{completionTokens}, #{totalTokens}, " +
            "#{systemPrompt}, #{userPrompt}, #{responseData}, #{durationMs}, NOW(), NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(WdAiCallLog log);

    @Select("SELECT * FROM wd_ai_call_log WHERE user_id = #{userId} AND is_deleted = 0 ORDER BY create_time DESC")
    List<WdAiCallLog> selectByUserId(Long userId);
}
