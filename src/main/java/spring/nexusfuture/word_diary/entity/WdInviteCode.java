package spring.nexusfuture.word_diary.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 邀请码表
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WdInviteCode {

    /** 主键 */
    private Long id;

    /** 序号 */
    private Integer seqNo;

    /** 邀请码 */
    private String inviteCode;

    /** 对应会员等级，关联 sys_member_tier.tier */
    private Integer memberTier;

    /** 核销用户ID，NULL表示未核销 */
    private Long usedBy;

    /** 核销时间 */
    private LocalDateTime usedTime;

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
