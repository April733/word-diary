package spring.nexusfuture.word_diary.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 大模型调用历史表（wd_ai_call_log）：记录每次 AI 调用的输入、token 用量与估算成本，供审计与费用看板使用。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WdAiCallLog {

    /** 主键 */
    private Long id;

    /** 调用用户ID */
    private Long userId;

    /** 实际调用的模型名称，如 deepseek-v4-flash / qwen-turbo */
    private String modelName;

    /** 调用场景：word_parse / reading_chat / feature 等 */
    private String callScene;

    /** 用户原始输入（选中文本/单词列表/提问） */
    private String inputText;

    /** 用户原始输入字符数 */
    private Integer inputCharCount;

    /** 用户原始输入词数（中英混合估算） */
    private Integer inputWordCount;

    /** 拼装后 system+user 提示词总字符数 */
    private Integer assembledPromptChars;

    /** 提示词 token 数（模型返回 usage） */
    private Integer promptTokens;

    /** 补全 token 数（模型返回 usage，含 reasoning + content） */
    private Integer completionTokens;

    /** 思考 token 数（reasoning tokens，仅推理模型有） */
    private Integer reasoningTokens;

    /** 可见内容 token 数（= completionTokens - reasoningTokens，给用户看的实际内容） */
    private Integer contentTokens;

    /** 总 token 数 */
    private Integer totalTokens;

    /** 按当时模型单价估算的成本 */
    private java.math.BigDecimal estimatedCost;

    /** 系统提示词（拼装后） */
    private String systemPrompt;

    /** 用户提示词（拼装后） */
    private String userPrompt;

    /** 大模型返回的原始数据 */
    private String responseData;

    /** 调用耗时（毫秒） */
    private Integer durationMs;

    /** 创建人 */
    private Long createBy;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 修改人 */
    private Long updateBy;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 是否删除：1已删除 0未删除 */
    private Integer isDeleted;
}
