package spring.nexusfuture.word_diary.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * AI 模型目录：模型名 + 供应商 + Token 单价（与场景解耦，可被多个场景复用）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SysAiModel {

    private Long id;

    /** 模型名称，对齐 wd_ai_call_log.model_name */
    private String modelKey;

    private String displayName;

    /** 对齐 sys_ai_provider.provider_key */
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
