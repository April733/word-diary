package spring.nexusfuture.word_diary.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 会员等级配置表
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SysMemberTier {

    /** 会员等级 */
    private Integer tier;

    /** 等级名称 */
    private String tierName;

    /** 书架数量上限 */
    private Integer maxBookshelves;

    /** 单词本数量上限 */
    private Integer maxWordbooks;

    /** 每个单词本单词上限 */
    private Integer maxWordsPerBook;

    /** 每日批量导入次数上限 */
    private Integer dailyImportLimit;

    /** 每日AI阅读次数上限 */
    private Integer dailyAiReadingLimit;

    /** 是否启用AI功能 1是 0否 */
    private Integer isAiEnabled;

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
