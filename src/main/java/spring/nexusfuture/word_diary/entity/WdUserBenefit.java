package spring.nexusfuture.word_diary.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 用户权益记录表
 * 记录用户各项权益的获取来源、数值和时间
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WdUserBenefit {

    /** 主键 */
    private Long id;

    /** 用户ID */
    private Long userId;

    /** 会员等级快照，记录获取时的等级配置 */
    private Integer tierSnapshot;

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

    /**
     * 权益获取渠道：
     * invite_code - 邀请码注册获取
     * purchase - 购买获取
     * gift - 赠送
     * admin - 管理员发放
     */
    private String channel;

    /** 渠道关联ID，如邀请码ID、订单ID等 */
    private Long channelRefId;

    /** 获取时间 */
    private LocalDateTime acquiredAt;

    /** 过期时间，NULL表示永久 */
    private LocalDateTime expiredAt;

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
