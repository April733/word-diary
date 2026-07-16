package spring.nexusfuture.word_diary.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * AI 场景调用配置：场景 → 模型 → Token 单价（公开价录入，用于成本估算）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SysAiModel {

    private Long id;

    /** 场景标识，对齐 wd_ai_call_log.call_scene：feature / word_parse / reading_chat / default */
    private String sceneKey;

    /** 场景描述，如「阅读解析」「单词批量导入」 */
    private String sceneName;

    /** 模型名称，对齐 wd_ai_call_log.model_name */
    private String modelKey;

    private String displayName;

    /** deepseek / aliyun 等，决定走哪条 app.ai.providers */
    private String provider;

    /** 公开价币种，统一 CNY */
    private String currency;

    /** 输入单价：每百万 prompt tokens */
    private BigDecimal inputPricePerMillion;

    /** 输出单价：每百万 completion tokens */
    private BigDecimal outputPricePerMillion;

    private Integer sortNum;
    private Integer isEnabled;
    private String remark;

    private Long createBy;
    private LocalDateTime createTime;
    private Long updateBy;
    private LocalDateTime updateTime;
    private Integer isDeleted;
}
