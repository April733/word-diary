package spring.nexusfuture.word_diary.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * AI 模型目录表（sys_ai_model）：维护模型名、所属供应商与 Token 单价，与场景解耦并可被多个场景复用。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SysAiModel {

    /** 主键 */
    private Long id;

    /** 模型标识/名称，对齐 wd_ai_call_log.model_name */
    private String modelKey;

    /** 管理端展示名 */
    private String displayName;

    /** 所属供应商标识，对齐 sys_ai_provider.provider_key */
    private String provider;

    /**
     * 计费单位：{@code token}=元/百万 tokens；{@code second}=元/秒（ASR，存于 inputPricePerMillion）。
     */
    private String billingUnit;

    /** 公开价币种，统一 CNY */
    private String currency;

    /**
     * token：输入单价（元/百万 prompt tokens）；
     * second：有效语音单价（元/秒）。
     */
    private BigDecimal inputPricePerMillion;

    /** token：输出单价（元/百万 completion tokens）；second 时忽略 */
    private BigDecimal outputPricePerMillion;

    /** 排序权重 */
    private Integer sortNum;

    /** 是否启用：1启用 0停用 */
    private Integer isEnabled;

    /** 备注说明 */
    private String remark;

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
