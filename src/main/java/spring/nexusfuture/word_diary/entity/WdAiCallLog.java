package spring.nexusfuture.word_diary.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 大模型调用历史记录（含成本计量字段）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WdAiCallLog {

    private Long id;
    private Long userId;
    private String modelName;

    /** 调用场景: word_parse / reading_chat 等 */
    private String callScene;

    /** 用户原始输入 */
    private String inputText;
    private Integer inputCharCount;
    private Integer inputWordCount;

    /** 拼装后 system+user 提示词总字符数 */
    private Integer assembledPromptChars;

    private Integer promptTokens;
    private Integer completionTokens;
    private Integer totalTokens;

    /** 按当时模型单价估算的成本 */
    private java.math.BigDecimal estimatedCost;

    private String systemPrompt;
    private String userPrompt;
    private String responseData;
    private Integer durationMs;

    private Long createBy;
    private LocalDateTime createTime;
    private Long updateBy;
    private LocalDateTime updateTime;
    private Integer isDeleted;
}
