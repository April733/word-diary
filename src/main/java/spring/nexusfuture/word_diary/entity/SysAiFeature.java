package spring.nexusfuture.word_diary.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
/**
 * AI功能配置表
 */
public class SysAiFeature {

    /** 主键 */
    private Long id;

    /** 功能标识键，如 word/meaning/collocation/sentence_pattern/phonetic/root_affix/example */
    private String featureKey;

    /** 功能显示名称 */
    private String featureName;

    /** 功能描述说明 */
    private String description;

    /** AI提示词模板 */
    private String prompt;

    /** 排序序号 */
    private Integer sortNum;

    /** 状态 1启用 0停用 */
    private Integer status;

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
