package spring.nexusfuture.word_diary.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 大模型调用历史记录
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

    /** 模型名称，如 deepseek-chat */
    private String modelName;

    /** 提示词token数量 */
    private Integer promptTokens;

    /** 返回token数量 */
    private Integer completionTokens;

    /** 总token数量 */
    private Integer totalTokens;

    /** 系统提示词 */
    private String systemPrompt;

    /** 用户提示词 */
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

    /** 是否删除 1已删除 0未删除 */
    private Integer isDeleted;
}
