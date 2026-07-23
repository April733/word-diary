package spring.nexusfuture.word_diary.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 系统基础配置表（sys_base_config）：存放键值型全局配置（如学习解锁时刻、功能开关等）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SysBaseConfig {

    /** 主键 */
    private Long id;

    /** 配置键，如 study.review_unlock_hour */
    private String configKey;

    /** 配置值 */
    private String configValue;

    /** 值类型：string / int / bool / json */
    private String valueType;

    /** 分类：study / general / ai 等 */
    private String category;

    /** 配置说明（用途、取值范围） */
    private String description;

    /** 同分类内排序权重 */
    private Integer sortNum;

    /** 是否启用：1启用 0停用 */
    private Integer isEnabled;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 是否删除：1已删除 0未删除 */
    private Integer isDeleted;
}
